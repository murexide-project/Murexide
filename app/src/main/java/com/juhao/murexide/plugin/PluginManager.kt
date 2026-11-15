package com.juhao.murexide.plugin

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object PluginManager {

    private val _manifests = MutableStateFlow<List<PluginManifest>>(emptyList())
    val manifests: StateFlow<List<PluginManifest>> = _manifests.asStateFlow()

    private val _rules = MutableStateFlow<List<PluginRule>>(emptyList())
    val rules: StateFlow<List<PluginRule>> = _rules.asStateFlow()

    private val _functions = MutableStateFlow<List<PluginFunctionEntry>>(emptyList())
    val functions: StateFlow<List<PluginFunctionEntry>> = _functions.asStateFlow()

    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        reload(context)
        PluginEventBus.addListener { event -> handleEvent(event) }
    }

    fun reload(context: Context) {
        val dir = File(context.filesDir, "plugins")
        if (!dir.exists()) {
            _manifests.value = emptyList()
            _rules.value = emptyList()
            _functions.value = emptyList()
            return
        }
        val manifests = mutableListOf<PluginManifest>()
        val allRules = mutableListOf<PluginRule>()
        val allFunctions = mutableListOf<PluginFunctionEntry>()
        dir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            val manifestFile = File(pluginDir, "manifest.json")
            if (!manifestFile.exists()) return@forEach
            runCatching {
                val m = parseManifest(
                    manifestText = manifestFile.readText(),
                    rulesText = File(pluginDir, "execute.json").takeIf { it.exists() }?.readText(),
                    onInstalledText = File(pluginDir, "onInstalled.json").takeIf { it.exists() }?.readText(),
                    onUninstalledText = File(pluginDir, "onUninstalled.json").takeIf { it.exists() }?.readText(),
                    functionsText = File(pluginDir, "function.json").takeIf { it.exists() }?.readText()
                )
                manifests += m
                if (m.enabled) {
                    allRules += m.rules
                    allFunctions += m.functions
                }
            }.onFailure {
                PluginLogger.error(context, pluginDir.name, "manifest parse failed: ${it.message}")
            }
        }
        _manifests.value = manifests
        _rules.value = allRules
        _functions.value = allFunctions
    }

    fun install(context: Context, zipFile: File): Result<Unit> = runCatching {
        val manifest = parseZip(zipFile)
        require(manifest.id.isNotBlank()) { "插件 id 不能为空" }
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrDefault(0)
        if (manifest.minAppVersion != null && appVersion > 0 && appVersion < manifest.minAppVersion) {
            throw IllegalArgumentException("需要 App 版本 ≥ ${manifest.minAppVersion}（当前 $appVersion）")
        }
        if (!manifest.platform.isNullOrBlank() && manifest.platform != "android") {
            throw IllegalArgumentException("仅支持 Android 平台")
        }

        val pluginDir = File(context.filesDir, "plugins/${manifest.id}")
        pluginDir.deleteRecursively()
        pluginDir.mkdirs()

        File(pluginDir, "manifest.json").writeText(serializeManifestMeta(manifest))
        if (manifest.rules.isNotEmpty()) File(pluginDir, "execute.json").writeText(serializeRules(manifest.rules))
        if (manifest.onInstalled.isNotEmpty()) File(pluginDir, "onInstalled.json").writeText(serializeRules(manifest.onInstalled))
        if (manifest.onUninstalled.isNotEmpty()) File(pluginDir, "onUninstalled.json").writeText(serializeRules(manifest.onUninstalled))
        if (manifest.functions.isNotEmpty()) File(pluginDir, "function.json").writeText(serializeFunctions(manifest.functions))

        ZipFile(zipFile).use { zip ->
            var totalSize = 0L
            var fileCount = 0
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                if (entry.name in setOf(
                        "manifest.json", "execute.json",
                        "onInstalled.json", "onUninstalled.json", "function.json"
                    )) return@forEach
                totalSize += entry.size
                fileCount++
                require(totalSize <= 32L * 1024 * 1024) { "解压后超过 32MB" }
                require(fileCount <= 2048) { "文件数超过 2048" }
                val target = File(pluginDir, entry.name)
                require(target.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                    "非法路径: ${entry.name}"
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
        reload(context)
        dispatchEvent(context, manifest.id, manifest.onInstalled, "installed")
    }

    fun uninstall(context: Context, id: String) {
        val pluginDir = File(context.filesDir, "plugins/$id")
        val onUninstalledFile = File(pluginDir, "onUninstalled.json")
        val actions = if (onUninstalledFile.exists()) {
            runCatching { parseRuleList(JSONObject(onUninstalledFile.readText()), forcedEvent = "uninstalled") }
                .getOrDefault(emptyList())
        } else emptyList()
        dispatchEvent(context, id, actions, "uninstalled")
        pluginDir.deleteRecursively()
        reload(context)
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val manifestFile = File(context.filesDir, "plugins/$id/manifest.json")
        if (!manifestFile.exists()) return
        runCatching {
            val obj = JSONObject(manifestFile.readText())
            obj.put("enabled", enabled)
            manifestFile.writeText(obj.toString(2))
            reload(context)
        }
    }

    fun rulesForPage(pageId: String, event: String): List<Pair<PluginManifest, PluginRule>> {
        val list = mutableListOf<Pair<PluginManifest, PluginRule>>()
        _manifests.value.filter { it.enabled }.forEach { m ->
            m.rules.filter { (it.page == pageId || it.page == "*") && it.event == event }
                .forEach { list += m to it }
        }
        return list
    }

    fun rulesForAppEvent(event: String): List<Pair<PluginManifest, PluginRule>> {
        val list = mutableListOf<Pair<PluginManifest, PluginRule>>()
        _manifests.value.filter { it.enabled }.forEach { m ->
            m.rules.filter { it.event == event && (it.page == "*" || it.page.isBlank()) }
                .forEach { list += m to it }
        }
        return list
    }

    fun functionById(id: String): PluginFunctionEntry? = _functions.value.firstOrNull { it.id == id }

    fun manifestForFunction(id: String): PluginManifest? =
        _manifests.value.firstOrNull { m -> m.enabled && m.functions.any { it.id == id } }

    fun manifestById(id: String): PluginManifest? = _manifests.value.firstOrNull { it.id == id }

    private fun handleEvent(event: PluginEventBus.PluginEvent) {
        if (event.name == "ws.connected" || event.name == "network.online") {
            PluginActivityTracker.currentActivity()?.let { PluginOverlayInjector.removeAll(it) }
        }

        val pairs = rulesForAppEvent(event.name)
        pairs.forEach handler@ { (manifest, rule) ->
            runCatching {
                val ctx = PluginContext(
                    context = event.context,
                    pluginId = manifest.id,
                    pageId = null,
                    event = event.name,
                    extras = event.extras
                )
                if (!WhenEvaluator.evaluate(rule.whenExpr, ctx)) return@handler
                when (rule.content.type) {
                    "toast" -> {
                        val text = ctx.interpolate(rule.content.value?.toString() ?: rule.content.message)
                            ?: return@handler
                        android.widget.Toast.makeText(event.context, text, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    "action" -> PluginActions.executeList(event.context, ctx, rule.content.execute)
                    "text", "column", "row", "card", "button", "spacer", "divider",
                    "scroll", "image", "icon", "progress", "switch", "textField",
                    "slider", "list", "hscroll" -> {
                        val activity = PluginActivityTracker.currentActivity() ?: return@handler
                        PluginOverlayInjector.add(activity, manifest.id, rule)
                    }
                }
            }.onFailure {
                PluginLogger.error(event.context, manifest.id, "event ${event.name} failed: ${it.message}")
            }
        }
    }

    private fun dispatchEvent(context: Context, pluginId: String, rules: List<PluginRule>, event: String) {
        rules.forEach handler@ { rule ->
            runCatching {
                val ctx = PluginContext(context, pluginId, null, event)
                if (!WhenEvaluator.evaluate(rule.whenExpr, ctx)) return@handler
                when (rule.content.type) {
                    "toast" -> {
                        val text = ctx.interpolate(rule.content.value?.toString() ?: rule.content.message)
                            ?: return@handler
                        val duration = if (rule.content.duration == "long") android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                        android.widget.Toast.makeText(context, text, duration).show()
                    }
                    "action" -> PluginActions.executeList(context, ctx, rule.content.execute)
                }
            }.onFailure {
                PluginLogger.error(context, pluginId, "dispatch $event failed: ${it.message}")
            }
        }
    }

    private fun parseZip(zipFile: File): PluginManifest = ZipFile(zipFile).use { zip ->
        val manifestEntry = zip.getEntry("manifest.json") ?: throw IllegalArgumentException("缺少 manifest.json")
        val manifestText = zip.getInputStream(manifestEntry).bufferedReader().readText()
        val rulesText = zip.getEntry("execute.json")?.let { zip.getInputStream(it).bufferedReader().readText() }
        val onInstalledText = zip.getEntry("onInstalled.json")?.let { zip.getInputStream(it).bufferedReader().readText() }
        val onUninstalledText = zip.getEntry("onUninstalled.json")?.let { zip.getInputStream(it).bufferedReader().readText() }
        val functionsText = zip.getEntry("function.json")?.let { zip.getInputStream(it).bufferedReader().readText() }
        parseManifest(manifestText, rulesText, onInstalledText, onUninstalledText, functionsText)
    }

    private fun parseManifest(
        manifestText: String,
        rulesText: String?,
        onInstalledText: String?,
        onUninstalledText: String?,
        functionsText: String?
    ): PluginManifest {
        val meta = JSONObject(manifestText)
        val rules = rulesText?.let { parseRuleList(JSONObject(it)) } ?: emptyList()
        val onInstalled = onInstalledText?.let { parseRuleList(JSONObject(it), forcedEvent = "installed") } ?: emptyList()
        val onUninstalled = onUninstalledText?.let { parseRuleList(JSONObject(it), forcedEvent = "uninstalled") } ?: emptyList()
        val functions = functionsText?.let { parseFunctions(JSONObject(it)) } ?: emptyList()
        val permissions = meta.optJSONArray("permissions")?.let { arr ->
            List(arr.length()) { i -> arr.getString(i) }.filter { it in PluginPermissions.KNOWN }
        } ?: emptyList()

        return PluginManifest(
            id = meta.getString("id"),
            name = meta.optString("name", meta.getString("id")),
            version = meta.optInt("version", 1),
            enabled = meta.optBoolean("enabled", true),
            description = meta.optString("description").ifBlank { null },
            author = meta.optString("author").ifBlank { null },
            platform = meta.optString("platform").ifBlank { null },
            minAppVersion = if (meta.has("minAppVersion")) meta.optInt("minAppVersion") else null,
            changelog = meta.optString("changelog").ifBlank { null },
            icon = meta.optString("icon").ifBlank { null },
            permissions = permissions,
            rules = rules,
            onInstalled = onInstalled,
            onUninstalled = onUninstalled,
            functions = functions
        )
    }

    private fun parseRuleList(obj: JSONObject, forcedEvent: String? = null): List<PluginRule> {
        val arr = obj.optJSONArray("rules") ?: return emptyList()
        return List(arr.length()) { i ->
            val r = arr.getJSONObject(i)
            val pd = r.optJSONObject("positionData")?.let {
                PositionData(
                    anchor = it.optString("anchor", "bottomEnd"),
                    offsetX = it.optInt("offsetX", 0),
                    offsetY = it.optInt("offsetY", 0),
                    padding = it.optJSONObject("padding")?.let { p ->
                        PluginPadding(
                            top = p.optInt("top", 0),
                            bottom = p.optInt("bottom", 0),
                            start = p.optInt("start", 0),
                            end = p.optInt("end", 0),
                            horizontal = if (p.has("horizontal")) p.optInt("horizontal") else null,
                            vertical = if (p.has("vertical")) p.optInt("vertical") else null,
                            all = if (p.has("all")) p.optInt("all") else null
                        )
                    }
                )
            }
            PluginRule(
                page = r.optString("page", "*"),
                event = forcedEvent ?: r.optString("event", "created"),
                action = r.optString("action", "decorate"),
                position = r.optString("position", "top"),
                positionData = pd,
                whenExpr = WhenExpr.parse(r.opt("when")),
                content = parseContent(r.getJSONObject("content"))
            )
        }
    }

    private fun parseFunctions(obj: JSONObject): List<PluginFunctionEntry> {
        val arr = obj.optJSONArray("entries") ?: return emptyList()
        return List(arr.length()) { i ->
            val e = arr.getJSONObject(i)
            PluginFunctionEntry(
                id = e.getString("id"),
                title = e.optString("title").ifBlank { e.getString("id") },
                subtitle = e.optString("subtitle").ifBlank { null },
                icon = e.optString("icon").ifBlank { null },
                content = parseContent(e.getJSONObject("content"))
            )
        }
    }

    private fun parseContent(obj: JSONObject): PluginContent {
        return PluginContent(
            type = obj.getString("type"),
            value = obj.opt("value"),
            color = obj.optString("color").ifBlank { null },
            title = obj.optString("title").ifBlank { null },
            message = obj.optString("message").ifBlank { null },
            confirmText = obj.optString("confirmText").ifBlank { null },
            cancelText = obj.optString("cancelText").ifBlank { null },
            duration = obj.optString("duration").ifBlank { null },
            onClick = parseActions(obj.opt("onClick")),
            onLongClick = parseActions(obj.opt("onLongClick")),
            onChange = parseActions(obj.opt("onChange")),
            execute = parseActions(obj.opt("execute")),
            children = obj.optJSONArray("children")?.let { arr -> List(arr.length()) { parseContent(arr.getJSONObject(it)) } },
            items = obj.optJSONArray("items")?.let { arr -> List(arr.length()) { parseContent(arr.getJSONObject(it)) } },
            padding = obj.optJSONObject("padding")?.let { p ->
                PluginPadding(
                    top = p.optInt("top", 0), bottom = p.optInt("bottom", 0),
                    start = p.optInt("start", 0), end = p.optInt("end", 0),
                    horizontal = if (p.has("horizontal")) p.optInt("horizontal") else null,
                    vertical = if (p.has("vertical")) p.optInt("vertical") else null,
                    all = if (p.has("all")) p.optInt("all") else null
                )
            },
            margin = obj.optJSONObject("margin")?.let { p ->
                PluginPadding(
                    top = p.optInt("top", 0), bottom = p.optInt("bottom", 0),
                    start = p.optInt("start", 0), end = p.optInt("end", 0),
                    horizontal = if (p.has("horizontal")) p.optInt("horizontal") else null,
                    vertical = if (p.has("vertical")) p.optInt("vertical") else null,
                    all = if (p.has("all")) p.optInt("all") else null
                )
            },
            width = obj.optString("width").ifBlank { null },
            height = obj.optString("height").ifBlank { null },
            weight = if (obj.has("weight")) obj.optDouble("weight").toFloat() else null,
            align = obj.optString("align").ifBlank { null },
            background = obj.optString("background").ifBlank { null },
            radius = if (obj.has("radius")) obj.optDouble("radius").toFloat() else null,
            hint = obj.optString("hint").ifBlank { null },
            min = if (obj.has("min")) obj.optDouble("min").toFloat() else null,
            max = if (obj.has("max")) obj.optDouble("max").toFloat() else null,
            step = if (obj.has("step")) obj.optDouble("step").toFloat() else null,
            size = obj.optString("size").ifBlank { null },
            src = obj.optString("src").ifBlank { null },
            icon = obj.optString("icon").ifBlank { null },
            indeterminate = if (obj.has("indeterminate")) obj.optBoolean("indeterminate") else null,
            bind = obj.optString("bind").ifBlank { null },
            fontSize = if (obj.has("fontSize")) obj.optDouble("fontSize").toFloat() else null
        )
    }

    private fun parseActions(obj: Any?): List<PluginAction>? {
        return when (obj) {
            null -> null
            is JSONObject -> listOf(parseAction(obj))
            is JSONArray -> List(obj.length()) { parseAction(obj.getJSONObject(it)) }
            else -> null
        }
    }

    private fun parseAction(o: JSONObject): PluginAction {
        val extrasObj = o.optJSONObject("extras")
        val extras = extrasObj?.let { e ->
            e.keys().asSequence().associateWith { k -> e.get(k) }
        }
        val flagsArr = o.optJSONArray("flags")
        val flags = flagsArr?.let { arr -> List(arr.length()) { arr.getString(it) } }
        val headersObj = o.optJSONObject("headers")
        val headers = headersObj?.let { h ->
            h.keys().asSequence().associateWith { k -> h.getString(k) }
        }
        return PluginAction(
            action = o.getString("action"),
            target = o.optString("target").ifBlank { null },
            text = o.optString("text").ifBlank { null },
            extras = extras,
            flags = flags,
            method = o.optString("method").ifBlank { null },
            url = o.optString("url").ifBlank { null },
            headers = headers,
            body = o.optString("body").ifBlank { null },
            saveTo = o.optString("saveTo").ifBlank { null },
            duration = if (o.has("duration")) o.optLong("duration") else null,
            key = o.optString("key").ifBlank { null },
            value = if (o.has("value")) o.opt("value") else null,
            persist = if (o.has("persist")) o.optBoolean("persist") else null,
            condition = WhenExpr.parse(o.opt("condition")),
            then = parseActions(o.opt("then")),
            elseActions = parseActions(o.opt("else")),
            packageName = o.optString("packageName").ifBlank { null },
            scheme = o.optString("scheme").ifBlank { null },
            dialog = o.optJSONObject("dialog")?.let { parseContent(it) },
            notifyTitle = o.optString("notifyTitle").ifBlank { null },
            mimeType = o.optString("mimeType").ifBlank { null },
            chatId = o.optString("chatId").ifBlank { null },
            chatType = if (o.has("chatType")) o.optInt("chatType") else null,
            readKey = o.optString("readKey").ifBlank { null },
            title = o.optString("title").ifBlank { null },
            message = o.optString("message").ifBlank { null }
        )
    }

    private fun serializeManifestMeta(m: PluginManifest): String = JSONObject().apply {
        put("id", m.id)
        put("name", m.name)
        put("version", m.version)
        put("enabled", m.enabled)
        m.description?.let { put("description", it) }
        m.author?.let { put("author", it) }
        m.platform?.let { put("platform", it) }
        m.minAppVersion?.let { put("minAppVersion", it) }
        m.changelog?.let { put("changelog", it) }
        m.icon?.let { put("icon", it) }
        if (m.permissions.isNotEmpty()) put("permissions", JSONArray(m.permissions))
    }.toString(2)

    private fun contentToJson(c: PluginContent): JSONObject = JSONObject().apply {
        put("type", c.type)
        c.value?.let { put("value", it) }
        c.color?.let { put("color", it) }
        c.title?.let { put("title", it) }
        c.message?.let { put("message", it) }
        c.confirmText?.let { put("confirmText", it) }
        c.cancelText?.let { put("cancelText", it) }
        c.duration?.let { put("duration", it) }
        c.onClick?.let { put("onClick", actionsToJson(it)) }
        c.onLongClick?.let { put("onLongClick", actionsToJson(it)) }
        c.onChange?.let { put("onChange", actionsToJson(it)) }
        c.execute?.let { put("execute", actionsToJson(it)) }
        c.children?.let { list -> put("children", JSONArray().apply { list.forEach { put(contentToJson(it)) } }) }
        c.items?.let { list -> put("items", JSONArray().apply { list.forEach { put(contentToJson(it)) } }) }
        c.padding?.let { put("padding", paddingToJson(it)) }
        c.margin?.let { put("margin", paddingToJson(it)) }
        c.width?.let { put("width", it) }
        c.height?.let { put("height", it) }
        c.weight?.let { put("weight", it) }
        c.align?.let { put("align", it) }
        c.background?.let { put("background", it) }
        c.radius?.let { put("radius", it) }
        c.hint?.let { put("hint", it) }
        c.min?.let { put("min", it) }
        c.max?.let { put("max", it) }
        c.step?.let { put("step", it) }
        c.size?.let { put("size", it) }
        c.src?.let { put("src", it) }
        c.icon?.let { put("icon", it) }
        c.indeterminate?.let { put("indeterminate", it) }
        c.bind?.let { put("bind", it) }
        c.fontSize?.let { put("fontSize", it) }
    }

    private fun paddingToJson(p: PluginPadding): JSONObject = JSONObject().apply {
        put("top", p.top); put("bottom", p.bottom); put("start", p.start); put("end", p.end)
        p.horizontal?.let { put("horizontal", it) }
        p.vertical?.let { put("vertical", it) }
        p.all?.let { put("all", it) }
    }

    private fun actionsToJson(list: List<PluginAction>): JSONArray = JSONArray().apply {
        list.forEach { a ->
            put(JSONObject().apply {
                put("action", a.action)
                a.target?.let { put("target", it) }
                a.text?.let { put("text", it) }
            })
        }
    }

    private fun serializeRules(rules: List<PluginRule>): String = JSONObject().apply {
        put("rules", JSONArray().apply {
            rules.forEach { rule ->
                put(JSONObject().apply {
                    put("page", rule.page)
                    put("event", rule.event)
                    put("action", rule.action)
                    put("position", rule.position)
                    rule.positionData?.let { pd ->
                        put("positionData", JSONObject().apply {
                            put("anchor", pd.anchor)
                            put("offsetX", pd.offsetX)
                            put("offsetY", pd.offsetY)
                            pd.padding?.let { put("padding", paddingToJson(it)) }
                        })
                    }
                    rule.whenExpr?.let { put("when", whenExprToJson(it)) }
                    put("content", contentToJson(rule.content))
                })
            }
        })
    }.toString(2)
    
    private fun whenExprToJson(expr: WhenExpr): JSONObject = when (expr) {
        is WhenExpr.True -> JSONObject().put("true", true)
        is WhenExpr.False -> JSONObject().put("false", true)
        is WhenExpr.Equals -> JSONObject().put("equals", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.NotEquals -> JSONObject().put("notEquals", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.Contains -> JSONObject().put("contains", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.StartsWith -> JSONObject().put("startsWith", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.EndsWith -> JSONObject().put("endsWith", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.Matches -> JSONObject().put("matches", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.Gt -> JSONObject().put("gt", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.Gte -> JSONObject().put("gte", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.Lt -> JSONObject().put("lt", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.Lte -> JSONObject().put("lte", JSONArray().apply {
            put(expr.a); put(expr.b)
        })
        is WhenExpr.And -> JSONObject().put("and", JSONArray().apply {
            expr.items.forEach { put(whenExprToJson(it)) }
        })
        is WhenExpr.Or -> JSONObject().put("or", JSONArray().apply {
            expr.items.forEach { put(whenExprToJson(it)) }
        })
        is WhenExpr.Not -> JSONObject().put("not", whenExprToJson(expr.item))
        is WhenExpr.Exists -> JSONObject().put("exists", expr.path)
        is WhenExpr.Empty -> JSONObject().put("empty", expr.path)
    }

    private fun serializeFunctions(entries: List<PluginFunctionEntry>): String = JSONObject().apply {
        put("entries", JSONArray().apply {
            entries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("title", e.title)
                    e.subtitle?.let { put("subtitle", it) }
                    e.icon?.let { put("icon", it) }
                    put("content", contentToJson(e.content))
                })
            }
        })
    }.toString(2)

    fun scanZipForReplace(zipFile: File): Boolean = runCatching {
        ZipFile(zipFile).use { zip ->
            val rulesEntry = zip.getEntry("execute.json")
            if (rulesEntry != null) {
                val arr = JSONObject(zip.getInputStream(rulesEntry).bufferedReader().readText()).getJSONArray("rules")
                if ((0 until arr.length()).any { arr.getJSONObject(it).optString("action", "decorate") == "replace" }) return@runCatching true
            }
            val fnEntry = zip.getEntry("function.json")
            if (fnEntry != null) {
                val arr = JSONObject(zip.getInputStream(fnEntry).bufferedReader().readText()).optJSONArray("entries")
                if (arr != null && (0 until arr.length()).any { contentHasDanger(arr.getJSONObject(it).getJSONObject("content")) }) return@runCatching true
            }
            val inst = zip.getEntry("onInstalled.json")
            if (inst != null) {
                val arr = JSONObject(zip.getInputStream(inst).bufferedReader().readText()).optJSONArray("rules")
                if (arr != null && (0 until arr.length()).any {
                        val content = arr.getJSONObject(it).optJSONObject("content")
                        content?.optJSONObject("execute")?.optString("action") == "restartApp"
                    }) return@runCatching true
            }
            false
        }
    }.getOrDefault(false)

    private fun contentHasDanger(c: JSONObject): Boolean {
        val actions = c.optJSONObject("onClick") ?: c.optJSONObject("execute")
        if (actions?.optString("action") == "restartApp") return true
        val children = c.optJSONArray("children") ?: return false
        return (0 until children.length()).any { contentHasDanger(children.getJSONObject(it)) }
    }
}