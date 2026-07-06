package com.juhao.murexide.ui.chat.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.UnifiedHtmlWebView
import com.juhao.murexide.ui.components.MultiImageViewer
import com.juhao.murexide.ui.components.MarkdownRenderer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: MessageItem,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: (MessageItem) -> Unit = {},
    onClickInSelectionMode: (MessageItem) -> Unit = {},
    onRecall: () -> Unit = {},
    onEdit: () -> Unit = {},
    onReply: () -> Unit = {},
    isAdmin: Boolean = false,
    isLastFromSender: Boolean = true,
    isFirstFromSender: Boolean = true,
    isOlderSameSender: Boolean = false,
    isNewerSameSender: Boolean = false,
    showAvatar: Boolean = true,
    showMenu: Boolean = false,
    showMenuMsgId: String? = null,
    showMenuChanged: (String?) -> Unit = {},
    onImageClick: (MessageItem) -> Unit = {},
    onAvatarClick: () -> Unit = {},
    bubbleCornerRadius: Float = 16f,
    bubbleOpacity: Float = 0.9f,
    showMyBubbleAvatarSetting: Boolean = true,
    avatarAlignment: Alignment.Vertical = Alignment.Bottom,
    downloadProgress: Float? = null,
    isDownloaded: Boolean = false,
    onDownloadClick: (MessageItem) -> Unit = {},
    privateMode: Boolean = false
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val isMine = message.isMine
    val context = LocalContext.current

    var showImageViewer by remember { mutableStateOf(false) }
    var imageList by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentImageIndex by remember { mutableIntStateOf(0) }

    val timestampDisplay = remember(message.timestamp) {
        try {
            val date = Date(message.timestamp)
            val now = Date()
        
            val todayCalendar = Calendar.getInstance().apply {
                time = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        
            when {
                date.after(todayCalendar.time) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                date.after(Date(todayCalendar.timeInMillis - 86400000)) -> "昨天 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            ""
        }
    }

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
    
    if (showImageViewer) {
        MultiImageViewer(
            images = imageList,
            initialPage = currentImageIndex,
            isVisible = true,
            onDismiss = { showImageViewer = false }
        )
    }
    
    Row(
        modifier = Modifier
            .alpha(animatedAlpha)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
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
                    if (!isSelectionMode) {
                        onLongPress(message)
                    }
                }
            )
    ) {
        if (message.contentType == MessageItem.CONTENT_TYPE_TIP) {
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
                        text = message.content,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = if (isOlderSameSender) 0.dp else 4.dp,
                        bottom = if (isNewerSameSender) 0.dp else 4.dp
                    ),
                verticalAlignment = avatarAlignment,
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
            ) {
                if (isFirstFromSender || isLastFromSender) {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            
                if (!isMine && showAvatar) {
                    if (privateMode) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Spy, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        }
                    } else {
                        Avatar(
                            url = message.senderAvatar,
                            modifier = Modifier.clickable {
                                onAvatarClick()
                            },
                            size = 36.dp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                } else if (!isMine) {
                    Spacer(modifier = Modifier.width(44.dp))
                }
    
                val hideMsgCard = (message.contentType == MessageItem.CONTENT_TYPE_IMAGE
                    || message.contentType == MessageItem.CONTENT_TYPE_STICKER
                    || message.contentType == MessageItem.CONTENT_TYPE_FILE)
                    && !message.isRecalled
    
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = if (isMine) bubbleCornerRadius.dp else if (isLastFromSender) bubbleCornerRadius.dp else (bubbleCornerRadius / 4).dp,
                                topEnd = if (isMine) if (isLastFromSender) bubbleCornerRadius.dp else (bubbleCornerRadius / 4).dp else bubbleCornerRadius.dp,
                                bottomStart = if (isMine) bubbleCornerRadius.dp else if (isFirstFromSender) bubbleCornerRadius.dp else (bubbleCornerRadius / 4).dp,
                                bottomEnd = if (isMine) if (isFirstFromSender) bubbleCornerRadius.dp else (bubbleCornerRadius / 4).dp else bubbleCornerRadius.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hideMsgCard)
                                    Color.Transparent
                                else if (isMine)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = bubbleOpacity)
                                else
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = bubbleOpacity)
                            )
                        ) {
                            Column(modifier = Modifier.padding(if (hideMsgCard) 0.dp else 8.dp)) {
                                if (!isMine && isLastFromSender && !hideMsgCard) {
                                    Row(
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (privateMode) "用户 ${message.senderId.maskMiddle()}" else message.senderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        if (message.senderType == 3) {
                                            Surface(
                                                shape = RoundedCornerShape(50.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "机器人",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
    
                                message.cmdName?.let {
                                    Text(
                                        text = "/$it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
    
                                if (message.quoteMsgText != null) {
                                    val quoteText = message.quoteMsgText
                                    Surface(
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(22.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary,
                                                        RoundedCornerShape(2.dp)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            if (message.quoteImageUrl != null) {
                                                val builder = ImageRequest.Builder(context)
                                                    .data(message.quoteImageUrl)
    
                                                if (message.quoteImageUrl.contains("chat-img.jwznb.com") ||
                                                    message.quoteImageUrl.contains("jwznb.com") ||
                                                    message.quoteImageUrl.contains("myapp.jwznb.com")) {
                                                    builder.setHeader("Referer", "https://myapp.jwznb.com")
                                                }
    
                                                AsyncImage(
                                                    model = builder.build(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(
                                                text = quoteText,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
    
                                if (message.isRecalled) {
                                    Text(
                                        text = "此消息已被撤回",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                } else {
                                    when (message.contentType) {
                                        MessageItem.CONTENT_TYPE_TEXT,
                                        MessageItem.CONTENT_TYPE_MARKDOWN -> {
                                            if (message.contentType == MessageItem.CONTENT_TYPE_MARKDOWN) {
                                                MarkdownRenderer.Render(content = message.content)
                                            } else {
                                                val timeId = "time_${message.msgId}"
                                                val textMeasurer = rememberTextMeasurer()
                                                
                                                val timeText = buildString {
                                                    append(timestampDisplay)
                                                    if (message.isEdited) append(" 已编辑")
                                                }
                                                
                                                val timeWidth = textMeasurer.measure(
                                                    text = AnnotatedString(timeText),
                                                    style = MaterialTheme.typography.labelSmall
                                                ).size.width
                                                
                                                val density = LocalDensity.current
                                                val timeWidthSp = with(density) { timeWidth.toSp() }
                                                
                                                val textWithTime = buildAnnotatedString {
                                                    append(message.content)
                                                    append(" ")
                                                    appendInlineContent(timeId, " ")
                                                }
                                                
                                                val inlineContent = mapOf(
                                                    timeId to InlineTextContent(
                                                        placeholder = Placeholder(
                                                            width = timeWidthSp,
                                                            height = 1.em,
                                                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom
                                                        )
                                                    ) {
                                                        Row (
                                                            modifier = Modifier.wrapContentWidth(unbounded = true)
                                                        ) {
                                                            Text(
                                                                text = timestampDisplay,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                maxLines = 1,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                            )
                                                            if (message.isEdited) {
                                                                Text(
                                                                    text = " 已编辑",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    maxLines = 1,
                                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                )
                                                
                                                Text(
                                                    text = textWithTime,
                                                    inlineContent = inlineContent,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            }
                                        }
                                        
                                        MessageItem.CONTENT_TYPE_HTML -> {
                                            UnifiedHtmlWebView(
                                                htmlContent = message.content,
                                                modifier = Modifier.fillMaxWidth(),
                                                onImageClick = { imageUrl ->
                                                    val allImages = extractImageUrls(message.content)
                                                    imageList = allImages
                                                    currentImageIndex = allImages.indexOf(imageUrl).coerceAtLeast(0)
                                                    showImageViewer = true
                                                }
                                            )
                                        }
        
                                        MessageItem.CONTENT_TYPE_IMAGE,
                                        MessageItem.CONTENT_TYPE_STICKER -> {
                                            message.imageUrl?.let { url ->
                                                val builder = ImageRequest.Builder(context)
                                                    .data(url)
        
                                                if (url.contains("chat-img.jwznb.com") ||
                                                    url.contains("jwznb.com") ||
                                                    url.contains("myapp.jwznb.com")) {
                                                    builder.setHeader("Referer", "https://myapp.jwznb.com")
                                                }
        
                                                Box {
                                                    AsyncImage(
                                                        model = builder.build(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.FillWidth,
                                                        modifier = Modifier
                                                            .then(
                                                                if (isLastFromSender || message.quoteMsgText != null)
                                                                    Modifier.clip(
                                                                        RoundedCornerShape(
                                                                            topStart = bubbleCornerRadius.dp,
                                                                            topEnd = bubbleCornerRadius.dp
                                                                        )
                                                                    )
                                                                 else Modifier
                                                            )
                                                            .combinedClickable(
                                                                onClick = { onImageClick(message) },
                                                                onLongClick = { onLongPress(message) }
                                                            )
                                                    )
        
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(end = 6.dp, bottom = 6.dp)
                                                            .background(
                                                                color = Color.Black.copy(alpha = 0.3f),
                                                                shape = RoundedCornerShape(50.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        if (message.contentType == MessageItem.CONTENT_TYPE_STICKER) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Mood,
                                                                contentDescription = "mood",
                                                                modifier = Modifier.size(12.dp),
                                                                tint = Color.White
                                                            )
                                                        }
                                                        Text(
                                                            text = timestampDisplay,
                                                            fontSize = 10.sp,
                                                            lineHeight = 16.sp,
                                                            maxLines = 1,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
    
                                        MessageItem.CONTENT_TYPE_FILE -> {
                                            message.fileName?.let { fileName ->
                                                val progress = downloadProgress ?: 0f
                                                val isDownloading = downloadProgress != null && downloadProgress < 1f
                                                val isComplete = isDownloaded || (downloadProgress != null && progress >= 1f)
    
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .then(
                                                            if (isLastFromSender || message.quoteMsgText != null)
                                                                Modifier.clip(
                                                                    RoundedCornerShape(
                                                                        topStart = bubbleCornerRadius.dp,
                                                                        topEnd = bubbleCornerRadius.dp
                                                                    )
                                                                )
                                                            else Modifier
                                                        )
                                                        .background(
                                                            if (isMine)
                                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = bubbleOpacity)
                                                            else
                                                                MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = bubbleOpacity)
                                                        )
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (!isDownloading) {
                                                                    onDownloadClick(message)
                                                                }
                                                            },
                                                            onLongClick = { onLongPress(message) }
                                                        )
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            if (isDownloading) {
                                                                CircularProgressIndicator(
                                                                    progress = { progress },
                                                                    modifier = Modifier.size(30.dp),
                                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                                    strokeWidth = 2.dp
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = if (isComplete) Icons.Rounded.Check else getFileIcon(fileName),
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(24.dp),
                                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                                )
                                                            }
                                                        }
                                                    }
    
                                                    Spacer(modifier = Modifier.width(12.dp))
    
                                                    Column(modifier = Modifier.weight(1f)) {
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
                                                            Text(
                                                                text = timestampDisplay,
                                                                fontSize = 12.sp,
                                                                lineHeight = 18.sp,
                                                                maxLines = 1,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                            )
    
                                                            if (isDownloading) {
                                                                Text(
                                                                    text = " ${(progress * 100).toInt()}%",
                                                                    fontSize = 12.sp,
                                                                    lineHeight = 18.sp,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            } else if (isComplete) {
                                                                Text(
                                                                    text = " 已下载",
                                                                    fontSize = 12.sp,
                                                                    lineHeight = 18.sp,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    }
    
                                                    if (isComplete) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.CheckCircle,
                                                            contentDescription = "已下载",
                                                            modifier = Modifier.size(20.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    } else if (isDownloading) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "取消下载",
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clickable { /* 取消下载逻辑 */ },
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Download,
                                                            contentDescription = "下载",
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clickable { onDownloadClick(message) },
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
        
                                        else -> {
                                            Text(
                                                text = "暂不支持解析此消息：${message.contentType}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
    
                                if ((!hideMsgCard && message.contentType != MessageItem.CONTENT_TYPE_TEXT) || message.isRecalled) {
                                    Row(
                                        modifier = Modifier.align(if (isMine) Alignment.End else Alignment.Start)
                                    ) {
                                        Text(
                                            text = timestampDisplay,
                                            fontSize = 10.sp,
                                            lineHeight = 16.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        if (message.isEdited && !message.isRecalled) {
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
                            }
                        }
                    }
    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenuChanged(null) },
                        modifier = Modifier.align(if (isMine) Alignment.TopStart else Alignment.TopEnd)
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
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
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
                                Icon(Icons.Rounded.FormatQuote, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
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
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
    
                if (isMine && showAvatar && showMyBubbleAvatarSetting) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Avatar(
                        url = message.senderAvatar,
                        modifier = Modifier.clickable {
                            onAvatarClick()
                        },
                        size = 36.dp
                    )
                } else if (isMine && showMyBubbleAvatarSetting) {
                    Spacer(modifier = Modifier.width(44.dp))
                }
            }
        }
    }
}

private fun getFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "apk" -> Icons.Rounded.Android
        "pdf" -> Icons.Rounded.PictureAsPdf
        "doc", "docx" -> Icons.Rounded.Description
        "xls", "xlsx" -> Icons.Rounded.TableChart
        "ppt", "pptx" -> Icons.Rounded.Slideshow
        "zip", "rar", "7z", "tar", "gz" -> Icons.Rounded.FolderZip
        "mp3", "wav", "aac", "flac", "ogg", "m4a" -> Icons.Rounded.AudioFile
        "mp4", "avi", "mkv", "mov", "flv" -> Icons.Rounded.VideoFile
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> Icons.Rounded.Image
        "txt", "md", "json", "xml", "html", "css", "js", "kt", "java" -> Icons.Rounded.Code
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size.toFloat() / (1024 * 1024))}MB"
        else -> "${"%.2f".format(size.toFloat() / (1024 * 1024 * 1024))}GB"
    }
}

private fun extractImageUrls(html: String): List<String> {
    val regex = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    return regex.findAll(html).map { it.groupValues[1] }.toList()
}

fun String.maskMiddle(): String {
    return when {
        length <= 2 -> this
        length <= 4 -> "${first()}***${last()}"
        else -> "${first()}${"*".repeat(length - 2)}${last()}"
    }
}