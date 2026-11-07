package com.juhao.murexide.ui.sticker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.StickerPack
import com.juhao.murexide.data.resolveStickerUrl
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.components.*
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StickerPackManagerScreen(
    viewModel: StickerPackManagerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val accountStorage = remember { AccountStorage.getInstance(context) }
    var currentUserId by remember { mutableStateOf("") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var packToRemove by remember { mutableStateOf<StickerPack?>(null) }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        accountStorage.getCurrentUserId()?.let { currentUserId = it }
    }

    val listState = rememberLazyListState()
    val packsState = rememberUpdatedState(state.packs)
    var draggingPackId by remember { mutableStateOf<Long?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var overlayTop by remember { mutableFloatStateOf(0f) }
    var scrollSpeed by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val touchOffsetY = remember { floatArrayOf(0f) }
    val dragIndex = remember { intArrayOf(-1) }
    val dragItemHeight = remember { floatArrayOf(0f) }
    val edgePx = remember(density) { with(density) { 64.dp.toPx() } }
    val maxSpeedPx = remember(density) { with(density) { 900.dp.toPx() } }
    val maxStepPx = remember(density) { with(density) { 36.dp.toPx() } }

    fun clampToViewport(y: Float): Float {
        val layout = listState.layoutInfo
        val top = layout.beforeContentPadding.toFloat()
        val bottom = (layout.viewportEndOffset - layout.afterContentPadding).toFloat()
        return y.coerceIn(top, bottom)
    }

    fun refreshScrollSpeed(y: Float) {
        val layout = listState.layoutInfo
        val top = layout.beforeContentPadding.toFloat()
        val bottom = (layout.viewportEndOffset - layout.afterContentPadding).toFloat()
        if (bottom <= top) {
            scrollSpeed = 0f
            return
        }
        scrollSpeed = when {
            y < top + edgePx && listState.canScrollBackward ->
                -maxSpeedPx * ((top + edgePx - y) / edgePx).coerceIn(0f, 1f)
            y > bottom - edgePx && listState.canScrollForward ->
                maxSpeedPx * ((y - (bottom - edgePx)) / edgePx).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    fun reorderByOffset() {
        val id = draggingPackId ?: return
        val from = dragIndex[0]
        if (from < 0) return
        val height = dragItemHeight[0]
        if (height <= 0f) return
        val steps = (dragOffsetY / height).roundToInt()
        if (steps == 0) return
        val total = packsState.value.size
        val to = (from + steps).coerceIn(0, total - 1)
        if (to == from) {
            dragOffsetY -= steps * height
            return
        }
        viewModel.move(from, to)
        dragIndex[0] = to
        dragOffsetY -= steps * height
    }

    LaunchedEffect(draggingPackId) {
        if (draggingPackId == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val dt = ((frame - last) / 1_000_000_000f).coerceIn(0f, 0.034f)
            last = frame
            val speed = scrollSpeed
            if (speed != 0f) {
                val step = (speed * dt).coerceIn(-maxStepPx, maxStepPx)
                listState.scrollBy(step)
                reorderByOffset()
            }
        }
    }

    StyledScaffold(
        title = { Text("管理表情包") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(AppIcons.Add, contentDescription = "新建表情包")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.packs.isEmpty() -> {
                    Box(
                        Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null && state.packs.isEmpty() -> EmptyPlaceholder(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    icon = AppIcons.Warning,
                    title = "加载失败",
                    subtitle = state.error,
                    primaryButton = {
                        Button(onClick = { viewModel.load() }) { Text("重试") }
                    }
                )

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = state.packs,
                            key = { _, pack -> pack.id }
                        ) { _, pack ->
                            val isDragging = draggingPackId == pack.id
                            StickerPackRow(
                                pack = pack,
                                isDragging = false,
                                dragOffsetY = 0f,
                                onClick = {
                                    if (draggingPackId == null) {
                                        StickerDetailActivity.start(
                                            context = context,
                                            packId = pack.id,
                                            token = viewModel.token,
                                            currentUserId = currentUserId
                                        )
                                    }
                                },
                                onRemove = { packToRemove = pack },
                                modifier = Modifier
                                    .then(
                                        if (!isDragging) Modifier.animateItem()
                                        else Modifier
                                    )
                                    .graphicsLayer { alpha = if (isDragging) 0f else 1f }
                                    .pointerInput(pack.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { start ->
                                                val info = listState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == pack.id }
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val index = packsState.value.indexOfFirst { it.id == pack.id }
                                                if (index < 0) return@detectDragGesturesAfterLongPress
                                                draggingPackId = pack.id
                                                touchOffsetY[0] = start.y
                                                dragItemHeight[0] = info.size.toFloat().coerceAtLeast(1f)
                                                dragIndex[0] = index
                                                dragOffsetY = 0f
                                                fingerY = clampToViewport(info.offset + start.y)
                                                overlayTop = fingerY - touchOffsetY[0]
                                                refreshScrollSpeed(fingerY)
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (draggingPackId != pack.id) {
                                                    return@detectDragGesturesAfterLongPress
                                                }
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                val info = listState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == pack.id }
                                                val rawFingerY = if (info != null) {
                                                    info.offset + change.position.y
                                                } else {
                                                    fingerY + dragAmount.y
                                                }
                                                fingerY = clampToViewport(rawFingerY)
                                                overlayTop = fingerY - touchOffsetY[0]
                                                refreshScrollSpeed(fingerY)
                                                reorderByOffset()
                                            },
                                            onDragEnd = {
                                                scrollSpeed = 0f
                                                draggingPackId = null
                                                dragIndex[0] = -1
                                                dragOffsetY = 0f
                                                viewModel.persistSort()
                                            },
                                            onDragCancel = {
                                                scrollSpeed = 0f
                                                draggingPackId = null
                                                dragIndex[0] = -1
                                                dragOffsetY = 0f
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            val draggingPack = draggingPackId?.let { id ->
                state.packs.firstOrNull { it.id == id }
            }
            if (draggingPack != null) {
                val topPaddingPx = with(density) { innerPadding.calculateTopPadding().toPx() }
                StickerPackRow(
                    pack = draggingPack,
                    isDragging = true,
                    dragOffsetY = 0f,
                    onClick = {},
                    onRemove = {},
                    interactive = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(10f)
                        .offset {
                            IntOffset(0, (overlayTop + topPaddingPx).roundToInt())
                        }
                )
            }
        }
    }

    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建表情包") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("表情包名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPack(newName) {
                            showCreateDialog = false
                        }
                    },
                    enabled = newName.isNotBlank() && !state.isSubmitting
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }

    packToRemove?.let { pack ->
        AlertDialog(
            onDismissRequest = { packToRemove = null },
            title = { Text("移除表情包") },
            text = { Text("确定要从收藏中移除「${pack.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removePack(pack.id)
                    packToRemove = null
                }) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { packToRemove = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerPackRow(
    pack: StickerPack,
    isDragging: Boolean,
    dragOffsetY: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    val context = LocalContext.current
    val coverUrl = pack.stickerItems.firstOrNull()?.url

    val imageRequest = remember(coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl?.let { resolveStickerUrl(it) })
            .apply {
                if (coverUrl?.contains("jwznb.com") == true) {
                    setHeader("Referer", "https://myapp.jwznb.com")
                }
            }
            .build()
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isDragging) 8.dp else 0.dp,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = dragOffsetY }
            .then(
                if (interactive) Modifier.combinedClickable(onClick = onClick)
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.Mood,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${pack.stickerItems.size} 个表情",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (interactive) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = "移除",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Icon(
                imageVector = AppIcons.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}