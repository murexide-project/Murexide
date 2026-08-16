package com.juhao.murexide.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.MessageTag
import com.juhao.murexide.data.StickyItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCacheBoundaryTest {
    private lateinit var accountId: String

    @Before
    fun setUp() {
        LocalCache.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        accountId = "cache-test-${System.nanoTime()}"
        LocalCache.setActiveAccount(accountId)
    }

    @After
    fun tearDown() {
        runBlocking { LocalCache.purgeAccount(accountId) }
        LocalCache.setActiveAccount(null)
    }

    @Test
    fun replaceConversationsPreservesOrderAndStoresTheAccountMd5() = runBlocking {
        LocalCache.replaceConversations(
            accountId,
            listOf(
                conversation("first", timestamp = 10L),
                conversation("second", timestamp = 20L)
            ),
            md5 = "snapshot-md5"
        )

        val cached = LocalCache.observeConversations(accountId).first { it.size == 2 }

        assertEquals(listOf("first", "second"), cached.map { it.chatId })
        assertEquals("snapshot-md5", LocalCache.conversationMd5(accountId))
    }

    @Test
    fun cacheMessagesRoundTripsSenderFieldsAndPagesBeforeAStableCursor() = runBlocking {
        val old = message(
            msgId = "old",
            timestamp = 100L,
            msgSeq = 1L,
            senderName = "Alice",
            senderAvatar = "alice.png",
            tags = listOf(MessageTag(1L, "member", "#fff"))
        )
        val newest = message(
            msgId = "new",
            timestamp = 200L,
            msgSeq = 2L,
            senderName = "Bob",
            senderAvatar = "bob.png"
        )

        LocalCache.cacheMessages(accountId, listOf(old, newest))

        val latest = LocalCache.observeMessages(accountId, "chat", 1, limit = 1)
            .first { it.size == 1 && it.first().msgId == "new" }
        val before = LocalCache.getCachedMessagesBefore(
            accountId = accountId,
            chatId = "chat",
            chatType = 1,
            beforeTimestamp = newest.timestamp,
            beforeMsgSeq = newest.msgSeq,
            beforeMsgId = newest.msgId,
            limit = 20
        )

        assertEquals("new", latest.single().msgId)
        assertEquals("Bob", latest.single().senderName)
        assertEquals("bob.png", latest.single().senderAvatar)
        assertEquals(listOf("old"), before.map { it.msgId })
        assertEquals(listOf("member"), before.single().tags.map { it.text })
    }

    @Test
    fun payloadFreshnessAndScopeDeletionRespectTheCacheBoundary() = runBlocking {
        LocalCache.putPayload(
            accountId = accountId,
            kind = LocalCache.KIND_DETAIL,
            payload = "detail",
            scope = "1:chat"
        )
        LocalCache.putPayload(
            accountId = accountId,
            kind = LocalCache.KIND_MEMBERS,
            payload = "member-one",
            scope = "group:1:1"
        )
        LocalCache.putPayload(
            accountId = accountId,
            kind = LocalCache.KIND_MEMBERS,
            payload = "member-two",
            scope = "group:1:2"
        )
        LocalCache.putPayload(
            accountId = accountId,
            kind = LocalCache.KIND_MEMBERS,
            payload = "other",
            scope = "other"
        )
        LocalCache.putPayload(
            accountId = accountId,
            kind = LocalCache.KIND_DETAIL,
            payload = "expired",
            scope = "expired",
            ttlMs = 1L
        )

        assertTrue(LocalCache.isPayloadFresh(accountId, LocalCache.KIND_DETAIL, "1:chat"))
        Thread.sleep(20L)
        assertFalse(LocalCache.isPayloadFresh(accountId, LocalCache.KIND_DETAIL, "expired"))

        LocalCache.deletePayloadsByScopePrefix(accountId, LocalCache.KIND_MEMBERS, "group:")

        assertNull(LocalCache.getPayload(accountId, LocalCache.KIND_MEMBERS, "group:1:1"))
        assertNull(LocalCache.getPayload(accountId, LocalCache.KIND_MEMBERS, "group:1:2"))
        assertEquals(
            "other",
            LocalCache.getPayload(accountId, LocalCache.KIND_MEMBERS, "other")?.payload
        )
    }

    @Test
    fun clearUnreadResetsConversationCountersAndWritesReadGuardState() = runBlocking {
        LocalCache.replaceConversations(
            accountId,
            listOf(conversation("chat", unreadMessage = 4, at = 1)),
            md5 = null
        )

        LocalCache.clearUnread(accountId, "chat", 1)

        val cached = LocalCache.getCachedConversation(accountId, "chat", 1)
        val readGuard = LocalCacheDatabase.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).states().get(accountId, "conversation_read_guard:1:chat")

        assertEquals(0, cached?.unreadMessage)
        assertEquals(0, cached?.at)
        assertNotNull(readGuard)
    }

    @Test
    fun activeAccountRejectsBlankValuesAndAcceptsARealAccountId() {
        LocalCache.setActiveAccount(" ")
        assertNull(LocalCache.currentAccountId())

        LocalCache.setActiveAccount(accountId)
        assertEquals(accountId, LocalCache.currentAccountId())
    }

    @Test
    fun purgeAccountRemovesConversationsMessagesPayloadsAndSyncState() = runBlocking {
        LocalCache.replaceConversations(accountId, listOf(conversation("chat")), md5 = "md5")
        LocalCache.cacheSticky(accountId, listOf(StickyItem(1L, 1, "chat", "Chat", "", 0)))
        LocalCache.cacheMessages(accountId, listOf(message("message", 1L, 1L)))
        LocalCache.putPayload(accountId, LocalCache.KIND_PROFILE, "profile", scope = "info")
        LocalCache.setContactMd5(accountId, "contact-md5")

        LocalCache.purgeAccount(accountId)

        val database = LocalCacheDatabase.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        assertEquals(emptyList<CachedConversationEntity>(), database.conversations().getConversations(accountId))
        assertEquals(emptyList<CachedStickyEntity>(), database.conversations().observeSticky(accountId).first())
        assertNull(database.messages().latestUpdateTimestamp(accountId, "chat", 1))
        assertNull(database.payloads().get(accountId, LocalCache.KIND_PROFILE, "info"))
        assertNull(database.states().get(accountId, "contact_md5"))
    }

    private fun conversation(
        chatId: String,
        timestamp: Long = 1L,
        unreadMessage: Int = 0,
        at: Int = 0
    ) = ConversationItem(
        chatId = chatId,
        chatType = 1,
        name = chatId,
        chatContent = "content",
        timestampMs = timestamp,
        unreadMessage = unreadMessage,
        at = at,
        avatarUrl = "",
        sendTimestamp = timestamp,
        latestMessageId = null,
        latestMessageSeq = 0L,
        latestContentType = MessageItem.CONTENT_TYPE_TEXT
    )

    private fun message(
        msgId: String,
        timestamp: Long,
        msgSeq: Long,
        senderName: String = "Sender",
        senderAvatar: String = "",
        tags: List<MessageTag> = emptyList()
    ) = MessageItem(
        msgId = msgId,
        senderId = "sender-id",
        senderName = senderName,
        senderAvatar = senderAvatar,
        chatId = "chat",
        chatType = 1,
        content = msgId,
        contentType = MessageItem.CONTENT_TYPE_TEXT,
        timestamp = timestamp,
        msgSeq = msgSeq,
        direction = "left",
        tags = tags,
        updateTimestamp = timestamp
    )
}
