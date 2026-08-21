package com.juhao.murexide.repository

import com.juhao.murexide.data.*
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CommunityRepository(
    private val token: String
) {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getBaList(typ: Int = 2, size: Int = 50, page: Int = 1): Result<List<BaItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "typ" to typ,
                    "size" to size,
                    "page" to page
                )
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/ba/following-ba-list")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString<BaListResponse>(responseBody)

                        if (result.code == 1) {
                            Result.success(result.data?.ba ?: emptyList())
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取分区列表失败" }))
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

    suspend fun getBaListByCreate(userId: String): Result<List<BaItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("userId" to userId)
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/ba/list-by-create")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString<BaCreateListResponse>(response.body.string())
                        if (result.code == 1) {
                            Result.success(result.data?.ba ?: emptyList())
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取分区失败" }))
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

    /** 查看自己发布的文章 */
    suspend fun getMyPostList(size: Int = 20, page: Int = 1): Result<PostListData> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("size" to size, "page" to page)
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/posts/my-post-list")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString<PostListResponse>(response.body.string())
                        if (result.code == 1) {
                            Result.success(result.data ?: PostListData(emptyList(), 0))
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取我的文章失败" }))
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

    suspend fun getBaInfo(baId: Int): Result<BaDetail> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("id" to baId)
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/ba/info")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString<BaInfoResponse>(responseBody)

                        if (result.code == 1 && result.data != null) {
                            Result.success(result.data.ba)
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取分区信息失败" }))
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

    suspend fun followBa(baId: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("baId" to baId, "followSource" to 2)
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/ba/user-follow-ba")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString<BaseResponse>(response.body.string())
                        if (result.code == 1) Result.success(true)
                        else Result.failure(Exception(result.msg.ifEmpty { "关注失败" }))
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun unfollowBa(baId: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("baId" to baId)
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/ba/user-unfollow-ba")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString<BaseResponse>(response.body.string())
                        if (result.code == 1) Result.success(true)
                        else Result.failure(Exception(result.msg.ifEmpty { "取关失败" }))
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createPost(
        baId: Int,
        title: String,
        content: String,
        contentType: Int,
        groupId: String = "",
        draftId: Int = 0
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("baId", baId)
                    put("groupId", groupId)
                    put("title", title)
                    put("content", content)
                    put("contentType", contentType)
                    put("draftId", draftId)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/posts/create")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString<CreatePostResponse>(response.body.string())
                        if (result.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "发布失败" }))
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

    suspend fun editPost(
        postId: Int,
        title: String,
        content: String,
        contentType: Int
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("postId", postId)
                    put("title", title)
                    put("content", content)
                    put("contentType", contentType)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/posts/edit")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString<EditPostResponse>(response.body.string())
                        if (result.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "编辑失败" }))
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

    suspend fun deletePost(
        postId: Int
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("postId", postId)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/posts/delete")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString<EditPostResponse>(response.body.string())
                        if (result.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "删除失败" }))
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

    suspend fun getPostList(baId: Int, size: Int = 20, page: Int = 1): Result<PostListData> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "typ" to 1,
                    "baId" to baId,
                    "size" to size,
                    "page" to page
                )
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/posts/post-list")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString<PostListResponse>(responseBody)

                        if (result.code == 1) {
                            Result.success(result.data ?: PostListData(emptyList(), 0))
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取文章列表失败" }))
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

    suspend fun getRecommendPosts(size: Int = 20, page: Int = 1): Result<PostListData> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "size" to size,
                    "page" to page
                )
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/posts/post-list-recommend")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString<PostListResponse>(responseBody)

                        if (result.code == 1) {
                            Result.success(result.data ?: PostListData(emptyList(), 0))
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取推荐文章失败" }))
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

    suspend fun getPostDetail(id: Int): Result<PostDetailData> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("id" to id)
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/posts/post-detail")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString<PostDetailResponse>(responseBody)

                        if (result.code == 1 && result.data != null) {
                            Result.success(result.data)
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取文章详情失败" }))
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

    suspend fun getCommentList(postId: Int, size: Int = 10, page: Int = 1): Result<CommentListData> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "postId" to postId,
                    "size" to size,
                    "page" to page
                )
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/comment/comment-list")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString<CommentListResponse>(responseBody)

                        if (result.code == 1) {
                            Result.success(result.data ?: CommentListData())
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取评论列表失败" }))
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

    suspend fun toggleLike(postId: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("id" to postId)
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/posts/post-like")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString<BaseResponse>(responseBody)

                        if (result.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "操作失败" }))
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

    suspend fun toggleCollect(postId: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("id" to postId)
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/community/posts/post-collect")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString<BaseResponse>(responseBody)

                        if (result.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "操作失败" }))
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