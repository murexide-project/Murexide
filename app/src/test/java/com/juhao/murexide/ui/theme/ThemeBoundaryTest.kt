package com.juhao.murexide.ui.theme

import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.StickyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeBoundaryTest {
    @Test
    fun `unknown theme modes fall back to light while oled remains dark`() {
        assertFalse(usesDarkTheme("unknown", systemInDarkTheme = true))
        assertFalse(usesDarkTheme("", systemInDarkTheme = false))
        assertTrue(usesDarkTheme("oled", systemInDarkTheme = false))
    }

    @Test
    fun `clearing UI cache removes conversations and sticky conversations together`() {
        UiCache.conversation.value = listOf(
            ConversationItem(
                chatId = "chat",
                chatType = 1,
                name = "Chat",
                chatContent = "",
                timestampMs = 0,
                avatarUrl = ""
            )
        )
        UiCache.stickyConversations.value = listOf(
            StickyItem(
                id = 1L,
                chatType = 1,
                chatId = "chat",
                chatName = "Chat",
                avatarUrl = "",
                certificationLevel = 0
            )
        )

        try {
            UiCache.clearAccountData()

            assertEquals(emptyList<ConversationItem>(), UiCache.conversation.value)
            assertEquals(emptyList<StickyItem>(), UiCache.stickyConversations.value)
        } finally {
            UiCache.clearAccountData()
        }
    }
}
