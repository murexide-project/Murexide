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
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.MurexideTheme
import java.io.File

class PluginManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MurexideTheme {
                PluginManagerScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PluginManagerActivity::class.java))
        }
    }
}

private val TUTORIAL_MARKDOWN = """
# 插件开发教程

插件是一个 zip 文件。解压到私有目录后由宿主按 JSON 规则渲染 UI、执行动作。不需要写代码，只要写 JSON。

## 一、zip 目录结构

${"```"}
my-plugin.zip
├── manifest.json        必需
├── execute.json         可选，页面规则
├── onInstalled.json     可选，安装后执行
├── onUninstalled.json   可选，卸载后执行
├── function.json        可选，独立功能页入口
├── logs.txt             自动生成，插件日志
└── assets/              可选，图片等资源
${"```"}

manifest.json 必须在 zip 根目录，不要多套一层文件夹。

## 二、manifest.json

${"```"}json
{
  "id": "com.juhao.demo",
  "name": "演示插件",
  "version": 1,
  "description": "插件简介",
  "author": "juhao",
  "platform": "android",
  "minAppVersion": 1,
  "changelog": "首个版本",
  "icon": "assets/icon.png",
  "permissions": ["clipboard", "openUrl", "navigate", "network", "log", "storage"]
}
${"```"}

| 字段 | 说明 |
| --- | --- |
| id | 必填，插件唯一标识，反向域名 |
| name | 显示名 |
| version | 整数 |
| description | 简介 |
| author | 作者 |
| platform | 固定 android |
| minAppVersion | 最低 App versionCode |
| changelog | 更新日志 |
| icon | 图标路径 |
| permissions | 权限列表，见下 |

permissions 可选值：network / clipboard / notification / vibrate / readSettings / readAccount / openChat / navigate / restartApp / openUrl / storage / log / share / all

## 三、execute.json 页面规则

${"```"}json
{
  "rules": [
    {
      "page": "*",
      "event": "created",
      "action": "decorate",
      "position": "floating",
      "when": { "equals": ["{{network.online}}", "true"] },
      "content": {
        "type": "button",
        "value": "点我",
        "onClick": [
          { "action": "toast", "text": "hi" }
        ]
      }
    }
  ]
}
${"```"}

| 字段 | 说明 |
| --- | --- |
| page | Activity 全类名或 * |
| event | created/started/resumed/paused/stopped/destroyed，或应用级事件，见下 |
| action | decorate / replace |
| position | top/bottom/floating/left/right/center，或 positionData |
| when | 条件表达式，可选 |
| content | 内容对象 |

应用级事件：
app.foreground / app.background / network.online / network.offline /
ws.connected / ws.disconnected / ws.message / ws.messageRecall / ws.messageEdit / account.changed

## 四、content 类型

- text / button / dialog
- row / column / card / scroll / hscroll / list
- spacer / divider / image / icon
- switch / textField / slider / progress
- toast / action

通用布局属性：padding / margin / width / height / weight / background / radius。

## 五、动作（onClick / onChange / execute 均为数组）

${"```"}json
[
  { "action": "toast", "text": "hi" },
  { "action": "copy", "text": "内容" },
  { "action": "openUrl", "target": "https://example.com" },
  { "action": "startActivity", "target": "全类名", "extras": {"k": "v"}, "flags": ["NEW_TASK"] },
  { "action": "restartApp" },
  { "action": "log", "text": "内容" },
  { "action": "set", "key": "x", "value": "1", "persist": true },
  { "action": "delay", "duration": 1000 },
  { "action": "http", "method": "POST", "url": "https://...", "headers": {"Content-Type": "application/json"}, "body": "{}", "saveTo": "resp" },
  { "action": "notify", "notifyTitle": "标题", "text": "内容" },
  { "action": "vibrate", "duration": 100 },
  { "action": "share", "text": "分享内容", "mimeType": "text/plain" },
  { "action": "openApp", "scheme": "https://..." },
  { "action": "openChat", "chatId": "xxx", "chatType": 1, "title": "会话名" },
  { "action": "readSetting", "readKey": "themeMode", "saveTo": "theme" },
  { "action": "getAccount", "saveTo": "acc" }
]
${"```"}

条件动作：
${"```"}json
{
  "action": "if",
  "condition": { "equals": ["{{var.x}}", "1"] },
  "then": [{ "action": "toast", "text": "命中" }],
  "else": [{ "action": "toast", "text": "未命中" }]
}
${"```"}

## 六、条件 when

${"```"}json
{ "equals": ["a", "b"] }     { "notEquals": ["a", "b"] }
{ "contains": ["a", "b"] }   { "startsWith": ["a", "b"] }
{ "endsWith": ["a", "b"] }   { "matches": ["a", "正则"] }
{ "gt": ["1", "0"] }         { "gte": ["1", "0"] }
{ "lt": ["1", "0"] }         { "lte": ["1", "0"] }
{ "exists": "path" }         { "empty": "path" }
{ "and": [ {...}, {...} ] }  { "or": [ {...}, {...} ] }
{ "not": {...} }
${"```"}

## 七、变量（{{...}}）

- page.id / page.name / event
- plugin.id / plugin.dir
- account.id / account.username / account.avatar / account.loggedIn
- time.now / time.date / time.time / time.datetime
- app.version / app.versionCode / app.sdk / app.package
- network.online / ws.connected
- var.xxx（事件内变量）
- kv.xxx（持久化变量）
- extra.xxx（事件附加参数，例如 ws.message 时的 senderName/content）

## 八、存储

${"```"}json
{ "action": "set", "key": "count", "value": "1", "persist": true }
${"```"}

写入 plugins/{id}/data/storage.json，用 {{kv.count}} 读取。

## 九、日志

插件所有 warn / error / log 会写入 plugins/{id}/logs.txt，在插件管理页的"日志"按钮查看。

## 十、权限

无权限的动作会被拦截并写入日志。在 manifest.permissions 声明，例：["clipboard", "navigate", "network", "log"]。
""".trimIndent()

@Composable
fun PluginManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manifests by PluginManager.manifests.collectAsState()
    var showTutorial by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingInstall by remember { mutableStateOf<File?>(null) }
    var showReplaceWarning by remember { mutableStateOf(false) }
    var logsPluginId by remember { mutableStateOf<String?>(null) }
    var logsText by remember { mutableStateOf("") }

    fun doInstall(zip: File) {
        runCatching {
            PluginManager.install(context, zip).getOrThrow()
            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导入失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
        zip.delete()
    }

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

        if (PluginManager.scanZipForReplace(tmp)) {
            pendingInstall = tmp
            showReplaceWarning = true
        } else {
            doInstall(tmp)
        }
    }

    if (showReplaceWarning) {
        AlertDialog(
            onDismissRequest = {
                showReplaceWarning = false
                pendingInstall?.delete()
                pendingInstall = null
            },
            icon = {
                Icon(
                    AppIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    "安全提示",
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = { Text("插件可能修改页面、发起网络请求或打开外部链接，请确认插件安全后导入。") },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceWarning = false
                    pendingInstall?.let { doInstall(it) }
                    pendingInstall = null
                }) {
                    Text("确认导入", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReplaceWarning = false
                    pendingInstall?.delete()
                    pendingInstall = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    if (showTutorial) {
        AlertDialog(
            onDismissRequest = { showTutorial = false },
            title = { Text("插件开发教程") },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownText(
                        markdown = TUTORIAL_MARKDOWN,
                        enableTextSelection = true,
                        persistRenderState = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTutorial = false }) {
                    Text("关闭")
                }
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
                        .verticalScroll(rememberScrollState())
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
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, "返回")
            }
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(AppIcons.MoreVert, "更多")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("使用教程") },
                    onClick = {
                        menuExpanded = false
                        showTutorial = true
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("application/zip")) }
            ) {
                Icon(AppIcons.Add, "导入插件")
            }
        }
    ) { padding ->
        if (manifests.isEmpty()) {
            EmptyPlaceholder(
                icon = AppIcons.Info,
                title = "还没有插件",
                subtitle = "点右下角按钮导入 zip 扩展应用功能",
                modifier = Modifier.padding(padding),
                secondaryButton = {
                    OutlinedButton(onClick = { showTutorial = true }) {
                        Icon(AppIcons.Help, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("使用教程")
                    }
                }
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
                if (!manifest.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        manifest.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(4.dp))
                val storageSize = PluginStorage(context, manifest.id).size()
                Text(
                    "v${manifest.version} · ${manifest.rules.size} 规则 · ${manifest.functions.size} 功能 · 存储 ${formatSize(storageSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (manifest.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "权限: " + manifest.permissions.joinToString("、") { PluginPermissions.label(it) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!manifest.author.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "作者: ${manifest.author}",
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
                    TextButton(onClick = onShowLogs) {
                        Text("日志")
                    }
                    TextButton(onClick = {
                        PluginManager.uninstall(context, manifest.id)
                        Toast.makeText(
                            context,
                            "已卸载 ${manifest.name}",
                            Toast.LENGTH_SHORT
                        ).show()
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
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "%.1fKB".format(bytes / 1024.0)
    else -> "%.1fMB".format(bytes / 1024.0 / 1024.0)
}