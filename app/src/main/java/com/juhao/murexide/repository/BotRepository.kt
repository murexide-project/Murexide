package com.juhao.murexide.repository

import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 创建者视角下的机器人信息（来自 /v1/bot/bot-group-list + /v1/bot/console/my-bots）。 */
@Serializable
data class ManagedBot(
    val botId: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val introduction: String = "",
    val token: String = "",
    val link: String = "",
    val isPrivate: Boolean = false,
    val isStop: Boolean = false,
    val alwaysAgree: Boolean = false,
    val groupLimit: Boolean = false,
    val linkStop: Boolean = false,
    /** 0-url 订阅，1-wss 订阅 */
    val subscribeType: Int = 0,
    val settingJson: String = "",
    val headcount: Long = 0
)

/** 机器人管理接口（JSON），文档见 /api/v1/bot。 */
class BotRepository(
    private val client: OkHttpClient = NetworkClient.okHttpClient,
    private val baseUrl: String = NetworkClient.BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** POST JSON 并校验 code == 1，返回响应根对象。 */
    private suspend fun postJson(token: String, path: String, body: JsonObject): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl$path")
                    .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMediaType))
                    .header("token", token)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                    val text = response.body.string()
                    if (text.isBlank()) return@use Result.failure(Exception("响应为空"))
                    val root = json.parseToJsonElement(text).jsonObject
                    val code = root["code"].asInt()
                    if (code == 1) {
                        Result.success(root)
                    } else {
                        Result.failure(Exception(root["msg"].asString().ifBlank { "请求失败 ($code)" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 获取我创建的所有机器人。 */
    suspend fun getMyBots(token: String): Result<List<ManagedBot>> {
        val list = postJson(token, "/v1/bot/bot-group-list", JsonObject(emptyMap()))
            .map { root -> root.botsArray().map { it.jsonObject.toManagedBot() } }
            .getOrElse { return Result.failure(it) }

        // console/my-bots 额外返回 subscribeType / linkStop；失败不影响主流程
        val extra = postJson(token, "/v1/bot/console/my-bots", JsonObject(emptyMap()))
            .getOrNull()
            ?.botsArray()
            ?.associate { e ->
                val o = e.jsonObject
                o["botId"].asString() to o
            }
            .orEmpty()

        return Result.success(list.map { bot ->
            val o = extra[bot.botId] ?: return@map bot
            bot.copy(
                subscribeType = o["subscribeType"].asInt(),
                linkStop = o["linkStop"].asInt() == 1,
                link = o["link"].asString().ifBlank { bot.link },
                token = o["token"].asString().ifBlank { bot.token }
            )
        })
    }

    suspend fun getMyBot(token: String, botId: String): Result<ManagedBot> =
        getMyBots(token).mapCatching { bots ->
            bots.firstOrNull { it.botId == botId } ?: throw Exception("只有机器人创建者可以编辑")
        }

    /** 更改机器人信息：名称 / 简介 / 头像 / 公开或私有。 */
    suspend fun editBot(
        token: String,
        botId: String,
        nickname: String,
        introduction: String,
        avatarUrl: String,
        isPrivate: Boolean
    ): Result<Unit> = postJson(token, "/v1/bot/web-edit-bot", buildJsonObject {
        put("nickname", nickname)
        put("introduction", introduction)
        put("avatarUrl", avatarUrl)
        put("botId", botId)
        put("private", if (isPrivate) 1 else 0)
    }).map { }

    /** 设置消息订阅接口。subscribeType：0-url，1-wss。 */
    suspend fun editSubscribedLink(
        token: String,
        botId: String,
        link: String,
        subscribeType: Int
    ): Result<Unit> = postJson(token, "/v1/bot/edit-subscribed-link", buildJsonObject {
        put("botId", botId)
        put("link", link)
        put("subscribeType", subscribeType)
    }).map { }

    /** 更改机器人设置 JSON（JSON 数组字符串）。 */
    suspend fun editSettingJson(token: String, botId: String, settingJson: String): Result<Unit> =
        postJson(token, "/v1/bot/edit-setting-json", buildJsonObject {
            put("id", botId)
            put("settingJson", settingJson)
        }).map { }

    /** 重置机器人 token，返回新 token。 */
    suspend fun resetToken(token: String, botId: String): Result<String> =
        postJson(token, "/v1/bot/reset-bot-token", buildJsonObject { put("botId", botId) })
            .mapCatching { root ->
                root["data"]?.jsonObject?.get("token").asString().ifBlank {
                    throw Exception("未返回新 Token")
                }
            }

    suspend fun removeFollower(token: String, botId: String, userId: String): Result<Unit> =
        postJson(token, "/v1/bot/remove-follower", buildJsonObject {
            put("botId", botId)
            put("userId", userId)
        }).map { }

    suspend fun removeGroup(token: String, botId: String, groupId: String): Result<Unit> =
        postJson(token, "/v1/bot/remove-group", buildJsonObject {
            put("botId", botId)
            put("groupId", groupId)
        }).map { }

    private fun JsonObject.botsArray(): JsonArray =
        (this["data"] as? JsonObject)
            ?.let { it["list"] as? JsonObject }
            ?.let { it["bots"] as? JsonArray }
            ?: JsonArray(emptyList())

    private fun JsonObject.toManagedBot() = ManagedBot(
        botId = this["botId"].asString(),
        nickname = this["nickname"].asString(),
        avatarUrl = this["avatarUrl"].asString(),
        introduction = this["introduction"].asString(),
        token = this["token"].asString(),
        link = this["link"].asString(),
        isPrivate = this["private"].asInt() == 1,
        isStop = this["isStop"].asInt() == 1,
        alwaysAgree = this["alwaysAgree"].asInt() == 1,
        groupLimit = this["groupLimit"].asInt() == 1,
        linkStop = this["linkStop"].asInt() == 1,
        subscribeType = this["subscribeType"].asInt(),
        settingJson = this["settingJson"].asString(),
        headcount = (this["headcount"] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() } ?: 0L
    )
}

/** 兼容接口里数字/字符串混用（如 "0" 与 0）。 */
private fun JsonElement?.asInt(): Int {
    val p = this as? JsonPrimitive ?: return 0
    return p.intOrNull ?: p.contentOrNull?.toIntOrNull() ?: 0
}

private fun JsonElement?.asString(): String =
    (this as? JsonPrimitive)?.contentOrNull.orEmpty()
