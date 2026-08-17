package com.juhao.murexide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ConversationModelsTest {
    @Test
    fun `outgoing message updates preview without increasing unread count`() {
        val otherConversation = conversation(chatId = "other", content = "other message")
        val targetConversation = conversation(
            chatId = "target",
            content = "old message",
            unreadCount = 2
        )
        val message = outgoingMessage(
            chatId = "target",
            content = "latest message",
            timestamp = 1234L
        )

        val updated = listOf(otherConversation, targetConversation).withLatestMessage(message)

        assertNotNull(updated)
        assertEquals("target", updated!![0].chatId)
        assertEquals("latest message", updated[0].chatContent)
        assertEquals(1234L, updated[0].timestampMs)
        assertEquals(2, updated[0].unreadMessage)
        assertEquals("other", updated[1].chatId)
    }

    @Test
    fun `outgoing media message uses display preview`() {
        val message = outgoingMessage(
            chatId = "target",
            content = "",
            contentType = MessageItem.CONTENT_TYPE_IMAGE
        )

        val updated = listOf(conversation(chatId = "target")).withLatestMessage(message)

        assertEquals("[图片消息]", updated!!.single().chatContent)
    }

    @Test
    fun `unknown conversation requests a server refresh`() {
        val message = outgoingMessage(chatId = "missing", content = "new")

        assertNull(listOf(conversation(chatId = "target")).withLatestMessage(message))
    }

    @Test
    fun `editing latest message updates preview without reordering conversation`() {
        val otherConversation = conversation(chatId = "other", content = "other message")
        val targetConversation = conversation(
            chatId = "target",
            content = "old message",
            unreadCount = 2,
            timestamp = 1_234L
        )
        val editedMessage = outgoingMessage(
            chatId = "target",
            content = "edited message",
            timestamp = 1_234L
        ).copy(isEdited = true)

        val updated = listOf(otherConversation, targetConversation)
            .withEditedLatestMessage(editedMessage)

        assertNotNull(updated)
        assertSame(otherConversation, updated[0])
        assertEquals("other", updated[0].chatId)
        assertEquals("target", updated[1].chatId)
        assertEquals("edited message", updated[1].chatContent)
        assertEquals(1_234L, updated[1].timestampMs)
        assertEquals(2, updated[1].unreadMessage)
    }

    @Test
    fun `editing older message does not replace latest preview`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "latest message",
                timestamp = 2_000L
            )
        )
        val editedOlderMessage = outgoingMessage(
            chatId = "target",
            content = "edited older message",
            timestamp = 1_000L
        ).copy(isEdited = true)

        val updated = conversations.withEditedLatestMessage(editedOlderMessage)

        assertEquals(conversations, updated)
        assertEquals("latest message", updated.single().chatContent)
    }

    @Test
    fun `edited message without timestamp leaves previews unchanged`() {
        val conversations = listOf(conversation(chatId = "target"))
        val message = outgoingMessage(
            chatId = "target",
            content = "edited message",
            timestamp = 0L
        ).copy(isEdited = true)

        assertSame(conversations, conversations.withEditedLatestMessage(message))
    }

    @Test
    fun `older push cannot replace the latest preview`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "latest message",
                timestamp = 2_000L,
                latestMessageId = "latest-id",
                latestMessageSeq = 20L
            )
        )
        val delayedMessage = outgoingMessage(
            chatId = "target",
            content = "delayed message",
            timestamp = 1_000L,
            msgId = "older-id",
            msgSeq = 10L
        ).copy(direction = "left")

        val updated = conversations.withLatestMessage(delayedMessage)

        assertSame(conversations, updated)
        assertEquals("latest message", updated!!.single().chatContent)
    }

    @Test
    fun `newer message sequence replaces preview even when timestamp moves backwards`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "old message",
                timestamp = 2_000L,
                latestMessageId = "old-id",
                latestMessageSeq = 20L
            )
        )
        val latestMessage = outgoingMessage(
            chatId = "target",
            content = "latest message",
            timestamp = 1_999L,
            msgId = "latest-id",
            msgSeq = 21L
        )

        val updated = conversations.withLatestMessage(latestMessage)!!

        assertEquals("latest message", updated.single().chatContent)
        assertEquals(21L, updated.single().latestMessageSeq)
    }

    @Test
    fun `older message sequence cannot replace preview with a later timestamp`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "latest message",
                timestamp = 2_000L,
                latestMessageId = "latest-id",
                latestMessageSeq = 21L
            )
        )
        val delayedMessage = outgoingMessage(
            chatId = "target",
            content = "delayed message",
            timestamp = 3_000L,
            msgId = "older-id",
            msgSeq = 20L
        )

        val updated = conversations.withLatestMessage(delayedMessage)

        assertSame(conversations, updated)
        assertEquals("latest message", updated!!.single().chatContent)
    }

    @Test
    fun `duplicate latest push does not reorder or increase unread count`() {
        val otherConversation = conversation(chatId = "other", timestamp = 3_000L)
        val targetConversation = conversation(
            chatId = "target",
            content = "local content",
            unreadCount = 2,
            timestamp = 2_000L,
            latestMessageId = "same-id",
            latestMessageSeq = 42L
        )
        val duplicate = outgoingMessage(
            chatId = "target",
            content = "server content",
            timestamp = 2_000L,
            msgId = "same-id",
            msgSeq = 42L
        ).copy(direction = "left")

        val updated = listOf(otherConversation, targetConversation)
            .withLatestMessage(duplicate)!!

        assertEquals("other", updated[0].chatId)
        assertEquals("target", updated[1].chatId)
        assertEquals("server content", updated[1].chatContent)
        assertEquals(2, updated[1].unreadMessage)
    }

    @Test
    fun `same id in another chat type does not update the wrong conversation`() {
        val group = conversation(chatId = "shared", chatType = 2, content = "group")
        val user = conversation(chatId = "shared", chatType = 1, content = "user")
        val directMessage = outgoingMessage(
            chatId = "shared",
            chatType = 1,
            content = "direct latest",
            timestamp = 3_000L
        )

        val updated = listOf(group, user).withLatestMessage(directMessage)!!

        assertEquals(1, updated[0].chatType)
        assertEquals("direct latest", updated[0].chatContent)
        assertEquals("group", updated[1].chatContent)
    }

    @Test
    fun `send timestamp identifies latest message when conversation update time differs`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "old content",
                timestamp = 3_000L,
                sendTimestamp = 2_000L
            )
        )
        val editedMessage = outgoingMessage(
            chatId = "target",
            content = "edited content",
            timestamp = 2_000L
        ).copy(isEdited = true)

        val updated = conversations.withEditedLatestMessage(editedMessage)

        assertEquals("edited content", updated.single().chatContent)
        assertEquals(2_000L, updated.single().latestMessageTimestamp)
    }

    @Test
    fun `known latest message id allows timestamp-less edit`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "old content",
                timestamp = 2_000L,
                latestMessageId = "message-id"
            )
        )
        val editedMessage = outgoingMessage(
            chatId = "target",
            content = "edited content",
            timestamp = 0L
        ).copy(isEdited = true)

        val updated = conversations.withEditedLatestMessage(editedMessage)

        assertEquals("edited content", updated.single().chatContent)
    }

    @Test
    fun `stream chunks only append to the matching latest text message`() {
        val latest = conversation(
            chatId = "target",
            content = "hello",
            latestMessageId = "latest-id",
            latestContentType = MessageItem.CONTENT_TYPE_TEXT
        )
        val other = conversation(
            chatId = "other",
            content = "unchanged",
            latestMessageId = "other-id",
            latestContentType = MessageItem.CONTENT_TYPE_TEXT
        )

        val updated = listOf(latest, other)
            .withStreamedLatestMessage("latest-id", " world")

        assertEquals("hello world", updated[0].chatContent)
        assertEquals("unchanged", updated[1].chatContent)
        assertSame(updated, updated.withStreamedLatestMessage("older-id", "ignored"))
    }

    @Test
    fun `recalling latest message replaces preview with recall state`() {
        val conversations = listOf(
            conversation(
                chatId = "target",
                content = "latest content",
                timestamp = 2_000L,
                latestMessageId = "message-id"
            )
        )
        val recalled = outgoingMessage(
            chatId = "target",
            content = "latest content",
            timestamp = 0L
        ).copy(isRecalled = true)

        val updated = conversations.withRecalledLatestMessage(recalled)

        assertEquals("此消息已被撤回", updated.single().chatContent)
    }

    @Test
    fun `message id is required before an equal timestamp edit is trusted`() {
        val conversation = conversation(
            chatId = "target",
            timestamp = 2_000L,
            latestMessageId = null
        )
        val edit = outgoingMessage(
            chatId = "target",
            content = "edited",
            timestamp = 2_000L,
            msgId = "unknown-id"
        )

        assertEquals(LatestMessageRelation.UNKNOWN, conversation.relationToLatest(edit))
        assertEquals(
            LatestMessageRelation.DIFFERENT,
            conversation.relationToLatest(edit.copy(timestamp = 1_999L))
        )
    }

    private fun conversation(
        chatId: String,
        content: String = "old message",
        unreadCount: Int = 0,
        at: Int = 0,
        timestamp: Long = 1L,
        sendTimestamp: Long = timestamp,
        chatType: Int = 2,
        latestMessageId: String? = null,
        latestMessageSeq: Long = 0L,
        latestContentType: Int = 0
    ) = ConversationItem(
        chatId = chatId,
        chatType = chatType,
        name = chatId,
        chatContent = content,
        timestampMs = timestamp,
        sendTimestamp = sendTimestamp,
        unreadMessage = unreadCount,
        at = at,
        avatarUrl = "",
        latestMessageId = latestMessageId,
        latestMessageSeq = latestMessageSeq,
        latestContentType = latestContentType
    )

    private fun outgoingMessage(
        chatId: String,
        content: String,
        contentType: Int = MessageItem.CONTENT_TYPE_TEXT,
        timestamp: Long = 2L,
        chatType: Int = 2,
        msgId: String = "message-id",
        msgSeq: Long = 0L
    ) = MessageItem(
        msgId = msgId,
        senderId = "me",
        senderName = "Me",
        senderAvatar = "",
        chatId = chatId,
        chatType = chatType,
        content = content,
        contentType = contentType,
        timestamp = timestamp,
        msgSeq = msgSeq,
        direction = "right"
    )
}
