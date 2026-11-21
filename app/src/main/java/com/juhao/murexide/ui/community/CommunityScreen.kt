package com.juhao.murexide.ui.community

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.data.PostItem
import com.juhao.murexide.data.BaItem
import com.juhao.murexide.ui.community.ba.BaDetailActivity
import com.juhao.murexide.ui.community.detail.PostDetailActivity
import com.juhao.murexide.ui.community.myposts.MyPostsActivity
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.icons.*
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.LayoutDirection

operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    return PaddingValues(
        start = calculateStartPadding(LayoutDirection.Ltr) +
                other.calculateStartPadding(LayoutDirection.Ltr),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(LayoutDirection.Ltr) +
                other.calculateEndPadding(LayoutDirection.Ltr),
        bottom = calculateBottomPadding() + other.calculateBottomPadding()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    token: String,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val viewModel: CommunityViewModel = viewModel(
        key = "community_$token",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return CommunityViewModel(token) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val context = LocalContext.current

    var showCreateBaDialog by remember { mutableStateOf(false) }

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { query ->
                viewModel.searchPosts(query)
            }
    }

    LaunchedEffect(searchState.keyword) {
        if (textFieldState.text.toString() != searchState.keyword) {
            textFieldState.setTextAndPlaceCursorAtEnd(searchState.keyword)
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text("搜索文章") },
            leadingIcon = {
                if (searchBarState.targetValue != SearchBarValue.Collapsed) {
                    IconButton(onClick = {
                        scope.launch { searchBarState.animateToCollapsed() }
                    }) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                } else {
                    Icon(AppIcons.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(AppIcons.Close, contentDescription = "清除")
                    }
                }
            }
        )
    }

    val tabs = listOf(
        CommunityTab.RECOMMEND to "推荐",
        CommunityTab.ALL_BA to "全部板块",
        CommunityTab.MANAGE to "管理"
    )

    val selectedIndex = tabs.indexOfFirst {
        it.first == uiState.currentTab
    }.coerceAtLeast(0)

    StyledScaffold(
        title = { Text("社区") },
        actions = {
            if (uiState.currentTab == CommunityTab.RECOMMEND) {
                IconButton(onClick = { viewModel.toggleSort() }) {
                    Icon(
                        imageVector = if (uiState.sort == CommunitySort.RECOMMEND) {
                            AppIcons.LocalFireDepartment
                        } else {
                            AppIcons.Update
                        },
                        contentDescription = if (uiState.sort == CommunitySort.RECOMMEND) {
                            "推荐"
                        } else {
                            "最新"
                        }
                    )
                }
            }

            IconButton(onClick = {
                scope.launch { searchBarState.animateToExpanded() }
            }) {
                Icon(AppIcons.Search, contentDescription = "搜索")
            }
        },
        isMediumTopAppBarDisabled = true,
        extraContent = {
            CapsuleTabBar(
                tabs = tabs.map { it.second },
                selectedTabIndex = selectedIndex,
                onTabSelected = { index ->
                    viewModel.selectTab(tabs[index].first)
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    ) { contentPadding ->
        val combinedPadding = innerPadding + contentPadding

        when (uiState.currentTab) {
            CommunityTab.RECOMMEND -> {
                if (uiState.sort == CommunitySort.RECOMMEND) {
                    PostsList(
                        posts = uiState.posts,
                        contentPadding = combinedPadding,
                        isLoading = uiState.isLoadingPosts,
                        isRefreshing = uiState.isRefreshingPosts,
                        isLoadingMore = uiState.isLoadingMore,
                        onRefresh = viewModel::loadRecommend,
                        onLikeClick = viewModel::toggleLike,
                        onCollectClick = viewModel::toggleCollect,
                        onPostClick = {
                            PostDetailActivity.start(context, it.id)
                        },
                        onLoadMore = viewModel::loadMorePosts
                    )
                } else {
                    PostsList(
                        posts = uiState.latestPosts,
                        contentPadding = combinedPadding,
                        isLoading = uiState.isLatestLoading,
                        isRefreshing = uiState.isLatestRefreshing,
                        isLoadingMore = uiState.isLatestLoadingMore,
                        onRefresh = viewModel::loadLatest,
                        onLikeClick = viewModel::toggleLike,
                        onCollectClick = viewModel::toggleCollect,
                        onPostClick = {
                            PostDetailActivity.start(context, it.id)
                        },
                        onLoadMore = viewModel::loadMoreLatest
                    )
                }
            }

            CommunityTab.ALL_BA -> AllBaContent(
                contentPadding = combinedPadding,
                currentSide = uiState.currentBaSide,
                onSideSelected = viewModel::selectBaSide,
                baList = uiState.allBaList,
                isLoading = uiState.isLoadingAllBa,
                onBaClick = {
                    BaDetailActivity.start(context, it.id)
                }
            )

            CommunityTab.MANAGE -> ManageContent(
                contentPadding = combinedPadding,
                baList = uiState.manageBaList,
                isLoading = uiState.isLoadingManageBa,
                onMyPostsClick = {
                    MyPostsActivity.start(context)
                },
                onCreateBaClick = {
                    showCreateBaDialog = true
                },
                onBaClick = {
                    BaDetailActivity.start(context, it.id)
                }
            )
        }
    }

    if (showCreateBaDialog) {
        CreateBaDialog(
            isCreating = uiState.isCreatingBa,
            isUploadingAvatar = uiState.isUploadingAvatar,
            uploadProgress = uiState.uploadProgress,
            avatarUrl = uiState.avatarUrl,
            error = uiState.error,
            onDismiss = {
                if (!uiState.isCreatingBa && !uiState.isUploadingAvatar) {
                    showCreateBaDialog = false
                }
            },
            onPickAvatar = { uri ->
                viewModel.uploadBaAvatar(context, uri)
            },
            onCreate = { name ->
                viewModel.createBa(name) { success, message ->
                    if (success) {
                        showCreateBaDialog = false
                        Toast.makeText(context, "创建成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField
    ) {
        val listState = rememberLazyListState()

        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = layoutInfo.totalItemsCount
                totalItems > 0 && lastVisible >= totalItems - 3
            }
        }

        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) viewModel.loadMoreSearch()
        }

        when {
            searchState.isSearching && searchState.results.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            searchState.error != null && searchState.results.isEmpty() -> EmptyPlaceholder(
                modifier = Modifier.fillMaxSize(),
                icon = AppIcons.Warning,
                title = "搜索失败",
                subtitle = searchState.error
            )

            searchState.results.isEmpty() && searchState.hasSearched -> EmptyPlaceholder(
                modifier = Modifier.fillMaxSize(),
                icon = AppIcons.Search,
                title = "未找到相关文章",
                subtitle = "换个关键词试试"
            )

            searchState.results.isEmpty() -> EmptyPlaceholder(
                modifier = Modifier.fillMaxSize(),
                icon = AppIcons.Inbox,
                title = "搜索文章",
                subtitle = "输入关键词开始搜索"
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues()
            ) {
                itemsIndexed(searchState.results, key = { _, item -> "search_${item.id}" }) { index, post ->
                    PostCard(
                        modifier = Modifier.animateItem(),
                        isTop = index == 0,
                        isBottom = index == searchState.results.lastIndex,
                        post = post,
                        onLikeClick = viewModel::toggleLike,
                        onCollectClick = viewModel::toggleCollect,
                        onPostClick = {
                            PostDetailActivity.start(context, it.id)
                        }
                    )
                }

                if (searchState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AllBaContent(
    contentPadding: PaddingValues,
    currentSide: BaSide,
    onSideSelected: (BaSide) -> Unit,
    baList: List<BaItem>,
    isLoading: Boolean,
    onBaClick: (BaItem) -> Unit
) {
    val sides = listOf(
        BaSide.OFFICIAL to AppIcons.WorkspacePremium,
        BaSide.USER to AppIcons.People,
        BaSide.SUBSCRIBE to AppIcons.Favorite
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(contentPadding)
        ) {
            sides.forEach { (side, icon) ->
                val selected = currentSide == side

                Box(
                    modifier = Modifier
                        .height(58.dp)
                        .width(72.dp)
                        .padding(vertical = 4.dp)
                        .padding(end = 12.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 0.dp,
                                topEnd = 50.dp,
                                bottomStart = 0.dp,
                                bottomEnd = 50.dp
                            )
                        )
                        .clickable { onSideSelected(side) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            BaList(
                contentPadding = contentPadding,
                baList = baList,
                isLoading = isLoading,
                emptyText = if (currentSide == BaSide.USER) {
                    "暂无用户自建分区"
                } else {
                    "暂无分区"
                },
                onBaClick = onBaClick
            )
        }
    }
}

@Composable
fun ManageContent(
    contentPadding: PaddingValues,
    baList: List<BaItem>,
    isLoading: Boolean,
    onMyPostsClick: () -> Unit,
    onCreateBaClick: () -> Unit,
    onBaClick: (BaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
            top = 8.dp + contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
            bottom = contentPadding.calculateBottomPadding()
        )
    ) {
        item {
            SettingsGroupHeader("管理")
        }

        item {
            SettingsGroupSurface(isTop = true, isBottom = false) {
                SettingsItem(
                    icon = AppIcons.Article,
                    title = "我的文章",
                    subtitle = "查看我发布的全部文章",
                    onClick = onMyPostsClick
                )
            }

            SettingsGroupSurface(isTop = false, isBottom = true) {
                SettingsItem(
                    icon = AppIcons.Add,
                    title = "新建分区",
                    onClick = onCreateBaClick
                )
            }
        }

        item {
            SettingsGroupHeader("我的分区")
        }

        when {
            isLoading && baList.isEmpty() -> item {
                Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }

            baList.isEmpty() -> item {
                Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "你还没有创建分区",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> itemsIndexed(baList) { index, ba ->
                SettingsGroupSurface(
                    isTop = index == 0,
                    isBottom = index == baList.size - 1
                ) {
                    BaRow(ba = ba, onClick = { onBaClick(ba) })
                }
            }
        }
    }
}

@Composable
fun CreateBaDialog(
    isCreating: Boolean,
    isUploadingAvatar: Boolean,
    uploadProgress: Float,
    avatarUrl: String,
    error: String?,
    onDismiss: () -> Unit,
    onPickAvatar: (Uri) -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onPickAvatar(uri)
    }

    AlertDialog(
        onDismissRequest = {
            if (!isCreating && !isUploadingAvatar) onDismiss()
        },
        title = { Text("创建文章分区") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable(enabled = !isCreating && !isUploadingAvatar) {
                                imagePicker.launch("image/*")
                            }
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            Avatar(url = avatarUrl, size = 64.dp)
                        } else {
                            Box(
                                modifier = Modifier.size(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    AppIcons.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("分区头像", fontWeight = FontWeight.Medium)
                        Text(
                            text = when {
                                isUploadingAvatar -> "上传中 ${(uploadProgress * 100).toInt()}%"
                                avatarUrl.isNotBlank() -> "点击更换头像"
                                else -> "点击选择图片"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isUploadingAvatar) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 10) name = it },
                    label = { Text("分区名称（最多10字）") },
                    singleLine = true,
                    enabled = !isCreating && !isUploadingAvatar,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()) },
                enabled = name.isNotBlank() && !isCreating && !isUploadingAvatar
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("创建")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating && !isUploadingAvatar
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun BaList(
    contentPadding: PaddingValues,
    baList: List<BaItem>,
    isLoading: Boolean,
    emptyText: String,
    onBaClick: (BaItem) -> Unit
) {
    if (isLoading && baList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (baList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                emptyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(contentPadding = contentPadding) {
            items(
                items = baList,
                key = { item -> "ba_${item.id}" }
            ) { ba ->
                BaRow(
                    Modifier.animateItem(),
                    ba = ba,
                    onClick = { onBaClick(ba) }
                )
            }
        }
    }
}

@Composable
fun BaRow(
    modifier: Modifier = Modifier,
    ba: BaItem,
    onClick: () -> Unit
) {
    ListItem(
        modifier = modifier,
        onClick = onClick,
        leadingContent = {
            Avatar(url = ba.avatar, size = 48.dp)
        },
        trailingContent = {
            Icon(
                AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = ba.name,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )

            if (ba.memberNum > 0 || ba.postNum > 0) {
                Text(
                    text = "${ba.memberNum} 关注 · ${ba.postNum} 文章",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsList(
    posts: List<PostItem>,
    contentPadding: PaddingValues,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    onRefresh: () -> Unit,
    onLikeClick: (Int) -> Unit,
    onCollectClick: (Int) -> Unit,
    onPostClick: (PostItem) -> Unit,
    onLoadMore: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading && posts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (posts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无文章",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = rememberLazyListState()

            val shouldLoadMore by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val totalItems = layoutInfo.totalItemsCount
                    totalItems > 0 && lastVisible >= totalItems - 3
                }
            }

            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore()
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
                itemsIndexed(
                    items = posts,
                    key = { _, item -> "post_${item.id}" }
                ) { index, post ->
                    PostCard(
                        modifier = Modifier.animateItem(),
                        isTop = index == 0,
                        isBottom = index == posts.lastIndex,
                        post = post,
                        onLikeClick = onLikeClick,
                        onCollectClick = onCollectClick,
                        onPostClick = onPostClick
                    )
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(
    modifier: Modifier = Modifier,
    isTop: Boolean,
    isBottom: Boolean,
    post: PostItem,
    onLikeClick: (Int) -> Unit,
    onCollectClick: (Int) -> Unit,
    onPostClick: (PostItem) -> Unit
) {
    SettingsGroupSurface(isTop = isTop, isBottom = isBottom, onClick = { onPostClick(post) }) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(url = post.senderAvatar, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
    
                Column {
                    Text(
                        text = post.senderNickname,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    Text(
                        text = post.createTimeText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
    
            Spacer(modifier = Modifier.height(10.dp))
    
            Text(
                text = post.title,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
    
            if (post.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = post.content,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
    
            Spacer(modifier = Modifier.height(10.dp))
    
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                InteractionButton(
                    icon = if (post.isLiked == "1") {
                        AppFilledIcons.Favorite
                    } else {
                        AppIcons.Favorite
                    },
                    count = post.likeNum,
                    tint = if (post.isLiked == "1") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { onLikeClick(post.id) }
                )
    
                InteractionButton(
                    icon = AppIcons.ChatBubble,
                    count = post.commentNum,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {}
                )
    
                InteractionButton(
                    icon = if (post.isCollected == 1) {
                        AppIcons.Bookmark
                    } else {
                        AppIcons.BookmarkBorder
                    },
                    count = post.collectNum,
                    tint = if (post.isCollected == 1) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { onCollectClick(post.id) }
                )
            }
        }
    }
}

@Composable
fun InteractionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint
        )

        if (count > 0) {
            Text(
                text = count.toString(),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}