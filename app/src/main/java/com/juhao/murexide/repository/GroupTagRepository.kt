package com.juhao.murexide.repository

import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.data.GroupTag
import com.juhao.murexide.data.GroupTagMemberPage
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.group.tag_member
import com.juhao.murexide.proto.group.tag_member_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** 群组标签相关操作 */
class GroupTagRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val jsonMediaType = "application/json".toMediaType()
    private val protoMediaType = "application/octet-stream".toMediaType()

    suspend fun listTags(
        token: String,
        groupId: String,
        page: Int = 1,
        size: Int = 50,
        keyword: String = ""
    ): Result<List<GroupTag>> {
        val body = JSONObject().apply {
            put("groupId", groupId)
            put("size", size)
            put("page", page)
            put("tag", keyword)
        }
        return postJson(token, "/v1/group-tag/list", body).mapCatching { data ->
            val tags = mutableListOf<GroupTag>()
            val array = data.optJSONArray("list")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    tags.add(
                        GroupTag(
                            id = item.optLong("id"),
                            groupId = item.optString("groupId"),
                            tag = item.optString("tag"),
                            color = item.optString("color"),
                            desc = item.optString("desc"),
                            sort = item.optInt("sort"),
                            createTime = item.optLong("createTime")
                        )
                    )
                }
            }
            tags.toList()
        }
    }

    suspend fun createTag(
        token: String,
        groupId: String,
        tag: String,
        color: String,
        desc: String,
        sort: Int
    ): Result<Unit> {
        val body = JSONObject().apply {
            put("groupId", groupId)
            put("tag", tag)
            put("color", color)
            put("desc", desc)
            put("sort", sort)
        }
        return postJson(token, "/v1/group-tag/create", body).map { }
    }

    suspend fun editTag(
        token: String,
        id: Long,
        groupId: String,
        tag: String,
        color: String,
        desc: String,
        sort: Int
    ): Result<Unit> {
        val body = JSONObject().apply {
            put("id", id)
            put("groupId", groupId)
            put("tag", tag)
            put("color", color)
            put("desc", desc)
            put("sort", sort)
        }
        return postJson(token, "/v1/group-tag/edit", body).map { }
    }

    suspend fun deleteTag(token: String, id: Long): Result<Unit> {
        val body = JSONObject().apply { put("id", id) }
        return postJson(token, "/v1/group-tag/delete", body).map { }
    }

    suspend fun relateTag(token: String, userId: String, tagGroupId: Long): Result<Unit> {
        val body = JSONObject().apply {
            put("userId", userId)
            put("tagGroupId", tagGroupId)
        }
        return postJson(token, "/v1/group-tag/relate", body).map { }
    }

    suspend fun relateCancelTag(token: String, userId: String, tagGroupId: Long): Result<Unit> {
        val body = JSONObject().apply {
            put("userId", userId)
            put("tagGroupId", tagGroupId)
        }
        return postJson(token, "/v1/group-tag/relate-cancel", body).map { }
    }

    suspend fun listTagMembers(
        token: String,
        groupId: String,
        tagId: Long,
        page: Int = 1,
        size: Int = 50
    ): Result<GroupTagMemberPage> = withContext(Dispatchers.IO) {
        try {
            val body = tag_member_send(
                data_ = tag_member_send.Data(size = size, page = page),
                group_id = groupId,
                tag_id = tagId
            ).encode()

            val request = Request.Builder()
                .url("$baseUrl/v1/group-tag/members")
                .post(body.toRequestBody(protoMediaType))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val result = tag_member.ADAPTER.decode(response.body.bytes())
                if (result.status?.code != 1) {
                    return@use Result.failure(Exception(result.status?.msg ?: "标签成员加载失败"))
                }
                val members = result.user.mapNotNull { user ->
                    val info = user.user_info ?: return@mapNotNull null
                    GroupMember(
                        userId = info.user_id,
                        name = info.name,
                        avatarUrl = info.avatar_url,
                        permissionLevel = user.permission_level,
                        isVip = info.is_vip == 1,
                        isGag = user.is_gag == 1,
                        gagTime = user.gag_time
                    )
                }
                Result.success(GroupTagMemberPage(members = members, total = result.total))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun postJson(
        token: String,
        path: String,
        body: JSONObject
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(body.toString().toRequestBody(jsonMediaType))
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
                Result.success(resp.optJSONObject("data") ?: JSONObject())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
