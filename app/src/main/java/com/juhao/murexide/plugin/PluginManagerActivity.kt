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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.MurexideTheme
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

private data class PendingInstall(val zip: File, val id: String, val name: String, val permissions: Set<String>)

@Composable
fun PluginManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manifests by PluginManager.manifests.collectAsState()
    var showTutorial by remember { mutableStateOf(false) }
    var logsPluginId by remember { mutableStateOf<String?>(null) }
    var logsText by remember { mutableStateOf("") }
    var pendingInstall by remember { mutableStateOf<PendingInstall?>(null) }

    fun doInstall(zip: File) {
        runCatching {
            PluginManager.install(context, zip).getOrThrow()
            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导入失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
        zip.delete()
    }

    fun parseInstallMeta(zip: File): PendingInstall? = runCatching {
        ZipFile(zip).use { z ->
            val entry = z.getEntry("manifest.json") ?: return@runCatching null
            val meta = z.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()) }
            val id = meta.optString("id")
            val name = meta.optString("name", id)
            val permsArr = meta.optJSONArray("permissions")
            val perms = if (permsArr == null) emptySet() else
                (0 until permsArr.length()).mapNotNull { permsArr.optString(it).ifBlank { null } }.toSet()
            PendingInstall(zip, id, name, perms)
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
            Toast.makeText(context, "读取文件失败: ${it.message}", Toast.LENGTH_LONG).show()
            tmp.delete()
            return@rememberLauncherForActivityResult
        }
        val meta = parseInstallMeta(tmp)
        if (meta == null) {
            Toast.makeText(context, "无效插件包", Toast.LENGTH_LONG).show()
            tmp.delete()
        } else {
            pendingInstall = meta
        }
    }

    pendingInstall?.let { pi ->
        AlertDialog(
            onDismissRequest = {
                pi.zip.delete()
                pendingInstall = null
            },
            title = { Text("安装 ${pi.name}") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("该插件将请求以下权限：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    if (pi.permissions.isEmpty()) {
                        Text(
                            "未请求任何权限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        pi.permissions.sorted().forEach { p ->
                            Text(
                                "· ${PluginPermissions.label(p)}",
                                style = MaterialTheme.typography.bodySmall
                            )
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

    if (showTutorial) {
        AlertDialog(
            onDismissRequest = { showTutorial = false },
            title = { Text("插件开发教程") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "插件是一个 zip，含 manifest.json 与 Lua 脚本：\n\n" +
                            "· PageRegister.lua —— registerPage(icon,title,page={...})\n" +
                            "· OnActivityEvent.lua —— onEvent(event=...,pages={...},onEvent={...})\n" +
                            "· onEvent.lua / Global.lua —— 应用级 onEvent\n\n" +
                            "宿主函数：Toast / Alert / Notify / Copy / OpenUrl / Http / writeData / readData / If\n\n" +
                            "页面组件：Button / Text / Column / Row / Spacer / Image / Card\n\n" +
                            "事件：onCreate/onResume/onPause/onDestroy、ws.connected/disconnected、ws.message 等。\n\n" +
                            "Lua 里可直接读取 Kt 注入的事件字段，例如 ws.message 的 type、chatId、content。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTutorial = false }) { Text("关闭") }
            }
        )
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
                    "v${manifest.version}${manifest.author?.let { " · $it" } ?: ""}",
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