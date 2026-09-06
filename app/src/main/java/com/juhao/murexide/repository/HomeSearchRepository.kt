package com.juhao.murexide.repository

import com.juhao.murexide.data.HomeSearchResult
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HomeSearchRepository {
    private val client = NetworkClient.okHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchHome(token: String, word: String): Result<List<HomeSearchResult>> =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(buildJsonObject { put("word", word) })
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("${NetworkClient.BASE_URL}/v1/search/home-search")
                    .post(body)
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val root = json.parseToJsonElement(response.body.string()).jsonObject
                    val code = root["code"].asInt()
                    if (code != null && code != 1) {
                        return@use Result.failure(Exception(root["msg"].asString().orEmpty().ifBlank { "搜索失败" }))
                    }
                    val results = parseHomeSearchResults(root)
                    if (results == null) {
                        Result.failure(Exception("搜索响应格式不受支持"))
                    } else {
                        Result.success(results)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Normalizes the documented `data.list[].list` response into chat targets. */
    internal fun parseHomeSearchResults(root: JsonObject): List<HomeSearchResult>? {
        val container = root["data"] ?: root["result"] ?: return emptyList()
        val result = mutableListOf<HomeSearchResult>()
        var recognized = false

        fun appendList(element: JsonElement?, type: Int) {
            val items = element as? JsonArray ?: return
            recognized = true
            items.filterIsInstance<JsonObject>()
                .mapNotNull { item -> item.toSearchResult(type) }
                .forEach(result::add)
        }

        when (container) {
            is JsonArray -> {
                recognized = true
                container.filterIsInstance<JsonObject>().mapNotNull { item ->
                    item.toSearchResult(item["chatType"].asInt() ?: item["chat_type"].asInt())
                }.forEach(result::add)
            }
            is JsonObject -> {
                val categorizedLists = container["list"] as? JsonArray
                val isCategorizedResponse = categorizedLists?.any { item ->
                    val category = item as? JsonObject
                    category?.get("title") != null && category["list"] != null
                } == true
                if (isCategorizedResponse) {
                    recognized = true
                    categorizedLists.filterIsInstance<JsonObject>().forEach { category ->
                        val fallbackType = when (category["title"].asString()) {
                            "用户" -> 1
                            "群组", "群聊" -> 2
                            "机器人" -> 3
                            else -> null
                        }
                        (category["list"] as? JsonArray).orEmpty()
                            .filterIsInstance<JsonObject>()
                            .mapNotNull { item ->
                                item.toSearchResult(item["friendType"].asInt() ?: fallbackType)
                            }
                            .forEach(result::add)
                    }
                } else {
                    appendList(container["users"] ?: container["user"] ?: container["userList"], 1)
                    appendList(container["groups"] ?: container["group"] ?: container["groupList"], 2)
                    appendList(container["bots"] ?: container["bot"] ?: container["botList"], 3)
                    appendList(container["list"] ?: container["data"], 0)
                }
                if (!recognized && container.toSearchResult(container["chatType"].asInt()) != null) {
                    recognized = true
                    container.toSearchResult(container["chatType"].asInt())?.let(result::add)
                }
            }
            else -> Unit
        }
        return result.takeIf { recognized }
    }

    private fun JsonObject.toSearchResult(forcedType: Int?): HomeSearchResult? {
        val chatType = forcedType?.takeIf { it in 1..3 }
            ?: this["chatType"].asInt()?.takeIf { it in 1..3 }
            ?: this["chat_type"].asInt()?.takeIf { it in 1..3 }
            ?: this["friendType"].asInt()?.takeIf { it in 1..3 }
            ?: return null
        val chatId = sequenceOf(
            "friendId",
            "chatId",
            "chat_id",
            "id",
            "userId",
            "user_id",
            "groupId",
            "group_id",
            "botId",
            "bot_id"
        ).firstNotNullOfOrNull { this[it].asString()?.takeIf { value -> value.isNotBlank() } } ?: return null
        val name = sequenceOf(
            "nickname",
            "name",
            "groupName",
            "group_name",
            "botName",
            "bot_name"
        ).firstNotNullOfOrNull { this[it].asString()?.takeIf { value -> value.isNotBlank() } } ?: "未知对象"
        val avatar = sequenceOf(
            "avatarUrl",
            "avatar_url",
            "avatar",
            "headUrl",
            "head_url"
        ).firstNotNullOfOrNull { this[it].asString()?.takeIf { value -> value.isNotBlank() } }.orEmpty()
        val introduction = sequenceOf(
            "introduction",
            "introduce",
            "description"
        ).firstNotNullOfOrNull { this[it].asString()?.takeIf { value -> value.isNotBlank() } }.orEmpty()
        return HomeSearchResult(chatId, chatType, name, avatar, introduction)
    }
}

private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)
    ?.takeUnless { it.toString() == "null" }
    ?.content
private fun JsonElement?.asInt(): Int? = asString()?.toIntOrNull()
