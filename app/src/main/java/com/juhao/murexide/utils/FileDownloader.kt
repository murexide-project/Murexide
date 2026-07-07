package com.juhao.murexide.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

object FileDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun downloadFileWithProgress(
        url: String,
        fileName: String,
        context: Context,
        onProgress: (Float) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Referer", "https://myapp.jwznb.com")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onError("下载失败: ${response.code}")
                }
                return@withContext
            }

            val contentLength = response.body.contentLength()
            val inputStream = response.body.byteStream()

            val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToDownloadWithMediaStore(context, fileName, inputStream, contentLength, onProgress)
            } else {
                saveToDownloadLegacy(context, fileName, inputStream, contentLength, onProgress)
            }

            inputStream.close()

            withContext(Dispatchers.Main) {
                onComplete(savedPath)
                openFile(context, savedPath)
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
        onProgress: (Float) -> Unit
    ): String {
        val resolver = context.contentResolver
        val uniqueName = getUniqueDisplayName(resolver, fileName)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("无法创建下载条目")

        resolver.openOutputStream(uri)?.use { outputStream ->
            copyWithProgress(inputStream, outputStream, contentLength, onProgress)
        } ?: throw IOException("无法写入文件")

        return uri.toString()
    }

    private fun saveToDownloadLegacy(
        context: Context,
        fileName: String,
        inputStream: java.io.InputStream,
        contentLength: Long,
        onProgress: (Float) -> Unit
    ): String {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(null) ?: context.filesDir

        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val file = getUniqueFile(downloadDir, fileName)

        FileOutputStream(file).use { outputStream ->
            copyWithProgress(inputStream, outputStream, contentLength, onProgress)
        }

        return file.absolutePath
    }

    private fun copyWithProgress(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        contentLength: Long,
        onProgress: (Float) -> Unit
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead = 0L
        var lastProgress = -1f

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
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
        // 确保进度条走满
        onProgress(1f)
    }

    private fun splitName(fileName: String): Pair<String, String> {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            fileName.substring(0, dotIndex) to fileName.substring(dotIndex)
        } else {
            fileName to ""
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getUniqueDisplayName(
        resolver: android.content.ContentResolver,
        fileName: String
    ): String {
        val (base, ext) = splitName(fileName)
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
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(displayName, "%${Environment.DIRECTORY_DOWNLOADS}%")
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection, selection, args, null
        )?.use { cursor ->
            return cursor.count > 0
        }
        return false
    }

    /**
     * Legacy：若目标文件已存在，则在扩展名之前追加 (n)，
     * 例如 main.lua -> main(1).lua。
     */
    private fun getUniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        if (!file.exists()) return file
        val (base, ext) = splitName(fileName)
        var index = 1
        while (file.exists()) {
            file = File(dir, "$base($index)$ext")
            index++
        }
        return file
    }

    private fun openFile(context: Context, filePathOrUri: String) {
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

            val mimeType = getMimeType(filePathOrUri)
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

    private fun getMimeType(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
            "mp4", "avi", "mkv", "mov" -> "video/*"
            "mp3", "wav", "aac", "flac", "ogg", "m4a" -> "audio/*"
            "apk" -> "application/vnd.android.package-archive"
            "zip", "rar", "7z", "tar", "gz" -> "application/zip"
            "txt", "md", "json", "xml", "html", "css", "js", "kt", "java" -> "text/plain"
            else -> "*/*"
        }
    }
}