package com.juhao.murexide.repository

import com.juhao.murexide.data.ContactGroup
import com.juhao.murexide.data.ContactItem
import com.juhao.murexide.data.ContactRequestItem
import com.juhao.murexide.data.ContactRequestList
import com.juhao.murexide.data.DeleteFriendResponse
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.friend.address_book_list
import com.juhao.murexide.proto.friend.address_book_list_send
import com.juhao.murexide.proto.friend.request_list
import com.juhao.murexide.proto.friend.request_list_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val protobufMediaType = "application/octet-stream".toMediaType()

internal fun createFriendRequestListBody() =
    request_list_send().encode().toRequestBody(protobufMediaType)

internal fun resolveInviterId(inviterId: String, alternateInviterId: String): String =
    inviterId.ifBlank { alternateInviterId }

class FriendRepository(
    private val client: OkHttpClient = NetworkClient.okHttpClient,
    private val baseUrl: String = NetworkClient.BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncCachedAddressBook(token: String): Result<List<ContactGroup>> {
        val accountId = LocalCache.currentAccountId()
        val md5 = accountId?.let { LocalCache.contactMd5(it) } ?: ""
        return getAddressBook(token, md5)
    }

    suspend fun getAddressBook(token: String, md5: String = ""): Result<List<ContactGroup>> {
        return withContext(Dispatchers.IO) {
            try {
                val requestProto = address_book_list_send(md5 = md5)
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/friend/address-book-list")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val bookList = address_book_list.ADAPTER.decode(responseBody)

                        if (bookList.status?.code == 1) {
                            val groups = bookList.data_.map { groupData ->
                                ContactGroup(
                                    groupName = groupData.list_name,
                                    chatType = groupData.chat_type,
                                    contacts = groupData.data_.map { item ->
                                        ContactItem(
                                            chatId = item.chat_id,
                                            chatType = groupData.chat_type,
                                            remark = item.remark.takeIf { it.isNotEmpty() },
                                            avatarUrl = item.avatar_url,
                                            permissionLevel = item.permisson_level,
                                            noDisturb = item.noDisturb,
                                            name = item.name
                                        )
                                    }
                                )
                            }
                            LocalCache.currentAccountId()?.let { accountId ->
                                if (md5.isBlank() || groups.isNotEmpty()) {
                                    cacheContacts(accountId, groups)
                                }
                                if (bookList.md5.isNotBlank()) {
                                    LocalCache.setContactMd5(accountId, bookList.md5)
                                }
                            }
                            Result.success(groups)
                        } else {
                            Result.failure(Exception(bookList.status?.msg ?: "请求失败"))
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

    suspend fun getRequests(token: String): Result<ContactRequestList> {
        return withContext(Dispatchers.IO) {
            try {
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/friend/request-list")
                    .post(createFriendRequestListBody())
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }

                    val result = request_list.ADAPTER.decode(response.body.bytes())
                    if (result.status?.code != 1) {
                        return@use Result.failure(Exception(result.status?.msg ?: "请求失败"))
                    }

                    val requests = ContactRequestList(
                        requests = result.requests.map { item ->
                            ContactRequestItem(
                                requestId = item.requestId,
                                requesterName = item.name,
                                requesterAvatarUrl = item.avatar,
                                receiverName = item.receiverName,
                                receiverAvatarUrl = item.receiverAvatar,
                                groupName = item.groupName,
                                groupAvatarUrl = item.groupAvatar,
                                botName = item.botName,
                                botAvatarUrl = item.botAvatar,
                                inviterId = resolveInviterId(
                                    inviterId = item.inviterId,
                                    alternateInviterId = item.alternateInviterId
                                ),
                                sourceType = item.sourceType,
                                targetType = item.targetType,
                                targetId = item.targetId,
                                receiverId = item.receiverId,
                                result = item.result,
                                processedAt = item.processedAt,
                                invitedAt = item.inviteAt,
                                invitedAtText = item.inviteAtStr,
                                processorName = item.processorName,
                                note = item.note
                            )
                        },
                        total = result.total,
                        pending = result.pending
                    )
                    LocalCache.currentAccountId()?.let { accountId ->
                        LocalCache.putPayload(
                            accountId = accountId,
                            kind = LocalCache.KIND_REQUESTS,
                            payload = json.encodeToString(ContactRequestList.serializer(), requests),
                            ttlMs = 60_000L
                        )
                    }
                    Result.success(requests)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun cacheContacts(accountId: String, groups: List<ContactGroup>) {
        LocalCache.putPayload(
            accountId = accountId,
            kind = LocalCache.KIND_CONTACTS,
            payload = json.encodeToString(ListSerializer(ContactGroup.serializer()), groups)
        )
    }

    suspend fun respondToRequest(
        token: String,
        requestId: Int,
        agree: Int,
        usesGroupAgreeInvite: Boolean
    ): Result<DeleteFriendResponse> {
        return withContext(Dispatchers.IO) {
            val payload = json.encodeToString(
                buildJsonObject {
                    put("id", requestId)
                    put("agree", agree)
                }
            )
            val primaryPath = if (usesGroupAgreeInvite) {
                "/v1/group/agree-invite"
            } else {
                "/v1/friend/agree-apply"
            }
            postRequestResponse(token, payload, primaryPath)
        }
    }

    private fun postRequestResponse(
        token: String,
        payload: String,
        path: String
    ): Result<DeleteFriendResponse> {
        return try {
            val requestBody = payload.toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url("$baseUrl$path")
                .post(requestBody)
                .header("token", token)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val result = json.decodeFromString<DeleteFriendResponse>(response.body.string())
                if (result.code == 1) {
                    Result.success(result)
                } else {
                    Result.failure(Exception(result.msg.ifBlank { "处理失败" }))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun apply(token: String, chatId: String, chatType: Int, remark: String = ""): Result<DeleteFriendResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("chatId", chatId)
                    put("chatType", chatType)
                    put("remark", remark)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/friend/apply")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString<DeleteFriendResponse>(response.body.string())
                        Result.success(result)
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun isAdded(token: String, chatId: String, chatType: Int): Result<Boolean> {
        return getAddressBook(token).map { groups ->
            groups.any { g ->
                g.chatType == chatType && g.contacts.any { it.chatId == chatId }
            }
        }
    }

    suspend fun deleteFriend(token: String, id: String, type: Int = 1): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("chatId", id)
                    put("chatType", type)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/friend/delete-friend")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString<DeleteFriendResponse>(responseBody)

                        if (result.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "请求失败" }))
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

    suspend fun inviteBotToGroup(token: String, botId: String, groupId: String): Result<DeleteFriendResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("chatId", botId)
                    put("chatType", 3)
                    put("groupId", groupId)
                }
                val request = Request.Builder()
                    .url("$baseUrl/v1/group/invite")
                    .post(json.encodeToString(params).toRequestBody("application/json".toMediaType()))
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<DeleteFriendResponse>(response.body.string())
                    if (result.code == 1) Result.success(result)
                    else Result.failure(Exception(result.msg.ifBlank { "邀请机器人失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun setNoNotify(token: String, chatId: String, muted: Boolean): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("chatId", chatId)
                    put("noNotify", if (muted) 1 else 0)
                }
                val request = Request.Builder()
                    .url("$baseUrl/v1/friend/no-notify")
                    .post(json.encodeToString(params).toRequestBody("application/json".toMediaType()))
                    .header("token", token)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<DeleteFriendResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "修改免打扰失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}