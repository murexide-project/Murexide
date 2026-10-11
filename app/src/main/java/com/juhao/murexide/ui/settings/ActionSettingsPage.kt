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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    val hideLauncher by settingsStorage.hideLauncherFlow.collectAsState(initial = false)

    var showHideLauncherConfirm by remember { mutableStateOf(false) }

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
            icon = AppIcons.VisibilityOff,
            title = "隐藏应用入口",
            subtitle = if (hideLauncher) "已隐藏，拨号 *#*#2468#*#* 启动" else "显示在桌面启动器中",
            checked = hideLauncher,
            onCheckedChange = { checked ->
                if (checked) {
                    showHideLauncherConfirm = true
                } else {
                    scope.launch {
                        settingsStorage.setHideLauncher(false)
                    }
                }
            }
        )
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

    if (showHideLauncherConfirm) {
        AlertDialog(
            onDismissRequest = { showHideLauncherConfirm = false },
            title = { Text("隐藏应用入口") },
            icon = {
                Icon(AppIcons.Info, contentDescription = null)
            },
            text = {
                Text("隐藏后应用图标将从桌面消失，只能通过拨号盘输入暗码 *#*#2468#*#* 启动应用。\n\n请确认你知道暗码并能正常通过暗码启动。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showHideLauncherConfirm = false
                    scope.launch {
                        settingsStorage.setHideLauncher(true)
                    }
                }) {
                    Text("确认开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideLauncherConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}