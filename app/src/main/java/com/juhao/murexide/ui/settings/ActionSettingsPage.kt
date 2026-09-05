package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.UiState
import kotlinx.coroutines.launch
import com.juhao.murexide.utils.hasLegacyWritePermission
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.LifecycleResumeEffect


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
    
    var avatarFollow by remember { mutableStateOf(false) }
    var bigScreen by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        avatarFollow = settingsStorage.getAvatarFollow()
        bigScreen = settingsStorage.getBigScreen()
    }
    
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

    SettingsGroup() {
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
        SettingsItem(
            icon = AppIcons.Screenshot,
            title = "截图设置",
            subtitle = "隐藏信息等",
            onClick = {
                val intent = Intent(context, SettingsActivity::class.java).apply{
                    putExtra("page", 6)
                }
                context.startActivity(intent)
            }
        )
        SettingsSwitchItem(
            icon = AppIcons.Animation,
            title = "聊天页头像跟随",
            subtitle = "头像跟随视角移动",
            checked = avatarFollow,
            onCheckedChange = { checked ->
                avatarFollow = checked
                scope.launch {
                    settingsStorage.setAvatarFollow(checked)
                }
            }
        )
    }
}