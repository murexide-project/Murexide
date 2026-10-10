package com.juhao.murexide.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.data.MessageDisplayItem
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.reconcileLoadedMessages
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.icons.AppIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

internal enum class ChatMediaKind {
    IMAGE,
    VIDEO
}

internal data class ChatMediaGalleryEntry(
    val messageId: String,
    val sequence: Long,
    val url: String,
    val kind: ChatMediaKind
)

internal data class ChatMediaGallery(
    val entries: List<ChatMediaGalleryEntry>,
    val initialIndex: Int
)

internal data class EarlierChatMediaPage(
    val entries: List<ChatMediaGalleryEntry>,
    val nextAnchorMessageId: String?,
    val hasMoreMessages: Boolean
) {
    val shouldContinueLoading: Boolean
        get() = entries.isEmpty() && hasMoreMessages
}

internal fun buildChatMediaGallery(
    messages: List<MessageItem>,
    selectedMessageId: String
): ChatMediaGallery? {
    val entries = messages.asReversed().mapNotNull(MessageItem::toChatMediaGalleryEntry)

    val initialIndex = entries.indexOfFirst { it.messageId == selectedMessageId }
    return if (initialIndex >= 0) {
        ChatMediaGallery(entries = entries, initialIndex = initialIndex)
    } else {
        null
    }
}

internal fun MessageItem.toChatMediaGalleryEntry(): ChatMediaGalleryEntry? {
    if (isRecalled) return null

    val kind: ChatMediaKind
    val mediaUrl = when (contentType) {
        MessageItem.CONTENT_TYPE_IMAGE -> {
            kind = ChatMediaKind.IMAGE
            imageUrl
        }
        MessageItem.CONTENT_TYPE_VIDEO -> {
            kind = ChatMediaKind.VIDEO
            videoUrl
        }
        else -> return null
    }
    val url = mediaUrl?.takeIf { it.isNotBlank() } ?: return null

    return ChatMediaGalleryEntry(
        messageId = msgId,
        sequence = msgSeq,
        url = url,
        kind = kind
    )
}

internal fun buildEarlierChatMediaPage(
    messages: List<MessageItem>,
    knownMessageIds: Set<String>,
    currentAnchorMessageId: String,
    pageSize: Int
): EarlierChatMediaPage {
    val nextAnchor = messages.lastOrNull()?.msgId?.takeIf { it.isNotBlank() }
    val entries = messages
        .asReversed()
        .mapNotNull(MessageItem::toChatMediaGalleryEntry)
        .filter { it.messageId.isNotBlank() && it.messageId !in knownMessageIds }
    val hasMoreMessages = messages.size >= pageSize &&
        nextAnchor != null &&
        nextAnchor != currentAnchorMessageId

    return EarlierChatMediaPage(
        entries = entries,
        nextAnchorMessageId = nextAnchor,
        hasMoreMessages = hasMoreMessages
    )
}

internal data class ServerHistorySnapshot(
    val messages: List<MessageItem>,
    val nextAnchorMessageId: String?,
    val hasMore: Boolean
)

internal fun resolveServerHistorySnapshot(
    existingMessages: List<MessageItem>,
    serverMessages: List<MessageItem>
): ServerHistorySnapshot {
    val resolvedMessages = reconcileLoadedMessages(
        existingMessages = existingMessages,
        loadedMessages = serverMessages
    )
    val nextAnchorMessageId = resolvedMessages.lastOrNull()
        ?.msgId
        ?.takeIf { it.isNotBlank() }
    return ServerHistorySnapshot(
        messages = resolvedMessages,
        nextAnchorMessageId = nextAnchorMessageId,
        hasMore = nextAnchorMessageId != null
    )
}

internal data class OlderMessagePage(
    val newMessages: List<MessageItem>,
    val nextAnchorMessageId: String?,
    val madeCursorProgress: Boolean
)

internal data class CachedHistoryPage(
    val newMessages: List<MessageItem>,
    val nextAnchorMessage: MessageItem?,
    val madeCursorProgress: Boolean,
    val hasMore: Boolean
)

internal fun resolveOlderMessagePage(
    knownMessageIds: Set<String>,
    currentAnchorMessageId: String,
    messages: List<MessageItem>
): OlderMessagePage {
    val seenMessageIds = knownMessageIds.toMutableSet()
    val newMessages = messages.filter { message ->
        message.msgId.isNotBlank() && seenMessageIds.add(message.msgId)
    }
    val nextAnchorMessageId = messages.lastOrNull()
        ?.msgId
        ?.takeIf { it.isNotBlank() }

    return OlderMessagePage(
        newMessages = newMessages,
        nextAnchorMessageId = nextAnchorMessageId,
        madeCursorProgress = nextAnchorMessageId != null &&
            nextAnchorMessageId != currentAnchorMessageId
    )
}

internal fun resolveCachedHistoryPage(
    knownMessageIds: Set<String>,
    currentAnchorMessageId: String,
    messages: List<MessageItem>,
    pageSize: Int
): CachedHistoryPage {
    val page = resolveOlderMessagePage(
        knownMessageIds = knownMessageIds,
        currentAnchorMessageId = currentAnchorMessageId,
        messages = messages
    )
    val nextAnchorMessage = messages.lastOrNull()
        ?.takeIf { it.msgId.isNotBlank() }
    return CachedHistoryPage(
        newMessages = page.newMessages,
        nextAnchorMessage = nextAnchorMessage,
        madeCursorProgress = page.madeCursorProgress,
        hasMore = messages.size >= pageSize && page.madeCursorProgress
    )
}

internal data class AvatarPlacement(
    val message: MessageItem,
    val isMine: Boolean,
    val yPx: Float
)

@Composable
internal fun FloatingAvatarsLayer(
    listState: LazyListState,
    items: List<MessageDisplayItem>,
    listHeightPx: Int,
    composerHeightPx: Int,
    showMyAvatar: Boolean,
    onAvatarClick: (MessageItem) -> Unit,
    onAvatarLongClick: (MessageItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val avatarSize = 36.dp
    val avatarSizePx = with(density) { avatarSize.toPx() }
    val bubbleInsetPx = with(density) { 2.dp.toPx() }

    val placements by remember(items, listHeightPx, composerHeightPx, showMyAvatar, avatarSizePx) {
        derivedStateOf {
            computeAvatarPlacements(
                layoutInfo = listState.layoutInfo,
                items = items,
                listHeightPx = listHeightPx,
                composerHeightPx = composerHeightPx,
                showMyAvatar = showMyAvatar,
                avatarSizePx = avatarSizePx,
                bubbleInsetPx = bubbleInsetPx
            )
        }
    }

    Box(modifier = modifier) {
        placements.forEach { placement ->
            key(placement.message.msgId) {
                FloatingAvatarItem(
                    placement = placement,
                    avatarSize = avatarSize,
                    onAvatarClick = onAvatarClick,
                    onAvatarLongClick = onAvatarLongClick
                )
            }
        }
    }
}

@Composable
private fun BoxScope.FloatingAvatarItem(
    placement: AvatarPlacement,
    avatarSize: Dp,
    onAvatarClick: (MessageItem) -> Unit,
    onAvatarLongClick: (MessageItem) -> Unit
) {
    val message = placement.message
    val currentOnClick by rememberUpdatedState(onAvatarClick)
    val currentOnLongClick by rememberUpdatedState(onAvatarLongClick)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .align(if (placement.isMine) Alignment.TopEnd else Alignment.TopStart)
            .graphicsLayer {
                translationY = placement.yPx
            }
            .padding(horizontal = 8.dp)
            .combinedClickable(
                onClick = { currentOnClick(message) },
                onLongClick = { currentOnLongClick(message) },
                indication = null,
                interactionSource = interactionSource
            )
    ) {
        Avatar(
            url = message.senderAvatar,
            size = avatarSize
        )
    }
}

internal fun computeAvatarPlacements(
    layoutInfo: LazyListLayoutInfo,
    items: List<MessageDisplayItem>,
    listHeightPx: Int,
    composerHeightPx: Int,
    showMyAvatar: Boolean,
    avatarSizePx: Float,
    bubbleInsetPx: Float
): List<AvatarPlacement> {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return emptyList()

    val maxY = (listHeightPx - composerHeightPx).toFloat()

    class Group(
        val senderId: String,
        val isMine: Boolean,
        val messages: MutableList<MessageItem> = mutableListOf(),
        var minCellTop: Float = Float.MAX_VALUE,
        var maxCellBottom: Float = Float.MIN_VALUE,
    )

    val groups = ArrayList<Group>()
    var currentGroup: Group? = null

    for (info in visible) {
        val item = items.getOrNull(info.index) ?: continue
        val message = item.message

        if (message.contentType == MessageItem.CONTENT_TYPE_TIP) {
            currentGroup = null
            continue
        }

        val bottomInset = if (item.isFirstFromSender) bubbleInsetPx else 0f
        val topInset = if (item.isLastFromSender) bubbleInsetPx else 0f

        val rawBottom = (listHeightPx - composerHeightPx - info.offset).toFloat()
        val rawTop = rawBottom - info.size.toFloat()
        val cellBottom = rawBottom - bottomInset
        val cellTop = rawTop + topInset

        val isMine = message.isMine
        val sameSender = currentGroup != null &&
                currentGroup.senderId == message.senderId &&
                currentGroup.isMine == isMine

        if (!sameSender) {
            currentGroup = Group(senderId = message.senderId, isMine = isMine)
            groups.add(currentGroup)
        }

        currentGroup.messages.add(message)
        if (cellTop < currentGroup.minCellTop) currentGroup.minCellTop = cellTop
        if (cellBottom > currentGroup.maxCellBottom) currentGroup.maxCellBottom = cellBottom
    }

    val drawGroups = groups
        .filter { it.messages.isNotEmpty() && !(it.isMine && !showMyAvatar) }
        .sortedByDescending { it.maxCellBottom }

    val result = ArrayList<AvatarPlacement>(drawGroups.size)
    var maxTopForNext = listHeightPx.toFloat()

    for (group in drawGroups) {
        val message = group.messages.first()

        var avatarTopPx = group.maxCellBottom - avatarSizePx

        if (avatarTopPx < group.minCellTop) {
            avatarTopPx = group.minCellTop
        }
        if (avatarTopPx + avatarSizePx > maxY) {
            avatarTopPx = maxY - avatarSizePx
        }
        if (avatarTopPx + avatarSizePx > listHeightPx) {
            avatarTopPx = listHeightPx - avatarSizePx
        }

        if (avatarTopPx + avatarSizePx > maxTopForNext) {
            avatarTopPx = maxTopForNext - avatarSizePx
        }

        if (avatarTopPx < group.minCellTop) {
            avatarTopPx = group.minCellTop
        }

        maxTopForNext = avatarTopPx

        if (avatarTopPx + avatarSizePx <= 0f) continue
        if (avatarTopPx >= listHeightPx) continue

        result.add(
            AvatarPlacement(
                message = message,
                isMine = group.isMine,
                yPx = avatarTopPx
            )
        )
    }

    return result
}

@Composable
fun AnimatedScrollToBottomButton(
    visible: Boolean,
    unreadCount: Int,
    loadNewerMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "scroll_button_alpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "scroll_button_scale"
    )

    Box(
        modifier = modifier
            .wrapContentSize()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
            }
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        ) {
            SmallFloatingActionButton(
                onClick = onClick,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    imageVector = if (loadNewerMode) AppIcons.Refresh else AppIcons.KeyboardArrowDown,
                    contentDescription = "滚动到底部",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal fun calculateItemCenterScrollDistance(
    itemOffset: Int,
    itemSize: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int
): Float {
    val itemCenter = itemOffset + itemSize / 2f
    val viewportCenter = (viewportStartOffset + viewportEndOffset) / 2f
    return itemCenter - viewportCenter
}

internal suspend fun LazyListState.animateScrollToCenteredItem(index: Int) {
    val visibleItemScrollDistance = centerScrollDistance(index)
    if (visibleItemScrollDistance != null) {
        animateCenterCorrection(visibleItemScrollDistance)
        return
    }

    val itemIsAvailable = withTimeoutOrNull(2_000L.milliseconds) {
        snapshotFlow { layoutInfo.totalItemsCount > index }.first { it }
    } ?: false
    if (!itemIsAvailable) return

    animateScrollToItem(index)

    val centerScrollDistance = withTimeoutOrNull(2_000L.milliseconds) {
        snapshotFlow { centerScrollDistance(index) }.first { it != null }
    } ?: return

    animateCenterCorrection(centerScrollDistance)
}

private fun LazyListState.centerScrollDistance(index: Int): Float? {
    val currentLayoutInfo = layoutInfo
    val itemInfo = currentLayoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return null

    return calculateItemCenterScrollDistance(
        itemOffset = itemInfo.offset,
        itemSize = itemInfo.size,
        viewportStartOffset = currentLayoutInfo.viewportStartOffset,
        viewportEndOffset = currentLayoutInfo.viewportEndOffset
    )
}

private suspend fun LazyListState.animateCenterCorrection(distance: Float) {
    if (abs(distance) > 0.5f) {
        animateScrollBy(distance)
    }
}