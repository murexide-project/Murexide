package com.juhao.murexide.repository

import com.juhao.murexide.data.ConversationDetail
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.bot.bot_info
import com.juhao.murexide.proto.bot.bot_info_send
import com.juhao.murexide.proto.group.info
import com.juhao.murexide.proto.group.info_send
import com.juhao.murexide.proto.user.get_user
import com.juhao.murexide.proto.user.get_user_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 加载会话详情：根据会话类型分别请求用户 / 群聊 / 机器人信息。
 */
class ConversationDetailRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL

    suspend fun getDetail(
        token: String,
        chatId: String,
        chatType: Int
    ): Result<ConversationDetail> {
        return when (chatType) {
            2 -> getGroupDetail(token, chatId)
            3 -> getBotDetail(token, chatId)
            else -> getUserDetail(token, chatId)
        }
    }

    private fun buildRequest(path: String, token: String, body: ByteArray): Request =
        Request.Builder()
            .url("$baseUrl$path")
            .post(body.toRequestBody("application/octet-stream".toMediaType()))
            .header("token", token)
            .build()

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
                            myGroupNickname = d?.my_group_nickname?.takeIf { it.isNotEmpty() },
                            isPrivate = (d?.private_ ?: 0) == 1,
                            doNotDisturb = (d?.do_not_disturb ?: 0) == 1
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
