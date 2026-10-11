package com.juhao.murexide.repository

import com.juhao.murexide.data.*
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ExpressionRepository {
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
    
    suspend fun createExpression(
        token: String,
        url: String
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("url", url)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
    
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/expression/create")
                    .post(requestBody)
                    .header("token", token)
                    .build()
    
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
    
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun deleteExpression(
        token: String,
        id: Int
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("id", id)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
    
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/expression/delete")
                    .post(requestBody)
                    .header("token", token)
                    .build()
    
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
    
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getStickerDetail(token: String, packId: Long): Result<StickerDetailData> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject { put("id", packId) }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/detail")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<StickerDetailResponse>(response.body.string())
                    if (result.code == 1 && result.data != null) {
                        Result.success(result.data)
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "获取表情包详情失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun addStickerPack(token: String, packId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject { put("id", packId) }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/add")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "收藏失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun removeStickerPack(token: String, packId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject { put("id", packId) }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/remove-sticker-pack")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "移除失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun createStickerPack(token: String, name: String): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject { put("name", name) }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/create-pack")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<StickerCreatePackResponse>(response.body.string())
                    if (result.code == 1 && result.data != null) {
                        Result.success(result.data.id)
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "创建失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun renameStickerPack(token: String, packId: Long, name: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("id", packId)
                    put("name", name)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/rename-pack")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "重命名失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun deleteStickerPack(token: String, packId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject { put("id", packId) }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/delete-pack")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "删除失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun addStickerToPack(
        token: String,
        name: String,
        url: String,
        stickerPackId: Long
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("name", name)
                    put("url", url)
                    put("stickerPackId", stickerPackId)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/add-sticker")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "添加失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun removeSticker(token: String, stickerId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject { put("id", stickerId) }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/remove-sticker")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "删除失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun importPack(token: String, name: String, fileKey: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("name", name)
                    put("fileKey", fileKey)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/import-pack")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "导入失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun renameSticker(token: String, stickerId: Long, name: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("id", stickerId)
                    put("name", name)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/rename-sticker")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "重命名失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun topExpression(token: String, id: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject { put("id", id) }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/expression/topping")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "置顶失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun sortStickerPacks(token: String, packs: List<StickerPack>): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val sortArray = buildJsonArray {
                    packs.forEachIndexed { index, pack ->
                        addJsonObject {
                            put("id", pack.id.toString())
                            put("sort", (packs.size - index).toString())
                        }
                    }
                }
                val sortJsonString = sortArray.toString()
    
                val params = buildJsonObject {
                    put("sort", sortJsonString)
                }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
    
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/sticker/sort")
                    .post(requestBody)
                    .header("token", token)
                    .build()
    
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "排序失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun addExpressionToFavorites(token: String, id: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject { put("id", id) }
                val requestBody = json.encodeToString(params).toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/expression/add")
                    .post(requestBody)
                    .header("token", token)
                    .build()
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val result = json.decodeFromString<CommonResponse>(response.body.string())
                    if (result.code == 1) Result.success(true)
                    else Result.failure(Exception(result.msg.ifBlank { "添加失败" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}