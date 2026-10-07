package com.juhao.murexide.ui.chatsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.repository.MessageRepository
import com.juhao.murexide.repository.toMessageItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatSearchUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val keyword: String = "",
    val results: List<MessageItem> = emptyList(),
    val error: String? = null,
    val hasMore: Boolean = true,
    val timeCursor: Long = Long.MAX_VALUE
)

class ChatSearchViewModel(
    private val token: String,
    private val chatId: String,
    private val chatType: Int,
    private val repository: MessageRepository = MessageRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatSearchUiState())
    val uiState: StateFlow<ChatSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onKeywordChange(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
        searchJob?.cancel()
        if (keyword.isBlank()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = true,
                    timeCursor = Long.MAX_VALUE,
                    error = null
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            performSearch(keyword)
        }
    }

    private suspend fun performSearch(keyword: String) {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                results = emptyList(),
                hasMore = true,
                timeCursor = Long.MAX_VALUE
            )
        }
        repository.searchMessages(
            token = token,
            chatId = chatId,
            chatType = chatType,
            keyword = keyword,
            timeCursor = Long.MAX_VALUE
        ).fold(
            onSuccess = { list ->
                val messages = list.map { it.toMessageItem(chatId, chatType) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = messages,
                        hasMore = list.size >= PAGE_SIZE,
                        timeCursor = messages.minOfOrNull { m -> m.timestamp }
                            ?: Long.MAX_VALUE
                    )
                }
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "搜索失败")
                }
            }
        )
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMore || state.keyword.isBlank()) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            repository.searchMessages(
                token = token,
                chatId = chatId,
                chatType = chatType,
                keyword = state.keyword,
                timeCursor = state.timeCursor
            ).fold(
                onSuccess = { list ->
                    val newMessages = list
                        .map { it.toMessageItem(chatId, chatType) }
                        .filter { new -> state.results.none { it.msgId == new.msgId } }
                    val merged = state.results + newMessages
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            results = merged,
                            hasMore = list.size >= PAGE_SIZE,
                            timeCursor = merged.minOfOrNull { m -> m.timestamp }
                                ?: state.timeCursor
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoadingMore = false, error = e.message ?: "加载更多失败")
                    }
                }
            )
        }
    }

    fun retry() {
        val keyword = _uiState.value.keyword
        if (keyword.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(keyword) }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}