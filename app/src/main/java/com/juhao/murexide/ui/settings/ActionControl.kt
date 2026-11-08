package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.juhao.murexide.MyApplication
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.utils.hasLegacyWritePermission
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionControl(
    highlightId: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
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
            scope.launch { settingsStorage.setNotificationEnabled(checked) }
        }
    }

    var bigScreen by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { bigScreen = settingsStorage.getBigScreen() }

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

    val allIds = remember(legacyStoragePermission, appLockEnabled) {
        buildList {
            add("header_action")
            add("action_notification")
            add("action_cache")
            if (legacyStoragePermission) add("action_storage")
            add("action_big_screen")
            add("action_app_lock")
            if (appLockEnabled) {
                add("action_lock_background")
                add("action_change_pin")
            }
            add("action_screenshot")
        }
    }

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
        item { SettingsGroupHeader("行为") }

        var index = 0
        item {
            SettingsGroupSurface(index = index++, count = allIds.count { !it.startsWith("header_") }, id = "action_notification", highlightId = highlightId) {
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
            }
        }
        item {
            SettingsGroupSurface(index = index++, count = allIds.count { !it.startsWith("header_") }, id = "action_cache", highlightId = highlightId) {
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
            }
        }
        if (legacyStoragePermission) {
            item {
                SettingsGroupSurface(index = index++, count = allIds.count { !it.startsWith("header_") }, id = "action_storage", highlightId = highlightId) {
                    SettingsItem(
                        icon = AppIcons.FolderZip,
                        title = "存储权限",
                        subtitle = if (storagePermissionGranted) "已允许，下载保存到 Download/Murexide"
                        else "未允许，点击申请存储权限",
                        onClick = ::requestStoragePermission
                    )
                }
            }
        }
        item {
            SettingsGroupSurface(index = index++, count = allIds.count { !it.startsWith("header_") }, id = "action_big_screen", highlightId = highlightId) {
                SettingsSwitchItem(
                    icon = AppIcons.LaptopChromebook,
                    title = "大屏模式",
                    subtitle = "在大屏幕下使用大屏模式",
                    checked = bigScreen,
                    onCheckedChange = { checked ->
                        bigScreen = checked
                        scope.launch { settingsStorage.setBigScreen(checked) }
                    }
                )
            }
        }
        item {
            SettingsGroupSurface(index = index++, count = allIds.count { !it.startsWith("header_") }, id = "action_app_lock", highlightId = highlightId) {
                SettingsSwitchItem(
                    icon = AppIcons.Lock,
                    title = "应用锁",
                    subtitle = if (appLockEnabled) "已开启，启动或回到前台需验证密码" else "开启后启动应用需要输入密码",
                    checked = appLockEnabled,
                    onCheckedChange = { checked ->
                        if (checked) showSetPinDialog = true else showDisableLockConfirm = true
                    }
                )
            }
        }
        if (appLockEnabled) {
            item {
                SettingsGroupSurface(index = index++, count = allIds.count { !it.startsWith("header_") }, id = "action_lock_background", highlightId = highlightId) {
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
                }
            }
            item {
                SettingsGroupSurface(index = index++, count = allIds.count { !it.startsWith("header_") }, id = "action_change_pin", highlightId = highlightId) {
                    SettingsItem(
                        icon = AppIcons.Edit,
                        title = "修改应用锁密码",
                        onClick = { showSetPinDialog = true }
                    )
                }
            }
        }
        item {
            SettingsGroupSurface(index = index++, count = allIds.count { !it.startsWith("header_") }, id = "action_screenshot", highlightId = highlightId) {
                SettingsItem(
                    icon = AppIcons.Screenshot,
                    title = "截图设置",
                    onClick = {
                        context.startActivity(
                            Intent(context, SettingsActivity::class.java).putExtra("page", 6)
                        )
                    }
                )
            }
        }
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
                }) { Text("确认关闭") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableLockConfirm = false }) { Text("取消") }
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
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(12); error = null },
                    label = { Text("新密码（至少 4 位数字）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(12); error = null },
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
                if (pin.length < 4) error = "密码至少 4 位"
                else if (pin != confirm) error = "两次输入不一致"
                else onConfirm(pin)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}