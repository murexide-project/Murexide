package com.juhao.murexide.repository

import com.juhao.murexide.data.ExpressionItem
import com.juhao.murexide.data.ExpressionListResponse
import com.juhao.murexide.data.StickerListResponse
import com.juhao.murexide.data.StickerPack
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class StickerRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStickerList(token: String): Result<List<StickerPack>> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = "{}".toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/list")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString(
                            StickerListResponse.serializer(),
                            response.body.string()
                        )
                        if (result.code == 1) {
                            Result.success(result.data?.stickerPacks ?: emptyList())
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取表情包失败" }))
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

    suspend fun getExpressionList(token: String): Result<List<ExpressionItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = "{}".toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/expression/list")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val result = json.decodeFromString(
                            ExpressionListResponse.serializer(),
                            response.body.string()
                        )
                        if (result.code == 1) {
                            Result.success(result.data?.expression ?: emptyList())
                        } else {
                            Result.failure(Exception(result.msg.ifEmpty { "获取收藏表情失败" }))
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
