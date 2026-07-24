package com.juhao.murexide.ui.chat

import com.juhao.murexide.data.MessageItem

internal data class ChatImageGalleryEntry(
    val messageId: String,
    val imageId: Long,
    val url: String
)

internal data class ChatImageGallery(
    val entries: List<ChatImageGalleryEntry>,
    val initialIndex: Int
)

/**
 * Builds the large-image pager in chronological order.
 *
 * The chat list is stored newest-first because it is rendered with
 * reverseLayout. Reverse it for the image viewer so a right swipe moves toward
 * earlier images. Only regular image messages belong in this gallery:
 * stickers must be skipped.
 */
internal fun buildChatImageGallery(
    messages: List<MessageItem>,
    selectedMessageId: String
): ChatImageGallery? {
    val entries = messages
        .asReversed()
        .mapNotNull { message ->
            if (message.isRecalled || message.contentType != MessageItem.CONTENT_TYPE_IMAGE) {
                return@mapNotNull null
            }

            val url = message.imageUrl?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            ChatImageGalleryEntry(
                messageId = message.msgId,
                imageId = message.msgSeq,
                url = url
            )
        }

    val initialIndex = entries.indexOfFirst { it.messageId == selectedMessageId }
    return if (initialIndex >= 0) {
        ChatImageGallery(entries = entries, initialIndex = initialIndex)
    } else {
        null
    }
}
