package com.juhao.murexide.ui.chat.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.ExpressionItem
import com.juhao.murexide.data.StickerItem
import com.juhao.murexide.data.StickerPack
import com.juhao.murexide.data.resolveStickerUrl
import com.juhao.murexide.ui.icons.AppIcons
import kotlinx.coroutines.launch

private val PanelMinHeight = 280.dp

@Composable
private fun rememberStaticImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context).components { }.build()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPanel(
    expressions: List<ExpressionItem>,
    stickerPacks: List<StickerPack>,
    isLoading: Boolean,
    onExpressionClick: (ExpressionItem) -> Unit,
    onStickerItemClick: (StickerItem) -> Unit,
    onEmojiTextSelected: (String) -> Unit = {},
    onStickerPackHeaderClick: (StickerPack) -> Unit = {},
    onDeleteExpression: (Int) -> Unit = {},
    onTopExpression: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    minHeight: Dp = PanelMinHeight,
    maxHeight: Dp? = null,
    onManageStickerPacks: () -> Unit = {}
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val minPx = with(density) { minHeight.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val autoMaxPx = screenHeightPx * 0.75f
    val maxPx = maxHeight?.let { with(density) { it.toPx() } } ?: autoMaxPx
    val safeMaxPx = maxPx.coerceAtLeast(minPx * 1.5f)

    var panelHeightPx by remember { mutableFloatStateOf(minPx) }
    var listAtTop by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    val tabCount = remember(stickerPacks) { 2 + stickerPacks.size }
    val pagerState = rememberPagerState(pageCount = { tabCount })

    val nestedScroll = remember(minPx, safeMaxPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta <= 0f) return Offset.Zero
                if (!listAtTop) return Offset.Zero
                if (panelHeightPx <= minPx) return Offset.Zero
                val newHeight = (panelHeightPx - delta).coerceAtLeast(minPx)
                val consumed = panelHeightPx - newHeight
                panelHeightPx = newHeight
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                if (delta >= 0f) return Offset.Zero
                if (panelHeightPx >= safeMaxPx) return Offset.Zero
                val newHeight = (panelHeightPx - delta).coerceAtMost(safeMaxPx)
                val consumedByPanel = newHeight - panelHeightPx
                panelHeightPx = newHeight
                return Offset(0f, consumedByPanel)
            }
        }
    }

    val handleDraggableState = rememberDraggableState { delta ->
        panelHeightPx = (panelHeightPx - delta).coerceIn(minPx, safeMaxPx)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { panelHeightPx.toDp() })
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .draggable(
                    state = handleDraggableState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        scope.launch {
                            val target = when {
                                velocity < -1200f -> safeMaxPx
                                velocity > 1200f -> minPx
                                panelHeightPx < minPx -> minPx
                                panelHeightPx > safeMaxPx -> safeMaxPx
                                else -> panelHeightPx
                            }
                            animate(
                                initialValue = panelHeightPx,
                                targetValue = target,
                                animationSpec = tween(260)
                            ) { value, _ ->
                                panelHeightPx = value
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 4.dp,
                containerColor = Color.Transparent,
                minTabWidth = 56.dp,
                modifier = Modifier.weight(1f)
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    modifier = Modifier.requiredWidth(56.dp),
                    icon = {
                        Text(text = "😀", fontSize = 20.sp)
                    }
                )

                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    modifier = Modifier.requiredWidth(56.dp),
                    icon = {
                        Icon(
                            imageVector = AppIcons.Favorite,
                            contentDescription = "收藏",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )

                stickerPacks.forEachIndexed { index, pack ->
                    val page = index + 2
                    Tab(
                        selected = pagerState.currentPage == page,
                        onClick = { scope.launch { pagerState.animateScrollToPage(page) } },
                        modifier = Modifier.requiredWidth(56.dp),
                        icon = {
                            StickerPackTabIcon(pack = pack)
                        }
                    )
                }
            }

            IconButton(onClick = onManageStickerPacks) {
                Icon(
                    imageVector = AppIcons.Settings,
                    contentDescription = "管理表情包",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(nestedScroll)
        ) { page ->
            when {
                isLoading && expressions.isEmpty() && stickerPacks.isEmpty() && page == 1 -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                page == 0 -> {
                    SystemEmojiGridPage(
                        onEmojiSelected = onEmojiTextSelected,
                        onAtTopChanged = { listAtTop = it }
                    )
                }

                page == 1 -> {
                    ExpressionGridPage(
                        expressions = expressions,
                        onItemClick = onExpressionClick,
                        onDeleteExpression = onDeleteExpression,
                        onTopExpression = onTopExpression,
                        onAtTopChanged = { listAtTop = it }
                    )
                }

                else -> {
                    val packIndex = page - 2
                    if (packIndex in stickerPacks.indices) {
                        StickerPackGridPage(
                            pack = stickerPacks[packIndex],
                            onItemClick = onStickerItemClick,
                            onHeaderClick = onStickerPackHeaderClick,
                            onAtTopChanged = { listAtTop = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerPackTabIcon(pack: StickerPack) {
    val context = LocalContext.current
    val staticImageLoader = rememberStaticImageLoader()
    val coverUrl = pack.stickerItems.firstOrNull()?.url

    if (coverUrl.isNullOrBlank()) {
        Icon(
            imageVector = AppIcons.Mood,
            contentDescription = pack.name,
            modifier = Modifier.size(22.dp)
        )
        return
    }

    val imageRequest = remember(coverUrl) {
        ImageRequest.Builder(context)
            .data(resolveStickerUrl(coverUrl))
            .apply {
                if (coverUrl.contains("jwznb.com")) {
                    setHeader("Referer", "https://myapp.jwznb.com")
                }
            }
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        imageLoader = staticImageLoader,
        contentDescription = pack.name,
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun SystemEmojiGridPage(
    onEmojiSelected: (String) -> Unit,
    onAtTopChanged: (Boolean) -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { !gridState.canScrollBackward }.collect(onAtTopChanged)
    }

    val emojis = EmojiData.UNICODE_EMOJIS

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = emojis.size,
            key = { index -> "emoji_$index" },
            contentType = { "emoji" }
        ) { index ->
            val emoji = emojis[index]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onEmojiSelected(emoji) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 26.sp
                )
            }
        }
    }
}

@Composable
private fun ExpressionGridPage(
    expressions: List<ExpressionItem>,
    onItemClick: (ExpressionItem) -> Unit,
    onDeleteExpression: (Int) -> Unit,
    onTopExpression: (Int) -> Unit,
    onAtTopChanged: (Boolean) -> Unit
) {
    if (expressions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无收藏表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        return
    }

    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { !gridState.canScrollBackward }.collect(onAtTopChanged)
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = expressions.size,
            key = { index -> "expr_${expressions[index].id}" },
            contentType = { "expr" }
        ) { index ->
            val item = expressions[index]
            var showMenu by remember { mutableStateOf(false) }

            Box {
                EmojiGridItem(
                    url = resolveStickerUrl(item.url),
                    onClick = { onItemClick(item) },
                    onLongClick = { showMenu = true }
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("置顶") },
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.KeyboardArrowUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onTopExpression(item.id.toInt())
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("取消收藏") },
                        leadingIcon = {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDeleteExpression(item.id.toInt())
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerPackGridPage(
    pack: StickerPack,
    onItemClick: (StickerItem) -> Unit,
    onHeaderClick: (StickerPack) -> Unit,
    onAtTopChanged: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onHeaderClick(pack) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pack.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = AppIcons.KeyboardArrowRight,
                contentDescription = "查看表情包详情",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (pack.stickerItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无表情",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            val gridState = rememberLazyGridState()

            LaunchedEffect(gridState) {
                snapshotFlow { !gridState.canScrollBackward }.collect(onAtTopChanged)
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    count = pack.stickerItems.size,
                    key = { index -> "sticker_${pack.stickerItems[index].id}" },
                    contentType = { "sticker" }
                ) { index ->
                    val item = pack.stickerItems[index]
                    EmojiGridItem(
                        url = resolveStickerUrl(item.url),
                        name = item.name,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiGridItem(
    url: String?,
    name: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val staticImageLoader = rememberStaticImageLoader()
    val imageRequest = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (url?.contains("jwznb.com") == true) {
                    setHeader("Referer", "https://myapp.jwznb.com")
                }
            }
            .crossfade(true)
            .build()
    }

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.combinedClickable(onClick = onClick)
    }

    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .then(clickModifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = imageRequest,
            imageLoader = staticImageLoader,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            contentScale = ContentScale.Fit
        )
        name?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}