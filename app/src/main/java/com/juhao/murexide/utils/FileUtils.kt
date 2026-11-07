package com.juhao.murexide.utils

import androidx.compose.ui.graphics.vector.ImageVector
import com.juhao.murexide.ui.icons.AppIcons

fun getFileIcon(fileName: String): ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "apk" -> AppIcons.Android
        "pdf" -> AppIcons.PictureAsPdf
        "doc", "docx" -> AppIcons.Description
        "xls", "xlsx" -> AppIcons.TableChart
        "ppt", "pptx" -> AppIcons.Slideshow
        "zip", "rar", "7z", "tar", "gz" -> AppIcons.FolderZip
        "mp3", "wav", "aac", "flac", "ogg", "m4a" -> AppIcons.AudioFile
        "mp4", "avi", "mkv", "mov", "flv" -> AppIcons.VideoFile
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> AppIcons.Image
        "txt", "md", "json", "xml", "html", "css", "js", "kt", "java" -> AppIcons.Code
        else -> AppIcons.InsertDriveFile
    }
}