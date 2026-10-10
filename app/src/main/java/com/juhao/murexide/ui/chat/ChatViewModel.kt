package com.juhao.murexide.ui.chat

import android.net.Uri
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
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
import com.juhao.murexide.repository.ConversationDetailRepository
import com.juhao.murexide.repository.MessageRepository
import com.juhao.murexide.repository.FriendRepository
import com.juhao.murexide.repository.GroupMemberRepository
import com.juhao.murexide.utils.FileDownloader.downloadFileWithProgress
import com.juhao.murexide.data.*
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.utils.MentionUtils
import com.juhao.murexide.utils.QiniuUploadResponse
import com.juhao.murexide.utils.QiniuUploader
import com.juhao.murexide.utils.parseCommaSeparatedInts
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.network.RecallActor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.time.Duration.Companion.milliseconds

@Immutable
internal data class ChatComposerState(
    val text: String = "",
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val mentions: List<MentionToken> = emptyList()
)

internal fun ChatUiState.withoutComposerFields(): ChatUiState = copy(
    inputText = "",
    inputSelectionStart = 0,
    inputSelectionEnd = 0,
    mentions = emptyList()
)

internal fun ChatUiState.toComposerState(): ChatComposerState = ChatComposerState(
    text = inputText,
    selectionStart = inputSelectionStart,
    selectionEnd = inputSelectionEnd,
    mentions = mentions
)

class ChatViewModel(
    val token: String,
    val chatId: String,
    private val chatType: Int,
    private val accountStorage: AccountStorage,
    private val repository: MessageRepository = MessageRepository(),
    private val backgroundRepository: ChatBackgroundRepository = ChatBackgroundRepository(),
    private val stickerRepository: StickerRepository = StickerRepository(),
    private val instructionRepository: InstructionRepository = InstructionRepository(),
    private val friendRepository: FriendRepository = FriendRepository(),
    private val groupMemberRepository: GroupMemberRepository = GroupMemberRepository(),
    private val boardRepository: BoardRepository = BoardRepository(),
    private val conversationDetailRepository: ConversationDetailRepository = ConversationDetailRepository(),
    private val wsManager: WebSocketManager = WebSocketManager.getInstance(),
    private val currentUserId: String = "",
    private val currentUserName: String = "",
    private val currentUserAvatar: String = ""
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val RECALL_CONFIRMATION_GRACE_MS = 1_500L
        private const val HISTORY_PAGE_SIZE = 20
    }

    private var uploadJob: Job? = null
    private var streamPersistJob: Job? = null

    private val msgIdCache = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val screenState: StateFlow<ChatUiState> = _uiState
        .map(ChatUiState::withoutComposerFields)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _uiState.value.withoutComposerFields()
        )
    internal val composerState: StateFlow<ChatComposerState> = _uiState
        .map(ChatUiState::toComposerState)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _uiState.value.toComposerState()
        )

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _buttonEvent = MutableSharedFlow<ButtonEvent>()
    val buttonEvent: SharedFlow<ButtonEvent> = _buttonEvent.asSharedFlow()

    private val _recallDialog = MutableStateFlow(RecallDialogState())
    val recallDialog: StateFlow<RecallDialogState> = _recallDialog.asStateFlow()
    private val pendingRecallConfirmations = mutableMapOf<String, CompletableDeferred<Unit>>()

    private val _stickerPanel = MutableStateFlow(StickerPanelState())
    val stickerPanel: StateFlow<StickerPanelState> = _stickerPanel.asStateFlow()

    private val _instructionForm = MutableStateFlow<InstructionItem?>(null)
    val instructionForm: StateFlow<InstructionItem?> = _instructionForm.asStateFlow()

    private val _downloadingFiles = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingFiles: StateFlow<Map<String, Float>> = _downloadingFiles.asStateFlow()

    private var historyCursorMessageId: String? = null
    private var cachedHistoryCursor: MessageItem? = null
    private var isUsingCachedHistory = false
    private var historyLoadGeneration = 0L
    private var isLoadingMore = false
    private var isLoadingNewer = false
    private val historyLoadMutex = Mutex()

    private var draftSaveJob: Job? = null
    private var draftRestored = false

    init {
        setupWebSocket()
        loadMessages()

        viewModelScope.launch {
            val draft = accountStorage.getDraft(chatId, chatType)
            if (draft.isNotEmpty() && !draftRestored) {
                draftRestored = true
                _uiState.update {
                    it.copy(
                        inputText = draft,
                        inputSelectionStart = draft.length,
                        inputSelectionEnd = draft.length
                    )
                }
            }
        }

        viewModelScope.launch {
            delay(200L.milliseconds)
            if (!isActive) return@launch
            loadBackground()
            when (chatType) {
                1 -> loadUserInfo()
                2 -> { loadGroupInfo(); loadBoard(); loadInstructionData() }
                3 -> { loadBotInfo(); loadBoard(); loadInstructionData() }
            }
        }
    }

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
                            val limitedMsgType = parseCommaSeparatedInts(data?.limited_msg_type)
                            _uiState.update {
                                it.copy(
                                    memberCount = memberCount,
                                    ownerId = ownerId,
                                    adminIds = adminIds,
                                    myGroupNickname = data?.my_group_nickname,
                                    permissionLevel = permissionLevel,
                                    isAdmin = permissionLevel >= 2,
                                    isGag = data?.is_gag ?: false,
                                    gagUntilTimestamp = data?.gag_until_timestamp ?: 0L,
                                    limitedMsgType = limitedMsgType
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
                                it.copy(usageCount = d?.headcount)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bot info", e)
            }
        }
    }

    private fun loadUserInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            conversationDetailRepository.getDetail(token, chatId, chatType).onSuccess { detail ->
                _uiState.update {
                    it.copy(continuousOnlineDay = detail.continuousOnlineDay)
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to load user info", error)
            }
        }
    }

    private fun setupWebSocket() {
        viewModelScope.launch {
            wsManager.messageFlow.collect { event ->
                when (event) {
                    is WebSocketManager.WsEvent.NewMessage -> {
                        val state = _uiState.value
                        val match = event.message.chatId == chatId ||
                            (event.message.chatType == 1 && event.message.senderId == chatId)
                        if (match && !state.hasNewer) {
                            addReceivedMessage(event.message)
                        }
                    }
                    is WebSocketManager.WsEvent.EditMessage -> {
                        if (event.message.chatId == chatId) {
                            updateEditedMessage(event.message)
                        }
                    }
                    is WebSocketManager.WsEvent.StreamContent -> {
                        updateStreamMessage(event.msgId, event.content)
                    }
                    is WebSocketManager.WsEvent.MessageDeleted -> {
                        pendingRecallConfirmations[event.msgId]?.complete(Unit)
                        applyRecalledMessage(event.message, event.actor)
                    }
                    is WebSocketManager.WsEvent.BoardUpdate -> {
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
        val loadGeneration = ++historyLoadGeneration
        historyCursorMessageId = null
        cachedHistoryCursor = null
        isUsingCachedHistory = false
        msgIdCache.clear()
        _uiState.update {
            it.copy(
                isLoading = true,
                hasMore = false,
                error = null
            )
        }

        viewModelScope.launch {
            var initialCachedMessages: List<MessageItem> = emptyList()
            LocalCache.currentAccountId()?.let { accountId ->
                val cached = LocalCache.observeMessages(
                    accountId = accountId,
                    chatId = chatId,
                    chatType = chatType,
                    limit = HISTORY_PAGE_SIZE
                ).first()
                if (loadGeneration != historyLoadGeneration) return@launch
                initialCachedMessages = cached
            }

            repository.getMessageList(
                token = token,
                chatId = chatId,
                chatType = chatType
            ).onSuccess { messages ->
                if (loadGeneration != historyLoadGeneration) return@onSuccess
                val loadedMessages = messages.map(::withCurrentUserProfileFallback)
                val snapshot = resolveServerHistorySnapshot(
                    existingMessages = _uiState.value.messages,
                    serverMessages = loadedMessages
                )
                msgIdCache.clear()
                msgIdCache.addAll(snapshot.messages.map { it.msgId })
                historyCursorMessageId = snapshot.nextAnchorMessageId
                cachedHistoryCursor = null
                isUsingCachedHistory = false

                _uiState.update {
                    it.copy(
                        messages = snapshot.messages,
                        isLoading = false,
                        hasMore = snapshot.hasMore,
                        error = null
                    )
                }
            }.onFailure { error ->
                if (loadGeneration != historyLoadGeneration) return@onFailure
                cachedHistoryCursor = initialCachedMessages.lastOrNull()
                isUsingCachedHistory = cachedHistoryCursor != null
                if (initialCachedMessages.isNotEmpty()) {
                    msgIdCache.clear()
                    msgIdCache.addAll(initialCachedMessages.map { it.msgId })
                    _uiState.update {
                        it.copy(
                            messages = initialCachedMessages,
                            isLoading = false,
                            hasMore = isUsingCachedHistory &&
                                initialCachedMessages.size >= HISTORY_PAGE_SIZE,
                            error = null
                        )
                    }
                    return@onFailure
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasMore = isUsingCachedHistory &&
                            initialCachedMessages.size >= HISTORY_PAGE_SIZE,
                        error = error.message ?: "加载失败"
                    )
                }
            }
        }
    }

    fun loadMore() {
        val hasUsableCursor = if (isUsingCachedHistory) {
            cachedHistoryCursor != null
        } else {
            historyCursorMessageId != null
        }
        if (
            isLoadingMore ||
            !_uiState.value.hasMore ||
            !hasUsableCursor ||
            !historyLoadMutex.tryLock()
        ) return

        viewModelScope.launch {
            try {
                loadOlderMessagesUntil()
            } finally {
                historyLoadMutex.unlock()
            }
        }
    }

    fun loadNewer() {
        if (isLoadingNewer) return

        val state = _uiState.value
        if (!state.hasNewer || state.messages.isEmpty()) return
        if (token.isBlank()) return

        viewModelScope.launch {
            if (isLoadingNewer) return@launch

            isLoadingNewer = true
            _uiState.update { it.copy(isLoadingNewer = true) }

            try {
                val currentMessages = _uiState.value.messages
                if (currentMessages.isEmpty()) return@launch

                val newestMessage = currentMessages.maxByOrNull { it.msgSeq } ?: return@launch

                val result = repository.getMessagesAroundMsgId(
                    token = token,
                    chatId = chatId,
                    chatType = chatType,
                    msgId = newestMessage.msgId,
                    msgSeq = newestMessage.msgSeq
                )

                result.onSuccess { page ->
                    val current = _uiState.value.messages

                    val fresh = page
                        .map(::withCurrentUserProfileFallback)
                        .filter { it.msgId !in msgIdCache }

                    if (fresh.isEmpty()) {
                        _uiState.update { it.copy(hasNewer = false) }
                        return@onSuccess
                    }

                    msgIdCache.addAll(fresh.map { it.msgId })

                    val merged = (current + fresh)
                        .distinctBy { it.msgId }
                        .sortedByDescending { it.timestamp }

                    _uiState.update {
                        it.copy(
                            messages = merged,
                            hasNewer = fresh.size >= HISTORY_PAGE_SIZE
                        )
                    }
                }
            } finally {
                isLoadingNewer = false
                _uiState.update { it.copy(isLoadingNewer = false) }
            }
        }
    }

    fun clearLocatingMessageError() {
        _uiState.update { it.copy(locatingMessageError = null) }
    }

    override fun onCleared() {
        val text = _uiState.value.inputText
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            if (text.isNotBlank()) {
                accountStorage.saveDraft(chatId, chatType, text)
            } else {
                accountStorage.clearDraft(chatId, chatType)
            }
        }
        ActiveConversationRegistry.deactivate(this)
        super.onCleared()
    }

    suspend fun loadQuotedMessage(messageId: String, messageSeq: Long? = null): Boolean {
        if (messageId.isBlank()) return false
        if (_uiState.value.messages.any { it.msgId == messageId }) return true

        _uiState.update { it.copy(locatingMessage = true, locatingMessageError = null) }

        return try {
            historyLoadMutex.withLock {
                if (_uiState.value.messages.any { it.msgId == messageId }) return@withLock true
                if (token.isBlank()) return@withLock false
                loadQuotedMessageByChain(messageId, messageSeq)
            }
        } finally {
            _uiState.update {
                it.copy(
                    locatingMessage = false,
                    locatingMessageError = if (it.messages.any { m -> m.msgId == messageId }) {
                        null
                    } else {
                        "未找到目标消息"
                    }
                )
            }
        }
    }

    private suspend fun loadQuotedMessageByChain(targetMessageId: String, targetMessageSeq: Long?): Boolean {
        val current = _uiState.value.messages
        val minExistingSendTime = current.minOfOrNull { it.timestamp }
        val timeGapThresholdMs = 4 * 60 * 60 * 1000L
        val maxChainIterations = 10

        val accumulated = mutableListOf<MessageItem>()
        val fetchedIds = mutableSetOf<String>()

        var nextMsgId: String? = targetMessageId
        var nextMsgSeq: Long = targetMessageSeq ?: -1L
        var iteration = 0

        while (nextMsgId != null && iteration < maxChainIterations) {
            iteration++

            val page = repository.getMessagesAroundMsgId(
                token = token,
                chatId = chatId,
                chatType = chatType,
                msgId = nextMsgId,
                msgSeq = nextMsgSeq,
                msgCount = 30
            ).getOrElse { emptyList() }

            if (page.isEmpty()) break

            val fresh = page.filter { it.msgId !in fetchedIds }
            if (fresh.isEmpty()) break

            fresh.forEach {
                fetchedIds.add(it.msgId)
                accumulated.add(it)
            }

            val newest = accumulated.maxByOrNull { it.timestamp }
            if (minExistingSendTime == null || newest == null) break

            val timeDiff = minExistingSendTime - newest.timestamp
            if (timeDiff <= timeGapThresholdMs) break

            if (newest.msgId == nextMsgId) break

            nextMsgId = newest.msgId
            nextMsgSeq = newest.msgSeq.takeIf { it > 0L } ?: -1L
        }

        if (accumulated.isEmpty()) return false

        historyLoadGeneration++

        val combined = accumulated
            .distinctBy { it.msgId }
            .sortedByDescending { it.timestamp }

        msgIdCache.clear()
        msgIdCache.addAll(combined.map { it.msgId })

        val oldest = combined.minByOrNull { it.timestamp }
        historyCursorMessageId = oldest?.msgId
        cachedHistoryCursor = null
        isUsingCachedHistory = false

        _uiState.update {
            it.copy(
                messages = combined,
                hasMore = oldest != null,
                hasNewer = true,
                isLoading = false,
                error = null
            )
        }

        return _uiState.value.messages.any { it.msgId == targetMessageId }
    }

    private suspend fun loadOlderMessagesUntil(targetMessageId: String? = null): Boolean {
        if (targetMessageId != null && _uiState.value.messages.any { it.msgId == targetMessageId }) {
            return true
        }
        if (!_uiState.value.hasMore) {
            if (targetMessageId != null) {
                _toastMessage.emit("原消息已删除或不可查看")
            }
            return false
        }

        isLoadingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }
        val loadGeneration = historyLoadGeneration

        try {
            if (isUsingCachedHistory) {
                return loadOlderCachedMessagesUntil(
                    targetMessageId = targetMessageId,
                    loadGeneration = loadGeneration
                )
            }

            while (true) {
                if (loadGeneration != historyLoadGeneration) return false
                val anchorMessageId = historyCursorMessageId
                    ?: return quotedMessageUnavailable(targetMessageId)

                val result = repository.getMessageList(
                    token = token,
                    chatId = chatId,
                    chatType = chatType,
                    msgId = anchorMessageId,
                    size = 50
                )
                if (loadGeneration != historyLoadGeneration) return false
                val error = result.exceptionOrNull()
                if (error != null) {
                    cachedHistoryCursor = _uiState.value.messages.lastOrNull()
                    isUsingCachedHistory = cachedHistoryCursor != null &&
                        LocalCache.currentAccountId() != null
                    if (isUsingCachedHistory) {
                        return loadOlderCachedMessagesUntil(
                            targetMessageId = targetMessageId,
                            loadGeneration = loadGeneration
                        )
                    }
                    if (targetMessageId != null) {
                        _toastMessage.emit("加载原消息失败")
                    }
                    return false
                }

                val resolvedMessages = result.getOrThrow().map(::withCurrentUserProfileFallback)
                if (resolvedMessages.isEmpty()) {
                    _uiState.update { it.copy(hasMore = false) }
                    return quotedMessageUnavailable(targetMessageId)
                }

                val page = resolveOlderMessagePage(
                    knownMessageIds = msgIdCache,
                    currentAnchorMessageId = anchorMessageId,
                    messages = resolvedMessages
                )
                val newMessages = page.newMessages
                if (newMessages.isNotEmpty()) {
                    msgIdCache.addAll(newMessages.map { it.msgId })
                    _uiState.update {
                        it.copy(messages = it.messages + newMessages, hasMore = true)
                    }
                }
                historyCursorMessageId = page.nextAnchorMessageId

                if (targetMessageId != null &&
                    _uiState.value.messages.any { it.msgId == targetMessageId }
                ) {
                    return true
                }

                if (!page.madeCursorProgress) {
                    _uiState.update { it.copy(hasMore = false) }
                    return quotedMessageUnavailable(targetMessageId)
                }

                if (targetMessageId == null) return true
            }
        } finally {
            isLoadingMore = false
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    private suspend fun loadOlderCachedMessagesUntil(
        targetMessageId: String?,
        loadGeneration: Long
    ): Boolean {
        val accountId = LocalCache.currentAccountId()
            ?: return quotedMessageUnavailable(targetMessageId)

        while (true) {
            if (loadGeneration != historyLoadGeneration || !isUsingCachedHistory) return false
            val anchorMessage = cachedHistoryCursor
                ?: return quotedMessageUnavailable(targetMessageId)
            val cachedMessages = LocalCache.getCachedMessagesBefore(
                accountId = accountId,
                chatId = chatId,
                chatType = chatType,
                beforeTimestamp = anchorMessage.timestamp,
                beforeMsgSeq = anchorMessage.msgSeq,
                beforeMsgId = anchorMessage.msgId,
                limit = HISTORY_PAGE_SIZE
            )
            if (loadGeneration != historyLoadGeneration || !isUsingCachedHistory) return false
            if (cachedMessages.isEmpty()) {
                cachedHistoryCursor = null
                _uiState.update { it.copy(hasMore = false) }
                return quotedMessageUnavailable(targetMessageId)
            }

            val page = resolveCachedHistoryPage(
                knownMessageIds = msgIdCache,
                currentAnchorMessageId = anchorMessage.msgId,
                messages = cachedMessages,
                pageSize = HISTORY_PAGE_SIZE
            )
            cachedHistoryCursor = page.nextAnchorMessage
            if (page.newMessages.isNotEmpty()) {
                msgIdCache.addAll(page.newMessages.map { it.msgId })
            }
            _uiState.update {
                it.copy(
                    messages = it.messages + page.newMessages,
                    hasMore = page.hasMore
                )
            }

            if (targetMessageId != null &&
                _uiState.value.messages.any { it.msgId == targetMessageId }
            ) {
                return true
            }
            if (!page.madeCursorProgress || !page.hasMore) {
                return quotedMessageUnavailable(targetMessageId)
            }
            if (targetMessageId == null) return true
        }
    }

    private suspend fun quotedMessageUnavailable(targetMessageId: String?): Boolean {
        if (targetMessageId != null) {
            _toastMessage.emit("原消息已删除或不可查看")
        }
        return false
    }

    fun refresh() {
        _uiState.update { it.copy(hasNewer = false) }
        loadMessages()
    }

    fun updateInputText(
        text: String,
        mentions: List<MentionToken>,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        val state = _uiState.value
        val safeStart = selectionStart.coerceIn(0, text.length)
        val safeEnd = selectionEnd.coerceIn(0, text.length)
        if (
            state.inputText == text &&
            state.mentions == mentions &&
            state.inputSelectionStart == safeStart &&
            state.inputSelectionEnd == safeEnd
        ) return
        _uiState.update {
            it.copy(
                inputText = text,
                mentions = mentions,
                inputSelectionStart = safeStart,
                inputSelectionEnd = safeEnd
            )
        }

        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(500L.milliseconds)
            accountStorage.saveDraft(chatId, chatType, text)
        }
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
        if (state.inputText.isEmpty() || state.isSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val contentType = when (sendTypeOverride ?: state.sendType) {
                "markdown" -> MessageItem.CONTENT_TYPE_MARKDOWN
                "html" -> MessageItem.CONTENT_TYPE_HTML
                else -> MessageItem.CONTENT_TYPE_TEXT
            }
            val mentionedIds = MentionUtils.mentionedUserIds(state.inputText, state.mentions)

            val content = MessageContent(
                text = state.inputText,
                mentionedId = mentionedIds,
                quoteMsgText = state.replyTo?.let { "${it.senderName}: ${it.content}" },
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
            ).onSuccess { msgId ->
                accountStorage.clearDraft(chatId, chatType)
                addSentMessage(
                    msgId = msgId,
                    content = content,
                    contentType = contentType,
                    quoteMsgId = state.replyTo?.msgId,
                    commandId = state.pendingCommandId,
                    commandName = state.pendingCommandName
                )
                _uiState.update {
                    it.copy(
                        inputText = "",
                        inputSelectionStart = 0,
                        inputSelectionEnd = 0,
                        replyTo = null,
                        isSending = false,
                        mentions = emptyList(),
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

    fun uploadAndSendMedia(uris: List<Uri>, context: Context) {
        if (uris.isEmpty()) return
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            uris.forEach { uri ->
                val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
                if (uploadSingleMedia(uri, context, isVideo)) successCount++ else failCount++
            }
            if (failCount > 0) {
                _toastMessage.emit(
                    if (uris.size > 1) "已发送 $successCount 项，$failCount 项失败" else "发送失败"
                )
            }
        }
    }

    private suspend fun uploadSingleMedia(
        uri: Uri,
        context: Context,
        isVideo: Boolean
    ): Boolean {
        _uiState.update {
            it.copy(
                isUploading = true,
                uploadProgress = 0f,
                uploadImagePath = uri.toString(),
                isSending = false
            )
        }

        return try {
            val uploader = QiniuUploader(
                context = context,
                userToken = token,
                uploadType = if (isVideo) 2 else 1,
                enableWebp = !isVideo
            )

            val result = uploader.uploadFromUri(
                context = context,
                uri = uri,
                onProgress = { progress ->
                    _uiState.update { it.copy(uploadProgress = progress) }
                }
            )

            if (!currentCoroutineContext().isActive) {
                _uiState.update {
                    it.copy(isUploading = false, uploadProgress = 0f, uploadImagePath = null)
                }
                return false
            }

            result.onSuccess { response ->
                _uiState.update {
                    it.copy(isUploading = false, uploadProgress = 1f, uploadImagePath = null)
                }
                if (isVideo) sendVideoMessage(response) else sendImageMessage(response)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isUploading = false, uploadProgress = 0f, uploadImagePath = null)
                }
                val prefix = if (isVideo) "视频" else "图片"
                _toastMessage.emit("${prefix}上传失败: ${error.message}")
            }

            result.isSuccess
        } catch (e: CancellationException) {
            _uiState.update {
                it.copy(isUploading = false, uploadProgress = 0f, uploadImagePath = null)
            }
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isUploading = false, uploadProgress = 0f, uploadImagePath = null)
            }
            _toastMessage.emit("上传失败: ${e.message}")
            false
        }
    }

    private fun sendVideoMessage(upload: QiniuUploadResponse) {
        val state = _uiState.value

        viewModelScope.launch {
            val content = MessageContent(
                video = upload.key,
                text = "",
                quoteMsgText = state.replyTo?.let { "${it.senderName}: ${it.content}" },
                quoteImageUrl = state.replyTo?.imageUrl,
                quoteImageName = state.replyTo?.imageUrl?.toUri()?.lastPathSegment,
                media = upload.toMessageMedia()
            )

            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_VIDEO,
                quoteMsgId = state.replyTo?.msgId
            ).onSuccess { msgId ->
                accountStorage.clearDraft(chatId, chatType)
                addSentMessage(
                    msgId = msgId,
                    content = content,
                    contentType = MessageItem.CONTENT_TYPE_VIDEO,
                    quoteMsgId = state.replyTo?.msgId
                )
                _uiState.update { it.copy(replyTo = null, isSending = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit("发送失败: ${error.message}")
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        _uiState.update {
            it.copy(isUploading = false, uploadProgress = 0f, uploadImagePath = null)
        }
    }

    private fun sendImageMessage(upload: QiniuUploadResponse) {
        val state = _uiState.value

        viewModelScope.launch {
            val content = MessageContent(
                image = upload.key,
                text = "",
                quoteMsgText = state.replyTo?.let { "${it.senderName}: ${it.content}" },
                quoteImageUrl = state.replyTo?.imageUrl,
                quoteImageName = state.replyTo?.imageUrl?.toUri()?.lastPathSegment,
                media = upload.toMessageMedia()
            )

            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_IMAGE,
                quoteMsgId = state.replyTo?.msgId
            ).onSuccess { msgId ->
                accountStorage.clearDraft(chatId, chatType)
                addSentMessage(
                    msgId = msgId,
                    content = content,
                    contentType = MessageItem.CONTENT_TYPE_IMAGE,
                    quoteMsgId = state.replyTo?.msgId
                )
                _uiState.update { it.copy(replyTo = null, isSending = false) }
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
                        it.copy(isUploading = false, uploadProgress = 0f, uploadImagePath = null)
                    }
                    return@launch
                }

                result.onSuccess { response ->
                    _uiState.update {
                        it.copy(isUploading = false, uploadProgress = 1f, uploadImagePath = null)
                    }
                    sendFileMessage(response.key, response.fsize, uri, context)
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(isUploading = false, uploadProgress = 0f, uploadImagePath = null)
                    }
                    _toastMessage.emit("文件上传失败: ${error.message}")
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(isUploading = false, uploadProgress = 0f, uploadImagePath = null)
                }
                _toastMessage.emit("已取消上传")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUploading = false, uploadProgress = 0f, uploadImagePath = null)
                }
                _toastMessage.emit("上传失败: ${e.message}")
            }
        }
    }

    private fun sendFileMessage(fileUrl: String, fileSize: Long, uri: Uri, context: Context) {
        val state = _uiState.value

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
                quoteMsgId = state.replyTo?.msgId
            ).onSuccess { msgId ->
                accountStorage.clearDraft(chatId, chatType)
                addSentMessage(
                    msgId = msgId,
                    content = content,
                    contentType = MessageItem.CONTENT_TYPE_FILE,
                    quoteMsgId = state.replyTo?.msgId
                )
                _uiState.update { it.copy(replyTo = null, isSending = false) }
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
        if (_recallDialog.value.isSubmitting) return
        _recallDialog.value = RecallDialogState(isOpen = true, msgId = msgId)
    }

    fun hideRecallDialog() {
        if (_recallDialog.value.isSubmitting) return
        _recallDialog.value = RecallDialogState(isOpen = false)
    }

    fun recallMessage() {
        val dialog = _recallDialog.value
        val msgId = dialog.msgId ?: return
        if (dialog.isSubmitting || msgId in pendingRecallConfirmations) return

        val recalledMessage = _uiState.value.messages.firstOrNull { it.msgId == msgId }
        if (recalledMessage == null || recalledMessage.isRecalled) {
            _recallDialog.value = RecallDialogState()
            return
        }

        val confirmation = CompletableDeferred<Unit>()
        pendingRecallConfirmations[msgId] = confirmation
        _recallDialog.value = dialog.copy(isSubmitting = true)

        viewModelScope.launch {
            val result = repository.recallMessage(
                token = token,
                msgId = msgId,
                chatId = chatId,
                chatType = chatType
            )
            val confirmedByWebSocket = if (result.isFailure) {
                withTimeoutOrNull(RECALL_CONFIRMATION_GRACE_MS.milliseconds) {
                    confirmation.await()
                    true
                } ?: false
            } else {
                false
            }
            if (pendingRecallConfirmations[msgId] === confirmation) {
                pendingRecallConfirmations.remove(msgId)
            }

            if (result.isSuccess) {
                val actor = currentRecallActor()
                applyRecalledMessage(recalledMessage.copy(isRecalled = true), actor)
                wsManager.publishLocalMessageRecalled(recalledMessage, actor)
                closeRecallDialog()
                _toastMessage.emit("撤回成功")
            } else if (confirmedByWebSocket) {
                closeRecallDialog()
                _toastMessage.emit("撤回成功")
            } else {
                _recallDialog.update { state ->
                    if (state.msgId == msgId) state.copy(isSubmitting = false) else state
                }
                val error = result.exceptionOrNull()
                _toastMessage.emit("撤回失败: ${error?.message ?: "未知错误"}")
                Log.e(TAG, "recall-msg failed: msgId=$msgId", error)
            }
        }
    }

    private fun closeRecallDialog() {
        _recallDialog.value = RecallDialogState()
    }

    private fun currentRecallActor(): RecallActor {
        return RecallActor(
            id = currentUserId.ifBlank { wsManager.loggedInUserId.orEmpty() },
            name = currentUserName
        )
    }

    fun startEditMessage(message: MessageItem) {
        hideStickerPanel()
        hideInstructionPanel()
        _uiState.update {
            it.copy(
                editingMessage = message,
                inputText = message.content,
                inputSelectionStart = message.content.length,
                inputSelectionEnd = message.content.length,
                mentions = emptyList(),
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

    fun cancelEdit() {
        _uiState.update {
            it.copy(
                editingMessage = null,
                inputText = "",
                inputSelectionStart = 0,
                inputSelectionEnd = 0,
                mentions = emptyList(),
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
                accountStorage.clearDraft(chatId, chatType)
                _uiState.update {
                    it.copy(
                        isSending = false,
                        editingMessage = null,
                        inputText = "",
                        inputSelectionStart = 0,
                        inputSelectionEnd = 0,
                        mentions = emptyList(),
                        sendType = "text"
                    )
                }
                val editedMessage = message.copy(
                    content = newContent,
                    contentType = contentType,
                    isEdited = true
                )
                updateEditedMessage(editedMessage)
                wsManager.publishLocalMessageEdited(editedMessage)
                _toastMessage.emit("编辑成功")
            }.onFailure { error ->
                _uiState.update { it.copy(isSending = false) }
                _toastMessage.emit("编辑失败: ${error.message}")
                error.printStackTrace()
            }
        }
    }

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

    fun sendExpression(expression: ExpressionItem) {
        sendStickerMessage(
            imageUrl = expression.url,
            expressionId = expression.id.toString()
        )
    }

    fun sendStickerItem(item: StickerItem) {
        sendStickerMessage(
            imageUrl = item.url,
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
            quoteMsgText = state.replyTo?.let { "${it.senderName}: ${it.content}" },
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
                quoteMsgId = state.replyTo?.msgId
            ).onSuccess { msgId ->
                accountStorage.clearDraft(chatId, chatType)
                addSentMessage(
                    msgId = msgId,
                    content = content,
                    contentType = MessageItem.CONTENT_TYPE_STICKER,
                    quoteMsgId = state.replyTo?.msgId
                )
                hideStickerPanel()
                _uiState.update { it.copy(replyTo = null) }
            }.onFailure { error ->
                _toastMessage.emit("表情发送失败: ${error.message}")
                error.printStackTrace()
            }
        }
    }

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
                    bots = listOf(BotItem(id = chatId, name = state.chatName, avatarUrl = state.chatAvatar))
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

    fun onInstructionClick(item: InstructionItem) {
        when (item.type) {
            2 -> sendInstructionDirect(item)
            5 -> _instructionForm.value = item
            else -> {
                hideInstructionPanel()
                _uiState.update {
                    it.copy(
                        inputText = item.defaultText,
                        inputSelectionStart = item.defaultText.length,
                        inputSelectionEnd = item.defaultText.length,
                        mentions = emptyList(),
                        pendingCommandId = item.id,
                        pendingCommandName = item.name,
                        pendingCommandHint = item.hintText
                    )
                }
                viewModelScope.launch {
                    accountStorage.saveDraft(chatId, chatType, item.defaultText)
                }
            }
        }
    }

    private fun sendInstructionDirect(item: InstructionItem) {
        viewModelScope.launch {
            val content = MessageContent()
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                commandId = item.id
            ).onSuccess { msgId ->
                accountStorage.clearDraft(chatId, chatType)
                addSentMessage(
                    msgId = msgId,
                    content = content,
                    contentType = MessageItem.CONTENT_TYPE_TEXT,
                    quoteMsgId = null,
                    commandId = item.id,
                    commandName = item.name
                )
                hideInstructionPanel()
            }.onFailure { error ->
                _toastMessage.emit("指令发送失败: ${error.message}")
            }
        }
    }

    fun submitInstructionForm(item: InstructionItem, formJson: String) {
        viewModelScope.launch {
            val content = MessageContent(form = formJson)
            repository.sendMessage(
                token = token,
                chatId = chatId,
                chatType = chatType,
                content = content,
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                commandId = item.id
            ).onSuccess { msgId ->
                accountStorage.clearDraft(chatId, chatType)
                addSentMessage(
                    msgId = msgId,
                    content = content,
                    contentType = MessageItem.CONTENT_TYPE_TEXT,
                    quoteMsgId = null,
                    commandId = item.id,
                    commandName = item.name
                )
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
        val resolvedMessage = withCurrentUserProfileFallback(message)
        if (resolvedMessage.msgId in msgIdCache) {
            _uiState.update { state ->
                state.copy(messages = upsertNewestMessage(state.messages, resolvedMessage))
            }
            return
        }

        if (resolvedMessage.isRecalled) {
            return
        }

        msgIdCache.add(resolvedMessage.msgId)
        _uiState.update {
            it.copy(messages = upsertNewestMessage(it.messages, resolvedMessage))
        }
    }

    private fun withCurrentUserProfileFallback(message: MessageItem): MessageItem {
        if (!message.hasReliableSender) return message

        val belongsToCurrentUser = message.isMine ||
            currentUserId.isNotBlank() && message.senderId == currentUserId
        if (!belongsToCurrentUser) return message

        return message.copy(
            senderId = message.senderId.ifBlank {
                currentUserId.ifBlank { wsManager.loggedInUserId.orEmpty() }
            },
            senderName = message.senderName.ifBlank { currentUserName },
            senderAvatar = message.senderAvatar.ifBlank { currentUserAvatar }
        )
    }

    private fun addSentMessage(
        msgId: String,
        content: MessageContent,
        contentType: Int,
        quoteMsgId: String?,
        commandId: Long? = null,
        commandName: String? = null
    ) {
        val state = _uiState.value
        if (msgId in msgIdCache || state.hasNewer) return

        val ownMessage = _uiState.value.messages.firstOrNull { it.isMine }
        val message = createOutgoingMessage(
            msgId = msgId,
            senderId = currentUserId.ifBlank {
                wsManager.loggedInUserId ?: ownMessage?.senderId.orEmpty()
            },
            senderName = currentUserName.ifBlank { ownMessage?.senderName.orEmpty() },
            senderAvatar = currentUserAvatar.ifBlank { ownMessage?.senderAvatar.orEmpty() },
            chatId = chatId,
            chatType = chatType,
            content = content,
            contentType = contentType,
            quoteMsgId = quoteMsgId,
            commandId = commandId,
            commandName = commandName
        )
        addReceivedMessage(message)
        wsManager.publishLocalMessageSent(message)
    }

    fun updateStreamMessage(msgId: String, content: String) {
        _uiState.update {
            it.copy(
                messages = it.messages.map { msg ->
                    if (msg.msgId == msgId) msg.copy(content = msg.content + content) else msg
                }
            )
        }
        streamPersistJob?.cancel()
        streamPersistJob = viewModelScope.launch {
            delay(500L.milliseconds)
            val message = _uiState.value.messages.firstOrNull { it.msgId == msgId } ?: return@launch
            LocalCache.currentAccountId()?.let { accountId ->
                LocalCache.cacheMessages(accountId, listOf(message))
            }
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
                            buttons = message.buttons,
                            updateTimestamp = maxOf(msg.updateTimestamp, message.updateTimestamp)
                        )
                    else
                        msg
                }
            )
        }
        val updated = _uiState.value.messages.firstOrNull { it.msgId == message.msgId } ?: return
        viewModelScope.launch {
            LocalCache.currentAccountId()?.let { accountId ->
                LocalCache.cacheMessages(accountId, listOf(updated))
                LocalCache.applyMessageMutationToConversation(accountId, updated, recalled = false)
            }
        }
    }

    private fun applyRecalledMessage(
        recalledMessage: MessageItem,
        actor: RecallActor? = null
    ) {
        _uiState.update {
            it.copy(messages = it.messages.withRecalledMessage(recalledMessage, actor))
        }
        val updated = _uiState.value.messages.firstOrNull { it.msgId == recalledMessage.msgId } ?: return
        viewModelScope.launch {
            LocalCache.currentAccountId()?.let { accountId ->
                LocalCache.cacheMessages(accountId, listOf(updated))
                LocalCache.applyMessageMutationToConversation(accountId, updated, recalled = true)
            }
        }
    }

    fun enterSelectionMode(message: MessageItem) {
        _uiState.update {
            it.copy(selectionMode = true, selectedMessages = setOf(message))
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
                state.copy(selectionMode = false, selectedMessages = emptySet())
            } else {
                state.copy(selectedMessages = newSelected)
            }
        }
    }

    fun removeForwardedMessage(msgId: String) {
        if (msgId.isBlank()) return
        _uiState.update { state ->
            if (msgId !in state.selectedMessages.map { it.msgId }) return@update state
            val remaining = state.selectedMessages.filterNot { it.msgId == msgId }.toSet()
            state.copy(
                selectionMode = remaining.isNotEmpty(),
                selectedMessages = remaining
            )
        }
    }

    fun exitSelectionMode() {
        _uiState.update {
            it.copy(selectionMode = false, selectedMessages = emptySet())
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
                    val actor = currentRecallActor()
                    applyRecalledMessage(message.copy(isRecalled = true), actor)
                    wsManager.publishLocalMessageRecalled(message, actor)
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
        if (message.msgId in _downloadingFiles.value) return

        _downloadingFiles.update { it + (message.msgId to 0f) }
        _uiState.update { it.copy(downloadedFiles = it.downloadedFiles - message.msgId) }

        viewModelScope.launch {
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
                    viewModelScope.launch { _toastMessage.emit("文件已保存到: $savedPath") }
                },
                onError = { error ->
                    _downloadingFiles.update { it - message.msgId }
                    _uiState.update { it.copy(downloadedFiles = it.downloadedFiles - message.msgId) }
                    viewModelScope.launch { _toastMessage.emit("下载失败: $error") }
                }
            )
        }
    }

    fun updateNickName(value: String) = _uiState.update { it.copy(myGroupNickname = value) }

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

    fun mentionUser(userId: String, name: String) {
        if (chatType != 2 || name.isEmpty()) return
        _uiState.update { state ->
            if (MentionUtils.mentionedUserIds(state.inputText, state.mentions).contains(userId)) {
                state
            } else {
                val result = MentionUtils.insertMention(
                    text = state.inputText,
                    mentions = state.mentions,
                    userId = userId,
                    displayName = name
                )
                state.copy(
                    inputText = result.text,
                    mentions = result.mentions,
                    inputSelectionStart = result.selection.start,
                    inputSelectionEnd = result.selection.end
                )
            }
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
            val result = MentionUtils.insertMention(
                text = state.inputText,
                mentions = state.mentions,
                userId = member.userId,
                displayName = member.name,
                triggerPos = state.mentionPicker.triggerPos
            )
            state.copy(
                inputText = result.text,
                mentions = result.mentions,
                inputSelectionStart = result.selection.start,
                inputSelectionEnd = result.selection.end,
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
    val msgId: String? = null,
    val isSubmitting: Boolean = false
)

internal fun List<MessageItem>.withRecalledMessage(
    recalledMessage: MessageItem,
    actor: RecallActor? = null
): List<MessageItem> {
    return map { existing ->
        if (existing.msgId != recalledMessage.msgId) return@map existing

        existing.copy(
            isRecalled = true,
            deleteTime = recalledMessage.deleteTime.takeIf { it > 0 } ?: existing.deleteTime,
            recalledById = actor?.id?.takeIf(String::isNotBlank)
                ?: recalledMessage.recalledById
                ?: existing.recalledById,
            recalledByName = actor?.name?.takeIf(String::isNotBlank)
                ?: recalledMessage.recalledByName
                ?: existing.recalledByName,
            updateTimestamp = maxOf(existing.updateTimestamp, recalledMessage.updateTimestamp)
        )
    }
}

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