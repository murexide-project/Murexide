package com.juhao.murexide.ui.conversation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.CreatedChat
import com.juhao.murexide.repository.CreationRepository
import com.juhao.murexide.utils.QiniuUploader
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CreationKind(val chatType: Int, val title: String) {
    GROUP(2, "创建群聊"),
    BOT(3, "创建机器人")
}

data class CreationUiState(
    val name: String = "",
    val introduction: String = "",
    val avatarUrl: String = "",
    val isPrivate: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val uploadProgress: Float = 0f,
    val isCreating: Boolean = false,
    val error: String? = null
)

sealed interface CreationEvent {
    data class Created(val chat: CreatedChat) : CreationEvent
    data class ShowMessage(val message: String) : CreationEvent
}

class CreationViewModel(
    private val token: String,
    private val kind: CreationKind,
    private val repository: CreationRepository = CreationRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreationUiState())
    val uiState: StateFlow<CreationUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<CreationEvent>()
    val events: SharedFlow<CreationEvent> = _events.asSharedFlow()

    fun updateName(value: String) = _uiState.update { it.copy(name = value, error = null) }
    fun updateIntroduction(value: String) = _uiState.update { it.copy(introduction = value, error = null) }
    fun updatePrivate(value: Boolean) = _uiState.update { it.copy(isPrivate = value) }

    fun uploadAvatar(context: Context, uri: Uri) {
        if (_uiState.value.isUploadingAvatar || _uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, uploadProgress = 0f, error = null) }
            QiniuUploader(context, token).uploadFromUri(context, uri) { progress ->
                _uiState.update { it.copy(uploadProgress = progress) }
            }.onSuccess { response ->
                _uiState.update { it.copy(isUploadingAvatar = false, avatarUrl = "https://chat-img.jwznb.com/${response.key}") }
            }.onFailure { error ->
                _uiState.update { it.copy(isUploadingAvatar = false, error = "头像上传失败: ${error.message}") }
            }
        }
    }

    fun create() {
        val state = _uiState.value
        if (state.isCreating || state.isUploadingAvatar) return
        if (state.name.trim().isEmpty()) {
            _uiState.update { it.copy(error = "请输入名称") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            val result = when (kind) {
                CreationKind.GROUP -> repository.createGroup(token, state.name.trim(), state.introduction.trim(), state.avatarUrl)
                CreationKind.BOT -> repository.createBot(token, state.name.trim(), state.introduction.trim(), state.avatarUrl, state.isPrivate)
            }
            result.onSuccess { chat ->
                _uiState.update { it.copy(isCreating = false) }
                _events.emit(CreationEvent.Created(chat))
            }.onFailure { error ->
                _uiState.update { it.copy(isCreating = false, error = error.message ?: "创建失败") }
            }
        }
    }
}
