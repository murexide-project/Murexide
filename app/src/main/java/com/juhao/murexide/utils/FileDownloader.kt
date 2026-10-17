package com.juhao.murexide.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val FILE_DOWNLOAD_REFERER = "http://myapp.jwznb.com"
private const val MAX_FILE_NAME_ATTEMPTS = 100

internal fun sanitizeDownloadFileName(fileName: String): String {
    val leafName = fileName
        .replace('\\', '/')
        .substringAfterLast('/')
        .trim()
    val sanitized = buildString(leafName.length) {
        leafName.forEach { character ->
            val isInvalid = character.code < 32 || character in "<>:\"/\\|?*"
            append(if (isInvalid) '_' else character)
        }
    }.trim().trimEnd('.', ' ')

    return sanitized.ifBlank { "download" }
}

internal fun downloadDisplayName(fileName: String, collisionIndex: Int): String {
    require(collisionIndex >= 0)
    if (collisionIndex == 0) return fileName

    val (base, extension) = splitFileName(fileName)
    return "$base($collisionIndex)$extension"
}

private fun splitFileName(fileName: String): Pair<String, String> {
    val dotIndex = fileName.lastIndexOf('.')
    return if (dotIndex > 0) {
        fileName.substring(0, dotIndex) to fileName.substring(dotIndex)
    } else {
        fileName to ""
    }
}

object FileDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadFileWithProgress(
        url: String,
        fileName: String,
        context: Context,
        onProgress: (Float) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        onCallCreated: ((okhttp3.Call) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val safeFileName = sanitizeDownloadFileName(fileName)
            val request = Request.Builder()
                .url(url)
                .addHeader("Referer", FILE_DOWNLOAD_REFERER)
                .build()
    
            val call = client.newCall(request)
            onCallCreated?.invoke(call)
            val isCancelled = { call.isCanceled() }
    
            val savedPath = call.execute().use { response ->
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onError("HTTP ${response.code}")
                    }
                    return@withContext
                }
    
                val contentLength = response.body.contentLength()
                response.body.byteStream().use { inputStream ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveToDownloadWithMediaStore(
                            context,
                            safeFileName,
                            inputStream,
                            contentLength,
                            onProgress,
                            isCancelled
                        )
                    } else {
                        saveToDownloadLegacy(
                            context,
                            safeFileName,
                            inputStream,
                            contentLength,
                            onProgress,
                            isCancelled
                        )
                    }
                }
            }
    
            withContext(Dispatchers.Main) {
                onComplete(savedPath)
                openFile(context, savedPath, safeFileName)
            }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                onError(e.message ?: "网络错误")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e.message ?: "下载失败")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloadWithMediaStore(
        context: Context,
        fileName: String,
        inputStream: java.io.InputStream,
        contentLength: Long,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): String {
        val resolver = context.contentResolver
        val uri = insertPendingDownload(resolver, fileName)
    
        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                copyWithProgress(inputStream, outputStream, contentLength, onProgress, isCancelled)
            } ?: throw IOException("无法写入文件")
    
            fixSuffixAfterExtension(resolver, uri, fileName)
            val publishedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, publishedValues, null, null)
            return uri.toString()
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertPendingDownload(
        resolver: android.content.ContentResolver,
        fileName: String
    ): Uri {
        var lastCollision: RuntimeException? = null

        repeat(MAX_FILE_NAME_ATTEMPTS) { collisionIndex ->
            val candidate = downloadDisplayName(fileName, collisionIndex)
            if (displayNameExists(resolver, candidate)) return@repeat

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, candidate)
                mimeTypeForName(fileName)?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
                put(MediaStore.MediaColumns.RELATIVE_PATH, murexideDownloadRelativePath())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            try {
                return resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: throw IOException("无法创建下载条目")
            } catch (error: RuntimeException) {
                if (!error.isMediaStorePathCollision()) throw error
                lastCollision = error
            }
        }

        throw IOException("无法创建不重名的下载文件", lastCollision)
    }

    private fun mimeTypeForName(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun fixSuffixAfterExtension(
        resolver: android.content.ContentResolver,
        uri: Uri,
        requestedName: String
    ) {
        val (_, ext) = splitFileName(requestedName)
        if (ext.isEmpty()) return

        val actual = queryDisplayName(resolver, uri) ?: return
        val pattern = Regex("^(.*)" + Regex.escape(ext) + "\\s*\\((\\d+)\\)$")
        val match = pattern.matchEntire(actual) ?: return
        val stem = match.groupValues[1]
        val index = match.groupValues[2]

        val corrected = getUniqueDisplayName(resolver, "$stem($index)$ext")
        if (corrected == actual) return
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, corrected)
            }
            resolver.update(uri, values, null, null)
        } catch (_: Exception) { }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryDisplayName(
        resolver: android.content.ContentResolver,
        uri: Uri
    ): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }

    private fun saveToDownloadLegacy(
        context: Context,
        fileName: String,
        inputStream: java.io.InputStream,
        contentLength: Long,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): String {
        if (requiresLegacyWritePermission(context)) {
            throw LegacyStoragePermissionRequiredException()
        }
    
        val downloadDir = legacyMurexideDownloadDirectory(context)
    
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            throw IOException("无法创建下载目录")
        }
    
        val file = createUniqueFile(downloadDir, fileName)
    
        try {
            FileOutputStream(file).use { outputStream ->
                copyWithProgress(inputStream, outputStream, contentLength, onProgress, isCancelled)
            }
            return file.absolutePath
        } catch (error: Throwable) {
            runCatching { file.delete() }
            throw error
        }
    }

    private fun copyWithProgress(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        contentLength: Long,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead = 0L
        var lastProgress = -1f
    
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            if (isCancelled()) {
                throw IOException("已取消下载")
            }
            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
    
            if (contentLength > 0) {
                val progress = (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                if (progress - lastProgress >= 0.01f) {
                    lastProgress = progress
                    onProgress(progress)
                }
            } else {
                onProgress(-1f)
            }
        }
    
        outputStream.flush()
        onProgress(1f)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getUniqueDisplayName(
        resolver: android.content.ContentResolver,
        fileName: String
    ): String {
        val (base, ext) = splitFileName(fileName)
        var candidate = fileName
        var index = 1
        while (displayNameExists(resolver, candidate)) {
            candidate = "$base($index)$ext"
            index++
        }
        return candidate
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun displayNameExists(
        resolver: android.content.ContentResolver,
        displayName: String
    ): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(displayName, murexideDownloadRelativePath())
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection, selection, args, null
        )?.use { cursor ->
            return cursor.count > 0
        }
        return false
    }

    private fun createUniqueFile(dir: File, fileName: String): File {
        repeat(MAX_FILE_NAME_ATTEMPTS) { collisionIndex ->
            val candidate = File(dir, downloadDisplayName(fileName, collisionIndex))
            if (candidate.createNewFile()) return candidate
        }
        throw IOException("无法创建不重名的下载文件")
    }

    fun openFile(context: Context, filePathOrUri: String, fileName: String) {
        try {
            val uri = if (filePathOrUri.startsWith("content://")) {
                filePathOrUri.toUri()
            } else {
                val file = File(filePathOrUri)
                if (!file.exists()) {
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    Uri.fromFile(file)
                }
            }

            val mimeType = mimeTypeForName(fileName) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun Throwable.isMediaStorePathCollision(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            val message = current.message.orEmpty()
            if (current is SQLiteConstraintException &&
                message.contains("files._data", ignoreCase = true)
            ) {
                return true
            }
            if (message.contains("UNIQUE constraint failed: files._data", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
