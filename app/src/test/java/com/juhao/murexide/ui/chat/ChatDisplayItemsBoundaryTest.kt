package com.juhao.murexide.ui.chat

import com.juhao.murexide.data.MessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDisplayItemsBoundaryTest {
    @Test
    fun `empty message list produces no display items`() {
        assertTrue(
            computeDisplayItems(
                messages = emptyList(),
                chatType = 2,
                ownerId = null,
                adminIds = emptySet()
            ).isEmpty()
        )
    }

    @Test
    fun `adjacent messages from one sender form one visual group`() {
        val items = computeDisplayItems(
            messages = listOf(message("new", "sender"), message("old", "sender")),
            chatType = 2,
            ownerId = null,
            adminIds = emptySet()
        )

        assertTrue(items[0].isFirstFromSender)
        assertFalse(items[0].isLastFromSender)
        assertFalse(items[1].isFirstFromSender)
        assertTrue(items[1].isLastFromSender)
    }

    @Test
    fun `tip messages split neighboring messages from the same sender`() {
        val items = computeDisplayItems(
            messages = listOf(
                message("new", "sender"),
                message("tip", "system", MessageItem.CONTENT_TYPE_TIP),
                message("old", "sender")
            ),
            chatType = 2,
            ownerId = null,
            adminIds = emptySet()
        )

        assertTrue(items[0].isLastFromSender)
        assertTrue(items[1].isFirstFromSender)
        assertTrue(items[1].isLastFromSender)
        assertTrue(items[2].isFirstFromSender)
    }

    @Test
    fun `group roles prefer owner over admin and are absent outside normal group messages`() {
        val owner = computeDisplayItems(
            messages = listOf(message("owner", "owner")),
            chatType = 2,
            ownerId = "owner",
            adminIds = setOf("owner")
        ).single()
        val admin = computeDisplayItems(
            messages = listOf(message("admin", "admin")),
            chatType = 2,
            ownerId = "owner",
            adminIds = setOf("admin")
        ).single()
        val bot = computeDisplayItems(
            messages = listOf(message("bot", "owner", senderType = 3)),
            chatType = 2,
            ownerId = "owner",
            adminIds = setOf("owner")
        ).single()
        val direct = computeDisplayItems(
            messages = listOf(message("direct", "owner")),
            chatType = 1,
            ownerId = "owner",
            adminIds = setOf("owner")
        ).single()

        assertEquals("群主", owner.roleLabel)
        assertEquals("管理员", admin.roleLabel)
        assertEquals(null, bot.roleLabel)
        assertEquals(null, direct.roleLabel)
    }

    private fun message(
        id: String,
        senderId: String,
        contentType: Int = MessageItem.CONTENT_TYPE_TEXT,
        senderType: Int = 1
    ) = MessageItem(
        msgId = id,
        senderId = senderId,
        senderName = senderId,
        senderAvatar = "",
        senderType = senderType,
        contentType = contentType,
        timestamp = 1,
        direction = "left"
    )
}
