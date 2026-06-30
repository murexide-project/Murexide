package com.juhao.murexide.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.BaItem
import com.juhao.murexide.data.PostItem
import com.juhao.murexide.repository.CommunityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunityViewModel(
    token: String
) : ViewModel() {

    private val repository = CommunityRepository(token)

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private var currentBaId: Int = 0

    init {
        loadData()
    }

    fun loadData() {
        loadBaList()
        loadPosts()
    }

    private fun loadBaList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBa = true)
            val result = repository.getBaList()
            result.onSuccess { list ->
                _uiState.value = _uiState.value.copy(
                    baList = list,
                    isLoadingBa = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoadingBa = false,
                    error = e.message
                )
            }
        }
    }

    fun loadPosts(baId: Int? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPosts = true)

            val result = if (baId != null && baId > 0) {
                currentBaId = baId
                repository.getPostList(baId)
            } else {
                currentBaId = 0
                repository.getRecommendPosts()
            }

            result.onSuccess { list ->
                _uiState.value = _uiState.value.copy(
                    posts = list,
                    isLoadingPosts = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoadingPosts = false,
                    error = e.message
                )
            }
        }
    }

    fun toggleLike(postId: Int) {
        viewModelScope.launch {
            val result = repository.toggleLike(postId)
            result.onSuccess {
                updatePostLike(postId)
            }
        }
    }

    fun toggleCollect(postId: Int) {
        viewModelScope.launch {
            val result = repository.toggleCollect(postId)
            result.onSuccess {
                updatePostCollect(postId)
            }
        }
    }

    private fun updatePostLike(postId: Int) {
        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                val isLiked = post.isLiked == "0"
                post.copy(
                    isLiked = if (isLiked) "1" else "0",
                    likeNum = if (isLiked) post.likeNum + 1 else post.likeNum - 1
                )
            } else post
        }
        _uiState.value = _uiState.value.copy(posts = updatedPosts)
    }

    private fun updatePostCollect(postId: Int) {
        val updatedPosts = _uiState.value.posts.map { post ->
            if (post.id == postId) {
                val isCollected = post.isCollected == 0
                post.copy(
                    isCollected = if (isCollected) 1 else 0,
                    collectNum = if (isCollected) post.collectNum + 1 else post.collectNum - 1
                )
            } else post
        }
        _uiState.value = _uiState.value.copy(posts = updatedPosts)
    }

    fun selectBa(baId: Int) {
        if (currentBaId != baId) {
            loadPosts(baId)
        }
    }

    fun refresh() {
        if (currentBaId == 0) {
            loadPosts()
        } else {
            loadPosts(currentBaId)
        }
    }
}

data class CommunityUiState(
    val baList: List<BaItem> = emptyList(),
    val posts: List<PostItem> = emptyList(),
    val isLoadingBa: Boolean = false,
    val isLoadingPosts: Boolean = false,
    val error: String? = null
)