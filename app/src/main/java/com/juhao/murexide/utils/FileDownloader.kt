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
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("无法创建下载条目")

        resolver.openOutputStream(uri)?.use { outputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (contentLength > 0) {
                    val progress = totalBytesRead.toFloat() / contentLength
                    onProgress(progress)
                }
            }
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

        val file = File(downloadDir, fileName)

        FileOutputStream(file).use { outputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (contentLength > 0) {
                    val progress = totalBytesRead.toFloat() / contentLength
                    onProgress(progress)
                }
            }
        }

        return file.absolutePath
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