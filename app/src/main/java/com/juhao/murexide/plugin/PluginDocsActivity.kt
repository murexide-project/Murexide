package com.juhao.murexide.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.MarkdownText
import com.juhao.murexide.ui.components.StyledScaffold
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.MurexideTheme

class PluginDocsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MurexideTheme {
                PluginDocsScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PluginDocsActivity::class.java))
        }
    }
}

@Composable
private fun PluginDocsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    StyledScaffold(
        title = { Text("插件开发文档") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, "返回") } },
        actions = {
            IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("plugin_docs", PluginDocs.MARKDOWN))
                Toast.makeText(context, "文档已复制", Toast.LENGTH_SHORT).show()
            }) {
                Icon(AppIcons.ContentCopy, "复制完整文档")
            }
        }
    ) { padding ->
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            MarkdownText(
                markdown = PluginDocs.MARKDOWN,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

object PluginDocs {

    val MARKDOWN: String = """
# Murexide 插件开发文档

插件用 **Lua** 编写，打包成 zip 分发与安装。事件文件直接执行 Lua，插件页面由 Lua 构建组件树，宿主函数在调用时立即执行。

## 1. 快速开始

**只有 `manifest.json` 是必需文件**，仅包含它的插件包也合法。`onInstalled.lua`、`onLaunched.lua`、事件脚本、`pages/pages.json` 和页面脚本均可选，缺失时不会因没有事件或页面而拒绝安装。下面是带页面的示例；将目录内的文件和子目录一起压缩，保证 `manifest.json` 位于 zip 根目录。

`manifest.json`

```json
{
  "id": "hello_plugin",
  "name": "你好插件",
  "author": "你的名字",
  "version": 1,
  "enabled": true,
  "permissions": ["clipboard", "notification"]
}
```

`pages/pages.json`

```json
[
  {
    "id": "hello_main",
    "icon": "SmartToy",
    "title": "你好插件",
    "subtitle": "点一下看看效果"
  },
  {
    "id": "hello_about",
    "icon": "Info",
    "title": "插件说明",
    "subtitle": "同一插件的第二个页面"
  }
]
```

`pages/hello_main.lua`

```lua
local copied = 0
setContent(Column(children={
  Text(content="当前页面：${'$'}{context.page.id}"),
  Text(content="当前账号：${'$'}{(context.account and context.account.username) or '未登录'}"),
  Button(content="复制", enabled="${'$'}{getValue(name='ready',default=true)}", onClick=function()
    copied = copied + 1
    Copy(content="ok")
    Toast(content="已复制 " .. copied .. " 次")
  end)
}))
```

`pages/hello_about.lua`

```lua
setContent(Column(children={
  Text(content="这是同一插件的第二个独立页面"),
  Text(content="页面 id：${'$'}{pageId}")
}))
```

打开 **设置 → 插件 → 右下角 ＋**，选择 zip，在权限确认弹窗中检查申请内容后安装。两个页面分别显示在 **设置 → 额外功能**，各自的标题取 `pages/pages.json` 中注册的 `title`；点击“你好插件”和“插件说明”会分别执行对应 Lua 文件。

## 2. 文件结构与执行方式

```text
hello_plugin/
  manifest.json
  onInstalled.lua
  onLaunched.lua
  onEvent/
    regular/
      onCreated.lua
      onStarted.lua
      onResumed.lua
      onPaused.lua
      onStopped.lua
      onDestroyed.lua
      onWSMsg.lua
      onWSStateChanged.lua
      onNetworkChanged.lua
      onAccountChanged.lua
      onAppForeground.lua
    com.juhao.murexide.ui.chat.ChatActivity/
      onResumed.lua
      onWSMsg.lua
  pages/
    pages.json
    hello_main.lua
    hello_about.lua
```

| 路径 | 作用 |
| --- | --- |
| `manifest.json` | 必需，插件元信息与权限声明 |
| `onEvent/regular/事件文件.lua` | 可选，匹配事件时执行的通用脚本 |
| `onEvent/完整活动类名/事件文件.lua` | 可选，当前 Activity 匹配时执行的同名事件脚本 |
| `pages/pages.json` | 可选，注册一个或多个额外功能页面，每项生成独立入口 |
| `pages/页面id.lua` | 对应页面打开时执行，通过 `setContent` 设置内容 |
| `onInstalled.lua` | 可选，安装成功后执行一次 |
| `onLaunched.lua` | 可选，位于插件根目录，每次 Application 进程启动时执行一次 |
| `data.json` | 由宿主维护，保存当前插件的持久化数据 |

事件脚本不需要注册函数或动作列表。匹配事件时，宿主依次执行 `regular` 下的事件文件和当前完整活动类名目录下的同名文件；两份都存在时都会执行。每份事件文件使用独立 Lua 会话，普通全局或局部变量不跨文件保留；跨事件、跨页面共享数据使用 `setValue/getValue` 或 `writeData/readData`。

### 2.1 安装脚本

`onInstalled.lua` **每次安装成功后执行一次（包括同 id 覆盖安装）**：这是普通脚本，正常 `eval` 一遍，其中的宿主动作（`Toast`、`Notify`、`writeData`、`Http` 等）**边调用边立即执行**，适合做首次初始化、欢迎通知等一次性工作。之后重启应用、开关插件、重新加载都不会再执行它。

安装脚本使用表传参，字符串支持 `{{字段路径}}` 插值：

```lua
Toast({ content = "感谢安装 {{plugin.name}}" })
writeData({ name = "installed_at", value = "{{time.datetime}}" })
```

### 2.2 进程启动脚本

插件根目录的 `onLaunched.lua` 在每次 Application 进程启动、插件管理器首次初始化完成后执行一次。只有已启用插件会执行它；仅切换页面、回到前台、重新加载、开关插件或安装插件不会额外触发。

```lua
setValue(name="ready", value=true)
```

缺少该文件会直接跳过，不影响插件安装或加载。它与只在安装时执行的 `onInstalled.lua` 时机不同，使用新的命名参数及直接 Lua 执行方式。

## 3. manifest.json

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 无，必填 | 插件唯一标识，建议用下划线命名，不要含空格与斜杠 |
| `name` | string | 同 `id` | 显示名称 |
| `description` | string | 空 | 插件简介，显示在安装确认弹窗与插件管理卡片上 |
| `author` | string | 空 | 作者，可为空 |
| `version` | int | `1` | 版本号，仅用于展示 |
| `minVersion` | int | `0` | 要求的最低应用 versionCode，低于当前应用版本时拒绝安装 |
| `enabled` | bool | `true` | 是否启用，插件管理页的开关会改写此字段；禁用后事件脚本与页面入口停止生效 |
| `permissions` | array | 空数组 | 申请的权限列表，见第 4 节 |

未知字段会被忽略，未知权限会在日志中给出警告并从权限集合中剔除。

## 4. 权限

安装时，每个权限都会以「图标 + 名称 + 风险说明」的形式列出，并按风险等级排序：高危权限在最上，敏感权限其次，普通权限在最后。

| 权限 | 名称 | 风险 | 说明 |
| --- | --- | --- | --- |
| `network` | 网络请求 | 敏感 | 可向外部服务器收发数据，消息内容可能被读取 |
| `clipboard` | 读写剪贴板 | 普通 | 当前脚本接口提供 `Copy` 写入能力，未提供读取函数 |
| `notification` | 发送通知 | 普通 | 可向通知栏推送消息 |
| `vibrate` | 震动 | 普通 | 可触发设备震动 |
| `readSettings` | 读取应用设置 | 敏感 | 保留的权限标识；当前运行时未注入设置字段或提供读取函数 |
| `readAccount` | 读取当前账号 | 敏感 | 可读取当前账号的用户名与头像 |
| `openChat` | 打开会话 | 敏感 | 可直接跳转到指定会话 |
| `startActivity` | 启动 Activity | 敏感 | 可通过完整类名启动应用内任意页面 |
| `restartApp` | 重启应用 | 敏感 | 可触发应用重启 |
| `openUrl` | 打开外部链接 | 普通 | 可调用浏览器 |
| `storage` | 插件存储 | 敏感 | 可在插件私有空间持久化数据 |
| `log` | 写入日志 | 普通 | 可写入插件日志文件 |
| `share` | 系统分享 | 普通 | 可调用系统分享面板 |
| `user_bot` | 用户 Bot | 高危 | 可使用当前登录账号发送文本消息、撤回消息 |
| `all` | 全部权限 | 高危 | 等同于授予以上全部能力，包含 `user_bot` |

权限在执行宿主函数时校验。命名参数、Lua 闭包和页面脚本均不会绕过权限检查。缺少权限时查阅日志中的 `permission denied`。

```json
{ "permissions": ["clipboard", "notification", "storage"] }
```

`all` 为通配权限，包含 `user_bot`。申请 `all` 或 `user_bot` 都会在安装弹窗中显示红色高危警示条，请只安装可信插件。

## 5. 事件脚本

### 5.1 事件文件

| 文件名 | 触发时机 | 事件附加字段 |
| --- | --- | --- |
| `onCreated.lua` | Activity 创建 | 页面及生命周期上下文 |
| `onStarted.lua` | Activity 启动 | 页面及生命周期上下文 |
| `onResumed.lua` | Activity 恢复交互 | 页面及生命周期上下文 |
| `onPaused.lua` | Activity 暂停 | 页面及生命周期上下文 |
| `onStopped.lua` | Activity 停止 | 页面及生命周期上下文 |
| `onDestroyed.lua` | Activity 销毁 | 页面及生命周期上下文 |
| `onSaveInstanceState.lua` | Activity 保存实例状态 | 页面上下文，不提供 Bundle 对象 |
| `onWSMsg.lua` | WebSocket 新消息、编辑或撤回 | `type`（`new` / `edit` / `recall`）、`msgId`、`chatId`、`chatType`、`senderId`、`senderName`、`content`、`contentType`、`timestamp` |
| `onWSStateChanged.lua` | 首次订阅状态及后续连接状态变化 | `extra.connected` 或 `context.ws.connected` 为本次事件状态 |
| `onNetworkChanged.lua` | 网络可用状态通知 | `extra.online` 或 `context.network.online` 为本次事件状态 |
| `onAccountChanged.lua` | 首次订阅账号及后续账号或登录状态变化 | `accountId`、`username`、`avatar` |
| `onAppForeground.lua` | 应用回到前台 | 时间、账号、网络等上下文 |

表中附加字段通过 `extra` 或 `context.extra` 读取；宿主携带的其他事件字段也会完整保留，不限于表内列出的名称。生命周期事件目前排除应用锁入口 `GateActivity`。WS 与账号状态流首次订阅时也会产生通知，不应将每次通知都视为一次实际状态切换。

### 5.2 通用事件与指定页面事件

三类消息统一执行 `onWSMsg.lua`，`event` 均为 `ws.message`，通过 `extra.type`（或 `context.extra.type`）区分：`new` 表示新消息、`edit` 表示编辑、`recall` 表示撤回。三类事件使用相同的字段结构；`content` 等字段取自本次事件携带的消息快照，撤回事件不保证保留原文，`timestamp` 是消息时间而非编辑或撤回时间。

只处理新消息时，使用 `extra.type == "new"` 判断；处理编辑或撤回时，分别使用 `"edit"`、`"recall"` 分支。

`onEvent/regular/onWSMsg.lua`：

```lua
if extra.type == "new" and tostring(extra.contentType) == "1" then
  Notify(title="来自 " .. (extra.senderName or "未知发送者"), content=extra.content or "")
elseif extra.type == "edit" then
  setValue(name="lastEditedMessage", value=extra.msgId)
elseif extra.type == "recall" then
  setValue(name="lastRecalledMessage", value=extra.msgId)
end
```

`onEvent/com.juhao.murexide.ui.chat.ChatActivity/onWSMsg.lua`：

```lua
if extra.type == "new" or extra.type == "edit" then
  setValue(name="lastChatMessage", value=extra.content)
end
```

当前 Activity 为 `com.juhao.murexide.ui.chat.ChatActivity` 时，收到消息会先运行通用文件，再运行该页面目录中的文件。生命周期事件使用发生事件的 Activity；WS、网络等应用事件使用最近恢复交互且未销毁的 Activity，后台时也可能保留该页面。没有可用 Activity 时只匹配 `regular`，`page` 为 `nil`。脚本内可直接使用 Lua 的 `if`、循环、局部变量和函数。

## 6. Lua 语法与上下文

### 6.1 命名参数

```lua
Copy(content="")
writeData(name="x", value={1,true,"x"})
local x = readData(name="x", default={})
setValue(name="ready", value=true)
local ready = getValue(name="ready", default=false)
```

`name=value` 形式的函数实参是宿主提供的语法兼容层，会在执行前转换为标准 Lua。普通 Lua 的表、函数、局部变量、控制流和表达式仍然支持。组件构造也可使用命名参数。同一次调用不要混用命名参数与位置参数。函数参数无需外包 `{}`，但列表和对象值仍使用 Lua table，例如 `children={...}`、`value={1,true}`。

宿主函数被调用时立即执行；需要等点击后再执行的逻辑必须放在 `function() ... end` 回调内。

### 6.2 嵌套上下文

`context` 是嵌套 Lua table，可用 `context.account.username` 等方式读取，包含下表列出的时间、账号、应用、网络、WebSocket、插件和页面上下文。账号字段受 `readAccount` 或 `all` 权限限制；当前没有 `context.settings`。主题颜色通过 `MaterialTheme.colorScheme` 使用，无需 `readSettings` 权限。

| 变量 | 说明 |
| --- | --- |
| `context.account.id/username/avatar/loggedIn` | 当前账号信息与登录状态 |
| `context.app.version/versionCode/package` | 应用版本及包名 |
| `context.time.now/date/time/datetime` | 毫秒时间戳、日期、时间、完整日期时间 |
| `context.network.online` | 网络是否可用 |
| `context.ws.connected` | WebSocket 是否连接 |
| `context.plugin.id/name/author/version` | 当前插件信息 |
| `page` | 当前 Activity 的完整类名字符串 |
| `currentPage` | 与 `page` 相同的完整类名字符串 |
| `pageId` | 当前插件页面的原始注册 id |
| `context.page.id` | 当前 Activity 的完整类名 |
| `event`、`context.event` | 宿主事件标识，不一定与文件名相同，见下方说明 |
| `extra`、`context.extra` | 当前事件附加字段 table，保留宿主提供的所有字段 |

`page` 是字符串，读取活动类名可直接用 `page`，或使用 `context.page.id`；不要把它当作页面对象读取 `page.id`。插件页面 id 使用 `pageId`。

账号未登录、未获 `readAccount` 权限或字段不存在时，对应 table 或字段可能为 `nil`，可用 `(context.account and context.account.username) or "未登录"` 等表达式提供默认值。

生命周期事件标识为 `created`、`started`、`resumed`、`paused`、`stopped`、`destroyed`、`saveInstanceState`；应用事件为 `ws.message`、`ws.connected`、`ws.disconnected`、`network.online`、`network.offline`、`account.changed`、`app.foreground`。消息的新建、编辑与撤回共用 `ws.message`，由 `extra.type` 区分。根启动脚本的标识为 `onLaunched`。启动时尚未创建页面，`page`、`currentPage` 和 `context.page.id` 可以为 `nil`；`pageId` 仅插件页面脚本有值。

### 6.3 字符串插值

启动、事件与页面脚本向宿主传递的字符串可使用 `${'$'}{expr}` 模板，`expr` 是 Lua 表达式，可访问上下文、当前作用域局部变量以及只读宿主函数。模板由宿主求值，不是 Lua 自带的字符串插值；Lua 内部的判断和运算应直接使用变量或表达式，不要对模板字符串做布尔判断。

```lua
local prefix = "你好"
setContent(Column(children={
  Text(content="${'$'}{prefix}，${'$'}{(context.account and context.account.username) or '访客'}"),
  Text(content="当前活动：${'$'}{context.page.id}"),
  Text(content="计数：${'$'}{getValue(name='count',default=0) + 1}"),
  Button(content="操作", enabled="${'$'}{getValue(name='ready',default=true)}", onClick=function()
    Copy(content=prefix)
  end)
}))
```

插值不仅支持字段替换，也支持运算、读取数据的函数调用与局部变量。模板表达式应只读取数据；宿主会拒绝在模板求值时调用 `Copy`、`writeData`、`setValue` 等产生副作用的宿主函数，自定义 Lua 函数也应保持无副作用。`enabled` 支持使用整个模板表达式返回布尔值。文本、标题、副标题、占位文字、颜色及宽高字符串支持随页面刷新重新求值；`size`、`padding`、`paddingH`、`paddingV`、`min`、`max`、`step` 及 `options` 在构造组件时求值，需要变化时重新调用 `setContent`。`type`、`icon`、`align`、`model` 使用固定值。页面交互回调结束或 `setValue/writeData` 修改数据后，宿主会刷新页面绑定；普通局部变量不会自行触发刷新。这一段描述 `setContent` 页面组件；`addFloatingView` 的重新求值范围见第 7.6 节。

## 7. 宿主函数

### 7.1 提示与系统能力

| 函数示例 | 说明 | 权限 |
| --- | --- | --- |
| `Toast(content="已复制", type="short")` | 轻提示，`type` 可为 `short` 或 `long` | — |
| `Notify(title="提醒", content="有新消息")` | 发送插件通知 | `notification` |
| `Copy(content="文本")` | 写入剪贴板 | `clipboard` |
| `Vibrate(duration=300)` | 震动，时长单位毫秒 | `vibrate` |
| `OpenUrl(url="https://example.com")` | 交给系统打开链接 | `openUrl` |
| `OpenChat(chatId="123", chatType=2, name="群名", avatar="")` | 打开会话，`chatType` 为 1 私聊、2 群聊 | `openChat` |
| `StartActivity(activity="com.juhao.murexide.ui.about.AboutActivity")` | 按完整类名打开应用内 Activity | `startActivity` |
| `RestartApp()` | 重启应用，未保存的数据会丢失 | `restartApp` |
| `Log(level="warn", content="发生了一点小问题")` | 写插件日志，等级为 `info`、`warn`、`error` | `log` |
| `Share(content="分享内容")` | 调用系统分享面板 | `share` |

`StartActivity` 可传入 `extras` 表。Lua 整数作为 `Long`、小数作为 `Double` 写入 Intent，不能替代目标 Activity 用 `getIntExtra` 读取的参数。打开会话请使用 `OpenChat`，它会按接收端要求传递 `chatType`。

```lua
StartActivity(activity="com.juhao.murexide.ui.about.AboutActivity", extras={source="plugin"})
```

### 7.2 对话框与回调

```lua
local key = "history"
Alert(title="确认", message="确定要清空记录吗？", confirmText="清空", cancelText="取消", onConfirm=function()
  writeData(name=key, value={})
  Toast(content="已清空")
end)
```

回调是原生 Lua 函数，保留闭包中的局部变量。函数体在用户确认后执行。同一插件同时保留一个待确认对话框，新对话框会替换旧对话框。当前只有插件管理页和插件页面挂载了对话框宿主；其他页面或启动阶段调用 `Alert` 不会自动弹出，未订阅时发出的显示事件不会在之后进入页面时自动补发。

### 7.3 writeData / readData：持久化数据

```lua
writeData(name="x", value={1,true,"x"})
local x = readData(name="x", default={})
local first = x[1]
```

需要 `storage` 权限。数据按插件隔离，保存到当前插件目录的 `data.json`。支持字符串、有限数值、布尔、连续列表与字符串键对象；读取直接返回 Lua 值，键不存在时返回 `default`。列表必须从索引 1 连续排列，不支持混合键、稀疏列表、函数和循环引用；空 table 按空列表保存。`writeData(name="x", value=nil)` 删除该键，`nil` 不能作为可与缺失键区分的保存值；列表中间的 `nil` 会形成空洞，尾部 `nil` 也不能保留列表长度。数据嵌套深度必须小于 48。读取返回的 table 是副本，修改后需要再次 `writeData` 才会保存。

不要先转成字符串再保存，也不需要通过额外事件取回读取结果。卸载插件时会删除插件目录及其 `data.json`；当前同 id 覆盖安装也会先重建插件目录，旧持久化数据不会自动保留。

### 7.4 setValue / getValue：临时变量

```lua
setValue(name="ready", value=false)
setValue(name="count", value=getValue(name="count", default=0) + 1)
local ready = getValue(name="ready", default=true)
```

临时变量在同一插件的事件和页面之间共享，插件之间隔离，支持与 `writeData` 相同的数据类型，不支持 Lua 函数或循环引用。`setValue(name="x", value=nil)` 删除变量，变量不存在时 `getValue` 返回 `default`。读取返回的 table 是副本，修改后需要再次 `setValue` 才会更新共享值。它们只存在于当前应用进程，重启后清空；需要跨重启保存的数据使用 `writeData`。

### 7.5 Http：网络请求

```lua
local response = Http(method="POST", url="https://httpbin.org/post", body='{"hello":"world"}', headers={["Content-Type"]="application/json"}, saveTo="resp")
if response.successful then
  local body = response.body
end
```

需要 `network` 权限。`method` 默认 `GET`；`url` 为请求地址，`body` 为请求体，`headers` 为请求头 table。在 Lua 执行线程等待请求完成，直接返回含 `code`、`successful`、`body` 的 table，下一行即可读取响应。可选 `saveTo` 将响应体同时写入同名临时变量。本节接口适用于启动、事件与页面脚本。

### 7.6 当前页面浮窗

使用 `addFloatingView(id="xxx", gravity="top|end", content={...})` 创建附着当前 Activity 的独立原生浮窗，使用 `removeFloatingView(id="xxx")` 移除。`id` 必须是非空字符串；同一插件、同一 Activity 实例下相同 id 会替换原浮窗及位置，不同插件及不同页面实例互不影响。移除不存在的 id 不报错。

例如放到 `onEvent/regular/onResumed.lua`：

```lua
addFloatingView(id="quick_actions", gravity="bottom|end", content={
  Button(content="计数：${'$'}{getValue(name='count',default=0)}", onClick=function()
    setValue(name="count", value=getValue(name="count", default=0) + 1)
  end)
})
```

`content` 可传单个组件或从索引 1 开始的连续组件列表。浮窗通过 `WindowManager` 的 `TYPE_APPLICATION_PANEL` 单独承载，直接显示插件原内容；宿主不添加外框、标题栏、收起/关闭按钮、内边距、阴影或背景，也不强制 320dp 宽度、60% 高度或自动滚动。大小随内容决定并受当前可用区域限制；需要滚动时由内容设置 `Column(scrollable=true)` / `Row(scrollable=true)` 或使用 `Scroll`。窗口外的触摸可操作原页面，沿用宿主配色，不绘制全屏液态玻璃背景。这是应用内附着当前 Activity 的原生窗口，无需跨应用悬浮窗权限；不会悬浮到其他应用上。离开页面不会将浮窗迁移到新页面；页面销毁、插件重载、禁用或卸载时会清理。

| 定位参数 | 说明 |
| --- | --- |
| `gravity` | 默认 `"top|end"`；支持 `top`、`bottom`、`center`、`start`、`end`、`left`、`right`、`center_horizontal`、`center_vertical`，使用 `|` 组合，如 `"bottom|end"`。单独 `top` / `bottom` 时横向居中，单独 `start` / `end` 时纵向居中；start/end 随布局方向变化 |
| `position` | `gravity` 的别名，两者同时指定时优先使用 `gravity` |
| `x`、`y` | 可选有限 dp 坐标；指定 x 后横向从可用区域左边定位，指定 y 后纵向从顶部定位，覆盖对应方向的 gravity。可只指定一个轴；0 有效 |

```lua
addFloatingView(id="middle", position="center", content=Text(content="居中"))
addFloatingView(id="bottom", gravity="bottom|end", content=Button(content="右下角"))
addFloatingView(id="custom", x=24, y=120, content=Text(content="坐标定位"))
```

位置参数在调用时读取；需要移动时以相同 id 再次调用 `addFloatingView`。

浮窗保留声明式实时更新：`setValue/writeData` 修改数据、浮窗交互回调结束，以及当前 Activity 的生命周期、WS、网络、账号等事件都会触发重新求值。`content={...}` 中的 `${'$'}{}` 绑定每次刷新重新解析，包含文本、`enabled`、颜色、尺寸、间距、范围、选项、图标、对齐及 model 名称；创建时直接传入的普通 Lua 计算结果不会自动变成绑定。事件附加字段 `extra` 保留创建该会话时的事件数据，时间、账号、网络等上下文在刷新时重新读取；不会使用定时轮询刷新时钟。

需要条件分支、列表变化或使用普通 Lua 表达式重建整个组件树时，可传入内容函数：

```lua
addFloatingView(id="status", content=function()
  local ready = getValue(name="ready", default=false)
  if ready then
    return { Text(content="已就绪", color=MaterialTheme.colorScheme.primary) }
  end
  return { Text(content="等待中") }
end)
```

内容函数在每次刷新时重新执行，必须返回单个组件或组件列表，并保持只读；不要在其中调用 `setValue`、`writeData`、`addFloatingView`、`removeFloatingView`、`Http` 等产生副作用的宿主函数。交互逻辑放在 `onClick=function() ... end` 中。

浮窗会保留创建它的 Lua 会话，因此事件文件执行结束后，按钮回调和局部变量仍然有效；最后一个浮窗移除且没有待确认对话框时，事件会话会释放。没有可用 Activity、页面正在关闭或已经销毁时不能创建浮窗，因此不要在进程刚启动的根 `onLaunched.lua` 中调用。安装脚本不提供浮窗接口。

### 7.7 sendMessage / recallMessage：用户 Bot

需要在 `manifest.json` 中声明高危权限：

```json
{ "permissions": ["user_bot"] }
```

```lua
local sent = sendMessage(chatId="123", chatType=2, content="你好")
if sent.successful then
  local recalled = recallMessage(chatId="123", chatType=2, msgId=sent.msgId)
  if not recalled.successful then
    Toast(content=recalled.error)
  end
else
  Toast(content=sent.error)
end
```

| 函数 | 参数 | 返回值 |
| --- | --- | --- |
| `sendMessage` | 必填 `chatId`、`chatType`、`content`；可选 `quoteMsgId` | 成功返回 `{successful=true, msgId="消息ID"}` |
| `recallMessage` | 必填 `chatId`、`chatType`、`msgId` | 成功返回 `{successful=true, msgId="被撤回的消息ID"}` |

`chatType` 必须明确指定：`1` 私聊、`2` 群聊、`3` 机器人；`chatId`、`msgId` 使用字符串。`content` 是非空文本，目前不支持图片或文件。两函数均支持普通 Lua 表传参及位置参数：`sendMessage("123", 2, "你好", "可选引用消息ID")`、`recallMessage("123", 2, "消息ID")`。

函数使用调用时的当前登录账号，在 Lua 执行线程等待服务器响应。成功后同步应用内消息状态；账号已经切换时不会向新账号发布旧账号的本地消息事件。未登录、参数错误或服务器拒绝时返回 `{successful=false, error="原因"}`；缺少权限、插件停用或在只读模板中调用时抛出 Lua 错误，停止本次调用。撤回范围、管理权限与时限由服务器校验。

事件脚本、页面及回调、`onLaunched.lua`、`onInstalled.lua` 均可使用这两个函数。自动回复应在 `onEvent/regular/onWSMsg.lua` 中筛选新消息及发送者，避免回复自己或让多个插件互相触发；不要在通用目录和 Activity 目录重复配置同一自动回复逻辑。

## 8. 插件页面

### 8.1 pages.json 的三种格式

`pages/` 是正式页面目录，`pages/pages.json` 是可选注册文件，可采用单对象、数组或包含 `pages` 数组的对象。多页面推荐直接使用 JSON 数组 `[{...},{...}]`，不能写成 Lua 风格的 `{{...},{...}}`；后者不是有效 JSON。每个对象对应同目录中的一个 Lua 文件，并在 **设置 → 额外功能** 生成独立入口，按注册顺序展示。

页面注册文件及对应 Lua 脚本放在 `pages/` 中。`pages/pages.json` 决定页面入口；注册为空、格式错误或不是普通文件时，不会注册其他页面。

注册文件缺失或没有页面时不注册入口；缺少对应 Lua 文件的页面会记录警告并跳过。JSON 格式错误，或条目不是对象、id 缺失、非法、重复时，会记录错误并停止解析后续条目；已解析的页面保留，插件本身仍可加载。

三种格式示例：

```json
{"id":"main","icon":"Settings","title":"我的功能","subtitle":"副标题"}
```

```json
[
  {"id":"main","icon":"Settings","title":"我的功能","subtitle":"副标题"},
  {"id":"history","icon":"Info","title":"历史记录","subtitle":"查看记录"}
]
```

```json
{"pages":[{"id":"main","icon":"Settings","title":"我的功能","subtitle":"副标题"},{"id":"history","icon":"Info","title":"历史记录","subtitle":"查看记录"}]}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 同插件内唯一，匹配 `[A-Za-z0-9_][A-Za-z0-9_.-]*`，对应 `pages/id.lua` |
| `icon` | 宿主图标名 |
| `title` | 设置入口与插件页面展示的标题 |
| `subtitle` | 可选副标题 |

宿主的页面入口使用 `pluginId:pageId` 全局命名空间，插件文件继续使用注册的原始 `id`。例如插件 `hello_plugin` 注册 `main` 和 `history`，将生成 `hello_plugin:main` 和 `hello_plugin:history` 两个独立入口，分别执行 `pages/main.lua` 和 `pages/history.lua`。不要把命名空间前缀写进脚本文件名。每个页面打开时有独立 Lua 会话，跨页面共享数据使用 `setValue/getValue` 或 `writeData/readData`。

`pageId` 与 `context.page.pluginPageId` 均表示当前插件页面的注册 id；`page`、`currentPage` 和 `context.page.id` 表示 Activity 类名。

### 8.2 设置页面内容

`pages/main.lua`：

```lua
local copied = 0
setContent(Column(children={
  Text(content="插件页面：${'$'}{pageId}"),
  Text(content="活动：${'$'}{context.page.id}"),
  Button(content="复制", enabled="${'$'}{getValue(name='ready',default=true)}", onClick=function()
    copied = copied + 1
    Copy(content="ok")
    setValue(name="copied", value=copied)
  end)
}))
```

`setContent` 接收根组件。页面由宿主的 **StyledScaffold** 承载，保留宿主主题、标题栏与返回行为。

### 8.3 组件

| 组件 | 用途与主要参数 |
| --- | --- |
| `Column(scrollable=false, children={...})` | 纵向容器；`scrollable=true` 开启纵向滚动 |
| `Row(scrollable=false, children={...})` | 横向容器；`scrollable=true` 开启横向滚动 |
| `Scroll(children={...})` | 纵向滚动容器，避免同方向滚动容器无限嵌套 |
| `Text(content="文本")` | 文本 |
| `Button(content="按钮", onClick=function() ... end)` | 按钮 |
| `TextField(model="input", placeholder="请输入")` | 输入框 |
| `Dropdown(options={"A","B"}, model="choice")` | 下拉选择 |
| `Slider(min=0, max=100, step=1, model="level")` | 滑块 |
| `Switch(model="switch")` | 开关 |
| `Checkbox(model="checked")` | 复选框 |
| `IconButton(icon="Build", onClick=function() ... end)` | 图标按钮 |
| `Card(children={...}, onClick=function() ... end)` | 卡片容器 |
| `SettingsCard(title="设置", subtitle="说明", icon="Settings", children={...}, onClick=function() ... end, enabled=true)` | 设置样式卡片 |
| `Spacer(size=8)` | 间距，单位 dp |
| `Image(src="assets/logo.png", size=64)` | 图片 |
| `Icon(icon="Info", size=24)` | 图标 |
| `Divider()` | 分隔线 |

`scrollable` 仅用于 `Column` 和 `Row`，默认 `false`；支持布尔值和整个模板表达式（例如 `scrollable="${'$'}{getValue(name='canScroll',default=true)}"`），布尔转换规则与 `enabled` 相同。滚动方向上的视口必须有界：可使用页面可用尺寸或固定 `height` / `width`，内容超出视口时才能滚动。同方向嵌套时共用外层滚动，避免无限尺寸测量；`scrollable=false` 只关闭该组件自身的滚动，不禁止外层滚动。浮窗不自动添加滚动容器，需要滚动时由插件内容显式开启。

```lua
setContent(Column(scrollable=true, height=320, children={
  Text(content="纵向内容"),
  Row(scrollable=true, width="match", children={
    Button(content="操作一"),
    Button(content="操作二"),
    Button(content="操作三"),
    Button(content="操作四")
  })
}))
```

`model` 绑定当前插件的运行时变量，可用 `getValue(name="input", default="")` 等方式读取，写成 `model="input"` 或 `model="var.input"` 均可。TextField、Dropdown、Slider、Switch、Checkbox 的值先写入 model，再调用 `onClick`；回调不带位置参数，通过 `getValue` 读取新值。交互回调可捕获局部变量。

页面入口支持 `Info`、`SmartToy`、`Person`、`Link`、`Image`、`Settings`、`Code`、`Warning`、`Build`；页面组件还支持 `People`。图标名区分大小写，未知名称回退为 `Build`。

### 8.4 enabled 与通用属性

`enabled` 适用于 **Button、TextField、Dropdown、Slider、Switch、Checkbox、IconButton、Card、SettingsCard**。可直接传布尔值，也可用表达式读取临时状态。也提供 `enabled(value=true)` 函数，作为 `enabled=enabled(value=true)` 的属性值使用；它返回传入值，不会单独修改已有组件。直接写 `enabled=getValue(...)` 只取构造时的值，持续绑定应使用模板。不要传入 Lua 函数作为 `enabled` 属性。布尔转换接受 `true`、有限非零数值及字符串 `"true"` 或有限非零数字；其他值为禁用，这与 Lua 将 0 视为真值的规则不同。父容器禁用时，其子组件一并禁用：

```lua
setContent(SettingsCard(title="操作", subtitle="准备好后可用", icon="Settings", enabled="${'$'}{getValue(name='ready',default=true)}", children={
  Button(content="复制", enabled=true, onClick=function()
    Copy(content="ok")
  end)
}))
```

| 属性 | 说明 |
| --- | --- |
| `width`、`height` | `match` / `fill`、`wrap` 或固定 dp 数值 |
| `padding`、`paddingH`、`paddingV` | 内边距 dp |
| `align` | 对齐方式 |
| `size` | Text 字号（sp）；图标、图片或间距等组件的尺寸（dp） |
| `color`、`background` | 颜色字面值或宿主语义颜色引用 |
| `children` | 原生 Lua table 组成的子组件列表 |
| `onClick` | 点击时调用的 Lua 函数 |
| `enabled` | 交互组件是否启用 |
| `scrollable` | Column 纵向 / Row 横向滚动，默认 false，支持模板绑定 |

### 8.5 主题颜色与图片

```lua
setContent(Column(children={
  Text(content="主题色文本", color=MaterialTheme.colorScheme.primary),
  Image(src="assets/logo.png", size=64),
  Image(src="https://example.com/a.png", size=64)
}))
```

`MaterialTheme.colorScheme.primary` 等值由 Lua 环境注入为语义颜色引用，宿主在支持颜色属性的组件渲染时解析为当前主题的实际颜色。不要把语义引用当成固定十六进制色值。

图片 `assets/` 路径相对于插件目录，网络图片使用 `http://` 或 `https://` 地址。主题字段名大小写必须准确，未知主题字段或无效颜色显示为洋红色。当前图片加载不经过 Lua `Http` 的权限检查，不应将 `network` 权限视为整个插件页面的网络隔离机制。

## 9. 完整示例：关键词提醒

`manifest.json`

```json
{
  "id": "keyword_alert",
  "name": "关键词提醒",
  "author": "you",
  "version": 1,
  "enabled": true,
  "permissions": ["notification", "storage"]
}
```

`onEvent/regular/onWSMsg.lua`

```lua
local content = extra.content or ""
if extra.type == "new" and tostring(extra.contentType) == "1" and string.find(content, "提醒", 1, true) then
  writeData(name="last_msg", value={sender=extra.senderName, content=content})
  Notify(title="新消息 · " .. (extra.senderName or "未知发送者"), content=content)
end
```

`pages/pages.json`

```json
{"pages":[{"id":"main","icon":"Info","title":"关键词提醒","subtitle":"查看最近一条提醒"}]}
```

`pages/main.lua`

```lua
local function refresh()
  local message = readData(name="last_msg", default={content="暂无提醒"})
  setContent(Column(children={
    Text(content="最近消息：${'$'}{message.content}"),
    Button(content="刷新", onClick=function()
      refresh()
    end)
  }))
end
refresh()
```

## 10. 存储、日志与排错

- 临时数据使用 `setValue/getValue`，插件之间互不可见，应用重启清空。
- 持久化数据使用 `writeData/readData`，类型保持并存入插件 `data.json`。
- 加载失败、权限被拒与执行异常可在 **插件管理页 → 日志** 中检查。
- 安装提示无效插件包时，检查 `manifest.json` 是否在 zip 根目录及 JSON 是否有效。
- 事件未执行时，检查 `onEvent/regular/` 或完整活动类名目录、事件文件名大小写以及插件启用状态。
- 两份同名事件脚本都会执行；出现重复效果时，检查通用目录和活动目录是否都调用了相同函数。
- 页面空白时，检查 `pages/pages.json` 注册的 id、对应 `pages/id.lua` 与 `setContent` 调用。
- 插件入口标题来自注册 `title`，不是全局入口 id。
- 回调提前执行时，将宿主函数调用放进 `function() ... end`，不要在构建参数时先调用。
- 表达式异常时，检查局部变量作用域、`context` 路径及可能为 `nil` 的字段，并用 Lua `or` 提供默认值。
- `page` 与 `currentPage` 是完整活动类名字符串，插件页面 id 使用 `pageId`。
- `readData` 直接返回读取值；事件与页面中的 `Http` 也直接返回请求完成后的响应 table。

## 11. 安全与边界

- 插件跑在应用进程内，受控宿主函数按 `manifest.json` 声明的权限检查；页面网络图片加载目前不校验 `network` 权限。
- 命名参数兼容层只处理语法，不扩大插件权限。
- 仅安装来源可信的插件，谨慎申请和授予 `all`。
- 安装包中的非法路径会被拒绝；卸载插件会删除插件目录和其中的持久化数据。
- `onInstalled.lua` 在安装成功后执行，使用表传参；启动、事件与页面脚本支持本文的命名参数及上下文接口。
""".trimIndent()
}
