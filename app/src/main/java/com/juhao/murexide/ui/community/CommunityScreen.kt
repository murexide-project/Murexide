package com.juhao.murexide.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import kotlinx.coroutines.launch

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
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
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
    val selectedIndex = tabs.indexOfFirst { it.first == uiState.currentTab }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
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
                                    contentDescription = if (uiState.sort == CommunitySort.RECOMMEND) "推荐" else "最新"
                                )
                            }
                        }
                        IconButton(onClick = {
                            scope.launch { searchBarState.animateToExpanded() }
                        }) {
                            Icon(AppIcons.Search, contentDescription = "搜索")
                        }
                    }
                )
                PrimaryTabRow(selectedTabIndex = selectedIndex) {
                    tabs.forEachIndexed { index, (tab, label) ->
                        Tab(
                            selected = selectedIndex == index,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            when (uiState.currentTab) {
                CommunityTab.RECOMMEND -> {
                    if (uiState.sort == CommunitySort.RECOMMEND) {
                        PostsList(
                            posts = uiState.posts,
                            innerPadding = innerPadding,
                            isLoading = uiState.isLoadingPosts,
                            isRefreshing = uiState.isRefreshingPosts,
                            isLoadingMore = uiState.isLoadingMore,
                            onRefresh = viewModel::loadRecommend,
                            onLikeClick = viewModel::toggleLike,
                            onCollectClick = viewModel::toggleCollect,
                            onPostClick = { post -> PostDetailActivity.start(context, post.id) },
                            onLoadMore = viewModel::loadMorePosts
                        )
                    } else {
                        PostsList(
                            posts = uiState.latestPosts,
                            innerPadding = innerPadding,
                            isLoading = uiState.isLatestLoading,
                            isRefreshing = uiState.isLatestRefreshing,
                            isLoadingMore = uiState.isLatestLoadingMore,
                            onRefresh = viewModel::loadLatest,
                            onLikeClick = viewModel::toggleLike,
                            onCollectClick = viewModel::toggleCollect,
                            onPostClick = { post -> PostDetailActivity.start(context, post.id) },
                            onLoadMore = viewModel::loadMoreLatest
                        )
                    }
                }

                CommunityTab.ALL_BA -> AllBaContent(
                    innerPadding = innerPadding,
                    currentSide = uiState.currentBaSide,
                    onSideSelected = viewModel::selectBaSide,
                    baList = uiState.allBaList,
                    isLoading = uiState.isLoadingAllBa,
                    onBaClick = { ba -> BaDetailActivity.start(context, ba.id) }
                )

                CommunityTab.MANAGE -> ManageContent(
                    innerPadding = innerPadding,
                    baList = uiState.manageBaList,
                    isLoading = uiState.isLoadingManageBa,
                    onMyPostsClick = { MyPostsActivity.start(context) },
                    onBaClick = { ba -> BaDetailActivity.start(context, ba.id) }
                )
            }
        }
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
            ) { CircularProgressIndicator() }

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
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchState.results, key = { "search_${it.id}" }) { post ->
                    PostCard(
                        modifier = Modifier.animateItem(),
                        post = post,
                        onLikeClick = viewModel::toggleLike,
                        onCollectClick = viewModel::toggleCollect,
                        onPostClick = { PostDetailActivity.start(context, it.id) }
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
    innerPadding: PaddingValues,
    currentSide: BaSide,
    onSideSelected: (BaSide) -> Unit,
    baList: List<BaItem>,
    isLoading: Boolean,
    onBaClick: (BaItem) -> Unit
) {
    val sides = listOf(
        BaSide.OFFICIAL to "官方",
        BaSide.USER to "用户自建",
        BaSide.SUBSCRIBE to "关注",
    )
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(96.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            sides.forEach { (side, label) ->
                val selected = currentSide == side
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSideSelected(side) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                        )
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BaList(
                innerPadding = innerPadding,
                baList = baList,
                isLoading = isLoading,
                emptyText = if (currentSide == BaSide.USER) "暂无用户自建分区" else "暂无分区",
                onBaClick = onBaClick
            )
        }
    }
}

@Composable
fun ManageContent(
    innerPadding: PaddingValues,
    baList: List<BaItem>,
    isLoading: Boolean,
    onMyPostsClick: () -> Unit,
    onBaClick: (BaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = innerPadding.calculateBottomPadding()
        )
    ) {
        item {
            SettingsGroupHeader("管理")
        }
        item {
            SettingsGroupSurface(index = 0, count = 1) {
                SettingsItem(
                    icon = AppIcons.Article,
                    title = "我的文章",
                    subtitle = "查看我发布的全部文章",
                    onClick = onMyPostsClick
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
                SettingsGroupSurface(index = index, count = baList.size) {
                    BaRow(ba = ba, onClick = { onBaClick(ba) })
                }
            }
        }
    }
}

@Composable
fun BaList(
    innerPadding: PaddingValues,
    baList: List<BaItem>,
    isLoading: Boolean,
    emptyText: String,
    onBaClick: (BaItem) -> Unit
) {
    if (isLoading && baList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (baList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())) {
            items(
                items = baList,
                key = { item -> "ba_${item.id}" }
            ) { ba ->
                BaRow(Modifier.animateItem(), ba = ba, onClick = { onBaClick(ba) })
            }
        }
    }
}

@Composable
fun BaRow(modifier: Modifier = Modifier, ba: BaItem, onClick: () -> Unit) {
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
    innerPadding: PaddingValues,
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
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (posts.isEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无文章", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
            ) {
                items(
                    items = posts,
                    key = { item -> "post_${item.id}" }
                ) { post ->
                    PostCard(
                        modifier = Modifier.animateItem(),
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
    post: PostItem,
    onLikeClick: (Int) -> Unit,
    onCollectClick: (Int) -> Unit,
    onPostClick: (PostItem) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onPostClick(post) }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
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
                icon = if (post.isLiked == "1") AppIcons.Favorite else AppIcons.FavoriteBorder,
                count = post.likeNum,
                tint = if (post.isLiked == "1") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onLikeClick(post.id) }
            )

            InteractionButton(
                icon = AppIcons.ChatBubble,
                count = post.commentNum,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { }
            )

            InteractionButton(
                icon = if (post.isCollected == 1) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                count = post.collectNum,
                tint = if (post.isCollected == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onCollectClick(post.id) }
            )
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