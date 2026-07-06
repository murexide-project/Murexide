package com.juhao.murexide.ui.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.data.PostItem
import com.juhao.murexide.data.BaItem
import com.juhao.murexide.ui.components.Avatar

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

    val currentBaId = uiState.currentBaId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("社区") },
                actions = {
                    IconButton(onClick = { /* TODO: 搜索 */ }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: 发布文章 */ }
            ) {
                Icon(Icons.Default.Add, contentDescription = "发布")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 分区选择栏
            BaSelector(
                baList = uiState.baList,
                isLoading = uiState.isLoadingBa,
                currentBaId = currentBaId,
                onBaSelected = { baId ->
                    viewModel.selectBa(baId)
                }
            )

            // 文章列表
            PostsList(
                posts = uiState.posts,
                isLoading = uiState.isLoadingPosts,
                onLikeClick = { postId ->
                    viewModel.toggleLike(postId)
                },
                onCollectClick = { postId ->
                    viewModel.toggleCollect(postId)
                },
                onPostClick = { post ->
                    // TODO: 跳转到文章详情
                },
                onRefresh = {
                    viewModel.refresh()
                }
            )
        }
    }
}

@Composable
fun BaSelector(
    baList: List<BaItem>,
    isLoading: Boolean,
    onBaSelected: (Int) -> Unit,
    currentBaId: Int,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        // 推荐选项
        item {
            FilterChip(
                selected = currentBaId == 0,
                onClick = { onBaSelected(0) },
                label = { Text("推荐") }
            )
        }

        items(baList) { ba ->
            FilterChip(
                selected = currentBaId == ba.id,
                onClick = { onBaSelected(ba.id) },
                label = { Text(ba.name) },
                leadingIcon = {
                    Avatar(
                        url = ba.avatar,
                        size = 20.dp
                    )
                }
            )
        }

        if (isLoading) {
            item {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsList(
    posts: List<PostItem>,
    isLoading: Boolean,
    onLikeClick: (Int) -> Unit,
    onCollectClick: (Int) -> Unit,
    onPostClick: (PostItem) -> Unit,
    onRefresh: () -> Unit
) {
    if (isLoading && posts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (posts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无文章", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(posts) { post ->
                PostCard(
                    post = post,
                    onLikeClick = onLikeClick,
                    onCollectClick = onCollectClick,
                    onPostClick = onPostClick
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
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

@Composable
fun PostCard(
    post: PostItem,
    onLikeClick: (Int) -> Unit,
    onCollectClick: (Int) -> Unit,
    onPostClick: (PostItem) -> Unit
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPostClick(post) }
                .padding(horizontal = 16.dp)
                .padding(16.dp)
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
                    icon = if (post.isLiked == "1") Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    count = post.likeNum,
                    tint = if (post.isLiked == "1") Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onLikeClick(post.id) }
                )
    
                // 评论
                InteractionButton(
                    icon = Icons.Default.ChatBubbleOutline,
                    count = post.commentNum,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { /* TODO: 打开评论 */ }
                )
    
                // 收藏
                InteractionButton(
                    icon = if (post.isCollected == 1) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    count = post.collectNum,
                    tint = if (post.isCollected == 1) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onCollectClick(post.id) }
                )
            }
        }
        
        HorizontalDivider()
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .clickable { onClick() },
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