package com.juhao.murexide.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
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
    var expandedGroups by remember(pluginId) { mutableStateOf(emptySet<Long>()) }
    val groups = remember(traces) { groupConsecutiveCalls(traces) }

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
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                groups.forEachIndexed { index, group ->
                    val trace = group.first()
                    val expanded = trace.id in expandedGroups
                    val date = dateFormat.format(Date(trace.timestamp * 1000L))
                    val previousDate = groups.getOrNull(index - 1)?.first()?.let { dateFormat.format(Date(it.timestamp * 1000L)) }
                    if (date != previousDate) {
                        item(key = "date_${trace.id}") {
                            Row(
                                Modifier.animateItem().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(AppIcons.CalendarToday, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item(key = "group_${trace.id}") {
                        PluginCallTraceCard(
                            function = trace.function,
                            count = group.size,
                            singleTime = if (group.size == 1) timeFormat.format(Date(trace.timestamp * 1000L)) else null,
                            expanded = expanded,
                            onClick = {
                                if (group.size > 1) {
                                    expandedGroups = if (expanded) expandedGroups - trace.id else expandedGroups + trace.id
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                    if (expanded) {
                        group.forEach { call ->
                            item(key = "call_${call.id}") {
                                Row(
                                    Modifier.animateItem().fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        AppIcons.AccessTime, null, Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    SelectionContainer {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                call.function,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                timeFormat.format(Date(call.timestamp * 1000L)),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
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

private fun groupConsecutiveCalls(traces: List<PluginCallTrace>): List<List<PluginCallTrace>> {
    val groups = mutableListOf<MutableList<PluginCallTrace>>()
    traces.forEach { trace ->
        val previous = groups.lastOrNull()
        if (previous != null && previous.last().function == trace.function) {
            previous.add(trace)
        } else {
            groups.add(mutableListOf(trace))
        }
    }
    return groups
}

@Composable
private fun PluginCallTraceCard(
    function: String,
    count: Int,
    singleTime: String?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val subtitle = if (count > 1) "${count}次函数调用" else "函数调用"

    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.12f)) {
                Icon(
                    traceIcon(function), null,
                    Modifier.padding(10.dp).size(22.dp),
                    tint = accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    function,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (singleTime != null) {
                Spacer(Modifier.width(8.dp))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        AppIcons.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        singleTime,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (count > 1) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = if (expanded) "折叠详情" else "展开详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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