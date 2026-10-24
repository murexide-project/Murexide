package com.juhao.murexide.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.icons.AppIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebSocketPage() {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()
    val wsManager = remember { WebSocketManager.getInstance() }

    val connected by wsManager.connectionState.collectAsState()

    var wsEnabled by remember { mutableStateOf(true) }

    var batteryOptimizationIgnored by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    LaunchedEffect(Unit) {
        wsEnabled = settingsStorage.getWsEnabled()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val (cardBgColor, cardTextColor) = if (connected)
        Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    else
        Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)

    SettingsGroup {
        Surface(
            color = cardBgColor,
            contentColor = cardTextColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = MaterialShapes.Pill.toShape(),
                    color = if (connected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.tertiary
                ) {
                    Icon(
                        imageVector = if (connected) AppIcons.Check else AppIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.requiredSize(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (connected) "连接正常" else "连接已断开",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    SettingsGroup {
        SettingsSwitchItem(
            icon = AppIcons.Link,
            title = "启用 WebSocket",
            subtitle = "关闭后将不再连接，重启应用生效",
            checked = wsEnabled,
            onCheckedChange = { enabled ->
                wsEnabled = enabled
                scope.launch {
                    settingsStorage.setWsEnabled(enabled)
                }
            }
        )

        SettingsSwitchItem(
            icon = AppIcons.BatteryPlus,
            title = "忽略电池优化",
            subtitle = "忽略电池优化以减少后台被杀概率",
            checked = batteryOptimizationIgnored,
            isEnabled = !batteryOptimizationIgnored,
            onCheckedChange = { enabled ->
                requestIgnoreBatteryOptimizations(context, enabled)
            }
        )

        SettingsItem(
            icon = AppIcons.Refresh,
            title = "重启连接",
            isEnabled = wsEnabled,
            subtitle = "立即断开并重新建立 WebSocket 连接",
            onClick = { wsManager.manualReconnect() }
        )
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context, enable: Boolean) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (enable) {
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            runCatching {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
    } else {
        runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        }
    }
}