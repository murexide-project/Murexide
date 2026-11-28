package com.juhao.murexide.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.EmptyPlaceholder
import com.juhao.murexide.ui.icons.AppIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PluginCallTracePage(
    modifier: Modifier = Modifier,
    pluginId: String,
    innerPadding: PaddingValues
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var traces by remember(pluginId) { mutableStateOf(emptyList<PluginCallTrace>()) }
    var busy by remember(pluginId) { mutableStateOf(false) }
    var error by remember(pluginId) { mutableStateOf<String?>(null) }
    var hasMore by remember(pluginId) { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    fun load(reset: Boolean) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val beforeId = if (reset) Long.MAX_VALUE else traces.lastOrNull()?.id ?: Long.MAX_VALUE
                val page = withContext(Dispatchers.IO) {
                    PluginCallTraceStorage.getInstance(context).read(pluginId, beforeId)
                }
                traces = if (reset) page else traces + page
                hasMore = page.size == 100
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "读取调用轨迹失败：${e.message ?: "未知错误"}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(pluginId) { load(true) }

    Column(modifier) {
        if (error != null) {
            EmptyPlaceholder(
                icon = AppIcons.Close,
                title = "出错了",
                subtitle = error,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (traces.isEmpty() && !busy && error == null) {
            EmptyPlaceholder(
                icon = AppIcons.History,
                title = "暂无调用轨迹",
                subtitle = "插件调用函数后会记录在这里",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "timeline_header") {
                    Row(
                        Modifier.animateItem().fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("调用时间线", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "最新调用在前 · 已加载 ${traces.size} 条",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalIconButton(onClick = { load(true) }, enabled = !busy) {
                            Icon(AppIcons.Refresh, contentDescription = "刷新调用轨迹")
                        }
                    }
                }

                traces.forEachIndexed { index, trace ->
                    val date = dateFormat.format(Date(trace.timestamp * 1000L))
                    val previousDate = traces.getOrNull(index - 1)?.let { dateFormat.format(Date(it.timestamp * 1000L)) }
                    if (date != previousDate) {
                        item(key = "date_${trace.id}") {
                            Row(
                                Modifier.animateItem().padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(AppIcons.CalendarToday, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item(key = "call_${trace.id}") {
                        PluginCallTraceRow(
                            function = trace.function,
                            time = timeFormat.format(Date(trace.timestamp * 1000L)),
                            isLast = index == traces.lastIndex && !hasMore,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                if (hasMore) {
                    item(key = "load_more") {
                        TextButton(
                            onClick = { load(false) },
                            enabled = !busy,
                            modifier = Modifier.animateItem().fillMaxWidth()
                        ) {
                            Icon(AppIcons.ExpandMore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("加载更早的调用")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginCallTraceRow(
    function: String,
    time: String,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val permission = tracePermission(function)
    val risk = permission?.let(PluginPermissions::risk) ?: 1
    val accent = when (risk) {
        3 -> MaterialTheme.colorScheme.error
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            Modifier.width(40.dp).heightIn(min = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(traceIcon(function), null, Modifier.size(19.dp), tint = accent)
                }
            }
            if (!isLast) {
                Box(
                    Modifier.width(2.dp).weight(1f).heightIn(min = 24.dp)
                        .padding(top = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f).padding(top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                function,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = accent
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(traceCategory(function), style = MaterialTheme.typography.labelSmall, color = accent)
                Text(time, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun tracePermission(function: String): String? = when (function) {
    "sendMessage", "recallMessage" -> PluginPermissions.USER_BOT
    "Http" -> PluginPermissions.NETWORK
    "readData", "writeData" -> PluginPermissions.STORAGE
    "getValue", "setValue" -> PluginPermissions.READ_SETTINGS
    "OpenChat" -> PluginPermissions.OPEN_CHAT
    "StartActivity" -> PluginPermissions.START_ACTIVITY
    "RestartApp" -> PluginPermissions.RESTART
    "OpenUrl" -> PluginPermissions.OPEN_URL
    "Copy" -> PluginPermissions.CLIPBOARD
    "Share" -> PluginPermissions.SHARE
    "Toast", "Alert", "Notify" -> PluginPermissions.NOTIFICATION
    "Vibrate" -> PluginPermissions.VIBRATE
    "Log" -> PluginPermissions.LOG
    else -> null
}

private fun traceCategory(function: String): String = when (function) {
    "sendMessage", "recallMessage" -> "消息操作"
    "Toast", "Alert", "Notify", "Copy", "Share" -> "交互反馈"
    "Http", "OpenUrl" -> "网络访问"
    "readData", "writeData", "getValue", "setValue" -> "数据与设置"
    "OpenChat", "StartActivity", "RestartApp" -> "应用控制"
    "Vibrate" -> "设备功能"
    "Log" -> "日志"
    "addFloatingView", "removeFloatingView" -> "界面"
    else -> "其他"
}

private fun traceIcon(function: String): ImageVector = when (function) {
    "sendMessage" -> AppIcons.Send
    "recallMessage" -> AppIcons.Undo
    "Toast", "Alert", "Notify" -> AppIcons.Notifications
    "Copy" -> AppIcons.ContentCopy
    "Share" -> AppIcons.Share
    "OpenUrl" -> AppIcons.Link
    "Http" -> AppIcons.Cloud
    "Log" -> AppIcons.Article
    "Vibrate" -> AppIcons.Phone
    "RestartApp" -> AppIcons.Refresh
    "OpenChat" -> AppIcons.ChatBubble
    "StartActivity" -> AppIcons.NavigateNext
    "readData", "writeData" -> AppIcons.Storage
    "getValue", "setValue" -> AppIcons.Tune
    "addFloatingView", "removeFloatingView" -> AppIcons.Layers
    else -> AppIcons.Code
}
