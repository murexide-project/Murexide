package com.juhao.murexide.ui.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.juhao.murexide.R
import com.juhao.murexide.utils.MentionUtils
import kotlin.math.roundToInt

private val SendButtonSize = 44.dp
private val SendFormatOptionWidth = 76.dp
private val SendFormatPickerHeight = 48.dp
private val SendFormatPickerGap = 8.dp
private val SendCancelDragDistance = 128.dp

private data class SendFormatOption(
    val type: String,
    val label: String
)

// The text option stays nearest to the send button, which sits at the right edge of the screen.
private val SendFormatOptions = listOf(
    SendFormatOption(type = "html", label = "HTML"),
    SendFormatOption(type = "markdown", label = "Markdown"),
    SendFormatOption(type = "text", label = "文本")
)

internal fun sendFormatOptionIndex(
    horizontalDrag: Float,
    initialIndex: Int,
    optionWidth: Float,
    optionCount: Int
): Int {
    require(optionWidth > 0f)
    require(optionCount > 0)
    require(initialIndex in 0 until optionCount)

    return (initialIndex + (horizontalDrag / optionWidth).roundToInt())
        .coerceIn(0, optionCount - 1)
}

internal fun shouldCancelFormatSend(
    verticalDrag: Float,
    cancelDistance: Float
): Boolean {
    require(cancelDistance > 0f)
    return verticalDrag >= cancelDistance
}

@Composable
fun MessageInput(
    inputText: String,
    sendType: String,
    isSending: Boolean = false,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onSendWithType: (String) -> Unit,
    onAddImageClick: () -> Unit,
    onAddVideoClick: () -> Unit,
    onAddFileClick: () -> Unit,
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
            onAddImageClick = onAddImageClick,
            onAddVideoClick = onAddVideoClick,
            onAddFileClick = onAddFileClick
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
                FormatSendButton(
                    sendType = sendType,
                    enabled = !isSending,
                    isSending = isSending,
                    onSendClick = onSendClick,
                    onSendWithType = onSendWithType
                )
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
private fun FormatSendButton(
    sendType: String,
    enabled: Boolean,
    isSending: Boolean,
    onSendClick: () -> Unit,
    onSendWithType: (String) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var showFormatPicker by remember { mutableStateOf(false) }
    var selectedType by remember(sendType) { mutableStateOf(sendType) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var initialFormatIndex by remember { mutableStateOf(SendFormatOptions.lastIndex) }
    var cancelSend by remember { mutableStateOf(false) }

    val currentEnabled by rememberUpdatedState(enabled)
    val currentSendType by rememberUpdatedState(sendType)
    val currentOnSendClick by rememberUpdatedState(onSendClick)
    val currentOnSendWithType by rememberUpdatedState(onSendWithType)
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val optionWidthPx = with(density) { SendFormatOptionWidth.toPx() }
    val cancelDistancePx = with(density) { SendCancelDragDistance.toPx() }
    val pickerOffsetPx = with(density) {
        (SendFormatPickerHeight + SendFormatPickerGap).roundToPx()
    }

    Box(
        modifier = Modifier
            .size(SendButtonSize)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(SendButtonSize)
                .clip(CircleShape)
                .background(
                    if (enabled && (isPressed || showFormatPicker)) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                    } else {
                        Color.Transparent
                    }
                )
                .semantics {
                    role = Role.Button
                    contentDescription = "发送"
                    stateDescription = if (enabled) {
                        "长按并滑动可选择消息格式"
                    } else {
                        "发送中"
                    }
                    if (!enabled) disabled()
                    onClick(label = "发送") {
                        if (enabled) onSendClick()
                        enabled
                    }
                    customActions = if (enabled) {
                        SendFormatOptions.reversed().map { option ->
                            CustomAccessibilityAction(
                                label = "以${option.label}格式发送",
                                action = {
                                    onSendWithType(option.type)
                                    true
                                }
                            )
                        }
                    } else {
                        emptyList()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (currentEnabled) {
                                isPressed = true
                                val released = tryAwaitRelease()
                                isPressed = false

                                if (showFormatPicker) {
                                    val formatToSend = selectedType
                                    val wasCancelled = cancelSend
                                    showFormatPicker = false
                                    cancelSend = false
                                    if (released && currentEnabled && !wasCancelled) {
                                        currentOnSendWithType(formatToSend)
                                    }
                                }
                            }
                        },
                        onTap = {
                            if (currentEnabled) currentOnSendClick()
                        },
                        onLongPress = { pressPosition ->
                            if (currentEnabled) {
                                val initialType = currentSendType.takeIf { type ->
                                    SendFormatOptions.any { it.type == type }
                                } ?: "text"
                                selectedType = initialType
                                initialFormatIndex = SendFormatOptions.indexOfFirst {
                                    it.type == initialType
                                }
                                dragOrigin = pressPosition
                                cancelSend = false
                                showFormatPicker = true
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    )
                }
                // Selection is relative to the long-press point, so the finger can stay
                // anywhere around the button instead of having to enter the popup.
                .pointerInput(optionWidthPx, cancelDistancePx) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (!showFormatPicker) continue

                            val pointer = event.changes.firstOrNull { it.pressed } ?: continue
                            val nextCancelSend = shouldCancelFormatSend(
                                verticalDrag = pointer.position.y - dragOrigin.y,
                                cancelDistance = cancelDistancePx
                            )
                            if (nextCancelSend != cancelSend) {
                                cancelSend = nextCancelSend
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (nextCancelSend) continue

                            val optionIndex = sendFormatOptionIndex(
                                horizontalDrag = pointer.position.x - dragOrigin.x,
                                initialIndex = initialFormatIndex,
                                optionWidth = optionWidthPx,
                                optionCount = SendFormatOptions.size
                            )
                            val nextType = SendFormatOptions[optionIndex].type
                            if (nextType != selectedType) {
                                selectedType = nextType
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
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
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }

        if (showFormatPicker) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(x = 0, y = -pickerOffsetPx),
                onDismissRequest = {
                    showFormatPicker = false
                    cancelSend = false
                },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    modifier = Modifier
                        .width(SendFormatOptionWidth * SendFormatOptions.size)
                        .heightIn(min = SendFormatPickerHeight, max = SendFormatPickerHeight)
                        .clearAndSetSemantics { },
                    shape = RoundedCornerShape(14.dp),
                    color = if (cancelSend) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    if (cancelSend) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "松开取消发送",
                                modifier = Modifier.padding(start = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SendFormatOptions.forEach { option ->
                                val selected = option.type == selectedType
                                Box(
                                    modifier = Modifier
                                        .width(SendFormatOptionWidth)
                                        .fillMaxHeight()
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                Color.Transparent
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        when (option.type) {
                                            "html" -> Icon(
                                                imageVector = Icons.Rounded.Code,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            "markdown" -> Icon(
                                                painter = painterResource(R.drawable.markdown),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            else -> Icon(
                                                imageVector = Icons.Rounded.TextFields,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = option.label,
                                            modifier = Modifier.padding(start = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreActionsButton(
    onAddImageClick: () -> Unit,
    onAddVideoClick: () -> Unit,
    onAddFileClick: () -> Unit
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
        }
    }
}
