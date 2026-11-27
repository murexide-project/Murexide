package com.juhao.murexide.plugin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.MurexideTheme
import com.juhao.murexide.utils.getAppVersionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

class PluginManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MurexideTheme {
                PluginManagerScreen(onBack = { finish() })
                PluginDialogHost()
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PluginManagerActivity::class.java))
        }
    }
}

private data class PendingInstall(
    val zip: File,
    val id: String,
    val name: String,
    val description: String?,
    val minVersion: Int,
    val permissions: Set<String>
)

@Composable
fun PluginManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manifests by PluginManager.manifests.collectAsState()
    var showTutorial by remember { mutableStateOf(false) }
    var managedPluginId by remember { mutableStateOf<String?>(null) }
    var pendingInstall by remember { mutableStateOf<PendingInstall?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }

    val appVersionCode = remember {
        context.getAppVersionInfo().versionCode
    }

    fun doInstall(zip: File) {
        runCatching {
            PluginManager.install(context, zip).getOrThrow()
            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
        }.onFailure {
            installError = it.message ?: "导入失败"
        }
        zip.delete()
    }

    fun parseInstallMeta(zip: File): PendingInstall? = runCatching {
        ZipFile(zip).use { z ->
            val entry = z.getEntry("manifest.json") ?: return@runCatching null
            val meta = z.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()) }
            val id = meta.optString("id")
            val name = meta.optString("name", id)
            val description = meta.optString("description").ifBlank { null }
            val minVersion = meta.optInt("minVersion", 0)
            val permsArr = meta.optJSONArray("permissions")
            val perms = if (permsArr == null) emptySet() else
                (0 until permsArr.length()).mapNotNull { permsArr.optString(it).ifBlank { null } }.toSet()
            PendingInstall(zip, id, name, description, minVersion, perms)
        }
    }.getOrNull()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val tmp = File(context.cacheDir, "plugin_import_${System.currentTimeMillis()}.zip")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure {
            installError = "读取文件失败: ${it.message}"
            tmp.delete()
            return@rememberLauncherForActivityResult
        }
        val meta = parseInstallMeta(tmp)
        if (meta == null) {
            installError = "无效插件包，zip 内缺少根目录 manifest.json"
            tmp.delete()
        } else if (meta.minVersion > 0 && appVersionCode < meta.minVersion.toLong()) {
            installError = "需要应用版本 ${meta.minVersion}，当前 $appVersionCode，无法安装"
            tmp.delete()
        } else {
            pendingInstall = meta
        }
    }


    installError?.let { message ->
        AlertDialog(
            onDismissRequest = { installError = null },
            icon = { Icon(AppIcons.Warning, contentDescription = null) },
            title = { Text("安装失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { installError = null }) { Text("知道了") }
            }
        )
    }

    pendingInstall?.let { pi ->
        AlertDialog(
            onDismissRequest = {
                pi.zip.delete()
                pendingInstall = null
            },
            icon = { Icon(AppIcons.Shield, contentDescription = null) },
            title = { Text("安装 ${pi.name}") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    pi.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (pi.minVersion > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    AppIcons.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "要求应用版本 ≥ ${pi.minVersion}（当前 $appVersionCode）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        "该插件由第三方提供，安装后将获得以下权限：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    if (pi.permissions.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "未请求任何权限",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val ordered = pi.permissions
                            .sortedWith(
                                compareByDescending<String> { PluginPermissions.risk(it) }
                                    .thenBy { PluginPermissions.label(it) }
                            )
                        ordered.forEach { p ->
                            PermissionRow(permission = p)
                        }
                    }
                    if (pi.permissions.any { PluginPermissions.risk(it) == 3 }) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    AppIcons.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (PluginPermissions.ALL in pi.permissions) {
                                        "该插件申请了全部权限，包含以当前账号发送与撤回消息的能力，请确认来源可信后再安装。"
                                    } else {
                                        "该插件申请了用户 Bot 高危权限，可使用当前账号发送与撤回消息，请确认来源可信后再安装。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    doInstall(pi.zip)
                    pendingInstall = null
                }) { Text("安装") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pi.zip.delete()
                    pendingInstall = null
                }) { Text("取消") }
            }
        )
    }

    LaunchedEffect(showTutorial) {
        if (showTutorial) {
            PluginDocsActivity.start(context)
            showTutorial = false
        }
    }

    manifests.firstOrNull { it.id == managedPluginId }?.let { manifest ->
        key(manifest.id) {
            PluginManagementSheet(
                manifest = manifest,
                onDismiss = { managedPluginId = null }
            )
        }
    }

    StyledScaffold(
        title = { Text("插件管理") },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, "返回") }
        },
        actions = {
            IconButton(onClick = { showTutorial = true }) { Icon(AppIcons.Help, "教程") }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
                Icon(AppIcons.Add, "导入插件")
            }
        }
    ) { padding ->
        if (manifests.isEmpty()) {
            EmptyPlaceholder(
                icon = AppIcons.Info,
                title = "还没有插件",
                subtitle = "点右下角按钮导入 zip 扩展应用功能",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(manifests, key = { it.id }) { m ->
                    PluginCard(
                        manifest = m,
                        context = context,
                        sizeBytes = PluginManager.pluginDirSize(context, m.id),
                        onManage = { managedPluginId = m.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    manifest: PluginManifest,
    context: Context,
    sizeBytes: Long,
    onManage: () -> Unit
) {
    Surface(
        onClick = {
            PluginManager.setEnabled(context, manifest.id, !manifest.enabled)
            Toast.makeText(
                context,
                if (!manifest.enabled) "已启用 ${manifest.name}" else "已停用 ${manifest.name}",
                Toast.LENGTH_SHORT
            ).show()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .alpha(if (manifest.enabled) 1f else 0.6f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(manifest.name, style = MaterialTheme.typography.titleMedium)
                manifest.description?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "v${manifest.version}${manifest.author?.let { " · $it" } ?: ""} · ${formatSize(sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onManage) {
                    Icon(AppIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("管理")
                }
                StyledSwitch(
                    checked = manifest.enabled,
                    onCheckedChange = null
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginManagementSheet(
    manifest: PluginManifest,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var logsText by remember { mutableStateOf<String?>(null) }
    var logsBusy by remember { mutableStateOf(false) }
    var logsError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val sizeText by produceState("读取中", manifest.id, logsText) {
        value = withContext(Dispatchers.IO) {
            runCatching { formatSize(PluginManager.pluginDirSize(context, manifest.id)) }
                .getOrDefault("读取失败")
        }
    }

    fun refreshLogs(clear: Boolean = false) {
        if (logsBusy) return
        logsBusy = true
        logsError = null
        scope.launch {
            try {
                logsText = withContext(Dispatchers.IO) {
                    if (clear) PluginLogger.clear(context, manifest.id)
                    PluginLogger.read(context, manifest.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logsError = "${if (clear) "清空" else "读取"}日志失败：${e.message ?: "未知错误"}"
            } finally {
                logsBusy = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) refreshLogs()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
        ) {
            Text("插件详情", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(manifest.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                listOf("详情", "所需权限", "日志").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            key(selectedTab) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            item { PluginDetailRow("插件 ID", manifest.id) }
                            item { PluginDetailRow("版本", "v${manifest.version}") }
                            item { PluginDetailRow("作者", manifest.author?.takeIf { it.isNotBlank() } ?: "未提供") }
                            item { PluginDetailRow("状态", if (manifest.enabled) "已启用" else "已停用") }
                            item { PluginDetailRow("占用空间", sizeText) }
                            item {
                                PluginDetailRow(
                                    "最低应用版本",
                                    if (manifest.minVersion > 0) manifest.minVersion.toString() else "未限制"
                                )
                            }
                            item { PluginDetailRow("描述", manifest.description?.takeIf { it.isNotBlank() } ?: "暂无描述") }
                        }
                        1 -> {
                            if (manifest.permissions.isEmpty()) {
                                item {
                                    Text(
                                        "未请求任何权限",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(
                                    manifest.permissions.sortedWith(
                                        compareByDescending<String> { PluginPermissions.risk(it) }
                                            .thenBy { PluginPermissions.label(it) }
                                    ),
                                    key = { it }
                                ) { permission ->
                                    PermissionRow(permission)
                                }
                            }
                        }
                        2 -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { refreshLogs() }, enabled = !logsBusy) {
                                        Text("刷新")
                                    }
                                    TextButton(
                                        onClick = { refreshLogs(clear = true) },
                                        enabled = !logsBusy && !logsText.isNullOrEmpty()
                                    ) { Text("清空") }
                                }
                            }
                            if (logsBusy) {
                                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            }
                            logsError?.let { message ->
                                item { Text(message, color = MaterialTheme.colorScheme.error) }
                            }
                            item {
                                SelectionContainer {
                                    Text(
                                        logsText?.ifBlank { "暂无日志" }
                                            ?: if (logsBusy) "正在读取日志…" else "暂无日志",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            TextButton(
                onClick = {
                    deleteError = null
                    showDeleteConfirm = true
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("删除插件")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(AppIcons.Delete, contentDescription = null) },
            title = { Text("删除 ${manifest.name}？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将停用并删除此插件及其本地数据和日志，此操作无法撤销。")
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            PluginManager.uninstall(context, manifest.id)
                            check(PluginManager.manifestById(manifest.id) == null) {
                                "插件未能完整删除，请重试"
                            }
                        }.onSuccess {
                            showDeleteConfirm = false
                            onDismiss()
                            Toast.makeText(context, "已删除 ${manifest.name}", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            deleteError = it.message ?: "删除失败，请重试"
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PluginDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun PermissionRow(permission: String) {
    val riskLevel = PluginPermissions.risk(permission)
    val accent = when (riskLevel) {
        3 -> MaterialTheme.colorScheme.error
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = accent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PluginPermissions.icon(permission),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = PluginPermissions.label(permission),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = PluginPermissions.description(permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
