package com.juhao.murexide.ui.conversationdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.DiskFileItem
import com.juhao.murexide.repository.DiskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiskUiState(
    val isLoading: Boolean = false,
    val files: List<DiskFileItem> = emptyList(),
    val totalSize: Long = 0,
    val currentFolderId: Long = 0,
    val folderStack: List<Pair<Long, String>> = emptyList(),
    val error: String? = null
)

class DiskViewModel(
    private val token: String,
    private val chatId: String,
    private val repository: DiskRepository = DiskRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(DiskUiState())
    val state: StateFlow<DiskUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        loadFiles(_state.value.currentFolderId)
        loadSize()
    }

    fun loadFiles(folderId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.listFiles(token, chatId, folderId)
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            files = list,
                            currentFolderId = folderId
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
    
    fun uploadToDisk(
        fileSizeKb: Long,
        fileName: String,
        fileMd5WithExt: String,
        fileEtag: String,
        qiniuKey: String
    ) {
        viewModelScope.launch {
            repository.uploadFile(
                token = token,
                chatId = chatId,
                fileSizeKb = fileSizeKb,
                fileName = fileName,
                fileMd5WithExt = fileMd5WithExt,
                fileEtag = fileEtag,
                qiniuKey = qiniuKey,
                folderId = _state.value.currentFolderId
            ).onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun loadSize() {
        viewModelScope.launch {
            repository.getTotalSize(token, chatId)
                .onSuccess { size -> _state.update { it.copy(totalSize = size) } }
        }
    }

    fun enterFolder(folder: DiskFileItem) {
        val stack = _state.value.folderStack + (folder.id to folder.name)
        _state.update { it.copy(folderStack = stack) }
        loadFiles(folder.id)
    }

    fun goBack(): Boolean {
        val stack = _state.value.folderStack
        if (stack.isEmpty()) return false
        val newStack = stack.dropLast(1)
        val parentId = newStack.lastOrNull()?.first ?: 0L
        _state.update { it.copy(folderStack = newStack) }
        loadFiles(parentId)
        return true
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(token, chatId, name, _state.value.currentFolderId)
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun rename(item: DiskFileItem, newName: String) {
        viewModelScope.launch {
            repository.rename(token, item.id, item.objectType, newName)
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun remove(item: DiskFileItem) {
        viewModelScope.launch {
            repository.remove(token, item.id, item.objectType)
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
    
    fun jumpTo(folderId: Long, stack: List<Pair<Long, String>>) {
        _state.update { it.copy(folderStack = stack) }
        loadFiles(folderId)
    }

    fun clearError() = _state.update { it.copy(error = null) }
}