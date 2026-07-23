package com.juhao.murexide.ui.chat

import android.net.Uri
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.bot.bot_info
import com.juhao.murexide.proto.bot.bot_info_send
import com.juhao.murexide.proto.group.info
import com.juhao.murexide.proto.group.info_send
import com.juhao.murexide.repository.ChatBackgroundRepository
import com.juhao.murexide.repository.StickerRepository
import com.juhao.murexide.repository.InstructionRepository
import com.juhao.murexide.repository.BoardRepository
import com.juhao.murexide.repository.MessageRepository
import com.juhao.murexide.repository.FriendRepository
import com.juhao.murexide.repository.GroupMemberRepository
import com.juhao.murexide.utils.FileDownloader.downloadFileWithProgress
import com.juhao.murexide.data.*
import com.juhao.murexide.utils.QiniuUploader
import com.juhao.murexide.network.WebSocketManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatViewModel(
    val token: String,
    val chatId: String,
    private val chatType: Int,
    private val repository: MessageRepository = MessageRepository(),
    private val backgroundRepository: ChatBackgroundRepository = ChatBackgroundRepository(),
    private val stickerRepository: StickerRepository = StickerRepository(),
    private val instructionRepository: InstructionRepository = InstructionRepository(),
    private val friendRepository: FriendRepository = FriendRepository(),
    private val groupMemberRepository: GroupMemberRepository = GroupMemberRepository(),
    private val boardRepository: BoardRepository = BoardRepository(),
    private val wsManager: WebSocketManager = WebSocketManager.getInstance()
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private var uploadJob: Job? = null

    private val msgIdCache = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _buttonEvent = MutableSharedFlow<ButtonEvent>()
    val buttonEvent: SharedFlow<ButtonEvent> = _buttonEvent.asSharedFlow()

    private val _recallDialog = MutableStateFlow(RecallDialogState())
    val recallDialog: StateFlow<RecallDialogState> = _recallDialog.asStateFlow()

    private val _stickerPanel = MutableStateFlow(StickerPanelState())
    val stickerPanel: StateFlow<StickerPanelState> = _stickerPanel.asStateFlow()

    private val _instructionForm = MutableStateFlow<InstructionItem?>(null)
    val instructionForm: StateFlow<InstructionItem?> = _instructionForm.asStateFlow()

    private val _downloadingFiles = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingFiles: StateFlow<Map<String, Float>> = _downloadingFiles.asStateFlow()

    private var currentMsgId: String? = null
    private var isLoadingMore = false

    init {
        loadMessages()
        setupWebSocket()
        loadBackground()
        if (chatType == 2) { // 群聊
            loadGroupInfo()
        }
        if (chatType == 3) { // 机器人
            loadBotInfo()
        }
        if (chatType == 2 || chatType == 3) {
            loadBoard()
        }
    }

    /** 加载群看板 */
    private fun loadBoard() {
        _uiState.update { it.copy(boardPanel = it.boardPanel.copy(isLoading = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            boardRepository.getBoard(token, chatId, chatType).onSuccess { boards ->
                _uiState.update {
                    it.copy(
                        boardPanel = it.boardPanel.copy(
                            boards = boards,
                            isLoaded = true,
                            isLoading = false
                        )
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to load board", e)
                _uiState.update {
                    it.copy(boardPanel = it.boardPanel.copy(isLoaded = true, isLoading = false))
                }
            }
        }
    }

    /** 收到 WS 看板更新：按 botId upsert 到当前列表 */
    private fun applyBoardUpdate(event: WebSocketManager.WsEvent.BoardUpdate) {
        _uiState.update { state ->
            val existing = state.boardPanel.boards
            val item = BoardItem(
                botId = event.botId,
                botName = event.botName,
                content = event.content,
                contentType = event.contentType,
                lastUpdateTime = event.lastUpdateTime
            )
            val updated = if (existing.any { it.botId == event.botId }) {
                existing.map { if (it.botId == event.botId) item else it }
            } else {
                existing + item
            }.filter { it.content.isNotBlank() }
                .sortedByDescending { it.lastUpdateTime }
            state.copy(boardPanel = state.boardPanel.copy(boards = updated))
        }
    }

    /** 切换看板面板展开/折叠 */
    fun toggleBoard() {
        _uiState.update {
            it.copy(boardPanel = it.boardPanel.copy(isExpanded = !it.boardPanel.isExpanded))
        }
    }

    private fun loadBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            backgroundRepository.getBackgroundList(token).onSuccess { list ->
                val url = backgroundRepository.resolveBackground(list, chatId)
                _uiState.update { it.copy(backgroundUrl = url) }
            }.onFailure { e ->
                Log.e(TAG, "Failed to load background", e)
            }
        }
    }

    private fun loadGroupInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val requestProto = info_send(group_id = chatId)
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())
                
                val request = Request.Builder()
                    .url("${NetworkClient.BASE_URL}/v1/group/info")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body.bytes()
                        val groupInfo = info.ADAPTER.decode(body)
                        if (groupInfo.status?.code == 1) {
                            val data = groupInfo.data_
                            val memberCount = data?.member
                            val ownerId = data?.owner?.takeIf { it.isNotEmpty() }
                            val adminIds = data?.admin?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
                            val permissionLevel = data?.permisson_level ?: 0
                            _uiState.update {
                                it.copy(
                                    memberCount = memberCount,
                                    ownerId = ownerId,
                                    adminIds = adminIds,
                                    myGroupNickname = data?.my_group_nickname,
                                    permissionLevel = permissionLevel,
                                    isAdmin = permissionLevel >= 2
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load group info", e)
            }
        }
    }
    
    private fun loadBotInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val requestProto = bot_info_send(id = chatId)
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())
                
                val request = Request.Builder()
                    .url("${NetworkClient.BASE_URL}/v1/bot/bot-info")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body.bytes()
                        val botInfo = bot_info.ADAPTER.decode(body)
                        if (botInfo.status?.code == 1) {
                            val d = botInfo.data_
                            _uiState.update {
                                it.copy(
                                    usageCount = d?.headcount
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bot info", e)
            }
        }
    }

    private fun setupWebSocket() {
        viewModelScope.launch {
            wsManager.messageFlow.collect { event ->
                Log.d(TAG, "Received WS event: ${event::class.simpleName}")
                when (event) {
                    is WebSocketManager.WsEvent.NewMessage -> {
                        val match = event.message.chatId == chatId || (event.message.chatType == 1 && event.message.senderId == chatId)
                        Log.d(TAG, "New message: chatId=${event.message.chatId}, expected=$chatId, match=${match}")
                        if (match) {
                            addReceivedMessage(event.message)
                        }
                    }
                    is WebSocketManager.WsEvent.EditMessage -> {
                        Log.d(TAG, "Edit message: chatId=${event.message.chatId}, expected=$chatId")
                        if (event.message.chatId == chatId) {
                            updateEditedMessage(event.message)
                        }
                    }
                    is WebSocketManager.WsEvent.StreamContent -> {
                        Log.d(TAG, "Stream content: msgId=${event.msgId}")
                        updateStreamMessage(event.msgId, event.content)
                    }
                    is WebSocketManager.WsEvent.MessageDeleted -> {
                        Log.d(TAG, "Message deleted: msgId=${event.msgId}")
                        deleteMessage(event.msgId)
                    }
                    is WebSocketManager.WsEvent.BoardUpdate -> {
                        Log.d(TAG, "Board update: chatId=${event.chatId}, expected=$chatId")
                        if (event.chatId == chatId) {
                            applyBoardUpdate(event)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getMessageList(
                token = token,
                chatId = chatId,
                chatType = chatType
            ).onSuccess { messages ->
                msgIdCache.clear()
                msgIdCache.addAll(messages.map { it.msgId })

                _uiState.update {
                    it.copy(
                        messages = messages,
                        isLoading = false,
                        hasMore = messages.isNotEmpty(),
                        error = null
                    )
                }
                if (messages.isNotEmpty()) {
                    currentMsgId = messages.last().msgId
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "加载失败"
                    )
                }
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || !_uiState.value.hasMore) return

        viewModelScope.launch {
            isLoadingMore = true
            _uiState.update { it.copy(isLoadingMore = true) }

            repository.getMessageList(
                token = token,
                chatId = chatId,
                chatType = chatType,
                msgId = currentMsgId
            ).onSuccess { messages ->
                if (messages.isNotEmpty()) {
                    val newMessages = messages.filter { it.msgId !in msgIdCache }
                    if (newMessages.isNotEmpty()) {
                        msgIdCache.addAll(newMessages.map { it.msgId })
                        _uiState.update {
                            it.copy(
                                messages = it.messages + newMessages,
                                isLoadingMore = false,
                                hasMore = true
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoadingMore = false, hasMore = true) }
                    }
                    currentMsgId = messages.last().msgId
                } else {
                    _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
            }

            isLoadingMore = false
        }
    }

    fun refresh() {
        msgIdCache.clear()
        currentMsgId = null
        loadMessages()
    }

    fun updateInputText(text: String) {
        if (_uiState.value.inputText == text) return
        _uiState.update { it.copy(inputText = text) }
    }

    fun setReplyTo(message: MessageItem) {
        _uiState.update { it.copy(replyTo = message) }
    }

    fun clearReplyTo() {
        _uiState.update { it.copy(replyTo = null) }
    }

    fun sendMessage(sendTypeOverride: String? = null) {
        val state = _uiState.value
        if (state.editingMessage != null) {
            editCurrentMessage(sendTypeOverride)
            return
        }
        if (state.inputText.isBlank() || state.isSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val contentType = when (sendTypeOverride ?: state.sendType) {
                "markdown" -> MessageItem.CONTENT_TYPE_MARKDOWN
                "html" -> MessageItem.CONTENT_TYPE_HTML
                else -> MessageItem.CONTENT_TYPE_TEXT
            }
            val mentionedIds = state.mentions
                .filterKeys { name -> state.inputText.contains("@$name") }
                .values.distinct()

            val content = MessageContent(
                text = state.inputText,
                mentionedId = mentionedIds,
                quoteMsgText = state.replyTo?.let {
                    "${it.senderName}: ${it.content}"
                },
                quoteImageUrl = state.replyTo?.imageUrl,
                quoteImageName = state.replyTo?.imageUrl?.toUri()?.lastPathSegment
            )

            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = contentType,
                quoteMsgId = state.replyTo?.msgId,
                commandId = state.pendingCommandId
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        inputText = "",
                        replyTo = null,
                        isSending = false,
                        mentions = emptyMap(),
                        pendingCommandId = null,
                        pendingCommandName = null,
                        pendingCommandHint = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit(error.message ?: "发送失败")
            }
        }
    }

    fun uploadAndSendVideo(uri: Uri, context: Context) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    uploadProgress = 0f,
                    uploadImagePath = uri.toString(),
                    isSending = false
                )
            }
    
            try {
                val uploader = QiniuUploader(
                    context = context,
                    userToken = token,
                    uploadType = 2
                )
    
                val result = uploader.uploadFromUri(
                    context = context,
                    uri = uri,
                    onProgress = { progress ->
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                )
    
                if (!isActive) {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    return@launch
                }
    
                result.onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f,
                            uploadImagePath = null
                        )
                    }
                    sendVideoMessage(response.key)
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    _toastMessage.emit("视频上传失败: ${error.message}")
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("已取消上传")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("上传失败: ${e.message}")
            }
        }
    }
    
    private fun sendVideoMessage(videoUrl: String) {
        val state = _uiState.value
        
        viewModelScope.launch {
            val content = MessageContent(
                video = videoUrl,
                text = "",
                quoteMsgText = state.replyTo?.let {
                    "${it.senderName}: ${it.content}"
                },
                quoteImageUrl = state.replyTo?.imageUrl,
                quoteImageName = state.replyTo?.imageUrl?.toUri()?.lastPathSegment
            )
            
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_VIDEO,
                quoteMsgId = _uiState.value.replyTo?.msgId
            ).onSuccess {
                _uiState.update { 
                    it.copy(
                        replyTo = null,
                        isSending = false
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit("发送失败: ${error.message}")
            }
        }
    }
    
    fun uploadAndSendImage(uri: Uri, context: Context) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    uploadProgress = 0f,
                    uploadImagePath = uri.toString(),
                    isSending = false
                )
            }
    
            try {
                val uploader = QiniuUploader(
                    context = context,
                    userToken = token,
                    enableWebp = true
                )
    
                val result = uploader.uploadFromUri(
                    context = context,
                    uri = uri,
                    onProgress = { progress ->
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                )
    
                if (!isActive) {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    return@launch
                }
    
                result.onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f,
                            uploadImagePath = null
                        )
                    }
                    sendImageMessage(response.key)
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    _toastMessage.emit("图片上传失败: ${error.message}")
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("已取消上传")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("上传失败: ${e.message}")
            }
        }
    }
    
    fun cancelUpload() {
        uploadJob?.cancel()
        _uiState.update { 
            it.copy(
                isUploading = false,
                uploadProgress = 0f,
                uploadImagePath = null
            )
        }
    }
    
    private fun sendImageMessage(imageUrl: String) {
        val state = _uiState.value
        
        viewModelScope.launch {
            val content = MessageContent(
                image = imageUrl,
                text = "",
                quoteMsgText = state.replyTo?.let {
                    "${it.senderName}: ${it.content}"
                },
                quoteImageUrl = state.replyTo?.imageUrl,
                quoteImageName = state.replyTo?.imageUrl?.toUri()?.lastPathSegment
            )
            
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_IMAGE,
                quoteMsgId = _uiState.value.replyTo?.msgId
            ).onSuccess {
                _uiState.update { 
                    it.copy(
                        replyTo = null,
                        isSending = false
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit("发送失败: ${error.message}")
            }
        }
    }
    
    fun uploadAndSendFile(uri: Uri, context: Context) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    uploadProgress = 0f,
                    uploadImagePath = uri.toString(),
                    isSending = false
                )
            }
    
            try {
                val uploader = QiniuUploader(
                    context = context,
                    userToken = token,
                    uploadType = 3
                )
    
                val result = uploader.uploadFromUri(
                    context = context,
                    uri = uri,
                    onProgress = { progress ->
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                )
    
                if (!isActive) {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    return@launch
                }
    
                result.onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f,
                            uploadImagePath = null
                        )
                    }
                    sendFileMessage(response.key, response.fsize, uri, context)
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            uploadImagePath = null
                        )
                    }
                    _toastMessage.emit("文件上传失败: ${error.message}")
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("已取消上传")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        uploadImagePath = null
                    )
                }
                _toastMessage.emit("上传失败: ${e.message}")
            }
        }
    }
    
    private fun sendFileMessage(fileUrl: String, fileSize: Long, uri: Uri, context: Context) {
        viewModelScope.launch {
            val fileName = getFileNameFromUri(context, uri)
            
            val content = MessageContent(
                text = "",
                fileKey = fileUrl,
                fileName = fileName,
                fileSize = fileSize
            )
            
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_FILE,
                quoteMsgId = _uiState.value.replyTo?.msgId
            ).onSuccess {
                _uiState.update { 
                    it.copy(
                        replyTo = null,
                        isSending = false
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit("发送失败: ${error.message}")
            }
        }
    }
    
    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
    }

    /**
     * 处理消息气泡按钮点击。
     * actionType=1 跳转URL / 2 复制文本，交由 UI 处理（返回事件）；
     * actionType=3 上报点击事件到服务端。
     */
    fun onButtonClick(message: MessageItem, button: MessageButton) {
        when (button.actionType) {
            MessageButton.ACTION_JUMP -> {
                val url = button.url?.takeIf { it.isNotBlank() }
                if (url != null) {
                    viewModelScope.launch { _buttonEvent.emit(ButtonEvent.OpenUrl(url)) }
                } else {
                    viewModelScope.launch { _toastMessage.emit("按钮缺少跳转链接") }
                }
            }

            MessageButton.ACTION_COPY -> {
                val value = button.value ?: button.text
                viewModelScope.launch { _buttonEvent.emit(ButtonEvent.CopyText(value)) }
            }

            else -> {
                // actionType 3 或其它：上报点击事件
                reportButtonClick(message, button)
            }
        }
    }

    private fun reportButtonClick(message: MessageItem, button: MessageButton) {
        val userId = wsManager.loggedInUserId
        if (userId.isNullOrEmpty()) {
            viewModelScope.launch { _toastMessage.emit("无法获取用户信息，请稍后重试") }
            return
        }
        val value = button.value ?: button.text
        viewModelScope.launch {
            repository.reportButtonClick(
                token = token,
                msgId = message.msgId,
                chatId = chatId,
                chatType = chatType,
                userId = userId,
                buttonValue = value
            ).onFailure { error ->
                _toastMessage.emit("按钮操作失败: ${error.message}")
                Log.e(TAG, "button-report failed", error)
            }
        }
    }

    fun showRecallDialog(msgId: String) {
        _recallDialog.value = RecallDialogState(isOpen = true, msgId = msgId)
    }

    fun hideRecallDialog() {
        _recallDialog.value = RecallDialogState(isOpen = false)
    }

    fun recallMessage() {
        val msgId = _recallDialog.value.msgId ?: return

        viewModelScope.launch {
            repository.recallMessage(
                token = token,
                msgId = msgId,
                chatId = chatId,
                chatType = chatType
            ).onSuccess {
                hideRecallDialog()
                deleteMessage(msgId)
                _toastMessage.emit("撤回成功")
            }.onFailure { error ->
                hideRecallDialog()
                _toastMessage.emit("撤回失败: ${error.message}")
                error.printStackTrace()
            }
        }
    }

    /** 进入内联编辑模式：把消息内容填入主输入框，并按原类型设置发送类型 */
    fun startEditMessage(message: MessageItem) {
        hideStickerPanel()
        hideInstructionPanel()
        _uiState.update {
            it.copy(
                editingMessage = message,
                inputText = message.content,
                sendType = when (message.contentType) {
                    MessageItem.CONTENT_TYPE_MARKDOWN -> "markdown"
                    MessageItem.CONTENT_TYPE_HTML -> "html"
                    else -> "text"
                },
                replyTo = null,
                pendingCommandId = null,
                pendingCommandName = null,
                pendingCommandHint = null
            )
        }
    }

    /** 退出编辑模式 */
    fun cancelEdit() {
        _uiState.update {
            it.copy(
                editingMessage = null,
                inputText = "",
                sendType = "text"
            )
        }
    }

    private fun editCurrentMessage(sendTypeOverride: String? = null) {
        val state = _uiState.value
        val message = state.editingMessage ?: return
        if (state.inputText.isBlank() || state.isSending) return

        val contentType = when (sendTypeOverride ?: state.sendType) {
            "markdown" -> MessageItem.CONTENT_TYPE_MARKDOWN
            "html" -> MessageItem.CONTENT_TYPE_HTML
            else -> MessageItem.CONTENT_TYPE_TEXT
        }
        val newContent = state.inputText

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val content = MessageContent(text = newContent)

            repository.editMessage(
                token = token,
                msgId = message.msgId,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = contentType
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        editingMessage = null,
                        inputText = "",
                        sendType = "text"
                    )
                }
                updateEditedMessage(
                    message.copy(
                        content = newContent,
                        contentType = contentType,
                        isEdited = true
                    )
                )
                _toastMessage.emit("编辑成功")
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit("编辑失败: ${error.message}")
                error.printStackTrace()
            }
        }
    }

    // ---------- 表情面板 ----------

    /** 切换表情面板显示/隐藏（显示前懒加载一次数据） */
    fun toggleStickerPanel() {
        val current = _stickerPanel.value
        if (current.isVisible) {
            _stickerPanel.value = current.copy(isVisible = false)
        } else {
            hideInstructionPanel()
            _stickerPanel.value = current.copy(isVisible = true)
            if (!current.isLoaded && !current.isLoading) {
                loadStickerData()
            }
        }
    }

    fun hideStickerPanel() {
        _stickerPanel.update { it.copy(isVisible = false) }
    }

    /** 同时加载个人收藏表情和表情包列表 */
    private fun loadStickerData() {
        _stickerPanel.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val exprResult = stickerRepository.getExpressionList(token)
            val packResult = stickerRepository.getStickerList(token)

            val expressions = exprResult.getOrElse {
                Log.e(TAG, "Failed to load expressions", it); emptyList()
            }
            val packs = packResult.getOrElse {
                Log.e(TAG, "Failed to load sticker packs", it); emptyList()
            }

            _stickerPanel.update {
                it.copy(
                    isLoading = false,
                    isLoaded = true,
                    expressions = expressions,
                    stickerPacks = packs
                )
            }
        }
    }

    /** 发送个人收藏表情 */
    fun sendExpression(expression: ExpressionItem) {
        val url = expression.url
        sendStickerMessage(
            imageUrl = url,
            expressionId = expression.id.toString()
        )
    }

    /** 发送表情包里的单个表情 */
    fun sendStickerItem(item: StickerItem) {
        val url = item.url
        sendStickerMessage(
            imageUrl = url,
            stickerItemId = item.id,
            stickerPackId = item.stickerPackId
        )
    }

    private fun sendStickerMessage(
        imageUrl: String,
        expressionId: String? = null,
        stickerItemId: Long? = null,
        stickerPackId: Long? = null
    ) {
        val state = _uiState.value
        
        val content = MessageContent(
            image = imageUrl,
            expressionId = expressionId,
            stickerItemId = stickerItemId,
            stickerPackId = stickerPackId,
            quoteMsgText = state.replyTo?.let {
                "${it.senderName}: ${it.content}"
            },
            quoteImageUrl = state.replyTo?.imageUrl,
            quoteImageName = state.replyTo?.imageUrl?.toUri()?.lastPathSegment
        )

        viewModelScope.launch {
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_STICKER,
                quoteMsgId = _uiState.value.replyTo?.msgId
            ).onSuccess {
                hideStickerPanel()
                _uiState.update { 
                    it.copy(
                        replyTo = null
                    )
                }
            }.onFailure { error ->
                _toastMessage.emit("表情发送失败: ${error.message}")
                error.printStackTrace()
            }
        }
    }

    // ---------- 指令面板 ----------

    /** 切换指令面板显示/隐藏（显示前懒加载一次数据） */
    fun toggleInstructionPanel() {
        val panel = _uiState.value.instructionPanel
        if (panel.isVisible) {
            hideInstructionPanel()
        } else {
            hideStickerPanel()
            _uiState.update { it.copy(instructionPanel = it.instructionPanel.copy(isVisible = true)) }
            if (!panel.isLoaded && !panel.isLoading) {
                loadInstructionData()
            }
        }
    }

    fun hideInstructionPanel() {
        _uiState.update { it.copy(instructionPanel = it.instructionPanel.copy(isVisible = false)) }
    }

    /** 按会话类型加载指令：群聊走 bot-list，机器人私聊走 web-list */
    private fun loadInstructionData() {
        _uiState.update { it.copy(instructionPanel = it.instructionPanel.copy(isLoading = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val bots: List<BotItem>
            val instructions: List<InstructionItem>
            when (chatType) {
                2 -> {
                    val result = instructionRepository.getGroupBots(token, chatId).getOrElse {
                        Log.e(TAG, "Failed to load group bots", it)
                        emptyList<BotItem>() to emptyList()
                    }
                    bots = result.first
                    instructions = result.second
                }
                3 -> {
                    val list = instructionRepository.getBotInstructions(
                        token, chatId, botName = state.chatName
                    ).getOrElse {
                        Log.e(TAG, "Failed to load bot instructions", it)
                        emptyList()
                    }
                    bots = listOf(
                        BotItem(id = chatId, name = state.chatName, avatarUrl = state.chatAvatar)
                    )
                    instructions = list
                }
                else -> {
                    bots = emptyList()
                    instructions = emptyList()
                }
            }
            _uiState.update {
                it.copy(
                    instructionPanel = it.instructionPanel.copy(
                        isLoading = false,
                        isLoaded = true,
                        bots = bots,
                        instructions = instructions
                    )
                )
            }
        }
    }

    /** 点击指令：按类型分流 */
    fun onInstructionClick(item: InstructionItem) {
        when (item.type) {
            2 -> sendInstructionDirect(item)          // 直发指令
            5 -> _instructionForm.value = item        // 自定义输入指令 → 弹表单
            else -> {                                  // 普通指令 → 预填输入框、挂载待发送
                hideInstructionPanel()
                _uiState.update {
                    it.copy(
                        inputText = item.defaultText,
                        pendingCommandId = item.id,
                        pendingCommandName = item.name,
                        pendingCommandHint = item.hintText
                    )
                }
            }
        }
    }

    /** 直发指令：立即发送 */
    private fun sendInstructionDirect(item: InstructionItem) {
        viewModelScope.launch {
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = MessageContent(),
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                commandId = item.id
            ).onSuccess {
                hideInstructionPanel()
            }.onFailure { error ->
                _toastMessage.emit("指令发送失败: ${error.message}")
            }
        }
    }

    /** 提交自定义输入指令表单 */
    fun submitInstructionForm(item: InstructionItem, formJson: String) {
        viewModelScope.launch {
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = MessageContent(form = formJson),
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                commandId = item.id
            ).onSuccess {
                _instructionForm.value = null
                hideInstructionPanel()
            }.onFailure { error ->
                _toastMessage.emit("指令发送失败: ${error.message}")
            }
        }
    }

    fun dismissInstructionForm() {
        _instructionForm.value = null
    }

    /** 取消待发送指令 */
    fun clearPendingCommand() {
        _uiState.update {
            it.copy(
                pendingCommandId = null,
                pendingCommandName = null,
                pendingCommandHint = null
            )
        }
    }

    fun addReceivedMessage(message: MessageItem) {
        if (message.msgId in msgIdCache) {
            updateEditedMessage(message)
            return
        }
        
        if (message.isRecalled) {
            return
        }

        msgIdCache.add(message.msgId)
        _uiState.update {
            it.copy(messages = listOf(message) + it.messages)
        }
    }

    fun updateStreamMessage(msgId: String, content: String) {
        _uiState.update {
            it.copy(
                messages = it.messages.map { msg ->
                    if (msg.msgId == msgId)
                        msg.copy(content = msg.content + content)
                    else
                        msg
                }
            )
        }
    }

    fun updateEditedMessage(message: MessageItem) {
        _uiState.update {
            it.copy(
                messages = it.messages.map { msg ->
                    if (msg.msgId == message.msgId) 
                        msg.copy(
                            content = message.content,
                            contentType = message.contentType,
                            isEdited = true,
                            isRecalled = message.isRecalled,
                            buttons = message.buttons
                        )
                    else 
                        msg
                }
            )
        }
    }

    fun deleteMessage(msgId: String) {
        _uiState.update {
            it.copy(
                messages = it.messages.map { msg ->
                    if (msg.msgId == msgId) msg.copy(isRecalled = true) else msg
                }
            )
        }
    }

    fun enterSelectionMode(message: MessageItem) {
        _uiState.update {
            it.copy(
                selectionMode = true,
                selectedMessages = setOf(message)
            )
        }
    }
    
    fun toggleMessageSelection(message: MessageItem) {
        _uiState.update { state ->
            if (!state.selectionMode) return@update state
    
            val newSelected = if (state.selectedMessages.contains(message)) {
                state.selectedMessages - message
            } else {
                state.selectedMessages + message
            }
    
            if (newSelected.isEmpty()) {
                state.copy(
                    selectionMode = false,
                    selectedMessages = emptySet()
                )
            } else {
                state.copy(selectedMessages = newSelected)
            }
        }
    }
    
    fun exitSelectionMode() {
        _uiState.update {
            it.copy(
                selectionMode = false,
                selectedMessages = emptySet()
            )
        }
    }

    fun recallSelectedMessages() {
        val selected = _uiState.value.selectedMessages
        if (selected.isEmpty()) return
    
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
    
            selected.forEach { message ->
                repository.recallMessage(
                    token = token,
                    msgId = message.msgId,
                    chatId = chatId,
                    chatType = chatType
                ).onSuccess {
                    successCount++
                    deleteMessage(message.msgId)
                }.onFailure {
                    failCount++
                }
            }
    
            exitSelectionMode()
    
            when {
                failCount == 0 -> _toastMessage.emit("撤回成功")
                successCount == 0 -> _toastMessage.emit("撤回失败")
                else -> _toastMessage.emit("成功撤回 $successCount 条，失败 $failCount 条")
            }
        }
    }

    fun startDownload(message: MessageItem, context: Context) {
        val fileUrl = message.fileUrl ?: return
        val fileName = message.fileName ?: "file_${System.currentTimeMillis()}"

        viewModelScope.launch {
            _downloadingFiles.update { it + (message.msgId to 0f) }
            _uiState.update { it.copy(downloadedFiles = it.downloadedFiles - message.msgId) }

            downloadFileWithProgress(
                url = fileUrl,
                fileName = fileName,
                context = context,
                onProgress = { progress ->
                    _downloadingFiles.update { it + (message.msgId to progress) }
                },
                onComplete = { savedPath ->
                    _downloadingFiles.update { it - message.msgId }
                    _uiState.update { it.copy(downloadedFiles = it.downloadedFiles + message.msgId) }
                    viewModelScope.launch {
                        _toastMessage.emit("文件已保存到: $savedPath")
                    }
                },
                onError = { error ->
                    _downloadingFiles.update { it - message.msgId }
                    _uiState.update { it.copy(downloadedFiles = it.downloadedFiles - message.msgId) }
                    viewModelScope.launch {
                        _toastMessage.emit("下载失败: $error")
                    }
                }
            )
        }
    }

    fun updateNickName(value: String) = _uiState.update { it.copy(myGroupNickname = value) }

    // ---------- 群成员 / @提及 ----------

    /** 分页加载群成员 */
    fun loadGroupMembers(refresh: Boolean = false) {
        val state = _uiState.value.groupMembers
        if (state.isLoading) return
        if (!refresh && !state.hasMore) return

        val page = if (refresh) 1 else state.page + 1
        _uiState.update { it.copy(groupMembers = it.groupMembers.copy(isLoading = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            groupMemberRepository.listMembers(token, chatId, page = page)
                .onSuccess { members ->
                    _uiState.update {
                        val existing = if (refresh) emptyList() else it.groupMembers.members
                        val existingIds = existing.map { m -> m.userId }.toSet()
                        val merged = existing + members.filter { m -> m.userId !in existingIds }
                        it.copy(
                            groupMembers = it.groupMembers.copy(
                                isLoading = false,
                                members = merged,
                                page = page,
                                hasMore = members.isNotEmpty()
                            )
                        )
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load group members", e)
                    _uiState.update { it.copy(groupMembers = it.groupMembers.copy(isLoading = false)) }
                    _toastMessage.emit("加载群成员失败: ${e.message}")
                }
        }
    }

    /** 长按头像 @某人：不经弹窗直接插入 */
    fun mentionUser(userId: String, name: String) {
        if (chatType != 2 || name.isEmpty()) return
        _uiState.update { state ->
            val text = state.inputText
            val mentionText = "@$name "
            val newText = if (text.contains("@$name")) text else text + mentionText
            state.copy(
                inputText = newText,
                mentions = state.mentions + (name to userId)
            )
        }
    }

    fun showMentionPicker(triggerPos: Int = -1) {
        if (chatType != 2) return
        _uiState.update { it.copy(mentionPicker = MentionPickerState(isVisible = true, triggerPos = triggerPos)) }
        if (_uiState.value.groupMembers.members.isEmpty()) {
            loadGroupMembers(refresh = true)
        }
    }

    fun hideMentionPicker() {
        _uiState.update { it.copy(mentionPicker = MentionPickerState()) }
    }

    fun selectMention(member: GroupMember) {
        _uiState.update { state ->
            val pos = state.mentionPicker.triggerPos
            val text = state.inputText
            val mentionText = "@${member.name} "
            val newText = if (pos in text.indices && text.getOrNull(pos) == '@') {
                text.substring(0, pos) + mentionText + text.substring(pos + 1)
            } else {
                text + mentionText
            }
            state.copy(
                inputText = newText,
                mentions = state.mentions + (member.name to member.userId),
                mentionPicker = MentionPickerState()
            )
        }
    }

    
    fun deleteFriend(
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        viewModelScope.launch {
            friendRepository.deleteFriend(
                token = token,
                id = chatId,
                type = chatType
            ).onSuccess {
                _toastMessage.emit("操作成功")
                onSuccess()
            }.onFailure {
                _toastMessage.emit(it.message ?: "操作失败")
                onFailure()
            }
        }
    }
}

sealed class ButtonEvent {
    data class OpenUrl(val url: String) : ButtonEvent()
    data class CopyText(val text: String) : ButtonEvent()
}

data class RecallDialogState(
    val isOpen: Boolean = false,
    val msgId: String? = null
)

data class StickerPanelState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val expressions: List<ExpressionItem> = emptyList(),
    val stickerPacks: List<StickerPack> = emptyList()
)

fun computeDisplayItems(
    messages: List<MessageItem>,
    chatType: Int,
    ownerId: String?,
    adminIds: Set<String>
): List<MessageDisplayItem> {
    return messages.mapIndexed { index, message ->
        val newer = messages.getOrNull(index - 1)
        val older = messages.getOrNull(index + 1)

        val isFirstFromSender = newer == null
                || newer.contentType == MessageItem.CONTENT_TYPE_TIP
                || newer.senderId != message.senderId

        val isLastFromSender = older == null
                || older.contentType == MessageItem.CONTENT_TYPE_TIP
                || older.senderId != message.senderId

        val roleLabel: String? = when {
            chatType != 2 || message.senderType == 3 -> null
            message.senderId == ownerId -> "群主"
            message.senderId in adminIds -> "管理员"
            else -> null
        }

        MessageDisplayItem(
            message = message,
            isFirstFromSender = isFirstFromSender,
            isLastFromSender = isLastFromSender,
            roleLabel = roleLabel
        )
    }
}
