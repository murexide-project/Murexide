package com.juhao.murexide.repository

import com.juhao.murexide.data.ConversationDetail
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.bot.bot_info
import com.juhao.murexide.proto.bot.bot_info_send
import com.juhao.murexide.proto.group.edit_group
import com.juhao.murexide.proto.group.edit_group_send
import com.juhao.murexide.proto.group.info
import com.juhao.murexide.proto.group.info_send
import com.juhao.murexide.proto.user.get_user
import com.juhao.murexide.proto.user.get_user_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class CommonStatusResponse(
    val code: Int = 0,
    val msg: String? = null
)

/**
 * 加载会话详情：根据会话类型分别请求用户 / 群聊 / 机器人信息。
 */
class ConversationDetailRepository(
    private val client: OkHttpClient = NetworkClient.okHttpClient,
    private val baseUrl: String = NetworkClient.BASE_URL
) {

    suspend fun getDetail(
        token: String,
        chatId: String,
        chatType: Int
    ): Result<ConversationDetail> {
        val result = when (chatType) {
            2 -> getGroupDetail(token, chatId)
            3 -> getBotDetail(token, chatId)
            else -> getUserDetail(token, chatId)
        }
        result.onSuccess { detail ->
            LocalCache.currentAccountId()?.let { accountId ->
                LocalCache.putPayload(
                    accountId = accountId,
                    kind = LocalCache.KIND_DETAIL,
                    scope = "$chatType:$chatId",
                    payload = json.encodeToString(ConversationDetail.serializer(), detail),
                    ttlMs = 15 * 60_000L
                )
            }
        }
        return result
    }

    suspend fun getCachedDetail(chatId: String, chatType: Int): ConversationDetail? {
        val accountId = LocalCache.currentAccountId() ?: return null
        val cached = LocalCache.getPayload(accountId, LocalCache.KIND_DETAIL, "$chatType:$chatId")
            ?: return null
        return runCatching {
            json.decodeFromString(ConversationDetail.serializer(), cached.payload)
        }.getOrNull()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildRequest(path: String, token: String, body: ByteArray): Request =
        Request.Builder()
            .url("$baseUrl$path")
            .post(body.toRequestBody("application/octet-stream".toMediaType()))
            .header("token", token)
            .build()

    /** 编辑群聊信息（需群主/管理员权限）。 */
    suspend fun editGroup(
        token: String,
        groupId: String,
        name: String,
        introduction: String,
        avatarUrl: String,
        directJoin: Boolean,
        historyMsg: Boolean,
        isPrivate: Boolean,
        hideGroupMembers: Boolean,
        categoryName: String = "",
        categoryId: Long = 0L
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = edit_group_send(
                group_id = groupId,
                name = name,
                introduction = introduction,
                avatar_url = avatarUrl,
                direct_join = if (directJoin) 1 else 0,
                history_msg = if (historyMsg) 1 else 0,
                category_name = categoryName,
                category_id = categoryId,
                private_ = if (isPrivate) 1 else 0,
                hide_group_members = if (hideGroupMembers) 1L else 0L
            ).encode()

            client.newCall(buildRequest("/v1/group/edit-group", token, body)).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val result = edit_group.ADAPTER.decode(response.body.bytes())
                if (result.status?.code == 1) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(result.status?.msg ?: "保存失败"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 设置我在该群的群昵称。 */
    suspend fun editMyGroupNickname(
        token: String,
        groupId: String,
        nickname: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf("groupId" to groupId, "nickname" to nickname)
            val requestBody = json.encodeToString(params)
                .toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url("$baseUrl/v1/group/edit-my-group-nickname")
                .post(requestBody)
                .header("token", token)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val status = json.decodeFromString<CommonStatusResponse>(response.body.string())
                if (status.code == 1) {
                    Result.success(true)
                } else {
                    val message = status.msg
                        ?.takeIf { it.isNotBlank() }
                        ?: "修改失败（code: ${status.code}）"
                    Result.failure(Exception(message))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getUserDetail(token: String, chatId: String): Result<ConversationDetail> =
        withContext(Dispatchers.IO) {
            try {
                val req = buildRequest(
                    "/v1/user/get-user",
                    token,
                    get_user_send(id = chatId).encode()
                )
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = get_user.ADAPTER.decode(response.body.bytes())
                    if (result.status?.code != 1) {
                        return@use Result.failure(Exception(result.status?.msg ?: "加载失败"))
                    }
                    val d = result.data_
                    Result.success(
                        ConversationDetail(
                            chatId = chatId,
                            chatType = 1,
                            name = d?.name ?: "",
                            avatarUrl = d?.avatar_url ?: "",
                            introduction = d?.profile_info?.introduction ?: "",
                            nameId = d?.name_id,
                            registerTime = d?.register_time?.takeIf { it.isNotEmpty() },
                            lastActiveTime = d?.profile_info?.last_active_time?.takeIf { it.isNotEmpty() },
                            onlineDay = d?.online_day,
                            continuousOnlineDay = d?.continuous_online_day,
                            ipGeo = d?.ipGeo?.takeIf { it.isNotEmpty() },
                            doNotDisturb = d?.do_not_disturb ?: false,
                            isVip = (d?.is_vip ?: 0) == 1,
                            gender = d?.profile_info?.gender ?: 3
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun getGroupDetail(token: String, chatId: String): Result<ConversationDetail> =
        withContext(Dispatchers.IO) {
            try {
                val req = buildRequest(
                    "/v1/group/info",
                    token,
                    info_send(group_id = chatId).encode()
                )
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = info.ADAPTER.decode(response.body.bytes())
                    if (result.status?.code != 1) {
                        return@use Result.failure(Exception(result.status?.msg ?: "加载失败"))
                    }
                    val d = result.data_
                    Result.success(
                        ConversationDetail(
                            chatId = chatId,
                            chatType = 2,
                            name = d?.name ?: "",
                            avatarUrl = d?.avatar_url ?: "",
                            introduction = d?.introduction ?: "",
                            memberCount = d?.member,
                            ownerId = d?.owner?.takeIf { it.isNotEmpty() },
                            groupCode = d?.group_code?.takeIf { it.isNotEmpty() },
                            categoryName = d?.category_name?.takeIf { it.isNotEmpty() },
                            categoryId = d?.category_id,
                            myGroupNickname = d?.my_group_nickname?.takeIf { it.isNotEmpty() },
                            isPrivate = (d?.private_ ?: 0) == 1,
                            isGag = d?.is_gag ?: false,
                            doNotDisturb = (d?.do_not_disturb ?: 0) == 1,
                            permissionLevel = d?.permisson_level ?: 0,
                            directJoin = (d?.direct_join ?: 0) == 1,
                            historyMsg = (d?.history_msg ?: 0) == 1,
                            hideGroupMembers = (d?.hide_group_members ?: 0L) == 1L
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun getBotDetail(token: String, chatId: String): Result<ConversationDetail> =
        withContext(Dispatchers.IO) {
            try {
                val req = buildRequest(
                    "/v1/bot/bot-info",
                    token,
                    bot_info_send(id = chatId).encode()
                )
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = bot_info.ADAPTER.decode(response.body.bytes())
                    if (result.status?.code != 1) {
                        return@use Result.failure(Exception(result.status?.msg ?: "加载失败"))
                    }
                    val d = result.data_
                    Result.success(
                        ConversationDetail(
                            chatId = chatId,
                            chatType = 3,
                            name = d?.name ?: "",
                            avatarUrl = d?.avatar_url ?: "",
                            introduction = d?.introduction ?: "",
                            createBy = d?.create_by?.takeIf { it.isNotEmpty() },
                            createTime = d?.create_time?.takeIf { it > 0 },
                            usageCount = d?.headcount,
                            groupLimit = (d?.group_limit ?: 0L) == 1L,
                            isPrivate = (d?.private_ ?: 0L) == 1L,
                            isStop = (d?.is_stop ?: 0L) == 1L,
                            doNotDisturb = (d?.do_not_disturb ?: 0L) == 1L
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
