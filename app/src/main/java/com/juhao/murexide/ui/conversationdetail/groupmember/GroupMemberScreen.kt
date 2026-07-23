package com.juhao.murexide.ui.conversationdetail.groupmember

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.StyledIconButton
import com.juhao.murexide.ui.components.StyledTopBar
import com.juhao.murexide.ui.conversationdetail.ConversationDetailActivity
import com.juhao.murexide.ui.theme.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMemberScreen(
    viewModel: GroupMemberViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.toastShown()
        }
    }

    var selectedGagDuration by remember { mutableIntStateOf(600) }
    val gagOptions = listOf(
        0 to "取消禁言",
        600 to "10分钟",
        3600 to "1小时",
        21600 to "6小时",
        43200 to "12小时",
        1 to "永久"
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            StyledTopBar(
                title = {
                    Text(
                        text = "群成员列表"
                    )
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    StyledIconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.members.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.members.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("重试")
                        }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = uiState.members,
                            key = { it.userId }
                        ) { member ->
                            MemberItem(
                                member = member,
                                isOwner = uiState.isOwner,
                                isAdmin = uiState.isAdmin,
                                onKick = { viewModel.kickMember(it) },
                                onGag = { viewModel.showGagDialog(it) },
                                onSetAdmin = { viewModel.showSetAdmin(it) },
                                onCancelAdmin = { viewModel.showCancelAdmin(it) }
                            )
                        }

                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        if (uiState.hasMore && !uiState.isLoadingMore && uiState.members.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "上拉加载更多",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // 加载更多监听
                    LaunchedEffect(listState, uiState.members.size) {
                        snapshotFlow {
                            val info = listState.layoutInfo
                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                            Pair(lastVisible, info.totalItemsCount)
                        }.collect { (lastVisible, total) ->
                            if (uiState.hasMore && !uiState.isLoadingMore && total > 0 && lastVisible >= total - 3) {
                                viewModel.loadMore()
                            }
                        }
                    }
                }
            }

            // 遮罩层 - 操作进行中
            if (uiState.isPerformingAction) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // 踢出确认对话框
    if (uiState.showKickConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            icon = {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("踢出成员") },
            text = {
                Text("确定要踢出 ${uiState.kickTarget?.name ?: "该成员"} 吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmKick
                ) {
                    Text("踢出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text("取消")
                }
            }
        )
    }

    // 禁言对话框
    if (uiState.showGagDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            icon = { Icon(Icons.Rounded.AccessTime, contentDescription = null) },
            title = { Text("禁言 ${uiState.gagTarget?.name ?: ""}") },
            text = {
                Column {
                    Text("选择禁言时长：", modifier = Modifier.padding(bottom = 8.dp))
                    RadioButtonGroup(
                        options = gagOptions,
                        selectedValue = selectedGagDuration,
                        onValueChange = { selectedGagDuration = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmGag(selectedGagDuration)
                        selectedGagDuration = 600 // 重置
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text("取消")
                }
            }
        )
    }

    // 设置管理员确认对话框
    if (uiState.showSetAdminConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            icon = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null) },
            title = { Text("设置管理员") },
            text = {
                Text("确定要设置 ${uiState.setAdminTarget?.name ?: "该成员"} 为群管理员吗？")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSetAdmin) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text("取消")
                }
            }
        )
    }

    // 取消管理员确认对话框
    if (uiState.showCancelAdminConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            icon = {
                Icon(
                    Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("取消管理员") },
            text = {
                Text("确定要取消 ${uiState.cancelAdminTarget?.name ?: "该成员"} 的管理员权限吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmCancelAdmin,
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun RadioButtonGroup(
    options: List<Pair<Int, String>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        options.forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onValueChange(value) }
            ) {
                RadioButton(
                    selected = selectedValue == value,
                    onClick = { onValueChange(value) }
                )
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun MemberItem(
    member: GroupMember,
    isOwner: Boolean,
    isAdmin: Boolean,
    onKick: (GroupMember) -> Unit,
    onGag: (GroupMember) -> Unit,
    onSetAdmin: (GroupMember) -> Unit,
    onCancelAdmin: (GroupMember) -> Unit
) {
    val context = LocalContext.current

    val isGroupOwner = member.permissionLevel == 100
    val isGroupAdmin = member.permissionLevel == 2
    val canManage = isOwner || isAdmin

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                ConversationDetailActivity.start(
                    context = context,
                    chatId = member.userId,
                    chatType = 1,
                    chatName = member.name,
                    chatAvatar = member.avatarUrl
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                url = member.avatarUrl,
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isGroupOwner) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "群主",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isGroupAdmin) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "管理员",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (member.isVip) {
                        Text(
                            text = "VIP",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (member.isGag) {
                        Icon(
                            imageVector = Icons.Rounded.MicOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "ID: ${member.userId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canManage && !isGroupOwner) {
                Box {
                    IconButton(
                        onClick = { expanded = !expanded }
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "管理"
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (isOwner) {
                            if (isGroupAdmin) {
                                DropdownMenuItem(
                                    text = { Text("取消管理员", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        expanded = false
                                        onCancelAdmin(member)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("设为管理员") },
                                    onClick = {
                                        expanded = false
                                        onSetAdmin(member)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        DropdownMenuItem(
                            text = { Text("禁言") },
                            onClick = {
                                expanded = false
                                onGag(member)
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.MicOff, contentDescription = null)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("踢出群聊", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                expanded = false
                                onKick(member)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.PersonRemove,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}