package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.juhao.murexide.MyApplication
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import kotlinx.coroutines.launch
import com.juhao.murexide.utils.hasLegacyWritePermission
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPage() {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    val (notificationEnabled, onNotificationToggle) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val enabled = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        Pair(enabled) { _: Boolean ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }
    } else {
        val enabledState by settingsStorage.notificationEnabledFlow.collectAsState(initial = true)
        Pair(enabledState) { checked: Boolean ->
            scope.launch {
                settingsStorage.setNotificationEnabled(checked)
            }
        }
    }

    var bigScreen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        bigScreen = settingsStorage.getBigScreen()
    }

    val cacheEnabled by settingsStorage.cacheEnabledFlow.collectAsState(initial = true)

    val appLockEnabled by settingsStorage.appLockEnabledFlow.collectAsState(initial = false)
    val lockOnBackground by settingsStorage.appLockLockOnBackgroundFlow.collectAsState(initial = true)

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDisableLockConfirm by remember { mutableStateOf(false) }

    val legacyStoragePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    var storagePermissionGranted by remember {
        mutableStateOf(!legacyStoragePermission || hasLegacyWritePermission(context))
    }
    var storagePermissionRequestAttempted by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        storagePermissionRequestAttempted = true
        storagePermissionGranted = granted
        if (!granted) {
            Toast.makeText(context, "需要存储权限才能保存下载内容", Toast.LENGTH_SHORT).show()
        }
    }

    fun openApplicationSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        })
    }

    LifecycleResumeEffect(legacyStoragePermission) {
        storagePermissionGranted = !legacyStoragePermission || hasLegacyWritePermission(context)
        onPauseOrDispose { }
    }

    fun requestStoragePermission() {
        if (!legacyStoragePermission) return
        if (storagePermissionRequestAttempted && !storagePermissionGranted) {
            openApplicationSettings()
        } else {
            storagePermissionRequestAttempted = true
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun refreshAppLock() {
        (context.applicationContext as? MyApplication)?.refreshAppLockState()
    }

    SettingsGroup {
        SettingsSwitchItem(
            icon = AppIcons.Notifications,
            title = "消息通知",
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (notificationEnabled) "已允许消息通知" else "已关闭消息通知，点击跳转设置"
            } else {
                "开启或关闭消息通知"
            },
            checked = notificationEnabled,
            onCheckedChange = { onNotificationToggle(it) }
        )
        SettingsSwitchItem(
            icon = AppIcons.SaveAlt,
            title = "本地缓存",
            subtitle = if (cacheEnabled) "已开启，会话和消息会缓存在本地" else "已关闭，退出后不保留本地数据",
            checked = cacheEnabled,
            onCheckedChange = { checked ->
                scope.launch {
                    settingsStorage.setCacheEnabled(checked)
                    if (!checked) {
                        LocalCache.currentAccountId()?.let { LocalCache.purgeAccount(it) }
                    }
                }
            }
        )
        if (legacyStoragePermission) {
            SettingsItem(
                icon = AppIcons.FolderZip,
                title = "存储权限",
                subtitle = when {
                    storagePermissionGranted -> "已允许，下载保存到 Download/Murexide"
                    else -> "未允许，点击申请存储权限"
                },
                onClick = ::requestStoragePermission
            )
        }
        SettingsSwitchItem(
            icon = AppIcons.LaptopChromebook,
            title = "大屏模式",
            subtitle = "在大屏幕下使用大屏模式",
            checked = bigScreen,
            onCheckedChange = { checked ->
                bigScreen = checked
                scope.launch {
                    settingsStorage.setBigScreen(checked)
                }
            }
        )
        SettingsSwitchItem(
            icon = AppIcons.Lock,
            title = "应用锁",
            subtitle = if (appLockEnabled) "已开启，启动或回到前台需验证密码" else "开启后启动应用需要输入密码",
            checked = appLockEnabled,
            onCheckedChange = { checked ->
                if (checked) {
                    showSetPinDialog = true
                } else {
                    showDisableLockConfirm = true
                }
            }
        )
        if (appLockEnabled) {
            SettingsSwitchItem(
                icon = AppIcons.Lock,
                title = "切到后台后重新锁定",
                subtitle = "关闭后仅在冷启动时验证密码",
                checked = lockOnBackground,
                onCheckedChange = { checked ->
                    scope.launch {
                        settingsStorage.setAppLockLockOnBackground(checked)
                        refreshAppLock()
                    }
                }
            )
            SettingsItem(
                icon = AppIcons.Edit,
                title = "修改应用锁密码",
                onClick = { showSetPinDialog = true }
            )
        }
        SettingsItem(
            icon = AppIcons.Screenshot,
            title = "截图设置",
            onClick = {
                val intent = Intent(context, SettingsActivity::class.java).apply {
                    putExtra("page", 6)
                }
                context.startActivity(intent)
            }
        )
    }

    if (showSetPinDialog) {
        SetAppLockPinDialog(
            onDismiss = { showSetPinDialog = false },
            onConfirm = { pin ->
                scope.launch {
                    settingsStorage.setAppLockPin(pin)
                    refreshAppLock()
                    showSetPinDialog = false
                }
            }
        )
    }

    if (showDisableLockConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableLockConfirm = false },
            title = { Text("关闭应用锁") },
            icon = { Icon(AppIcons.Info, contentDescription = null) },
            text = { Text("关闭后启动应用将不再需要密码。确定关闭吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDisableLockConfirm = false
                    scope.launch {
                        settingsStorage.clearAppLock()
                        refreshAppLock()
                    }
                }) {
                    Text("确认关闭")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableLockConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SetAppLockPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置应用锁密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(12)
                        error = null
                    },
                    label = { Text("新密码（至少 4 位数字）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        confirm = it.filter { c -> c.isDigit() }.take(12)
                        error = null
                    },
                    label = { Text("确认密码") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pin.length < 4) {
                    error = "密码至少 4 位"
                } else if (pin != confirm) {
                    error = "两次输入不一致"
                } else {
                    onConfirm(pin)
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}