package com.juhao.murexide.ui.conversationdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.data.GroupTag
import com.juhao.murexide.repository.GroupMemberRepository
import com.juhao.murexide.repository.GroupTagRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupTagUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val tags: List<GroupTag> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val keyword: String = "",
    val error: String? = null,
    val showEditor: Boolean = false,
    val draftIsNew: Boolean = true,
    val draftId: Long = 0L,
    val draftName: String = "",
    val draftColor: String = "#2196F3",
    val draftDesc: String = "",
    val draftSort: String = "0",
    val isSubmitting: Boolean = false,
    val deleteTarget: GroupTag? = null,
    val showDeleteConfirm: Boolean = false,
    val activeTag: GroupTag? = null,
    val boundUserIds: Set<String> = emptySet(),
    val members: List<GroupMember> = emptyList(),
    val memberPage: Int = 1,
    val hasMoreMembers: Boolean = true,
    val isLoadingMembers: Boolean = false,
    val isLoadingMoreMembers: Boolean = false,
    val memberKeyword: String = "",
    val pendingUserId: String? = null
)

class GroupTagViewModel(
    val token: String,
    private val groupId: String,
    val groupName: String = "",
    private val tagRepository: GroupTagRepository = GroupTagRepository(),
    private val memberRepository: GroupMemberRepository = GroupMemberRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupTagUiState())
    val uiState: StateFlow<GroupTagUiState> = _uiState.asStateFlow()

    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private var tagSearchJob: Job? = null
    private var memberSearchJob: Job? = null

    init {
        loadTags()
    }

    fun loadTags() {
        if (_uiState.value.isLoading) return
        fetchTagPage(1, _uiState.value.keyword)
    }

    fun loadMoreTags() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        fetchTagPage(state.page + 1, state.keyword)
    }

    fun searchTags(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
        tagSearchJob?.cancel()
        tagSearchJob = viewModelScope.launch {
            delay(300)
            fetchTagPage(1, keyword)
        }
    }

    fun openEditor(tag: GroupTag?) {
        _uiState.update {
            if (tag == null) {
                it.copy(
                    showEditor = true,
                    draftIsNew = true,
                    draftId = 0L,
                    draftName = "",
                    draftColor = DEFAULT_COLOR,
                    draftDesc = "",
                    draftSort = "0"
                )
            } else {
                it.copy(
                    showEditor = true,
                    draftIsNew = false,
                    draftId = tag.id,
                    draftName = tag.tag,
                    draftColor = tag.color.ifBlank { DEFAULT_COLOR },
                    draftDesc = tag.desc,
                    draftSort = tag.sort.toString()
                )
            }
        }
    }

    fun closeEditor() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(showEditor = false) }
    }

    fun updateDraftName(value: String) = _uiState.update { it.copy(draftName = value) }
    fun updateDraftColor(value: String) = _uiState.update { it.copy(draftColor = value) }
    fun updateDraftDesc(value: String) = _uiState.update { it.copy(draftDesc = value) }
    fun updateDraftSort(value: String) = _uiState.update { it.copy(draftSort = value) }

    fun saveDraft() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val name = state.draftName.trim()
        if (name.isEmpty()) {
            notify("标签名称不能为空")
            return
        }
        val sort = state.draftSort.trim().toIntOrNull() ?: 0
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = if (state.draftIsNew) {
                tagRepository.createTag(
                    token = token,
                    groupId = groupId,
                    tag = name,
                    color = state.draftColor,
                    desc = state.draftDesc.trim(),
                    sort = sort
                )
            } else {
                tagRepository.editTag(
                    token = token,
                    id = state.draftId,
                    groupId = groupId,
                    tag = name,
                    color = state.draftColor,
                    desc = state.draftDesc.trim(),
                    sort = sort
                )
            }
            result.onSuccess {
                _uiState.update { it.copy(isSubmitting = false, showEditor = false) }
                _toast.emit(if (state.draftIsNew) "标签已创建" else "标签已更新")
                fetchTagPage(1, _uiState.value.keyword)
            }.onFailure { error ->
                _uiState.update { it.copy(isSubmitting = false) }
                _toast.emit(error.message ?: "保存失败")
            }
        }
    }

    fun requestDelete(tag: GroupTag) =
        _uiState.update { it.copy(deleteTarget = tag, showDeleteConfirm = true) }

    fun dismissDelete() =
        _uiState.update { it.copy(deleteTarget = null, showDeleteConfirm = false) }

    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        _uiState.update { it.copy(deleteTarget = null, showDeleteConfirm = false) }
        viewModelScope.launch {
            tagRepository.deleteTag(token, target.id).onSuccess {
                _uiState.update { state ->
                    state.copy(tags = state.tags.filterNot { it.id == target.id })
                }
                _toast.emit("标签已删除")
            }.onFailure { error ->
                _toast.emit(error.message ?: "删除失败")
            }
        }
    }

    fun openTag(tag: GroupTag) {
        _uiState.update {
            it.copy(
                activeTag = tag,
                members = emptyList(),
                memberPage = 1,
                hasMoreMembers = true,
                memberKeyword = "",
                boundUserIds = emptySet(),
                pendingUserId = null,
                isLoadingMembers = true,
                isLoadingMoreMembers = false
            )
        }
        loadBindings(tag)
        fetchMemberPage(tag, 1, "")
    }

    fun closeTag() {
        _uiState.update {
            it.copy(
                activeTag = null,
                members = emptyList(),
                memberPage = 1,
                hasMoreMembers = true,
                boundUserIds = emptySet(),
                pendingUserId = null,
                memberKeyword = "",
                isLoadingMembers = false,
                isLoadingMoreMembers = false
            )
        }
    }

    fun loadMoreMembers() {
        val state = _uiState.value
        val tag = state.activeTag ?: return
        if (state.isLoadingMembers || state.isLoadingMoreMembers || !state.hasMoreMembers) return
        fetchMemberPage(tag, state.memberPage + 1, state.memberKeyword)
    }

    fun searchMembers(keyword: String) {
        val tag = _uiState.value.activeTag ?: return
        _uiState.update { it.copy(memberKeyword = keyword) }
        memberSearchJob?.cancel()
        memberSearchJob = viewModelScope.launch {
            delay(300)
            fetchMemberPage(tag, 1, keyword)
        }
    }

    fun toggleMember(member: GroupMember) {
        val state = _uiState.value
        val tag = state.activeTag ?: return
        if (state.pendingUserId != null) return
        val bound = member.userId in state.boundUserIds
        _uiState.update { it.copy(pendingUserId = member.userId) }
        viewModelScope.launch {
            val result = if (bound) {
                tagRepository.relateCancelTag(token, member.userId, tag.id)
            } else {
                tagRepository.relateTag(token, member.userId, tag.id)
            }
            result.onSuccess {
                _uiState.update { current ->
                    if (current.activeTag?.id != tag.id) {
                        current
                    } else {
                        current.copy(
                            boundUserIds = if (bound) {
                                current.boundUserIds - member.userId
                            } else {
                                current.boundUserIds + member.userId
                            },
                            pendingUserId = null
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(pendingUserId = null) }
                _toast.emit(error.message ?: "操作失败")
            }
        }
    }

    private fun fetchTagPage(page: Int, keyword: String) {
        val first = page <= 1
        _uiState.update {
            it.copy(
                isLoading = first,
                isLoadingMore = !first,
                tags = if (first) emptyList() else it.tags,
                page = if (first) 1 else it.page,
                error = null
            )
        }
        viewModelScope.launch {
            tagRepository.listTags(token, groupId, page, PAGE_SIZE, keyword)
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            tags = (if (first) emptyList() else it.tags)
                                .plus(items)
                                .distinctBy(GroupTag::id),
                            page = page,
                            hasMore = items.size >= PAGE_SIZE
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = error.message ?: "标签加载失败"
                        )
                    }
                }
        }
    }

    private fun fetchMemberPage(tag: GroupTag, page: Int, keyword: String) {
        val first = page <= 1
        _uiState.update {
            it.copy(
                isLoadingMembers = first,
                isLoadingMoreMembers = !first,
                members = if (first) emptyList() else it.members
            )
        }
        viewModelScope.launch {
            memberRepository.listMembers(token, groupId, page, PAGE_SIZE, keyword)
                .onSuccess { items ->
                    _uiState.update { current ->
                        if (current.activeTag?.id != tag.id) {
                            current
                        } else {
                            current.copy(
                                members = (if (first) emptyList() else current.members)
                                    .plus(items)
                                    .distinctBy(GroupMember::userId),
                                memberPage = page,
                                hasMoreMembers = items.size >= PAGE_SIZE,
                                isLoadingMembers = false,
                                isLoadingMoreMembers = false
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        if (current.activeTag?.id != tag.id) {
                            current
                        } else {
                            current.copy(
                                isLoadingMembers = false,
                                isLoadingMoreMembers = false
                            )
                        }
                    }
                    _toast.emit(error.message ?: "成员加载失败")
                }
        }
    }

    private fun loadBindings(tag: GroupTag) {
        viewModelScope.launch {
            val ids = mutableSetOf<String>()
            var page = 1
            var finished = false
            while (!finished) {
                val result = tagRepository.listTagMembers(token, groupId, tag.id, page, PAGE_SIZE)
                val pageData = result.getOrNull()
                if (pageData == null) {
                    result.exceptionOrNull()?.let { error ->
                        _toast.emit(error.message ?: "标签成员加载失败")
                    }
                    finished = true
                } else {
                    ids += pageData.members.map(GroupMember::userId)
                    finished = pageData.members.size < PAGE_SIZE ||
                        ids.size >= pageData.total ||
                        page >= MAX_BINDING_PAGES
                    page++
                }
            }
            _uiState.update { current ->
                if (current.activeTag?.id != tag.id) current else current.copy(boundUserIds = ids)
            }
        }
    }

    private fun notify(message: String) {
        viewModelScope.launch { _toast.emit(message) }
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val MAX_BINDING_PAGES = 20
        const val DEFAULT_COLOR = "#2196F3"
    }
}
