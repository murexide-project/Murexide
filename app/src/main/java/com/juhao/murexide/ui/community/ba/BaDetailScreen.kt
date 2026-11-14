package com.juhao.murexide.ui.community.ba

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.juhao.murexide.data.BaDetail
import com.juhao.murexide.ui.community.PostCard
import com.juhao.murexide.ui.community.detail.PostDetailActivity
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.StyledScaffold
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BaDetailScreen(
    onBackClick: () -> Unit,
    viewModel: BaDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

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

    val thresholdPx = with(density) { 140.dp.toPx() }
    val topBarAlpha by remember {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> (listState.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    val ba = uiState.ba
    var headerAvatarBounds by remember { mutableStateOf<Rect?>(null) }
    var headerTitleBounds by remember { mutableStateOf<Rect?>(null) }
    var topBarAvatarBounds by remember { mutableStateOf<Rect?>(null) }
    var topBarTitleBounds by remember { mutableStateOf<Rect?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        StyledScaffold(
            modifier = Modifier.fillMaxSize(),
            isMediumTopAppBarDisabled = true,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (ba != null && ba.avatar.isNotBlank()) {
                        Avatar(
                            url = ba.avatar,
                            size = 28.dp,
                            alwaysCircle = true,
                            modifier = Modifier
                                .alpha(0f)
                                .onGloballyPositioned {
                                    topBarAvatarBounds = it.boundsInRoot()
                                }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = ba?.name ?: "分区",
                        modifier = Modifier
                            .weight(1f)
                            .alpha(0f)
                            .onGloballyPositioned {
                                topBarTitleBounds = it.boundsInRoot()
                            },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    AutoMirroredIcon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedVisibility(
                        visible = listState.firstVisibleItemIndex > 0,
                        enter = fadeIn() + scaleIn(initialScale = 0.85f),
                        exit = fadeOut() + scaleOut(targetScale = 0.85f)
                    ) {
                        Surface(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.ArrowUpward,
                                    contentDescription = "回到顶部",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    ba?.let { b ->
                        Surface(
                            onClick = { CreatePostActivity.startCreate(context, b.id, b.name) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.Edit,
                                    contentDescription = "发帖",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End
        ) { padding ->
            when {
                uiState.isLoadingInfo && ba == null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding()),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }

                ba == null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            uiState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = padding
                    ) {
                        item(key = "ba_header") {
                            BaHeader(
                                ba = ba,
                                onAvatarBoundsChanged = { headerAvatarBounds = it },
                                onTitleBoundsChanged = { headerTitleBounds = it },
                                onFollowClick = { viewModel.toggleFollow() }
                            )
                            HorizontalDivider()
                        }

                        if (uiState.posts.isEmpty() && !uiState.isLoadingPosts) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "暂无文章",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                    ContainedLoadingIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        val t = topBarAlpha
        val tbA = topBarAvatarBounds
        val avatarRect: Rect? = when {
            ba == null || ba.avatar.isBlank() -> null
            tbA == null || tbA.isEmpty -> null
            t >= 1f -> tbA
            else -> {
                val h = headerAvatarBounds
                if (h == null || h.isEmpty) tbA
                else lerpRectCenter(h, tbA, t)
            }
        }

        if (avatarRect != null) {
            Avatar(
                url = ba!!.avatar,
                size = with(density) { avatarRect.width.toDp() },
                alwaysCircle = true,
                modifier = Modifier.offset(
                    x = with(density) { avatarRect.left.toDp() },
                    y = with(density) { avatarRect.top.toDp() }
                )
            )
        }

        val tbT = topBarTitleBounds
        val titleRect: Rect? = when {
            ba == null -> null
            tbT == null || tbT.isEmpty -> null
            t >= 1f -> tbT
            else -> {
                val h = headerTitleBounds
                if (h == null || h.isEmpty) tbT
                else lerpRectCenter(h, tbT, t)
            }
        }

        if (titleRect != null && ba != null) {
            val fontSize = lerp(20f, 16f, t).sp
            Text(
                text = ba.name,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .offset(
                        x = with(density) { titleRect.left.toDp() },
                        y = with(density) { titleRect.top.toDp() }
                    )
                    .width(with(density) { titleRect.width.toDp() })
            )
        }
    }
}

private fun lerpRectCenter(a: Rect, b: Rect, t: Float): Rect {
    val cx = lerp(a.center.x, b.center.x, t)
    val cy = lerp(a.center.y, b.center.y, t)
    val w = lerp(a.width, b.width, t)
    val h = lerp(a.height, b.height, t)
    return Rect(
        left = cx - w / 2f,
        top = cy - h / 2f,
        right = cx + w / 2f,
        bottom = cy + h / 2f
    )
}

@Composable
private fun BaHeader(
    ba: BaDetail,
    onAvatarBoundsChanged: (Rect) -> Unit,
    onTitleBoundsChanged: (Rect) -> Unit,
    onFollowClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                url = ba.avatar,
                size = 60.dp,
                alwaysCircle = true,
                modifier = Modifier
                    .alpha(0f)
                    .onGloballyPositioned { onAvatarBoundsChanged(it.boundsInRoot()) }
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ba.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .alpha(0f)
                        .onGloballyPositioned { onTitleBoundsChanged(it.boundsInRoot()) }
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