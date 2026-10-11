package com.juhao.murexide.ui.sticker

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.*
import com.juhao.murexide.repository.ExpressionRepository
import com.juhao.murexide.utils.QiniuUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StickerDetailUiState(
    val isLoading: Boolean = false,
    val pack: StickerPack? = null,
    val creator: StickerCreator? = null,
    val isCollected: Boolean = false,
    val isOwner: Boolean = false,
    val error: String? = null,
    val isSubmitting: Boolean = false
)

data class PendingStickerUpload(
    val url: String,
    val suggestedName: String
)

class StickerDetailViewModel(
    private val token: String,
    private val packId: Long,
    private val currentUserId: String,
    private val repository: ExpressionRepository = ExpressionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(StickerDetailUiState())
    val uiState: StateFlow<StickerDetailUiState> = _uiState.asStateFlow()

    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val _pendingUpload = MutableStateFlow<PendingStickerUpload?>(null)
    val pendingUpload: StateFlow<PendingStickerUpload?> = _pendingUpload.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private var uploadJob: Job? = null

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getStickerDetail(token, packId)
                .onSuccess { data ->
                    val pack = data.stickerPack
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pack = pack,
                            creator = data.user,
                            isOwner = pack?.createBy == currentUserId,
                            isCollected = isPackInMyList(pack)
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "加载失败")
                    }
                }
        }
    }

    private suspend fun isPackInMyList(pack: StickerPack?): Boolean {
        if (pack == null) return false
        val result = repository.getStickerList(token)
        return result.getOrNull()?.any { it.id == pack.id } == true
    }

    fun toggleCollect() {
        val state = _uiState.value
        val pack = state.pack ?: return
        if (state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = if (state.isCollected) {
                repository.removeStickerPack(token, pack.id)
            } else {
                repository.addStickerPack(token, pack.id)
            }
            result.onSuccess {
                _uiState.update { it.copy(isSubmitting = false, isCollected = !state.isCollected) }
                _toast.emit(if (state.isCollected) "已移除" else "收藏成功")
            }.onFailure { e ->
                _uiState.update { it.copy(isSubmitting = false) }
                _toast.emit(e.message ?: "操作失败")
            }
        }
    }

    fun renamePack(newName: String) {
        val pack = _uiState.value.pack ?: return
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameStickerPack(token, pack.id, newName)
                .onSuccess {
                    _uiState.update { it.copy(pack = it.pack?.copy(name = newName)) }
                    _toast.emit("重命名成功")
                }
                .onFailure { _toast.emit(it.message ?: "重命名失败") }
        }
    }

    fun deletePack(onSuccess: () -> Unit) {
        val pack = _uiState.value.pack ?: return
        viewModelScope.launch {
            repository.deleteStickerPack(token, pack.id)
                .onSuccess {
                    _toast.emit("删除成功")
                    onSuccess()
                }
                .onFailure { _toast.emit(it.message ?: "删除失败") }
        }
    }

    fun uploadSticker(uri: Uri, context: Context) {
        val pack = _uiState.value.pack ?: return
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            _uploadProgress.value = 0f
            try {
                val uploader = QiniuUploader(
                    context = context,
                    userToken = token,
                    uploadType = 1,
                    enableWebp = false
                )
                val result = uploader.uploadFromUri(context, uri) { progress ->
                    _uploadProgress.value = progress
                }
                result.onSuccess { response ->
                    _uploadProgress.value = null
                    val url = response.key
                    val suggested = url.substringAfterLast('/').substringBeforeLast('.')
                    _uiState.update { it.copy(isSubmitting = false) }
                    _pendingUpload.value = PendingStickerUpload(
                        url = url,
                        suggestedName = suggested
                    )
                }.onFailure { e ->
                    _uploadProgress.value = null
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.emit("上传失败: ${e.message}")
                }
            } catch (_: CancellationException) {
                _uploadProgress.value = null
                _uiState.update { it.copy(isSubmitting = false) }
            } catch (e: Exception) {
                _uploadProgress.value = null
                _uiState.update { it.copy(isSubmitting = false) }
                _toast.emit("上传失败: ${e.message}")
            }
        }
    }

    fun confirmAddSticker(name: String) {
        val pending = _pendingUpload.value ?: return
        val pack = _uiState.value.pack ?: return
        if (name.isBlank()) {
            viewModelScope.launch { _toast.emit("名称不能为空") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repository.addStickerToPack(token, name, pending.url, pack.id)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _pendingUpload.value = null
                    _toast.emit("添加成功")
                    load()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.emit(e.message ?: "添加失败")
                }
        }
    }

    fun cancelPendingUpload() {
        _pendingUpload.value = null
    }

    fun removeSticker(stickerId: Long) {
        viewModelScope.launch {
            repository.removeSticker(token, stickerId)
                .onSuccess {
                    _toast.emit("已删除")
                    load()
                }
                .onFailure { _toast.emit(it.message ?: "删除失败") }
        }
    }

    fun renameSticker(stickerId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameSticker(token, stickerId, newName)
                .onSuccess {
                    _toast.emit("重命名成功")
                    load()
                }
                .onFailure { _toast.emit(it.message ?: "重命名失败") }
        }
    }

    fun importPack(fileKey: String, name: String) {
        viewModelScope.launch {
            repository.importPack(token, name, fileKey)
                .onSuccess {
                    _toast.emit("导入成功")
                    load()
                }
                .onFailure { _toast.emit(it.message ?: "导入失败") }
        }
    }
    
    fun addStickerToFavorites(stickerId: Long) {
        viewModelScope.launch {
            repository.addExpressionToFavorites(token, stickerId.toInt())
                .onSuccess {
                    _toast.emit("已收藏到我的表情")
                }
                .onFailure { e ->
                    _toast.emit(e.message ?: "收藏失败")
                }
        }
    }
}