package com.juhao.murexide.ui.sticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.StickerPack
import com.juhao.murexide.repository.ExpressionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StickerPackManagerUiState(
    val isLoading: Boolean = false,
    val packs: List<StickerPack> = emptyList(),
    val error: String? = null,
    val isSubmitting: Boolean = false
)

class StickerPackManagerViewModel(
    val token: String,
    private val repository: ExpressionRepository = ExpressionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(StickerPackManagerUiState())
    val uiState: StateFlow<StickerPackManagerUiState> = _uiState.asStateFlow()

    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getStickerList(token)
                .onSuccess { packs ->
                    _uiState.update { it.copy(isLoading = false, packs = packs) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "加载失败")
                    }
                }
        }
    }

    fun move(from: Int, to: Int) {
        val current = _uiState.value.packs.toMutableList()
        if (from !in current.indices || to !in current.indices || from == to) return
        val item = current.removeAt(from)
        current.add(to, item)
        _uiState.update { it.copy(packs = current) }
    }

    fun persistSort() {
        val packs = _uiState.value.packs
        if (packs.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repository.sortStickerPacks(token, packs)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.emit(e.message ?: "排序保存失败")
                }
        }
    }

    fun createPack(name: String, onSuccess: () -> Unit = {}) {
        if (name.isBlank()) {
            viewModelScope.launch { _toast.emit("名称不能为空") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            repository.createStickerPack(token, name)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.emit("创建成功")
                    onSuccess()
                    load()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.emit(e.message ?: "创建失败")
                }
        }
    }

    fun removePack(packId: Long) {
        viewModelScope.launch {
            repository.removeStickerPack(token, packId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(packs = state.packs.filterNot { it.id == packId })
                    }
                    _toast.emit("已移除")
                }
                .onFailure { _toast.emit(it.message ?: "移除失败") }
        }
    }
}