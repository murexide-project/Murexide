package com.juhao.murexide.plugin

import android.content.Context
import com.seanproctor.lua.LuaConfig
import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaValue
import com.seanproctor.lua.StdLib
import org.json.JSONObject
import java.io.File

object PluginLoader {

    private val SCRIPT_NAMES = listOf(
        "OnActivityEvent.lua", "onEvent.lua", "Global.lua", "PageRegister.lua"
    )

    fun load(context: Context): List<Plugin> {
        val dir = File(context.filesDir, "lua_plugins")
        val out = mutableListOf<Plugin>()
        if (!dir.exists()) return out
        dir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            val manifestFile = File(pluginDir, "manifest.json")
            if (!manifestFile.exists()) return@forEach
            try {
                val meta = JSONObject(manifestFile.readText())
                val manifestId = meta.getString("id")
                val permissions = parsePermissions(meta, context, manifestId)
                val manifest = PluginManifest(
                    id = manifestId,
                    name = meta.optString("name", manifestId),
                    author = meta.optString("author").ifBlank { null },
                    version = meta.optInt("version", 1),
                    minVersion = meta.optInt("minVersion", 0),
                    enabled = meta.optBoolean("enabled", true),
                    permissions = permissions
                )
                val plugin = parsePlugin(context, manifest, pluginDir)
                if (plugin != null) out += plugin
            } catch (t: Throwable) {
                PluginLogger.error(context, pluginDir.name, "parse failed: ${t.message}")
            }
        }
        return out
    }

    private fun parsePermissions(meta: JSONObject, context: Context, pluginId: String): Set<String> {
        val arr = meta.optJSONArray("permissions") ?: return emptySet()
        val all = (0 until arr.length()).mapNotNull { arr.optString(it).ifBlank { null } }.toSet()
        val unknown = all - PluginPermissions.KNOWN
        if (unknown.isNotEmpty()) {
            PluginLogger.warn(context, pluginId, "unknown permissions: $unknown")
        }
        return all.intersect(PluginPermissions.KNOWN)
    }

    fun executeInstalledScript(context: Context, pluginId: String, pluginDir: File) {
        val scriptFile = File(pluginDir, "onInstalled.lua")
        if (!scriptFile.exists()) return
        try {
            val env = PluginContext(context, pluginId).toEnv()
            LuaState(
                LuaConfig(
                    stdlibs = setOf(
                        StdLib.BASE, StdLib.TABLE, StdLib.STRING,
                        StdLib.MATH, StdLib.COROUTINE, StdLib.UTF8
                    )
                )
            ).use { lua ->
                val run = { tag: String, args: List<LuaValue> ->
                    parseAction(actionTable(lua, tag, args))
                        ?.let { PluginExecutor.execute(context, pluginId, listOf(it), env) }
                    emptyList<LuaValue>()
                }
                lua.register("Toast") { args -> run("toast", args) }
                lua.register("Alert") { args -> run("alert", args) }
                lua.register("Notify") { args -> run("notify", args) }
                lua.register("Vibrate") { args -> run("vibrate", args) }
                lua.register("OpenChat") { args -> run("openChat", args) }
                lua.register("StartActivity") { args -> run("startActivity", args) }
                lua.register("RestartApp") { args -> run("restartApp", args) }
                lua.register("Log") { args -> run("log", args) }
                lua.register("Share") { args -> run("share", args) }
                lua.register("Copy") { args -> run("copy", args) }
                lua.register("OpenUrl") { args -> run("openUrl", args) }
                lua.register("writeData") { args -> run("writeData", args) }
                lua.register("readData") { args -> run("readData", args) }
                lua.register("Http") { args -> run("http", args) }
                lua.register("If") { args -> run("if", args) }
                lua.eval(scriptFile.readText())
            }
        } catch (t: Throwable) {
            PluginLogger.error(context, pluginId, "onInstalled.lua: ${t.message}")
        }
    }

    private fun parsePlugin(context: Context, manifest: PluginManifest, pluginDir: File): Plugin? {
        val builder = PluginBuilder(manifest.id)
        val scripts = SCRIPT_NAMES.mapNotNull { name ->
            val f = File(pluginDir, name)
            if (f.exists()) name to f.readText() else null
        }
        if (scripts.isEmpty()) return null

        LuaState(
            LuaConfig(
                stdlibs = setOf(
                    StdLib.BASE, StdLib.TABLE, StdLib.STRING,
                    StdLib.MATH, StdLib.COROUTINE, StdLib.UTF8
                )
            )
        ).use { lua ->
            registerHostFunctions(lua, builder)
            for ((name, src) in scripts) {
                try {
                    lua.eval(src)
                } catch (t: Throwable) {
                    PluginLogger.error(context, manifest.id, "$name: ${t.message}")
                }
            }
        }
        return builder.build(manifest)
    }

    private fun registerHostFunctions(lua: LuaState, b: PluginBuilder) {
        lua.register("onEvent") { args ->
            val opts = tableOf(lua, args)
            val event = lit(opts, "event")
            val pages = strList(opts[LuaValue.Str("pages")] as? LuaValue.Table)
            val code = opts[LuaValue.Str("onEvent")] as? LuaValue.Table
            val actions = collectActions(code)
            if (event.isNotBlank()) b.addRule(PluginRule(event, pages, actions))
            emptyList<LuaValue>()
        }

        lua.register("decorate") { args ->
            val opts = tableOf(lua, args)
            val page = lit(opts, "page", "*")
            val position = lit(opts, "position", "bottom")
            val whenTbl = opts[LuaValue.Str("when")] as? LuaValue.Table
            val whenField = lit(whenTbl, "field").ifBlank { null }
            val whenEquals = lit(whenTbl, "equals").ifBlank { null }
            val contentTbl = opts[LuaValue.Str("content")] as? LuaValue.Table ?: return@register emptyList()
            val widget = parseWidget(contentTbl)
            b.addDecorate(PluginAction.Decorate(page, position, whenField, whenEquals, widget))
            emptyList<LuaValue>()
        }

        lua.register("registerPage") { args ->
            val opts = tableOf(lua, args)
            val icon = lit(opts, "icon").ifBlank { "Build" }
            val title = lit(opts, "title")
            val pageId = lit(opts, "id").ifBlank { title }
            val subtitle = lit(opts, "subtitle").ifBlank { null }
            val page = opts[LuaValue.Str("page")] as? LuaValue.Table
            val widget = page?.let { parseWidget(it) } ?: PluginWidget("column")
            b.addPage(PluginPage(pageId, icon, title, subtitle, widget))
            emptyList<LuaValue>()
        }

        lua.register("Toast") { args -> listOf(actionTable(lua, "toast", args)) }
        lua.register("Alert") { args -> listOf(actionTable(lua, "alert", args)) }
        lua.register("Notify") { args -> listOf(actionTable(lua, "notify", args)) }
        lua.register("Vibrate") { args -> listOf(actionTable(lua, "vibrate", args)) }
        lua.register("OpenChat") { args -> listOf(actionTable(lua, "openChat", args)) }
        lua.register("StartActivity") { args -> listOf(actionTable(lua, "startActivity", args)) }
        lua.register("RestartApp") { args -> listOf(actionTable(lua, "restartApp", args)) }
        lua.register("Log") { args -> listOf(actionTable(lua, "log", args)) }
        lua.register("Share") { args -> listOf(actionTable(lua, "share", args)) }
        lua.register("Copy") { args -> listOf(actionTable(lua, "copy", args)) }
        lua.register("OpenUrl") { args -> listOf(actionTable(lua, "openUrl", args)) }
        lua.register("writeData") { args -> listOf(actionTable(lua, "writeData", args)) }
        lua.register("readData") { args -> listOf(actionTable(lua, "readData", args)) }
        lua.register("Http") { args -> listOf(actionTable(lua, "http", args)) }
        lua.register("If") { args -> listOf(actionTable(lua, "if", args)) }

        lua.register("Button") { args -> listOf(widgetTable(lua, "button", args)) }
        lua.register("Text") { args -> listOf(widgetTable(lua, "text", args)) }
        lua.register("Column") { args -> listOf(widgetTable(lua, "column", args)) }
        lua.register("Row") { args -> listOf(widgetTable(lua, "row", args)) }
        lua.register("Spacer") { args -> listOf(widgetTable(lua, "spacer", args)) }
        lua.register("Image") { args -> listOf(widgetTable(lua, "image", args)) }
    }

    private fun collectActions(code: LuaValue.Table?): List<PluginAction> {
        if (code == null) return emptyList()
        val out = mutableListOf<PluginAction>()
        for ((_, v) in code.toMap()) {
            (v as? LuaValue.Table)?.let { parseAction(it) }?.let { out += it }
        }
        return out
    }

    private fun parseAction(t: LuaValue.Table): PluginAction? {
        val tag = t[LuaValue.Str("\$a")] as? LuaValue.Str ?: return null
        return when (tag.value) {
            "toast" -> PluginAction.Toast(lit(t, "content"), lit(t, "type", "short"))
            "alert" -> {
                val d = t[LuaValue.Str("dialog")] as? LuaValue.Table
                PluginAction.Alert(
                    title = lit(d, "title"),
                    message = lit(d, "message"),
                    confirmText = lit(d, "confirmText", "确定"),
                    cancelText = lit(d, "cancelText", "取消"),
                    onConfirm = collectActions(d)
                )
            }
            "notify" -> PluginAction.Notify(lit(t, "title"), lit(t, "content"))
            "vibrate" -> PluginAction.Vibrate(
                duration = lit(t, "duration", "300").toLongOrNull() ?: 300L
            )
            "openChat" -> PluginAction.OpenChat(
                chatId = lit(t, "chatId").ifBlank { lit(t, "id") },
                chatType = lit(t, "chatType", "2").toIntOrNull() ?: 2,
                name = lit(t, "name"),
                avatar = lit(t, "avatar")
            )
            "startActivity" -> {
                val extraTbl = t[LuaValue.Str("extras")] as? LuaValue.Table
                val extras = mutableMapOf<String, Any?>()
                extraTbl?.toMap()?.forEach { (k, v) ->
                    val key = (k as? LuaValue.Str)?.value ?: return@forEach
                    val value: Any? = when (v) {
                        is LuaValue.Str -> v.value
                        is LuaValue.Integer -> v.value
                        is LuaValue.Number -> v.value
                        else -> return@forEach
                    }
                    extras[key] = value
                }
                PluginAction.StartActivity(
                    activity = lit(t, "activity").ifBlank { lit(t, "class") },
                    extras = extras
                )
            }
            "restartApp" -> PluginAction.RestartApp()
            "log" -> PluginAction.Log(
                level = lit(t, "level", "info").ifBlank { "info" },
                message = lit(t, "content").ifBlank { lit(t, "message") }
            )
            "share" -> PluginAction.Share(lit(t, "content"))
            "copy" -> PluginAction.Copy(lit(t, "content"))
            "openUrl" -> PluginAction.OpenUrl(lit(t, "url").ifBlank { lit(t, "content") })
            "writeData" -> PluginAction.WriteData(lit(t, "name"), lit(t, "value"))
            "readData" -> {
                val name = lit(t, "name")
                PluginAction.ReadData(name, lit(t, "model", "var.$name"))
            }
            "http" -> {
                val headers = mutableMapOf<String, String>()
                (t[LuaValue.Str("headers")] as? LuaValue.Table)?.toMap()?.forEach { (k, v) ->
                    val key = (k as? LuaValue.Str)?.value ?: return@forEach
                    val value = (v as? LuaValue.Str)?.value ?: return@forEach
                    headers[key] = value
                }
                PluginAction.Http(
                    method = lit(t, "method", "GET"),
                    url = lit(t, "url"),
                    body = lit(t, "body", ""),
                    headers = headers,
                    saveTo = lit(t, "saveTo")
                )
            }
            "if" -> PluginAction.If(
                field = lit(t, "field").ifBlank { lit(t, "key") },
                isEquals = lit(t, "equals").ifBlank { lit(t, "value") },
                op = lit(t, "op", "eq").ifBlank { "eq" },
                thenActions = collectActions(t[LuaValue.Str("then")] as? LuaValue.Table),
                elseActions = collectActions(t[LuaValue.Str("else")] as? LuaValue.Table)
            )
            else -> null
        }
    }

    private fun parseWidget(t: LuaValue.Table): PluginWidget {
        val type = lit(t, "type", "text")
        val content = lit(t, "content").ifBlank { lit(t, "text") }.ifBlank { lit(t, "src") }
        fun num(key: String): Float? = when (val v = t[LuaValue.Str(key)]) {
            is LuaValue.Number -> v.value.toFloat()
            is LuaValue.Integer -> v.value.toFloat()
            is LuaValue.Str -> v.value.toFloatOrNull()
            else -> null
        }
        return PluginWidget(
            type = type,
            content = content.ifBlank { null },
            icon = lit(t, "icon").ifBlank { null },
            size = num("size"),
            color = lit(t, "color").ifBlank { null },
            background = lit(t, "background").ifBlank { null },
            width = lit(t, "width").ifBlank { null },
            height = lit(t, "height").ifBlank { null },
            padding = num("padding"),
            paddingH = num("paddingH"),
            paddingV = num("paddingV"),
            align = lit(t, "align").ifBlank { null },
            children = (t[LuaValue.Str("children")] as? LuaValue.Table)?.let { tbl ->
                tbl.toMap().values.mapNotNull { (it as? LuaValue.Table)?.let(::parseWidget) }
            } ?: emptyList(),
            onClick = collectActions(t[LuaValue.Str("onClick")] as? LuaValue.Table)
        )
    }

    private fun newTable(lua: LuaState): LuaValue.Table =
        lua.eval("return {}").single() as LuaValue.Table

    private fun actionTable(lua: LuaState, tag: String, args: List<LuaValue>): LuaValue.Table {
        val t = newTable(lua)
        t[LuaValue.Str("\$a")] = LuaValue.Str(tag)
        val opts = args.firstOrNull() as? LuaValue.Table
        if (opts != null) {
            for ((k, v) in opts.toMap()) t[k] = v
        }
        return t
    }

    private fun widgetTable(lua: LuaState, tag: String, args: List<LuaValue>): LuaValue.Table {
        val t = newTable(lua)
        t[LuaValue.Str("type")] = LuaValue.Str(tag)
        val opts = args.firstOrNull()
        if (opts is LuaValue.Str) t[LuaValue.Str("content")] = opts
        else if (opts is LuaValue.Table) {
            for ((k, v) in opts.toMap()) t[k] = v
        }
        return t
    }

    private fun tableOf(lua: LuaState, args: List<LuaValue>): LuaValue.Table =
        args.firstOrNull() as? LuaValue.Table ?: newTable(lua)

    private fun lit(t: LuaValue.Table?, key: String, def: String = ""): String {
        if (t == null) return def
        return (t[LuaValue.Str(key)] as? LuaValue.Str)?.value ?: def
    }

    private fun strList(t: LuaValue.Table?): List<String> {
        if (t == null) return emptyList()
        return t.toMap().values.mapNotNull { (it as? LuaValue.Str)?.value }
    }
}

private class PluginBuilder(private val id: String) {
    private val pageRules = mutableListOf<PluginRule>()
    private val pages = mutableListOf<PluginPage>()
    private val global = mutableListOf<PluginRule>()
    private val decorates = mutableListOf<PluginAction.Decorate>()

    fun addRule(rule: PluginRule) {
        if (rule.pages.isEmpty()) global += rule else pageRules += rule
    }

    fun addPage(page: PluginPage) {
        pages += page
    }

    fun addDecorate(d: PluginAction.Decorate) {
        decorates += d
    }

    fun build(manifest: PluginManifest): Plugin =
        Plugin(manifest, pageRules, pages, global, decorates)
}