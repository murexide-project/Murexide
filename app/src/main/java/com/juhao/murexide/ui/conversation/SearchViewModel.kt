package com.juhao.murexide.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.HomeSearchResult
import com.juhao.murexide.repository.HomeSearchRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class HomeSearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<HomeSearchResult> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false
) {
    fun resultsFor(chatType: Int): List<HomeSearchResult> =
        results.asSequence().filter { it.chatType == chatType }.take(MAX_RESULTS_PER_CATEGORY).toList()
}

private const val MAX_RESULTS_PER_CATEGORY = 5

@OptIn(FlowPreview::class)
class HomeSearchViewModel(
    private val token: String,
    private val repository: HomeSearchRepository = HomeSearchRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeSearchUiState())
    val uiState: StateFlow<HomeSearchUiState> = _uiState.asStateFlow()
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query
                .debounce(400.milliseconds)
                .collectLatest(::search)
        }
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value, error = null) }
        query.value = value
    }

    fun retry() {
        viewModelScope.launch { search(_uiState.value.query) }
    }

    private suspend fun search(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, results = emptyList(), error = null, hasSearched = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null, hasSearched = true) }
        repository.searchHome(token, query)
            .onSuccess { results ->
                _uiState.update { current ->
                    if (current.query.trim() == query) current.copy(isLoading = false, results = results) else current
                }
            }
            .onFailure { error ->
                _uiState.update { current ->
                    if (current.query.trim() == query) current.copy(isLoading = false, results = emptyList(), error = error.message ?: "搜索失败") else current
                }
            }
    }
}
