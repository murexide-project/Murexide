package com.juhao.murexide.plugin

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    StyledScaffold(
        title = { Text("插件开发文档") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, "返回") } }
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

插件用 **Lua** 编写，运行时无需编译，打包成 zip 即可分发与安装。本文档覆盖文件结构、事件系统、宿主函数、界面组件、渲染规则与排错方法。

> 适用版本：Murexide 1.0.2+，脚本引擎 `com.seanproctor:lua-kmp:0.1.2`

---

## 1. 快速开始

### 1.1 最小可运行插件

新建一个文件夹 `hello_plugin`，放入两个文件：

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

`PageRegister.lua`

```lua
registerPage({
  id = "hello_main",
  icon = "SmartToy",
  title = "你好插件",
  subtitle = "点一下看看效果",
  page = Column({
    width = "match",
    children = {
      Text({ content = "当前账号：{{account.username}}", size = 14 }),
      Spacer({ size = 12 }),
      Button({
        content = "复制我的用户名",
        width = "match",
        onClick = { Copy({ content = "{{account.username}}" }) }
      })
    }
  })
})
```

### 1.2 打包

1. 选中文件夹内的**所有文件**（`manifest.json` 与 `.lua`），而不是外层文件夹
2. 压缩为 `hello_plugin.zip`
3. 打开 **设置 → 插件 → 右下角 ＋**，选择该 zip
4. 在权限确认弹窗中查看申请的权限与风险说明，点击「安装」
5. 回到 **设置 → 额外功能**，即可看到插件入口

> zip 内 `manifest.json` 必须位于**根目录**。若套了一层文件夹，安装会提示「无效插件包」。

---

## 2. 文件结构

插件目录中的文件名是固定的，宿主按下列名字读取，**多余的文件不会执行**：

| 文件名 | 是否必需 | 作用 |
| --- | --- | --- |
| `manifest.json` | 必需 | 插件元信息与权限声明 |
| `PageRegister.lua` | 可选 | 注册「额外功能」页面入口 |
| `OnActivityEvent.lua` | 可选 | 注册页面级事件（按 Activity 匹配） |
| `onEvent.lua` | 可选 | 注册应用级全局事件 |
| `Global.lua` | 可选 | 通用初始化，可放 `decorate` 悬浮组件 |
| `onInstalled.lua` | 可选 | **安装成功后执行一次**，其中的顶层动作立即执行 |

一个插件至少要有一个 `.lua` 文件，否则会被跳过而不报错。

常规脚本加载顺序固定为：

```
OnActivityEvent.lua → onEvent.lua → Global.lua → PageRegister.lua
```

同一插件内的所有脚本共享同一个 Lua 状态，但**各自独立执行**：某个脚本报错只会记录日志，不影响其他脚本继续加载。

`onInstalled.lua` 不参与上面的常规加载，**只在插件安装成功那一刻执行一次**：这是普通脚本，正常 `eval` 一遍，其中的宿主动作（`Toast`、`Notify`、`writeData`、`Http` 等）**边调用边立即执行**，适合做首次初始化、欢迎通知等一次性工作。之后重启应用、开关插件、重新加载都不会再执行它。

```lua
Toast({ content = "感谢安装 {{plugin.name}}" })
writeData({ name = "installed_at", value = "{{time.datetime}}" })
```

---

## 3. manifest.json

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 无，必填 | 插件唯一标识，建议用下划线命名，不要含空格与斜杠 |
| `name` | string | 同 `id` | 显示名称 |
| `author` | string | 空 | 作者，可为空 |
| `version` | int | `1` | 版本号，仅用于展示 |
| `minVersion` | int | `0` | 要求的最低应用 versionCode，低于当前应用版本时拒绝安装 |
| `enabled` | bool | `true` | 是否启用，插件管理页的开关会改写此字段；禁用后事件规则、悬浮组件与页面入口全部停止 |
| `permissions` | array | 空数组 | 申请的权限列表，见第 4 节 |

未知字段会被忽略，未知权限会在日志中给出警告并从权限集合中剔除。

---

## 4. 权限

安装时，每个权限都会以「图标 + 名称 + 风险说明」的形式列出，并按风险等级排序：全部权限在最上，敏感权限其次，普通权限在最后。

| 权限 | 名称 | 风险 | 说明 |
| --- | --- | --- | --- |
| `network` | 网络请求 | 敏感 | 可向外部服务器收发数据，消息内容可能被读取 |
| `clipboard` | 读写剪贴板 | 普通 | 可读取或覆盖系统剪贴板 |
| `notification` | 发送通知 | 普通 | 可向通知栏推送消息 |
| `vibrate` | 震动 | 普通 | 可触发设备震动 |
| `readSettings` | 读取应用设置 | 敏感 | 可读取主题、开关等设置 |
| `readAccount` | 读取当前账号 | 敏感 | 可读取当前账号的用户名与头像 |
| `openChat` | 打开会话 | 敏感 | 可直接跳转到指定会话 |
| `startActivity` | 启动 Activity | 敏感 | 可通过完整类名启动应用内任意页面 |
| `restartApp` | 重启应用 | 敏感 | 可触发应用重启 |
| `openUrl` | 打开外部链接 | 普通 | 可调用浏览器 |
| `storage` | 插件存储 | 敏感 | 可在插件私有空间持久化数据 |
| `log` | 写入日志 | 普通 | 可写入插件日志文件 |
| `share` | 系统分享 | 普通 | 可调用系统分享面板 |
| `all` | 全部权限 | 高危 | 等同于授予以上全部能力 |

权限在**执行动作时**校验，而不是在加载脚本时。若动作缺少对应权限，宿主会跳过该动作并写入一条 `permission denied` 警告日志，其余动作照常执行。

```json
{ "permissions": ["clipboard", "notification", "storage"] }
```

> `all` 为通配权限。申请它会在安装弹窗中显示红色警示条，非必要不要使用。

---

## 5. 事件系统

### 5.1 注册事件

```lua
onEvent({
  event = "ws.message",
  pages = { "com.juhao.murexide.ui.chat.ChatActivity" },
  onEvent = {
    Toast({ content = "收到新消息" })
  }
})
```

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `event` | string | 事件名，见下表 |
| `pages` | array | 限定生效的 Activity 类名；**留空或省略表示应用级全局事件** |
| `onEvent` | table | 事件触发时依次执行的动作列表 |

`pages` 为空时，该规则进入全局规则表，由应用级事件触发；`pages` 非空时，只有对应页面的事件才会命中。

### 5.2 事件一览

**应用级事件**（`pages` 留空）

| 事件名 | 触发时机 | 可用字段 |
| --- | --- | --- |
| `app.foreground` | 应用回到前台 | `time.*`、`account.*`、`network.online` |
| `network.online` | 网络恢复可用 | `network.online` |
| `network.offline` | 网络断开 | `network.online` |
| `ws.connected` | WebSocket 建立连接 | `ws.connected` |
| `ws.disconnected` | WebSocket 断开 | `ws.connected` |
| `ws.message` | 收到新消息 | `msgId`、`chatId`、`chatType`、`senderId`、`senderName`、`content`、`contentType`、`timestamp` |
| `ws.messageRecall` | 消息被撤回 | `msgId`、`chatId`、`senderId`、`senderName` |
| `ws.messageEdit` | 消息被编辑 | `msgId`、`chatId`、`content` |
| `account.changed` | 切换账号 / 登录状态变化 | `accountId`、`username`、`avatar` |

**页面级事件**（由 Activity 生命周期触发）

| 事件名 | 对应生命周期 |
| --- | --- |
| `created` | `onCreate` |
| `started` | `onStart` |
| `resumed` | `onResume` |
| `paused` | `onPause` |
| `stopped` | `onStop` |
| `destroyed` | `onDestroy` |

`GateActivity`（应用锁页面）不派发页面事件。

### 5.3 事件字段的读取方式

事件字段在插值中写作 `{{extra.字段名}}`：

```lua
onEvent({
  event = "ws.message",
  onEvent = {
    Notify({
      title = "来自 {{extra.senderName}}",
      content = "{{extra.content}}"
    })
  }
})
```

在 `If` 条件中则写作 `extra.字段名`（不带花括号）：

```lua
If({ field = "extra.contentType", equals = "1", then = { ... } })
```

---

## 6. 上下文变量

宿主在每次派发事件、每次打开插件页面时都会构建一份环境变量表，可在插值中直接引用。

| 变量 | 说明 | 示例 |
| --- | --- | --- |
| `{{account.id}}` | 当前账号 ID | `10086` |
| `{{account.username}}` | 当前账号用户名 | `小明` |
| `{{account.avatar}}` | 当前账号头像地址 | `https://...` |
| `{{account.loggedIn}}` | 是否已登录 | `true` |
| `{{app.version}}` | 应用版本名 | `1.0.2` |
| `{{app.versionCode}}` | 应用版本号 | `10200` |
| `{{app.package}}` | 应用包名 | `com.juhao.murexide` |
| `{{time.now}}` | 当前时间戳（毫秒） | `1795176868000` |
| `{{time.date}}` | 当前日期 | `2026-11-20` |
| `{{time.time}}` | 当前时间 | `12:01:13` |
| `{{time.datetime}}` | 当前日期时间 | `2026-11-20 12:01:13` |
| `{{network.online}}` | 网络是否可用 | `true` |
| `{{ws.connected}}` | WebSocket 是否连接 | `false` |
| `{{plugin.id}}` | 当前插件 ID | `hello_plugin` |
| `{{plugin.name}}` | 插件名称 | `你好插件` |
| `{{plugin.author}}` | 插件作者 | `你的名字` |
| `{{plugin.version}}` | 插件版本 | `1` |
| `{{page}}` | 当前页面类名 | `...ChatActivity` |
| `{{event}}` | 当前事件名 | `ws.message` |
| `{{extra.xxx}}` | 事件携带的自定义字段 | — |
| `{{var.xxx}}` | 运行时变量，由 `readData` / `Http` 写入 | — |

用户对象缺失时（未登录），`account.*` 会保持 `{{...}}` 原样输出，不会抛错。

---

## 7. 宿主函数

以下函数在插件脚本中**全局可用**，直接以函数形式调用。它们返回的是「动作对象」，需要放进 `onEvent` 或 `onClick` 的动作列表里才会执行。

### 7.1 Toast — 轻提示

```lua
Toast({ content = "已复制", type = "short" })
```

| 参数 | 说明 |
| --- | --- |
| `content` | 提示文本，支持插值 |
| `type` | `short`（默认）或 `long` |

### 7.2 Alert — 对话框

```lua
Alert({
  title = "确认",
  message = "确定要清空记录吗？",
  confirmText = "清空",
  cancelText = "取消",
  onConfirm = {
    writeData({ name = "history", value = "" }),
    Toast({ content = "已清空" })
  }
})
```

| 参数 | 说明 |
| --- | --- |
| `title` | 标题，为空时显示「提示」 |
| `message` | 正文 |
| `confirmText` | 确认按钮文字，默认「确定」 |
| `cancelText` | 取消按钮文字，默认「取消」 |
| `onConfirm` | 点击确认后执行的动作列表；点取消不执行任何动作 |

### 7.3 Notify — 通知

```lua
Notify({ title = "提醒", content = "有 {{extra.senderName}} 的新消息" })
```

需要 `notification` 权限。通知会自动归入「插件通知」渠道，点击后自动清除。

### 7.4 Copy — 写入剪贴板

```lua
Copy({ content = "{{account.username}}" })
```

需要 `clipboard` 权限。

### 7.5 Vibrate — 震动

```lua
Vibrate({ duration = 300 })
```

| 参数 | 说明 |
| --- | --- |
| `duration` | 震动时长（毫秒），默认 `300`，范围 50–5000 |

需要 `vibrate` 权限。设备不支持震动时静默忽略。

### 7.6 OpenUrl — 打开链接

```lua
OpenUrl({ url = "https://github.com/murexide-project/Murexide" })
```

需要 `openUrl` 权限。以 `ACTION_VIEW` 交给系统处理，可打开网页或唤起其他应用。

### 7.7 writeData — 写入持久化数据

```lua
writeData({ name = "last_seen", value = "{{time.datetime}}" })
```

| 参数 | 说明 |
| --- | --- |
| `name` | 键名，作用域为当前插件 |
| `value` | 值，必须是字符串，支持插值 |

需要 `storage` 权限。数据保存在**插件目录下的 `data.json`** 文件中，卸载插件时随目录一并删除。写入后会**立即同步到运行时变量** `var.<name>`，插件页面可直接用 `{{var.<name>}}` 显示，无需先 `readData`。

### 7.8 readData — 读取持久化数据

```lua
readData({ name = "last_seen", model = "var.last_seen" })
```

| 参数 | 说明 |
| --- | --- |
| `name` | 键名 |
| `model` | 写入的目标变量名，默认 `var.<name>` |

需要 `storage` 权限。读取结果写入运行时变量表，之后可用 `{{var.last_seen}}` 或 `var.last_seen` 引用。

> 读取是**异步**的：`readData` 执行完的同一批动作里，`{{var.xxx}}` 还是旧值。需要立即使用结果时，把它放在后续事件里消费。

### 7.9 Http — 网络请求

```lua
Http({
  method = "POST",
  url = "https://httpbin.org/post",
  body = "{\"user\":\"{{account.username}}\"}",
  headers = { ["Content-Type"] = "application/json" },
  saveTo = "resp"
})
```

| 参数 | 说明 |
| --- | --- |
| `method` | 请求方法，默认 `GET` |
| `url` | 请求地址，支持插值 |
| `body` | 请求体，`GET` 时忽略 |
| `headers` | 请求头表 |
| `saveTo` | 响应体保存到的变量名，默认写入 `var.<saveTo>` |

需要 `network` 权限。

> 请求在 IO 线程异步发出，**不会阻塞**后续动作。因此必须等响应回来才能读取 `{{var.resp}}`，例如在后续事件中读取。所有请求体按 `application/json` 编码。

### 7.10 If — 条件分支

```lua
If({
  field = "extra.contentType",
  equals = "1",
  ["then"] = { Copy({ content = "{{extra.content}}" }) },
  ["else"] = { Toast({ content = "非文本消息" }) }
})
```

| 参数 | 说明 |
| --- | --- |
| `field` | 被比较的环境变量名，如 `extra.senderName`、`var.state` |
| `equals` | 期望的值，支持插值，比较为**字符串全等** |
| `then` | 条件成立时执行的动作（Lua 保留字，必须写作 `["then"]`） |
| `else` | 条件不成立时执行的动作（Lua 保留字，必须写作 `["else"]`） |

比较双方都会转成字符串，所以布尔值要写 `"true"`、数字要写 `"1"`，注意不要带多余空格。

### 7.11 OpenChat — 打开会话

```lua
OpenChat({ chatId = "123", chatType = 2, name = "群名", avatar = "https://..." })
```

| 参数 | 说明 |
| --- | --- |
| `chatId` | 会话 ID |
| `chatType` | `1` 私聊，`2` 群聊，默认 `2` |
| `name` | 会话名称 |
| `avatar` | 会话头像 |

需要 `openChat` 权限。后台触发也能安全跳转。

### 7.12 StartActivity — 跳转任意页面

```lua
StartActivity({ activity = "com.juhao.murexide.ui.about.AboutActivity" })

StartActivity({
  activity = "com.juhao.murexide.ui.chat.ChatActivity",
  extras = { chat_id = "123", chat_type = "2" }
})
```

| 参数 | 说明 |
| --- | --- |
| `activity` | Activity 完整类名（含包名），必填 |
| `extras` | Intent 附加参数表。字符串值支持插值；整数按数字（Int/Long）传递，小数按 Double 传递 |

需要 `startActivity` 权限。类名不存在或启动失败时静默忽略（动作返回失败不影响其他动作）。

### 7.13 RestartApp — 重启应用

```lua
RestartApp()
```

需要 `restartApp` 权限。通过启动页 Intent 拉起应用后立即结束当前进程完成重启，未保存的数据会丢失。无参数。

### 7.14 Log — 写入插件日志

```lua
Log({ level = "warn", content = "发生了一点小问题" })
```

| 参数 | 说明 |
| --- | --- |
| `level` | `info` / `warn` / `error`，默认 `info` |
| `content` | 日志内容 |

需要 `log` 权限。日志可在插件管理页的「日志」中查看。

### 7.15 Share — 系统分享

```lua
Share({ content = "分享内容 {{time.datetime}}" })
```

需要 `share` 权限。调用系统分享面板，`content` 支持插值。

---

## 8. 界面组件

`registerPage` 的 `page` 与 `decorate` 的 `content` 都由组件树构成。

### 8.1 注册页面

```lua
registerPage({
  id = "my_page",
  icon = "Settings",
  title = "我的功能",
  subtitle = "副标题可选",
  page = Column({ ... })
})
```

| 参数 | 说明 |
| --- | --- |
| `id` | 页面标识，建议全局唯一 |
| `icon` | 图标名，见 8.4；未知名称回退为 `Build` |
| `title` | 设置页中显示的标题 |
| `subtitle` | 副标题，可为空 |
| `page` | 根组件，省略时渲染为空 `Column` |

注册后的入口出现在 **设置 → 额外功能**。

### 8.2 组件一览

| 函数 | 说明 |
| --- | --- |
| `Column({ children = {...} })` | 纵向排列 |
| `Row({ children = {...} })` | 横向排列 |
| `Text({ content = "..." })` | 文本 |
| `Button({ content = "...", onClick = {...} })` | 按钮 |
| `Spacer({ size = 8 })` | 空白间距，单位 dp |
| `Image({ src = "..." })` | 图片 |
| `Card` | 卡片容器（仅 `type = "card"` 可识别） |
| `Divider` | 分隔线（仅 `type = "divider"` 可识别） |

### 8.3 通用属性

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `width` | string / number | `match`、`fill` 占满父容器；`wrap` 包裹内容；数字则固定宽度 dp |
| `height` | string / number | 同上 |
| `padding` | number | 四边内边距 dp |
| `paddingH` | number | 水平内边距 dp |
| `paddingV` | number | 垂直内边距 dp |
| `align` | string | `start`、`center`、`end`、`spaceBetween`、`spaceAround`、`top`、`bottom` |
| `size` | number | 用于 `Spacer` 高度、`Image` / 图标边长 |
| `color` | string | 文字或图标颜色，十六进制如 `#888888` |
| `background` | string | 背景色，仅 `Column` 生效 |
| `content` | string | 文本 / 按钮文字 / 图片地址 |
| `icon` | string | 图标名，用于 `type = "icon"` |
| `children` | array | 子组件列表 |
| `onClick` | array | 点击动作列表，仅 `Button` 生效 |

`Column` 子元素间距固定 8dp；未知的 `type` 会渲染成 `[未知类型: xxx]` 文本。

### 8.4 图标名

可用名称：`Info`、`SmartToy`、`Person`、`Link`、`Image`、`Settings`、`Code`、`Warning`、`People`、`Build`。其他名称一律回退为 `Build`。

### 8.5 图片

```lua
Image({ src = "https://example.com/a.png", size = 64 })
Image({ src = "assets/logo.png", size = 64 })
```

- `http://`、`https://` 开头：走网络加载
- `assets/` 开头：读取插件目录内的文件
- 其他写法不会渲染

---

## 9. 悬浮组件 decorate

在页面底部或顶部叠加一条常驻控件：

```lua
decorate({
  page = "com.juhao.murexide.ui.conversationdetail.ConversationDetailActivity",
  position = "bottom",
  when = { field = "extra.chatId", equals = "123456" },
  content = Row({
    width = "match",
    align = "center",
    paddingH = 12,
    paddingV = 6,
    children = {
      Button({ content = "复制时间", onClick = { Copy({ content = "{{time.datetime}}" }) } })
    }
  })
})
```

| 参数 | 说明 |
| --- | --- |
| `page` | 目标页面类名，`*` 表示所有页面 |
| `position` | `top` 或 `bottom`，默认 `bottom` |
| `when` | 可选条件；省略则始终显示 |
| `when.field` | 参与比较的变量名 |
| `when.equals` | 期望值；省略时只要该变量非空即显示 |
| `content` | 组件树 |

悬浮层覆盖在整个 Activity 之上，不拦截触摸时会自动让出事件。它随页面事件重新求值，因此条件变化要等到下一次事件派发才刷新。

---

## 10. 字符串插值

任何带字符串参数的地方都支持 `{{ 变量 }}` 插值，变量名可含点号，花括号内两侧空格可有可无。

```lua
Notify({
  title = "{{account.username}} 的在线提醒",
  content = "现在是 {{time.datetime}}，网络：{{network.online}}"
})
```

规则：

1. 变量存在则替换为字符串形式
2. 变量不存在则**原样保留** `{{xxx}}`，便于发现拼写错误
3. `var.xxx` 会先查当前环境，再查插件运行时变量表
4. 不支持表达式、运算或函数调用，只做纯文本替换

---

## 11. 存储与日志

### 11.1 存储

`writeData` / `readData` 以「插件 ID + 键名」为作用域，插件之间互不可见。值一律为字符串，需要存结构时自行拼接：

```lua
writeData({ name = "rec", value = "{{extra.chatId}}|{{extra.senderName}}|{{time.now}}" })
```

### 11.2 日志

插件加载失败、权限被拒、动作异常都会写入插件目录下的 `logs.txt`，并在超过 256 KB 时自动清空。

在 **插件管理页 → 日志** 可查看与清空。排查问题的第一步永远是看日志。

---

## 12. 完整示例：关键词提醒

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

`Global.lua`

```lua
onEvent({
  event = "ws.message",
  onEvent = {
    If({
      field = "extra.contentType",
      equals = "1",
      then = {
        If({
          field = "extra.senderName",
          equals = "机器人",
          then = {
            writeData({ name = "bot_msg", value = "{{extra.content}}" })
          },
          ["else"] = {
            Notify({
              title = "新消息 · {{extra.senderName}}",
              content = "{{extra.content}}"
            }),
            writeData({ name = "last_msg", value = "{{extra.content}}" })
          }
        })
      }
    })
  }
})
```

`PageRegister.lua`

```lua
registerPage({
  id = "keyword_alert_page",
  icon = "Notifications",
  title = "关键词提醒",
  subtitle = "查看最近一条提醒",
  page = Column({
    width = "match",
    children = {
      Text({ content = "最近消息：{{var.last_msg}}", size = 14 }),
      Spacer({ size = 8 }),
      Button({
        content = "刷新",
        width = "match",
        onClick = { readData({ name = "last_msg", model = "var.last_msg" }) }
      }),
      Button({
        content = "复制账号",
        width = "match",
        onClick = { Copy({ content = "{{account.username}}" }) }
      })
    }
  })
})
```

---

## 13. 常见问题

**安装提示「无效插件包」**
`manifest.json` 不在 zip 根目录。重新选中文件夹内的文件再压缩。

**安装提示「插件缺 id」**
`manifest.json` 中缺少 `id` 字段，或 JSON 语法有误。

**插件列表里没有我的插件**
检查目录下是否有至少一个 `.lua` 文件，且文件名与第 2 节的固定名称一致。

**有入口但页面空白**
`registerPage` 的 `page` 为空，或根组件不是已知类型。先用 `Column({ children = { Text({ content = "hi" }) } })` 验证链路。

**Toast 弹了但文案里带着 `{{extra.xxx}}`**
该事件没有携带这个字段。对照第 5.2 节的字段表确认拼写。

**`readData` 之后 `{{var.xxx}}` 还是旧值**
读取是异步的，同一批动作内拿不到新值。改为在后续事件中引用，或给页面加一个「刷新」按钮。

**`Http` 之后读不到 `{{var.resp}}`**
同理，请求异步发出。等响应回来后再触发读取。

**权限被拒**
日志里有 `permission denied` 记录。在 `manifest.json` 的 `permissions` 中补上对应权限，重新打包安装。

**`If` 分支总是不走 then**
比较是字符串全等。确认 `fields` 拼写、值不带空格，布尔写 `"true"`、数字写 `"1"`。

**`else` 语法报错**
`else` 是 Lua 保留字，必须写成 `["else"] = { ... }`。

**`decorate` 的 `when` 条件不生效**
悬浮层在事件派发时求值，条件据当前事件字段判断。若字段不在该页面事件里，条件不会成立。

---

## 14. 安全与边界

- 插件跑在应用进程内，可访问的能力**仅限**第 4 节列出的权限
- 没有文件系统、进程、系统命令、`os.*` 库的访问能力
- 网络请求走应用已有的 OkHttp 客户端，不带任何登录凭据
- 安装包限制：单文件与总量 32 MB 以内，文件数 2048 以内，非法路径会被拒绝
- 卸载插件会删除插件目录，但 DataStore 中的键值会保留
- 仅安装来源可信的插件。`all` 权限等同于把上述能力全部交出

---

## 15. 速查表

```lua
Toast({ content = "...", type = "long" })
Alert({ title = "...", message = "...", confirmText = "...", cancelText = "...", onConfirm = {...} })
Notify({ title = "...", content = "..." })
Copy({ content = "..." })
OpenUrl({ url = "https://..." })
writeData({ name = "...", value = "..." })
readData({ name = "...", model = "var...." })
Http({ method = "GET", url = "...", body = "...", headers = {...}, saveTo = "..." })
If({ field = "...", equals = "...", then = {...}, ["else"] = {...} })

Column({ children = {...} })
Row({ children = {...} })
Text({ content = "...", size = 14, color = "#888888" })
Button({ content = "...", onClick = {...} })
Spacer({ size = 8 })
Image({ src = "...", size = 64 })

registerPage({ id = "...", icon = "...", title = "...", subtitle = "...", page = {...} })
onEvent({ event = "...", pages = {...}, onEvent = {...} })
decorate({ page = "...", position = "bottom", when = {...}, content = {...} })
```
""".trimIndent()
}
