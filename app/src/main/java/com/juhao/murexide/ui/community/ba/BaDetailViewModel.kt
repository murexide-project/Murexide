package com.juhao.murexide.ui.community.ba

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.BaDetail
import com.juhao.murexide.data.FollowerItem
import com.juhao.murexide.data.PostItem
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.repository.CommunityRepository
import com.juhao.murexide.utils.QiniuUploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BaDetailViewModel(
    private val accountStorage: AccountStorage,
    private val baId: Int
) : ViewModel() {

    private var repository: CommunityRepository? = null
    private val pageSize = 20
    private var token: String = ""

    private val _uiState = MutableStateFlow(BaDetailUiState())
    val uiState: StateFlow<BaDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val currentToken = accountStorage.getCurrentToken()
            if (currentToken == null) {
                _uiState.update { it.copy(error = "请先登录", isLoadingInfo = false, isLoadingPosts = false) }
                return@launch
            }
            token = currentToken
            repository = CommunityRepository(currentToken)
            loadInfo()
            loadPosts(reset = true)
        }
    }

    fun loadInfo() {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInfo = true) }
            repo.getBaInfo(baId)
                .onSuccess { ba ->
                    _uiState.update { it.copy(ba = ba, isLoadingInfo = false) }
                    checkManageAccess()
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingInfo = false, error = e.message) } }
        }
    }

    fun checkManageAccess() {
        val repo = repository ?: return
        if (_uiState.value.isCheckingManage) return
        val userId = accountStorage.getCurrentUserIdSync() ?: return
        _uiState.update { it.copy(isCheckingManage = true) }
        viewModelScope.launch {
            repo.getBaListByCreate(userId)
                .onSuccess { list ->
                    if (list.any { it.id == baId }) {
                        _uiState.update { it.copy(isOwner = true, isManageEnabled = true, isCheckingManage = false) }
                    } else {
                        repo.getBaFollowers(baId, size = 1, page = 1, memberName = "")
                            .onSuccess {
                                _uiState.update { it.copy(isManageEnabled = true, isCheckingManage = false) }
                            }
                            .onFailure {
                                _uiState.update { it.copy(isManageEnabled = false, isCheckingManage = false) }
                            }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isManageEnabled = false, isCheckingManage = false) }
                }
        }
    }

    fun uploadBaAvatar(context: Context, uri: Uri) {
        if (_uiState.value.isUploadingAvatar || _uiState.value.isSavingEdit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, uploadProgress = 0f, error = null) }
            QiniuUploader(context, token, enableWebp = true).uploadFromUri(context, uri) { progress ->
                _uiState.update { it.copy(uploadProgress = progress) }
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        editAvatarUrl = "https://chat-img.jwznb.com/${response.key}"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        error = "头像上传失败: ${error.message}"
                    )
                }
            }
        }
    }

    fun editBa(name: String, onResult: (Boolean, String?) -> Unit) {
        val repo = repository ?: return
        if (_uiState.value.isSavingEdit || _uiState.value.isUploadingAvatar) return
        if (name.isBlank()) {
            onResult(false, "分区名称不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingEdit = true, error = null) }
            val avatar = _uiState.value.editAvatarUrl.ifBlank { _uiState.value.ba?.avatar ?: "" }
            repo.editBa(baId, name.trim(), avatar)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            isSavingEdit = false,
                            ba = state.ba?.copy(name = name.trim(), avatar = avatar)
                        )
                    }
                    onResult(true, null)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSavingEdit = false, error = e.message) }
                    onResult(false, e.message ?: "编辑分区失败")
                }
        }
    }

    fun saveManageSettings(visibleRange: Int, publishAuthority: Int, onResult: (Boolean, String?) -> Unit) {
        val repo = repository ?: return
        if (_uiState.value.isSavingSettings) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingSettings = true, error = null) }
            repo.manageBa(baId, visibleRange, publishAuthority)
                .onSuccess {
                    _uiState.update { it.copy(isSavingSettings = false) }
                    onResult(true, null)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSavingSettings = false, error = e.message) }
                    onResult(false, e.message ?: "保存设置失败")
                }
        }
    }

    fun deleteBa(onResult: (Boolean, String?) -> Unit) {
        val repo = repository ?: return
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, error = null) }
            repo.deleteBa(baId)
                .onSuccess {
                    _uiState.update { it.copy(isDeleting = false) }
                    onResult(true, null)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isDeleting = false, error = e.message) }
                    onResult(false, e.message ?: "删除分区失败")
                }
        }
    }

    fun loadFollowers(reset: Boolean, keyword: String = "") {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.isLoadingFollowers) return
        if (!reset && !state.hasMoreFollowers) return
        if (reset) {
            _uiState.update {
                it.copy(isLoadingFollowers = true, followerKeyword = keyword, followerPage = 0, error = null)
            }
        } else {
            _uiState.update { it.copy(isLoadingFollowers = true) }
        }
        val nextPage = if (reset) 1 else state.followerPage + 1
        viewModelScope.launch {
            repo.getBaFollowers(baId, size = 20, page = nextPage, memberName = keyword)
                .onSuccess { data ->
                    val merged = if (reset) data.followers else state.followers + data.followers
                    _uiState.update {
                        it.copy(
                            followers = merged,
                            followerTotal = data.total,
                            followerPage = nextPage,
                            hasMoreFollowers = data.followers.isNotEmpty() && merged.size < data.total,
                            isLoadingFollowers = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoadingFollowers = false, error = e.message)
                    }
                }
        }
    }

    fun loadMoreFollowers() = loadFollowers(reset = false, keyword = _uiState.value.followerKeyword)

    fun setFollowerLevel(userId: String, level: Int, onResult: (Boolean, String?) -> Unit) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.setBaManager(baId, userId, level)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            followers = state.followers.map { follower ->
                                if (follower.userId == userId) follower.copy(userLevel = level) else follower
                            }
                        )
                    }
                    onResult(true, null)
                }
                .onFailure { e ->
                    onResult(false, e.message ?: "设置失败")
                }
        }
    }

    fun loadPosts(reset: Boolean) {
        val repo = repository ?: return
        val state = _uiState.value
        if (state.isLoadingPosts || state.isLoadingMore) return
        if (!reset && !state.hasMore) return

        val nextPage = if (reset) 1 else state.currentPage + 1
        viewModelScope.launch {
            _uiState.update {
                if (reset) it.copy(isLoadingPosts = true) else it.copy(isLoadingMore = true)
            }
            repo.getPostList(baId, size = pageSize, page = nextPage)
                .onSuccess { data ->
                    val merged = if (reset) data.posts else state.posts + data.posts
                    _uiState.update {
                        it.copy(
                            posts = merged,
                            total = data.total,
                            currentPage = nextPage,
                            hasMore = data.posts.isNotEmpty() && merged.size < data.total,
                            isLoadingPosts = false,
                            isLoadingMore = false,
                            isRefreshing = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoadingPosts = false, isLoadingMore = false, isRefreshing = false, error = e.message)
                    }
                }
        }
    }

    fun loadMore() = loadPosts(reset = false)

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadInfo()
        loadPosts(reset = true)
    }

    fun toggleFollow() {
        val repo = repository ?: return
        val ba = _uiState.value.ba ?: return
        val isFollowed = ba.isFollowed == "1"
        viewModelScope.launch {
            val result = if (isFollowed) repo.unfollowBa(baId) else repo.followBa(baId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        ba = ba.copy(
                            isFollowed = if (isFollowed) "0" else "1",
                            memberNum = if (isFollowed) (ba.memberNum - 1).coerceAtLeast(0) else ba.memberNum + 1
                        )
                    )
                }
            }
        }
    }

    fun toggleLike(postId: Int) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.toggleLike(postId).onSuccess {
                _uiState.update { state ->
                    state.copy(posts = state.posts.map { post ->
                        if (post.id == postId) {
                            val liked = post.isLiked == "0"
                            post.copy(
                                isLiked = if (liked) "1" else "0",
                                likeNum = if (liked) post.likeNum + 1 else (post.likeNum - 1).coerceAtLeast(0)
                            )
                        } else post
                    })
                }
            }
        }
    }

    fun toggleCollect(postId: Int) {
        val repo = repository ?: return
        viewModelScope.launch {
            repo.toggleCollect(postId).onSuccess {
                _uiState.update { state ->
                    state.copy(posts = state.posts.map { post ->
                        if (post.id == postId) {
                            val collected = post.isCollected == 0
                            post.copy(
                                isCollected = if (collected) 1 else 0,
                                collectNum = if (collected) post.collectNum + 1 else (post.collectNum - 1).coerceAtLeast(0)
                            )
                        } else post
                    })
                }
            }
        }
    }
}

data class BaDetailUiState(
    val ba: BaDetail? = null,
    val posts: List<PostItem> = emptyList(),
    val isLoadingInfo: Boolean = true,
    val isLoadingPosts: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentPage: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val isOwner: Boolean = false,
    val isManageEnabled: Boolean = false,
    val isCheckingManage: Boolean = false,
    val editAvatarUrl: String = "",
    val isUploadingAvatar: Boolean = false,
    val uploadProgress: Float = 0f,
    val isSavingEdit: Boolean = false,
    val isSavingSettings: Boolean = false,
    val isDeleting: Boolean = false,
    val followers: List<FollowerItem> = emptyList(),
    val followerTotal: Int = 0,
    val followerPage: Int = 0,
    val hasMoreFollowers: Boolean = false,
    val isLoadingFollowers: Boolean = false,
    val followerKeyword: String = "",
    val error: String? = null
)
