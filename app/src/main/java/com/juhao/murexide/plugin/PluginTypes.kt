package com.juhao.murexide.plugin

import org.json.JSONObject

data class PluginManifest(
    val id: String,
    val name: String,
    val version: Int,
    val enabled: Boolean,
    val description: String?,
    val author: String? = null,
    val platform: String? = null,
    val minAppVersion: Int? = null,
    val changelog: String? = null,
    val icon: String? = null,
    val permissions: List<String> = emptyList(),
    val rules: List<PluginRule>,
    val onInstalled: List<PluginRule>,
    val onUninstalled: List<PluginRule>,
    val functions: List<PluginFunctionEntry> = emptyList()
)

data class PluginRule(
    val page: String,
    val event: String,
    val action: String,
    val position: String,
    val positionData: PositionData? = null,
    val whenExpr: WhenExpr? = null,
    val content: PluginContent
)

data class PositionData(
    val anchor: String,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val padding: PluginPadding? = null
)

data class PluginPadding(
    val top: Int = 0,
    val bottom: Int = 0,
    val start: Int = 0,
    val end: Int = 0,
    val horizontal: Int? = null,
    val vertical: Int? = null,
    val all: Int? = null
)

data class PluginContent(
    val type: String,
    val value: Any? = null,
    val color: String? = null,
    val title: String? = null,
    val message: String? = null,
    val confirmText: String? = null,
    val cancelText: String? = null,
    val duration: String? = null,
    val onClick: List<PluginAction>? = null,
    val onLongClick: List<PluginAction>? = null,
    val onChange: List<PluginAction>? = null,
    val execute: List<PluginAction>? = null,
    val children: List<PluginContent>? = null,
    val items: List<PluginContent>? = null,
    val padding: PluginPadding? = null,
    val margin: PluginPadding? = null,
    val width: String? = null,
    val height: String? = null,
    val weight: Float? = null,
    val align: String? = null,
    val background: String? = null,
    val radius: Float? = null,
    val hint: String? = null,
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    val size: String? = null,
    val src: String? = null,
    val icon: String? = null,
    val indeterminate: Boolean? = null,
    val bind: String? = null,
    val fontSize: Float? = null
)

data class PluginAction(
    val action: String,
    val target: String? = null,
    val text: String? = null,
    val extras: Map<String, Any?>? = null,
    val flags: List<String>? = null,
    val method: String? = null,
    val url: String? = null,
    val headers: Map<String, String>? = null,
    val body: String? = null,
    val saveTo: String? = null,
    val duration: Long? = null,
    val key: String? = null,
    val value: Any? = null,
    val persist: Boolean? = null,
    val condition: WhenExpr? = null,
    val then: List<PluginAction>? = null,
    val elseActions: List<PluginAction>? = null,
    val packageName: String? = null,
    val scheme: String? = null,
    val dialog: PluginContent? = null,
    val notifyTitle: String? = null,
    val mimeType: String? = null,
    val chatId: String? = null,
    val chatType: Int? = null,
    val readKey: String? = null,
    val title: String? = null,
    val message: String? = null
)

data class PluginFunctionEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: String? = null,
    val content: PluginContent
)

sealed class WhenExpr {
    object True : WhenExpr()
    object False : WhenExpr()
    data class Equals(val a: String, val b: String) : WhenExpr()
    data class NotEquals(val a: String, val b: String) : WhenExpr()
    data class Contains(val a: String, val b: String) : WhenExpr()
    data class StartsWith(val a: String, val b: String) : WhenExpr()
    data class EndsWith(val a: String, val b: String) : WhenExpr()
    data class Matches(val a: String, val b: String) : WhenExpr()
    data class Gt(val a: String, val b: String) : WhenExpr()
    data class Gte(val a: String, val b: String) : WhenExpr()
    data class Lt(val a: String, val b: String) : WhenExpr()
    data class Lte(val a: String, val b: String) : WhenExpr()
    data class And(val items: List<WhenExpr>) : WhenExpr()
    data class Or(val items: List<WhenExpr>) : WhenExpr()
    data class Not(val item: WhenExpr) : WhenExpr()
    data class Exists(val path: String) : WhenExpr()
    data class Empty(val path: String) : WhenExpr()

    companion object {
        fun parse(obj: Any?): WhenExpr? = when (obj) {
            null -> null
            is Boolean -> if (obj) True else False
            is String -> Exists(obj)
            is JSONObject -> parseObject(obj)
            else -> null
        }

        private fun parseObject(o: JSONObject): WhenExpr? {
            if (o.has("equals")) {
                val arr = o.getJSONArray("equals")
                return Equals(arr.getString(0), arr.getString(1))
            }
            if (o.has("notEquals")) {
                val arr = o.getJSONArray("notEquals")
                return NotEquals(arr.getString(0), arr.getString(1))
            }
            if (o.has("contains")) {
                val arr = o.getJSONArray("contains")
                return Contains(arr.getString(0), arr.getString(1))
            }
            if (o.has("startsWith")) {
                val arr = o.getJSONArray("startsWith")
                return StartsWith(arr.getString(0), arr.getString(1))
            }
            if (o.has("endsWith")) {
                val arr = o.getJSONArray("endsWith")
                return EndsWith(arr.getString(0), arr.getString(1))
            }
            if (o.has("matches")) {
                val arr = o.getJSONArray("matches")
                return Matches(arr.getString(0), arr.getString(1))
            }
            if (o.has("gt")) {
                val arr = o.getJSONArray("gt")
                return Gt(arr.getString(0), arr.getString(1))
            }
            if (o.has("gte")) {
                val arr = o.getJSONArray("gte")
                return Gte(arr.getString(0), arr.getString(1))
            }
            if (o.has("lt")) {
                val arr = o.getJSONArray("lt")
                return Lt(arr.getString(0), arr.getString(1))
            }
            if (o.has("lte")) {
                val arr = o.getJSONArray("lte")
                return Lte(arr.getString(0), arr.getString(1))
            }
            if (o.has("exists")) return Exists(o.getString("exists"))
            if (o.has("empty")) return Empty(o.getString("empty"))
            if (o.has("and")) {
                val arr = o.getJSONArray("and")
                return And(List(arr.length()) { i -> parse(arr.get(i)) ?: False })
            }
            if (o.has("or")) {
                val arr = o.getJSONArray("or")
                return Or(List(arr.length()) { i -> parse(arr.get(i)) ?: False })
            }
            if (o.has("not")) return Not(parse(o.get("not")) ?: False)
            if (o.has("true")) return True
            if (o.has("false")) return False
            return null
        }
    }
}

object WhenEvaluator {
    fun evaluate(expr: WhenExpr?, ctx: PluginContext): Boolean {
        if (expr == null) return true
        return when (expr) {
            is WhenExpr.True -> true
            is WhenExpr.False -> false
            is WhenExpr.And -> expr.items.all { evaluate(it, ctx) }
            is WhenExpr.Or -> expr.items.any { evaluate(it, ctx) }
            is WhenExpr.Not -> !evaluate(expr.item, ctx)

            is WhenExpr.Equals -> strEq(ctx, expr.a, expr.b)
            is WhenExpr.NotEquals -> !strEq(ctx, expr.a, expr.b)
            is WhenExpr.Contains -> strContains(ctx, expr.a, expr.b)
            is WhenExpr.StartsWith -> strStartsWith(ctx, expr.a, expr.b)
            is WhenExpr.EndsWith -> strEndsWith(ctx, expr.a, expr.b)
            is WhenExpr.Matches -> strMatches(ctx, expr.a, expr.b)

            is WhenExpr.Gt -> numCompare(ctx, expr.a, expr.b) { x, y -> x > y }
            is WhenExpr.Gte -> numCompare(ctx, expr.a, expr.b) { x, y -> x >= y }
            is WhenExpr.Lt -> numCompare(ctx, expr.a, expr.b) { x, y -> x < y }
            is WhenExpr.Lte -> numCompare(ctx, expr.a, expr.b) { x, y -> x <= y }

            is WhenExpr.Exists -> ctx.resolve(expr.path) != null
            is WhenExpr.Empty -> {
                val v = ctx.resolve(expr.path)
                v == null || v.toString().isBlank()
            }
        }
    }

    private fun resolveStr(ctx: PluginContext, raw: String): String =
        ctx.interpolate(raw) ?: raw

    private fun strEq(ctx: PluginContext, a: String, b: String): Boolean {
        val av = resolveStr(ctx, a)
        val bv = resolveStr(ctx, b)
        return av == bv
    }

    private fun strContains(ctx: PluginContext, a: String, b: String): Boolean {
        val av = resolveStr(ctx, a)
        val bv = resolveStr(ctx, b)
        return av.contains(bv)
    }

    private fun strStartsWith(ctx: PluginContext, a: String, b: String): Boolean {
        val av = resolveStr(ctx, a)
        val bv = resolveStr(ctx, b)
        return av.startsWith(bv)
    }

    private fun strEndsWith(ctx: PluginContext, a: String, b: String): Boolean {
        val av = resolveStr(ctx, a)
        val bv = resolveStr(ctx, b)
        return av.endsWith(bv)
    }

    private fun strMatches(ctx: PluginContext, a: String, b: String): Boolean {
        val av = resolveStr(ctx, a)
        val bv = resolveStr(ctx, b)
        return runCatching { Regex(bv).containsMatchIn(av) }.getOrDefault(false)
    }

    private fun numCompare(
        ctx: PluginContext,
        a: String,
        b: String,
        op: (Double, Double) -> Boolean
    ): Boolean {
        val av = resolveStr(ctx, a).toDoubleOrNull() ?: 0.0
        val bv = resolveStr(ctx, b).toDoubleOrNull() ?: 0.0
        return op(av, bv)
    }
}