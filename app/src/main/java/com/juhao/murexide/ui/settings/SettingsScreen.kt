package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.ClipData
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.juhao.murexide.utils.UpdateInfo
import com.juhao.murexide.utils.checkForUpdateWithDetails
import com.juhao.murexide.utils.getAppVersionInfo
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.SettingsStorage
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.juhao.murexide.ui.about.AboutActivity
import com.juhao.murexide.ui.settings.appearance.AppearanceActivity
import com.juhao.murexide.ui.settings.switchAccount.SwitchAccountActivity
import com.juhao.murexide.utils.hasLegacyWritePermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
        
    val context = LocalContext.current
    val updateEnabled = context.getAppVersionInfo().commitHash != "dev"
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()
        
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showGetTokenDialog by remember { mutableStateOf(false) }

    var avatarFollow by remember { mutableStateOf(false) }
    var bigScreen by remember { mutableStateOf(true) }
    var updateChannel by remember { mutableStateOf("stable") }
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

    LifecycleResumeEffect(legacyStoragePermission) {
        storagePermissionGranted = !legacyStoragePermission || hasLegacyWritePermission(context)
        onPauseOrDispose { }
    }

    fun openApplicationSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        })
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

    LaunchedEffect(Unit) {
        avatarFollow = settingsStorage.getAvatarFollow()
        bigScreen = settingsStorage.getBigScreen()
        updateChannel = settingsStorage.getUpdateChannel()
    }
    
    val clipboardManager = LocalClipboard.current
    val accountStorage = remember(context.applicationContext) {
        AccountStorage.getInstance(context.applicationContext)
    }
    
    if (showGetTokenDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                AutoMirroredIcon(AppIcons.Info, contentDescription = null)
            },
            title = { Text("提取当前 Token") },
            text = { Text("点击确定按钮复制 Token，请注意不要泄露你的 Token，泄露自行负责！", color = MaterialTheme.colorScheme.error) },
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

    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            currentVersion = context.getAppVersionInfo().versionName,
            onDismiss = { showUpdateDialog = false },
            onConfirm = {
                val intent = Intent(Intent.ACTION_VIEW, updateInfo?.releaseUrl?.toUri())
                context.startActivity(intent)
                showUpdateDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            StyledTopBar(
                title = { Text("设置") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    StyledIconButton(onClick = onBack) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            SettingsGroup(title = "外观") {
                SettingsItem(
                    icon = AppIcons.Draw,
                    title = "外观设置",
                    subtitle = "主题、头像、会话等",
                    onClick = {
                        val intent = Intent(context, AppearanceActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }
            
            SettingsGroup(title = "行为") {
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
                        val intent = Intent(context, ScreenshotSettingsActivity::class.java)
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
            
            SettingsGroup(title = "更新") {
                SettingsItem(
                    icon = AppIcons.Update,
                    title = "检查更新",
                    isEnabled = updateEnabled,
                    subtitle = if (updateEnabled)
                        "访问仓库获取最新版本"
                    else
                        "Dev版本无法检查更新",
                    onClick = {
                        scope.launch {
                            val includePreRelease = updateChannel == "preRelease"
                            
                            val info = checkForUpdateWithDetails(
                                context = context,
                                includePreRelease = includePreRelease
                            )
                            if (info != null) {
                                updateInfo = info
                                showUpdateDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    "已是最新版本",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
                SettingsDropdownItem(
                    icon = AppIcons.List,
                    title = "更新频道",
                    subtitle = if (updateChannel == "stable")
                        "仅检查正式版本"
                    else
                        "检查预发布版本",
                    options = listOf(
                        "stable" to "仅正式版",
                        "preRelease" to "正式版 + 预发布版"
                    ),
                    selectedValue = updateChannel,
                    onOptionSelected = { selected ->
                        updateChannel = selected
                        scope.launch {
                            settingsStorage.setUpdateChannel(selected)
                        }
                    }
                )
            }

            SettingsGroup(title = "关于") {
                SettingsItem(
                    icon = AppIcons.Info,
                    title = "关于",
                    subtitle = "版本号、开发者信息",
                    onClick = {
                        val intent = Intent(context, AboutActivity::class.java)
                        context.startActivity(intent)
                    }
                )
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
                    title = "提取当前 Token",
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isPreRelease = updateInfo.isPreRelease
    val versionType = if (isPreRelease) "预发布版" else "正式版"
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPreRelease) {
                    "发现新预发布版"
                } else {
                    "发现新正式版"
                }
            )
        },
        text = {
            Column {
                Text(
                    text = "$currentVersion  →  ${updateInfo.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "版本类型：$versionType",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("前往下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}
