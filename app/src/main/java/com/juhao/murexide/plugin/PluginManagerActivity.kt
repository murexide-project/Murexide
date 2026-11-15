package com.juhao.murexide.plugin

import android.content.ClipData
import android.content.ClipboardManager
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

插件是一个 zip 文件。解压到私有目录后由宿主按 JSON 规则渲染 UI、执行动作、处理应用事件。不需要写代码，只要写 JSON。

## 一、zip 目录结构

${"```"}
my-plugin.zip
├── manifest.json        必需，插件元信息
├── execute.json         可选，页面规则
├── onInstalled.json     可选，安装后执行
├── onUninstalled.json   可选，卸载后执行
├── function.json        可选，独立功能页入口
├── logs.txt             自动生成，插件日志
├── data/                自动生成，插件存储
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

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| id | 是 | 插件唯一标识，反向域名，卸载按它删目录 |
| name | 否 | 显示名，不填用 id |
| version | 否 | 整数，默认 1 |
| description | 否 | 简介，显示在管理页卡片上 |
| author | 否 | 作者名，展示用 |
| platform | 否 | 固定 android |
| minAppVersion | 否 | 最低 App versionCode，低于此版本拒绝安装 |
| changelog | 否 | 更新日志 |
| icon | 否 | 图标路径，如 assets/icon.png |
| permissions | 否 | 权限列表，见第十节 |

## 三、execute.json 页面规则

${"```"}json
{
  "rules": [
    {
      "page": "*",
      "event": "created",
      "action": "decorate",
      "position": "bottom",
      "positionData": {
        "anchor": "bottomCenter",
        "offsetX": 0,
        "offsetY": 0,
        "padding": { "horizontal": 16, "vertical": 8 }
      },
      "when": { "equals": ["{{ws.connected}}", "false"] },
      "content": {
        "type": "text",
        "value": "WebSocket 已断开",
        "color": "#FFFFFF",
        "fontSize": 16,
        "background": "#D32F2F",
        "radius": 0,
        "width": "match",
        "align": "center"
      }
    }
  ]
}
${"```"}

| 字段 | 说明 |
| --- | --- |
| page | Activity 全类名，或 * 表示所有页面 |
| event | created/started/resumed/paused/stopped/destroyed，或应用级事件，见下 |
| action | decorate（叠加）/ replace（整页替换），默认 decorate |
| position | top/bottom/floating/left/right/center，或 centerStart/centerEnd/topStart/topEnd/bottomStart/bottomEnd |
| positionData | 精确定位，含 anchor / offsetX / offsetY / padding，优先于 position |
| when | 条件表达式，可选，不写恒成立 |
| content | 内容对象，见第四节 |

应用级事件：

${"```"}
app.foreground     App 回到前台
app.background     App 进入后台（需手动 emit）
network.online     网络恢复
network.offline    网络断开
ws.connected       WebSocket 连上
ws.disconnected    WebSocket 断开
ws.message         收到新消息（extra 里有 msg / chatId / senderId / senderName / content / contentType / timestamp）
ws.messageRecall   消息被撤回（extra 里有 msgId / chatId / senderId / senderName）
ws.messageEdit     消息被编辑（extra 里有 msgId / chatId / content）
account.changed    账号切换（extra 里有 accountId / username / avatar）
${"```"}

### when 的求值时机

- `created` 事件：**每次重组求值一次**。`ws.connected` / `network.online` 变化时浮窗会实时显隐。
- `ws.disconnected` / `ws.connected` / `network.*` 事件：**事件触发时求值一次**。触发后 `when` 不再重新求值，浮窗状态由 `PluginSingleOverlay` 内部自己订阅状态。

## 四、content 类型

### text 文本

${"```"}json
{
  "type": "text",
  "value": "这是一行文本 {{account.username}}",
  "color": "#2196F3",
  "fontSize": 16,
  "background": "#F5F5F5",
  "radius": 8,
  "width": "match",
  "align": "center"
}
${"```"}

`fontSize` 单位 sp，默认 14。`background` 为背景色，`radius` 圆角 dp，`width: "match"` 全宽，`align` 可选 center / end。

### button 按钮

${"```"}json
{
  "type": "button",
  "value": "点我",
  "color": "#2196F3",
  "fontSize": 14,
  "onClick": [
    { "action": "toast", "text": "你点了我" }
  ]
}
${"```"}

没有 onClick 时点击弹 AlertDialog。

### dialog 弹窗按钮

${"```"}json
{
  "type": "dialog",
  "value": "打开弹窗",
  "title": "标题",
  "message": "正文",
  "confirmText": "确定",
  "cancelText": "取消"
}
${"```"}

### row / column / card / scroll / hscroll / list

${"```"}json
{
  "type": "column",
  "align": "center",
  "padding": { "vertical": 8 },
  "children": [
    { "type": "text", "value": "第一行" },
    { "type": "button", "value": "按钮" }
  ]
}
${"```"}

- `row` 水平排列，`column` 垂直排列，`card` 带圆角背景，`scroll` 纵向滚动，`hscroll` 横向滚动，`list` 同 column 但用 items 数据源
- `align` 控制 column 子项水平对齐：start / center / end
- `scroll` 不能嵌套 `scroll`，否则崩

### spacer / divider

${"```"}json
{ "type": "spacer", "size": "8" }
{ "type": "divider" }
${"```"}

### image 图片

${"```"}json
{
  "type": "image",
  "src": "assets/logo.png",
  "size": "48",
  "radius": 24
}
${"```"}

`src` 支持三种：
- `assets/xxx.png`：插件 zip 里 assets/ 目录
- `file:///sdcard/...`：本地绝对路径
- `https://...`：网络图片，走 Coil

`size` 单位 dp，默认 96。`radius` 圆角 dp。

### icon 图标

${"```"}json
{
  "type": "icon",
  "icon": "Info",
  "size": "24",
  "color": "#2196F3"
}
${"```"}

`icon` 可选：Info / SmartToy / Person / Link / Image / Settings / Code / Warning / People / Build。

### switch 开关

${"```"}json
{
  "type": "switch",
  "title": "启用",
  "value": true,
  "bind": "enabled",
  "onChange": [
    { "action": "toast", "text": "现在 {{var.enabled}}" }
  ]
}
${"```"}

### textField 输入框

${"```"}json
{
  "type": "textField",
  "value": "默认内容",
  "hint": "请输入",
  "bind": "input",
  "onChange": [
    { "action": "toast", "text": "{{var.input}}" }
  ]
}
${"```"}

### slider 滑块

${"```"}json
{
  "type": "slider",
  "value": 50,
  "min": 0,
  "max": 100,
  "step": 10,
  "bind": "level",
  "onChange": [
    { "action": "toast", "text": "{{var.level}}" }
  ]
}
${"```"}

### progress 进度条

${"```"}json
{ "type": "progress", "value": 0.6 }
{ "type": "progress", "indeterminate": true }
${"```"}

### toast / action

`toast` 只在生命周期事件里弹一次；`action` 不渲染 UI，只执行 execute 动作。

${"```"}json
{ "type": "toast", "value": "页面已启动", "duration": "short" }
{ "type": "action", "execute": [{ "action": "log", "text": "页面创建" }] }
${"```"}

### 通用布局字段

所有 content 都支持：

| 字段 | 说明 |
| --- | --- |
| padding | 内边距，{ top / bottom / start / end / horizontal / vertical / all } |
| margin | 外边距，同上 |
| width | "match" 或 "20dp" |
| height | "match" 或 "20dp" |
| weight | 在 row / column 里占的权重 |
| background | 背景色，如 #FF5722 |
| radius | 圆角 dp |
| align | 对齐方式 |

## 五、动作

`onClick` / `onChange` / `execute` 都可以是单个对象或数组。

### 基础动作

${"```"}json
[
  { "action": "toast", "text": "hi" },
  { "action": "copy", "text": "内容" },
  { "action": "openUrl", "target": "https://example.com" },
  { "action": "startActivity", "target": "全类名", "extras": {"k": "v"}, "flags": ["NEW_TASK"] },
  { "action": "navigate", "target": "全类名" },
  { "action": "restartApp" },
  { "action": "log", "text": "内容" },
  { "action": "set", "key": "x", "value": "1", "persist": true },
  { "action": "delay", "duration": 1000 },
  { "action": "notify", "notifyTitle": "标题", "text": "内容" },
  { "action": "vibrate", "duration": 100 },
  { "action": "share", "text": "分享内容", "mimeType": "text/plain" },
  { "action": "openApp", "scheme": "https://..." },
  { "action": "openChat", "chatId": "xxx", "chatType": 1, "title": "会话名" },
  { "action": "readSetting", "readKey": "themeMode", "saveTo": "theme" },
  { "action": "getAccount", "saveTo": "acc" }
]
${"```"}

### http 请求

${"```"}json
{
  "action": "http",
  "method": "POST",
  "url": "https://example.com/api",
  "headers": { "Content-Type": "application/json" },
  "body": "{\"key\":\"{{kv.value}}\"}",
  "saveTo": "resp"
}
${"```"}

响应文本存到 `{{var.resp}}`。

### if 条件动作

${"```"}json
{
  "action": "if",
  "condition": { "equals": ["{{ws.connected}}", "false"] },
  "then": [{ "action": "toast", "text": "WS 断开" }],
  "else": [{ "action": "toast", "text": "WS 正常" }]
}
${"```"}

### readSetting 支持的 readKey

themeMode / themeColor / notificationEnabled / squareAvatar / bigScreen / updateChannel / showSticky / mergeSticky / floatBottomBar / floatingTopBar / showMsgTags / bubbleCornerRadius / bubbleOpacity / showBackground / backgroundOpacity / showMyBubbleAvatar / liquidGlassEnabled / liquidGlassBlur / screenshotHideSenderInfo / screenshotHideMyInfo / screenshotHideSessionInfo / screenshotHideImages / cacheEnabled / wsEnabled / userBotEnabled / appLockEnabled / appLockLockOnBackground / enterKeyToSend

## 六、when 条件

${"```"}json
{ "equals": ["a", "b"] }
{ "notEquals": ["a", "b"] }
{ "contains": ["a", "b"] }
{ "startsWith": ["a", "b"] }
{ "endsWith": ["a", "b"] }
{ "matches": ["a", "正则"] }
{ "gt": ["1", "0"] }
{ "gte": ["1", "0"] }
{ "lt": ["1", "0"] }
{ "lte": ["1", "0"] }
{ "exists": "path" }
{ "empty": "path" }
{ "and": [ {...}, {...} ] }
{ "or": [ {...}, {...} ] }
{ "not": {...} }
${"```"}

比较前两侧都会做 {{...}} 插值。

## 七、变量

| 变量 | 说明 |
| --- | --- |
| page.id / page.name | 当前 Activity 全类名 / 短名 |
| event | 当前事件 |
| plugin.id / plugin.dir | 插件 id / 插件目录绝对路径 |
| plugin.version / plugin.name / plugin.author / plugin.description | 插件版本 / 名称 / 作者 / 简介 |
| account.id / account.username / account.avatar / account.loggedIn | 当前账号信息 |
| time.now / time.date / time.time / time.datetime | 当前时间戳 / 日期 / 时分秒 / 日期时间 |
| app.version / app.versionCode / app.sdk / app.package | App 版本名 / versionCode / SDK / 包名 |
| network.online | 网络是否在线 |
| ws.connected | WebSocket 是否连接 |
| var.xxx | 事件内变量，set 动作写入 |
| kv.xxx | 持久化变量，set 动作 persist:true 写入 |
| extra.xxx | 事件附加参数，如 ws.message 时 extra.senderName |

## 八、存储

${"```"}json
{ "action": "set", "key": "count", "value": "1", "persist": true }
${"```"}

写入 plugins/{id}/data/storage.json，用 {{kv.count}} 读取。

## 九、日志

插件所有 warn / error / log 会写入 plugins/{id}/logs.txt，在插件管理页的"日志"按钮查看。

## 十、权限

**没在 manifest.permissions 声明的权限，对应动作会被静默拦截并写日志，不会崩溃。**

| 权限 | 影响动作 |
| --- | --- |
| network | http |
| clipboard | copy |
| notification | notify |
| vibrate | vibrate |
| readSettings | readSetting |
| readAccount | getAccount |
| openChat | openChat |
| navigate | startActivity / navigate |
| restartApp | restartApp |
| openUrl | openUrl / openApp |
| storage | set |
| log | log |
| share | share |
| all | 以上全部 |

## 十一、function.json 独立功能页

${"```"}json
{
  "entries": [
    {
      "id": "my_page",
      "title": "我的页面",
      "subtitle": "页面描述",
      "icon": "Info",
      "content": {
        "type": "scroll",
        "children": [
          { "type": "text", "value": "{{plugin.name}} v{{plugin.version}}" }
        ]
      }
    }
  ]
}
${"```"}

entry 会出现在「设置 → 额外功能」里，点击打开 PluginFunctionActivity。

**注意**：`function.json` 的 content 不支持 `when`，且外层已经滚动，`content.type` 如果是 `scroll` 内层不再滚动（避免嵌套崩）。

## 十二、onInstalled.json / onUninstalled.json

安装 / 卸载成功后执行。content 只支持 `toast` / `action`。

${"```"}json
{
  "rules": [
    {
      "content": {
        "type": "toast",
        "value": "插件安装成功",
        "duration": "short"
      }
    }
  ]
}
${"```"}

## 十三、常见坑

1. **copy 动作静默失败**：manifest.permissions 里没写 clipboard。
2. **fontSize 没生效**：宿主没改 `PluginContent.fontSize`，所有 text 用默认 14sp。
3. **scroll 嵌套 scroll 崩溃**：外层 Column.verticalScroll 里再套 scroll，内层拿无限高度约束崩。
4. **when 不生效**：检查 `filesDir/plugins/{id}/execute.json` 里 when 字段是否还在——`serializeRules` 旧版不写 when，安装后 when 丢失。新版已修。
5. **WS 断开浮窗不消失**：`ws.connected` 是 true 但 when 仍为 true，说明 when 求值时 ctx 是旧的，`created` 事件要配合 `key(wsConnected, networkOnline)` 或 `remember(key)` 强制重组。
6. **ComposeView 挂 decorView 崩 ViewTreeLifecycleOwner**：Activity 必须 `setContent` 完成后再注入；`waitForAttached` 轮询 decorView.isAttachedToWindow 能规避大部分时序问题。
7. **replace 后原页面返回键异常**：replace 只换 UI，不接管导航，适合本来就没复杂逻辑的页面。
8. **startActivity 目标 Activity 必须已注册**，且 target 是全类名，不是简称。
9. **http 的 body 必须是字符串**，嵌套 JSON 用 `\"` 转义，或拼接时用 `{{kv.xxx}}`。
10. **function.json 里 content 不支持 when**，需要条件判断请用 `if` 动作或在 content 里用 `exists`。
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
            },
            dismissButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("plugin_tutorial", TUTORIAL_MARKDOWN))
                        Toast.makeText(context, "教程已复制", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("复制完整教程")
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