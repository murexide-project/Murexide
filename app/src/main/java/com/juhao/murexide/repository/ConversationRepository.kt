package com.juhao.murexide.repository

import com.juhao.murexide.data.*
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.conversation.ConversationListSend
import com.juhao.murexide.proto.conversation.ConversationList
import com.juhao.murexide.proto.list_message
import com.juhao.murexide.proto.list_message_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class StickyListResponse(
    val code: Int = 0,
    val data: StickyData? = null,
    val msg: String = ""
)

@Serializable
private data class StickyData(
    val sticky: List<StickyItem> = emptyList()
)

class ConversationRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    private data class ConversationSnapshot(
        val items: List<ConversationItem>,
        val md5: String,
        val unchanged: Boolean
    )

    suspend fun getConversationList(token: String, md5: String = ""): Result<List<ConversationItem>> =
        fetchConversationList(token, md5).map { it.items }

    suspend fun syncCachedConversations(
        token: String,
        accountId: String,
        forceRefresh: Boolean = false
    ): Result<List<ConversationItem>?> {
        val md5 = conversationListRequestMd5(
            cachedMd5 = LocalCache.conversationMd5(accountId),
            forceRefresh = forceRefresh
        )
        return fetchConversationList(token, md5).map { snapshot ->
            if (!snapshot.unchanged) {
                LocalCache.replaceConversations(accountId, snapshot.items, snapshot.md5)
                snapshot.items
            } else {
                null
            }
        }
    }

    private suspend fun fetchConversationList(token: String, md5: String): Result<ConversationSnapshot> {
        return withContext(Dispatchers.IO) {
            try {
                val requestProto = ConversationListSend(md5 = md5)
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/conversation/list")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val conversationList = ConversationList.ADAPTER.decode(responseBody)

                        if (conversationList.status?.code == 1) {
                            val items = conversationList.data_.map { conversationData ->
                                ConversationItem(
                                    chatId = conversationData.chat_id,
                                    chatType = conversationData.chat_type.toInt(),
                                    name = conversationData.name,
                                    remark = conversationData.remark.takeIf { it.isNotEmpty() },
                                    chatContent = conversationData.chat_content.takeIf { it.isNotEmpty() } ?: "[消息]",
                                    timestampMs = conversationData.timestamp_ms,
                                    sendTimestamp = conversationData.send_timestamp
                                        .takeIf { it > 0 }
                                        ?: conversationData.timestamp_ms,
                                    unreadMessage = conversationData.unread_message.toInt(),
                                    at = conversationData.at.toInt(),
                                    avatarUrl = conversationData.avatar_url,
                                    doNotDisturb = conversationData.do_not_disturb.toInt(),
                                    certificationLevel = conversationData.certification_level.toInt()
                                )
                            }

                            Result.success(
                                ConversationSnapshot(
                                    items = items,
                                    md5 = conversationList.md5,
                                    unchanged = md5.isNotBlank() &&
                                        conversationList.md5 == md5 && items.isEmpty()
                                )
                            )
                        } else {
                            Result.failure(Exception(conversationList.status?.msg ?: "请求失败"))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getStickyList(
        token: String,
        accountId: String? = LocalCache.currentAccountId()
    ): Result<List<StickyItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/sticky/list")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(
                            Exception("HTTP error: ${response.code}")
                        )
                    }
                    val result = json.decodeFromString<StickyListResponse>(response.body.string())
                    if (result.code == 1) {
                        val sticky = result.data?.sticky.orEmpty()
                        accountId?.let { accountId ->
                            LocalCache.cacheSticky(accountId, sticky)
                        }
                        Result.success(sticky)
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "获取置顶会话失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun addSticky(token: String, chatId: String, chatType: Int): Result<Unit> =
        postStickyAction(token, "/v1/sticky/add", chatId, chatType)

    suspend fun deleteSticky(token: String, chatId: String, chatType: Int): Result<Unit> =
        postStickyAction(token, "/v1/sticky/delete", chatId, chatType)

    private suspend fun postStickyAction(
        token: String,
        path: String,
        chatId: String,
        chatType: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(
                buildJsonObject {
                    put("chatId", chatId)
                    put("chatType", chatType)
                }
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(body)
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val result = json.decodeFromString<CommonResponse>(response.body.string())
                if (result.code == 1) Result.success(Unit)
                else Result.failure(Exception(result.msg.ifBlank { "操作失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(
        token: String,
        chatId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(
                buildJsonObject { put("chatId", chatId) }
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/v1/conversation/remove")
                .post(body)
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val result = json.decodeFromString<CommonResponse>(response.body.string())
                if (result.code == 1) Result.success(Unit)
                else Result.failure(Exception(result.msg.ifBlank { "删除会话失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestMessage(
        token: String,
        chatId: String,
        chatType: Int
    ): Result<MessageItem?> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = list_message_send(
                    msg_count = 1,
                    msg_id = "",
                    chat_type = chatType.toLong(),
                    chat_id = chatId
                ).encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/list-message")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(
                            Exception("HTTP error: ${response.code}")
                        )
                    }

                    val messageList = list_message.ADAPTER.decode(response.body.bytes())
                    if (messageList.status?.code != 1) {
                        return@use Result.failure(
                            Exception(messageList.status?.msg ?: "获取消息预览失败")
                        )
                    }

                    Result.success(
                        messageList.msg
                            .firstOrNull()
                            ?.toMessageItem(chatId = chatId, chatType = chatType)
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun dismissNotification(token: String, chatId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(buildJsonObject { put("chatId", chatId) })
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/v1/conversation/dismiss-notification")
                    .post(body)
                    .header("token", token)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    val status = json.decodeFromString<DismissNotificationResponse>(response.body.string())
                    if (status.code == 1) Result.success(Unit)
                    else Result.failure(Exception(status.msg.ifBlank { "设为已读失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

internal fun conversationListRequestMd5(cachedMd5: String, forceRefresh: Boolean): String =
    if (forceRefresh) "" else cachedMd5

@Serializable
private data class DismissNotificationResponse(val code: Int = 0, val msg: String = "")