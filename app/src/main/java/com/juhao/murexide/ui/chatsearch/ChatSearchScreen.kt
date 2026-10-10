package com.juhao.murexide.ui.chatsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import com.juhao.murexide.utils.formatTimestamp

import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchScreen(
    chatName: String,
    viewModel: ChatSearchViewModel,
    onBack: () -> Unit,
    onOpenMessage: (msgId: String, msgSeq: Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to listState.layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (total > 0 && last >= total - 3) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索聊天记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索 $chatName 中的消息…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(AppIcons.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.keyword.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onKeywordChange("") }) {
                            Icon(AppIcons.Close, contentDescription = "清除")
                        }
                    }
                }
            )
    
            when {
                state.isLoading -> CenterLoading()
                state.error != null -> CenterError(state.error!!, viewModel::retry)
                state.keyword.isBlank() -> CenterHint("输入关键词搜索聊天记录")
                state.results.isEmpty() -> CenterHint("未找到相关消息")
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.results, key = { it.msgId }) { message ->
                            SearchResultItem(
                                message = message,
                                onClick = { onOpenMessage(message.msgId, message.msgSeq) }
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
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
private fun SearchResultItem(
    message: MessageItem,
    onClick: () -> Unit
) {
    ListItem(
        onClick = onClick,
        leadingContent = {
            Avatar(url = message.senderAvatar, size = 44.dp)
        },
        trailingContent = {
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Text(
                text = message.senderName.ifBlank { "未知用户" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = previewText(message),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun previewText(message: MessageItem): String = when (message.contentType) {
    MessageItem.CONTENT_TYPE_TEXT,
    MessageItem.CONTENT_TYPE_MARKDOWN,
    MessageItem.CONTENT_TYPE_HTML -> message.content.ifBlank { "[消息]" }

    MessageItem.CONTENT_TYPE_IMAGE -> "[图片]"
    MessageItem.CONTENT_TYPE_FILE -> "[文件] ${message.fileName.orEmpty()}".trim()
    MessageItem.CONTENT_TYPE_VIDEO -> "[视频]"
    MessageItem.CONTENT_TYPE_AUDIO -> "[语音]"
    MessageItem.CONTENT_TYPE_STICKER -> "[表情]"
    MessageItem.CONTENT_TYPE_POST -> "[文章] ${message.postTitle.orEmpty()}".trim()
    else -> message.getDisplayContent()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CenterLoading() = Box(Modifier.fillMaxSize(), Alignment.Center) {
    ContainedLoadingIndicator()
}

@Composable
private fun CenterHint(text: String) = Box(Modifier.fillMaxSize(), Alignment.Center) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CenterError(message: String, retry: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
    Text(message, color = MaterialTheme.colorScheme.error)
    TextButton(onClick = retry) { Text("重试") }
}