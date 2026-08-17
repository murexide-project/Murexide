package com.juhao.murexide.data.local

import android.content.Context
import androidx.room.withTransaction
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.ConversationKey
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.MessageTag
import com.juhao.murexide.data.StickyItem
import com.juhao.murexide.data.withEditedLatestMessage
import com.juhao.murexide.data.withLatestMessage
import com.juhao.murexide.data.withRecalledLatestMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object LocalCache {
    const val KIND_CONTACTS = "contacts"
    const val KIND_REQUESTS = "requests"
    const val KIND_PROFILE = "profile"
    const val KIND_DETAIL = "detail"
    const val KIND_MEMBERS = "members"

    private const val CONVERSATION_MD5_KEY = "conversation_md5"
    private const val CONTACT_MD5_KEY = "contact_md5"
    private const val CONVERSATION_READ_GUARD_PREFIX = "conversation_read_guard:"
    private const val CONVERSATION_READ_GUARD_TTL_MS = 60_000L
    private const val MAX_MESSAGES_PER_ACCOUNT = 20_000
    private const val MAX_MESSAGES_PER_CONVERSATION = 2_000

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Volatile private var database: LocalCacheDatabase? = null
    @Volatile private var activeAccountId: String? = null

    fun initialize(context: Context) {
        database = LocalCacheDatabase.getInstance(context)
    }

    fun setActiveAccount(accountId: String?) {
        activeAccountId = accountId?.takeIf { it.isNotBlank() }
    }

    fun currentAccountId(): String? = activeAccountId

    fun observeConversations(accountId: String): Flow<List<ConversationItem>> = db().conversations()
        .observeConversations(accountId)
        .map { rows -> rows.map { it.toModel() } }

    fun observeSticky(accountId: String): Flow<List<StickyItem>> = db().conversations()
        .observeSticky(accountId)
        .map { rows -> rows.map { it.toModel() } }


    suspend fun getCachedConversation(
        accountId: String,
        chatId: String,
        chatType: Int
    ): ConversationItem? = withContext(Dispatchers.IO) {
        db().conversations().getConversation(accountId, chatId, chatType)?.toModel()
    }

    fun observeMessages(
        accountId: String,
        chatId: String,
        chatType: Int,
        limit: Int
    ): Flow<List<MessageItem>> = db().messages()
        .observeMessages(accountId, chatId, chatType, limit)
        .map { rows -> rows.mapNotNull(::decodeMessage) }

    suspend fun getCachedMessagesBefore(
        accountId: String,
        chatId: String,
        chatType: Int,
        beforeTimestamp: Long,
        beforeMsgSeq: Long,
        beforeMsgId: String,
        limit: Int
    ): List<MessageItem> = withContext(Dispatchers.IO) {
        db().messages().getMessagesBefore(
            accountId = accountId,
            chatId = chatId,
            chatType = chatType,
            beforeTimestamp = beforeTimestamp,
            beforeMsgSeq = beforeMsgSeq,
            beforeMsgId = beforeMsgId,
            limit = limit
        ).mapNotNull(::decodeMessage)
    }

    suspend fun replaceConversations(
        accountId: String,
        conversations: List<ConversationItem>,
        md5: String?
    ) = withContext(Dispatchers.IO) {
        val database = db()
        database.withTransaction {
            val dao = database.conversations()
            dao.replaceConversations(
                accountId,
                conversations.mapIndexed { index, item -> item.toEntity(accountId, index) }
            )
            if (md5 != null) {
                database.states().put(
                    CacheSyncStateEntity(
                        accountId = accountId,
                        key = CONVERSATION_MD5_KEY,
                        value = md5,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun cacheSticky(accountId: String, items: List<StickyItem>) = withContext(Dispatchers.IO) {
        db().conversations().replaceSticky(
            accountId,
            items.mapIndexed { index, item -> item.toEntity(accountId, index) }
        )
    }

    suspend fun conversationMd5(accountId: String): String =
        db().states().get(accountId, CONVERSATION_MD5_KEY)?.value.orEmpty()

    suspend fun contactMd5(accountId: String): String =
        db().states().get(accountId, CONTACT_MD5_KEY)?.value.orEmpty()

    suspend fun setContactMd5(accountId: String, md5: String) =
        setState(accountId, CONTACT_MD5_KEY, md5)

    suspend fun clearUnread(accountId: String, chatId: String, chatType: Int) =
        withContext(Dispatchers.IO) {
            val database = db()
            val now = System.currentTimeMillis()
            database.withTransaction {
                database.conversations().clearUnread(accountId, chatId, chatType)
                database.states().deleteOlderThanByPrefix(
                    accountId = accountId,
                    prefix = CONVERSATION_READ_GUARD_PREFIX,
                    updatedBefore = now - CONVERSATION_READ_GUARD_TTL_MS
                )
                database.states().put(
                    CacheSyncStateEntity(
                        accountId = accountId,
                        key = conversationReadGuardKey(ConversationKey(chatId, chatType)),
                        updatedAt = now
                    )
                )
            }
        }

    suspend fun setConversationMuted(accountId: String, chatId: String, chatType: Int, muted: Boolean) =
        withContext(Dispatchers.IO) {
            db().conversations().setDoNotDisturb(accountId, chatId, chatType, if (muted) 1 else 0)
        }

    suspend fun removeConversation(accountId: String, chatId: String, chatType: Int) =
        withContext(Dispatchers.IO) {
            val database = db()
            database.withTransaction {
                database.conversations().deleteConversation(accountId, chatId, chatType)
                database.payloads().delete(accountId, KIND_DETAIL, "$chatType:$chatId")
                database.payloads().deleteByScopePrefix(accountId, KIND_MEMBERS, "$chatId:%")
            }
        }

    suspend fun applyNewMessageToConversation(
        accountId: String,
        message: MessageItem,
        incrementUnread: Boolean
    ) = withContext(Dispatchers.IO) {
        val database = db()
        database.withTransaction {
            val dao = database.conversations()
            val currentEntity = dao.getConversationForMessage(
                accountId,
                message.chatId,
                message.chatType,
                message.senderId
            ) ?: return@withTransaction
            val current = currentEntity.toModel()
            val isStrictlyNewer = message.isStrictlyNewerThan(current)
            val changed = listOf(current).withLatestMessage(message, incrementUnread)?.singleOrNull()
                ?: return@withTransaction

            val entity = changed.toEntity(accountId, currentEntity.listPosition)

            if (isStrictlyNewer) {
                dao.moveConversationToFront(entity)
            } else {
                dao.upsertConversations(listOf(entity))
            }
        }
    }

    suspend fun applyMessageMutationToConversation(
        accountId: String,
        message: MessageItem,
        recalled: Boolean
    ) = withContext(Dispatchers.IO) {
        val dao = db().conversations()
        val currentEntity = dao.getConversationForMessage(
            accountId,
            message.chatId,
            message.chatType,
            message.senderId
        ) ?: return@withContext
        val current = currentEntity.toModel()
        val changed = if (recalled) {
            listOf(current).withRecalledLatestMessage(message.copy(isRecalled = true))
        } else {
            listOf(current).withEditedLatestMessage(message.copy(isEdited = true))
        }
        changed.singleOrNull()?.let {
            dao.upsertConversations(listOf(it.toEntity(accountId, currentEntity.listPosition)))
        }
    }

    suspend fun cacheMessages(accountId: String, messages: List<MessageItem>) = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext
        val messageEntities = messages.map { it.toEntity(accountId, json) }
        val senderEntities = messages
            .filter { it.senderId.isNotBlank() }
            .map { it.toSenderEntity(accountId, json) }
            .distinctBy { listOf(it.chatType, it.chatId, it.senderType, it.senderId) }
        db().messages().upsertBatch(messageEntities, senderEntities)

        messages.map { it.chatType to it.chatId }.distinct().forEach { (chatType, chatId) ->
            trimConversationMessages(accountId, chatId, chatType)
        }
        trimAccountMessages(accountId)
    }

    suspend fun putPayload(
        accountId: String,
        kind: String,
        payload: String,
        scope: String = "",
        ttlMs: Long = 0L
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db().payloads().put(
            CachedPayloadEntity(
                accountId = accountId,
                kind = kind,
                scope = scope,
                payload = payload,
                updatedAt = now,
                expiresAt = if (ttlMs > 0) now + ttlMs else 0L
            )
        )
    }

    suspend fun getPayload(accountId: String, kind: String, scope: String = ""): CachedPayloadEntity? =
        withContext(Dispatchers.IO) { db().payloads().get(accountId, kind, scope) }

    suspend fun deletePayloadsByScopePrefix(accountId: String, kind: String, scopePrefix: String) =
        withContext(Dispatchers.IO) { db().payloads().deleteByScopePrefix(accountId, kind, scopePrefix) }

    suspend fun isPayloadFresh(accountId: String, kind: String, scope: String = ""): Boolean {
        val cached = getPayload(accountId, kind, scope) ?: return false
        return cached.expiresAt == 0L || cached.expiresAt > System.currentTimeMillis()
    }

    suspend fun purgeAccount(accountId: String) = withContext(Dispatchers.IO) {
        val database = db()
        database.states().deleteAccount(accountId)
        database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        database.openHelper.writableDatabase.execSQL("VACUUM")
    }

    private suspend fun setState(accountId: String, key: String, value: String) = withContext(Dispatchers.IO) {
        db().states().put(CacheSyncStateEntity(accountId, key, value, System.currentTimeMillis()))
    }

    private fun conversationReadGuardKey(conversation: ConversationKey): String =
        "$CONVERSATION_READ_GUARD_PREFIX${conversation.chatType}:${conversation.chatId}"

    private suspend fun trimConversationMessages(accountId: String, chatId: String, chatType: Int) {
        val excess = db().messages().oldestMessageIdsAfter(
            accountId,
            chatId,
            chatType,
            MAX_MESSAGES_PER_CONVERSATION
        )
        if (excess.isNotEmpty()) db().messages().deleteMessages(accountId, excess)
    }

    private suspend fun trimAccountMessages(accountId: String) {
        val excess = db().messages().accountOldestMessageIdsAfter(accountId, MAX_MESSAGES_PER_ACCOUNT)
        if (excess.isNotEmpty()) db().messages().deleteMessages(accountId, excess)
    }

    private fun db(): LocalCacheDatabase = checkNotNull(database) {
        "LocalCache.initialize must run from Application.onCreate before use"
    }

    private fun CachedConversationEntity.toModel() = ConversationItem(
        chatId = chatId,
        chatType = chatType,
        name = name,
        remark = remark,
        chatContent = chatContent,
        timestampMs = timestampMs,
        unreadMessage = unreadMessage,
        at = at,
        avatarUrl = avatarUrl,
        doNotDisturb = doNotDisturb,
        certificationLevel = certificationLevel,
        sendTimestamp = sendTimestamp,
        latestMessageId = latestMessageId,
        latestMessageSeq = latestMessageSeq,
        latestContentType = latestContentType
    )

    private fun ConversationItem.toEntity(accountId: String, listPosition: Int) = CachedConversationEntity(
        accountId, chatType, chatId, name, remark, chatContent, timestampMs, unreadMessage, at,
        avatarUrl, doNotDisturb, certificationLevel, sendTimestamp, latestMessageId,
        latestMessageSeq, latestContentType, listPosition
    )

    private fun CachedStickyEntity.toModel() = StickyItem(
        id, chatType, chatId, chatName, avatarUrl, certificationLevel
    )

    private fun StickyItem.toEntity(accountId: String, listPosition: Int) = CachedStickyEntity(
        accountId, id, chatType, chatId, chatName, avatarUrl, certificationLevel, listPosition
    )

    private fun MessageItem.toEntity(accountId: String, json: Json): CachedMessageEntity =
        CachedMessageEntity(
            accountId = accountId,
            msgId = msgId,
            chatId = chatId,
            chatType = chatType,
            senderId = senderId,
            senderType = senderType,
            timestamp = timestamp,
            msgSeq = msgSeq,
            updateTimestamp = updateTimestamp,
            payload = json.encodeToString(
                MessageItem.serializer(),
                copy(senderName = "", senderAvatar = "", tags = emptyList())
            )
        )

    private fun MessageItem.toSenderEntity(accountId: String, json: Json) = CachedMessageSenderEntity(
        accountId = accountId,
        chatType = chatType,
        chatId = chatId,
        senderType = senderType,
        senderId = senderId,
        name = senderName,
        avatarUrl = senderAvatar,
        tagsJson = json.encodeToString(ListSerializer(MessageTag.serializer()), tags)
    )

    private fun decodeMessage(row: CachedMessageRow): MessageItem? = runCatching {
        val payload = json.decodeFromString(MessageItem.serializer(), row.payload)
        payload.copy(
            senderName = row.senderName.orEmpty(),
            senderAvatar = row.senderAvatarUrl.orEmpty(),
            tags = row.senderTagsJson
                ?.let { json.decodeFromString(ListSerializer(MessageTag.serializer()), it) }
                .orEmpty()
        )
    }.getOrNull()

    private fun MessageItem.isStrictlyNewerThan(conversation: ConversationItem): Boolean {
        if (msgId.isNotBlank() && msgId == conversation.latestMessageId) return false
        if (msgSeq > 0L && conversation.latestMessageSeq > 0L && msgSeq != conversation.latestMessageSeq) {
            return msgSeq > conversation.latestMessageSeq
        }
        val latestTimestamp = conversation.latestMessageTimestamp
        return timestamp > 0L && (latestTimestamp <= 0L || timestamp > latestTimestamp)
    }
}
