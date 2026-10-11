package com.juhao.murexide.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.MessageContent
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.repository.MessageRepository
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import com.juhao.murexide.ui.theme.MurexideTheme
import com.juhao.murexide.utils.QiniuUploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SharePayload {
    data class Text(val text: String) : SharePayload()
    data class Images(val uris: List<Uri>) : SharePayload()
    data class Video(val uri: Uri) : SharePayload()
    data class File(val uri: Uri) : SharePayload()
    data class Multiple(val items: List<SharePayload>) : SharePayload()
}

private data class ShareSuccessTarget(
    val chatId: String,
    val chatType: Int,
    val chatName: String,
    val chatAvatar: String
)

class ShareReceiverActivity : ComponentActivity() {

    private val payloadState = mutableStateOf<SharePayload?>(null)
    private val accountIdState = mutableStateOf<String?>(null)
    private val tokenState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val payload = parseIntent(intent)
        if (payload == null) {
            finish()
            return
        }
        payloadState.value = payload

        lifecycleScope.launch {
            val account = AccountStorage.getInstance(this@ShareReceiverActivity)
                .getCurrentUserInfo()
            if (account == null || account.token.isBlank()) {
                finish()
                return@launch
            }
            accountIdState.value = account.id
            tokenState.value = account.token
        }

        setContent {
            MurexideTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val payload = payloadState.value
                    val accountId = accountIdState.value
                    val token = tokenState.value
                    if (payload != null && accountId != null && token != null) {
                        ShareTargetScreen(
                            payload = payload,
                            accountId = accountId,
                            token = token,
                            onDismiss = { finish() }
                        )
                    } else {
                        CenterLoading()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val payload = parseIntent(intent)
        if (payload != null) {
            payloadState.value = payload
        } else {
            finish()
        }
    }

    private fun parseIntent(intent: Intent): SharePayload? = when (intent.action) {
        Intent.ACTION_SEND -> parseSingle(intent)
        Intent.ACTION_SEND_MULTIPLE -> parseMultiple(intent)
        else -> null
    }

    private fun parseSingle(intent: Intent): SharePayload? {
        val type = intent.type ?: return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        @Suppress("DEPRECATION")
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        return when {
            type.startsWith("text/") && !text.isNullOrBlank() -> SharePayload.Text(text)
            type.startsWith("image/") && uri != null -> SharePayload.Images(listOf(uri))
            type.startsWith("video/") && uri != null -> SharePayload.Video(uri)
            uri != null -> SharePayload.File(uri)
            !text.isNullOrBlank() -> SharePayload.Text(text)
            else -> null
        }
    }

    private fun parseMultiple(intent: Intent): SharePayload? {
        @Suppress("DEPRECATION")
        val uris: List<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (uris.isNullOrEmpty()) return null

        val type = intent.type.orEmpty()
        return when {
            type.startsWith("image/") -> SharePayload.Images(uris)
            type.startsWith("video/") && uris.size == 1 -> SharePayload.Video(uris.first())
            else -> SharePayload.Multiple(uris.map { SharePayload.File(it) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareTargetScreen(
    payload: SharePayload,
    accountId: String,
    token: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val conversations by LocalCache.observeConversations(accountId)
        .collectAsStateWithLifecycle(initialValue = emptyList<ConversationItem>())

    var query by remember { mutableStateOf("") }
    var sendingKey by remember { mutableStateOf<String?>(null) }
    var successTarget by remember { mutableStateOf<ShareSuccessTarget?>(null) }

    val filtered = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter { it.displayName.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分享到") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "取消")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索会话…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(AppIcons.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(AppIcons.Close, contentDescription = "清除")
                        }
                    }
                }
            )

            when {
                conversations.isEmpty() -> CenterLoading()
                filtered.isEmpty() -> CenterHint("未找到相关会话")
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filtered, key = { "${it.chatType}:${it.chatId}" }) { conversation ->
                            val key = "${conversation.chatType}:${conversation.chatId}"
                            ShareTargetItem(
                                conversation = conversation,
                                isSending = sendingKey == key,
                                onClick = {
                                    if (sendingKey != null) return@ShareTargetItem
                                    sendingKey = key
                                    ShareDispatcher.dispatch(
                                        context = context,
                                        token = token,
                                        payload = payload,
                                        chatId = conversation.chatId,
                                        chatType = conversation.chatType
                                    ) { success ->
                                        sendingKey = null
                                        if (success) {
                                            successTarget = ShareSuccessTarget(
                                                chatId = conversation.chatId,
                                                chatType = conversation.chatType,
                                                chatName = conversation.displayName,
                                                chatAvatar = conversation.avatarUrl
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "分享失败",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    successTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                successTarget = null
                onDismiss()
            },
            title = { Text("分享成功") },
            text = { Text("已分享给「${target.chatName}」") },
            confirmButton = {
                TextButton(
                    onClick = {
                        successTarget = null
                        ChatActivity.start(
                            context = context,
                            chatId = target.chatId,
                            chatType = target.chatType,
                            chatName = target.chatName,
                            chatAvatar = target.chatAvatar
                        )
                        onDismiss()
                    }
                ) {
                    Text("打开会话")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        successTarget = null
                        onDismiss()
                    }
                ) {
                    Text("回到原应用")
                }
            }
        )
    }
}

@Composable
private fun ShareTargetItem(
    conversation: ConversationItem,
    isSending: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        onClick = onClick,
        enabled = !isSending,
        leadingContent = {
            Avatar(url = conversation.avatarUrl, size = 44.dp)
        },
        trailingContent = {
            if (isSending) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Text(
                text = conversation.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = conversation.chatContent,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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

private object ShareDispatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = MessageRepository()

    fun dispatch(
        context: Context,
        token: String,
        payload: SharePayload,
        chatId: String,
        chatType: Int,
        onComplete: (Boolean) -> Unit
    ) {
        val appContext = context.applicationContext
        scope.launch {
            val result = runCatching {
                sendPayload(appContext, token, chatId, chatType, payload)
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { onComplete(result) }
        }
    }

    private suspend fun sendPayload(
        context: Context,
        token: String,
        chatId: String,
        chatType: Int,
        payload: SharePayload
    ): Boolean = when (payload) {
        is SharePayload.Text -> sendText(token, chatId, chatType, payload.text)
        is SharePayload.Images -> sendImages(context, token, chatId, chatType, payload.uris)
        is SharePayload.Video -> sendVideo(context, token, chatId, chatType, payload.uri)
        is SharePayload.File -> sendFile(context, token, chatId, chatType, payload.uri)
        is SharePayload.Multiple -> payload.items.all {
            sendPayload(context, token, chatId, chatType, it)
        }
    }

    private suspend fun sendText(token: String, chatId: String, chatType: Int, text: String): Boolean =
        repository.sendMessage(
            token = token,
            chatId = chatId,
            chatType = chatType,
            content = MessageContent(text = text),
            contentType = MessageItem.CONTENT_TYPE_TEXT
        ).isSuccess

    private suspend fun sendImages(
        context: Context,
        token: String,
        chatId: String,
        chatType: Int,
        uris: List<Uri>
    ): Boolean {
        var allSuccess = true
        uris.forEach { uri ->
            val uploader = QiniuUploader(
                context = context,
                userToken = token,
                uploadType = 1,
                enableWebp = true
            )
            val response = uploader.uploadFromUri(context, uri, onProgress = {}).getOrNull()
            if (response == null) {
                allSuccess = false
                return@forEach
            }
            val ok = repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = MessageContent(
                    image = response.key,
                    media = response.toMessageMedia()
                ),
                contentType = MessageItem.CONTENT_TYPE_IMAGE
            ).isSuccess
            if (!ok) allSuccess = false
        }
        return allSuccess
    }

    private suspend fun sendVideo(
        context: Context,
        token: String,
        chatId: String,
        chatType: Int,
        uri: Uri
    ): Boolean {
        val uploader = QiniuUploader(
            context = context,
            userToken = token,
            uploadType = 2
        )
        val response = uploader.uploadFromUri(context, uri, onProgress = {}).getOrNull()
            ?: return false
        return repository.sendMessage(
            token = token,
            chatId = chatId,
            chatType = chatType,
            content = MessageContent(
                video = response.key,
                media = response.toMessageMedia()
            ),
            contentType = MessageItem.CONTENT_TYPE_VIDEO
        ).isSuccess
    }

    private suspend fun sendFile(
        context: Context,
        token: String,
        chatId: String,
        chatType: Int,
        uri: Uri
    ): Boolean {
        val uploader = QiniuUploader(
            context = context,
            userToken = token,
            uploadType = 3
        )
        val response = uploader.uploadFromUri(context, uri, onProgress = {}).getOrNull()
            ?: return false
        return repository.sendMessage(
            token = token,
            chatId = chatId,
            chatType = chatType,
            content = MessageContent(
                fileKey = response.key,
                fileName = queryFileName(context, uri),
                fileSize = response.fsize
            ),
            contentType = MessageItem.CONTENT_TYPE_FILE
        ).isSuccess
    }

    private fun queryFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
    }
}