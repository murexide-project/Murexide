package com.juhao.murexide.ui.chat

import com.juhao.murexide.data.MessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPaginationBoundaryTest {
    @Test
    fun `blank final message id cannot advance the media cursor`() {
        val page = buildEarlierChatMediaPage(
            messages = listOf(
                message("image", MessageItem.CONTENT_TYPE_IMAGE, imageUrl = "image.jpg"),
                message(" ", MessageItem.CONTENT_TYPE_TEXT)
            ),
            knownMessageIds = emptySet(),
            currentAnchorMessageId = "current",
            pageSize = 2
        )

        assertEquals(listOf("image"), page.entries.map { it.messageId })
        assertNull(page.nextAnchorMessageId)
        assertFalse(page.hasMoreMessages)
    }

    @Test
    fun `repeated anchor prevents another history page even when page is full`() {
        val messages = List(2) { index ->
            message(
                id = if (index == 1) "current" else "older",
                contentType = MessageItem.CONTENT_TYPE_TEXT
            )
        }

        val page = buildEarlierChatMediaPage(
            messages = messages,
            knownMessageIds = emptySet(),
            currentAnchorMessageId = "current",
            pageSize = 2
        )

        assertEquals("current", page.nextAnchorMessageId)
        assertFalse(page.hasMoreMessages)
        assertFalse(page.shouldContinueLoading)
    }

    @Test
    fun `missing selected media returns no gallery instead of an invalid index`() {
        assertNull(
            buildChatMediaGallery(
                messages = listOf(message("image", MessageItem.CONTENT_TYPE_IMAGE, imageUrl = "image.jpg")),
                selectedMessageId = "missing"
            )
        )
        assertNull(buildChatMediaGallery(emptyList(), selectedMessageId = "missing"))
    }

    @Test
    fun `older history filters blank and already known message ids`() {
        val page = resolveOlderMessagePage(
            knownMessageIds = setOf("known"),
            currentAnchorMessageId = "current",
            messages = listOf(
                message("known", MessageItem.CONTENT_TYPE_TEXT),
                message("older", MessageItem.CONTENT_TYPE_TEXT),
                message(" ", MessageItem.CONTENT_TYPE_TEXT)
            )
        )

        assertEquals(listOf("older"), page.newMessages.map { it.msgId })
        assertNull(page.nextAnchorMessageId)
        assertFalse(page.madeCursorProgress)
    }

    private fun message(
        id: String,
        contentType: Int,
        imageUrl: String? = null
    ) = MessageItem(
        msgId = id,
        senderId = "sender",
        senderName = "Sender",
        senderAvatar = "",
        contentType = contentType,
        timestamp = 1,
        direction = "left",
        imageUrl = imageUrl
    )
}
