package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun ScreenshotPage() {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    var hideSenderInfo by remember { mutableStateOf(false) }
    var hideMyInfo by remember { mutableStateOf(false) }
    var hideSessionInfo by remember { mutableStateOf(false) }
    var hideImages by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hideSenderInfo = settingsStorage.getScreenshotHideSenderInfo()
        hideMyInfo = settingsStorage.getScreenshotHideMyInfo()
        hideSessionInfo = settingsStorage.getScreenshotHideSessionInfo()
        hideImages = settingsStorage.getScreenshotHideImages()
    }

    SettingsGroup(title = "隐私") {
        SettingsSwitchItem(
            icon = AppIcons.Person,
            title = "隐藏发送者信息",
            subtitle = "隐藏消息发送者的名称和头像",
            checked = hideSenderInfo,
            onCheckedChange = { checked ->
                hideSenderInfo = checked
                scope.launch {
                    settingsStorage.setScreenshotHideSenderInfo(checked)
                }
            }
        )

        SettingsSwitchItem(
            icon = AppIcons.PersonOutline,
            title = "信息发送方匿名化",
            subtitle = "我的信息也显示为对方",
            checked = hideMyInfo,
            onCheckedChange = { checked ->
                hideMyInfo = checked
                scope.launch {
                    settingsStorage.setScreenshotHideMyInfo(checked)
                }
            }
        )

        SettingsSwitchItem(
            icon = AppIcons.ChatBubble,
            title = "隐藏会话信息",
            subtitle = "隐藏会话名称和会话头像",
            checked = hideSessionInfo,
            onCheckedChange = { checked ->
                hideSessionInfo = checked
                scope.launch {
                    settingsStorage.setScreenshotHideSessionInfo(checked)
                }
            }
        )

        SettingsSwitchItem(
            icon = AppIcons.Image,
            title = "隐藏图片及表情包",
            subtitle = "截图中的图片和表情包将被遮挡",
            checked = hideImages,
            onCheckedChange = { checked ->
                hideImages = checked
                scope.launch {
                    settingsStorage.setScreenshotHideImages(checked)
                }
            }
        )
    }
}