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

    fun init(context: Context) = reload(context)

    private fun reload(context: Context) {
        val dir = File(context.filesDir, "plugins")
        if (!dir.exists()) return
        val manifests = mutableListOf<PluginManifest>()
        val allRules = mutableListOf<PluginRule>()
        dir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            val manifestFile = File(pluginDir, "manifest.json")
            if (!manifestFile.exists()) return@forEach
            runCatching {
                val rulesFile = File(pluginDir, "execute.json")
                val m = parseManifest(
                    manifestText = manifestFile.readText(),
                    rulesText = rulesFile.takeIf { it.exists() }?.readText(),
                    onInstalledText = null,
                    onUninstalledText = null
                )
                manifests += m
                if (m.enabled) {
                    allRules += m.rules
                }
            }
        }
        _manifests.value = manifests
        _rules.value = allRules
    }

    fun install(context: Context, zipFile: File): Result<Unit> = runCatching {
        val manifest = parseZip(zipFile)
        require(manifest.id.isNotBlank()) { "插件 id 不能为空" }

        val pluginDir = File(context.filesDir, "plugins/${manifest.id}")
        pluginDir.deleteRecursively()
        pluginDir.mkdirs()

        File(pluginDir, "manifest.json").writeText(serializeManifestMeta(manifest, enabled = true))
        if (manifest.rules.isNotEmpty()) {
            File(pluginDir, "execute.json").writeText(serializeRules(manifest.rules))
        }
        if (manifest.onInstalled.isNotEmpty()) {
            File(pluginDir, "onInstalled.json").writeText(serializeRules(manifest.onInstalled))
        }
        if (manifest.onUninstalled.isNotEmpty()) {
            File(pluginDir, "onUninstalled.json").writeText(serializeRules(manifest.onUninstalled))
        }

        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                if (entry.name == "manifest.json" ||
                    entry.name == "execute.json" ||
                    entry.name == "onInstalled.json" ||
                    entry.name == "onUninstalled.json"
                ) return@forEach
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
        dispatchEvent(context, manifest.onInstalled)
    }

    fun uninstall(context: Context, id: String) {
        val pluginDir = File(context.filesDir, "plugins/$id")
        val onUninstalledFile = File(pluginDir, "onUninstalled.json")
        val actions = if (onUninstalledFile.exists()) {
            runCatching {
                parseRuleList(JSONObject(onUninstalledFile.readText()), forcedEvent = "uninstalled")
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        pluginDir.deleteRecursively()
        reload(context)
        dispatchEvent(context, actions)
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val pluginDir = File(context.filesDir, "plugins/$id")
        val manifestFile = File(pluginDir, "manifest.json")
        if (!manifestFile.exists()) return
        runCatching {
            val obj = JSONObject(manifestFile.readText())
            obj.put("enabled", enabled)
            manifestFile.writeText(obj.toString(2))
            reload(context)
        }
    }

    fun rulesFor(pageId: String, event: String = "created"): List<PluginRule> =
        _rules.value.filter {
            (it.page == pageId || it.page == "*") && it.event == event
        }

    private fun dispatchEvent(context: Context, rules: List<PluginRule>) {
        rules.forEach { rule ->
            when (rule.content.type) {
                "toast" -> {
                    val text = rule.content.value ?: rule.content.message ?: return@forEach
                    val duration = when (rule.content.duration) {
                        "long" -> android.widget.Toast.LENGTH_LONG
                        else -> android.widget.Toast.LENGTH_SHORT
                    }
                    android.widget.Toast.makeText(context, text, duration).show()
                }
                "action" -> {
                    rule.content.execute?.let { PluginActions.execute(context, it) }
                }
            }
        }
    }

    private fun parseZip(zipFile: File): PluginManifest = ZipFile(zipFile).use { zip ->
        val manifestEntry = zip.getEntry("manifest.json")
            ?: throw IllegalArgumentException("缺少 manifest.json")

        val manifestText = zip.getInputStream(manifestEntry).bufferedReader().readText()

        val rulesText = zip.getEntry("execute.json")?.let {
            zip.getInputStream(it).bufferedReader().readText()
        }
        val onInstalledText = zip.getEntry("onInstalled.json")?.let {
            zip.getInputStream(it).bufferedReader().readText()
        }
        val onUninstalledText = zip.getEntry("onUninstalled.json")?.let {
            zip.getInputStream(it).bufferedReader().readText()
        }

        parseManifest(
            manifestText = manifestText,
            rulesText = rulesText,
            onInstalledText = onInstalledText,
            onUninstalledText = onUninstalledText
        )
    }

    private fun parseManifest(
        manifestText: String,
        rulesText: String?,
        onInstalledText: String?,
        onUninstalledText: String?
    ): PluginManifest {
        val meta = JSONObject(manifestText)
        val rules = rulesText?.let { parseRuleList(JSONObject(it)) } ?: emptyList()
        val onInstalled = onInstalledText?.let {
            parseRuleList(JSONObject(it), forcedEvent = "installed")
        } ?: emptyList()
        val onUninstalled = onUninstalledText?.let {
            parseRuleList(JSONObject(it), forcedEvent = "uninstalled")
        } ?: emptyList()

        return PluginManifest(
            id = meta.getString("id"),
            name = meta.optString("name", meta.getString("id")),
            version = meta.optInt("version", 1),
            enabled = meta.optBoolean("enabled", true),
            description = meta.optString("description").ifBlank { null },
            rules = rules,
            onInstalled = onInstalled,
            onUninstalled = onUninstalled
        )
    }

    private fun parseRuleList(obj: JSONObject, forcedEvent: String? = null): List<PluginRule> {
        val arr = obj.optJSONArray("rules") ?: return emptyList()
        return List(arr.length()) { i ->
            val r = arr.getJSONObject(i)
            PluginRule(
                page = r.optString("page", "*"),
                event = forcedEvent ?: r.optString("event", "created"),
                action = r.optString("action", "decorate"),
                position = r.optString("position", "top"),
                content = parseContent(r.getJSONObject("content"))
            )
        }
    }

    private fun parseContent(obj: JSONObject): PluginContent {
        val childrenArr = obj.optJSONArray("children")
        val onClickObj = obj.optJSONObject("onClick")
        val executeObj = obj.optJSONObject("execute")
        return PluginContent(
            type = obj.getString("type"),
            value = obj.optString("value").ifBlank { null },
            color = obj.optString("color").ifBlank { null },
            title = obj.optString("title").ifBlank { null },
            message = obj.optString("message").ifBlank { null },
            confirmText = obj.optString("confirmText").ifBlank { null },
            duration = obj.optString("duration").ifBlank { null },
            onClick = onClickObj?.let {
                PluginAction(
                    action = it.getString("action"),
                    target = it.optString("target").ifBlank { null },
                    text = it.optString("text").ifBlank { null }
                )
            },
            execute = executeObj?.let {
                PluginAction(
                    action = it.getString("action"),
                    target = it.optString("target").ifBlank { null },
                    text = it.optString("text").ifBlank { null }
                )
            },
            children = childrenArr?.let { arr ->
                List(arr.length()) { i -> parseContent(arr.getJSONObject(i)) }
            }
        )
    }

    private fun serializeManifestMeta(m: PluginManifest, enabled: Boolean): String =
        JSONObject().apply {
            put("id", m.id)
            put("name", m.name)
            put("version", m.version)
            put("enabled", enabled)
            m.description?.let { put("description", it) }
        }.toString(2)

    private fun serializeRules(rules: List<PluginRule>): String {
        fun contentToJson(c: PluginContent): JSONObject = JSONObject().apply {
            put("type", c.type)
            c.value?.let { put("value", it) }
            c.color?.let { put("color", it) }
            c.title?.let { put("title", it) }
            c.message?.let { put("message", it) }
            c.confirmText?.let { put("confirmText", it) }
            c.duration?.let { put("duration", it) }
            c.onClick?.let { a ->
                put("onClick", JSONObject().apply {
                    put("action", a.action)
                    a.target?.let { put("target", it) }
                    a.text?.let { put("text", it) }
                })
            }
            c.execute?.let { a ->
                put("execute", JSONObject().apply {
                    put("action", a.action)
                    a.target?.let { put("target", it) }
                    a.text?.let { put("text", it) }
                })
            }
            c.children?.let { list ->
                put("children", JSONArray().apply {
                    list.forEach { put(contentToJson(it)) }
                })
            }
        }
        return JSONObject().apply {
            put("rules", JSONArray().apply {
                rules.forEach { rule ->
                    put(JSONObject().apply {
                        put("page", rule.page)
                        put("event", rule.event)
                        put("action", rule.action)
                        put("position", rule.position)
                        put("content", contentToJson(rule.content))
                    })
                }
            })
        }.toString(2)
    }

    fun scanZipForReplace(zipFile: File): Boolean = runCatching {
        ZipFile(zipFile).use { zip ->
            val rulesEntry = zip.getEntry("execute.json") ?: return@runCatching false
            val rulesText = zip.getInputStream(rulesEntry).bufferedReader().readText()
            val arr = JSONObject(rulesText).getJSONArray("rules")
            (0 until arr.length()).any { i ->
                arr.getJSONObject(i).optString("action", "decorate") == "replace"
            }
        }
    }.getOrDefault(false)
}