package com.juhao.murexide.repository

import com.juhao.murexide.data.DiskFileItem
import com.juhao.murexide.data.DiskListResponse
import com.juhao.murexide.data.DiskSizeResponse
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DiskRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun post(
        path: String,
        token: String,
        body: Map<String, Any?>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                body.forEach { (k, v) ->
                    when (v) {
                        is String -> put(k, v)
                        is Int -> put(k, v)
                        is Long -> put(k, v)
                        is Boolean -> put(k, v)
                        null -> put(k, "")
                    }
                }
            }.toString()
            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(resp.body.string())
                } else {
                    Result.failure(Exception("HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(
        token: String,
        chatId: String,
        folderId: Long,
        sort: String = "name_asc"
    ): Result<List<DiskFileItem>> = withContext(Dispatchers.IO) {
        post(
            "/v1/disk/file-list",
            token,
            mapOf("chatId" to chatId, "chatType" to 2, "folderId" to folderId, "sort" to sort)
        ).mapCatching { body ->
            val parsed = json.decodeFromString(DiskListResponse.serializer(), body)
            if (parsed.code == 1) parsed.data?.list.orEmpty()
            else throw Exception(parsed.msg.ifEmpty { "获取列表失败" })
        }
    }

    suspend fun getTotalSize(token: String, chatId: String): Result<Long> =
        withContext(Dispatchers.IO) {
            post("/v1/disk/file-size", token, mapOf("chatId" to chatId, "chatType" to 2))
                .mapCatching { body ->
                    val parsed = json.decodeFromString(DiskSizeResponse.serializer(), body)
                    if (parsed.code == 1) parsed.data?.totalSize ?: 0L
                    else throw Exception(parsed.msg.ifEmpty { "获取大小失败" })
                }
        }

    suspend fun createFolder(
        token: String,
        chatId: String,
        folderName: String,
        parentFolderId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        post(
            "/v1/disk/create-folder",
            token,
            mapOf(
                "chatId" to chatId,
                "chatType" to 2,
                "folderName" to folderName,
                "parentFolderId" to parentFolderId
            )
        ).mapCatching { body ->
            val parsed = json.decodeFromString(DiskListResponse.serializer(), body)
            if (parsed.code != 1) throw Exception(parsed.msg.ifEmpty { "创建文件夹失败" })
        }
    }

    suspend fun uploadFile(
        token: String,
        chatId: String,
        fileSizeKb: Long,
        fileName: String,
        fileMd5WithExt: String,
        fileEtag: String,
        qiniuKey: String,
        folderId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        post(
            "/v1/disk/upload-file",
            token,
            mapOf(
                "chatId" to chatId,
                "chatType" to 2,
                "fileSize" to fileSizeKb,
                "fileName" to fileName,
                "fileMd5" to fileMd5WithExt,
                "fileEtag" to fileEtag,
                "qiniuKey" to qiniuKey,
                "folderId" to folderId
            )
        ).mapCatching { body ->
            val parsed = json.decodeFromString(DiskListResponse.serializer(), body)
            if (parsed.code != 1) throw Exception(parsed.msg.ifEmpty { "上传失败" })
        }
    }

    suspend fun rename(
        token: String,
        id: Long,
        objectType: Int,
        name: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        post(
            "/v1/disk/rename",
            token,
            mapOf("id" to id, "objectType" to objectType, "name" to name)
        ).mapCatching { body ->
            val parsed = json.decodeFromString(DiskListResponse.serializer(), body)
            if (parsed.code != 1) throw Exception(parsed.msg.ifEmpty { "重命名失败" })
        }
    }

    suspend fun remove(
        token: String,
        id: Long,
        objectType: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        post(
            "/v1/disk/remove",
            token,
            mapOf("id" to id, "objectType" to objectType)
        ).mapCatching { body ->
            val parsed = json.decodeFromString(DiskListResponse.serializer(), body)
            if (parsed.code != 1) throw Exception(parsed.msg.ifEmpty { "删除失败" })
        }
    }
}