package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.settings.switchAccount.SwitchAccountActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountControl(
    highlightId: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showGetTokenDialog by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboard.current
    val accountStorage = remember(context.applicationContext) {
        AccountStorage.getInstance(context.applicationContext)
    }

    var currentAccount by remember { mutableStateOf<UserAccount?>(null) }
    LaunchedEffect(Unit) { currentAccount = accountStorage.getCurrentAccount() }

    if (showGetTokenDialog) {
        AlertDialog(
            onDismissRequest = { showGetTokenDialog = false },
            icon = { AutoMirroredIcon(AppIcons.Info, contentDescription = null) },
            title = { Text("提取当前 Token") },
            text = {
                Text(
                    "点击确定按钮复制 Token，请注意不要泄露你的 Token，泄露后果自负！",
                    color = MaterialTheme.colorScheme.error
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGetTokenDialog = false
                        scope.launch {
                            clipboardManager.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        "token",
                                        accountStorage.getCurrentToken() ?: ""
                                    )
                                )
                            )
                        }
                        Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("确定", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showGetTokenDialog = false }) { Text("取消") }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { AutoMirroredIcon(AppIcons.Logout, contentDescription = null) },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            }
        )
    }

    val allIds = listOf("header_account", "account_switch", "account_token", "account_logout")
    val listState = rememberLazyListState()
    LaunchedEffect(highlightId, allIds) {
        if (!highlightId.isNullOrBlank()) {
            val index = allIds.indexOf(highlightId)
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        )
    ) {
        currentAccount?.let { account ->
            item { SettingsGroupHeader("当前账号") }
            item {
                SettingsGroupSurface(isTop = true, isBottom = true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(url = account.avatar, size = 52.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(account.username, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "ID: ${account.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item { SettingsGroupHeader("账号") }
        item {
            SettingsGroupSurface(isTop = true, isBottom = false, id = "account_switch", highlightId = highlightId) {
                SettingsItem(
                    icon = AppIcons.People,
                    title = "切换账号",
                    onClick = {
                        context.startActivity(Intent(context, SwitchAccountActivity::class.java))
                    }
                )
            }
        }
        item {
            SettingsGroupSurface(isTop = false, isBottom = false, id = "account_token", highlightId = highlightId) {
                SettingsItem(
                    icon = AppIcons.Key,
                    title = "提取账号 Token",
                    subtitle = "提取当前账号的 Token 至剪贴板",
                    isDestructive = true,
                    onClick = { showGetTokenDialog = true }
                )
            }
        }
        item {
            SettingsGroupSurface(isTop = false, isBottom = true, id = "account_logout", highlightId = highlightId) {
                SettingsItem(
                    icon = AppIcons.Logout,
                    title = "退出登录",
                    isDestructive = true,
                    onClick = { showLogoutDialog = true }
                )
            }
        }
    }
}