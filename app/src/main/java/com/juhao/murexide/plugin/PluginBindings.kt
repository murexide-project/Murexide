package com.juhao.murexide.plugin

object PluginBindings {
    fun resolve(text: String, env: Map<String, Any?>, pluginId: String): Any? {
        val bindings = env["__bindings"] as? Map<*, *>
        if (bindings?.containsKey(text) == true) return bindings[text]
        val matches = runCatching { LuaScriptSyntax.placeholders(text) }.getOrElse { return text }
        if (matches.size == 1 && matches[0].start == 0 && matches[0].end == text.length) {
            return lookup(matches[0].expression.trim(), env, pluginId)
        }
        return interpolate(text, env, pluginId)
    }

    fun interpolate(text: String, env: Map<String, Any?>, pluginId: String): String {
        val bindings = env["__bindings"] as? Map<*, *>
        if (bindings?.containsKey(text) == true) return bindings[text]?.toString().orEmpty()
        val matches = runCatching { LuaScriptSyntax.placeholders(text) }.getOrElse { return text }
        if (matches.isEmpty()) return text
        return buildString {
            var offset = 0
            for (match in matches) {
                append(text.substring(offset, match.start))
                append(lookup(match.expression.trim(), env, pluginId)?.toString().orEmpty())
                offset = match.end
            }
            append(text.substring(offset))
        }
    }

    private fun lookup(path: String, env: Map<String, Any?>, pluginId: String): Any? {
        if (env.containsKey(path)) return env[path]
        if (path == "true") return true
        if (path == "false") return false
        path.toLongOrNull()?.let { return it }
        path.toDoubleOrNull()?.let { return it }
        val parts = path.split('.')
        var value: Any? = env[parts.first()] ?: PluginSession.getVar(pluginId, parts.first().removePrefix("var."))
        for (part in parts.drop(1)) value = (value as? Map<*, *>)?.get(part)
        return value ?: PluginSession.getVar(pluginId, path.removePrefix("var."))
    }
}
