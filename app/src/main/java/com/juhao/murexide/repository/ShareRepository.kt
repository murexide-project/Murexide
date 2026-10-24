package com.juhao.murexide.repository

import com.juhao.murexide.data.ShareCreateRequest
import com.juhao.murexide.data.ShareCreateResponse
import com.juhao.murexide.data.ShareCreated
import com.juhao.murexide.data.ShareInfo
import com.juhao.murexide.data.ShareInfoRequest
import com.juhao.murexide.data.ShareInfoResponse
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

private val shareJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

class ShareRepository {

    private val client = NetworkClient.okHttpClient

    suspend fun getShareInfo(token: String, key: String, ts: String): Result<ShareInfo> =
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = shareJson.encodeToString(
                    ShareInfoRequest.serializer(),
                    ShareInfoRequest(key = key, ts = ts)
                )
                val request = Request.Builder()
                    .url("${NetworkClient.BASE_URL}/v1/share/info")
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(
                            Exception("HTTP error: ${response.code}")
                        )
                    }
                    val bodyStr = response.body.string()
                    val parsed = shareJson.decodeFromString(
                        ShareInfoResponse.serializer(),
                        bodyStr
                    )
                    val share = parsed.data?.share
                    if (parsed.code == 1 && share != null) {
                        Result.success(share)
                    } else {
                        Result.failure(
                            Exception(parsed.msg.ifBlank { "获取分享信息失败" })
                        )
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun createShareLink(
        token: String,
        chatId: String,
        chatType: Int,
        chatName: String
    ): Result<ShareCreated> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = shareJson.encodeToString(
                ShareCreateRequest.serializer(),
                ShareCreateRequest(
                    chatId = chatId,
                    chatType = chatType,
                    chatName = chatName
                )
            )
            val request = Request.Builder()
                .url("${NetworkClient.BASE_URL}/v1/share/create")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(
                        Exception("HTTP error: ${response.code}")
                    )
                }
                val bodyStr = response.body.string()
                val parsed = shareJson.decodeFromString(
                    ShareCreateResponse.serializer(),
                    bodyStr
                )
                val data = parsed.data
                if (parsed.code == 1 && data != null) {
                    Result.success(data)
                } else {
                    Result.failure(
                        Exception(parsed.msg.ifBlank { "创建分享链接失败" })
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}