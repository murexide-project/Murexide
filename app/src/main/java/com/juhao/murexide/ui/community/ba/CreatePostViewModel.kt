package com.juhao.murexide.ui.community.ba

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.repository.CommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreatePostViewModel(
    private val accountStorage: AccountStorage,
    private val baId: Int
) : ViewModel() {

    private var repository: CommunityRepository? = null

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val token = accountStorage.getCurrentToken()
            if (token == null) {
                _uiState.update { it.copy(error = "请先登录") }
            } else {
                repository = CommunityRepository(token)
            }
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun onContentChange(content: String) {
        _uiState.update { it.copy(content = content) }
    }

    /** contentType: 1-文本，2-markdown */
    fun onContentTypeChange(contentType: Int) {
        _uiState.update { it.copy(contentType = contentType) }
    }

    fun onEditModeChange(value: Boolean) {
        _uiState.update { it.copy(isEditMode = value) }
    }

    fun onPostIdChange(postId: Int) {
        _uiState.update { it.copy(postId = postId) }
    }

    fun publish() {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.isPublishing) return
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "标题不能为空") }
            return
        }
        if (state.content.isBlank()) {
            _uiState.update { it.copy(error = "内容不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true, error = null) }
            if (!state.isEditMode) {
                repo.createPost(
                    baId = baId,
                    title = state.title.trim(),
                    content = state.content.trim(),
                    contentType = state.contentType
                ).onSuccess {
                    _uiState.update { it.copy(isPublishing = false, published = true) }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isPublishing = false,
                            error = e.message ?: "发布失败"
                        )
                    }
                }
            } else {
                repo.editPost(
                    title = state.title,
                    content = state.content,
                    contentType = state.contentType,
                    postId = state.postId
                ).onSuccess {
                    _uiState.update { it.copy(isPublishing = false, published = true) }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isPublishing = false,
                            error = e.message ?: "编辑失败"
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class CreatePostUiState(
    val title: String = "",
    val content: String = "",
    val contentType: Int = 1,
    val isPublishing: Boolean = false,
    val isEditMode: Boolean = false,
    val postId: Int = 0,
    val published: Boolean = false,
    val error: String? = null
)
