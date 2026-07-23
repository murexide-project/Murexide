package com.juhao.murexide.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.ui.theme.UiState
import com.juhao.murexide.data.PostItem
import com.juhao.murexide.data.BaItem
import com.juhao.murexide.ui.community.ba.BaDetailActivity
import com.juhao.murexide.ui.community.detail.PostDetailActivity
import com.juhao.murexide.ui.community.myposts.MyPostsActivity
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.SettingsGroup
import com.juhao.murexide.ui.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    token: String,
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
    val context = androidx.compose.ui.platform.LocalContext.current

    val tabs = listOf(
        CommunityTab.RECOMMEND to "推荐",
        CommunityTab.ALL_BA to "全部分区",
        CommunityTab.MANAGE to "管理"
    )
    val selectedIndex = tabs.indexOfFirst { it.first == uiState.currentTab }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("社区") },
                    actions = {
                        IconButton(onClick = { /* TODO: 搜索 */ }) {
                            Icon(Icons.Rounded.Search, contentDescription = "搜索")
                        }
                    }
                )
                SecondaryTabRow(selectedTabIndex = selectedIndex) {
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
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                CommunityTab.RECOMMEND -> PostsList(
                    posts = uiState.posts,
                    isLoading = uiState.isLoadingPosts,
                    isRefreshing = uiState.isRefreshingPosts,
                    isLoadingMore = uiState.isLoadingMore,
                    onRefresh = viewModel::loadRecommend,
                    onLikeClick = viewModel::toggleLike,
                    onCollectClick = viewModel::toggleCollect,
                    onPostClick = { post -> PostDetailActivity.start(context, post.id) },
                    onLoadMore = viewModel::loadMorePosts
                )

                CommunityTab.ALL_BA -> AllBaContent(
                    currentSide = uiState.currentBaSide,
                    onSideSelected = viewModel::selectBaSide,
                    baList = uiState.allBaList,
                    isLoading = uiState.isLoadingAllBa,
                    onBaClick = { ba -> BaDetailActivity.start(context, ba.id) }
                )

                CommunityTab.MANAGE -> ManageContent(
                    baList = uiState.manageBaList,
                    isLoading = uiState.isLoadingManageBa,
                    onMyPostsClick = { MyPostsActivity.start(context) },
                    onBaClick = { ba -> BaDetailActivity.start(context, ba.id) }
                )
            }
        }
    }
}

@Composable
fun AllBaContent(
    currentSide: BaSide,
    onSideSelected: (BaSide) -> Unit,
    baList: List<BaItem>,
    isLoading: Boolean,
    onBaClick: (BaItem) -> Unit
) {
    val sides = listOf(
        BaSide.OFFICIAL to "官方",
        BaSide.USER to "用户自建"
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
    baList: List<BaItem>,
    isLoading: Boolean,
    onMyPostsClick: () -> Unit,
    onBaClick: (BaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            SettingsGroup {
                SettingsItem(
                    icon = Icons.AutoMirrored.Rounded.Article,
                    title = "我的文章",
                    subtitle = "查看我发布的全部文章",
                    onClick = onMyPostsClick
                )
            }
        }

        item {
            SettingsGroup(title = "我的分区") {
                when {
                    isLoading && baList.isEmpty() -> {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    baList.isEmpty() -> {
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

                    else -> {
                        baList.forEach { ba ->
                            SettingsItem(
                                title = ba.name,
                                subtitle = if (ba.postNum > 0) "${ba.postNum} 文章" else null,
                                onClick = { onBaClick(ba) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BaList(
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
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(baList) { ba ->
                BaRow(ba = ba, onClick = { onBaClick(ba) })
            }
        }
    }
}

@Composable
fun BaRow(ba: BaItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = ba.avatar, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(ba.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            if (ba.memberNum > 0 || ba.postNum > 0) {
                Text(
                    "${ba.memberNum} 关注 · ${ba.postNum} 文章",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsList(
    posts: List<PostItem>,
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

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(posts) { post ->
                    PostCard(
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: PostItem,
    onLikeClick: (Int) -> Unit,
    onCollectClick: (Int) -> Unit,
    onPostClick: (PostItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onPostClick(post) }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 作者信息
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                url = post.senderAvatar,
                size = 36.dp
            )

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

        // 标题
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

        // 互动按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 点赞
            InteractionButton(
                icon = if (post.isLiked == "1") Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                count = post.likeNum,
                tint = if (post.isLiked == "1") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onLikeClick(post.id) }
            )

            // 评论
            InteractionButton(
                icon = Icons.Rounded.ChatBubbleOutline,
                count = post.commentNum,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { /* TODO: 打开评论 */ }
            )

            // 收藏
            InteractionButton(
                icon = if (post.isCollected == 1) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
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
