package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.UiState
import com.juhao.murexide.ui.theme.liquidglass.LiquidGlassSlider
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.MessageTag
import com.juhao.murexide.ui.chat.components.MessageBubble
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubblePage() {        
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    val themeMode by UiState.themeMode
    val themeColor by UiState.themeColor
    
    val squareAvatar by UiState.squareAvatar
    
    var bubbleCornerRadius by remember { mutableFloatStateOf(16f) }
    val showMyBubbleAvatar by settingsStorage.showMyBubbleAvatarFlow.collectAsState(initial = true)
    val showMsgTags by settingsStorage.showMsgTagsFlow.collectAsState(initial = true)
    var bubbleOpacity by remember { mutableFloatStateOf(0.9f) }
    
    LaunchedEffect(Unit) {
        bubbleCornerRadius = settingsStorage.getBubbleCornerRadius()
        bubbleOpacity = settingsStorage.getBubbleOpacity()
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
    
    // 气泡预览区域
    SettingsGroup() {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
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
                            AppIcons.RoundedCorner,
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
                
                LiquidGlassSlider(
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
                            AppIcons.Opacity,
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
                
                LiquidGlassSlider(
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
        
        SettingsSwitchItem(
            icon = AppIcons.Face,
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
            icon = AppIcons.Tag,
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
}