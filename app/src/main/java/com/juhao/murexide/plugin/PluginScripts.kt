package com.juhao.murexide.plugin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.juhao.murexide.network.NetworkClient
import com.seanproctor.lua.LuaConfig
import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaValue
import com.seanproctor.lua.StdLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object PluginScripts {
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "PluginLua").apply { isDaemon = true } }
    private val pages = ConcurrentHashMap.newKeySet<PluginScriptSession>()

    fun enqueue(context: Context, pluginId: String, block: () -> Unit) {
        executor.execute {
            try { block() } catch (error: Throwable) {
                PluginLogger.error(context, pluginId, "Lua: ${error.message}")
            }
        }
    }

    fun executeEvent(context: Context, plugin: Plugin, script: PluginEventScript, page: String?, event: String, extras: Map<String, Any?>, activity: Activity? = null) {
        val session = PluginScriptSession(context, plugin.manifest.id, page, null, event, extras, activity, plugin)
        pages += session
        try {
            session.execute(script.file)
            session.publishFloatingViews()
        } catch (error: Throwable) {
            PluginLogger.error(context, plugin.manifest.id, "${script.file.path}: ${error.message}")
        } finally { session.finishEvent() }
    }

    fun openPage(context: Context, pluginId: String, entry: PluginPage): PluginScriptSession {
        val session = PluginScriptSession(context.applicationContext, pluginId, PluginFunctionActivity::class.java.name, entry.localId, "page", emptyMap(), activityFor(context))
        pages += session
        enqueue(context.applicationContext, pluginId) {
            if (session.isClosed) return@enqueue
            try {
                check(PluginSession.findPlugin(pluginId) != null) { "插件已停用" }
                session.execute(requireNotNull(entry.script) { "缺少页面脚本" })
                session.publish()
            } catch (error: Throwable) {
                session.fail(error)
            }
        }
        return session
    }

    private fun activityFor(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return current as? Activity
    }

    fun refreshActivity(activity: Activity) {
        pages.toList().filter { it.belongsTo(activity) }.forEach { it.refresh() }
    }

    fun forget(session: PluginScriptSession) { pages -= session }
    fun invalidateStaleSessions() { pages.toList().filterNot { it.isCurrent }.forEach { it.close() } }
}

data class PluginRenderedPage(val widget: PluginWidget = PluginWidget("column"), val env: Map<String, Any?> = emptyMap(), val error: String? = null)

class PluginScriptSession internal constructor(
    private val context: Context,
    private val pluginId: String,
    private val page: String?,
    private val pageId: String?,
    private val event: String,
    private val extras: Map<String, Any?>,
    activity: Activity? = null,
    private val ownerPlugin: Plugin? = PluginSession.findPlugin(pluginId)
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    val isClosed: Boolean get() = closed.get()
    internal val isCurrent: Boolean get() = !isClosed && ownerPlugin != null && PluginSession.findPlugin(pluginId) === ownerPlugin
    private var lua: LuaState? = null
    private var root = PluginWidget("column")
    private val ownerActivity = activity?.let { WeakReference(it) }
    private data class FloatingView(val source: LuaValue, var widget: PluginWidget, val content: MutableStateFlow<PluginRenderedPage>)
    private val floatingViews = linkedMapOf<String, FloatingView>()
    private data class TemplateResolution(
        val values: LinkedHashMap<String, Any?> = linkedMapOf(),
        val visiting: MutableSet<String> = linkedSetOf()
    )
    private val templates = linkedMapOf<String, Pair<String, List<LuaValue.Function>>>()
    private val templatePattern = Regex("\u0002binding:\\d+")
    private var templateIndex = 0L
    private var activeTemplateResolution: TemplateResolution? = null
    private var evaluatingBindings = false
    private var buildingFloatingContent = false
    private var retainedDialogs = 0
    private var eventFinished = false
    private val refreshQueued = AtomicBoolean(false)
    private val _content = MutableStateFlow(PluginRenderedPage())
    val content = _content.asStateFlow()
    private var bindingValues: Map<String, Any?> = emptyMap()

    internal fun execute(file: File) {
        if (!isCurrent) { close(); return }
        val state = state()
        state.eval(LuaScriptSyntax.compile(file.readText()), "@${file.name}")
    }

    private fun state(): LuaState {
        lua?.let { return it }
        val state = LuaState(LuaConfig(stdlibs = setOf(StdLib.BASE, StdLib.TABLE, StdLib.STRING, StdLib.MATH, StdLib.COROUTINE, StdLib.UTF8)))
        lua = state
        register(state)
        updateGlobals(state)
        state.eval("MaterialTheme = { colorScheme = setmetatable({}, { __index = function(_, key) return 'MaterialTheme.colorScheme.' .. key end }) }")
        return state
    }

    private fun env(): LinkedHashMap<String, Any?> {
        val result = PluginContext(context, pluginId, page, event, extras).toEnv()
        result["page"] = page
        result["currentPage"] = page
        result["pageId"] = pageId
        result["page.id"] = page
        if (eventFinished && floatingViews.isNotEmpty()) {
            result["ws.connected"] = PluginSystem.isWsConnected()
            result["network.online"] = PluginSystem.isNetworkOnline(context)
        }
        result["__bindings"] = bindingValues
        return result
    }

    private fun updateGlobals(state: LuaState, values: Map<String, Any?> = env()) {
        val nested = linkedMapOf<String, Any?>()
        values.filterKeys { !it.startsWith("__") && !it.startsWith("var.") && !it.startsWith("extra.") }.forEach { (key, value) ->
            val parts = key.split('.', limit = 2)
            if (parts.size == 2) {
                @Suppress("UNCHECKED_CAST")
                val group = nested.getOrPut(parts[0]) { linkedMapOf<String, Any?>() } as? MutableMap<String, Any?>
                group?.set(parts[1], value)
            } else if (value != null) nested[key] = value
        }
        nested["extra"] = extras
        nested["var"] = PluginSession.varsFor(pluginId)
        nested["page"] = mapOf("id" to page, "name" to page, "pluginPageId" to pageId)
        val contextTable = toLua(state, nested)
        state.setGlobal("context", contextTable)
        nested.forEach { (key, value) -> if (key != "page" && key != "type") state.setGlobal(key, toLua(state, value)) }
        state.setGlobal("page", toLua(state, page))
        state.setGlobal("currentPage", toLua(state, page))
        state.setGlobal("pageId", toLua(state, pageId))
    }

    private fun register(state: LuaState) {
        state.register("__template") { args ->
            check(templates.size < 8192) { "模板数量超过 8192 个，请复用已有模板" }
            val source = (args.firstOrNull() as? LuaValue.Str)?.value.orEmpty()
            val callbacks = args.drop(1).map { it as? LuaValue.Function ?: error("模板表达式无效") }
            val key = "\u0002binding:${++templateIndex}"
            templates[key] = source to callbacks
            if (buildingFloatingContent) {
                try {
                    listOf(LuaValue.Str(resolveString(LuaValue.Str(key))))
                } finally {
                    templates.remove(key)
                    activeTemplateResolution?.values?.remove(key)
                }
            } else {
                listOf(LuaValue.Str(key))
            }
        }
        fun options(args: List<LuaValue>): LuaValue.Table? = args.firstOrNull() as? LuaValue.Table
        fun value(args: List<LuaValue>, name: String, position: Int = 0): LuaValue = options(args)?.get(LuaValue.Str(name)) ?: args.getOrNull(position) ?: LuaValue.Nil
        fun text(args: List<LuaValue>, name: String, default: String = "", position: Int = 0): String {
            val raw = value(args, name, position)
            return if (raw == LuaValue.Nil) default else resolveString(raw)
        }
        fun mutate() { check(!evaluatingBindings) { "模板表达式只能读取数据" } }
        PluginMessageFunctions.register(state, context, pluginId, beforeCall = {
            mutate()
            check(isCurrent) { "插件已停用或重载" }
        }, resolve = ::resolveString)
        fun action(name: String, create: (List<LuaValue>) -> PluginAction) {
            state.register(name) { args ->
                mutate()
                val success = PluginExecutor.execute(context, pluginId, listOf(create(args)), env())
                listOf(LuaValue.Bool(success))
            }
        }
        action("Toast") { PluginAction.Toast(text(it, "content"), text(it, "type", "short", 1)) }
        action("Copy") { PluginAction.Copy(text(it, "content")) }
        action("Share") { PluginAction.Share(text(it, "content")) }
        action("OpenUrl") { PluginAction.OpenUrl(text(it, "url").ifBlank { text(it, "content") }) }
        action("Notify") { PluginAction.Notify(text(it, "title"), text(it, "content", position = 1)) }
        action("Vibrate") { PluginAction.Vibrate(text(it, "duration", "300").toLongOrNull() ?: 300L) }
        action("Log") { PluginAction.Log(text(it, "level", "info", 1), text(it, "content").ifBlank { text(it, "message") }) }
        action("RestartApp") { PluginAction.RestartApp() }
        action("OpenChat") { PluginAction.OpenChat(text(it, "chatId").ifBlank { text(it, "id") }, text(it, "chatType", "2", 1).toIntOrNull() ?: 2, text(it, "name", position = 2), text(it, "avatar", position = 3)) }
        action("StartActivity") {
            @Suppress("UNCHECKED_CAST")
            val data = fromLua(value(it, "extras", 1)) as? Map<String, Any?> ?: emptyMap()
            PluginAction.StartActivity(text(it, "activity").ifBlank { text(it, "class") }, data)
        }
        state.register("Alert") { args ->
            mutate()
            val original = options(args)
            val table = original?.get(LuaValue.Str("dialog")) as? LuaValue.Table ?: original
            fun field(key: String, default: String): String = table?.get(LuaValue.Str(key))?.takeUnless { it == LuaValue.Nil }?.let(::resolveString) ?: default
            val callback = table?.get(LuaValue.Str("onConfirm")) as? LuaValue.Function
            val alert = PluginAction.Alert(field("title", "提示"), field("message", ""), field("confirmText", "确定"), field("cancelText", "取消"), emptyList())
            retainedDialogs++
            PluginSession.emitDialog(pluginId, alert, callback?.let { function -> { invoke(function) } }, {
                PluginScripts.enqueue(context, pluginId) {
                    retainedDialogs--
                    closeIfUnretained()
                }
            })
            emptyList()
        }
        state.register("readData") { args ->
            requirePermission(PluginPermissions.STORAGE)
            val key = text(args, "name").ifBlank { text(args, "key") }
            val stored = LuaPluginDataStorage.getInstance(context).readValue(pluginId, key)
            listOf(if (stored == null) value(args, "default", 1) else toLua(state, stored))
        }
        state.register("writeData") { args ->
            mutate()
            requirePermission(PluginPermissions.STORAGE)
            val key = text(args, "name").ifBlank { text(args, "key") }
            LuaPluginDataStorage.getInstance(context).writeValue(pluginId, key, resolvedValue(value(args, "value", 1)))
            PluginSession.bumpPage()
            listOf(LuaValue.Bool(true))
        }
        state.register("getValue") { args ->
            val key = text(args, "name").ifBlank { text(args, "key") }
            val stored = PluginSession.getVar(pluginId, key)
            listOf(if (stored == null) value(args, "default", 1) else toLua(state, stored))
        }
        state.register("setValue") { args ->
            mutate()
            val key = text(args, "name").ifBlank { text(args, "key") }
            PluginSession.setVar(pluginId, key, resolvedValue(value(args, "value", 1)))
            emptyList()
        }
        state.register("Http") { args ->
            mutate()
            requirePermission(PluginPermissions.NETWORK)
            val method = text(args, "method", "GET").uppercase()
            val body = if (method == "GET" || method == "HEAD") null else text(args, "body", position = 2).toRequestBody("application/json".toMediaType())
            val builder = Request.Builder().url(text(args, "url")).method(method, body)
            (value(args, "headers", 3) as? LuaValue.Table)?.toMap()?.forEach { (key, item) -> builder.addHeader(resolveString(key), resolveString(item)) }
            val result = NetworkClient.okHttpClient.newCall(builder.build()).execute().use { response ->
                mapOf("code" to response.code, "successful" to response.isSuccessful, "body" to response.body.string())
            }
            text(args, "saveTo", position = 4).takeIf { it.isNotBlank() }?.let { PluginSession.setVar(pluginId, it.removePrefix("var."), result["body"]) }
            listOf(toLua(state, result))
        }
        val widgets = mapOf("Column" to "column", "Row" to "row", "Text" to "text", "Button" to "button", "TextField" to "textfield", "Dropdown" to "dropdown", "Slider" to "slider", "Spacer" to "spacer", "Image" to "image", "Icon" to "icon", "IconButton" to "iconbutton", "Card" to "card", "SettingsCard" to "settingscard", "Scroll" to "scroll", "Divider" to "divider", "Switch" to "switch", "Checkbox" to "checkbox")
        widgets.forEach { (name, type) ->
            state.register(name) { args ->
                val table = state.eval("return {}").single() as LuaValue.Table
                options(args)?.toMap()?.forEach { (key, item) -> table[key] = item }
                if (args.firstOrNull() is LuaValue.Str) table[LuaValue.Str("content")] = args.first()
                table[LuaValue.Str("type")] = LuaValue.Str(type)
                listOf(table)
            }
        }
        state.register("enabled") { args -> listOf(value(args, "value")) }
        state.register("addFloatingView") { args ->
            mutate()
            val id = text(args, "id")
            require(id.isNotBlank()) { "浮窗 id 不能为空" }
            val target = floatingActivity()
            val source = value(args, "content", 1)
            require(source is LuaValue.Table || source is LuaValue.Function) { "浮窗 content 必须是 UI 组件、组件列表或返回组件的函数" }
            fun coordinate(name: String, index: Int): Float? {
                val raw = value(args, name, index)
                if (raw == LuaValue.Nil) return null
                val number = resolveString(raw).toFloatOrNull()
                require(number != null && number.isFinite() && kotlin.math.abs(number) <= 1_000_000f) {
                    "浮窗 $name 必须是有限的 dp 数值（绝对值不超过 1000000）"
                }
                return number
            }
            val gravity = text(args, "gravity", position = 2).ifBlank { text(args, "position", "top|end", 2) }
            val position = PluginFloatingPosition(parseFloatingGravity(gravity), coordinate("x", 3), coordinate("y", 4))
            val widget = withTemplateResolution { parseFloatingContent(source) }
            val rendered = MutableStateFlow(PluginRenderedPage(widget))
            floatingViews[id] = FloatingView(source, widget, rendered)
            PluginFloatingViews.add(target, pluginId, id, this, rendered, position)
            emptyList()
        }
        state.register("removeFloatingView") { args ->
            mutate()
            val id = text(args, "id")
            require(id.isNotBlank()) { "浮窗 id 不能为空" }
            ownerActivity?.get()?.let {
                floatingViews.remove(id)
                PluginFloatingViews.remove(it, pluginId, id)
            }
            emptyList()
        }
        state.register("setContent") { args ->
            mutate()
            check(pageId != null) { "setContent 仅适用于插件页面" }
            val candidate = options(args)
            val table = (candidate?.get(LuaValue.Str("content")) as? LuaValue.Table) ?: candidate
            root = parseWidget(requireNotNull(table) { "setContent 需要一个 UI 组件" })
            emptyList()
        }
    }

    internal fun belongsTo(activity: Activity): Boolean = isCurrent && ownerActivity?.get() === activity

    private fun parseFloatingContent(source: LuaValue): PluginWidget {
        val previous = buildingFloatingContent
        buildingFloatingContent = true
        return try {
            val result = if (source is LuaValue.Function) state().call(source).firstOrNull() else source
            val table = result as? LuaValue.Table ?: error("浮窗 content 必须返回 UI 组件或组件列表")
            if (table[LuaValue.Str("type")] != LuaValue.Nil) {
                parseWidget(table, resolveBindings = true)
            } else {
                val entries = table.toMap()
                val keys = entries.keys.mapNotNull { (it as? LuaValue.Integer)?.value }.sorted()
                require(keys.size == entries.size && keys.withIndex().all { (index, key) -> key == index.toLong() + 1 }) { "浮窗 content 必须是 UI 组件或连续组件列表" }
                val children = keys.map { key ->
                    parseWidget(entries.getValue(LuaValue.Integer(key)) as? LuaValue.Table ?: error("浮窗子项必须是 UI 组件"), resolveBindings = true)
                }
                val fillWidth = children.any {
                    when (it.width?.lowercase()) {
                        "match", "fill", "fillmaxwidth" -> true
                        else -> false
                    }
                }
                PluginWidget("column", width = if (fillWidth) "match" else null, children = children)
            }
        } finally {
            buildingFloatingContent = previous
        }
    }

    private fun floatingActivity(): Activity {
        val activity = ownerActivity?.get()
        check(event != "destroyed" && activity != null && !activity.isFinishing && !activity.isDestroyed) { "当前脚本没有可用的页面，无法操作浮窗" }
        return activity
    }

    internal fun releaseFloatingView(id: String, content: StateFlow<PluginRenderedPage>) {
        PluginScripts.enqueue(context, pluginId) {
            if (isClosed) return@enqueue
            if (floatingViews[id]?.content === content) floatingViews.remove(id)
            closeIfUnretained()
        }
    }

    private fun closeIfUnretained() {
        if (pageId == null && eventFinished && retainedDialogs == 0 && floatingViews.isEmpty()) close()
    }

    internal fun publishFloatingViews() {
        if (floatingViews.isNotEmpty()) publish()
    }

    private fun requirePermission(permission: String) {
        val permissions = PluginSession.findPlugin(pluginId)?.manifest?.permissions.orEmpty()
        check(permission in permissions || PluginPermissions.ALL in permissions) { "permission denied: $permission" }
    }

    private fun parseWidget(table: LuaValue.Table, depth: Int = 0, resolveBindings: Boolean = false): PluginWidget {
        require(depth < 48) { "UI 嵌套过深" }
        fun raw(name: String): LuaValue = table[LuaValue.Str(name)]
        fun string(name: String): String? = when (val item = raw(name)) {
            LuaValue.Nil -> null
            is LuaValue.Str -> if (resolveBindings) resolveString(item) else item.value
            is LuaValue.Bool -> item.value.toString()
            is LuaValue.Integer -> item.value.toString()
            is LuaValue.Number -> item.value.toString()
            else -> null
        }
        fun number(name: String): Float? = raw(name).takeUnless { it == LuaValue.Nil }?.let { resolveString(it).toFloatOrNull()?.takeIf(Float::isFinite) }
        fun ordered(name: String): List<LuaValue> = (raw(name) as? LuaValue.Table)?.toMap()?.entries?.sortedBy { (it.key as? LuaValue.Integer)?.value ?: Long.MAX_VALUE }?.map { it.value }.orEmpty()
        val callback = raw("onClick") as? LuaValue.Function
        return PluginWidget(
            type = string("type") ?: "text",
            content = string("content") ?: string("text") ?: string("src"),
            title = string("title"), subtitle = string("subtitle"), icon = string("icon"),
            color = string("color"), background = string("background"),
            width = string("width"), height = string("height"), size = number("size"),
            padding = number("padding"), paddingH = number("paddingH"), paddingV = number("paddingV"),
            align = string("align"), model = string("model"), placeholder = string("placeholder"),
            enabled = string("enabled") ?: "true",
            scrollable = string("scrollable") ?: "false",
            min = number("min"), max = number("max"), step = number("step"),
            options = ordered("options").map(::resolveString),
            children = ordered("children").map { parseWidget(it as? LuaValue.Table ?: error("子组件必须是 UI 组件"), depth + 1, resolveBindings) },
            onClickCallback = callback?.let { function -> { invoke(function) } }
        )
    }

    private fun invoke(function: LuaValue.Function) {
        PluginScripts.enqueue(context, pluginId) {
            if (!isCurrent) { close(); return@enqueue }
            try {
                val state = state()
                updateGlobals(state)
                state.call(function)
                if (pageId != null || floatingViews.isNotEmpty()) publish()
            } catch (error: Throwable) { fail(error) }
        }
    }

    private fun <T> withTemplateResolution(block: (TemplateResolution) -> T): T {
        activeTemplateResolution?.let { return block(it) }
        val resolution = TemplateResolution()
        val wasEvaluating = evaluatingBindings
        activeTemplateResolution = resolution
        evaluatingBindings = true
        return try {
            block(resolution)
        } finally {
            activeTemplateResolution = null
            evaluatingBindings = wasEvaluating
        }
    }

    private fun resolveTemplate(key: String, resolution: TemplateResolution, depth: Int): Any? {
        if (resolution.values.containsKey(key)) return resolution.values[key]
        val definition = templates[key] ?: return key
        require(depth < 48) { "模板嵌套过深" }
        check(resolution.visiting.add(key)) { "模板存在循环引用" }
        try {
            val (source, callbacks) = definition
            val matches = LuaScriptSyntax.placeholders(source)
            check(matches.size == callbacks.size) { "模板表达式数量不匹配" }
            val state = state()
            val values = callbacks.map { callback ->
                val raw = fromLua(state.call(callback).firstOrNull() ?: LuaValue.Nil)
                resolveTemplateValue(raw, resolution, depth + 1)
            }
            val result = if (matches.size == 1 && matches[0].start == 0 && matches[0].end == source.length) {
                values.firstOrNull()
            } else buildString {
                var offset = 0
                matches.forEachIndexed { index, match ->
                    append(source.substring(offset, match.start))
                    append(values[index]?.toString().orEmpty())
                    offset = match.end
                }
                append(source.substring(offset))
            }
            return resolveTemplateValue(result, resolution, depth + 1).also { resolution.values[key] = it }
        } catch (error: Throwable) {
            PluginLogger.warn(context, pluginId, "template: ${error.message}")
            resolution.values[key] = null
            return null
        } finally {
            resolution.visiting.remove(key)
        }
    }

    private fun resolveTemplateValue(value: Any?, resolution: TemplateResolution, depth: Int = 0): Any? {
        require(depth < 48) { "模板数据嵌套过深或存在循环引用" }
        return when (value) {
            is String -> if (templates.containsKey(value)) {
                resolveTemplate(value, resolution, depth + 1)
            } else {
                templatePattern.replace(value) { match ->
                    if (templates.containsKey(match.value)) {
                        resolveTemplate(match.value, resolution, depth + 1)?.toString().orEmpty()
                    } else match.value
                }
            }
            is Map<*, *> -> value.mapValues { (_, item) -> resolveTemplateValue(item, resolution, depth + 1) }
            is List<*> -> value.map { resolveTemplateValue(it, resolution, depth + 1) }
            else -> value
        }
    }

    private fun resolveTemplates(): Map<String, Any?> = withTemplateResolution { resolution ->
        fun visit(widget: PluginWidget, depth: Int) {
            require(depth < 48) { "UI 嵌套过深" }
            val strings = listOfNotNull(
                widget.type, widget.content, widget.title, widget.subtitle, widget.icon,
                widget.color, widget.background, widget.width, widget.height, widget.align,
                widget.model, widget.placeholder, widget.enabled, widget.scrollable
            ) + widget.options
            strings.forEach { text ->
                if (templatePattern.containsMatchIn(text)) {
                    resolution.values[text] = resolveTemplateValue(text, resolution)
                }
            }
            widget.children.forEach { visit(it, depth + 1) }
        }
        visit(root, 0)
        floatingViews.values.forEach { visit(it.widget, 0) }
        resolution.values.toMap().also { bindingValues = it }
    }

    private fun resolveString(value: LuaValue): String = resolvedValue(value)?.toString().orEmpty()

    private fun resolvedValue(value: LuaValue): Any? = withTemplateResolution { resolution ->
        resolveTemplateValue(fromLua(value), resolution)
    }

    internal fun publish() {
        if (!isCurrent) { close(); return }
        val environment = env()
        updateGlobals(state(), environment)
        withTemplateResolution {
            floatingViews.values.forEach { floating ->
                floating.widget = parseFloatingContent(floating.source)
            }
            resolveTemplates()
        }
        environment["__bindings"] = bindingValues
        _content.value = PluginRenderedPage(root, environment)
        floatingViews.values.forEach { floating ->
            floating.content.value = PluginRenderedPage(floating.widget, environment)
        }
    }

    fun refresh() {
        if (!isCurrent) { close(); return }
        if (!refreshQueued.compareAndSet(false, true)) return
        PluginScripts.enqueue(context, pluginId) {
            refreshQueued.set(false)
            if (!isCurrent) { close(); return@enqueue }
            try { publish() } catch (error: Throwable) { fail(error) }
        }
    }

    internal fun fail(error: Throwable) {
        PluginLogger.error(context, pluginId, "${pageId ?: event}: ${error.message}")
        val environment = env()
        val message = error.message ?: "脚本执行失败"
        _content.value = PluginRenderedPage(root, environment, message)
        floatingViews.values.forEach { floating ->
            floating.content.value = PluginRenderedPage(floating.widget, environment, message)
        }
    }

    internal fun finishEvent() {
        eventFinished = true
        closeIfUnretained()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        PluginScripts.forget(this)
        PluginFloatingViews.removeSession(this)
        PluginScripts.enqueue(context, pluginId) {
            floatingViews.clear()
            lua?.close()
            lua = null
            templates.clear()
        }
    }

    private fun toLua(state: LuaState, value: Any?, depth: Int = 0): LuaValue {
        require(depth < 48) { "数据嵌套过深" }
        return when (value) {
            null -> LuaValue.Nil
            is Boolean -> LuaValue.Bool(value)
            is String -> LuaValue.Str(value)
            is Byte, is Short, is Int, is Long -> LuaValue.Integer((value as Number).toLong())
            is Number -> LuaValue.Number(value.toDouble())
            is Map<*, *> -> (state.eval("return {}").single() as LuaValue.Table).also { table -> value.forEach { (key, item) -> table[LuaValue.Str(key.toString())] = toLua(state, item, depth + 1) } }
            is List<*> -> (state.eval("return {}").single() as LuaValue.Table).also { table -> value.forEachIndexed { index, item -> table[LuaValue.Integer(index.toLong() + 1)] = toLua(state, item, depth + 1) } }
            else -> LuaValue.Str(value.toString())
        }
    }

    private fun fromLua(value: LuaValue, depth: Int = 0): Any? {
        require(depth < 48) { "数据嵌套过深或存在循环引用" }
        return when (value) {
            LuaValue.Nil -> null
            is LuaValue.Str -> value.value
            is LuaValue.Bool -> value.value
            is LuaValue.Integer -> value.value
            is LuaValue.Number -> value.value.takeIf(Double::isFinite) ?: error("不支持非有限数值")
            is LuaValue.Table -> {
                val entries = value.toMap()
                val keys = entries.keys.mapNotNull { (it as? LuaValue.Integer)?.value }.sorted()
                if (keys.size == entries.size && keys.withIndex().all { (index, key) -> key == index.toLong() + 1 }) {
                    keys.map { fromLua(entries.getValue(LuaValue.Integer(it)), depth + 1) }
                } else {
                    require(entries.keys.all { it is LuaValue.Str }) { "数据表必须是连续列表或字符串键对象" }
                    entries.entries.associate { (key, item) -> (key as LuaValue.Str).value to fromLua(item, depth + 1) }
                }
            }
            is LuaValue.Function -> error("不能存储 Lua 函数")
        }
    }
}
