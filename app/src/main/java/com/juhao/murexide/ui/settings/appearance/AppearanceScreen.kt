package com.juhao.murexide.ui.settings.appearance

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.ThemeState
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.MessageTag
import com.juhao.murexide.ui.chat.components.MessageBubble
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
        
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    val themeMode by ThemeState.themeMode
    val themeStyle by ThemeState.themeStyle
    val themeColor by ThemeState.themeColor
    
    val scrollBehavior = if (themeStyle == "md3") TopAppBarDefaults.pinnedScrollBehavior()
        else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val squareAvatar by ThemeState.squareAvatar
    var showSticky by remember { mutableStateOf(true) }
    
    var bubbleCornerRadius by remember { mutableFloatStateOf(16f) }
    val showMyBubbleAvatar by settingsStorage.showMyBubbleAvatarFlow.collectAsState(initial = true)
    val showMsgTags by settingsStorage.showMsgTagsFlow.collectAsState(initial = true)
    var bubbleOpacity by remember { mutableFloatStateOf(0.9f) }
    var backgroundOpacity by remember { mutableFloatStateOf(0.5f) }

    LaunchedEffect(Unit) {
        ThemeState.squareAvatar.value = settingsStorage.getSquareAvatar()
        showSticky = settingsStorage.getShowSticky()
        bubbleCornerRadius = settingsStorage.getBubbleCornerRadius()
        bubbleOpacity = settingsStorage.getBubbleOpacity()
        backgroundOpacity = settingsStorage.getBackgroundOpacity()
    }

    val previewMessages = remember {
        listOf(
            MessageItem(
                msgId = "preview_other",
                senderId = "other",
                senderName = "那狗吧",
                senderAvatar = "https://chat-img.jwznb.com/defalut-avatars/Nellie%20Bly.png",
                content = "你好！",
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                timestamp = System.currentTimeMillis() - 60000,
                direction = "left",
                tags = listOf(
                    MessageTag(
                        id = 0,
                        text = "化学式",
                        color = "#66CCFF"
                    )
                )
            ),
            MessageItem(
                msgId = "preview_other",
                senderId = "other",
                senderName = "那狗吧",
                senderAvatar = "https://chat-img.jwznb.com/defalut-avatars/Nellie%20Bly.png",
                content = "看看这个气泡效果怎么样？",
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                timestamp = System.currentTimeMillis() - 60000,
                direction = "left"
            ),
            MessageItem(
                msgId = "preview_me",
                senderId = "me",
                senderName = "我",
                senderAvatar = "https://chat-img.jwznb.com/defalut-avatars/Mary%20Roebling.png",
                content = "效果不错！可以调整圆角和透明度",
                contentType = MessageItem.CONTENT_TYPE_TEXT,
                timestamp = System.currentTimeMillis(),
                direction = "right"
            )
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (themeStyle == "md3") {
                TopAppBar(
                    title = { Text("外观设置") },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        StyledIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            } else {
                LargeTopAppBar(
                    title = { Text("外观设置") },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        StyledIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // 主题设置
            SettingsGroup(title = "主题") {
                SettingsDropdownItem(
                    icon = Icons.Rounded.WbSunny,
                    title = "主题模式",
                    subtitle = when (themeMode) {
                        "dark" -> "深色模式"
                        "light" -> "浅色模式"
                        "oled" -> "纯黑模式（禁用动态取色）"
                        else -> "跟随系统"
                    },
                    options = listOf(
                        "system" to "跟随系统",
                        "light" to "浅色模式",
                        "dark" to "深色模式",
                        "oled" to "纯黑模式（禁用动态取色）"
                    ),
                    selectedValue = themeMode,
                    onOptionSelected = { selected ->
                        ThemeState.themeMode.value = selected
                        scope.launch {
                            settingsStorage.setThemeMode(selected)
                        }
                    }
                )
                SettingsDropdownItem(
                    icon = Icons.Rounded.Style,
                    title = "主题样式",
                    subtitle = when (themeStyle) {
                        "md3e" -> "Material 3 Expressive"
                        else -> "Material 3"
                    },
                    options = listOf(
                        "md3e" to "Material 3 Expressive",
                        "md3" to "Material 3"
                    ),
                    selectedValue = themeStyle,
                    onOptionSelected = { selected ->
                        ThemeState.themeStyle.value = selected
                        scope.launch {
                            settingsStorage.setThemeStyle(selected)
                        }
                    }
                )
                SettingsDropdownItem(
                    icon = Icons.Rounded.Draw,
                    title = "主题颜色",
                    subtitle = when (themeColor) {
                        "PURPLE" -> "紫色"
                        "BLUE" -> "蓝色"
                        "GREEN" -> "绿色"
                        "ORANGE" -> "橙色"
                        else -> "动态取色"
                    },
                    options = listOf(
                        "DYNAMIC" to "动态取色",
                        "PURPLE" to "紫色",
                        "BLUE" to "蓝色",
                        "GREEN" to "绿色",
                        "ORANGE" to "橙色"
                    ),
                    selectedValue = themeMode,
                    onOptionSelected = { selected ->
                        ThemeState.themeColor.value = selected
                        scope.launch {
                            settingsStorage.setThemeColor(selected)
                        }
                    }
                )
            }
            
            // 气泡预览区域
            SettingsGroup(title = "消息气泡预览") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MessageBubble(
                        message = previewMessages[0],
                        isLastFromSender = true,
                        isFirstFromSender = false,
                        showAvatar = false,
                        showTags = showMsgTags,
                        showMyBubbleAvatarSetting = showMyBubbleAvatar,
                        bubbleOpacity = bubbleOpacity,
                        bubbleCornerRadius = bubbleCornerRadius
                    )
                    
                    MessageBubble(
                        message = previewMessages[1],
                        isLastFromSender = false,
                        isFirstFromSender = true,
                        showAvatar = true,
                        showMyBubbleAvatarSetting = showMyBubbleAvatar,
                        bubbleOpacity = bubbleOpacity,
                        bubbleCornerRadius = bubbleCornerRadius
                    )
                    
                    MessageBubble(
                        message = previewMessages[2],
                        isLastFromSender = true,
                        isFirstFromSender = true,
                        showAvatar = showMyBubbleAvatar,
                        showMyBubbleAvatarSetting = showMyBubbleAvatar,
                        bubbleOpacity = bubbleOpacity,
                        bubbleCornerRadius = bubbleCornerRadius
                    )
                }
            }

            SettingsGroup(title = "气泡样式") {
                CustomItemCell {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.RoundedCorner,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "气泡圆角",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${bubbleCornerRadius.toInt()}dp",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Slider(
                            value = bubbleCornerRadius,
                            onValueChange = { bubbleCornerRadius = it },
                            onValueChangeFinished = {
                                scope.launch {
                                    settingsStorage.setBubbleCornerRadius(bubbleCornerRadius)
                                }
                            },
                            valueRange = 0f..24f,
                            steps = 23,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "0dp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "24dp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                CustomItemCell {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Opacity,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "气泡不透明度",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${(bubbleOpacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Slider(
                            value = bubbleOpacity,
                            onValueChange = { bubbleOpacity = it },
                            onValueChangeFinished = {
                                scope.launch {
                                    settingsStorage.setBubbleOpacity(bubbleOpacity)
                                }
                            },
                            valueRange = 0.4f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "40%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                CustomItemCell {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "背景不透明度",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${(backgroundOpacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Slider(
                            value = backgroundOpacity,
                            onValueChange = { backgroundOpacity = it },
                            onValueChangeFinished = {
                                scope.launch {
                                    settingsStorage.setBackgroundOpacity(backgroundOpacity)
                                }
                            },
                            valueRange = 0.2f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "20%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                SettingsSwitchItem(
                    icon = Icons.Rounded.Face,
                    title = "显示我的头像",
                    subtitle = "在我发送的消息气泡旁显示我的头像",
                    checked = showMyBubbleAvatar,
                    onCheckedChange = { checked ->
                        scope.launch {
                            settingsStorage.setShowMyBubbleAvatar(checked)
                        }
                    }
                )
                
                SettingsSwitchItem(
                    icon = Icons.Rounded.Tag,
                    title = "显示用户标签",
                    subtitle = "在发送者名称旁显示Ta的标签",
                    checked = showMsgTags,
                    onCheckedChange = { checked ->
                        scope.launch {
                            settingsStorage.setShowMsgTags(checked)
                        }
                    }
                )
            }

            SettingsGroup(title = "会话") {
                SettingsSwitchItem(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    title = "显示置顶会话",
                    subtitle = "在主页显示置顶会话",
                    checked = showSticky,
                    onCheckedChange = { checked ->
                        showSticky = checked
                        scope.launch {
                            settingsStorage.setShowSticky(checked)
                        }
                    }
                )
                SettingsSwitchItem(
                    icon = Icons.Rounded.People,
                    title = "圆角正方形头像",
                    subtitle = "将好友和群组头像显示为圆角正方形",
                    checked = squareAvatar,
                    onCheckedChange = { checked ->
                        ThemeState.squareAvatar.value = checked
                        scope.launch {
                            settingsStorage.setSquareAvatar(checked)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
