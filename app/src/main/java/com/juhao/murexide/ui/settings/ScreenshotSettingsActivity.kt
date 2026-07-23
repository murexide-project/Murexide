package com.juhao.murexide.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.theme.UiState
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class ScreenshotSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MurexideTheme {
                ScreenshotPrivacyScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotPrivacyScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()    
    val state = rememberScrollState()

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("截图设置") },
                navigationIcon = {
                    StyledIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(state)
        ) {
            SettingsGroup(title = "隐私选项") {
                SettingsSwitchItem(
                    icon = Icons.Rounded.Person,
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
                    icon = Icons.Rounded.PersonOutline,
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
                    icon = Icons.Rounded.ChatBubble,
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
                    icon = Icons.Rounded.Image,
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}