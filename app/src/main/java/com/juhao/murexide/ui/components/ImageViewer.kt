package com.juhao.murexide.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.widget.Toast
import com.flyjingfish.openimagelib.OpenImage
import com.flyjingfish.openimagelib.beans.OpenImageUrl
import com.flyjingfish.openimagelib.enums.MediaType
import com.juhao.murexide.R
import com.juhao.murexide.utils.imageThumbnailUrl

data class OpenImageItem(
    val originalUrl: String,
    val thumbnailUrl: String = originalUrl,
    val messageId: String? = null,
    val imageId: Long? = null
) : OpenImageUrl {
    override fun getImageUrl(): String = originalUrl

    override fun getVideoUrl(): String = ""

    override fun getCoverImageUrl(): String = thumbnailUrl

    override fun getType(): MediaType = MediaType.IMAGE
}

fun imageMessagePreviewItem(
    url: String,
    messageId: String? = null,
    imageId: Long? = null
): OpenImageItem = OpenImageItem(
    originalUrl = url,
    thumbnailUrl = imageThumbnailUrl(url),
    messageId = messageId,
    imageId = imageId
)

fun fullImagePreviewItem(url: String): OpenImageItem = OpenImageItem(originalUrl = url)

data class ImageViewerPagination(
    val chatId: String,
    val chatType: Int
)

fun showImageViewer(
    context: Context,
    images: List<OpenImageItem>,
    initialIndex: Int = 0,
    pagination: ImageViewerPagination? = null
): Boolean {
    val activity = context.findActivity() ?: return false
    if (images.isEmpty()) return false

    val selectedIndex = initialIndex.coerceIn(images.indices)
    val viewerOptions = Bundle().apply {
        putStringArrayList(
            MurexideOpenImageActivity.EXTRA_IMAGE_URLS,
            ArrayList(images.map(OpenImageItem::originalUrl))
        )
        putStringArrayList(
            MurexideOpenImageActivity.EXTRA_IMAGE_MESSAGE_IDS,
            ArrayList(images.map { it.messageId.orEmpty() })
        )
        putLongArray(
            MurexideOpenImageActivity.EXTRA_IMAGE_IDS,
            images.map { it.imageId ?: 0L }.toLongArray()
        )
        pagination?.let { options ->
            putString(MurexideOpenImageActivity.EXTRA_CHAT_ID, options.chatId)
            putInt(MurexideOpenImageActivity.EXTRA_CHAT_TYPE, options.chatType)
        }
    }
    val viewer = OpenImage.with(activity)
        .setImageUrlList(images)
        // Keep the first frame as a thumbnail, then let OpenImage load the
        // original image.  Keep one page on either side warm while swiping.
        .setBothLoadCover(true)
        .setPreloadCount(false, 1)
        .setOpenImageStyle(R.style.Theme_Murexide_OpenImage)
        .setOpenImageActivityCls(
            MurexideOpenImageActivity::class.java,
            MurexideOpenImageActivity.EXTRA_VIEWER_OPTIONS,
            viewerOptions
        )
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
