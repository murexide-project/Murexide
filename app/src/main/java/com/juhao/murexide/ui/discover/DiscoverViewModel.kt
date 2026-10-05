package com.juhao.murexide.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.BotBanner
import com.juhao.murexide.data.BotStoreItem
import com.juhao.murexide.data.GroupCategoryItem
import com.juhao.murexide.data.GroupRecommendItem
import com.juhao.murexide.repository.DiscoverRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiscoverTab { BOTS, GROUPS }

data class DiscoverUiState(
    val currentTab: DiscoverTab = DiscoverTab.BOTS,
    val banners: List<BotBanner> = emptyList(),
    val bots: List<BotStoreItem> = emptyList(),
    val isLoadingBots: Boolean = false,
    val isRefreshingBots: Boolean = false,
    val categories: List<GroupCategoryItem> = emptyList(),
    val selectedParent: GroupCategoryItem? = null,
    val selectedCategoryId: Int = 0,
    val keyword: String = "",
    val groups: List<GroupRecommendItem> = emptyList(),
    val isLoadingGroups: Boolean = false,
    val isRefreshingGroups: Boolean = false,
    val error: String? = null
)

class DiscoverViewModel(
    private val token: String
) : ViewModel() {

    private val repository = DiscoverRepository(token)

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadBanners()
        loadBots()
        loadCategories()
        loadGroups()
    }

    fun selectTab(tab: DiscoverTab) {
        if (_uiState.value.currentTab == tab) return
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun loadBanners() {
        viewModelScope.launch {
            repository.getBotBanners().onSuccess { list ->
                _uiState.update { it.copy(banners = list) }
            }
        }
    }

    fun loadBots(refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingBots = !refresh && it.bots.isEmpty(),
                    isRefreshingBots = refresh && it.bots.isNotEmpty()
                )
            }
            repository.getBotStore()
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            bots = list,
                            isLoadingBots = false,
                            isRefreshingBots = false,
                            error = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingBots = false,
                            isRefreshingBots = false,
                            error = e.message
                        )
                    }
                }
        }
    }

    fun refreshBots() {
        loadBanners()
        loadBots(refresh = true)
    }

    fun loadCategories() {
        viewModelScope.launch {
            repository.getGroupCategories().onSuccess { list ->
                _uiState.update { it.copy(categories = list) }
            }
        }
    }

    fun selectParent(category: GroupCategoryItem?) {
        _uiState.update {
            it.copy(
                selectedParent = category,
                selectedCategoryId = category?.id ?: 0
            )
        }
        loadGroups()
    }

    fun selectCategory(categoryId: Int) {
        if (_uiState.value.selectedCategoryId == categoryId) return
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        loadGroups()
    }

    fun onKeywordChange(value: String) {
        _uiState.update { it.copy(keyword = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadGroups()
        }
    }

    fun clearKeyword() {
        searchJob?.cancel()
        _uiState.update { it.copy(keyword = "") }
        loadGroups()
    }

    fun loadGroups(refresh: Boolean = false) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingGroups = !refresh && it.groups.isEmpty(),
                    isRefreshingGroups = refresh && it.groups.isNotEmpty()
                )
            }
            repository.getRecommendedGroups(state.selectedCategoryId, state.keyword)
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            groups = list,
                            isLoadingGroups = false,
                            isRefreshingGroups = false,
                            error = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingGroups = false,
                            isRefreshingGroups = false,
                            error = e.message
                        )
                    }
                }
        }
    }

    fun refreshGroups() = loadGroups(refresh = true)
}
