package com.juhao.murexide.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.BaItem
import com.juhao.murexide.data.PostItem
import com.juhao.murexide.repository.AuthRepository
import com.juhao.murexide.repository.CommunityRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommunityTab { RECOMMEND, ALL_BA, MANAGE }
enum class BaSide { OFFICIAL, USER, SUBSCRIBE }
enum class CommunitySort { RECOMMEND, LATEST }

data class SearchUiState(
    val keyword: String = "",
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val results: List<PostItem> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
)

class CommunityViewModel(
    private val token: String
) : ViewModel() {

    private val repository = CommunityRepository(token)
    private val authRepository = AuthRepository()

    private companion object {
        const val PAGE_SIZE = 20
        const val SEARCH_PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 350L
    }

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private var currentUserId: String? = null
    private var searchJob: Job? = null

    init {
        loadRecommend()
        loadAllBa()
        loadCurrentUserId()
    }

    private fun loadCurrentUserId() {
        viewModelScope.launch {
            authRepository.getUserInfo(token).onSuccess { info ->
                currentUserId = info.id
                loadManageBa()
            }
        }
    }

    fun selectTab(tab: CommunityTab) {
        if (_uiState.value.currentTab == tab) return
        _uiState.update { it.copy(currentTab = tab) }
        when (tab) {
            CommunityTab.RECOMMEND -> {
                if (_uiState.value.sort == CommunitySort.RECOMMEND && _uiState.value.posts.isEmpty()) loadRecommend()
                if (_uiState.value.sort == CommunitySort.LATEST && _uiState.value.latestPosts.isEmpty()) loadLatest()
            }
            CommunityTab.ALL_BA -> if (_uiState.value.allBaList.isEmpty()) loadAllBa()
            CommunityTab.MANAGE -> if (_uiState.value.manageBaList.isEmpty()) loadManageBa()
        }
    }

    fun toggleSort() {
        val newSort = if (_uiState.value.sort == CommunitySort.RECOMMEND) CommunitySort.LATEST else CommunitySort.RECOMMEND
        _uiState.update { it.copy(sort = newSort) }
        if (newSort == CommunitySort.LATEST && _uiState.value.latestPosts.isEmpty()) {
            loadLatest()
        }
    }

    fun loadRecommend() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingPosts = it.posts.isEmpty(),
                    isRefreshingPosts = it.posts.isNotEmpty(),
                    currentPage = 1
                )
            }
            repository.getRecommendPosts(size = PAGE_SIZE, page = 1)
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            posts = data.posts,
                            total = data.total,
                            hasMore = data.posts.isNotEmpty() && data.posts.size < data.total,
                            isLoadingPosts = false,
                            isRefreshingPosts = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingPosts = false,
                            isRefreshingPosts = false,
                            hasMore = false,
                            error = e.message
                        )
                    }
                }
        }
    }

    fun loadMorePosts() {
        val state = _uiState.value
        if (state.isLoadingPosts || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = state.currentPage + 1
            repository.getRecommendPosts(size = PAGE_SIZE, page = nextPage)
                .onSuccess { data ->
                    _uiState.update {
                        val merged = it.posts + data.posts
                        it.copy(
                            posts = merged,
                            total = data.total,
                            currentPage = nextPage,
                            hasMore = data.posts.isNotEmpty() && merged.size < data.total,
                            isLoadingMore = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
                }
        }
    }

    fun loadLatest() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLatestLoading = it.latestPosts.isEmpty(),
                    isLatestRefreshing = it.latestPosts.isNotEmpty(),
                    latestPage = 1
                )
            }
            repository.getLatestPosts(size = PAGE_SIZE, page = 1)
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            latestPosts = data.posts,
                            latestPage = 1,
                            latestHasMore = data.posts.size >= PAGE_SIZE,
                            isLatestLoading = false,
                            isLatestRefreshing = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLatestLoading = false,
                            isLatestRefreshing = false,
                            latestError = e.message
                        )
                    }
                }
        }
    }

    fun loadMoreLatest() {
        val state = _uiState.value
        if (state.isLatestLoading || state.isLatestLoadingMore || !state.latestHasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLatestLoadingMore = true) }
            val nextPage = state.latestPage + 1
            repository.getLatestPosts(size = PAGE_SIZE, page = nextPage)
                .onSuccess { data ->
                    _uiState.update {
                        val merged = it.latestPosts + data.posts
                        it.copy(
                            latestPosts = merged,
                            latestPage = nextPage,
                            latestHasMore = data.posts.size >= PAGE_SIZE,
                            isLatestLoadingMore = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLatestLoadingMore = false, latestError = e.message)
                    }
                }
        }
    }

    fun selectBaSide(side: BaSide) {
        if (_uiState.value.currentBaSide == side) return
        _uiState.update { it.copy(currentBaSide = side) }
        loadAllBa()
    }

    fun loadAllBa() {
        val side = _uiState.value.currentBaSide
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAllBa = true) }
            val typ = when (side) {
                BaSide.OFFICIAL -> 2
                BaSide.USER -> 4
                BaSide.SUBSCRIBE -> 1
            }
            repository.getBaList(typ = typ, size = 50, page = 1)
                .onSuccess { list ->
                    _uiState.update { it.copy(allBaList = list, isLoadingAllBa = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingAllBa = false, error = e.message) }
                }
        }
    }

    fun loadManageBa() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingManageBa = true) }
            repository.getBaListByCreate(userId)
                .onSuccess { list ->
                    _uiState.update { it.copy(manageBaList = list, isLoadingManageBa = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingManageBa = false, error = e.message) }
                }
        }
    }

    fun toggleLike(postId: Int) {
        viewModelScope.launch {
            repository.toggleLike(postId).onSuccess { updatePostLike(postId) }
        }
    }

    fun toggleCollect(postId: Int) {
        viewModelScope.launch {
            repository.toggleCollect(postId).onSuccess { updatePostCollect(postId) }
        }
    }

    private fun updatePostLike(postId: Int) {
        _uiState.update { state ->
            val posts = state.posts.map { post ->
                if (post.id == postId) {
                    val liked = post.isLiked == "0"
                    post.copy(
                        isLiked = if (liked) "1" else "0",
                        likeNum = if (liked) post.likeNum + 1 else (post.likeNum - 1).coerceAtLeast(0)
                    )
                } else post
            }
            val latestPosts = state.latestPosts.map { post ->
                if (post.id == postId) {
                    val liked = post.isLiked == "0"
                    post.copy(
                        isLiked = if (liked) "1" else "0",
                        likeNum = if (liked) post.likeNum + 1 else (post.likeNum - 1).coerceAtLeast(0)
                    )
                } else post
            }
            state.copy(posts = posts, latestPosts = latestPosts)
        }
        val searchUpdated = _searchState.value.results.map { post ->
            if (post.id == postId) {
                val liked = post.isLiked == "0"
                post.copy(
                    isLiked = if (liked) "1" else "0",
                    likeNum = if (liked) post.likeNum + 1 else (post.likeNum - 1).coerceAtLeast(0)
                )
            } else post
        }
        _searchState.update { it.copy(results = searchUpdated) }
    }

    private fun updatePostCollect(postId: Int) {
        _uiState.update { state ->
            val posts = state.posts.map { post ->
                if (post.id == postId) {
                    val collected = post.isCollected == 0
                    post.copy(
                        isCollected = if (collected) 1 else 0,
                        collectNum = if (collected) post.collectNum + 1 else (post.collectNum - 1).coerceAtLeast(0)
                    )
                } else post
            }
            val latestPosts = state.latestPosts.map { post ->
                if (post.id == postId) {
                    val collected = post.isCollected == 0
                    post.copy(
                        isCollected = if (collected) 1 else 0,
                        collectNum = if (collected) post.collectNum + 1 else (post.collectNum - 1).coerceAtLeast(0)
                    )
                } else post
            }
            state.copy(posts = posts, latestPosts = latestPosts)
        }
        val searchUpdated = _searchState.value.results.map { post ->
            if (post.id == postId) {
                val collected = post.isCollected == 0
                post.copy(
                    isCollected = if (collected) 1 else 0,
                    collectNum = if (collected) post.collectNum + 1 else (post.collectNum - 1).coerceAtLeast(0)
                )
            } else post
        }
        _searchState.update { it.copy(results = searchUpdated) }
    }

    fun searchPosts(keyword: String) {
        _searchState.update { it.copy(keyword = keyword) }
        searchJob?.cancel()
        if (keyword.isBlank()) {
            _searchState.update {
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    results = emptyList(),
                    page = 1,
                    hasMore = false,
                    error = null,
                    hasSearched = false
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _searchState.update { it.copy(isSearching = true, error = null) }
            repository.searchPosts(keyword = keyword, size = SEARCH_PAGE_SIZE, page = 1)
                .onSuccess { list ->
                    _searchState.update {
                        it.copy(
                            isSearching = false,
                            results = list,
                            page = 1,
                            hasMore = list.size >= SEARCH_PAGE_SIZE,
                            hasSearched = true
                        )
                    }
                }
                .onFailure { e ->
                    _searchState.update {
                        it.copy(
                            isSearching = false,
                            error = e.message,
                            hasMore = false,
                            hasSearched = true
                        )
                    }
                }
        }
    }

    fun loadMoreSearch() {
        val state = _searchState.value
        if (state.isSearching || state.isLoadingMore || !state.hasMore) return
        val keyword = state.keyword
        if (keyword.isBlank()) return
        searchJob = viewModelScope.launch {
            _searchState.update { it.copy(isLoadingMore = true) }
            val nextPage = state.page + 1
            repository.searchPosts(keyword = keyword, size = SEARCH_PAGE_SIZE, page = nextPage)
                .onSuccess { list ->
                    _searchState.update {
                        val merged = (it.results + list).distinctBy { p -> p.id }
                        it.copy(
                            isLoadingMore = false,
                            results = merged,
                            page = nextPage,
                            hasMore = list.size >= SEARCH_PAGE_SIZE
                        )
                    }
                }
                .onFailure { e ->
                    _searchState.update {
                        it.copy(isLoadingMore = false, error = e.message, hasMore = false)
                    }
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchState.value = SearchUiState()
    }

    fun refresh() {
        when (_uiState.value.currentTab) {
            CommunityTab.RECOMMEND -> {
                if (_uiState.value.sort == CommunitySort.RECOMMEND) loadRecommend()
                else loadLatest()
            }
            CommunityTab.ALL_BA -> loadAllBa()
            CommunityTab.MANAGE -> loadManageBa()
        }
    }
}

data class CommunityUiState(
    val currentTab: CommunityTab = CommunityTab.RECOMMEND,
    val sort: CommunitySort = CommunitySort.RECOMMEND,
    val posts: List<PostItem> = emptyList(),
    val isLoadingPosts: Boolean = false,
    val isRefreshingPosts: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val latestPosts: List<PostItem> = emptyList(),
    val isLatestLoading: Boolean = false,
    val isLatestRefreshing: Boolean = false,
    val isLatestLoadingMore: Boolean = false,
    val latestPage: Int = 1,
    val latestHasMore: Boolean = false,
    val latestError: String? = null,
    val currentBaSide: BaSide = BaSide.OFFICIAL,
    val allBaList: List<BaItem> = emptyList(),
    val isLoadingAllBa: Boolean = false,
    val manageBaList: List<BaItem> = emptyList(),
    val isLoadingManageBa: Boolean = false,
    val error: String? = null
)