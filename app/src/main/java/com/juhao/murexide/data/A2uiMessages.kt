package com.juhao.murexide.data

import androidx.a2ui.model.protocol.A2uiClientEventMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private const val MAX_A2UI_CONTENT_LENGTH = 1_048_576
private const val MAX_A2UI_MESSAGES = 256
private const val A2UI_VERSION = "1.0"
private const val A2UI_CATALOG = "https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json"
private const val A2UI_RUNTIME_VERSION = "v0.9"
private const val A2UI_RUNTIME_CATALOG = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"
private const val A2UI_USER_MESSAGE_KEY = "__murexide_a2ui_user_message"
private val a2uiOperations = setOf(
    "createSurface", "updateComponents", "updateDataModel", "deleteSurface",
    "callRendererFunction", "agentFunctionResponse"
)
private val a2uiFunctionReturnTypes = mapOf(
    "required" to "boolean", "regex" to "boolean", "length" to "boolean",
    "numeric" to "boolean", "email" to "boolean", "and" to "boolean",
    "or" to "boolean", "not" to "boolean", "formatString" to "string",
    "formatNumber" to "string", "formatCurrency" to "string",
    "formatDate" to "string", "pluralize" to "string", "openUrl" to "void"
)

internal fun parseA2uiMessages(content: String): List<String> {
    require(content.length <= MAX_A2UI_CONTENT_LENGTH) { "A2UI 消息过大" }
    var text = content.trim().removePrefix("\uFEFF").trim()
    if (text.startsWith("```")) {
        val start = text.indexOf('\n')
        require(start >= 0 && text.endsWith("```")) { "A2UI 代码块不完整" }
        text = text.substring(start + 1, text.length - 3).trim()
    }
    require(text.isNotEmpty()) { "A2UI 消息为空" }
    val document = runCatching { Json.parseToJsonElement(text) }.getOrNull()
    val elements = when (document) {
        is JsonArray -> document.toList()
        is JsonObject -> {
            if (document.keys.none { it in a2uiOperations } && document["messages"] is JsonArray) {
                (document.getValue("messages") as JsonArray).toList()
            } else {
                listOf(document)
            }
        }
        null -> text.lineSequence().filter { it.isNotBlank() }.map {
            Json.parseToJsonElement(it)
        }.take(MAX_A2UI_MESSAGES + 1).toList()
        else -> throw IllegalArgumentException("A2UI 消息必须是 JSON 对象或数组")
    }
    require(elements.isNotEmpty() && elements.size <= MAX_A2UI_MESSAGES) {
        "A2UI 消息数量无效"
    }
    return elements.flatMap { element ->
        require(element is JsonObject) { "A2UI 消息必须是 JSON 对象" }
        require(element.keys.count { it in a2uiOperations } == 1) { "A2UI 消息操作无效" }
        val version = element["version"]
        require(version == null || version == JsonPrimitive(A2UI_VERSION)) {
            "暂不支持此 A2UI 协议版本，请使用 1.0"
        }
        val operation = element.keys.single { it in a2uiOperations }
        val payload = element[operation] as? JsonObject
            ?: throw IllegalArgumentException("A2UI $operation 数据必须是 JSON 对象")
        if (operation == "callRendererFunction" || operation == "agentFunctionResponse") {
            return@flatMap listOf(element.toString())
        }
        val surfaceId = payload["surfaceId"]
        require(surfaceId is JsonPrimitive && surfaceId.isString && surfaceId.content.isNotBlank()) {
            "A2UI surfaceId 无效"
        }
        when (operation) {
            "createSurface" -> buildList {
                val catalogId = payload["catalogId"] ?: JsonPrimitive(A2UI_CATALOG)
                add(a2uiRuntimeMessage(operation, JsonObject(
                    payload.filterKeys { it !in setOf("components", "dataModel", "metadata") } +
                        ("catalogId" to catalogId.toA2uiRuntimeCatalog())
                )))
                payload["dataModel"]?.let { model ->
                    require(model is JsonObject) { "A2UI 初始数据模型必须是 JSON 对象" }
                    add(a2uiRuntimeMessage("updateDataModel", buildJsonObject {
                        put("surfaceId", surfaceId)
                        put("path", "/")
                        put("value", model)
                    }))
                }
                payload["components"]?.let { components ->
                    add(a2uiRuntimeMessage("updateComponents", buildJsonObject {
                        put("surfaceId", surfaceId)
                        put("components", components.toA2uiRuntimeComponents())
                    }))
                }
            }
            "updateComponents" -> listOf(a2uiRuntimeMessage(operation, JsonObject(
                payload + ("components" to (payload["components"]
                    ?: throw IllegalArgumentException("A2UI 缺少组件列表")).toA2uiRuntimeComponents())
            )))
            "updateDataModel" -> {
                require("value" in payload) { "A2UI 数据模型更新缺少 value" }
                listOf(a2uiRuntimeMessage(operation, payload))
            }
            else -> listOf(a2uiRuntimeMessage(operation, payload))
        }
    }
}

private fun JsonElement.toA2uiRuntimeCatalog(): JsonElement =
    if (this == JsonPrimitive(A2UI_CATALOG)) JsonPrimitive(A2UI_RUNTIME_CATALOG) else this

private fun a2uiRuntimeMessage(operation: String, payload: JsonObject): String =
    buildJsonObject {
        put("version", A2UI_RUNTIME_VERSION)
        put(operation, payload)
    }.toString()

private fun JsonElement.toA2uiRuntimeComponents(): JsonArray {
    require(this is JsonArray && isNotEmpty()) { "A2UI 组件列表无效" }
    return JsonArray(map { component ->
        require(component is JsonObject) { "A2UI 组件必须是 JSON 对象" }
        JsonObject(component.filterKeys { it != "@metadata" }).toA2uiRuntimeValue()
    })
}

private fun JsonElement.toA2uiRuntimeValue(): JsonElement = when (this) {
    is JsonArray -> JsonArray(map { it.toA2uiRuntimeValue() })
    is JsonObject -> {
        val function = this["@call"]
        val returnType = function?.let {
            a2uiFunctionReturnTypes[(it as? JsonPrimitive)?.content] ?: "any"
        }
        val converted = entries.associate { (key, value) ->
            val runtimeKey = when (key) {
                "@path" -> "path"
                "@call" -> "call"
                "@accessibility" -> "accessibility"
                "@checks" -> "checks"
                "@catalogId" -> "catalogId"
                else -> key
            }
            runtimeKey to if (runtimeKey == "catalogId") {
                value.toA2uiRuntimeCatalog()
            } else {
                value.toA2uiRuntimeValue()
            }
        }.toMutableMap()
        if (returnType != null) converted["returnType"] = JsonPrimitive(returnType)
        val event = converted["event"] as? JsonObject
        event?.get("userMessage")?.let { userMessage ->
            val context = event["context"] as? JsonObject ?: JsonObject(emptyMap())
            require(A2UI_USER_MESSAGE_KEY !in context) { "A2UI 事件上下文使用了保留字段" }
            converted["event"] = JsonObject(
                event.filterKeys { it != "userMessage" } +
                    ("context" to JsonObject(context + (A2UI_USER_MESSAGE_KEY to userMessage)))
            )
        }
        JsonObject(converted)
    }
    else -> this
}

internal fun A2uiClientEventMessage.toMessageButton(): MessageButton {
    val timestampText = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(timestamp))
    val actionMessage = buildJsonObject {
        put("version", A2UI_VERSION)
        put("action", buildJsonObject {
            put("name", type)
            put("surfaceId", surfaceId)
            put("sourceComponentId", componentId)
            put("timestamp", timestampText)
            put("context", context.filterKeys { it != A2UI_USER_MESSAGE_KEY }.toA2uiJson())
            (context[A2UI_USER_MESSAGE_KEY] as? String)?.let { put("userMessage", it) }
        })
    }
    return actionMessage.toA2uiMessageButton(type, clientDataModel?.surfaces?.toA2uiJson())
}

internal fun JsonObject.toA2uiMessageButton(
    label: String,
    surfaces: JsonElement? = null
): MessageButton {
    val message = this
    val payload = buildJsonObject {
        put("kind", "message")
        put("role", "user")
        put("messageId", UUID.randomUUID().toString())
        put("parts", JsonArray(listOf(buildJsonObject {
            put("kind", "data")
            put("data", JsonArray(listOf(message)))
            put("metadata", buildJsonObject { put("mimeType", "application/a2ui+json") })
        })))
        surfaces?.let { model ->
            put("metadata", buildJsonObject {
                put("a2uiClientDataModel", buildJsonObject {
                    put("version", A2UI_VERSION)
                    put("surfaces", model)
                })
            })
        }
    }
    return MessageButton(
        text = label,
        actionType = MessageButton.ACTION_REPORT,
        value = payload.toString()
    )
}

private fun Any?.toA2uiJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) ->
        require(key is String) { "A2UI 数据键必须为字符串" }
        key to value.toA2uiJson()
    })
    is List<*> -> JsonArray(map { it.toA2uiJson() })
    else -> throw IllegalArgumentException("A2UI 数据类型无效")
}
