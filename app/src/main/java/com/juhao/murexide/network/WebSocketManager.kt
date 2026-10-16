package com.juhao.murexide.network

import android.util.Log
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.MessageTag
import com.juhao.murexide.data.parseMessageButtons
import com.juhao.murexide.proto.chat_ws_go.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

data class RecallActor(
    val id: String,
    val name: String
)

class WebSocketManager private constructor() {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val WS_URL = "wss://chat-ws-go.jwzhd.com/ws"
        private const val HEARTBEAT_INTERVAL = 15_000L
        private const val CONNECT_TIMEOUT = 2_500L

        @Volatile
        private var instance: WebSocketManager? = null

        fun getInstance(): WebSocketManager {
            return instance ?: synchronized(this) {
                instance ?: WebSocketManager().also { instance = it }
            }
        }
    }

    private var webSocket: WebSocket? = null
    @Volatile private var isConnected = false
    @Volatile private var connectionVersion = 0
    private var currentUserId: String? = null
    private var currentToken: String? = null

    /** 当前登录用户ID，供上报按钮点击等场景使用 */
    val loggedInUserId: String?
        get() = currentUserId

    private var currentDeviceId: String? = null
    private var currentPlatform: String? = null
    @Volatile private var lastHeartbeatAckTime = 0L
    private var heartbeatJob: Job? = null

    private var maintainerJob: Job? = null
    private val connectionSignal = Channel<Unit>(Channel.CONFLATED)

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _messageFlow = MutableSharedFlow<WsEvent>()
    val messageFlow: SharedFlow<WsEvent> = _messageFlow.asSharedFlow()

    private val _invitationFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val invitationFlow: SharedFlow<Unit> = _invitationFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = NetworkClient.okHttpClient

    sealed class WsEvent {
        data class NewMessage(val message: MessageItem) : WsEvent()
        data class LocalMessageSent(val message: MessageItem) : WsEvent()
        data class LatestMessageResolved(val message: MessageItem) : WsEvent()
        data class EditMessage(val message: MessageItem) : WsEvent()
        data class StreamContent(
            val msgId: String,
            val chatId: String,
            val content: String
        ) : WsEvent()
        data class MessageDeleted(
            val message: MessageItem,
            val actor: RecallActor? = null
        ) : WsEvent() {
            val msgId: String
                get() = message.msgId
        }
        data class BoardUpdate(
            val chatId: String,
            val chatType: Int,
            val botId: String,
            val botName: String,
            val content: String,
            val contentType: Int,
            val lastUpdateTime: Long
        ) : WsEvent()
        object Connected : WsEvent()
        object Disconnected : WsEvent()
    }

    private val eventQueue = Channel<WsEvent>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in eventQueue) {
                _messageFlow.emit(event)
            }
        }
    }

    private fun publishEvent(event: WsEvent) {
        if (eventQueue.trySend(event).isFailure) {
            Log.w(TAG, "Failed to enqueue WebSocket event: ${event::class.simpleName}")
        }
    }

    fun publishLocalMessageSent(message: MessageItem) {
        publishEvent(WsEvent.LocalMessageSent(message))
    }

    fun publishLocalMessageEdited(message: MessageItem) {
        publishEvent(WsEvent.EditMessage(message))
    }

    fun publishLocalMessageRecalled(message: MessageItem, actor: RecallActor) {
        val recalled = message.copy(
            isRecalled = true,
            recalledById = actor.id.takeIf(String::isNotBlank),
            recalledByName = actor.name.takeIf(String::isNotBlank)
        )
        publishEvent(WsEvent.MessageDeleted(recalled, actor))
    }

    fun connect(userId: String, token: String, deviceId: String, platform: String = "android") {
        val credsChanged = currentUserId != userId ||
                currentToken != token ||
                currentDeviceId != deviceId ||
                currentPlatform != platform

        currentUserId = userId
        currentToken = token
        currentDeviceId = deviceId
        currentPlatform = platform

        if (credsChanged || !isConnected) {
            connectionVersion++
            closeCurrentSocket()
            handleDisconnect()
            startMaintainer()
            connectionSignal.trySend(Unit)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startMaintainer() {
        maintainerJob?.cancel()
        maintainerJob = scope.launch {
            var backoff = 1000L
            while (isActive && currentUserId != null && currentToken != null) {
                if (isConnected) {
                    connectionSignal.receive()
                    backoff = 1000L
                    continue
                }

                createConnection()

                val connected = waitForConnectionOrTimeout(CONNECT_TIMEOUT)
                if (connected) {
                    backoff = 1000L
                } else {
                    select {
                        connectionSignal.onReceive { }
                        onTimeout(backoff.milliseconds) {
                        }
                    }
                    backoff = minOf(backoff * 2, 30_000L)
                }
            }
        }
    }

    private fun createConnection() {
        connectionVersion++
        val thisVersion = connectionVersion

        closeCurrentSocket()

        val request = Request.Builder().url(WS_URL).build()
        Log.d(TAG, "Opening WebSocket (version=$thisVersion)")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (thisVersion != connectionVersion) {
                    Log.d(TAG, "Ignoring stale onOpen (v$thisVersion)")
                    return
                }
                Log.d(TAG, "WebSocket connected (v$thisVersion)")
                isConnected = true
                _connectionState.value = true
                lastHeartbeatAckTime = System.currentTimeMillis()
                sendLogin(
                    currentUserId ?: return,
                    currentToken ?: return,
                    currentDeviceId ?: return,
                    currentPlatform ?: "android"
                )
                startHeartbeat()
                publishEvent(WsEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (thisVersion != connectionVersion) return
                handleBinaryMessage(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (thisVersion != connectionVersion) return
                Log.d(TAG, "Received text message: $text")
                handleTextMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (thisVersion != connectionVersion) return
                Log.d(TAG, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (thisVersion != connectionVersion) return
                Log.d(TAG, "WebSocket closed: $code / $reason")
                onConnectionLost()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (thisVersion != connectionVersion) return
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                onConnectionLost()
            }
        })
    }

    private fun onConnectionLost() {
        if (isConnected) {
            handleDisconnect()
        }
        connectionSignal.trySend(Unit)
    }

    private suspend fun waitForConnectionOrTimeout(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isConnected) return true
            delay(100.milliseconds)
        }
        return isConnected
    }

    private fun closeCurrentSocket() {
        webSocket?.let {
            try {
                it.cancel()
            } catch (_: Exception) {
            }
        }
        webSocket = null
    }

    private fun handleDisconnect() {
        stopHeartbeat()
        isConnected = false
        _connectionState.value = false
        webSocket = null
        publishEvent(WsEvent.Disconnected)
    }

    fun manualReconnect() {
        closeCurrentSocket()
        handleDisconnect()
        startMaintainer()
        connectionSignal.trySend(Unit)
    }

    fun notifyNetworkLost() {
        closeCurrentSocket()
        handleDisconnect()
        connectionSignal.trySend(Unit)
    }

    private fun sendLogin(userId: String, token: String, deviceId: String, platform: String) {
        val json = JSONObject().apply {
            put("seq", UUID.randomUUID().toString())
            put("cmd", "login")
            put("data", JSONObject().apply {
                put("userId", userId)
                put("token", token)
                put("platform", platform)
                put("deviceId", deviceId)
            })
        }
        webSocket?.send(json.toString())
        Log.d(TAG, "Login sent")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isConnected) {
                delay(HEARTBEAT_INTERVAL.milliseconds)
                if (!isConnected) break
                val now = System.currentTimeMillis()
                if (now - lastHeartbeatAckTime > HEARTBEAT_INTERVAL * 2) {
                    Log.w(TAG, "Heartbeat timeout")
                    onConnectionLost()
                    break
                }
                sendHeartbeat()
            }
        }
    }

    private fun sendHeartbeat() {
        val json = JSONObject().apply {
            put("seq", UUID.randomUUID().toString())
            put("cmd", "heartbeat")
            put("data", JSONObject())
        }
        webSocket?.send(json.toString())
        Log.d(TAG, "Heartbeat sent")
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun handleTextMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (val cmd = json.optString("cmd", "")) {
                "heartbeat_ack" -> {
                    Log.d(TAG, "Heartbeat ACK received")
                    lastHeartbeatAckTime = System.currentTimeMillis()
                }
                "invite_apply" -> {
                    Log.d(TAG, "Invitation update received (Text)")
                    _invitationFlow.tryEmit(Unit)
                }
                else -> {
                    Log.d(TAG, "Unknown command: $cmd")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse text message", e)
        }
    }

    private fun handleBinaryMessage(data: ByteArray) {
        Log.d(TAG, "Received binary message, size: ${data.size} bytes")
        try {
            val base = heartbeat_ack.ADAPTER.decode(data)
            val cmd = base.info?.cmd
            Log.d(TAG, "Binary message cmd: $cmd")

            when (cmd) {
                "heartbeat_ack" -> {
                    Log.d(TAG, "Heartbeat ACK received (Binary)")
                    lastHeartbeatAckTime = System.currentTimeMillis()
                }
                "push_message" -> {
                    val pushMessage = push_message.ADAPTER.decode(data)
                    pushMessage.data_?.msg?.let { msg ->
                        val event = msg.toPushEvent(currentUserId)
                        when (event) {
                            is WsEvent.MessageDeleted -> Log.d(
                                TAG,
                                "Recalled message: msgId=${msg.msg_id}, chatId=${msg.chat_id}"
                            )
                            else -> Log.d(
                                TAG,
                                "New message: msgId=${msg.msg_id}, chatId=${msg.chat_id}"
                            )
                        }
                        publishEvent(event)
                    }
                }
                "edit_message" -> {
                    decodeEditMessageEvent(data)?.let { event ->
                        when (event) {
                            is WsEvent.EditMessage -> Log.d(
                                TAG,
                                "Edit message: msgId=${event.message.msgId}, chatId=${event.message.chatId}"
                            )
                            is WsEvent.MessageDeleted -> Log.d(
                                TAG,
                                "Recalled message: msgId=${event.msgId}"
                            )
                            else -> Unit
                        }
                        publishEvent(event)
                    }
                }
                "stream_message" -> {
                    val streamMessage = stream_message.ADAPTER.decode(data)
                    streamMessage.data_?.msg?.let { msg ->
                        publishEvent(
                            WsEvent.StreamContent(
                                msgId = msg.msg_id,
                                chatId = msg.chat_id,
                                content = msg.content
                            )
                        )
                    }
                }
                "file_send_message" -> {
                    val fileMsg = file_send_message.ADAPTER.decode(data)
                    Log.d(TAG, "File send message: ${fileMsg.data_?.sender?.send_type}")
                }
                "invite_apply" -> {
                    Log.d(TAG, "Invitation update received (Binary)")
                    _invitationFlow.tryEmit(Unit)
                }
                "bot_board_message" -> {
                    val boardMsg = bot_board_message.ADAPTER.decode(data)
                    boardMsg.data_?.board?.let { b ->
                        Log.d(TAG, "Bot board message from: ${b.bot_name}, chatId=${b.chat_id}")
                        publishEvent(
                            WsEvent.BoardUpdate(
                                chatId = b.chat_id,
                                chatType = b.chat_type,
                                botId = b.bot_id,
                                botName = b.bot_name,
                                content = b.content,
                                contentType = b.content_type,
                                lastUpdateTime = b.last_update_time
                            )
                        )
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown binary command: $cmd")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse binary message", e)
        }
    }

    fun disconnect() {
        currentUserId = null
        currentToken = null
        currentDeviceId = null
        currentPlatform = null
        maintainerJob?.cancel()
        maintainerJob = null
        connectionVersion++
        closeCurrentSocket()
        stopHeartbeat()
        isConnected = false
        _connectionState.value = false
    }
}

internal fun WsMsg.toPushEvent(currentUserId: String?): WebSocketManager.WsEvent {
    val message = toPushedMessageItem(currentUserId)
    return if (message.isRecalled) {
        WebSocketManager.WsEvent.MessageDeleted(message, sender?.toRecallActor())
    } else {
        WebSocketManager.WsEvent.NewMessage(message)
    }
}

private fun WsMsg.toPushedMessageItem(currentUserId: String?): MessageItem {
    val isRecalled = delete_time > 0
    return MessageItem(
        msgId = msg_id,
        senderId = sender?.chat_id ?: "",
        senderName = sender?.name ?: "",
        senderAvatar = sender?.avatar_url ?: "",
        senderType = sender?.chat_type ?: 1,
        chatId = chat_id,
        chatType = chat_type,
        content = content?.text ?: "",
        contentType = content_type,
        timestamp = timestamp,
        msgSeq = msg_seq,
        direction = if (sender?.chat_id == currentUserId) "right" else "left",
        isRecalled = isRecalled,
        hasReliableSender = !isRecalled,
        deleteTime = delete_time,
        isEdited = edit_time > 0,
        quoteMsgId = quote_msg_id.takeIf { it.isNotEmpty() },
        quoteMsgText = content?.quote_msg_text?.takeIf { it.isNotEmpty() },
        quoteImageUrl = content?.quote_image_url?.takeIf { it.isNotEmpty() },
        stickerPackId = content?.sticker_pack_id,
        stickerUrl = content?.sticker_url?.takeIf { it.isNotEmpty() },
        imageUrl = content?.image_url?.takeIf { it.isNotEmpty() },
        imageWidth = content?.width?.takeIf { it > 0 },
        imageHeight = content?.height?.takeIf { it > 0 },
        audioUrl = content?.audio_url?.takeIf { it.isNotEmpty() },
        audioTime = if ((content?.audio_time ?: 0) > 0) content?.audio_time?.toInt() else null,
        videoUrl = content?.video_url?.takeIf { it.isNotEmpty() },
        videoTime = if ((content?.video_time ?: 0) > 0) content?.video_time?.toInt() else null,
        fileUrl = content?.file_url?.takeIf { it.isNotEmpty() },
        fileName = content?.file_name?.takeIf { it.isNotEmpty() },
        fileSize = if ((content?.file_size ?: 0) > 0) content?.file_size else null,
        cmdName = cmd?.name?.takeIf { it.isNotEmpty() },
        cmdId = cmd?.id,
        postId = content?.post_id,
        postTitle = content?.post_title,
        postContent = content?.post_content,
        postContentType = content?.post_content_type?.toIntOrNull(),
        buttons = parseMessageButtons(content?.buttons),
        tags = sender?.tag?.map { tag ->
            MessageTag(
                id = tag.id,
                text = tag.text,
                color = tag.color
            )
        } ?: emptyList(),
        updateTimestamp = maxOf(timestamp, edit_time, delete_time)
    )
}

private fun WsMsg.WsSender.toRecallActor(): RecallActor? {
    if (chat_id.isBlank() && name.isBlank()) return null
    return RecallActor(id = chat_id, name = name)
}

internal fun decodeEditMessageEvent(data: ByteArray): WebSocketManager.WsEvent? {
    val msg = edit_message.ADAPTER.decode(data).data_?.msg ?: return null
    val message = msg.toEditedMessageItem()
    return if (message.isRecalled) {
        val actor = msg.sender?.let { sender ->
            if (sender.chat_id.isBlank() && sender.name.isBlank()) {
                null
            } else {
                RecallActor(id = sender.chat_id, name = sender.name)
            }
        }
        WebSocketManager.WsEvent.MessageDeleted(message, actor)
    } else {
        WebSocketManager.WsEvent.EditMessage(message)
    }
}

private fun WsMsg.toEditedMessageItem(): MessageItem {
    return MessageItem(
        msgId = msg_id,
        senderId = "",
        senderName = "",
        senderAvatar = "",
        chatId = chat_id,
        chatType = chat_type,
        content = content?.text ?: "",
        contentType = content_type,
        timestamp = timestamp,
        msgSeq = msg_seq,
        direction = "left",
        isRecalled = delete_time > 0,
        recalledById = sender?.chat_id?.takeIf { delete_time > 0 && it.isNotEmpty() },
        recalledByName = sender?.name?.takeIf { delete_time > 0 && it.isNotEmpty() },
        hasReliableSender = false,
        deleteTime = delete_time,
        isEdited = true,
        quoteMsgId = quote_msg_id.takeIf { it.isNotEmpty() },
        quoteMsgText = content?.quote_msg_text?.takeIf { it.isNotEmpty() },
        buttons = parseMessageButtons(content?.buttons),
        updateTimestamp = maxOf(timestamp, edit_time, delete_time)
    )
}
