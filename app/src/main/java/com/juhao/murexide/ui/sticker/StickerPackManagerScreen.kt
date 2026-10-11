package com.juhao.murexide.ui.sticker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.StickerPack
import com.juhao.murexide.data.resolveStickerUrl
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.ui.icons.AppIcons
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
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showCreateDialog by remember { mutableStateOf(false) }
    var packToRemove by remember { mutableStateOf<StickerPack?>(null) }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        accountStorage.getCurrentUserId()?.let { currentUserId = it }
    }

    val listState = rememberLazyListState()
    var draggingPackId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = remember { with(density) { 76.dp.toPx() } }

    val currentPacks by rememberUpdatedState(state.packs)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("管理表情包") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(AppIcons.Add, contentDescription = "新建表情包")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading && state.packs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null && state.packs.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("加载失败", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(state.error ?: "")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.load() }) { Text("重试") }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = state.packs,
                            key = { _, pack -> pack.id }
                        ) { _, pack ->
                            val isDragging = draggingPackId == pack.id

                            val baseModifier = Modifier
                                .zIndex(if (isDragging) 10f else 0f)
                                .then(
                                    if (isDragging) Modifier else Modifier.animateItem()
                                )
                                .pointerInput(pack.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingPackId = pack.id
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y

                                            val currentIndex = currentPacks
                                                .indexOfFirst { it.id == pack.id }
                                            if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                            val steps = (dragOffsetY / itemHeightPx).roundToInt()
                                            if (steps != 0) {
                                                val target = (currentIndex + steps)
                                                    .coerceIn(0, currentPacks.size - 1)
                                                if (target != currentIndex) {
                                                    viewModel.move(currentIndex, target)
                                                    dragOffsetY -= steps * itemHeightPx
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggingPackId = null
                                            dragOffsetY = 0f
                                            viewModel.persistSort()
                                        },
                                        onDragCancel = {
                                            draggingPackId = null
                                            dragOffsetY = 0f
                                        }
                                    )
                                }

                            StickerPackRow(
                                pack = pack,
                                isDragging = isDragging,
                                dragOffsetY = if (isDragging) dragOffsetY else 0f,
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
                                modifier = baseModifier
                            )
                        }
                    }
                }
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
    modifier: Modifier = Modifier
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
            .graphicsLayer {
                translationY = dragOffsetY
            }
            .combinedClickable(onClick = onClick)
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

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = AppIcons.Delete,
                    contentDescription = "移除",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
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