package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class ConversationItem(
    val chatId: String,
    val chatType: Int,
    val name: String,
    val remark: String? = null,
    val chatContent: String,
    val timestampMs: Long,
    val unreadMessage: Int = 0,
    val at: Int = 0,
    val avatarUrl: String,
    val doNotDisturb: Int = 0,
    val certificationLevel: Int = 0,
    val sendTimestamp: Long = timestampMs,
    val latestMessageId: String? = null,
    val latestMessageSeq: Long = 0,
    val latestContentType: Int = 0
) {
    val displayName: String
        get() = remark?.takeIf { it.isNotBlank() } ?: name
    
    val hasUnread: Boolean
        get() = unreadMessage > 0
    
    val isAtMentioned: Boolean
        get() = at > 0

    /** The original send time of the message represented by [chatContent]. */
    val latestMessageTimestamp: Long
        get() = sendTimestamp.takeIf { it > 0 } ?: timestampMs
}

internal enum class LatestMessageRelation {
    MATCHES,
    DIFFERENT,
    UNKNOWN
}

internal fun List<ConversationItem>.findConversationFor(
    message: MessageItem
): ConversationItem? {
    val index = findConversationIndex(message)
    return getOrNull(index)
}

internal fun ConversationItem.relationToLatest(
    message: MessageItem
): LatestMessageRelation {
    if (latestMessageId != null && message.msgId.isNotBlank()) {
        return if (latestMessageId == message.msgId) {
            LatestMessageRelation.MATCHES
        } else {
            LatestMessageRelation.DIFFERENT
        }
    }
    if (latestMessageTimestamp > 0L &&
        message.timestamp > 0L &&
        message.timestamp < latestMessageTimestamp
    ) {
        return LatestMessageRelation.DIFFERENT
    }
    return LatestMessageRelation.UNKNOWN
}

internal fun List<ConversationItem>.withLatestMessage(
    message: MessageItem,
    incrementUnread: Boolean = true
): List<ConversationItem>? {
    val index = findConversationIndex(message)
    if (index == -1) return null

    val oldConversation = this[index]
    val sameMessage = message.msgId.isNotBlank() &&
        oldConversation.latestMessageId == message.msgId
    val order = message.compareToLatest(oldConversation, sameMessage)
    if (order < 0) return this

    val isStrictlyNewer = !sameMessage && order > 0
    val useIncomingOrdering = message.timestamp > 0 &&
        (!sameMessage || message.msgSeq > 0 || oldConversation.latestMessageSeq == 0L)
    val updatedConversation = oldConversation.copy(
        chatContent = message.getDisplayContent(),
        timestampMs = if (useIncomingOrdering) message.timestamp else oldConversation.timestampMs,
        sendTimestamp = if (useIncomingOrdering) message.timestamp else oldConversation.sendTimestamp,
        unreadMessage = oldConversation.unreadMessage +
            if (incrementUnread && isStrictlyNewer && !message.isMine) 1 else 0,
        latestMessageId = message.msgId.takeIf { it.isNotBlank() }
            ?: oldConversation.latestMessageId,
        latestMessageSeq = message.msgSeq.takeIf { it > 0 }
            ?: oldConversation.latestMessageSeq,
        latestContentType = message.contentType.takeIf { it > 0 }
            ?: oldConversation.latestContentType
    )

    val conversations = toMutableList()
    if (isStrictlyNewer) {
        conversations.removeAt(index)
        conversations.add(0, updatedConversation)
    } else {
        conversations[index] = updatedConversation
    }
    return conversations
}

internal fun List<ConversationItem>.withEditedLatestMessage(
    message: MessageItem
): List<ConversationItem> {
    val index = findConversationIndex(message)
    if (index == -1) return this

    val conversation = this[index]
    if (!conversation.represents(message)) return this

    return toMutableList().apply {
        this[index] = conversation.copy(
            chatContent = message.getDisplayContent(),
            latestMessageId = message.msgId.takeIf { it.isNotBlank() }
                ?: conversation.latestMessageId,
            latestMessageSeq = message.msgSeq.takeIf { it > 0 }
                ?: conversation.latestMessageSeq,
            latestContentType = message.contentType.takeIf { it > 0 }
                ?: conversation.latestContentType
        )
    }
}

internal fun List<ConversationItem>.withStreamedLatestMessage(
    msgId: String,
    content: String
): List<ConversationItem> {
    if (msgId.isBlank() || content.isEmpty()) return this
    val index = indexOfFirst { it.latestMessageId == msgId }
    if (index == -1) return this

    val conversation = this[index]
    if (conversation.latestContentType != MessageItem.CONTENT_TYPE_TEXT) return this

    return toMutableList().apply {
        this[index] = conversation.copy(chatContent = conversation.chatContent + content)
    }
}

internal fun List<ConversationItem>.withRecalledLatestMessage(
    message: MessageItem
): List<ConversationItem> {
    val index = findConversationIndex(message)
    if (index == -1) return this

    val conversation = this[index]
    if (!conversation.represents(message)) return this

    return toMutableList().apply {
        this[index] = conversation.copy(
            chatContent = message.copy(isRecalled = true).getDisplayContent(),
            latestMessageId = message.msgId.takeIf { it.isNotBlank() }
                ?: conversation.latestMessageId,
            latestMessageSeq = message.msgSeq.takeIf { it > 0 }
                ?: conversation.latestMessageSeq
        )
    }
}

internal fun List<ConversationItem>.withLatestMessageIdentity(
    message: MessageItem
): List<ConversationItem> {
    if (message.msgId.isBlank()) return this
    val index = findConversationIndex(message)
    if (index == -1) return this

    val conversation = this[index]
    if (conversation.latestMessageTimestamp > 0L &&
        message.timestamp > 0L &&
        conversation.latestMessageTimestamp != message.timestamp
    ) {
        return this
    }

    return toMutableList().apply {
        this[index] = conversation.copy(
            latestMessageId = message.msgId,
            latestMessageSeq = message.msgSeq.takeIf { it > 0 }
                ?: conversation.latestMessageSeq,
            latestContentType = message.contentType.takeIf { it > 0 }
                ?: conversation.latestContentType
        )
    }
}

private fun List<ConversationItem>.findConversationIndex(message: MessageItem): Int {
    if (message.msgId.isNotBlank()) {
        val messageIndex = indexOfFirst { it.latestMessageId == message.msgId }
        if (messageIndex != -1) return messageIndex
    }

    return indexOfFirst { conversation ->
        conversation.chatType == message.chatType &&
            (conversation.chatId == message.chatId ||
                (message.chatType == 1 &&
                    message.senderId.isNotBlank() &&
                    conversation.chatId == message.senderId))
    }
}

private fun MessageItem.compareToLatest(
    conversation: ConversationItem,
    sameMessage: Boolean
): Int {
    if (sameMessage) return 0

    val latestSeq = conversation.latestMessageSeq
    if (msgSeq > 0L && latestSeq > 0L && msgSeq != latestSeq) {
        return msgSeq.compareTo(latestSeq)
    }

    val latestTimestamp = conversation.latestMessageTimestamp
    if (timestamp <= 0L) return if (latestTimestamp <= 0L) 0 else -1
    if (latestTimestamp <= 0L) return 1
    if (timestamp != latestTimestamp) return timestamp.compareTo(latestTimestamp)

    return when {
        msgSeq > 0L && conversation.latestMessageId != null -> 1
        else -> 0
    }
}

private fun ConversationItem.represents(message: MessageItem): Boolean {
    if (latestMessageId != null && message.msgId.isNotBlank()) {
        return latestMessageId == message.msgId
    }
    return message.timestamp > 0L && latestMessageTimestamp == message.timestamp
}
