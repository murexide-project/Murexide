package com.juhao.murexide.ui.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.juhao.murexide.R
import com.juhao.murexide.utils.MentionUtils

@Composable
fun MessageInput(
    inputText: String,
    sendType: String,
    isSending: Boolean = false,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onAddVideoClick: () -> Unit,
    onAddFileClick: () -> Unit,
    onToggleSendType: (String) -> Unit,
    isEmojiPanelVisible: Boolean = false,
    onEmojiClick: () -> Unit,
    isInstructionPanelVisible: Boolean = false,
    onInstructionClick: () -> Unit = {},
    mentionNames: Collection<String> = emptyList(),
    onMentionTriggered: (Int) -> Unit = {},
    focusRequester: FocusRequester,
    onInputFocused: () -> Unit = {}
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(inputText)) }
    LaunchedEffect(inputText) {
        if (fieldValue.text != inputText) {
            fieldValue = TextFieldValue(inputText, TextRange(inputText.length))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MoreActionsButton(
            sendType = sendType,
            onAddImageClick = onAddImageClick,
            onAddVideoClick = onAddVideoClick,
            onAddFileClick = onAddFileClick,
            onToggleSendType = onToggleSendType
        )
        
        IconButton(
            onClick = onEmojiClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = if (isEmojiPanelVisible) {
                    Icons.Rounded.Keyboard
                } else {
                    Icons.Rounded.Mood
                },
                contentDescription = if (isEmojiPanelVisible) "切换到键盘" else "表情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                val result = MentionUtils.processEdit(fieldValue, new, mentionNames)
                fieldValue = result.value
                if (result.value.text != inputText) {
                    onTextChange(result.value.text)
                }
                if (result.insertedText == "@") {
                    onMentionTriggered(result.insertPos)
                }
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp, max = 144.dp)
                .padding(horizontal = 4.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) onInputFocused()
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            minLines = 1,
            maxLines = 5,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (fieldValue.text.isEmpty()) {
                        Text(
                            text = "输入消息...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            }
        )

        AnimatedContent(
            targetState = inputText.isNotBlank(),
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(200))
            },
            label = "bottom_bar_button_transition"
        ) { isNotBlank ->
            if (isNotBlank) {
                IconButton(
                    onClick = onSendClick,
                    enabled = !isSending,
                    modifier = Modifier
                        .size(44.dp)
                        .focusProperties { canFocus = false }
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .focusProperties { canFocus = false },
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "发送",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onInstructionClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isInstructionPanelVisible) {
                            Icons.Rounded.Keyboard
                        } else {
                            Icons.Rounded.Code
                        },
                        contentDescription = if (isInstructionPanelVisible) "切换到键盘" else "指令",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreActionsButton(
    sendType: String,
    onAddImageClick: () -> Unit,
    onAddVideoClick: () -> Unit,
    onAddFileClick: () -> Unit,
    onToggleSendType: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("图片") },
                onClick = {
                    showMenu = false
                    onAddImageClick()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("视频") },
                onClick = {
                    showMenu = false
                    onAddVideoClick()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Movie, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("文件") },
                onClick = {
                    showMenu = false
                    onAddFileClick()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.AttachFile, contentDescription = null)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            DropdownMenuItem(
                text = { Text("文本") },
                onClick = {
                    showMenu = false
                    onToggleSendType("text")
                },
                leadingIcon = {
                    Icon(Icons.Rounded.TextFields, contentDescription = null)
                },
                trailingIcon = {
                    if (sendType == "text") {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "已选择",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Markdown") },
                onClick = {
                    showMenu = false
                    onToggleSendType("markdown")
                },
                leadingIcon = {
                    Icon(painterResource(R.drawable.markdown), contentDescription = null)
                },
                trailingIcon = {
                    if (sendType == "markdown") {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "已选择",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("HTML") },
                onClick = {
                    showMenu = false
                    onToggleSendType("html")
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Code, contentDescription = null)
                },
                trailingIcon = {
                    if (sendType == "html") {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "已选择",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    }
}
