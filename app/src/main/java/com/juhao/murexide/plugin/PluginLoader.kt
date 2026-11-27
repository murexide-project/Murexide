package com.juhao.murexide.plugin

import android.content.Context
import com.seanproctor.lua.LuaConfig
import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaValue
import com.seanproctor.lua.StdLib
import org.json.JSONObject
import java.io.File

object PluginLoader {

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
                require(manifestId.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*")) && manifestId == pluginDir.name) { "非法插件 id" }
                val permissions = parsePermissions(meta, context, manifestId)
                val manifest = PluginManifest(
                    id = manifestId,
                    name = meta.optString("name", manifestId),
                    author = meta.optString("author").ifBlank { null },
                    description = meta.optString("description").ifBlank { null },
                    version = meta.optInt("version", 1),
                    minVersion = meta.optInt("minVersion", 0),
                    enabled = meta.optBoolean("enabled", true),
                    permissions = permissions
                )
                val plugin = parsePlugin(context, manifest, pluginDir)
                out += plugin
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
                PluginMessageFunctions.register(lua, context, pluginId, resolve = { value ->
                    when (value) {
                        is LuaValue.Str -> PluginBindings.interpolate(value.value, env, pluginId)
                        is LuaValue.Integer -> value.value.toString()
                        else -> error("消息参数必须是字符串或整数")
                    }
                })
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

    private fun parsePlugin(context: Context, manifest: PluginManifest, pluginDir: File): Plugin {
        val events = File(pluginDir, "onEvent").listFiles().orEmpty()
            .filter { it.isDirectory && (it.name == "regular" || it.name.matches(Regex("[A-Za-z_][A-Za-z0-9_.$]*"))) }
            .sortedBy { it.name }
            .flatMap { scope ->
                scope.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("on[A-Za-z][A-Za-z0-9_]*\\.lua")) }
                    .sortedBy { it.name }.map { PluginEventScript(scope.name, it.nameWithoutExtension, it) }
            }.toMutableList()
        File(pluginDir, "onLaunched.lua").takeIf { it.isFile }?.let {
            events.add(0, PluginEventScript("regular", "onLaunched", it))
        }
        // An explicit pages directory owns registration, even when empty or invalid.
        // Only legacy packages without it fall back to pluginPage.
        val pagesDir = File(pluginDir, "pages").takeIf { it.exists() }
            ?: File(pluginDir, "pluginPage")
        // A present pages.json owns registration; never merge or revive page.json.
        val registration = File(pagesDir, "pages.json").takeIf { it.exists() }
            ?: File(pagesDir, "page.json")
        val pages = mutableListOf<PluginPage>()
        if (registration.isFile) {
            try {
                val source = org.json.JSONTokener(registration.readText()).nextValue()
                val array = when (source) {
                    is org.json.JSONArray -> source
                    is JSONObject -> source.optJSONArray("pages") ?: org.json.JSONArray().put(source)
                    else -> error("${registration.name} 必须是对象或数组")
                }
                val ids = mutableSetOf<String>()
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.getString("id")
                    require(id.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*")) && ids.add(id)) { "非法或重复页面 id: $id" }
                    val script = File(pagesDir, "$id.lua")
                    if (!script.isFile) {
                        PluginLogger.warn(context, manifest.id, "跳过缺少脚本的页面: ${pagesDir.name}/$id.lua")
                        continue
                    }
                    pages += PluginPage(
                        id = "${manifest.id}:$id",
                        icon = item.optString("icon", "Build"),
                        title = item.optString("title", id),
                        subtitle = item.optString("subtitle").ifBlank { null },
                        script = script,
                        localId = id
                    )
                }
            } catch (error: Throwable) {
                PluginLogger.error(context, manifest.id, "${pagesDir.name}/${registration.name}: ${error.message}")
            }
        }
        return Plugin(manifest, events, pages)
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

    private fun lit(t: LuaValue.Table?, key: String, def: String = ""): String {
        if (t == null) return def
        return (t[LuaValue.Str(key)] as? LuaValue.Str)?.value ?: def
    }
}
