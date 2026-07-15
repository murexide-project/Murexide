package com.juhao.murexide.ui.conversationdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationDetailUiState
import com.juhao.murexide.repository.ConversationDetailRepository
import com.juhao.murexide.repository.FriendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationDetailViewModel(
    private val token: String,
    private val chatId: String,
    private val chatType: Int,
    fallbackName: String = "",
    fallbackAvatar: String = "",
    private val repository: ConversationDetailRepository = ConversationDetailRepository(),
    private val friendRepository: FriendRepository = FriendRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConversationDetailUiState(
            isLoading = true,
            detail = com.juhao.murexide.data.ConversationDetail(
                chatId = chatId,
                chatType = chatType,
                name = fallbackName,
                avatarUrl = fallbackAvatar
            )
        )
    )
    val uiState: StateFlow<ConversationDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
        checkAdded()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getDetail(token, chatId, chatType)
                .onSuccess { detail ->
                    _uiState.update { it.copy(isLoading = false, detail = detail, error = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "加载失败") }
                }
        }
    }

    private fun checkAdded() {
        viewModelScope.launch {
            friendRepository.isAdded(token, chatId, chatType)
                .onSuccess { added -> _uiState.update { it.copy(isAdded = added) } }
        }
    }

    fun addChat() {
        val state = _uiState.value
        if (state.isAdding) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true) }
            friendRepository.apply(token, chatId, chatType)
                .onSuccess { rep ->
                    when (rep.code) {
                        1 -> _uiState.update {
                            state.detail?.let { detail ->
                                if (detail.chatType == 1) {
                                    it.copy(isAdding = false, message = "已发送申请")
                                } else if (detail.chatType == 2) {
                                    if (detail.directJoin) {
                                        it.copy(isAdding = false, isAdded = true, message = "已加入群聊")
                                    } else {
                                        it.copy(isAdding = false, message = "已发送申请")
                                    }
                                } else {
                                    it.copy(isAdding = false, isAdded = true, message = "已添加")
                                }
                            }
                        }
                        -9 -> _uiState.update {
                            // 已在群聊中，视作已添加
                            it.copy(isAdding = false, isAdded = true, message = "你已在群聊中")
                        }
                        else -> _uiState.update {
                            it.copy(isAdding = false, message = rep.msg)
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isAdding = false, message = e.message ?: "添加失败") }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
