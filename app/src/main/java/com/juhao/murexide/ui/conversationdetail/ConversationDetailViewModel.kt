package com.juhao.murexide.ui.conversationdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationDetailUiState
import com.juhao.murexide.repository.ConversationDetailRepository
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
    private val repository: ConversationDetailRepository = ConversationDetailRepository()
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
}
