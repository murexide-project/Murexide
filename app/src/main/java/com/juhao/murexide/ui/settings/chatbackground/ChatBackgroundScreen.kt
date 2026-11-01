package com.juhao.murexide.ui.settings.chatbackground

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.juhao.murexide.data.ChatBackgroundItem
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import com.juhao.murexide.utils.QiniuUploader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBackgroundScreen(
    viewModel: ChatBackgroundViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ChatBackgroundItem?>(null) }

    LaunchedEffect(Unit) {
        if (state.token.isNotEmpty() && state.list.isEmpty()) viewModel.load()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val token = state.token
        if (token.isEmpty()) {
            Toast.makeText(context, "未登录", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            uploading = true
            QiniuUploader(
                context = context,
                userToken = token,
                uploadType = 1
            ).uploadFromUri(context, uri)
                .onSuccess { response ->
                    viewModel.setBackground("all", response.key)
                }
                .onFailure { e ->
                    Toast.makeText(context, e.message ?: "上传失败", Toast.LENGTH_SHORT).show()
                }
            uploading = false
        }
    }

    StyledScaffold(
        title = { Text("聊天背景") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            IconButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(AppIcons.Add, contentDescription = "上传背景")
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.list.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    Alignment.Center
                ) { CircularProgressIndicator() }

                state.error != null -> EmptyPlaceholder(
                    modifier = Modifier.padding(padding),
                    icon = AppIcons.Warning,
                    title = "加载失败",
                    subtitle = state.error,
                    primaryButton = { Button(onClick = viewModel::load) { Text("重试") } }
                )

                state.list.isEmpty() -> EmptyPlaceholder(
                    modifier = Modifier.padding(padding),
                    icon = AppIcons.Image,
                    title = "暂无背景",
                    subtitle = "点击右上角上传一张图片作为聊天背景"
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.list, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = item.imgUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (item.chatId == "all") "全部聊天" else item.chatId,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = SimpleDateFormat(
                                                "yyyy-MM-dd HH:mm",
                                                Locale.getDefault()
                                            ).format(Date(item.updateTime * 1000)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { deleteTarget = item }) {
                                        Icon(
                                            imageVector = AppIcons.Delete,
                                            contentDescription = "删除背景",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uploading || state.isSubmitting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = padding.calculateTopPadding())
                )
            }
        }
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(AppIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除背景") },
            text = {
                Text(
                    "确定要删除「${if (target.chatId == "all") "全部聊天" else target.chatId}」的背景吗？"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBackground(target.chatId)
                        deleteTarget = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}