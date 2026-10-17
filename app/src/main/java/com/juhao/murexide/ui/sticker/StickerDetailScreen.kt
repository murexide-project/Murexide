package com.juhao.murexide.ui.sticker

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.StickerItem
import com.juhao.murexide.data.StickerPack
import com.juhao.murexide.data.resolveStickerUrl
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.components.ExpressiveDropdownMenu
import com.juhao.murexide.ui.components.fullImagePreviewItem
import com.juhao.murexide.ui.components.showImageViewer

private const val GRID_COLUMNS = 4

private val PreviewInitialHeight = 320.dp
private val PreviewMinHeight = 160.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerDetailScreen(
    viewModel: StickerDetailViewModel,
    previewStickerUrl: String? = null,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val pendingUpload by viewModel.pendingUpload.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current

    var showRenamePackDialog by remember { mutableStateOf(false) }
    var showDeletePackDialog by remember { mutableStateOf(false) }
    var stickerToRename by remember { mutableStateOf<StickerItem?>(null) }
    var stickerToDelete by remember { mutableStateOf<StickerItem?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var stickerNameInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(pendingUpload) {
        pendingUpload?.let { stickerNameInput = it.suggestedName }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadSticker(it, context) }
    }

    val hasPreview = !previewStickerUrl.isNullOrBlank()

    val maxHeightPx = with(density) { PreviewInitialHeight.toPx() }
    val minHeightPx = with(density) { PreviewMinHeight.toPx() }
    val previewHeightPx = remember { mutableFloatStateOf(maxHeightPx) }

    LaunchedEffect(hasPreview, maxHeightPx) {
        previewHeightPx.floatValue = if (hasPreview) maxHeightPx else 0f
    }

    val nestedScrollConnection = remember(hasPreview, maxHeightPx, minHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!hasPreview) return Offset.Zero
                val delta = available.y
                if (delta >= 0f) return Offset.Zero

                val current = previewHeightPx.floatValue
                if (current <= minHeightPx) return Offset.Zero

                val target = (current + delta).coerceAtLeast(minHeightPx)
                val consumed = target - current
                previewHeightPx.floatValue = target
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!hasPreview) return Offset.Zero
                val delta = available.y
                if (delta <= 0f) return Offset.Zero

                val current = previewHeightPx.floatValue
                if (current >= maxHeightPx) return Offset.Zero

                val target = (current + delta).coerceAtMost(maxHeightPx)
                val consumedByPreview = target - current
                previewHeightPx.floatValue = target
                return Offset(0f, consumedByPreview)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.pack?.name ?: "表情包详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isOwner) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(AppIcons.MoreVert, contentDescription = "更多")
                            }
                            ExpressiveDropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("重命名表情包") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = AppIcons.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showRenamePackDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("添加表情") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = AppIcons.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "删除表情包",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = AppIcons.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeletePackDialog = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(Modifier.fillMaxSize()) {

                AnimatedVisibility(
                    visible = uploadProgress != null,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    UploadProgressBar(progress = uploadProgress ?: 0f)
                }

                when {
                    state.isLoading && state.pack == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    state.error != null && state.pack == null -> {
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
                        val pack = state.pack ?: return@Column
                        val previewUrl = previewStickerUrl?.takeIf { it.isNotBlank() }
                        val rows = remember(pack.stickerItems) {
                            pack.stickerItems.chunked(GRID_COLUMNS)
                        }

                        val currentPreviewHeight: Dp = with(density) {
                            previewHeightPx.floatValue.toDp()
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (previewUrl != null) {
                                item(key = "preview") {
                                    StickerPreviewHeader(
                                        url = previewUrl,
                                        height = currentPreviewHeight
                                    )
                                }
                            }

                            item(key = "header") {
                                StickerPackHeader(
                                    pack = pack,
                                    creatorName = state.creator?.nickname ?: "",
                                    creatorAvatar = state.creator?.avatar_url ?: "",
                                    isCollected = state.isCollected,
                                    isOwner = state.isOwner,
                                    isSubmitting = state.isSubmitting,
                                    onToggleCollect = { viewModel.toggleCollect() }
                                )
                            }

                            item(key = "divider") {
                                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
                            }

                            itemsIndexed(
                                items = rows,
                                key = { index, _ -> "row_$index" }
                            ) { _, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    row.forEach { sticker ->
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            StickerDetailItem(
                                                sticker = sticker,
                                                isOwner = state.isOwner,
                                                onRename = { stickerToRename = sticker },
                                                onDelete = { stickerToDelete = sticker },
                                                onAddToFavorites = { viewModel.addStickerToFavorites(sticker.id) }
                                            )
                                        }
                                    }

                                    repeat(GRID_COLUMNS - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingUpload != null) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isSubmitting) {
                    viewModel.cancelPendingUpload()
                    stickerNameInput = ""
                }
            },
            title = { Text("为表情命名") },
            text = {
                OutlinedTextField(
                    value = stickerNameInput,
                    onValueChange = { stickerNameInput = it },
                    label = { Text("表情名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = stickerNameInput.trim()
                        if (name.isNotEmpty()) {
                            viewModel.confirmAddSticker(name)
                            stickerNameInput = ""
                        }
                    },
                    enabled = stickerNameInput.isNotBlank() && !state.isSubmitting
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("确定")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelPendingUpload()
                        stickerNameInput = ""
                    },
                    enabled = !state.isSubmitting
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showRenamePackDialog) {
        var newName by remember { mutableStateOf(state.pack?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenamePackDialog = false },
            title = { Text("重命名表情包") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renamePack(newName)
                    showRenamePackDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRenamePackDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDeletePackDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePackDialog = false },
            title = { Text("删除表情包") },
            text = { Text("确定要删除「${state.pack?.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePack { onBack() }
                    showDeletePackDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePackDialog = false }) { Text("取消") }
            }
        )
    }

    stickerToRename?.let { sticker ->
        var newName by remember { mutableStateOf(sticker.name) }
        AlertDialog(
            onDismissRequest = { stickerToRename = null },
            title = { Text("重命名表情") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameSticker(sticker.id, newName)
                    stickerToRename = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { stickerToRename = null }) { Text("取消") }
            }
        )
    }

    stickerToDelete?.let { sticker ->
        AlertDialog(
            onDismissRequest = { stickerToDelete = null },
            title = { Text("删除表情") },
            text = { Text("确定要删除「${sticker.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSticker(sticker.id)
                    stickerToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { stickerToDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun UploadProgressBar(progress: Float) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "上传中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerPreviewHeader(
    url: String,
    height: Dp
) {
    val context = LocalContext.current
    val resolvedUrl = remember(url) { resolveStickerUrl(url) }
    val imageRequest = remember(resolvedUrl) {
        ImageRequest.Builder(context)
            .data(resolvedUrl)
            .apply {
                if (url.contains("jwznb.com")) {
                    setHeader("Referer", "https://myapp.jwznb.com")
                }
            }
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = {
                    showImageViewer(
                        context = context,
                        images = listOf(fullImagePreviewItem(resolvedUrl))
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "表情预览",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun StickerPackHeader(
    pack: StickerPack,
    creatorName: String,
    creatorAvatar: String,
    isCollected: Boolean,
    isOwner: Boolean,
    isSubmitting: Boolean,
    onToggleCollect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (creatorAvatar.isNotBlank()) {
                AsyncImage(
                    model = creatorAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = creatorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (creatorName.isNotBlank()) {
                    Text(
                        text = "创建者",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            if (!isOwner) {
                Button(
                    onClick = onToggleCollect,
                    enabled = !isSubmitting,
                    colors = if (isCollected) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (isCollected) "已收藏" else "收藏")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatItem(
                label = "表情数",
                value = pack.stickerItems.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "使用人数",
                value = pack.userCount.toString(),
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "热度",
                value = pack.hot.toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerDetailItem(
    sticker: StickerItem,
    isOwner: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddToFavorites: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val stickerUrl = remember(sticker.url) { resolveStickerUrl(sticker.url) }

    val imageRequest = remember(stickerUrl) {
        ImageRequest.Builder(context)
            .data(stickerUrl)
            .apply {
                if (sticker.url.contains("jwznb.com")) {
                    setHeader("Referer", "https://myapp.jwznb.com")
                }
            }
            .crossfade(true)
            .build()
    }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = {
                        showImageViewer(
                            context = context,
                            images = listOf(fullImagePreviewItem(stickerUrl))
                        )
                    },
                    onLongClick = { menuExpanded = true }
                )
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = sticker.name,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            Text(
                text = sticker.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("收藏到我的表情") },
                leadingIcon = {
                    Icon(
                        imageVector = AppIcons.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onAddToFavorites()
                }
            )

            if (isOwner) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    leadingIcon = {
                        Icon(
                            imageVector = AppIcons.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = AppIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}