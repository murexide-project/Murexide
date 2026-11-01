package com.juhao.murexide.ui.settings.chatbackground

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ChatBackgroundItem
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.repository.ChatBackgroundRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatBackgroundUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val list: List<ChatBackgroundItem> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val token: String = "",
    val accountId: String = ""
)

class ChatBackgroundViewModel(
    application: Application,
    private val repository: ChatBackgroundRepository = ChatBackgroundRepository()
) : AndroidViewModel(application) {

    private val accountStorage = AccountStorage.getInstance(application)

    private val _state = MutableStateFlow(ChatBackgroundUiState())
    val state: StateFlow<ChatBackgroundUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val account = accountStorage.getCurrentAccount()
            val token = account?.token.orEmpty()
            _state.update {
                it.copy(
                    accountId = account?.id.orEmpty(),
                    token = token
                )
            }
            if (token.isNotEmpty()) load()
        }
    }

    fun load() {
        val token = _state.value.token
        if (token.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getBackgroundList(token)
                .onSuccess { list ->
                    _state.update { it.copy(isLoading = false, list = list) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun setBackground(chatId: String, fileName: String) {
        val token = _state.value.token
        val accountId = _state.value.accountId
        if (token.isEmpty() || accountId.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            repository.editBackground(token, accountId, chatId, fileName)
                .onSuccess {
                    _state.update { it.copy(isSubmitting = false, message = "背景已更新") }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSubmitting = false, message = e.message) }
                }
        }
    }
    
    fun deleteBackground(chatId: String) {
        val token = _state.value.token
        val accountId = _state.value.accountId
        if (token.isEmpty() || accountId.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            repository.editBackground(token, accountId, chatId, "")
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            message = "背景已删除",
                            list = it.list.filterNot { item -> item.chatId == chatId }
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSubmitting = false, message = e.message) }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}