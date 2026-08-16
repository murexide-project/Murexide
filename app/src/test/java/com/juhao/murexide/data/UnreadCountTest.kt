package com.juhao.murexide.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadCountTest {
    @Test
    fun `total excludes muted conversations and active conversation`() {
        val conversations = listOf(
            conversation(id = "user", type = 1, unread = 2),
            conversation(id = "group", type = 2, unread = 4, muted = 1),
            conversation(id = "bot", type = 3, unread = 1)
        )

        assertEquals(3, conversations.unreadTotal())
        assertEquals(1, conversations.unreadTotal(ConversationKey("user", 1)))
        assertEquals(3, conversations.unreadTotal(ConversationKey("group", 2)))
    }

    private fun conversation(id: String, type: Int, unread: Int, muted: Int = 0) = ConversationItem(
        chatId = id,
        chatType = type,
        name = id,
        chatContent = "",
        timestampMs = 0,
        unreadMessage = unread,
        avatarUrl = "",
        doNotDisturb = muted
    )
}
