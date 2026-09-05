package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.UiState
import kotlinx.coroutines.launch
import com.juhao.murexide.ui.settings.switchAccount.SwitchAccountActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPage(
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showGetTokenDialog by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboard.current
    val accountStorage = remember(context.applicationContext) {
        AccountStorage.getInstance(context.applicationContext)
    }
    
    var currentAccount by remember { mutableStateOf<UserAccount?>(null) }
    
    LaunchedEffect(Unit) {
        currentAccount = accountStorage.getCurrentAccount()
    }
    
    if (showGetTokenDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                AutoMirroredIcon(AppIcons.Info, contentDescription = null)
            },
            title = { Text("提取当前 Token") },
            text = { Text("点击确定按钮复制 Token，请注意不要泄露你的 Token，泄露后果自负！", color = MaterialTheme.colorScheme.error) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGetTokenDialog = false
                        scope.launch {
                            clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("token", accountStorage.getCurrentToken() ?: "")))
                        }
                        Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGetTokenDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                AutoMirroredIcon(AppIcons.Logout, contentDescription = null)
            },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    currentAccount?.let {
        SettingsGroup() {
            ListItem(
                verticalAlignment = Alignment.CenterVertically,
                leadingContent = {
                    Avatar(url = it.avatar, size = 52.dp)
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.64f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = it.username,
                        style = MaterialTheme.typography.bodyLarge                )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ID: ${it.id}",
                        style = MaterialTheme.typography.bodySmall                )
                }
            }
        }
    }
    
    SettingsGroup(title = "账号") {
        SettingsItem(
            icon = AppIcons.People,
            title = "切换账号",
            onClick = {
                val intent = Intent(context, SwitchAccountActivity::class.java)
                context.startActivity(intent)
            }
        )
        SettingsItem(
            icon = AppIcons.Key,
            title = "提取账号 Token",
            subtitle = "提取当前账号的 Token 至剪贴板",
            isDestructive = true,
            onClick = {
                showGetTokenDialog = true
            }
        )
        SettingsItem(
            icon = AppIcons.Logout,
            title = "退出登录",
            isDestructive = true,
            onClick = { showLogoutDialog = true }
        )
    }
}