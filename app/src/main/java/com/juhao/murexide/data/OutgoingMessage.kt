package com.juhao.murexide.data

private const val IMAGE_BASE_URL = "https://chat-img.jwznb.com"
private const val VIDEO_BASE_URL = "https://chat-video1.jwznb.com"
private const val AUDIO_BASE_URL = "https://chat-audio1.jwznb.com"
private const val FILE_BASE_URL = "https://chat-file.jwznb.com"

internal fun createOutgoingMessage(
    msgId: String,
    senderId: String,
    senderName: String,
    senderAvatar: String,
    chatId: String,
    chatType: Int,
    content: MessageContent,
    contentType: Int,
    quoteMsgId: String?,
    commandId: Long? = null,
    commandName: String? = null,
    timestamp: Long = System.currentTimeMillis()
): MessageItem {
    return MessageItem(
        msgId = msgId,
        senderId = senderId,
        senderName = senderName,
        senderAvatar = senderAvatar,
        senderType = 1,
        chatId = chatId,
        chatType = chatType,
        content = content.text,
        contentType = contentType,
        timestamp = timestamp,
        direction = "right",
        quoteMsgId = quoteMsgId,
        quoteMsgText = content.quoteMsgText,
        quoteImageUrl = content.quoteImageUrl,
        stickerPackId = content.stickerPackId,
        imageUrl = content.image.toMediaUrl(IMAGE_BASE_URL),
        imageWidth = content.media?.width,
        imageHeight = content.media?.height,
        audioUrl = content.audio.toMediaUrl(AUDIO_BASE_URL),
        audioTime = content.audioTime,
        videoUrl = content.video.toMediaUrl(VIDEO_BASE_URL),
        fileUrl = content.fileKey.toMediaUrl(FILE_BASE_URL),
        fileName = content.fileName,
        fileSize = content.fileSize,
        cmdName = commandName,
        cmdId = commandId
    )
}

internal fun upsertNewestMessage(
    messages: List<MessageItem>,
    message: MessageItem
): List<MessageItem> {
    if (messages.none { it.msgId == message.msgId }) {
        return listOf(message) + messages
    }
    return messages.map { existing ->
        if (existing.msgId == message.msgId) {
            mergeMessageIdentity(existing, message)
        } else {
            existing
        }
    }
}

internal fun reconcileLoadedMessages(
    existingMessages: List<MessageItem>,
    loadedMessages: List<MessageItem>
): List<MessageItem> {
    val existingById = existingMessages.associateBy(MessageItem::msgId)
    return loadedMessages.map { loaded ->
        val existing = existingById[loaded.msgId]
        if (existing != null && loaded.isRecalled && existing.hasReliableSender) {
            mergeMessageIdentity(existing, loaded)
        } else {
            loaded
        }
    }
}

internal fun mergeIncrementalMessages(
    existingMessages: List<MessageItem>,
    updatedMessages: List<MessageItem>,
    anchorMessage: MessageItem
): List<MessageItem> {
    if (updatedMessages.isEmpty()) return existingMessages

    val updateById = updatedMessages
        .asSequence()
        .filter { it.msgId.isNotBlank() }
        .groupBy(MessageItem::msgId)
        .mapValues { (_, messages) -> messages.maxBy(MessageItem::updateTimestamp) }
    if (updateById.isEmpty()) return existingMessages

    val existingIds = existingMessages.mapTo(mutableSetOf(), MessageItem::msgId)
    val replacedMessages = existingMessages.map { existing ->
        updateById[existing.msgId]?.let { incoming ->
            if (incoming.updateTimestamp >= existing.updateTimestamp) {
                mergeMessageIdentity(existing, incoming)
            } else {
                existing
            }
        } ?: existing
    }
    val newlyReceived = updateById.values.filter { incoming ->
        incoming.msgId !in existingIds && incoming.isStrictlyNewerThan(anchorMessage)
    }
    if (newlyReceived.isEmpty()) return replacedMessages

    val anchorIndex = replacedMessages.indexOfFirst { it.msgId == anchorMessage.msgId }
    val existingNewerPrefix = if (anchorIndex >= 0) {
        replacedMessages.take(anchorIndex)
    } else {
        emptyList()
    }
    val unchangedTail = if (anchorIndex >= 0) {
        replacedMessages.drop(anchorIndex)
    } else {
        replacedMessages
    }
    val newPrefix = (existingNewerPrefix + newlyReceived)
        .distinctBy(MessageItem::msgId)
        .sortedWith(newestMessageFirstComparator)

    return newPrefix + unchangedTail
}

private val newestMessageFirstComparator = Comparator<MessageItem> { left, right ->
    when {
        left.msgSeq > 0L && right.msgSeq > 0L && left.msgSeq != right.msgSeq ->
            right.msgSeq.compareTo(left.msgSeq)
        left.timestamp != right.timestamp -> right.timestamp.compareTo(left.timestamp)
        else -> right.updateTimestamp.compareTo(left.updateTimestamp)
    }
}

private fun MessageItem.isStrictlyNewerThan(other: MessageItem): Boolean {
    if (msgSeq > 0L && other.msgSeq > 0L) return msgSeq > other.msgSeq
    return timestamp > other.timestamp
}

private fun mergeMessageIdentity(
    existing: MessageItem,
    incoming: MessageItem
): MessageItem {
    if (incoming.isRecalled && existing.hasReliableSender) {
        return incoming.copy(
            senderId = existing.senderId,
            senderName = existing.senderName,
            senderAvatar = existing.senderAvatar,
            senderType = existing.senderType,
            tags = existing.tags,
            direction = existing.direction,
            hasReliableSender = true
        )
    }

    return incoming.copy(
        senderId = incoming.senderId.ifBlank { existing.senderId },
        senderName = incoming.senderName.ifBlank { existing.senderName },
        senderAvatar = incoming.senderAvatar.ifBlank { existing.senderAvatar }
    )
}

private fun String?.toMediaUrl(baseUrl: String): String? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        else -> "$baseUrl/${value.trimStart('/')}"
    }
}
