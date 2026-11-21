package com.juhao.murexide.ui.community.ba

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.icons.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaManageSheet(
    isOwner: Boolean,
    viewModel: BaDetailViewModel,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var visibleRange by remember { mutableIntStateOf(0) }
    var publishAuthority by remember { mutableIntStateOf(0) }
    var editName by remember { mutableStateOf(uiState.ba?.name.orEmpty()) }
    var followerKeyword by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.uploadBaAvatar(context, uri)
    }

    LaunchedEffect(Unit) {
        editName = uiState.ba?.name.orEmpty()
        if (uiState.followers.isEmpty() && !uiState.isLoadingFollowers) {
            viewModel.loadFollowers(reset = true)
        }
    }

    val currentUserId = remember {
        AccountStorage.getInstance(context).getCurrentUserIdSync()
    }

    val busy = uiState.isUploadingAvatar || uiState.isSavingEdit

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "分区管理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${uiState.followerTotal} 位关注者",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isOwner) {
                item {
                    ManageCard {
                        Text(
                            "基本资料",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(76.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .align(Alignment.Center)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .clickable(enabled = !busy) {
                                            imagePicker.launch("image/*")
                                        }
                                ) {
                                    val preview = uiState.editAvatarUrl.ifBlank {
                                        uiState.ba?.avatar.orEmpty()
                                    }
                                    if (preview.isNotBlank()) {
                                        Avatar(url = preview, size = 64.dp)
                                    } else {
                                        Box(
                                            modifier = Modifier.size(64.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                AppIcons.Group,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(enabled = !busy) {
                                            imagePicker.launch("image/*")
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            AppIcons.Edit,
                                            contentDescription = "更换头像",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            OutlinedTextField(
                                value = editName,
                                onValueChange = { if (it.length <= 10) editName = it },
                                label = { Text("分区名称") },
                                supportingText = { Text("${editName.length}/10") },
                                singleLine = true,
                                enabled = !busy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (uiState.isUploadingAvatar) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { uiState.uploadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.editBa(editName) { success, message ->
                                    if (success) {
                                        Toast.makeText(context, "分区已更新", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, message ?: "编辑失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = editName.isNotBlank() && !busy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isSavingEdit) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("保存修改")
                            }
                        }
                    }
                }

                item {
                    ManageCard {
                        Text(
                            "可见状态",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        OptionRow(
                            title = "所有人可见",
                            selected = visibleRange == 0,
                            onClick = { visibleRange = 0 }
                        )
                        OptionRow(
                            title = "仅分区所有者可见",
                            selected = visibleRange == 1,
                            onClick = { visibleRange = 1 }
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        Text(
                            "开放度",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        OptionRow(
                            title = "所有人都可以发文章和评论",
                            selected = publishAuthority == 0,
                            onClick = { publishAuthority = 0 }
                        )
                        OptionRow(
                            title = "仅所有者可发文章",
                            subtitle = "任何人都可以评论",
                            selected = publishAuthority == 1,
                            onClick = { publishAuthority = 1 }
                        )
                        OptionRow(
                            title = "仅所有者可发文章和评论",
                            selected = publishAuthority == 2,
                            onClick = { publishAuthority = 2 }
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.saveManageSettings(visibleRange, publishAuthority) { success, message ->
                                    if (success) {
                                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, message ?: "保存失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !uiState.isSavingSettings,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isSavingSettings) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("保存设置")
                            }
                        }
                    }
                }
            }

            item {
                ManageCard {
                    Text(
                        "关注者",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = followerKeyword,
                        onValueChange = { keyword ->
                            followerKeyword = keyword
                            viewModel.loadFollowers(reset = true, keyword = keyword)
                        },
                        placeholder = { Text("搜索关注者") },
                        singleLine = true,
                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    when {
                        uiState.followers.isEmpty() && uiState.isLoadingFollowers -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(Modifier.size(26.dp))
                            }
                        }

                        uiState.followers.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (followerKeyword.isBlank()) "暂无关注者" else "未找到关注者",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> Column {
                            uiState.followers.forEachIndexed { index, follower ->
                                if (index > 0) HorizontalDivider()
                                FollowerRow(
                                    nickname = follower.nickname,
                                    avatarUrl = follower.avatarUrl,
                                    userId = follower.userId,
                                    isManager = follower.userLevel == 2,
                                    canManage = isOwner && follower.userId != currentUserId,
                                    onToggleManager = {
                                        val level = if (follower.userLevel == 2) 0 else 2
                                        viewModel.setFollowerLevel(follower.userId, level) { success, message ->
                                            if (success) {
                                                Toast.makeText(
                                                    context,
                                                    if (level == 2) "已设为管理员" else "已取消管理员",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Toast.makeText(context, message ?: "设置失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }

                            if (uiState.hasMoreFollowers) {
                                TextButton(
                                    onClick = { viewModel.loadMoreFollowers() },
                                    enabled = !uiState.isLoadingFollowers,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    if (uiState.isLoadingFollowers) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("加载更多")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isOwner) {
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !uiState.isDeleting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (uiState.isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("删除分区")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isDeleting) showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除此分区吗？删除后该分区及其文章将无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBa { success, message ->
                            showDeleteConfirm = false
                            if (success) {
                                Toast.makeText(context, "分区已删除", Toast.LENGTH_SHORT).show()
                                onDeleted()
                            } else {
                                Toast.makeText(context, message ?: "删除失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ManageCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FollowerRow(
    nickname: String,
    avatarUrl: String,
    userId: String,
    isManager: Boolean,
    canManage: Boolean,
    onToggleManager: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Avatar(url = avatarUrl, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    nickname.ifBlank { userId },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isManager) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            "管理员",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "UID $userId",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (canManage) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onToggleManager) {
                Text(
                    if (isManager) "取消管理员" else "设为管理员",
                    fontSize = 13.sp
                )
            }
        }
    }
}