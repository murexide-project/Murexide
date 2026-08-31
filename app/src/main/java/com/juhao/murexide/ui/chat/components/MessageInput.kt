package com.juhao.murexide.ui.chat.components

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.juhao.murexide.data.DefaultEmoji
import com.juhao.murexide.data.MentionToken
import kotlin.math.roundToInt

private val SendButtonSize = 44.dp
private val SendFormatOptionWidth = 48.dp
private val SendFormatPickerHeight = 48.dp
private val SendFormatPickerGap = 8.dp

private data class SendFormatOption(
    val type: String?,
    val label: String
)

private val SendFormatOptions = listOf(
    SendFormatOption(type = null, label = "取消"),
    SendFormatOption(type = "text", label = "文本"),
    SendFormatOption(type = "html", label = "HTML"),
    SendFormatOption(type = "markdown", label = "Markdown")
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

@Composable
fun MessageInput(
    inputText: String,
    inputSelectionStart: Int,
    inputSelectionEnd: Int,
    defaultEmojis: List<DefaultEmoji>,
    isSending: Boolean = false,
    onTextChange: (String, List<MentionToken>, Int, Int) -> Unit,
    onSendClick: () -> Unit,
    onSendWithType: (String) -> Unit,
    onAddAlbumClick: () -> Unit,
    onAddFileClick: () -> Unit,
    isEmojiPanelVisible: Boolean = false,
    onEmojiClick: () -> Unit,
    hasInstructions: Boolean = false,
    isInstructionPanelVisible: Boolean = false,
    onInstructionClick: () -> Unit = {},
    mentions: List<MentionToken> = emptyList(),
    onMentionTriggered: (Int) -> Unit = {},
    focusRequester: FocusRequester,
    onInputFocused: () -> Unit = {}
) {
    val fieldValue = TextFieldValue(
        text = inputText,
        selection = TextRange(
            start = inputSelectionStart.coerceIn(0, inputText.length),
            end = inputSelectionEnd.coerceIn(0, inputText.length)
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MoreActionsButton(
            onAddAlbumClick = onAddAlbumClick,
            onAddFileClick = onAddFileClick
        )
        
        IconButton(
            onClick = onEmojiClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = if (isEmojiPanelVisible) {
                    AppIcons.Keyboard
                } else {
                    AppIcons.Mood
                },
                contentDescription = if (isEmojiPanelVisible) "切换到键盘" else "表情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DefaultEmojiTextField(
            value = fieldValue,
            mentions = mentions,
            emojis = defaultEmojis,
            enabled = true,
            textColor = MaterialTheme.colorScheme.onSurface,
            hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            textSizeSp = MaterialTheme.typography.bodyLarge.fontSize.value,
            focusRequester = focusRequester,
            onValueChange = { new, updatedMentions, insertedText, insertPosition ->
                onTextChange(
                    new.text,
                    updatedMentions,
                    new.selection.start,
                    new.selection.end
                )
                if (insertedText == "@") onMentionTriggered(insertPosition)
            },
            onFocused = onInputFocused,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp, max = 144.dp)
                .padding(horizontal = 4.dp)
        )

        AnimatedContent(
            targetState = inputText.isNotBlank() || !hasInstructions,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(200))
            },
            label = "bottom_bar_button_transition"
        ) { showSendButton ->
            if (showSendButton) {
                FormatSendButton(
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
                            AppIcons.Keyboard
                        } else {
                            AppIcons.Code
                        },
                        contentDescription = if (isInstructionPanelVisible) "切换到键盘" else "指令",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FormatSendButton(
    enabled: Boolean,
    isSending: Boolean,
    onSendClick: () -> Unit,
    onSendWithType: (String) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var showFormatPicker by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }

    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnSendClick by rememberUpdatedState(onSendClick)
    val currentOnSendWithType by rememberUpdatedState(onSendWithType)
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val optionWidthPx = with(density) { SendFormatOptionWidth.toPx() }
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
                        "长按并滑动可取消或选择消息格式"
                    } else {
                        "发送中"
                    }
                    if (!enabled) disabled()
                    onClick(label = "发送") {
                        if (enabled) onSendClick()
                        enabled
                    }
                    customActions = if (enabled) {
                        SendFormatOptions.mapNotNull { option ->
                            val type = option.type ?: return@mapNotNull null
                            CustomAccessibilityAction(
                                label = "以${option.label}格式发送",
                                action = {
                                    onSendWithType(type)
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
                                    showFormatPicker = false
                                    if (released && formatToSend != null) {
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
                                selectedType = "markdown"
                                dragOrigin = pressPosition
                                showFormatPicker = true
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    )
                }
                .pointerInput(optionWidthPx) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (!showFormatPicker) continue

                            val pointer = event.changes.firstOrNull { it.pressed } ?: continue
                            val optionIndex = sendFormatOptionIndex(
                                horizontalDrag = pointer.position.x - dragOrigin.x,
                                initialIndex = SendFormatOptions.lastIndex,
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
                ContainedLoadingIndicator(
                    modifier = Modifier.size(20.dp)
                )
            } else {
                AutoMirroredIcon(
                    AppIcons.Send,
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
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
                                when (option.type) {
                                    "text" -> Icon(
                                        imageVector = AppIcons.TextFields,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    "html" -> Icon(
                                        imageVector = AppIcons.Code,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    "markdown" -> Icon(
                                        painter = painterResource(R.drawable.markdown),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    else -> Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
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

@Composable
private fun MoreActionsButton(
    onAddAlbumClick: () -> Unit,
    onAddFileClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                AppIcons.Add,
                contentDescription = "更多"
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("相册") },
                onClick = {
                    showMenu = false
                    onAddAlbumClick()
                },
                leadingIcon = {
                    Icon(AppIcons.Image, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("文件") },
                onClick = {
                    showMenu = false
                    onAddFileClick()
                },
                leadingIcon = {
                    Icon(AppIcons.AttachFile, contentDescription = null)
                }
            )
        }
    }
}
