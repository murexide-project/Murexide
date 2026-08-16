package com.juhao.murexide.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCacheDaoBoundaryTest {
    private lateinit var database: LocalCacheDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LocalCacheDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun conversationDaoMovesOnlyOneAccountToTheFrontAndUpdatesFlags() = runBlocking {
        val dao = database.conversations()
        dao.upsertConversations(
            listOf(
                conversation("account-a", "first", listPosition = 0),
                conversation("account-a", "second", listPosition = 1),
                conversation("account-b", "other", listPosition = 0)
            )
        )

        dao.moveConversationToFront(conversation("account-a", "second", listPosition = 1))
        dao.setDoNotDisturb("account-a", "second", 1, 1)
        dao.clearUnread("account-a", "second", 1)

        assertEquals(listOf("second", "first"), dao.getConversations("account-a").map { it.chatId })
        assertEquals(listOf(0, 1), dao.getConversations("account-a").map { it.listPosition })
        assertEquals(listOf("other"), dao.getConversations("account-b").map { it.chatId })
        assertEquals(1, dao.getConversation("account-a", "second", 1)?.doNotDisturb)
        assertEquals(0, dao.getConversation("account-a", "second", 1)?.unreadMessage)
        assertEquals(0, dao.getConversation("account-a", "second", 1)?.at)
    }

    @Test
    fun conversationForMessageResolvesDirectChatsBySenderButKeepsGroupsExact() = runBlocking {
        val dao = database.conversations()
        dao.upsertConversations(
            listOf(
                conversation("account-a", "user-42", chatType = 1),
                conversation("account-a", "group-1", chatType = 2),
                conversation("account-b", "user-42", chatType = 1)
            )
        )

        assertEquals(
            "user-42",
            dao.getConversationForMessage("account-a", "server-chat", 1, "user-42")?.chatId
        )
        assertEquals(
            "group-1",
            dao.getConversationForMessage("account-a", "group-1", 2, "someone-else")?.chatId
        )
        assertNull(dao.getConversationForMessage("account-a", "server-chat", 2, "user-42"))
        assertNull(dao.getConversationForMessage("account-a", "server-chat", 1, "missing"))
    }

    @Test
    fun stickyReplacementAndObservationAreOrderedAndAccountScoped() = runBlocking {
        val dao = database.conversations()
        dao.upsertSticky(
            listOf(
                sticky("account-a", 1, "first", listPosition = 1),
                sticky("account-a", 2, "second", listPosition = 0),
                sticky("account-b", 1, "other", listPosition = 0)
            )
        )

        dao.replaceSticky("account-a", listOf(sticky("account-a", 3, "replacement")))

        assertEquals(listOf("replacement"), dao.observeSticky("account-a").first().map { it.chatId })
        assertEquals(listOf("other"), dao.observeSticky("account-b").first().map { it.chatId })
    }

    @Test
    fun messageBatchJoinsSenderMetadataAndKeepsAccountsSeparate() = runBlocking {
        val dao = database.messages()
        dao.upsertBatch(
            messages = listOf(
                message("account-a", "old", "chat", 100L, 1L),
                message("account-a", "new", "chat", 200L, 2L),
                message("account-a", "without-sender", "chat", 300L, 3L)
            ),
            senders = listOf(
                sender("account-a", "chat", "sender-1", "Alice", "alice.png", "[tag-a]"),
                sender("account-a", "chat", "sender-2", "Bob", "bob.png", "[tag-b]")
            )
        )
        dao.upsertMessages(listOf(message("account-b", "new", "chat", 400L, 4L)))

        val rows = dao.observeMessages("account-a", "chat", 1, 20).first()

        assertEquals(listOf("without-sender", "new", "old"), rows.map { it.msgId })
        assertEquals("Alice", rows.single { it.msgId == "old" }.senderName)
        assertEquals("alice.png", rows.single { it.msgId == "old" }.senderAvatarUrl)
        assertEquals("[tag-a]", rows.single { it.msgId == "old" }.senderTagsJson)
        assertNull(rows.single { it.msgId == "without-sender" }.senderName)
        assertFalse(rows.any { it.msgId == "new" && it.accountId == "account-b" })
    }

    @Test
    fun messageRetentionQueriesReturnOnlyRowsBeyondTheKeptPrefix() = runBlocking {
        val dao = database.messages()
        dao.upsertMessages(
            listOf(
                message("account-a", "one", "chat", 100L, 1L),
                message("account-a", "two", "chat", 200L, 2L),
                message("account-a", "three", "chat", 300L, 3L),
                message("account-b", "other", "chat", 400L, 4L)
            )
        )

        assertEquals(
            listOf("two", "three"),
            dao.oldestMessageIdsAfter("account-a", "chat", 1, keep = 1)
        )
        assertEquals(
            listOf("two", "three"),
            dao.accountOldestMessageIdsAfter("account-a", keep = 1)
        )
    }

    @Test
    fun payloadDaoReplacesKeysAndDeletesOnlyMatchingScopePrefix() = runBlocking {
        val dao = database.payloads()
        dao.put(payload("account-a", "kind", "group:1", "old"))
        dao.put(payload("account-a", "kind", "group:1", "new"))
        dao.put(payload("account-a", "kind", "other", "keep"))
        dao.put(payload("account-b", "kind", "group:1", "other-account"))

        assertEquals("new", dao.get("account-a", "kind", "group:1")?.payload)
        dao.deleteByScopePrefix("account-a", "kind", "group:%")

        assertNull(dao.get("account-a", "kind", "group:1"))
        assertEquals("keep", dao.get("account-a", "kind", "other")?.payload)
        assertEquals("other-account", dao.get("account-b", "kind", "group:1")?.payload)
    }

    @Test
    fun stateDaoFiltersByPrefixAndUpdatedTimeBeforeDeletingOldRows() = runBlocking {
        val dao = database.states()
        dao.put(CacheSyncStateEntity("account-a", "sync:old", "old", 10L))
        dao.put(CacheSyncStateEntity("account-a", "sync:new", "new", 20L))
        dao.put(CacheSyncStateEntity("account-a", "other", "other", 30L))
        dao.put(CacheSyncStateEntity("account-b", "sync:other", "other-account", 5L))

        assertEquals(
            setOf("sync:new"),
            dao.getRecentByPrefix("account-a", "sync:", updatedAfter = 15L)
                .map { it.key }
                .toSet()
        )
        dao.deleteOlderThanByPrefix("account-a", "sync:", updatedBefore = 20L)

        assertNull(dao.get("account-a", "sync:old"))
        assertNotNull(dao.get("account-a", "sync:new"))
        assertNotNull(dao.get("account-b", "sync:other"))
    }

    @Test
    fun deleteAccountCascadesAcrossEveryCacheTableWithoutTouchingOtherAccounts() = runBlocking {
        val conversations = database.conversations()
        val messages = database.messages()
        val payloads = database.payloads()
        val states = database.states()
        conversations.upsertConversations(listOf(conversation("account-a", "chat")))
        conversations.upsertSticky(listOf(sticky("account-a", 1, "chat")))
        messages.upsertBatch(
            listOf(message("account-a", "message", "chat", 1L, 1L)),
            listOf(sender("account-a", "chat", "sender", "Sender", "", "[]"))
        )
        payloads.put(payload("account-a", "kind", "scope", "payload"))
        states.put(CacheSyncStateEntity("account-a", "state", "value", 1L))

        conversations.upsertConversations(listOf(conversation("account-b", "chat")))
        messages.upsertMessages(listOf(message("account-b", "message", "chat", 2L, 2L)))

        states.deleteAccount("account-a")

        assertEquals(emptyList<CachedConversationEntity>(), conversations.getConversations("account-a"))
        assertEquals(emptyList<CachedStickyEntity>(), conversations.observeSticky("account-a").first())
        assertNull(messages.latestUpdateTimestamp("account-a", "chat", 1))
        assertNull(payloads.get("account-a", "kind", "scope"))
        assertNull(states.get("account-a", "state"))
        assertEquals(2L, messages.latestUpdateTimestamp("account-b", "chat", 1))
    }

    private fun conversation(
        accountId: String,
        chatId: String,
        chatType: Int = 1,
        listPosition: Int = 0,
        unreadMessage: Int = 0,
        at: Int = 0
    ) = CachedConversationEntity(
        accountId = accountId,
        chatType = chatType,
        chatId = chatId,
        name = chatId,
        remark = null,
        chatContent = "content",
        timestampMs = 1L,
        unreadMessage = unreadMessage,
        at = at,
        avatarUrl = "",
        doNotDisturb = 0,
        certificationLevel = 0,
        sendTimestamp = 1L,
        latestMessageId = null,
        latestMessageSeq = 0L,
        latestContentType = 1,
        listPosition = listPosition
    )

    private fun sticky(
        accountId: String,
        id: Long,
        chatId: String,
        listPosition: Int = 0
    ) = CachedStickyEntity(
        accountId = accountId,
        id = id,
        chatType = 1,
        chatId = chatId,
        chatName = chatId,
        avatarUrl = "",
        certificationLevel = 0,
        listPosition = listPosition
    )

    private fun message(
        accountId: String,
        msgId: String,
        chatId: String,
        timestamp: Long,
        msgSeq: Long
    ) = CachedMessageEntity(
        accountId = accountId,
        msgId = msgId,
        chatId = chatId,
        chatType = 1,
        senderId = if (msgId == "without-sender") "" else "sender-1",
        senderType = 1,
        timestamp = timestamp,
        msgSeq = msgSeq,
        updateTimestamp = timestamp,
        payload = "{\"msgId\":\"$msgId\"}"
    )

    private fun sender(
        accountId: String,
        chatId: String,
        senderId: String,
        name: String,
        avatarUrl: String,
        tagsJson: String
    ) = CachedMessageSenderEntity(
        accountId = accountId,
        chatType = 1,
        chatId = chatId,
        senderType = 1,
        senderId = senderId,
        name = name,
        avatarUrl = avatarUrl,
        tagsJson = tagsJson
    )

    private fun payload(
        accountId: String,
        kind: String,
        scope: String,
        value: String
    ) = CachedPayloadEntity(
        accountId = accountId,
        kind = kind,
        scope = scope,
        payload = value,
        updatedAt = 1L,
        expiresAt = 0L
    )
}
