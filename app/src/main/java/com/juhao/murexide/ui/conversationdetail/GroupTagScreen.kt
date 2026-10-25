package com.juhao.murexide.ui.conversationdetail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.data.GroupTag
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.EmptyPlaceholder
import com.juhao.murexide.ui.components.ExpressiveDropdownMenu
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

private val tagPalette = listOf(
    "#2196F3", "#E91E63", "#4CAF50", "#FF9800", "#9C27B0", "#00BCD4",
    "#F44336", "#3F51B5", "#8BC34A", "#FFC107", "#795548", "#607D8B"
)

private fun tagColor(color: String): Color {
    val hex = color.trim().ifBlank { "#2196F3" }
    return runCatching {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    }.getOrDefault(Color(0xFF2196F3))
}

@Composable
fun GroupTagScreen(
    viewModel: GroupTagViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toast.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val activeTag = state.activeTag
    if (activeTag == null) {
        TagListPage(state = state, viewModel = viewModel, onBack = onBack)
    } else {
        TagMemberPage(tag = activeTag, state = state, viewModel = viewModel)
    }

    if (state.showEditor) {
        TagEditorDialog(state = state, viewModel = viewModel)
    }

    val deleteTarget = state.deleteTarget
    if (state.showDeleteConfirm && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            icon = { Icon(AppIcons.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除标签") },
            text = { Text("确定要删除标签「${deleteTarget.tag}」吗？已关联该标签的成员会失去此标签。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagListPage(
    state: GroupTagUiState,
    viewModel: GroupTagViewModel,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var searchVisible by remember { mutableStateOf(false) }

    LoadMoreEffect(
        listState = listState,
        itemCount = state.tags.size,
        canLoadMore = state.hasMore && !state.isLoading && !state.isLoadingMore,
        onLoadMore = viewModel::loadMoreTags
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("标签管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) viewModel.searchTags("")
                        }
                    ) {
                        Icon(AppIcons.Search, contentDescription = "搜索标签")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openEditor(null) },
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text("新建标签") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (searchVisible) {
                OutlinedTextField(
                    value = state.keyword,
                    onValueChange = viewModel::searchTags,
                    placeholder = { Text("搜索标签") },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            when {
                state.isLoading && state.tags.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.tags.isEmpty() && state.error != null -> EmptyPlaceholder(
                    icon = AppIcons.Warning,
                    title = "加载失败",
                    subtitle = state.error,
                    primaryButton = {
                        Button(onClick = viewModel::loadTags) { Text("重试") }
                    }
                )

                state.tags.isEmpty() -> EmptyPlaceholder(
                    icon = AppIcons.Tag,
                    title = "暂无标签",
                    subtitle = "创建标签后可以为群成员添加标识"
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(state.tags, key = { it.id }) { tag ->
                        TagRow(
                            tag = tag,
                            onClick = { viewModel.openTag(tag) },
                            onEdit = { viewModel.openEditor(tag) },
                            onDelete = { viewModel.requestDelete(tag) }
                        )
                    }
                    if (state.hasMore) {
                        item(key = "tags-load-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagRow(
    tag: GroupTag,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember(tag.id) { mutableStateOf(false) }
    ListItem(
        onClick = onClick,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tagColor(tag.color))
            )
        },
        trailingContent = {
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(AppIcons.MoreVert, contentDescription = "更多")
                }
                ExpressiveDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑标签") },
                        leadingIcon = { Icon(AppIcons.Edit, null) },
                        onClick = {
                            expanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除标签", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(AppIcons.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            expanded = false
                            onDelete()
                        }
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = tag.tag,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tag.desc.ifBlank { "排序 ${tag.sort}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagMemberPage(
    tag: GroupTag,
    state: GroupTagUiState,
    viewModel: GroupTagViewModel
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(tag.id) { listState.scrollToItem(0) }

    LoadMoreEffect(
        listState = listState,
        itemCount = state.members.size,
        canLoadMore = state.hasMoreMembers &&
            !state.isLoadingMembers &&
            !state.isLoadingMoreMembers,
        onLoadMore = viewModel::loadMoreMembers
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = tag.tag,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "已添加 ${state.boundUserIds.size} 人",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeTag) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = state.memberKeyword,
                onValueChange = viewModel::searchMembers,
                placeholder = { Text("搜索成员") },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when {
                state.isLoadingMembers && state.members.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.members.isEmpty() -> EmptyPlaceholder(
                    icon = AppIcons.Group,
                    title = "暂无成员",
                    subtitle = "换一个关键词试试"
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.members, key = { it.userId }) { member ->
                        TagMemberRow(
                            member = member,
                            bound = member.userId in state.boundUserIds,
                            pending = state.pendingUserId == member.userId,
                            onClick = { viewModel.toggleMember(member) }
                        )
                    }
                    if (state.hasMoreMembers) {
                        item(key = "members-load-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.isLoadingMoreMembers) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagMemberRow(
    member: GroupMember,
    bound: Boolean,
    pending: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        onClick = onClick,
        leadingContent = { Avatar(url = member.avatarUrl, size = 44.dp) },
        trailingContent = {
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (bound) AppIcons.CheckCircle else AppIcons.CheckBoxOutlineBlank,
                    contentDescription = if (bound) "移除标签" else "添加标签",
                    tint = if (bound) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    member.permissionLevel == 100 -> "群主"
                    member.permissionLevel >= 2 -> "管理员"
                    else -> "成员"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TagEditorDialog(
    state: GroupTagUiState,
    viewModel: GroupTagViewModel
) {
    AlertDialog(
        onDismissRequest = { if (!state.isSubmitting) viewModel.closeEditor() },
        title = { Text(if (state.draftIsNew) "新建标签" else "编辑标签") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.draftName,
                    onValueChange = viewModel::updateDraftName,
                    label = { Text("标签名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.draftDesc,
                    onValueChange = viewModel::updateDraftDesc,
                    label = { Text("标签描述") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.draftSort,
                    onValueChange = viewModel::updateDraftSort,
                    label = { Text("排序") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "标签颜色",
                    style = MaterialTheme.typography.labelLarge
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tagPalette.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { hex ->
                                val selected = state.draftColor.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(tagColor(hex))
                                        .clickable { viewModel.updateDraftColor(hex) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.isSubmitting,
                onClick = viewModel::saveDraft
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !state.isSubmitting,
                onClick = viewModel::closeEditor
            ) { Text("取消") }
        }
    )
}

@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    itemCount: Int,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(listState, itemCount, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layoutInfo.totalItemsCount
        }.collect { (lastVisible, totalItems) ->
            if (totalItems > 0 && lastVisible >= totalItems - 3) onLoadMore()
        }
    }
}
