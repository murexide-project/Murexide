package com.juhao.murexide.ui.community.ba

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.data.BaDetail
import com.juhao.murexide.ui.community.PostCard
import com.juhao.murexide.ui.community.detail.PostDetailActivity
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.StyledIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaDetailScreen(
    onBackClick: () -> Unit,
    viewModel: BaDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.ba?.name ?: "分区") },
                navigationIcon = {
                    StyledIconButton(onClick = onBackClick) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            val baId = uiState.ba?.id
            if (baId != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        CreatePostActivity.startCreate(context, baId, uiState.ba?.name ?: "")
                    },
                    icon = { Icon(AppIcons.Edit, contentDescription = null) },
                    text = { Text("发帖") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoadingInfo && uiState.ba == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.ba == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item(key = "ba_header") {
                            BaHeader(ba = uiState.ba!!, onFollowClick = { viewModel.toggleFollow() })
                            HorizontalDivider()
                        }

                        if (uiState.posts.isEmpty() && !uiState.isLoadingPosts) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("暂无文章", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        items(uiState.posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                onLikeClick = viewModel::toggleLike,
                                onCollectClick = viewModel::toggleCollect,
                                onPostClick = { PostDetailActivity.start(context, post.id) }
                            )
                        }

                        if (uiState.isLoadingPosts || uiState.isLoadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BaHeader(ba: BaDetail, onFollowClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = ba.avatar, size = 60.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ba.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${ba.memberNum} 关注 · ${ba.postNum} 文章 · ${ba.groupNum} 群聊",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        val isFollowed = ba.isFollowed == "1"
        if (isFollowed) {
            OutlinedButton(
                onClick = onFollowClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("已关注")
            }
        } else {
            Button(
                onClick = onFollowClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("关注")
            }
        }
    }
}
