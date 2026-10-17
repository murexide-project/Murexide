package com.juhao.murexide.ui.conversationdetail

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.ClipData
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.CustomItemCell
import com.juhao.murexide.ui.components.SettingsGroup
import com.juhao.murexide.ui.components.SettingsItemCell
import com.juhao.murexide.ui.components.SettingsSwitchItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotSettingsScreen(
    viewModel: BotSettingsViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
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
                is BotSettingsViewModel.BotSettingsEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is BotSettingsViewModel.BotSettingsEvent.Saved -> onSaved()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("机器人设置") },
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
                    } else if (uiState.original != null) {
                        IconButton(
                            onClick = { viewModel.save() },
                            enabled = !uiState.isUploadingAvatar
                        ) {
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
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val loadError = uiState.loadError
        if (loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = loadError,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::load) { Text("重试") }
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
                    text = if (uiState.isUploadingAvatar) "上传中…" else "点击更换机器人头像",
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
                        label = { Text("机器人名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                CustomItemCell {
                    OutlinedTextField(
                        value = uiState.introduction,
                        onValueChange = viewModel::updateIntroduction,
                        label = { Text("机器人简介") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SettingsSwitchItem(
                    icon = AppIcons.Lock,
                    title = "私有机器人",
                    subtitle = "开启后不在机器人商店展示",
                    checked = uiState.isPrivate,
                    onCheckedChange = viewModel::togglePrivate
                )
            }

            SettingsGroup(title = "消息订阅") {
                CustomItemCell {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.subscribeType == 0,
                            onClick = { viewModel.updateSubscribeType(0) },
                            label = { Text("URL 订阅") }
                        )
                        FilterChip(
                            selected = uiState.subscribeType == 1,
                            onClick = { viewModel.updateSubscribeType(1) },
                            label = { Text("WSS 订阅") }
                        )
                    }
                }
                CustomItemCell {
                    OutlinedTextField(
                        value = uiState.link,
                        onValueChange = viewModel::updateLink,
                        label = { Text("订阅地址") },
                        placeholder = {
                            Text(if (uiState.subscribeType == 1) "wss://..." else "https://...")
                        },
                        supportingText = if (uiState.original?.linkStop == true) {
                            { Text("当前订阅地址已被停用", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SettingsGroup(title = "机器人设置 JSON") {
                CustomItemCell {
                    OutlinedTextField(
                        value = uiState.settingJson,
                        onValueChange = viewModel::updateSettingJson,
                        label = { Text("settingJson") },
                        placeholder = { Text("[]") },
                        supportingText = { Text("群内机器人设置表单，需为 JSON 数组") },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        minLines = 3,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SettingsGroup(title = "Token") {
                SettingsItemCell(
                    icon = AppIcons.Key,
                    endIcon = if (uiState.tokenVisible) AppIcons.VisibilityOff else AppIcons.Visibility,
                    title = if (uiState.tokenVisible) uiState.botToken else maskToken(uiState.botToken),
                    subtitle = "点击显示 / 隐藏",
                    onClick = viewModel::toggleTokenVisible
                )
                SettingsItemCell(
                    icon = AppIcons.ContentCopy,
                    title = "复制 Token",
                    isEnabled = uiState.botToken.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("bot token", uiState.botToken))
                            )
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                SettingsItemCell(
                    icon = AppIcons.Refresh,
                    title = if (uiState.isResettingToken) "重置中…" else "重置 Token",
                    subtitle = "旧 Token 将立即失效",
                    isDestructive = true,
                    isEnabled = !uiState.isResettingToken,
                    onClick = { viewModel.showResetConfirm(true) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (uiState.showResetConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showResetConfirm(false) },
            title = { Text("重置 Token？") },
            text = { Text("重置后旧 Token 会立即失效，使用旧 Token 的服务需要同步更新。") },
            confirmButton = {
                TextButton(onClick = viewModel::resetToken) {
                    Text("重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showResetConfirm(false) }) { Text("取消") }
            }
        )
    }
}

private fun maskToken(token: String): String = when {
    token.isEmpty() -> "（无）"
    token.length <= 8 -> "••••••••"
    else -> token.take(4) + "••••••••" + token.takeLast(4)
}
