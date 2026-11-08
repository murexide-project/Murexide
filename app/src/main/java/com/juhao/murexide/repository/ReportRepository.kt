package com.juhao.murexide.repository

import com.juhao.murexide.data.ReportRequest
import com.juhao.murexide.data.ReportResponse
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val reportJsonMediaType = "application/json; charset=utf-8".toMediaType()

private val reportJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

class ReportRepository {

    private val client = NetworkClient.okHttpClient

    suspend fun submitReport(
        token: String,
        chatId: String,
        chatType: Int,
        chatName: String,
        content: String,
        reason: String,
        url: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = reportJson.encodeToString(
                ReportRequest.serializer(),
                ReportRequest(
                    chatId = chatId,
                    chatType = chatType,
                    chatName = chatName,
                    content = content,
                    url = url,
                    reason = reason
                )
            )
            val request = Request.Builder()
                .url("${NetworkClient.BASE_URL}/v1/report/create")
                .post(bodyJson.toRequestBody(reportJsonMediaType))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(
                        Exception("HTTP error: ${response.code}")
                    )
                }
                val bodyStr = response.body.string()
                val parsed = reportJson.decodeFromString(
                    ReportResponse.serializer(),
                    bodyStr
                )
                if (parsed.code == 1) {
                    Result.success(Unit)
                } else {
                    Result.failure(
                        Exception(parsed.msg.ifBlank { "举报失败" })
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}