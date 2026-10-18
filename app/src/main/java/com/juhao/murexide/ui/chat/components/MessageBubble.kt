package com.juhao.murexide.ui.chat.components

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.ClipData
import android.view.View
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.juhao.murexide.data.MessageButton
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.resolveStickerMessageUrl
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.ImageViewerSourceBounds
import com.juhao.murexide.ui.components.LiteHtmlContent
import com.juhao.murexide.ui.components.fullImagePreviewItem
import com.juhao.murexide.ui.components.MarkdownText
import com.juhao.murexide.ui.components.showImageViewer
import com.juhao.murexide.ui.theme.UiState
import com.juhao.murexide.ui.theme.usesDarkTheme
import com.juhao.murexide.utils.formatTimestamp
import com.juhao.murexide.utils.isYunhuImageUrl
import com.juhao.murexide.utils.imageAspectRatio
import com.juhao.murexide.utils.imageThumbnailUrl
import com.juhao.murexide.utils.videoAspectRatio

import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import androidx.core.graphics.toColorInt

private val WhiteThemeIncomingBubbleColor = Color(0xFFEEEEF0)
private val IMG_SRC_REGEX = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
private val QUOTE_PREFIX_REGEX = Regex("^[^:：]+[:：]\\s*(.*)$")

private const val AVATAR_SIZE_DP = 36
private const val QUOTE_PREVIEW_SIZE_DP = 40
private const val IMAGE_MAX_LANDSCAPE_DP = 240
private const val IMAGE_MAX_PORTRAIT_DP = 160
private const val VIDEO_WIDTH_DP = 200
private const val STICKER_MAX_DP = 120

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    modifier: Modifier = Modifier,
    message: MessageItem,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: (MessageItem) -> Unit = {},
    onClickInSelectionMode: (MessageItem) -> Unit = {},
    onRecall: () -> Unit = {},
    onEdit: () -> Unit = {},
    onReply: () -> Unit = {},
    onForward: () -> Unit = {},
    onQuoteClick: ((MessageItem) -> Unit)? = null,
    isAdmin: Boolean = false,
    isLastFromSender: Boolean = true,
    isFirstFromSender: Boolean = true,
    drawAvatar: Boolean = true,
    showTags: Boolean = true,
    showMenu: Boolean = false,
    showMenuMsgId: String? = null,
    showMenuChanged: (String?) -> Unit = {},
    onImageClick: (MessageItem, ImageViewerSourceBounds?) -> Unit = { _, _ -> },
    onMarkdownImageClick: (String) -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onAvatarLongClick: () -> Unit = {},
    bubbleCornerRadius: Float = 18f,
    bubbleOpacity: Float = 0.9f,
    showMyBubbleAvatarSetting: Boolean = true,
    downloadProgress: Float? = null,
    isDownloaded: Boolean = false,
    onDownloadClick: (MessageItem) -> Unit = {},
    onButtonClick: (MessageItem, MessageButton) -> Unit = { _, _ -> },
    onEditIconClick: (String) -> Unit = {},
    hideSenderInfo: Boolean = false,
    hideMyInfo: Boolean = false,
    hideImages: Boolean = false,
    anonymousNameProvider: ((String) -> String)? = null,
    roleLabel: String? = null,
    isHighlighted: Boolean = false,
    onCreateExpression: (String) -> Unit = {}
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val isMine = if (hideMyInfo) false else message.isMine

    val themeColor by UiState.themeColor
    val themeMode by UiState.themeMode
    val isSystemDark = isSystemInDarkTheme()
    val isWhiteLightTheme = themeColor == "WHITE" && !usesDarkTheme(themeMode, isSystemDark)
    val isWhiteDarkTheme = themeColor == "WHITE" && themeMode != "oled" && usesDarkTheme(themeMode, isSystemDark)

    val incomingBubbleColor = when {
        isWhiteLightTheme -> WhiteThemeIncomingBubbleColor
        isWhiteDarkTheme -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = bubbleOpacity)
    }
    val incomingAttachmentBackgroundColor = when {
        isWhiteLightTheme -> WhiteThemeIncomingBubbleColor
        isWhiteDarkTheme -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    }

    val context = LocalContext.current
    val audioPlaybackState by AudioPlaybackManager.state.collectAsState()

    val layoutDirection = LocalConfiguration.current.layoutDirection
    val defaultLayoutDirection = remember(layoutDirection) {
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    }

    val timestampDisplay = remember(message.timestamp) { formatTimestamp(message.timestamp) }

    val targetAlpha = when {
        showMenuMsgId != null && !showMenu -> 0.5f
        message.isRecalled -> 0.6f
        else -> 1f
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300),
        label = "message_alpha"
    )

    val highlightColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            isHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 250),
        label = "message_highlight"
    )

    val bubbleLayoutDirection = if (isMine) defaultLayoutDirection.opposite() else defaultLayoutDirection

    CompositionLocalProvider(LocalLayoutDirection provides bubbleLayoutDirection) {
        Row(
            modifier = modifier
                .alpha(animatedAlpha)
                .background(highlightColor, shape = RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isSelectionMode) {
                            onClickInSelectionMode(message)
                        } else if (!message.isRecalled && message.contentType != MessageItem.CONTENT_TYPE_TIP) {
                            showMenuChanged(message.msgId)
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) onLongPress(message)
                    }
                )
        ) {
            if (message.contentType == MessageItem.CONTENT_TYPE_TIP) {
                TipContent(content = message.content)
            } else {
                MessageBubbleBody(
                    message = message,
                    isMine = isMine,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onLongPress = onLongPress,
                    onClickInSelectionMode = onClickInSelectionMode,
                    onRecall = onRecall,
                    onEdit = onEdit,
                    onReply = onReply,
                    onForward = onForward,
                    onQuoteClick = onQuoteClick,
                    isAdmin = isAdmin,
                    isLastFromSender = isLastFromSender,
                    isFirstFromSender = isFirstFromSender,
                    drawAvatar = drawAvatar,
                    showMyBubbleAvatarSetting = showMyBubbleAvatarSetting,
                    showTags = showTags,
                    showMenu = showMenu,
                    showMenuChanged = showMenuChanged,
                    onImageClick = onImageClick,
                    onMarkdownImageClick = onMarkdownImageClick,
                    onAvatarClick = onAvatarClick,
                    onAvatarLongClick = onAvatarLongClick,
                    bubbleCornerRadius = bubbleCornerRadius,
                    bubbleOpacity = bubbleOpacity,
                    downloadProgress = downloadProgress,
                    isDownloaded = isDownloaded,
                    onDownloadClick = onDownloadClick,
                    onButtonClick = onButtonClick,
                    onEditIconClick = onEditIconClick,
                    hideSenderInfo = hideSenderInfo,
                    hideImages = hideImages,
                    anonymousNameProvider = anonymousNameProvider,
                    roleLabel = roleLabel,
                    incomingBubbleColor = incomingBubbleColor,
                    incomingAttachmentBackgroundColor = incomingAttachmentBackgroundColor,
                    defaultLayoutDirection = defaultLayoutDirection,
                    layoutDirection = layoutDirection,
                    audioPlaybackState = audioPlaybackState,
                    timestampDisplay = timestampDisplay,
                    clipboardManager = clipboardManager,
                    scope = scope,
                    context = context,
                    onCreateExpression = onCreateExpression
                )
            }
        }
    }
}

@Composable
private fun TipContent(content: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Text(
                text = content,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubbleBody(
    message: MessageItem,
    isMine: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongPress: (MessageItem) -> Unit,
    onClickInSelectionMode: (MessageItem) -> Unit,
    onRecall: () -> Unit,
    onEdit: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onQuoteClick: ((MessageItem) -> Unit)?,
    isAdmin: Boolean,
    isLastFromSender: Boolean,
    isFirstFromSender: Boolean,
    drawAvatar: Boolean = true,
    showMyBubbleAvatarSetting: Boolean = true,
    showTags: Boolean,
    showMenu: Boolean,
    showMenuChanged: (String?) -> Unit,
    onImageClick: (MessageItem, ImageViewerSourceBounds?) -> Unit,
    onMarkdownImageClick: (String) -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    bubbleCornerRadius: Float,
    bubbleOpacity: Float,
    downloadProgress: Float?,
    isDownloaded: Boolean,
    onDownloadClick: (MessageItem) -> Unit,
    onButtonClick: (MessageItem, MessageButton) -> Unit,
    onEditIconClick: (String) -> Unit,
    hideSenderInfo: Boolean,
    hideImages: Boolean,
    anonymousNameProvider: ((String) -> String)?,
    roleLabel: String?,
    incomingBubbleColor: Color,
    incomingAttachmentBackgroundColor: Color,
    defaultLayoutDirection: LayoutDirection,
    layoutDirection: Int,
    audioPlaybackState: AudioPlaybackState,
    timestampDisplay: String,
    clipboardManager: androidx.compose.ui.platform.Clipboard,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    onCreateExpression: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = if (!isLastFromSender) 0.dp else 2.dp,
                bottom = if (!isFirstFromSender) 0.dp else 2.dp
            ),
        verticalAlignment = Alignment.Bottom
    ) {
        val hideMyAvatar = isMine && !showMyBubbleAvatarSetting

        if (!hideMyAvatar) {
            if (drawAvatar) {
                if (hideSenderInfo) {
                    PlaceholderAvatar()
                } else {
                    Avatar(
                        url = message.senderAvatar,
                        modifier = Modifier.combinedClickable(
                            onClick = { onAvatarClick() },
                            onLongClick = { onAvatarLongClick() }
                        ),
                        size = AVATAR_SIZE_DP.dp
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(AVATAR_SIZE_DP.dp))
            }
        }

        val hideCard = (message.contentType == MessageItem.CONTENT_TYPE_IMAGE
            || message.contentType == MessageItem.CONTENT_TYPE_STICKER
            || message.contentType == MessageItem.CONTENT_TYPE_VIDEO
            || message.contentType == MessageItem.CONTENT_TYPE_FILE)
            && !message.isRecalled

        val hasQuote = message.quoteMsgText != null

        Box(modifier = Modifier.weight(1f, fill = false)) {
            val cardColor = if (isMine)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = bubbleOpacity)
            else
                incomingBubbleColor

            val cardShape = RoundedCornerShape(
                topStart = if (!isLastFromSender) (bubbleCornerRadius / 4).dp else bubbleCornerRadius.dp,
                topEnd = bubbleCornerRadius.dp,
                bottomStart = if (isFirstFromSender && (hideCard && message.contentType != MessageItem.CONTENT_TYPE_FILE))
                    bubbleCornerRadius.dp
                else if (isFirstFromSender)
                    0.dp
                else
                    (bubbleCornerRadius / 4).dp,
                bottomEnd = bubbleCornerRadius.dp
            )

            Row(verticalAlignment = Alignment.Bottom) {
                if (isFirstFromSender && (!hideCard || message.contentType == MessageItem.CONTENT_TYPE_FILE)) {
                    Spacer(Modifier.size(4.dp))
                    QuarterCircleCorner(
                        color = cardColor,
                        isMine = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) !isMine else isMine,
                        defaultLayoutDirection = defaultLayoutDirection
                    )
                } else {
                    Spacer(Modifier.size(12.dp))
                }

                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box {
                        Column(modifier = Modifier.padding(if (hideCard) 0.dp else 8.dp)) {
                            val displayName = if (hideSenderInfo && anonymousNameProvider != null) {
                                anonymousNameProvider(message.senderId)
                            } else {
                                message.senderName
                            }

                            if (!hideCard && !isMine && isLastFromSender) {
                                SenderHeader(
                                    displayName = displayName,
                                    roleLabel = roleLabel,
                                    message = message,
                                    showTags = showTags,
                                    hideSenderInfo = hideSenderInfo
                                )
                            }

                            message.cmdName?.let {
                                Text(
                                    text = "/$it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            if (hasQuote && !hideCard) {
                                QuoteCard(
                                    message = message,
                                    hideSenderInfo = hideSenderInfo,
                                    hideImages = hideImages,
                                    isSelectionMode = isSelectionMode,
                                    onQuoteClick = onQuoteClick
                                )
                            }

                            CompositionLocalProvider(LocalLayoutDirection provides defaultLayoutDirection) {
                                MessageMenu(
                                    message = message,
                                    showMenu = showMenu,
                                    showMenuChanged = showMenuChanged,
                                    isMine = isMine,
                                    isAdmin = isAdmin,
                                    onRecall = onRecall,
                                    onEdit = onEdit,
                                    onReply = onReply,
                                    onForward = onForward,
                                    onEditIconClick = onEditIconClick,
                                    clipboardManager = clipboardManager,
                                    scope = scope,
                                    context = context,
                                    onCreateExpression = onCreateExpression
                                )

                                MessageContentRouter(
                                    message = message,
                                    isMine = isMine,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onLongPress = onLongPress,
                                    onImageClick = onImageClick,
                                    onMarkdownImageClick = onMarkdownImageClick,
                                    onDownloadClick = onDownloadClick,
                                    downloadProgress = downloadProgress,
                                    isDownloaded = isDownloaded,
                                    hideImages = hideImages,
                                    timestampDisplay = timestampDisplay,
                                    incomingAttachmentBackgroundColor = incomingAttachmentBackgroundColor,
                                    audioPlaybackState = audioPlaybackState,
                                    context = context,
                                    bubbleCornerRadius = bubbleCornerRadius,
                                    bubbleOpacity = bubbleOpacity,
                                    incomingBubbleColor = incomingBubbleColor
                                )

                                if (!message.isRecalled && message.buttons.isNotEmpty()) {
                                    MessageButtons(
                                        buttons = message.buttons,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                top = if (hideCard) 4.dp else 6.dp,
                                                start = if (hideCard) 8.dp else 0.dp,
                                                end = if (hideCard) 8.dp else 0.dp,
                                                bottom = if (hideCard) 4.dp else 0.dp
                                            ),
                                        onButtonClick = { button -> onButtonClick(message, button) }
                                    )
                                }
                            }
                        }

                        MessageFooter(
                            timestampDisplay = timestampDisplay,
                            isEdited = message.isEdited,
                            isRecalled = message.isRecalled,
                            modifier = Modifier
                                .align(if (isMine) Alignment.BottomStart else Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(cardColor.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (hideCard && hasQuote) {
                    Spacer(modifier = Modifier.width(8.dp))
                    QuoteCard(
                        message = message,
                        hideSenderInfo = hideSenderInfo,
                        hideImages = hideImages,
                        isSelectionMode = isSelectionMode,
                        onQuoteClick = onQuoteClick,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenderHeader(
    displayName: String,
    roleLabel: String?,
    message: MessageItem,
    showTags: Boolean,
    hideSenderInfo: Boolean
) {
    FlowRow(
        modifier = Modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        if (roleLabel != null) {
            val roleColor = if (roleLabel == "群主") Color(0xFFE6A23C) else MaterialTheme.colorScheme.tertiary
            TagChip(
                text = roleLabel,
                containerColor = roleColor.copy(alpha = 0.2f),
                contentColor = roleColor,
                type = 0
            )
        }

        if (message.senderType == 3) {
            TagChip(
                text = "机器人",
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.primary,
                type = 1
            )
        }

        if (showTags && !hideSenderInfo && message.tags.isNotEmpty()) {
            message.tags.forEach { tag ->
                val color = tag.color.toColorOrNull() ?: return@forEach
                TagChip(
                    text = tag.text,
                    containerColor = color.copy(alpha = 0.2f),
                    contentColor = lerp(color, MaterialTheme.colorScheme.onSurface, 0.5f)
                )
            }
        }
    }
}

@Composable
private fun PlaceholderAvatar() {
    Surface(
        modifier = Modifier.size(AVATAR_SIZE_DP.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(AppIcons.Person, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun MessageMenu(
    message: MessageItem,
    showMenu: Boolean,
    showMenuChanged: (String?) -> Unit,
    isMine: Boolean,
    isAdmin: Boolean,
    onRecall: () -> Unit,
    onEdit: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onEditIconClick: (String) -> Unit,
    clipboardManager: androidx.compose.ui.platform.Clipboard,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    onCreateExpression: (String) -> Unit = {}
) {
    var showSelectTextDialog by remember(message.msgId) { mutableStateOf(false) }

    if (showSelectTextDialog) {
        SelectTextDialog(
            text = message.content,
            onDismiss = { showSelectTextDialog = false }
        )
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenuChanged(null) },
        shape = MenuDefaults.standaloneGroupShape
    ) {
        if (message.content.isNotBlank()) {
            DropdownMenuItem(
                text = { Text("复制") },
                onClick = {
                    scope.launch {
                        clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("msg", message.content)))
                    }
                    Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
                    showMenuChanged(null)
                },
                leadingIcon = {
                    Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
            DropdownMenuItem(
                text = { Text("选择复制") },
                onClick = {
                    showSelectTextDialog = true
                    showMenuChanged(null)
                },
                leadingIcon = {
                    Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }

        val imageUrl = message.imageUrl
        if (imageUrl != null && imageUrl.isNotBlank()) {
            DropdownMenuItem(
                text = { Text("收藏") },
                onClick = {
                    onCreateExpression(imageUrl)
                    showMenuChanged(null)
                },
                leadingIcon = {
                    Icon(AppIcons.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }

        DropdownMenuItem(
            text = { Text("引用") },
            onClick = {
                showMenuChanged(null)
                onReply()
            },
            leadingIcon = {
                Icon(AppIcons.FormatQuote, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )

        DropdownMenuItem(
            text = { Text("转发") },
            onClick = {
                showMenuChanged(null)
                onForward()
            },
            leadingIcon = {
                AutoMirroredIcon(AppIcons.Redo, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )

        if (isMine || isAdmin) {
            DropdownMenuItem(
                text = { Text("撤回") },
                onClick = {
                    showMenuChanged(null)
                    onRecall()
                },
                leadingIcon = {
                    AutoMirroredIcon(AppIcons.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }

        if (isMine && message.content.isNotBlank()) {
            DropdownMenuItem(
                text = { Text("编辑") },
                onClick = {
                    showMenuChanged(null)
                    onEdit()
                },
                leadingIcon = {
                    Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }

        if (message.isEdited) {
            DropdownMenuItem(
                text = { Text("编辑历史") },
                onClick = { onEditIconClick(message.msgId) },
                leadingIcon = {
                    Icon(AppIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }
    }
}

@Composable
private fun MessageContentRouter(
    message: MessageItem,
    isMine: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongPress: (MessageItem) -> Unit,
    onImageClick: (MessageItem, ImageViewerSourceBounds?) -> Unit,
    onMarkdownImageClick: (String) -> Unit,
    onDownloadClick: (MessageItem) -> Unit,
    downloadProgress: Float?,
    isDownloaded: Boolean,
    hideImages: Boolean,
    timestampDisplay: String,
    incomingAttachmentBackgroundColor: Color,
    audioPlaybackState: AudioPlaybackState,
    context: android.content.Context,
    bubbleCornerRadius: Float,
    bubbleOpacity: Float,
    incomingBubbleColor: Color
) {
    val reserve = remember(timestampDisplay, message.isEdited) {
        val timeCharCount = timestampDisplay.length + if (message.isEdited) 4 else 0
        val count = (timeCharCount * 2).coerceAtLeast(6)
        "\u202F".repeat(count)
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val timeTextStyle = remember { TextStyle(fontSize = 10.sp, lineHeight = 16.sp) }
    val textReserveWidthPx = remember(timestampDisplay, message.isEdited, density) {
        val timeText = timestampDisplay + if (message.isEdited) " 已编辑" else ""
        textMeasurer.measure(
            text = AnnotatedString(timeText),
            style = timeTextStyle
        ).size.width + with(density) { 8.dp.roundToPx() }
    }
    
    if (message.isRecalled) {
        Text(
            text = message.getRecallDisplayContent() + reserve,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        return
    }

    when (message.contentType) {
        MessageItem.CONTENT_TYPE_TEXT -> MessageText(
            text = message.content,
            bodyStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            trailingReserveWidthPx = textReserveWidthPx
        )

        MessageItem.CONTENT_TYPE_MARKDOWN -> MarkdownContent(
            text = message.content + reserve,
            isSelected = isSelected,
            onMarkdownImageClick = onMarkdownImageClick
        )

        MessageItem.CONTENT_TYPE_HTML -> HtmlContent(
            text = message.content + reserve,
            isMine = isMine,
            incomingAttachmentBackgroundColor = incomingAttachmentBackgroundColor,
            context = context
        )

        MessageItem.CONTENT_TYPE_AUDIO -> AudioContent(
            message = message,
            isMine = isMine,
            isSelectionMode = isSelectionMode,
            incomingAttachmentBackgroundColor = incomingAttachmentBackgroundColor,
            audioPlaybackState = audioPlaybackState,
            context = context
        )

        MessageItem.CONTENT_TYPE_IMAGE,
        MessageItem.CONTENT_TYPE_STICKER,
        MessageItem.CONTENT_TYPE_VIDEO -> MediaContent(
            message = message,
            isSelectionMode = isSelectionMode,
            onLongPress = onLongPress,
            onImageClick = onImageClick,
            hideImages = hideImages,
            incomingAttachmentBackgroundColor = incomingAttachmentBackgroundColor,
            context = context
        )

        MessageItem.CONTENT_TYPE_FILE -> FileContent(
            message = message,
            isMine = isMine,
            onLongPress = onLongPress,
            onDownloadClick = onDownloadClick,
            downloadProgress = downloadProgress,
            isDownloaded = isDownloaded,
            bubbleCornerRadius = bubbleCornerRadius,
            bubbleOpacity = bubbleOpacity,
            incomingBubbleColor = incomingBubbleColor
        )

        MessageItem.CONTENT_TYPE_POST -> PostContent(message = message)

        else -> UnsupportedContent(contentType = message.contentType)
    }
}

@Composable
private fun MarkdownContent(
    text: String,
    isSelected: Boolean,
    onMarkdownImageClick: (String) -> Unit
) {
    MarkdownText(
        markdown = text,
        enableTextSelection = isSelected,
        onImageClick = onMarkdownImageClick
    )
}

@Composable
private fun HtmlContent(
    text: String,
    isMine: Boolean,
    incomingAttachmentBackgroundColor: Color,
    context: android.content.Context
) {
    LiteHtmlContent(
        htmlContent = text,
        modifier = Modifier.fillMaxWidth(),
        onImageClick = { imageUrl ->
            val allImages = extractImageUrls(text)
            showImageViewer(
                context = context,
                images = allImages.map(::fullImagePreviewItem),
                initialIndex = allImages.indexOf(imageUrl).coerceAtLeast(0)
            )
        },
        backgroundColor = if (isMine)
            MaterialTheme.colorScheme.primaryContainer
        else
            incomingAttachmentBackgroundColor
    )
}

@Composable
private fun AudioContent(
    message: MessageItem,
    isMine: Boolean,
    isSelectionMode: Boolean,
    incomingAttachmentBackgroundColor: Color,
    audioPlaybackState: AudioPlaybackState,
    context: android.content.Context
) {
    val audioUrl = message.audioUrl
    val audioDuration = message.audioTime?.coerceAtLeast(0) ?: 0
    val isPlaying = audioPlaybackState.messageId == message.msgId && audioPlaybackState.isPlaying
    val durationText = formatAudioDuration(audioDuration)
    val audioWidth = (120 + audioDuration * 4).coerceIn(120, 240).dp

    Surface(
        modifier = Modifier
            .width(audioWidth)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isSelectionMode && !message.isRecalled && audioUrl != null) {
                audioUrl?.let {
                    AudioPlaybackManager.toggle(context = context, messageId = message.msgId, url = it)
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isMine) MaterialTheme.colorScheme.primaryContainer else incomingAttachmentBackgroundColor
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                contentDescription = if (isPlaying) "暂停语音" else "播放语音",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MediaContent(
    message: MessageItem,
    isSelectionMode: Boolean,
    onLongPress: (MessageItem) -> Unit,
    onImageClick: (MessageItem, ImageViewerSourceBounds?) -> Unit,
    hideImages: Boolean,
    incomingAttachmentBackgroundColor: Color,
    context: android.content.Context
) {
    if (hideImages) {
        HiddenMediaPlaceholder(
            contentType = message.contentType,
            incomingAttachmentBackgroundColor = incomingAttachmentBackgroundColor
        )
        return
    }

    val isImageMessage = message.contentType == MessageItem.CONTENT_TYPE_IMAGE
    val isVideoMessage = message.contentType == MessageItem.CONTENT_TYPE_VIDEO
    val isStickerMessage = message.contentType == MessageItem.CONTENT_TYPE_STICKER
    val mediaUrl = when (message.contentType) {
        MessageItem.CONTENT_TYPE_STICKER -> resolveStickerMessageUrl(
            imageUrl = message.imageUrl,
            stickerUrl = message.stickerUrl
        )
        MessageItem.CONTENT_TYPE_VIDEO -> message.videoUrl
        else -> message.imageUrl
    } ?: return

    val videoDuration = if (isVideoMessage) formatVideoDuration(message.videoTime) else null
    val imageRatio = if (isVideoMessage) {
        videoAspectRatio(message.imageWidth, message.imageHeight)
    } else {
        imageAspectRatio(message.imageWidth, message.imageHeight)
    }
    val imageMaxWidth = when {
        isStickerMessage -> STICKER_MAX_DP.dp
        imageRatio >= 1f -> IMAGE_MAX_LANDSCAPE_DP.dp
        else -> IMAGE_MAX_PORTRAIT_DP.dp
    }
    val displayUrl = if (isImageMessage) imageThumbnailUrl(mediaUrl) else mediaUrl
    var retryCount by remember(mediaUrl) { mutableIntStateOf(0) }
    var loadState by remember(mediaUrl, retryCount) { mutableIntStateOf(0) }

    val imageRequest = remember(displayUrl, message.contentType, retryCount) {
        val request = ImageRequest.Builder(context)
            .data(displayUrl)
            .setParameter("retry", retryCount)
            .allowHardware(chatMediaAllowsHardwareBitmaps(message.contentType))
        if (isVideoMessage) request.videoFrameMillis(0)
        request.build()
    }

    val sourceCoordinates = remember(message.msgId, mediaUrl) { ImageSourceCoordinates() }

    Box(
        modifier = Modifier
            .then(
                if (isVideoMessage) {
                    Modifier.width(VIDEO_WIDTH_DP.dp).aspectRatio(imageRatio)
                } else {
                    Modifier
                        .widthIn(min = if (isStickerMessage) 48.dp else 100.dp, max = imageMaxWidth)
                        .aspectRatio(imageRatio)
                }
            )
            .onGloballyPositioned { coordinates -> sourceCoordinates.value = coordinates }
            .background(
                if (isImageMessage || isVideoMessage) incomingAttachmentBackgroundColor else Color.Transparent
            )
            .combinedClickable(
                onClick = {
                    val sourceBounds = sourceCoordinates.value
                        ?.takeIf { it.isAttached }
                        ?.toImageViewerSourceBounds(isCropped = isImageMessage || isVideoMessage)
                    onImageClick(message, sourceBounds)
                },
                onLongClick = { onLongPress(message) }
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = if (isVideoMessage) "视频缩略图" else null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onLoading = { loadState = 0 },
            onSuccess = { loadState = 1 },
            onError = { loadState = 2 }
        )

        if (loadState == 0) {
            MediaLoadingIcon(isVideoMessage = isVideoMessage, isImageMessage = isImageMessage)
        } else if (loadState == 2) {
            IconButton(
                onClick = { retryCount++ },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = AppIcons.Refresh,
                    contentDescription = if (isVideoMessage) "重试加载视频缩略图" else "重试加载图片"
                )
            }
        }

        if (isVideoMessage && loadState == 1) {
            VideoPlayOverlay()
        }

        videoDuration?.let { duration ->
            VideoDurationBadge(duration = duration)
        }
    }
}

@Composable
private fun HiddenMediaPlaceholder(
    contentType: Int,
    incomingAttachmentBackgroundColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        color = incomingAttachmentBackgroundColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                AppIcons.ImageNotSupported,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (contentType) {
                    MessageItem.CONTENT_TYPE_STICKER -> "表情包已隐藏"
                    MessageItem.CONTENT_TYPE_VIDEO -> "视频已隐藏"
                    else -> "图片已隐藏"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BoxScope.MediaLoadingIcon(isVideoMessage: Boolean, isImageMessage: Boolean) {
    Icon(
        imageVector = when {
            isVideoMessage -> AppIcons.VideoFile
            isImageMessage -> AppIcons.Image
            else -> AppIcons.Mood
        },
        contentDescription = null,
        modifier = Modifier.size(28.dp).align(Alignment.Center),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    )
}

@Composable
private fun BoxScope.VideoPlayOverlay() {
    Surface(
        modifier = Modifier.size(48.dp).align(Alignment.Center),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Icon(
            imageVector = AppIcons.PlayArrow,
            contentDescription = "播放视频",
            modifier = Modifier.padding(8.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun BoxScope.VideoDurationBadge(duration: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 6.dp, bottom = 6.dp)
            .background(color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(50.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = AppIcons.PlayCircle,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = Color.White
        )
        Text(text = duration, fontSize = 10.sp, lineHeight = 16.sp, maxLines = 1, color = Color.White)
    }
}

@Composable
private fun FileContent(
    message: MessageItem,
    isMine: Boolean,
    onLongPress: (MessageItem) -> Unit,
    onDownloadClick: (MessageItem) -> Unit,
    downloadProgress: Float?,
    isDownloaded: Boolean,
    bubbleCornerRadius: Float,
    bubbleOpacity: Float,
    incomingBubbleColor: Color
) {
    val fileName = message.fileName ?: return
    val progress = downloadProgress ?: 0f
    val isDownloading = downloadProgress != null && downloadProgress < 1f
    val isComplete = isDownloaded || (downloadProgress != null && progress >= 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "file_progress_animated"
    )

    Row(
        modifier = Modifier
            .widthIn(min = 240.dp)
            .clip(
                RoundedCornerShape(
                    topStart = bubbleCornerRadius.dp,
                    topEnd = bubbleCornerRadius.dp
                )
            )
            .background(
                if (isMine)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = bubbleOpacity)
                else
                    incomingBubbleColor
            )
            .combinedClickable(
                onClick = { if (!isDownloading) onDownloadClick(message) },
                onLongClick = { onLongPress(message) }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            onClick = { onDownloadClick(message) },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isDownloading) {
                    val rotation by rememberInfiniteTransition(label = "file_progress_rotation")
                        .animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )

                    CircularProgressIndicator(
                        progress = { animatedProgress.coerceIn(0.01f, 1f) },
                        modifier = Modifier
                            .fillMaxSize()
                            .size(36.dp)
                            .graphicsLayer { rotationZ = rotation },
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }

                when {
                    isDownloading -> Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "取消下载",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    isComplete -> Icon(
                        imageVector = getFileIcon(fileName),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    else -> Icon(
                        imageVector = AppIcons.Download,
                        contentDescription = "下载",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = fileName,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(modifier = Modifier.padding(top = 2.dp)) {
                message.fileSize?.let { size ->
                    Text(
                        text = formatFileSize(size),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                if (isDownloading) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isComplete) {
                    Text(
                        text = "已下载",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PostContent(message: MessageItem) {
    PostCard(
        message.postId?.toIntOrNull() ?: 0,
        message.postTitle ?: "文章",
        message.postContent ?: "内容"
    )
}

@Composable
private fun UnsupportedContent(contentType: Int) {
    Text(
        text = "暂不支持解析此消息：$contentType",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    )
}

@Composable
private fun MessageFooter(
    timestampDisplay: String,
    isEdited: Boolean,
    isRecalled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Text(
            text = timestampDisplay,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (isEdited && !isRecalled) {
            Text(
                text = "已编辑",
                fontSize = 10.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private fun LayoutDirection.opposite(): LayoutDirection = when (this) {
    LayoutDirection.Ltr -> LayoutDirection.Rtl
    LayoutDirection.Rtl -> LayoutDirection.Ltr
}

@Composable
private fun QuoteCard(
    message: MessageItem,
    hideSenderInfo: Boolean,
    hideImages: Boolean,
    isSelectionMode: Boolean,
    onQuoteClick: ((MessageItem) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val quoteText = message.quoteMsgText ?: return
    val context = LocalContext.current

    Surface(
        modifier = modifier.padding(bottom = 4.dp),
        onClick = {
            if (!isSelectionMode && !message.quoteMsgId.isNullOrBlank() && onQuoteClick != null) {
                onQuoteClick(message)
            }
        },
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(IntrinsicSize.Max)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ) {
                if (message.quoteImageUrl != null && !hideImages) {
                    val builder = ImageRequest.Builder(context)
                        .data(message.quoteImageUrl)
                    if (isYunhuImageUrl(message.quoteImageUrl)) {
                        builder.setHeader("Referer", "https://myapp.jwznb.com")
                    }
                    AsyncImage(
                        model = builder.build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(QUOTE_PREVIEW_SIZE_DP.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (hideSenderInfo) processQuoteText(quoteText) else quoteText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TagChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    type: Int? = null
) {
    Surface(shape = RoundedCornerShape(50.dp), color = containerColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(6.dp))
            type?.let {
                if (type == 0) {
                    Icon(
                        imageVector = AppIcons.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                } else if (type == 1) {
                    Icon(
                        imageVector = AppIcons.Robot,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}

private fun String?.toColorOrNull(): Color? {
    if (this.isNullOrBlank()) return null
    return runCatching { Color(this.toColorInt()) }.getOrNull()
}

@Composable
private fun MessageButtons(
    buttons: List<List<MessageButton>>,
    modifier: Modifier = Modifier,
    onButtonClick: (MessageButton) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        buttons.forEach { row ->
            if (row.isEmpty()) return@forEach
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { button ->
                    val leadingIcon = when (button.actionType) {
                        MessageButton.ACTION_JUMP -> AppIcons.Link
                        MessageButton.ACTION_COPY -> AppIcons.ContentCopy
                        else -> null
                    }
                    OutlinedButton(
                        onClick = { onButtonClick(button) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (leadingIcon != null) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = button.text,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuarterCircleCorner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Dp = 16.dp,
    isMine: Boolean,
    defaultLayoutDirection: LayoutDirection = LayoutDirection.Ltr
) {
    val halfWidth = size / 2

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier = modifier.size(width = halfWidth, height = size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = if (isMine) w else 0f
            val cy = 0f
            val left = cx - w
            val top = cy - h
            val right = cx + w
            val bottom = cy + h

            val rectPath = Path().apply { addRect(Rect(left, top, right, bottom)) }
            val ovalPath = Path().apply { addOval(Rect(left, top, right, bottom)) }
            val diffPath = Path().apply { op(rectPath, ovalPath, PathOperation.Difference) }

            clipRect(left = 0f, top = 0f, right = w, bottom = h) {
                drawPath(path = diffPath, color = color)
            }
        }
    }
}

@Composable
private fun SelectTextDialog(
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择文本") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

private fun getFileIcon(fileName: String): ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "apk" -> AppIcons.Android
        "pdf" -> AppIcons.PictureAsPdf
        "doc", "docx" -> AppIcons.Description
        "xls", "xlsx" -> AppIcons.TableChart
        "ppt", "pptx" -> AppIcons.Slideshow
        "zip", "rar", "7z", "tar", "gz" -> AppIcons.FolderZip
        "mp3", "wav", "aac", "flac", "ogg", "m4a" -> AppIcons.AudioFile
        "mp4", "avi", "mkv", "mov", "flv" -> AppIcons.VideoFile
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> AppIcons.Image
        "txt", "md", "json", "xml", "html", "css", "js", "kt", "java" -> AppIcons.Code
        else -> AppIcons.InsertDriveFile
    }
}

private fun formatAudioDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        size < 1024 * 1024 * 1024 -> String.format(Locale.ROOT, "%.1fMB", size.toFloat() / (1024 * 1024))
        else -> String.format(Locale.ROOT, "%.2fGB", size.toFloat() / (1024 * 1024 * 1024))
    }
}

internal fun formatVideoDuration(totalSeconds: Int?): String? {
    val duration = totalSeconds?.takeIf { it >= 0 } ?: return null
    val hours = duration / 3600
    val minutes = (duration % 3600) / 60
    val seconds = duration % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

internal fun chatMediaAllowsHardwareBitmaps(contentType: Int): Boolean {
    return contentType != MessageItem.CONTENT_TYPE_IMAGE
}

private class ImageSourceCoordinates {
    var value: LayoutCoordinates? = null
}

private fun LayoutCoordinates.toImageViewerSourceBounds(
    isCropped: Boolean
): ImageViewerSourceBounds {
    val bounds = boundsInWindow()
    return ImageViewerSourceBounds(
        left = bounds.left.roundToInt(),
        top = bounds.top.roundToInt(),
        width = bounds.width.roundToInt(),
        height = bounds.height.roundToInt(),
        isCropped = isCropped
    )
}

private fun extractImageUrls(html: String): List<String> {
    return IMG_SRC_REGEX.findAll(html).map { it.groupValues[1] }.toList()
}

private fun processQuoteText(quoteText: String): String {
    if (quoteText.isBlank()) return quoteText
    val matchResult = QUOTE_PREFIX_REGEX.find(quoteText)
    return if (matchResult != null) {
        val content = matchResult.groupValues.getOrNull(1)
        if (!content.isNullOrBlank()) "用户：$content" else quoteText
    } else {
        quoteText
    }
}