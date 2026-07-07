package com.juhao.murexide.ui.community.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.data.CommentItem
import com.juhao.murexide.data.PostDetail
import com.juhao.murexide.data.GroupInfo
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.community.InteractionButton
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.MarkdownText
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun PostDetailScreen(
    onBackClick: () -> Unit,
    viewModel: PostDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.hasMoreComments && !uiState.isLoadingComments) {
            viewModel.loadMoreComments()
        }
    }
    
    val hazeState = remember { HazeState() }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin().copy(
                                noiseFactor = 0f
                            ),
                            block = null
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )
                
                TopAppBar(
                    title = { 
                        uiState.post?.let { post ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(url = post.senderAvatar, size = 36.dp)
                                
                                Spacer(Modifier.width(8.dp))
                                
                                Column {
                                    Text(
                                        post.senderNickname,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        post.createTimeText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val post = uiState.post
        Box(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState)
        ) {
            when {
                uiState.isLoadingDetail && post == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
    
                post == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            uiState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
    
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
                        }
                    
                        item { PostHeader(post = post, viewModel = viewModel) }
    
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "评论 ${uiState.commentTotal}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
    
                        if (uiState.comments.isEmpty() && !uiState.isLoadingComments) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("暂无评论", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
    
                        items(uiState.comments, key = { it.id }) { comment ->
                            CommentRow(comment)
                        }
    
                        if (uiState.isLoadingComments) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
private fun PostHeader(post: PostDetail, viewModel: PostDetailViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(post.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)

        Spacer(Modifier.height(12.dp))

        if (post.contentType == 2) {
            MarkdownText(markdown = post.content, modifier = Modifier.fillMaxWidth())
        } else {
            Text(post.content, fontSize = 15.sp, lineHeight = 22.sp)
        }

        post.group?.takeIf { it.groupId.isNotEmpty() }?.let { group ->
            Spacer(Modifier.height(12.dp))
            GroupCard(group)
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            InteractionButton(
                icon = if (post.isLiked == 1) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                count = post.likeNum,
                tint = if (post.isLiked == 1) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { viewModel.toggleLike() }
            )
            InteractionButton(
                icon = Icons.Rounded.ChatBubbleOutline,
                count = post.commentNum,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { }
            )
            InteractionButton(
                icon = if (post.isCollected == 1) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                count = post.collectNum,
                tint = if (post.isCollected == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { viewModel.toggleCollect() }
            )
        }
    }
}

@Composable
private fun GroupCard(group: GroupInfo) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                ChatActivity.start(
                    context = context,
                    chatId = group.groupId,
                    chatType = 2,
                    chatName = group.name,
                    chatAvatar = group.avatarUrl
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(url = group.avatarUrl, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "${group.headcount} 人",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommentRow(comment: CommentItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Avatar(url = comment.senderAvatar, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                comment.senderNickname,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                comment.createTimeText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(comment.content, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (comment.likeNum > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (comment.isLiked == "1") Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (comment.isLiked == "1") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    comment.likeNum.toString(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
