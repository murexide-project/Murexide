package com.juhao.murexide.repository

import com.juhao.murexide.data.BotBanner
import com.juhao.murexide.data.BotBannerResponse
import com.juhao.murexide.data.BotStoreItem
import com.juhao.murexide.data.BotStoreResponse
import com.juhao.murexide.data.GroupCategoryItem
import com.juhao.murexide.data.GroupCategoryResponse
import com.juhao.murexide.data.GroupRecommendItem
import com.juhao.murexide.data.GroupRecommendResponse
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DiscoverRepository(
    private val token: String
) {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun getBotBanners(): Result<List<BotBanner>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/bot/banner")
                    .post("{}".toRequestBody(jsonMediaType))
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<BotBannerResponse>(response.body.string())
                    if (result.code == 1) {
                        Result.success(result.data?.banners ?: emptyList())
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "获取轮播图失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getBotStore(): Result<List<BotStoreItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/bot/new-list")
                    .post("{}".toRequestBody(jsonMediaType))
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<BotStoreResponse>(response.body.string())
                    if (result.code == 1) {
                        Result.success(result.data?.bots ?: emptyList())
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "获取机器人列表失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getGroupCategories(): Result<List<GroupCategoryItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/group/category")
                    .get()
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<GroupCategoryResponse>(response.body.string())
                    if (result.code == 1) {
                        Result.success(result.data?.category ?: emptyList())
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "获取群聊分类失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRecommendedGroups(
        categoryId: Int,
        keyword: String
    ): Result<List<GroupRecommendItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("categoryId", categoryId)
                    put("keyword", keyword)
                }
                val request = Request.Builder()
                    .url("$baseUrl/v1/group/recommend/list")
                    .post(json.encodeToString(params).toRequestBody(jsonMediaType))
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<GroupRecommendResponse>(response.body.string())
                    if (result.code == 1) {
                        Result.success(result.data?.groups ?: emptyList())
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "获取推荐群聊失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
