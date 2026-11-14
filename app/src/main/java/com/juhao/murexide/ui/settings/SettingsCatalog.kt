package com.juhao.murexide.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.juhao.murexide.ui.icons.AppIcons

@Immutable
data class SettingsItemSpec(
    val id: String,
    val page: Int,
    val group: String,
    val title: String,
    val icon: ImageVector
)

val SettingsPageTitles: Map<Int, String> = mapOf(
    1 to "外观设置",
    2 to "消息气泡设置",
    3 to "行为设置",
    4 to "更新",
    5 to "WebSocket 设置",
    6 to "截图设置",
    7 to "账号设置"
)

val AllSettingsIndex: List<SettingsItemSpec> = listOf(
    SettingsItemSpec("appearance_theme_mode", 1, "外观", "主题模式", AppIcons.WbSunny),
    SettingsItemSpec("appearance_oled", 1, "外观", "纯黑模式", AppIcons.Contrast),
    SettingsItemSpec("appearance_theme_color", 1, "外观", "主题颜色", AppIcons.Draw),
    SettingsItemSpec("appearance_liquid_glass", 1, "外观", "液态玻璃", AppIcons.Opacity),
    SettingsItemSpec("appearance_liquid_glass_blur", 1, "外观", "液态玻璃模糊", AppIcons.Opacity),
    SettingsItemSpec("appearance_background", 1, "外观", "显示会话背景", AppIcons.Image),
    SettingsItemSpec("appearance_background_opacity", 1, "外观", "背景不透明度", AppIcons.Image),
    SettingsItemSpec("appearance_square_avatar", 1, "外观", "圆角矩形头像", AppIcons.People),
    SettingsItemSpec("appearance_float_bar", 1, "外观", "主页悬浮底栏", AppIcons.BottomBar),
    SettingsItemSpec("appearance_float_top_bar", 1, "外观", "悬浮顶栏", AppIcons.ArrowUpward),
    SettingsItemSpec("appearance_merge_sticky", 1, "外观", "合并置顶会话", AppIcons.Keep),

    SettingsItemSpec("bubble_corner", 2, "气泡", "气泡圆角", AppIcons.RoundedCorner),
    SettingsItemSpec("bubble_opacity", 2, "气泡", "气泡不透明度", AppIcons.Opacity),
    SettingsItemSpec("bubble_my_avatar", 2, "气泡", "显示我的头像", AppIcons.Face),
    SettingsItemSpec("bubble_tags", 2, "气泡", "显示用户标签", AppIcons.Tag),

    SettingsItemSpec("action_notification", 3, "行为", "消息通知", AppIcons.Notifications),
    SettingsItemSpec("action_cache", 3, "行为", "本地缓存", AppIcons.SaveAlt),
    SettingsItemSpec("action_storage", 3, "行为", "存储权限", AppIcons.FolderZip),
    SettingsItemSpec("action_big_screen", 3, "行为", "大屏模式", AppIcons.LaptopChromebook),
    SettingsItemSpec("action_enter_send", 3, "行为", "按回车键发送", AppIcons.Keyboard),
    SettingsItemSpec("action_app_lock", 3, "行为", "应用锁", AppIcons.Lock),
    SettingsItemSpec("action_lock_background", 3, "行为", "切到后台后重新锁定", AppIcons.Lock),
    SettingsItemSpec("action_change_pin", 3, "行为", "修改应用锁密码", AppIcons.Edit),
    SettingsItemSpec("action_screenshot", 3, "行为", "截图设置", AppIcons.Screenshot),

    SettingsItemSpec("update_channel", 4, "更新", "更新频道", AppIcons.List),
    SettingsItemSpec("update_qq", 4, "更新", "前往交流群更新", AppIcons.Download),

    SettingsItemSpec("ws_enable", 5, "WebSocket", "启用 WebSocket", AppIcons.Link),
    SettingsItemSpec("ws_battery", 5, "WebSocket", "忽略电池优化", AppIcons.BatteryPlus),
    SettingsItemSpec("ws_reconnect", 5, "WebSocket", "重启连接", AppIcons.Refresh),

    SettingsItemSpec("account_switch", 7, "账号", "切换账号", AppIcons.People),
    SettingsItemSpec("account_token", 7, "账号", "提取账号 Token", AppIcons.Key),
    SettingsItemSpec("account_logout", 7, "账号", "退出登录", AppIcons.Logout),

    SettingsItemSpec("screenshot_sender", 6, "截图", "隐藏发送者信息", AppIcons.Person),
    SettingsItemSpec("screenshot_my_info", 6, "截图", "信息发送方匿名化", AppIcons.PersonOutline),
    SettingsItemSpec("screenshot_session", 6, "截图", "隐藏会话信息", AppIcons.ChatBubble),
    SettingsItemSpec("screenshot_images", 6, "截图", "隐藏图片及表情包", AppIcons.Image)
)