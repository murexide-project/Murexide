package com.juhao.murexide.data

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal class A2uiFunctionRuntime(
    private val onOutbound: (JsonObject) -> Unit,
    private val openUrl: (String) -> Unit = { throw IllegalStateException("URL handler is not attached") }
) {
    private val models = linkedMapOf<String, JsonElement>()
    private val bases = mutableMapOf<String, String>()
    private val pending = linkedMapOf<String, (JsonObject) -> Unit>()
    val pendingCount: Int get() = pending.size

    fun process(message: JsonObject): Boolean {
        val call = message["callRendererFunction"] as? JsonObject
        if (call != null) {
            val id = call["functionCallId"]
            val response = buildJsonObject {
                if (id != null) put("functionCallId", id)
                try {
                    require(id is JsonPrimitive && id.isString && id.content.isNotBlank()) { "Missing functionCallId" }
                    val function = call["callFunction"] as? JsonObject ?: throw IllegalArgumentException("Missing callFunction")
                    put("value", invokeCall(function, call.string("surfaceId"), false, 0))
                } catch (e: Exception) {
                    put("error", error(e))
                }
            }
            onOutbound(buildJsonObject { put("version", "1.0"); put("rendererFunctionResponse", response) })
            return true
        }
        val response = message["agentFunctionResponse"] as? JsonObject ?: return false
        val id = response.string("functionCallId")
        if (id != null && (response.containsKey("value") || response["error"] is JsonObject)) pending.remove(id)?.invoke(response)
        return true
    }

    fun observe(message: JsonObject) {
        (message["createSurface"] as? JsonObject)?.let { payload ->
            payload.string("surfaceId")?.let { id ->
                models[id] = payload["dataModel"] ?: JsonObject(emptyMap())
                bases[id] = "/"
            }
        }
        (message["updateDataModel"] as? JsonObject)?.let { payload ->
            payload.string("surfaceId")?.let { id ->
                val value = payload["value"] ?: payload["contents"]?.let(::decodeContents) ?: JsonNull
                updateSurface(id, value, payload.string("path") ?: "/")
            }
        }
        (message["deleteSurface"] as? JsonObject)?.string("surfaceId")?.let { id ->
            models.remove(id)
            bases.remove(id)
        }
    }

    fun replaceDataModels(surfaces: Map<String, Any?>) {
        surfaces.forEach { (id, value) -> models[id] = toJson(value) }
    }

    fun updateSurface(surfaceId: String, model: JsonElement, path: String = "/", basePath: String = "/") {
        bases[surfaceId] = normalize(basePath, "/")
        val parts = segments(normalize(path, bases[surfaceId] ?: "/"))
        models[surfaceId] = replace(models[surfaceId] ?: JsonObject(emptyMap()), parts, model)
    }

    fun evaluate(value: JsonElement, surfaceId: String?): JsonElement = evaluate(value, surfaceId, false, 0)

    fun evaluateUserAction(value: JsonElement, surfaceId: String?): JsonElement = evaluate(value, surfaceId, true, 0)

    fun validate(value: JsonElement, surfaceId: String?): JsonObject = try {
        val result = evaluate(value, surfaceId)
        val valid = truth(result)
        buildJsonObject {
            put("valid", valid)
            put("errors", if (valid) JsonArray(emptyList()) else JsonArray(listOf(buildJsonObject {
                put("code", "validation_failed")
                put("message", (value as? JsonObject)?.string("message") ?: "Validation failed")
            })))
        }
    } catch (e: Exception) {
        buildJsonObject { put("valid", false); put("errors", JsonArray(listOf(error(e)))) }
    }

    fun callAgentFunction(
        name: String,
        arguments: JsonElement = JsonObject(emptyMap()),
        surfaceId: String? = null,
        catalogId: String? = null,
        onResponse: (JsonObject) -> Unit
    ): String {
        val id = UUID.randomUUID().toString()
        pending[id] = onResponse
        try {
            onOutbound(buildJsonObject {
                put("version", "1.0")
                put("callAgentFunction", buildJsonObject {
                    put("functionCallId", id)
                    put("callFunction", buildJsonObject {
                        put("@call", name)
                        put("args", arguments)
                        catalogId?.let { put("catalogId", it) }
                    })
                    surfaceId?.let { put("surfaceId", it) }
                })
            })
        } catch (e: Exception) {
            pending.remove(id)
            throw e
        }
        return id
    }

    fun cancelAgentFunction(callId: String): Boolean = pending.remove(callId) != null

    private fun evaluate(value: JsonElement, surfaceId: String?, userAction: Boolean, depth: Int): JsonElement {
        require(depth < 64) { "Function nesting is too deep" }
        return when (value) {
            is JsonArray -> JsonArray(value.map { evaluate(it, surfaceId, userAction, depth + 1) })
            is JsonObject -> {
                val path = value.string("@path")
                when {
                    path != null && value.keys == setOf("@path") -> resolve(path, surfaceId)
                    value["@call"] != null -> invokeCall(value, surfaceId, userAction, depth + 1)
                    else -> JsonObject(value.mapValues { evaluate(it.value, surfaceId, userAction, depth + 1) })
                }
            }
            is JsonPrimitive -> value
        }
    }

    private fun invokeCall(call: JsonObject, surfaceId: String?, userAction: Boolean, depth: Int): JsonElement {
        require(depth < 64) { "Function nesting is too deep" }
        val name = call.string("@call")
            ?: throw IllegalArgumentException("Missing function name")
        val raw = call["args"] ?: call["arguments"] ?: JsonObject(emptyMap())
        val positional = raw as? JsonArray
        val named = raw as? JsonObject ?: JsonObject(emptyMap())
        fun arg(key: String, index: Int = 0, fallback: JsonElement = JsonNull): JsonElement =
            evaluate(named[key] ?: positional?.getOrNull(index) ?: fallback, surfaceId, userAction, depth + 1)
        fun text(key: String, index: Int = 0, fallback: String = "") = string(arg(key, index, JsonPrimitive(fallback)))
        fun number(key: String, index: Int = 0): BigDecimal? = string(arg(key, index)).toBigDecimalOrNull()
        fun bound(key: String): BigDecimal? = named[key]?.let { string(evaluate(it, surfaceId, userAction, depth + 1)).toBigDecimalOrNull() }
        fun locale(): Locale = Locale.forLanguageTag(text("locale", fallback = Locale.getDefault().toLanguageTag()).replace('_', '-'))
        val function = name.substringAfterLast('#').substringAfterLast('/')
        val result: JsonElement = when (function) {
            "required" -> {
                val value = arg("value")
                JsonPrimitive(value != JsonNull && when (value) {
                    is JsonPrimitive -> !value.isString || value.content.isNotBlank()
                    is JsonArray -> value.isNotEmpty()
                    is JsonObject -> value.isNotEmpty()
                })
            }
            "regex" -> {
                val pattern = text("pattern", 1)
                val options = if (text("flags").contains('i')) setOf(RegexOption.IGNORE_CASE) else emptySet()
                JsonPrimitive(Regex(pattern, options).containsMatchIn(text("value")))
            }
            "length" -> {
                val value = arg("value")
                val size = when (value) { is JsonArray -> value.size; is JsonObject -> value.size; else -> string(value).codePointCount(0, string(value).length) }
                val n = size.toBigDecimal()
                JsonPrimitive((bound("min") ?: bound("minLength"))?.let { n >= it } != false &&
                    (bound("max") ?: bound("maxLength"))?.let { n <= it } != false && bound("exact")?.let { n == it } != false)
            }
            "numeric" -> {
                val n = number("value")
                JsonPrimitive(n != null && bound("min")?.let { n >= it } != false && bound("max")?.let { n <= it } != false &&
                    (!truth(arg("integer", fallback = JsonPrimitive(false))) || n.stripTrailingZeros().scale() <= 0))
            }
            "email" -> JsonPrimitive(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(text("value")))
            "and", "or" -> {
                val values = named["checks"] ?: named["values"] ?: named["operands"] ?: raw
                val operands = when (values) { is JsonArray -> values; is JsonObject -> JsonArray(values.values.toList()); else -> JsonArray(listOf(values)) }
                JsonPrimitive(if (function == "and") operands.all { truth(evaluate(it, surfaceId, userAction, depth + 1)) }
                    else operands.any { truth(evaluate(it, surfaceId, userAction, depth + 1)) })
            }
            "not" -> JsonPrimitive(!truth(arg("value")))
            "formatString" -> {
                val template = text("value")
                JsonPrimitive(Regex("\\$\\{([^{}]+)}").replace(template) { match ->
                    string(resolve(match.groupValues[1], surfaceId))
                })
            }
            "formatNumber", "formatCurrency" -> {
                val n = number("value") ?: throw IllegalArgumentException("Expected numeric value")
                val formatter = if (function == "formatCurrency") NumberFormat.getCurrencyInstance(locale()) else NumberFormat.getNumberInstance(locale())
                if (function == "formatCurrency") formatter.currency = Currency.getInstance(text("currency", 1, "USD"))
                bound("decimals")?.intValueExact()?.let { digits ->
                    require(digits >= 0) { "decimals must not be negative" }
                    formatter.maximumFractionDigits = digits
                    formatter.minimumFractionDigits = digits
                }
                formatter.isGroupingUsed = truth(arg("grouping", fallback = JsonPrimitive(true)))
                JsonPrimitive(formatter.format(n))
            }
            "formatDate" -> {
                val input = text("value")
                val zone = TimeZone.getTimeZone(text("timeZone", fallback = "UTC"))
                val date = input.toLongOrNull()?.let(::Date) ?: parseDate(input)
                val pattern = text("format", 1, text("pattern", fallback = "yyyy-MM-dd"))
                val formatter = SimpleDateFormat(pattern, locale()).apply { timeZone = zone }
                JsonPrimitive(formatter.format(date))
            }
            "pluralize" -> {
                val count = number("value") ?: throw IllegalArgumentException("Expected numeric value")
                val category = android.icu.text.PluralRules.forLocale(locale()).select(count.toDouble())
                val form = named[category] ?: named["other"]
                    ?: throw IllegalArgumentException("Missing plural form: other")
                evaluate(form, surfaceId, userAction, depth + 1)
            }
            "openUrl" -> {
                check(userAction) { "openUrl requires a user action" }
                val url = text("url")
                val uri = java.net.URI(url)
                require(uri.scheme?.lowercase(Locale.ROOT) in setOf("https", "http", "mailto", "tel")) { "URL scheme is not allowed" }
                openUrl(url)
                JsonNull
            }
            else -> throw IllegalArgumentException("Unknown renderer function: $name")
        }
        return if (function in setOf("required", "regex", "length", "numeric", "email")) {
            buildJsonObject { put("valid", truth(result)) }
        } else {
            result
        }
    }

    private fun resolve(path: String, surfaceId: String?): JsonElement {
        val id = surfaceId ?: return JsonNull
        return segments(normalize(path, bases[id] ?: "/")).fold(models[id] ?: JsonNull) { value, key ->
            when (value) { is JsonObject -> value[key] ?: JsonNull; is JsonArray -> key.toIntOrNull()?.let { value.getOrNull(it) } ?: JsonNull; else -> JsonNull }
        }
    }

    private fun normalize(path: String, base: String): String {
        val parts = mutableListOf<String>()
        (if (path.startsWith('/')) path else "$base/$path").split('/').forEach {
            when (it) { "", "." -> Unit; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex); else -> parts.add(it) }
        }
        return "/" + parts.joinToString("/")
    }

    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }.map { it.replace("~1", "/").replace("~0", "~") }

    private fun replace(node: JsonElement, path: List<String>, value: JsonElement): JsonElement {
        if (path.isEmpty()) return value
        val key = path.first()
        val rest = path.drop(1)
        if (node is JsonArray) {
            val index = if (key == "-") node.size else key.toIntOrNull() ?: throw IllegalArgumentException("Invalid array index")
            require(index in 0..node.size) { "Array index out of bounds" }
            val list = node.toMutableList()
            when {
                rest.isEmpty() && value == JsonNull -> if (index < list.size) list.removeAt(index)
                index == list.size -> list.add(replace(JsonNull, rest, value))
                else -> list[index] = replace(list[index], rest, value)
            }
            return JsonArray(list)
        }
        val map = (node as? JsonObject)?.toMutableMap() ?: linkedMapOf()
        if (rest.isEmpty() && value == JsonNull) {
            map.remove(key)
        } else {
            map[key] = replace(map[key] ?: JsonNull, rest, value)
        }
        return JsonObject(map)
    }

    private fun decodeContents(value: JsonElement): JsonElement {
        if (value !is JsonArray) return value
        return JsonObject(value.mapNotNull { item ->
            val entry = item as? JsonObject ?: return@mapNotNull null
            val key = entry.string("key") ?: return@mapNotNull null
            val content = entry["valueString"] ?: entry["valueNumber"] ?: entry["valueBoolean"] ?: entry["valueMap"]?.let(::decodeContents) ?: entry["value"] ?: JsonNull
            key to content
        }.toMap())
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to toJson(it.value) })
        is Iterable<*> -> JsonArray(value.map(::toJson))
        is Array<*> -> JsonArray(value.map(::toJson))
        else -> JsonPrimitive(value.toString())
    }

    private fun parseDate(value: String): Date {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd")
        for (pattern in patterns) {
            val parser = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }
            val position = java.text.ParsePosition(0)
            val parsed = parser.parse(value, position)
            if (parsed != null && position.index == value.length) return parsed
        }
        throw IllegalArgumentException("Invalid date: $value")
    }

    private fun truth(value: JsonElement): Boolean = when (value) {
        JsonNull -> false
        is JsonPrimitive -> value.booleanOrNull ?: value.doubleOrNull?.let { it != 0.0 } ?: value.content.isNotEmpty()
        is JsonObject -> value["valid"]?.let(::truth) ?: value.isNotEmpty()
        is JsonArray -> value.isNotEmpty()
    }

    private fun string(value: JsonElement): String = when (value) { JsonNull -> ""; is JsonPrimitive -> value.content; else -> value.toString() }
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun error(e: Exception) = buildJsonObject {
        put("code", if (e is IllegalStateException) "user_action_required" else "function_error")
        put("message", e.message ?: "Function failed")
    }
}
