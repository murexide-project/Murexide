package com.juhao.murexide.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import com.flyjingfish.openimagelib.OpenImage
import com.flyjingfish.openimagelib.beans.OpenImageUrl
import com.flyjingfish.openimagelib.enums.MediaType
import com.juhao.murexide.utils.imageThumbnailUrl

data class OpenImageItem(
    val originalUrl: String,
    val thumbnailUrl: String = originalUrl
) : OpenImageUrl {
    override fun getImageUrl(): String = originalUrl

    override fun getVideoUrl(): String = ""

    override fun getCoverImageUrl(): String = thumbnailUrl

    override fun getType(): MediaType = MediaType.IMAGE
}

fun imageMessagePreviewItem(url: String): OpenImageItem = OpenImageItem(
    originalUrl = url,
    thumbnailUrl = imageThumbnailUrl(url)
)

fun fullImagePreviewItem(url: String): OpenImageItem = OpenImageItem(originalUrl = url)

fun showImageViewer(
    context: Context,
    images: List<OpenImageItem>,
    initialIndex: Int = 0
): Boolean {
    val activity = context.findActivity() ?: return false
    if (images.isEmpty()) return false

    val selectedIndex = initialIndex.coerceIn(images.indices)
    val viewer = OpenImage.with(activity)
        .setImageUrlList(images)
        .setBothLoadCover(true)
        .setShowDownload()
        .setShowClose()
        .setNoneClickView()
        .setClickPosition(selectedIndex)
        .setOnItemLongClickListener { _, image, _ ->
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Image URL", image.imageUrl))
            Toast.makeText(activity, "链接已复制", Toast.LENGTH_SHORT).show()
        }

    viewer.show()
    return true
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
