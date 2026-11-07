package com.juhao.murexide.ui.conversationdetail

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.CustomItemCell
import com.juhao.murexide.ui.components.SettingsGroup
import com.juhao.murexide.ui.components.SettingsItem
import com.juhao.murexide.ui.components.SettingsSwitchItem
import kotlinx.coroutines.flow.collectLatest

/** 可选的受限消息类型：type 到展示名。 */
private val MsgTypeOptions = listOf(
    1 to "文本消息",
    2 to "图片消息",
    3 to "Markdown消息",
    4 to "文件消息",
    5 to "引用消息",
    6 to "帖子消息",
    7 to "表情消息",
    8 to "HTML消息",
    9 to "Bot消息",
    10 to "视频消息",
    11 to "语音消息",
    13 to "语音通话",
    14 to "转账",
    16 to "链接",
    17 to "卡片消息",
    19 to "文件(新)",
    20 to "指令消息",
    21 to "回复消息"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    viewModel: GroupSettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()

    var showMsgTypeDialog by remember { mutableStateOf(false) }
    var showGroupCodeDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.uploadAvatar(context, uri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is GroupSettingsViewModel.GroupSettingsEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is GroupSettingsViewModel.GroupSettingsEvent.Saved -> onBack()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("群设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.save() }) {
                            Icon(AppIcons.Check, contentDescription = "保存")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Avatar(
                        url = uiState.avatarUrl,
                        size = 88.dp,
                        modifier = Modifier.clickable(enabled = !uiState.isUploadingAvatar) {
                            imagePicker.launch("image/*")
                        }
                    )
                    if (uiState.isUploadingAvatar) {
                        CircularProgressIndicator(
                            progress = { uiState.uploadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(88.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (uiState.isUploadingAvatar) "上传中…" else "点击更换群头像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            SettingsGroup(title = "基本信息") {
                CustomItemCell {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::updateName,
                        label = { Text("群名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                CustomItemCell {
                    OutlinedTextField(
                        value = uiState.introduction,
                        onValueChange = viewModel::updateIntroduction,
                        label = { Text("群简介") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SettingsGroup(title = "群聊设置") {
                SettingsSwitchItem(
                    icon = AppIcons.HowToReg,
                    title = "进群免审核",
                    subtitle = "开启后新成员无需验证直接进群",
                    checked = uiState.directJoin,
                    onCheckedChange = viewModel::toggleDirectJoin
                )
                SettingsSwitchItem(
                    icon = AppIcons.History,
                    title = "新成员可见历史消息",
                    checked = uiState.historyMsg,
                    onCheckedChange = viewModel::toggleHistoryMsg
                )
                SettingsSwitchItem(
                    icon = AppIcons.Lock,
                    title = "群聊私有",
                    subtitle = "开启后需要验证才能加入",
                    checked = uiState.isPrivate,
                    onCheckedChange = viewModel::togglePrivate
                )
                SettingsSwitchItem(
                    icon = AppIcons.VisibilityOff,
                    title = "隐藏群成员",
                    checked = uiState.hideGroupMembers,
                    onCheckedChange = viewModel::toggleHideMembers
                )
            }

            if (uiState.isOwner) {
                SettingsGroup(title = "群云盘") {
                    SettingsSwitchItem(
                        icon = AppIcons.Folder,
                        title = "禁止成员上传到群云盘",
                        subtitle = "开启后仅群主可上传文件",
                        checked = uiState.denyMembersUploadToGroupDisk,
                        isEnabled = !uiState.isSavingDenyUpload,
                        onCheckedChange = viewModel::toggleDenyMembersUploadToGroupDisk
                    )
                }
            }

            if (uiState.canManage) {
                SettingsGroup(title = "高级设置") {
                    SettingsItem(
                        icon = AppIcons.TextFields,
                        title = "消息类型限制",
                        subtitle = if (uiState.limitedMsgType.isEmpty()) {
                            "未限制消息类型"
                        } else {
                            "已限制 ${uiState.limitedMsgType.size} 种消息类型"
                        },
                        onClick = { showMsgTypeDialog = true }
                    )
                    if (uiState.isOwner) {
                        SettingsItem(
                            icon = AppIcons.Key,
                            title = "群口令",
                            subtitle = uiState.groupCode.ifBlank { "设置后可用口令搜索加入群聊" },
                            onClick = { showGroupCodeDialog = true }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showMsgTypeDialog) {
        var selected by remember(uiState.limitedMsgType) {
            mutableStateOf(uiState.limitedMsgType)
        }
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSavingMsgTypeLimit) showMsgTypeDialog = false
            },
            icon = { Icon(AppIcons.TextFields, contentDescription = null) },
            title = { Text("限制群内消息类型") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "勾选后，群内成员将无法发送对应类型的消息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    MsgTypeOptions.forEach { (type, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !uiState.isSavingMsgTypeLimit) {
                                    selected = if (type in selected) {
                                        selected - type
                                    } else {
                                        selected + type
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = type in selected,
                                onCheckedChange = null,
                                enabled = !uiState.isSavingMsgTypeLimit
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isSavingMsgTypeLimit,
                    onClick = {
                        showMsgTypeDialog = false
                        viewModel.saveMsgTypeLimit(selected)
                    }
                ) {
                    if (uiState.isSavingMsgTypeLimit) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("保存")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isSavingMsgTypeLimit,
                    onClick = { showMsgTypeDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showGroupCodeDialog) {
        var code by remember(uiState.groupCode) {
            mutableStateOf(uiState.groupCode)
        }
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSavingGroupCode) showGroupCodeDialog = false
            },
            icon = { Icon(AppIcons.Key, contentDescription = null) },
            title = { Text("群口令") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("群口令（为空删除）") },
                    singleLine = true,
                    enabled = !uiState.isSavingGroupCode,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isSavingGroupCode,
                    onClick = {
                        showGroupCodeDialog = false
                        viewModel.saveGroupCode(code)
                    }
                ) {
                    if (uiState.isSavingGroupCode) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("保存")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isSavingGroupCode,
                    onClick = { showGroupCodeDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}