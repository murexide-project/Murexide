package com.juhao.murexide.plugin

import android.content.Context
import com.juhao.murexide.utils.getAppVersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object PluginManager {

    private val _manifests = MutableStateFlow<List<PluginManifest>>(emptyList())
    val manifests: StateFlow<List<PluginManifest>> = _manifests.asStateFlow()

    private val _functions = MutableStateFlow<List<PluginPage>>(emptyList())
    val functions: StateFlow<List<PluginPage>> = _functions.asStateFlow()

    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        reload(context)
    }

    fun reload(context: Context) {
        val plugins = PluginLoader.load(context)
        PluginSession.update(plugins.filter { it.manifest.enabled })
        _manifests.value = plugins.map { it.manifest }
        _functions.value = plugins.filter { it.manifest.enabled }.flatMap { it.pages }
    }

    fun install(context: Context, zipFile: File): Result<Unit> = runCatching {
        validateZip(zipFile)
        val id = readIdFromZip(zipFile)
        require(id.isNotBlank()) { "插件缺 id" }
        checkMinVersion(context, zipFile)
        val pluginDir = File(context.filesDir, "lua_plugins/$id")
        pluginDir.deleteRecursively()
        pluginDir.mkdirs()
        ZipFile(zipFile).use { zip ->
            var total = 0L
            var count = 0
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                total += entry.size
                count++
                require(total <= 32L * 1024 * 1024) { "超过 32MB" }
                require(count <= 2048) { "文件数过多" }
                val target = File(pluginDir, entry.name)
                require(target.canonicalPath.startsWith(pluginDir.canonicalPath)) { "非法路径" }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        reload(context)
        val loaded = PluginSession.findPlugin(id)
        require(loaded != null) { "插件加载失败，脚本缺失或格式错误" }
        require(
            loaded.rules.isNotEmpty() || loaded.global.isNotEmpty() ||
                loaded.pages.isNotEmpty() || loaded.decorates.isNotEmpty()
        ) { "插件加载失败：脚本未注册任何功能（检查 Lua 语法，详见插件日志）" }
        PluginLoader.executeInstalledScript(context, id, pluginDir)
    }

    private fun checkMinVersion(context: Context, zipFile: File) {
        val minVersion = readMinVersionFromZip(zipFile)
        if (minVersion <= 0) return
        val appVersionCode = context.getAppVersionInfo().versionCode
        require(appVersionCode >= minVersion.toLong()) {
            "需要应用版本 $minVersion，当前 $appVersionCode"
        }
    }

    private fun readMinVersionFromZip(zipFile: File): Int {
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry("manifest.json") ?: return 0
            return zip.getInputStream(entry).bufferedReader().use {
                JSONObject(it.readText()).optInt("minVersion", 0)
            }
        }
    }

    fun uninstall(context: Context, id: String) {
        File(context.filesDir, "lua_plugins/$id").deleteRecursively()
        reload(context)
    }

    fun pluginDirSize(context: Context, id: String): Long {
        val dir = File(context.filesDir, "lua_plugins/$id")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        runCatching {
            val f = File(context.filesDir, "lua_plugins/$id/manifest.json")
            if (!f.exists()) return
            val obj = JSONObject(f.readText())
            obj.put("enabled", enabled)
            f.writeText(obj.toString(2))
        }
        reload(context)
    }

    fun manifestById(id: String): PluginManifest? = _manifests.value.firstOrNull { it.id == id }

    fun pluginById(id: String): Plugin? = PluginSession.findPlugin(id)

    fun entryById(entryId: String): PluginPage? =
        _functions.value.firstOrNull { it.id == entryId }

    fun manifestForEntry(entryId: String): String? {
        for (p in PluginSession.allPlugins()) {
            if (p.pages.any { it.id == entryId }) return p.manifest.id
        }
        return null
    }

    fun rulesForPage(pageId: String, event: String): List<Pair<PluginManifest, PluginRule>> {
        val out = mutableListOf<Pair<PluginManifest, PluginRule>>()
        for ((pid, rule) in PluginSession.rulesFor(pageId, event)) {
            val m = manifestById(pid) ?: continue
            out += m to rule
        }
        return out
    }

    fun scanZipForReplace(zipFile: File): Boolean = runCatching {
        val id = readIdFromZip(zipFile)
        id.isNotBlank() && _manifests.value.any { it.id == id }
    }.getOrDefault(false)

    private fun validateZip(zipFile: File) {
        ZipFile(zipFile).use { zip ->
            require(zip.getEntry("manifest.json") != null) { "缺少 manifest.json" }
        }
    }

    private fun readIdFromZip(zipFile: File): String {
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry("manifest.json") ?: return ""
            return zip.getInputStream(entry).bufferedReader().use {
                JSONObject(it.readText()).optString("id")
            }
        }
    }
}