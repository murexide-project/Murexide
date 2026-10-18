package com.juhao.murexide.ui.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ContactGroup
import com.juhao.murexide.data.ContactRequestItem
import com.juhao.murexide.data.ContactRequestList
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.FriendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class ContactUiState(
    val contactGroups: List<ContactGroup> = emptyList(),
    val requests: List<ContactRequestItem> = emptyList(),
    val pendingRequestCount: Int = 0,
    val isLoading: Boolean = false,
    val isRequestsLoading: Boolean = false,
    val processingRequestIds: Set<Int> = emptySet(),
    val error: String? = null,
    val requestsError: String? = null,
    val userMessage: String? = null
)

class ContactViewModel(
    private val token: String,
    private val repository: FriendRepository = FriendRepository(),
    private val webSocketManager: WebSocketManager = WebSocketManager.getInstance()
) : ViewModel() {

    private val cacheJson = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadCachedContactsOrEmpty()
            loadCachedRequestsOrEmpty()
            loadContacts()
            loadRequests()
        }
        observeInvitationUpdates()
    }

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.syncCachedAddressBook(token)
                .onSuccess { groups ->
                    _uiState.update { state ->
                        if (groups.isEmpty() && state.contactGroups.isNotEmpty()) {
                            state.copy(isLoading = false, error = null)
                        } else {
                            state.copy(contactGroups = groups, isLoading = false, error = null)
                        }
                    }
                }
                .onFailure { e ->
                    val restored = loadCachedContactsOrEmpty()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = if (restored) null else (e.message ?: "加载失败")
                        )
                    }
                }
        }
    }

    fun loadRequests(showLoading: Boolean = true, forceNetwork: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isRequestsLoading = true, requestsError = null) }
            }

            repository.getRequests(token)
                .onSuccess { requestList ->
                    _uiState.update {
                        it.copy(
                            requests = requestList.requests,
                            pendingRequestCount = requestList.pending,
                            isRequestsLoading = false,
                            requestsError = null
                        )
                    }
                }
                .onFailure { error ->
                    val restored = loadCachedRequestsOrEmpty()
                    _uiState.update {
                        it.copy(
                            isRequestsLoading = false,
                            requestsError = if (restored) null else (error.message ?: "加载新消息失败")
                        )
                    }
                }
        }
    }

    private suspend fun loadCachedContactsOrEmpty(): Boolean {
        if (!LocalCache.isCacheEnabled()) return false
        val accountId = LocalCache.currentAccountId() ?: return false
        val cached = LocalCache.getPayload(accountId, LocalCache.KIND_CONTACTS) ?: return false
        val groups = runCatching {
            cacheJson.decodeFromString(ListSerializer(ContactGroup.serializer()), cached.payload)
        }.getOrNull() ?: return false
        _uiState.update { it.copy(contactGroups = groups, isLoading = false, error = null) }
        return true
    }

    private suspend fun loadCachedRequestsOrEmpty(): Boolean {
        if (!LocalCache.isCacheEnabled()) return false
        val accountId = LocalCache.currentAccountId() ?: return false
        val cached = LocalCache.getPayload(accountId, LocalCache.KIND_REQUESTS) ?: return false
        val requests = runCatching {
            cacheJson.decodeFromString(ContactRequestList.serializer(), cached.payload)
        }.getOrNull() ?: return false
        _uiState.update {
            it.copy(
                requests = requests.requests,
                pendingRequestCount = requests.pending,
                isRequestsLoading = false,
                requestsError = null
            )
        }
        return true
    }

    fun refreshAll() {
        loadContacts()
        loadRequests(forceNetwork = true)
    }

    fun respondToRequest(requestId: Int, accept: Boolean) {
        if (requestId in _uiState.value.processingRequestIds) return
        val request = _uiState.value.requests.firstOrNull { it.requestId == requestId }
            ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(processingRequestIds = it.processingRequestIds + requestId)
            }

            repository.respondToRequest(
                token = token,
                requestId = requestId,
                agree = if (accept) 1 else 2,
                usesGroupAgreeInvite = request.usesGroupAgreeInvite
            ).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        requests = state.requests.map { request ->
                            if (request.requestId == requestId) {
                                request.copy(
                                    result = if (accept) 1 else 2,
                                    processedAt = System.currentTimeMillis()
                                )
                            } else {
                                request
                            }
                        },
                        pendingRequestCount = (state.pendingRequestCount - 1).coerceAtLeast(0),
                        processingRequestIds = state.processingRequestIds - requestId,
                        userMessage = if (accept) "已同意申请" else "已拒绝申请"
                    )
                }
                loadRequests(showLoading = false, forceNetwork = true)
                if (accept) loadContacts()
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        processingRequestIds = state.processingRequestIds - requestId,
                        userMessage = error.message ?: "处理申请失败"
                    )
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun observeInvitationUpdates() {
        viewModelScope.launch {
            webSocketManager.invitationFlow.collect {
                _uiState.update { state ->
                    state.copy(pendingRequestCount = maxOf(1, state.pendingRequestCount))
                }
                loadRequests(showLoading = false, forceNetwork = true)
            }
        }
    }
}