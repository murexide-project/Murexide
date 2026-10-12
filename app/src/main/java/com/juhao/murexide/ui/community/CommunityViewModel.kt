package com.juhao.murexide.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.BaItem
import com.juhao.murexide.data.PostItem
import com.juhao.murexide.repository.AuthRepository
import com.juhao.murexide.repository.CommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 社区顶部三个 tab */
enum class CommunityTab { RECOMMEND, ALL_BA, SUBSCRIBE, MANAGE }

/** 「全部分区」左侧侧边分类 */
enum class BaSide { OFFICIAL, USER }

class CommunityViewModel(
    private val token: String
) : ViewModel() {

    private val repository = CommunityRepository(token)
    private val authRepository = AuthRepository()

    private companion object {
        const val PAGE_SIZE = 20
    }

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

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
        _uiState.value = _uiState.value.copy(currentTab = tab)
        when (tab) {
            CommunityTab.RECOMMEND -> if (_uiState.value.posts.isEmpty()) loadRecommend()
            CommunityTab.ALL_BA -> if (_uiState.value.allBaList.isEmpty()) loadAllBa()
            CommunityTab.SUBSCRIBE -> if (_uiState.value.subscribeBaList.isEmpty()) loadSubscribeBa()
            CommunityTab.MANAGE -> if (_uiState.value.manageBaList.isEmpty()) loadManageBa()
        }
    }

    // ==================== 推荐 ====================

    fun loadRecommend() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingPosts = _uiState.value.posts.isEmpty(),
                isRefreshingPosts = _uiState.value.posts.isNotEmpty(),
                currentPage = 1
            )
            repository.getRecommendPosts(size = PAGE_SIZE, page = 1)
                .onSuccess { data ->
                    _uiState.value = _uiState.value.copy(
                        posts = data.posts,
                        total = data.total,
                        hasMore = data.posts.isNotEmpty() && data.posts.size < data.total,
                        isLoadingPosts = false,
                        isRefreshingPosts = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingPosts = false,
                        isRefreshingPosts = false,
                        hasMore = false,
                        error = e.message
                    )
                }
        }
    }

    fun loadMorePosts() {
        val state = _uiState.value
        if (state.isLoadingPosts || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val nextPage = state.currentPage + 1
            repository.getRecommendPosts(size = PAGE_SIZE, page = nextPage)
                .onSuccess { data ->
                    val merged = _uiState.value.posts + data.posts
                    _uiState.value = _uiState.value.copy(
                        posts = merged,
                        total = data.total,
                        currentPage = nextPage,
                        hasMore = data.posts.isNotEmpty() && merged.size < data.total,
                        isLoadingMore = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoadingMore = false, error = e.message)
                }
        }
    }

    fun selectBaSide(side: BaSide) {
        if (_uiState.value.currentBaSide == side) return
        _uiState.value = _uiState.value.copy(currentBaSide = side)
        loadAllBa()
    }

    fun loadAllBa() {
        val side = _uiState.value.currentBaSide
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAllBa = true)
            val typ = when (side) {
                BaSide.OFFICIAL -> 2 // 热门（官方）
                BaSide.USER -> 4     // 全部（含用户自建）
            }
            repository.getBaList(typ = typ, size = 50, page = 1)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(allBaList = list, isLoadingAllBa = false)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoadingAllBa = false, error = e.message)
                }
        }
    }

    fun loadSubscribeBa() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSubscribeBa = true)
            repository.getBaList(typ = 1, size = 50, page = 1)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(subscribeBaList = list, isLoadingSubscribeBa = false)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoadingSubscribeBa = false, error = e.message)
                }
        }
    }

    fun loadManageBa() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingManageBa = true)
            repository.getBaListByCreate(userId)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(manageBaList = list, isLoadingManageBa = false)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoadingManageBa = false, error = e.message)
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
        val updated = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                val liked = post.isLiked == "0"
                post.copy(
                    isLiked = if (liked) "1" else "0",
                    likeNum = if (liked) post.likeNum + 1 else (post.likeNum - 1).coerceAtLeast(0)
                )
            } else post
        }
        _uiState.value = _uiState.value.copy(posts = updated)
    }

    private fun updatePostCollect(postId: Int) {
        val updated = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                val collected = post.isCollected == 0
                post.copy(
                    isCollected = if (collected) 1 else 0,
                    collectNum = if (collected) post.collectNum + 1 else (post.collectNum - 1).coerceAtLeast(0)
                )
            } else post
        }
        _uiState.value = _uiState.value.copy(posts = updated)
    }

    fun refresh() {
        when (_uiState.value.currentTab) {
            CommunityTab.RECOMMEND -> loadRecommend()
            CommunityTab.ALL_BA -> loadAllBa()
            CommunityTab.SUBSCRIBE -> loadSubscribeBa()
            CommunityTab.MANAGE -> loadManageBa()
        }
    }
}

data class CommunityUiState(
    val currentTab: CommunityTab = CommunityTab.RECOMMEND,
    // 推荐
    val posts: List<PostItem> = emptyList(),
    val isLoadingPosts: Boolean = false,
    val isRefreshingPosts: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
    // 全部分区
    val currentBaSide: BaSide = BaSide.OFFICIAL,
    val allBaList: List<BaItem> = emptyList(),
    val isLoadingAllBa: Boolean = false,
    // 关注
    val subscribeBaList: List<BaItem> = emptyList(),
    val isLoadingSubscribeBa: Boolean = false,
    // 管理
    val manageBaList: List<BaItem> = emptyList(),
    val isLoadingManageBa: Boolean = false,
    val error: String? = null
)
