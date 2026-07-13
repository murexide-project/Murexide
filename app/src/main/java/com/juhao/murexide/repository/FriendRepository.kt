package com.juhao.murexide.repository

import com.juhao.murexide.data.ContactGroup
import com.juhao.murexide.data.ContactItem
import com.juhao.murexide.data.DeleteFriendResponse
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.friend.address_book_list
import com.juhao.murexide.proto.friend.address_book_list_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class FriendRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAddressBook(token: String, md5: String = ""): Result<List<ContactGroup>> {
        return withContext(Dispatchers.IO) {
            try {
                // 构建 ProtoBuf 请求
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
    
    /**
     * 添加用户/群聊/机器人。
     * @param type 1-用户，2-群聊，3-机器人
     * code: 1 正常，-1 不存在，-9 已在群聊中
     */
    suspend fun apply(token: String, chatId: String, chatType: Int, remark: String = ""): Result<Int> {
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
                        Result.success(result.code)
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 判断某会话是否已在通讯录中（已添加）。 */
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
}
