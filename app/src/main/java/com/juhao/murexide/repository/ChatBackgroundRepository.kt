package com.juhao.murexide.repository

import com.juhao.murexide.data.ChatBackgroundItem
import com.juhao.murexide.data.ChatBackgroundListResponse
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatBackgroundRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getBackgroundList(token: String): Result<List<ChatBackgroundItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = "{}".toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/chat-background/list")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString(
                            ChatBackgroundListResponse.serializer(),
                            responseBody
                        )

                        if (result.code == 1) {
                            Result.success(result.data?.list ?: emptyList())
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取背景失败" }))
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

    suspend fun editBackground(
        token: String,
        userId: String,
        chatId: String,
        fileName: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = buildJsonObject {
                    put("userId", userId)
                    put("chatId", chatId)
                    put("url", fileName)
                }.toString()
                val requestBody = payload.toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/chat-background/edit")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val result = json.decodeFromString(
                            ChatBackgroundListResponse.serializer(),
                            responseBody
                        )
                        if (result.code == 1) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "设置背景失败" }))
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

    fun resolveBackground(list: List<ChatBackgroundItem>, chatId: String): String? {
        val specific = list.firstOrNull { it.chatId == chatId && it.imgUrl.isNotEmpty() }
        if (specific != null) return specific.imgUrl

        val global = list.firstOrNull { it.chatId == "all" && it.imgUrl.isNotEmpty() }
        return global?.imgUrl
    }
}