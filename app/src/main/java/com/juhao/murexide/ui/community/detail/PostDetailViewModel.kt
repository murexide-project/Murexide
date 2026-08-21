package com.juhao.murexide.ui.community.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.CommentItem
import com.juhao.murexide.data.PostDetail
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.repository.CommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PostDetailViewModel(
    private val accountStorage: AccountStorage,
    private val postId: Int
) : ViewModel() {

    private var repository: CommunityRepository? = null
    private var isDetailLoading = false
    private val commentPageSize = 10

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val token = accountStorage.getCurrentToken()
            if (token == null) {
                _uiState.update {
                    it.copy(
                        error = "请先登录",
                        isLoadingDetail = false
                    )
                }
                return@launch
            }

            repository = CommunityRepository(token)

            loadDetail()
            loadComments(reset = true)
        }
    }

    fun loadDetail() {
        if (isDetailLoading) return
        if (_uiState.value.post != null) return

        val repo = repository ?: return

        isDetailLoading = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true, error = null) }

            repo.getPostDetail(postId)
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            post = data.post,
                            isLoadingDetail = false,
                            isManageEnabled = data.post.senderId == accountStorage.getCurrentUserId()
                        )
                    }
                    isDetailLoading = false
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            error = "加载详情失败: ${e.message}"
                        )
                    }
                    isDetailLoading = false
                }
        }
    }

    fun loadComments(reset: Boolean) {
        val repo = repository ?: return

        val state = _uiState.value
        if (state.isLoadingComments) return
        if (!reset && !state.hasMoreComments) return

        val nextPage = if (reset) 1 else state.commentPage + 1

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingComments = true) }

            repo.getCommentList(postId, size = commentPageSize, page = nextPage)
                .onSuccess { data ->
                    val currentComments = if (reset) emptyList() else state.comments
                    val merged = currentComments + data.comments

                    _uiState.update {
                        it.copy(
                            comments = merged,
                            commentTotal = data.total,
                            commentPage = nextPage,
                            hasMoreComments = data.comments.size >= commentPageSize &&
                                    merged.size < data.total,
                            isLoadingComments = false,
                            error = if (it.error?.contains("评论") == true) null else it.error
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingComments = false,
                            error = "加载评论失败: ${e.message}"
                        )
                    }
                }
        }
    }

    fun loadMoreComments() = loadComments(reset = false)

    fun toggleLike() {
        val repo = repository ?: return
        val post = _uiState.value.post ?: return

        viewModelScope.launch {
            repo.toggleLike(post.id).onSuccess {
                val liked = post.isLiked == 0
                _uiState.update { state ->
                    state.copy(
                        post = post.copy(
                            isLiked = if (liked) 1 else 0,
                            likeNum = if (liked) post.likeNum + 1 else (post.likeNum - 1).coerceAtLeast(0)
                        )
                    )
                }
            }
        }
    }

    fun toggleCollect() {
        val repo = repository ?: return
        val post = _uiState.value.post ?: return

        viewModelScope.launch {
            repo.toggleCollect(post.id).onSuccess {
                val collected = post.isCollected == 0
                _uiState.update { state ->
                    state.copy(
                        post = post.copy(
                            isCollected = if (collected) 1 else 0,
                            collectNum = if (collected) post.collectNum + 1 else (post.collectNum - 1).coerceAtLeast(0)
                        )
                    )
                }
            }
        }
    }

    fun deletePost() {
        val repo = repository ?: return

        viewModelScope.launch {
            repo.deletePost(postId).onSuccess {
                _uiState.update { it.copy(isDeleted = true) }
            }
        }
    }
}

data class PostDetailUiState(
    val post: PostDetail? = null,
    val comments: List<CommentItem> = emptyList(),
    val commentTotal: Int = 0,
    val commentPage: Int = 0,
    val hasMoreComments: Boolean = true,
    val isLoadingDetail: Boolean = true,
    val isLoadingComments: Boolean = false,
    val isManageEnabled: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null
)