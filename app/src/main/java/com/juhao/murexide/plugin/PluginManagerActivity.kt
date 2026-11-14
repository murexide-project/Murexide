package com.juhao.murexide.plugin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import com.juhao.murexide.ui.theme.MurexideTheme
import java.io.File

class PluginManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

插件是一个 zip 文件。导入后应用会把它解压到私有目录，按其中的 JSON 规则在每个 Activity 上叠加 UI、整页替换、弹出 Toast，或在安装/卸载时执行动作。不需要写代码，只要写 JSON。

## 一、zip 目录结构

${"```"}
my-plugin.zip
├── manifest.json        # 必需
├── execute.json         # 可选，页面规则
├── onInstalled.json     # 可选，安装时执行
└── onUninstalled.json   # 可选，卸载时执行
${"```"}

manifest.json 是唯一必需文件，另外三个都可省略。不写 execute.json 时插件不产生页面效果，只作为可管理的占位。所有文件都放在 zip 根目录，不要多套一层文件夹。

## 二、manifest.json

插件元信息。

${"```"}json
{
  "id": "com.juhao.demo",
  "name": "弹窗演示",
  "description": "在所有页面右下角加一个弹窗按钮",
  "version": 1,
  "author": "juhao"
}
${"```"}

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| id | 是 | 插件唯一标识，建议用反向域名，卸载按它删目录 |
| name | 否 | 显示名，不填就用 id |
| description | 否 | 插件简介，显示在管理页卡片上 |
| version | 否 | 整数，默认 1 |
| author | 否 | 作者名，展示用 |

## 三、execute.json

页面规则数组。每条规则描述在哪个页面、哪个生命周期事件、做什么动作、放什么内容。这里的按钮用 onClick，表示用户点击时触发。

${"```"}json
{
  "rules": [
    {
      "page": "*",
      "event": "created",
      "action": "decorate",
      "position": "floating",
      "content": {
        "type": "dialog",
        "value": "打开弹窗",
        "title": "插件弹窗",
        "message": "这是插件注入的弹窗内容",
        "confirmText": "知道了",
        "color": "#FF5722"
      }
    }
  ]
}
${"```"}

### 规则字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| page | 是 | Activity 全类名，或 * 表示所有页面 |
| event | 否 | created、started、resumed、paused、stopped、destroyed，默认 created |
| action | 否 | decorate（叠加）、replace（整页替换），默认 decorate |
| position | 否 | top、bottom、floating，默认 top |
| content | 是 | 内容对象，见下 |

说明：

- decorate：内容叠加在页面之上，原页面照常运行。
- replace：原页面内容被清空，整个页面换成插件内容。同一个页面只取第一条 replace 规则生效。
- created 支持 text、button、dialog、column、toast。
- started / resumed / paused / stopped / destroyed 只支持 toast。

## 四、content 类型

### text 文本

${"```"}json
{
  "type": "text",
  "value": "这是一行文本",
  "color": "#4CAF50"
}
${"```"}

### button 按钮

渲染一个按钮。没有 onClick 时点击弹出 AlertDialog；有 onClick 时点击执行对应动作。

${"```"}json
{
  "type": "button",
  "value": "点我",
  "color": "#2196F3",
  "onClick": {
    "action": "toast",
    "text": "你点了我"
  }
}
${"```"}

### dialog 弹窗按钮

点击后弹出 AlertDialog，不支持 onClick。

${"```"}json
{
  "type": "dialog",
  "value": "打开弹窗",
  "title": "标题",
  "message": "正文",
  "confirmText": "确定"
}
${"```"}

### column 竖直容器

把多个 content 竖着排，可以嵌套。

${"```"}json
{
  "type": "column",
  "children": [
    { "type": "text", "value": "第一行" },
    { "type": "button", "value": "按钮" }
  ]
}
${"```"}

### toast 提示

只在生命周期事件里弹一次，不会常驻页面。

${"```"}json
{
  "type": "toast",
  "value": "页面已启动",
  "duration": "short"
}
${"```"}

duration 可选 short 或 long，默认 short。

## 五、onClick 动作

execute.json 里的 button 用 onClick，表示用户点击时触发。宿主只放行以下五种动作：

| action | 必填字段 | 说明 |
| --- | --- | --- |
| toast | text | 弹一条 Toast |
| copy | text | 复制文本到剪贴板 |
| openUrl | target | 用系统浏览器打开链接 |
| startActivity | target | 打开宿主内某个 Activity，target 为全类名 |
| restartApp | 无 | 清空返回栈并立即重启应用 |

示例：

${"```"}json
{
  "type": "button",
  "value": "打开关于页",
  "onClick": {
    "action": "startActivity",
    "target": "com.juhao.murexide.ui.about.AboutActivity"
  }
}
${"```"}

${"```"}json
{
  "type": "button",
  "value": "复制邀请码",
  "onClick": {
    "action": "copy",
    "text": "MUREXIDE-2024"
  }
}
${"```"}

${"```"}json
{
  "type": "button",
  "value": "打开官网",
  "onClick": {
    "action": "openUrl",
    "target": "https://www.yhchat.com"
  }
}
${"```"}

不在白名单里的动作会被忽略，不会执行，也不会崩溃。

## 六、onInstalled.json 和 onUninstalled.json

这两个文件专门描述安装成功、卸载成功后要执行的动作。结构和 execute.json 一样，但有两个区别：

- 不用写 event，文件名已经决定事件是 installed 或 uninstalled，写了也会被忽略。
- 不用写 page，默认对所有页面生效。
- content 里用 execute 字段，而不是 onClick，因为这里不需要用户点击，宿主读到就直接执行。

支持的 content 类型只有两种：

### toast

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

### action

不渲染任何 UI，只执行 execute 里的动作。

${"```"}json
{
  "rules": [
    {
      "content": {
        "type": "action",
        "execute": {
          "action": "restartApp"
        }
      }
    }
  ]
}
${"```"}

execute 支持的动作和 onClick 相同：toast、copy、openUrl、startActivity、restartApp。

安装成功后弹提示并立即重启：

${"```"}json
{
  "rules": [
    {
      "content": {
        "type": "toast",
        "value": "插件已安装，正在重启",
        "duration": "short"
      }
    },
    {
      "content": {
        "type": "action",
        "execute": {
          "action": "restartApp"
        }
      }
    }
  ]
}
${"```"}

卸载成功后立即重启：

${"```"}json
{
  "rules": [
    {
      "content": {
        "type": "action",
        "execute": {
          "action": "restartApp"
        }
      }
    }
  ]
}
${"```"}

注意：restartApp 会立刻清空任务栈并杀进程，同一批规则里如果还有 toast，Toast 可能来不及显示。如果重启和提示都需要，建议把提示放到重启后页面的 created 事件里。

## 七、page 字段怎么填

* 表示所有页面。要指定某个页面，填它的 Activity 全类名，例如：

${"```"}
com.juhao.murexide.ui.settings.SettingsActivity
${"```"}

不知道类名怎么办？先用 * 测效果；或者在 Android Studio 的 Logcat 里搜 Plugin 就能看到当前打开页面的类名。

## 八、整页替换示例

把设置页整个替换成插件页面，里面放三个带动作的按钮：

${"```"}json
{
  "rules": [
    {
      "page": "com.juhao.murexide.ui.settings.SettingsActivity",
      "event": "created",
      "action": "replace",
      "position": "top",
      "content": {
        "type": "column",
        "children": [
          {
            "type": "text",
            "value": "这个设置页已经被插件替换了",
            "color": "#FF5722"
          },
          {
            "type": "button",
            "value": "复制一段文字",
            "color": "#2196F3",
            "onClick": {
              "action": "copy",
              "text": "这是插件复制的内容"
            }
          },
          {
            "type": "button",
            "value": "打开关于页",
            "color": "#4CAF50",
            "onClick": {
              "action": "startActivity",
              "target": "com.juhao.murexide.ui.about.AboutActivity"
            }
          },
          {
            "type": "button",
            "value": "打开官网",
            "color": "#9C27B0",
            "onClick": {
              "action": "openUrl",
              "target": "https://www.yhchat.com"
            }
          }
        ]
      }
    }
  ]
}
${"```"}

## 九、生命周期 Toast 示例

页面启动和恢复时各弹一条 Toast：

${"```"}json
{
  "rules": [
    {
      "page": "*",
      "event": "started",
      "action": "decorate",
      "position": "top",
      "content": {
        "type": "toast",
        "value": "页面已启动",
        "duration": "short"
      }
    },
    {
      "page": "*",
      "event": "resumed",
      "action": "decorate",
      "position": "top",
      "content": {
        "type": "toast",
        "value": "页面已恢复",
        "duration": "long"
      }
    }
  ]
}
${"```"}

## 十、快速上手

1. 新建一个文件夹，放一个必需的 manifest.json
2. 需要页面效果就加 execute.json，需要安装/卸载动作就加 onInstalled.json / onUninstalled.json
3. 全选这些文件压缩成 zip（不要压缩文件夹本身，要选中文件再压缩）
4. 回到应用，点右下角按钮导入这个 zip
5. 打开目标页面，看到插件效果

## 十一、常见问题

- 导入失败，提示缺少 manifest.json：检查 zip 里是不是多套了一层文件夹，manifest.json 必须在 zip 根目录。
- 导入成功但页面没反应：检查有没有 execute.json，以及 rules 里的 page 字段是否匹配当前页面的类名，先用 * 测试。
- 弹窗按钮看不到：position 用 floating 会出现在右下角，top / bottom 会贴边。
- 整页替换后返回键行为异常：替换只换 UI，不接管原页面的导航逻辑，原页面的返回、状态保存都不再生效，适合本来就没复杂逻辑的页面。
- 按钮点击没反应：检查 execute.json 里 onClick.action 是否在 toast、copy、openUrl、startActivity、restartApp 五种之内；startActivity 的 target 必须是宿主已注册的 Activity 全类名。
- 生命周期 Toast 不弹：检查 event 是否为 started / resumed / paused / stopped / destroyed，且 content.type 为 toast。
- 安装/卸载动作没执行：检查有没有 onInstalled.json / onUninstalled.json，content.type 是否为 action，动作是否写在 execute 字段里而不是 onClick。
- 重启后 Toast 没看到：restartApp 会立刻杀进程，同一批规则里的 toast 可能来不及显示，把提示改到重启后页面的 created 事件里。
""".trimIndent()

@Composable
fun PluginManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manifests by PluginManager.manifests.collectAsState()
    var showTutorial by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingInstall by remember { mutableStateOf<File?>(null) }
    var showReplaceWarning by remember { mutableStateOf(false) }

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
            text = { Text("插件有修改页面等危险操作，请确认插件安全后导入。") },
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
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium)
                                if (!m.description.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        m.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "v${m.version} · ${m.rules.size} 条规则",
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
                                TextButton(onClick = {
                                    PluginManager.uninstall(context, m.id)
                                    Toast.makeText(context, "已卸载 ${m.name}", Toast.LENGTH_SHORT).show()
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
                                StyledSwitch(
                                    checked = m.enabled,
                                    onCheckedChange = { checked ->
                                        PluginManager.setEnabled(context, m.id, checked)
                                        Toast.makeText(
                                            context,
                                            if (checked) "已启用 ${m.name}" else "已停用 ${m.name}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}