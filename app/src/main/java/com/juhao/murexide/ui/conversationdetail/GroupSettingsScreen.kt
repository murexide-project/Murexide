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
import com.juhao.murexide.ui.components.SettingsSwitchItem
import kotlinx.coroutines.flow.collectLatest

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

            Spacer(Modifier.height(24.dp))
        }
    }
}
