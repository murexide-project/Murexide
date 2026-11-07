package com.juhao.murexide.repository

import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.group.list_member
import com.juhao.murexide.proto.group.list_member_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 群成员相关操作
 */
class GroupMemberRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listMembers(
        token: String,
        groupId: String,
        page: Int = 1,
        size: Int = 50,
        keywords: String = ""
    ): Result<List<GroupMember>> = withContext(Dispatchers.IO) {
        try {
            val body = list_member_send(
                data_ = list_member_send.Data(size = size, page = page),
                group_id = groupId,
                keywords = keywords
            ).encode()

            val request = Request.Builder()
                .url("$baseUrl/v1/group/list-member")
                .post(body.toRequestBody("application/octet-stream".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val result = list_member.ADAPTER.decode(response.body.bytes())
                if (result.status?.code != 1) {
                    return@use Result.failure(Exception(result.status?.msg ?: "加载失败"))
                }
                val members = result.user.mapNotNull { u ->
                    val info = u.user_info ?: return@mapNotNull null
                    GroupMember(
                        userId = info.user_id,
                        name = info.name,
                        avatarUrl = info.avatar_url,
                        permissionLevel = u.permission_level,
                        isVip = info.is_vip == 1,
                        isGag = u.is_gag == 1,
                        gagTime = u.gag_time
                    )
                }
                LocalCache.currentAccountId()?.let { accountId ->
                    val scope = "$groupId:$page"
                    if (page == 1) {
                        LocalCache.deletePayloadsByScopePrefix(
                            accountId,
                            LocalCache.KIND_MEMBERS,
                            "$groupId:%"
                        )
                    }
                    LocalCache.putPayload(
                        accountId = accountId,
                        kind = LocalCache.KIND_MEMBERS,
                        scope = scope,
                        payload = json.encodeToString(ListSerializer(GroupMember.serializer()), members),
                        ttlMs = 5 * 60_000L
                    )
                }
                Result.success(members)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCachedMembers(groupId: String, page: Int): List<GroupMember>? {
        val accountId = LocalCache.currentAccountId() ?: return null
        val cached = LocalCache.getPayload(accountId, LocalCache.KIND_MEMBERS, "$groupId:$page")
            ?: return null
        return runCatching {
            json.decodeFromString(ListSerializer(GroupMember.serializer()), cached.payload)
        }.getOrNull()
    }
}

class GroupRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL


    suspend fun removeMember(
        token: String,
        groupId: String,
        userId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("userId", userId)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/remove-member")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun gagMember(
        token: String,
        groupId: String,
        userId: String,
        gag: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("userId", userId)
                put("gag", gag)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/gag-member")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 移除群聊内机器人（/v1/group/remove-bot，群主/管理员可用）。 */
    suspend fun removeBot(
        token: String,
        groupId: String,
        botId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("botId", botId)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/remove-bot")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    
/** 设置消息类型限制（/v1/group/msg-type-limit，群主/管理员可用）。type 为逗号分隔字符串，如 "1,2,3"。 */
    suspend fun setMsgTypeLimit(
        token: String,
        groupId: String,
        type: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("type", type)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/msg-type-limit")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 设置群口令（/v1/group/edit-group-keyword，需 vip 且为群主）。 */
    suspend fun editGroupKeyword(
        token: String,
        groupId: String,
        keyword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("keyword", keyword)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/edit-group-keyword")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 设置禁止群成员上传到群云盘（/v1/group/edit-stop-member-upload-group-file，需群主）。 */
    suspend fun setStopMemberUploadGroupFile(
        token: String,
        groupId: String,
        stop: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("stopMemberUploadGroupFile", if (stop) 1 else 0)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/edit-stop-member-upload-group-file")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setAdmin(
        token: String,
        groupId: String,
        userId: String,
        isAdmin: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("userId", userId)
                put("userLevel", if (isAdmin) 2 else 0)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/manage-setting")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
