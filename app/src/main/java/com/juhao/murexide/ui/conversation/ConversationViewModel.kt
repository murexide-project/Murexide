package com.juhao.murexide.ui.conversation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.ActiveConversationRegistry
import com.juhao.murexide.data.LatestMessageRelation
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.StickyItem
import com.juhao.murexide.data.findConversationFor
import com.juhao.murexide.data.relationToLatest
import com.juhao.murexide.data.withEditedLatestMessage
import com.juhao.murexide.data.withLatestMessage
import com.juhao.murexide.data.withLatestMessageIdentity
import com.juhao.murexide.data.withRecalledLatestMessage
import com.juhao.murexide.data.withStreamedLatestMessage
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.ConversationRepository
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import com.juhao.murexide.ui.theme.UiCache
import com.juhao.murexide.utils.AppForegroundState


sealed class ConversationUiState {
    object Loading : ConversationUiState()
    data class Success(
        val conversations: List<ConversationItem>,
        val stickyConversations: List<StickyItem> = emptyList()
    ) : ConversationUiState()
    data class Error(val message: String) : ConversationUiState()
}

class ConversationViewModel(
    private val token: String,
    private val accountId: String,
    private val repository: ConversationRepository = ConversationRepository(),
    private val wsManager: WebSocketManager = WebSocketManager.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Loading)
    val uiState: StateFlow<ConversationUiState> = _uiState

    private val _isWsConnected = MutableStateFlow(true)
    val isWsConnected: StateFlow<Boolean> = _isWsConnected

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing
    
    private val _stickyIds = MutableStateFlow<Set<String>>(emptySet())
    val stickyIds: StateFlow<Set<String>> = _stickyIds.asStateFlow()

    private var loadJob: Job? = null
    private var loadGeneration = 0
    private val resolvingLatestMutations = mutableSetOf<String>()
    private var foregroundSyncEnabled = false

    init {
        observeCachedConversations()
        observeWebSocket()
        observeWsConnection()
        observeAppForeground()
    }

    private fun observeCachedConversations() {
        viewModelScope.launch {
            LocalCache.observeConversations(accountId).collect { cached ->
                if (cached.isNotEmpty() || _uiState.value is ConversationUiState.Success) {
                    val sticky = UiCache.stickyConversations.value ?: emptyList()
                    _uiState.value = ConversationUiState.Success(cached, sticky)
                    syncConversationCache()
                }
            }
        }
        viewModelScope.launch {
            LocalCache.observeSticky(accountId).collect { cached ->
                val ids = cached.map { it.chatId }.toSet()
                _stickyIds.value = ids
                
                _uiState.update { state ->
                    if (state is ConversationUiState.Success) state.copy(stickyConversations = cached) else state
                }
                UiCache.stickyConversations.value = cached
            }
        }
    }

    private fun observeAppForeground() {
        viewModelScope.launch {
            AppForegroundState.returnedToForeground.collect {
                if (foregroundSyncEnabled) refresh()
            }
        }
    }

    fun setForegroundSyncEnabled(enabled: Boolean) {
        if (foregroundSyncEnabled == enabled) return
        foregroundSyncEnabled = enabled
        if (enabled) refresh()
    }

    private fun observeWsConnection() {
        viewModelScope.launch {
            wsManager.connectionState.collect { connected ->
                _isWsConnected.value = connected
            }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            wsManager.messageFlow.collect { event ->
                when (event) {
                    is WebSocketManager.WsEvent.NewMessage -> handleNewMessage(event.message)
                    is WebSocketManager.WsEvent.LocalMessageSent -> handleNewMessage(event.message)
                    is WebSocketManager.WsEvent.LatestMessageResolved -> {
                        handleResolvedLatestMessage(event.message)
                    }
                    is WebSocketManager.WsEvent.EditMessage -> handleEditedMessage(event.message)
                    is WebSocketManager.WsEvent.StreamContent -> {
                        handleStreamContent(event.msgId, event.content)
                    }
                    is WebSocketManager.WsEvent.MessageDeleted -> {
                        handleRecalledMessage(event.message)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun handleNewMessage(message: MessageItem) {
        var conversationMissing = false
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                val conversations = state.conversations.withLatestMessage(
                    message = message,
                    incrementUnread = ActiveConversationRegistry.shouldIncrementUnread(message)
                )
                if (conversations != null) {
                    state.copy(conversations = conversations)
                } else {
                    conversationMissing = true
                    state
                }
            } else {
                state
            }
        }

        if (conversationMissing) {
            refresh()
        } else {
            syncConversationCache()
        }
    }

    private fun handleEditedMessage(message: MessageItem) {
        handleLatestMutation(message = message, recalled = false)
    }

    private fun handleLatestMutation(message: MessageItem, recalled: Boolean) {
        val state = _uiState.value as? ConversationUiState.Success ?: return
        val conversation = state.conversations.findConversationFor(message) ?: return

        when (conversation.relationToLatest(message)) {
            LatestMessageRelation.MATCHES -> applyLatestMutation(message, recalled)
            LatestMessageRelation.DIFFERENT -> Unit
            LatestMessageRelation.UNKNOWN -> resolveLatestMutation(
                conversation = conversation,
                eventMessage = message,
                recalled = recalled
            )
        }
    }

    private fun applyLatestMutation(message: MessageItem, recalled: Boolean) {
        _uiState.update { state ->
            if (state !is ConversationUiState.Success) return@update state
            val conversations = if (recalled) {
                state.conversations.withRecalledLatestMessage(message.copy(isRecalled = true))
            } else {
                state.conversations.withEditedLatestMessage(message.copy(isEdited = true))
            }
            state.copy(conversations = conversations)
        }
        syncConversationCache()
    }

    private fun resolveLatestMutation(
        conversation: ConversationItem,
        eventMessage: MessageItem,
        recalled: Boolean
    ) {
        val resolutionKey = buildString {
            append(conversation.chatType)
            append(':')
            append(conversation.chatId)
            append(':')
            append(eventMessage.msgId)
            append(':')
            append(recalled)
        }
        if (!resolvingLatestMutations.add(resolutionKey)) return

        viewModelScope.launch {
            try {
                val latest = repository.getLatestMessage(
                    token = token,
                    chatId = conversation.chatId,
                    chatType = conversation.chatType
                ).getOrNull() ?: return@launch

                if (latest.msgId == eventMessage.msgId) {
                    val resolvedMutation = if (recalled) {
                        latest.copy(
                            isRecalled = true,
                            deleteTime = maxOf(latest.deleteTime, eventMessage.deleteTime),
                            updateTimestamp = maxOf(
                                latest.updateTimestamp,
                                eventMessage.updateTimestamp
                            )
                        )
                    } else {
                        latest.copy(
                            content = eventMessage.content.takeIf { it.isNotEmpty() }
                                ?: latest.content,
                            contentType = eventMessage.contentType.takeIf { it > 0 }
                                ?: latest.contentType,
                            isEdited = true,
                            buttons = eventMessage.buttons.takeIf { it.isNotEmpty() }
                                ?: latest.buttons,
                            updateTimestamp = maxOf(
                                latest.updateTimestamp,
                                eventMessage.updateTimestamp
                            )
                        )
                    }
                    applyLatestMutation(resolvedMutation, recalled)
                } else {
                    _uiState.update { currentState ->
                        if (currentState is ConversationUiState.Success) {
                            currentState.copy(
                                conversations = currentState.conversations
                                    .withLatestMessageIdentity(latest)
                            )
                        } else {
                            currentState
                        }
                    }
                    syncConversationCache()
                }
            } finally {
                resolvingLatestMutations.remove(resolutionKey)
            }
        }
    }

    private fun handleResolvedLatestMessage(message: MessageItem) {
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                state.conversations.withLatestMessage(
                    message = message,
                    incrementUnread = false
                )?.let { state.copy(conversations = it) } ?: state
            } else {
                state
            }
        }
        syncConversationCache()
    }

    private fun handleStreamContent(msgId: String, content: String) {
        _uiState.update { state ->
            if (state is ConversationUiState.Success) {
                state.copy(
                    conversations = state.conversations.withStreamedLatestMessage(
                        msgId = msgId,
                        content = content
                    )
                )
            } else {
                state
            }
        }
        syncConversationCache()
    }

    private fun handleRecalledMessage(message: MessageItem) {
        handleLatestMutation(message = message, recalled = true)
    }

    private fun syncConversationCache() {
        val state = _uiState.value
        if (state is ConversationUiState.Success) {
            UiCache.conversation.value = state.conversations
            UiCache.stickyConversations.value = state.stickyConversations
        }
    }

    fun loadConversations(refreshPreviews: Boolean = false) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            val hadVisibleConversations = _uiState.value is ConversationUiState.Success
            _isRefreshing.value = true
            if (!hadVisibleConversations) {
                _uiState.value = ConversationUiState.Loading
            }
            fetchStickyList()
            repository.syncCachedConversations(
                token = token,
                accountId = accountId,
                forceRefresh = refreshPreviews
            ).onSuccess {
                if (generation != loadGeneration) return@onSuccess
                _isRefreshing.value = false
                if (_uiState.value is ConversationUiState.Loading) {
                    val sticky = UiCache.stickyConversations.value ?: emptyList()
                    _uiState.value = ConversationUiState.Success(emptyList(), sticky)
                    syncConversationCache()
                }
            }.onFailure { error ->
                if (generation != loadGeneration) return@onFailure
                _isRefreshing.value = false
                if (_uiState.value !is ConversationUiState.Success) {
                    _uiState.value = ConversationUiState.Error(error.message ?: "加载失败")
                } else {
                    Log.w("ConversationViewModel", "Conversation refresh failed", error)
                }
            }
        }
    }

    private fun fetchStickyList() {
        viewModelScope.launch {
            repository.getStickyList(token, accountId)
                .onSuccess { stickyList ->
                    val ids = stickyList.map { it.chatId }.toSet()
                    _stickyIds.value = ids
                    
                    _uiState.update { state ->
                        if (state is ConversationUiState.Success) {
                            state.copy(stickyConversations = stickyList)
                        } else {
                            state
                        }
                    }
                    UiCache.stickyConversations.value = stickyList
                }
                .onFailure { error ->
                    Log.w("ConversationViewModel", "Failed to fetch sticky list", error)
                }
        }
    }

    fun refresh() {
        loadConversations(refreshPreviews = true)
    }

    fun clearUnread(chatId: String, chatType: Int) {
        val currentState = _uiState.value
        if (currentState is ConversationUiState.Success) {
            loadJob?.cancel()
            loadJob = null
            loadGeneration += 1
            _isRefreshing.value = false
            val conversations = currentState.conversations.map {
                if (it.chatId == chatId && it.chatType == chatType) it.copy(unreadMessage = 0, at = 0) else it
            }
            _uiState.update { currentState.copy(conversations = conversations) }
            syncConversationCache()
            viewModelScope.launch {
                LocalCache.clearUnread(accountId, chatId, chatType)
                repository.dismissNotification(token, chatId)
            }
        }
    }
}