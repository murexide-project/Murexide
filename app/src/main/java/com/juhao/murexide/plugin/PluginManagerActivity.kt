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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.MurexideTheme
import com.juhao.murexide.utils.getAppVersionInfo
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
    val minVersion: Int,
    val permissions: Set<String>
)

@Composable
fun PluginManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manifests by PluginManager.manifests.collectAsState()
    var showTutorial by remember { mutableStateOf(false) }
    var logsPluginId by remember { mutableStateOf<String?>(null) }
    var logsText by remember { mutableStateOf("") }
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
            val minVersion = meta.optInt("minVersion", 0)
            val permsArr = meta.optJSONArray("permissions")
            val perms = if (permsArr == null) emptySet() else
                (0 until permsArr.length()).mapNotNull { permsArr.optString(it).ifBlank { null } }.toSet()
            PendingInstall(zip, id, name, minVersion, perms)
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
                    if (pi.permissions.any { it == PluginPermissions.ALL }) {
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
                                    "该插件申请了全部权限，请确认来源可信后再安装。",
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

    logsPluginId?.let { pid ->
        AlertDialog(
            onDismissRequest = { logsPluginId = null },
            title = { Text("插件日志 · $pid") },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        logsText.ifBlank { "暂无日志" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    PluginLogger.clear(context, pid)
                    logsPluginId = null
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { logsPluginId = null }) { Text("关闭") }
            }
        )
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
                        rulesCount = PluginManager.rulesForPage("*", "*").count { it.first == m },
                        sizeBytes = PluginManager.pluginDirSize(context, m.id),
                        onShowLogs = {
                            logsPluginId = m.id
                            logsText = PluginLogger.read(context, m.id)
                        }
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
    rulesCount: Int,
    sizeBytes: Long,
    onShowLogs: () -> Unit
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
                Spacer(Modifier.height(4.dp))
                Text(
                    "v${manifest.version}${manifest.author?.let { " · $it" } ?: ""} · ${formatSize(sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                if (manifest.permissions.isEmpty()) {
                    Text(
                        "未请求任何权限",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "权限：${manifest.permissions.sorted().joinToString("、") { PluginPermissions.label(it) }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onShowLogs) { Text("日志") }
                    TextButton(onClick = {
                        PluginManager.uninstall(context, manifest.id)
                        Toast.makeText(context, "已卸载 ${manifest.name}", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("卸载", color = MaterialTheme.colorScheme.error)
                    }
                }
                StyledSwitch(
                    checked = manifest.enabled,
                    onCheckedChange = null
                )
            }
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
