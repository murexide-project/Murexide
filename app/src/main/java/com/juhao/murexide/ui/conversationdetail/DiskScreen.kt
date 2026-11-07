package com.juhao.murexide.ui.conversationdetail

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juhao.murexide.data.DiskFileItem
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import com.juhao.murexide.utils.FileDownloader
import com.juhao.murexide.utils.QiniuUploader
import com.juhao.murexide.utils.getFileIcon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiskScreen(
    viewModel: DiskViewModel,
    token: String,
    canManage: Boolean,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DiskFileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<DiskFileItem?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<Long?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }

    val uploadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            val uploader = QiniuUploader(
                context = context,
                userToken = token,
                uploadType = 3,
                keyPrefix = "disk/"
            )
            uploader.uploadFromUri(context, uri)
                .onSuccess { resp ->
                    val fileName = queryDisplayName(context, uri)
                    viewModel.uploadToDisk(
                        fileSizeKb = resp.fsize,
                        fileName = fileName,
                        fileMd5WithExt = resp.key.substringAfterLast("/"),
                        fileEtag = resp.hash,
                        qiniuKey = resp.key
                    )
                }
                .onFailure {
                    Toast.makeText(context, it.message ?: "上传失败", Toast.LENGTH_SHORT).show()
                }
            uploading = false
        }
    }

    fun downloadFile(item: DiskFileItem) {
        if (downloadingId != null) return
        if (item.qiniuKey.isBlank()) {
            Toast.makeText(context, "文件地址为空", Toast.LENGTH_SHORT).show()
            return
        }
        val url = if (item.qiniuKey.startsWith("http")) {
            item.qiniuKey
        } else {
            "https://chat-file.jwznb.com/${item.qiniuKey}"
        }
        scope.launch {
            downloadingId = item.id
            downloadProgress = 0f
            FileDownloader.downloadFileWithProgress(
                url = url,
                fileName = item.name,
                context = context,
                onProgress = { downloadProgress = it },
                onComplete = { path ->
                    downloadingId = null
                    Toast.makeText(context, "已保存到: $path", Toast.LENGTH_SHORT).show()
                },
                onError = { error ->
                    downloadingId = null
                    Toast.makeText(context, "下载失败: $error", Toast.LENGTH_SHORT).show()
                },
                onCallCreated = { }
            )
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    BackHandler(enabled = state.folderStack.isNotEmpty()) {
        viewModel.goBack()
    }

    StyledScaffold(
        title = {
            Text(
                if (state.folderStack.isEmpty()) "群云盘"
                else state.folderStack.last().second
            )
        },
        subtitle = {
            Text(text = "已用 ${formatSize(state.totalSize)}")
        },
        navigationIcon = {
            IconButton(onClick = {
                if (!viewModel.goBack()) onBack()
            }) {
                AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(AppIcons.Add, contentDescription = "新建文件夹")
            }
            IconButton(onClick = { uploadPicker.launch("*/*") }) {
                Icon(AppIcons.AttachFile, contentDescription = "上传文件")
            }
        },
        extraContent = {
            DiskBreadcrumb(
                folderStack = state.folderStack,
                onRootClick = { viewModel.jumpTo(0L, emptyList()) },
                onFolderClick = { index ->
                    val newStack = state.folderStack.take(index + 1)
                    val targetId = newStack.lastOrNull()?.first ?: 0L
                    viewModel.jumpTo(targetId, newStack)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.files.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(padding), Alignment.Center
                ) { CircularProgressIndicator() }
                state.files.isEmpty() -> EmptyPlaceholder(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    icon = AppIcons.Inbox,
                    title = "空文件夹",
                    subtitle = "点击右上角上传文件或新建文件夹"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding
                ) {
                    items(state.files, key = { it.id }) { item ->
                        DiskFileRow(
                            modifier = Modifier.animateItem(),
                            item = item,
                            canManage = canManage,
                            downloading = downloadingId == item.id,
                            downloadProgress = if (downloadingId == item.id) downloadProgress else 0f,
                            onClick = {
                                if (item.isFolder) {
                                    viewModel.enterFolder(item)
                                } else {
                                    downloadFile(item)
                                }
                            },
                            onRename = { renameTarget = item },
                            onDelete = { deleteTarget = item }
                        )
                    }
                }
            }
            if (uploading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createFolder(name)
                        showCreateDialog = false
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.rename(target, name)
                        renameTarget = null
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除") },
            text = { Text("确定删除「${target.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(target)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DiskBreadcrumb(
    folderStack: List<Pair<Long, String>>,
    onRootClick: () -> Unit,
    onFolderClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "群云盘",
            style = MaterialTheme.typography.bodySmall,
            color = if (folderStack.isEmpty()) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .clickable(enabled = folderStack.isNotEmpty()) { onRootClick() }
                .padding(vertical = 4.dp)
        )
        folderStack.forEachIndexed { index, (_, name) ->
            Text(
                text = " / ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = if (index == folderStack.lastIndex) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clickable(enabled = index != folderStack.lastIndex) {
                        onFolderClick(index)
                    }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DiskFileRow(
    modifier: Modifier = Modifier,
    item: DiskFileItem,
    canManage: Boolean,
    downloading: Boolean,
    downloadProgress: Float,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        modifier = modifier,
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = if (item.isFolder) AppIcons.Folder else getFileIcon(item.name),
                contentDescription = null
            )
        },
        trailingContent = {
            if (canManage) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(AppIcons.MoreVert, contentDescription = "更多")
                    }
                    ExpressiveDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                            onClick = { expanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { expanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    item.isFolder -> "文件夹"
                    downloading -> "下载中 ${(downloadProgress * 100).toInt()}%"
                    else -> "${formatSize(item.fileSize)} · ${formatTime(item.uploadTime)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (downloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                cursor.getString(index)?.let { return it }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast("/") ?: "file"
}

private fun formatSize(size: Long): String = when {
    size < 1024 -> "${size}B"
    size < 1024 * 1024 -> "${size / 1024}KB"
    size < 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1fMB", size / 1024f / 1024f)
    else -> String.format(Locale.ROOT, "%.2fGB", size / 1024f / 1024f / 1024f)
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts * 1000))