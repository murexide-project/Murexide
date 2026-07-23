package com.juhao.murexide.ui.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ContactGroup
import com.juhao.murexide.data.ContactRequestItem
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.FriendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState: StateFlow<ContactUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
        loadRequests()
        observeInvitationUpdates()
    }

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getAddressBook(token)
                .onSuccess { groups ->
                    _uiState.update { it.copy(contactGroups = groups, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun loadRequests(showLoading: Boolean = true) {
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
                    _uiState.update {
                        it.copy(
                            isRequestsLoading = false,
                            requestsError = error.message ?: "加载新消息失败"
                        )
                    }
                }
        }
    }

    fun refreshAll() {
        loadContacts()
        loadRequests()
    }

    fun respondToRequest(requestId: Int, accept: Boolean) {
        if (requestId in _uiState.value.processingRequestIds) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(processingRequestIds = it.processingRequestIds + requestId)
            }

            repository.respondToRequest(
                token = token,
                requestId = requestId,
                agree = if (accept) 1 else 2
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
                loadRequests(showLoading = false)
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
                loadRequests(showLoading = false)
            }
        }
    }
}
