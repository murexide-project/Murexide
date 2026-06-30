package com.juhao.murexide.repository

import android.util.Log
import com.juhao.murexide.data.*
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    suspend fun getPostList(baId: Int, size: Int = 20, page: Int = 1): Result<List<PostItem>> {
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
                            Result.success(result.data?.posts ?: emptyList())
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

    suspend fun getRecommendPosts(size: Int = 20, page: Int = 1): Result<List<PostItem>> {
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
                            Result.success(result.data?.posts ?: emptyList())
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