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
        PluginDispatcher.dispatchAppEvent(context, "onLaunched")
    }

    fun reload(context: Context) {
        reload(context, changedPluginId = null)
    }

    private fun reload(context: Context, changedPluginId: String?, restoreActivity: Boolean = true) {
        val previous = PluginSession.allPlugins().associateBy { it.manifest.id }
        val plugins = PluginLoader.load(context).map { plugin ->
            if (changedPluginId != null && plugin.manifest.id != changedPluginId && plugin.manifest.enabled) {
                previous[plugin.manifest.id] ?: plugin
            } else plugin
        }
        val enabled = plugins.filter { it.manifest.enabled }
        PluginSession.update(enabled)
        PluginScripts.invalidateStaleSessions()
        _manifests.value = plugins.map { it.manifest }
        _functions.value = enabled.flatMap { it.pages }
        if (restoreActivity) {
            val changed = enabled.filter { previous[it.manifest.id] !== it }.map { it.manifest.id }.toSet()
            PluginDispatcher.refreshActivityPlugins(changed)
        }
    }

    fun install(context: Context, zipFile: File): Result<Unit> = runCatching {
        val manifest = readZipManifest(zipFile)
        val id = manifest.optString("id")
        require(id.isNotBlank()) { "插件缺 id" }
        require(id.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*"))) { "插件 id 格式无效" }
        val minVersion = manifest.optInt("minVersion", 0)
        val appVersionCode = context.getAppVersionInfo().versionCode
        require(minVersion <= 0 || appVersionCode >= minVersion.toLong()) {
            "需要应用版本 $minVersion，当前 $appVersionCode"
        }
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
                require(target.canonicalPath.startsWith(pluginDir.canonicalPath + File.separator)) { "非法路径" }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        reload(context, id, restoreActivity = false)
        require(_manifests.value.any { it.id == id }) { "插件加载失败，manifest.json 格式错误" }
        PluginLoader.executeInstalledScript(context, id, pluginDir)
        PluginDispatcher.refreshActivityPlugins(setOf(id))
    }

    fun uninstall(context: Context, id: String) {
        PluginSession.clearPlugin(id)
        File(context.filesDir, "lua_plugins/$id").deleteRecursively()
        reload(context, id)
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
        reload(context, id)
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

    fun scanZipForReplace(zipFile: File): Boolean = runCatching {
        val id = readZipManifest(zipFile).optString("id")
        id.isNotBlank() && _manifests.value.any { it.id == id }
    }.getOrDefault(false)

    private fun readZipManifest(zipFile: File): JSONObject = ZipFile(zipFile).use { zip ->
        val entry = requireNotNull(zip.getEntry("manifest.json")) { "缺少 manifest.json" }
        zip.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()) }
    }
}