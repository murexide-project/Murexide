package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun ScreenshotControl(
    highlightId: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
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

    val allIds = listOf("header_screenshot", "screenshot_sender", "screenshot_my_info", "screenshot_session", "screenshot_images")
    val listState = rememberLazyListState()
    LaunchedEffect(highlightId, allIds) {
        if (!highlightId.isNullOrBlank()) {
            val index = allIds.indexOf(highlightId)
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        )
    ) {
        item { SettingsGroupHeader("隐私") }
        item {
            SettingsGroupSurface(isTop = true, isBottom = false, id = "screenshot_sender", highlightId = highlightId) {
                SettingsSwitchItem(
                    icon = AppIcons.Person,
                    title = "隐藏发送者信息",
                    subtitle = "隐藏消息发送者的名称和头像",
                    checked = hideSenderInfo,
                    onCheckedChange = { checked ->
                        hideSenderInfo = checked
                        scope.launch { settingsStorage.setScreenshotHideSenderInfo(checked) }
                    }
                )
            }
        }
        item {
            SettingsGroupSurface(isTop = false, isBottom = false, id = "screenshot_my_info", highlightId = highlightId) {
                SettingsSwitchItem(
                    icon = AppIcons.PersonOutline,
                    title = "信息发送方匿名化",
                    subtitle = "我的信息也显示为对方",
                    checked = hideMyInfo,
                    onCheckedChange = { checked ->
                        hideMyInfo = checked
                        scope.launch { settingsStorage.setScreenshotHideMyInfo(checked) }
                    }
                )
            }
        }
        item {
            SettingsGroupSurface(isTop = false, isBottom = false, id = "screenshot_session", highlightId = highlightId) {
                SettingsSwitchItem(
                    icon = AppIcons.ChatBubble,
                    title = "隐藏会话信息",
                    subtitle = "隐藏会话名称和会话头像",
                    checked = hideSessionInfo,
                    onCheckedChange = { checked ->
                        hideSessionInfo = checked
                        scope.launch { settingsStorage.setScreenshotHideSessionInfo(checked) }
                    }
                )
            }
        }
        item {
            SettingsGroupSurface(isTop = false, isBottom = true, id = "screenshot_images", highlightId = highlightId) {
                SettingsSwitchItem(
                    icon = AppIcons.Image,
                    title = "隐藏图片及表情包",
                    subtitle = "截图中的图片和表情包将被遮挡",
                    checked = hideImages,
                    onCheckedChange = { checked ->
                        hideImages = checked
                        scope.launch { settingsStorage.setScreenshotHideImages(checked) }
                    }
                )
            }
        }
    }
}