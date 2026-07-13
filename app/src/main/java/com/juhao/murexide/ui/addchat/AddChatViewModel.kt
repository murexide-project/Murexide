package com.juhao.murexide.ui.addchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.ConversationDetail
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.repository.ConversationDetailRepository
import com.juhao.murexide.repository.FriendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 添加会话页面。展示对象（用户/群聊/机器人）信息，并根据是否已添加，
 * 显示「添加」或「进入聊天」。
 */
class AddChatViewModel(
    private val tokenStorage: TokenStorage,
    private val chatId: String,
    private val chatType: Int
) : ViewModel() {

    private val detailRepository = ConversationDetailRepository()
    private val friendRepository = FriendRepository()

    private var token: String? = null

    private val _uiState = MutableStateFlow(AddChatUiState(chatType = chatType))
    val uiState: StateFlow<AddChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val t = tokenStorage.getToken()
            if (t == null) {
                _uiState.update { it.copy(isLoading = false, error = "请先登录") }
                return@launch
            }
            token = t
            loadDetail()
            checkAdded()
        }
    }

    private fun loadDetail() {
        val t = token ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            detailRepository.getDetail(t, chatId, chatType)
                .onSuccess { detail ->
                    _uiState.update { it.copy(detail = detail, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
                }
        }
    }

    private fun checkAdded() {
        val t = token ?: return
        viewModelScope.launch {
            friendRepository.isAdded(t, chatId, chatType)
                .onSuccess { added -> _uiState.update { it.copy(isAdded = added) } }
        }
    }

    fun addChat() {
        val t = token ?: return
        val state = _uiState.value
        if (state.isAdding) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true, error = null) }
            friendRepository.apply(t, chatId, chatType)
                .onSuccess { code ->
                    when (code) {
                        1 -> _uiState.update {
                            it.copy(isAdding = false, isAdded = true, message = "添加成功")
                        }
                        -9 -> _uiState.update {
                            // 已在群聊中，视作已添加
                            it.copy(isAdding = false, isAdded = true, message = "你已在群聊中")
                        }
                        -1 -> _uiState.update {
                            it.copy(isAdding = false, error = "对象不存在")
                        }
                        else -> _uiState.update {
                            it.copy(isAdding = false, message = "已发送申请")
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isAdding = false, error = e.message ?: "添加失败") }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}

data class AddChatUiState(
    val chatType: Int,
    val detail: ConversationDetail? = null,
    val isLoading: Boolean = true,
    val isAdded: Boolean = false,
    val isAdding: Boolean = false,
    val message: String? = null,
    val error: String? = null
)
