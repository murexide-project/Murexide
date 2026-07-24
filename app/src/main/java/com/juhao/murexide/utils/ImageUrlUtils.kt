package com.juhao.murexide.utils

import java.net.URI
import java.util.Locale

private const val FALLBACK_IMAGE_ASPECT_RATIO = 4f / 3f
private const val THUMBNAIL_OPERATION = "imageMogr2/thumbnail/840x840>"

fun isYunhuImageUrl(url: String): Boolean {
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.ROOT)
    return host == "chat-img.jwznb.com" ||
        host?.endsWith(".jwznb.com") == true ||
        host?.endsWith(".jwzhd.com") == true
}

fun imageThumbnailUrl(url: String): String {
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.ROOT)
    if (host != "chat-img.jwznb.com" || url.contains("imageMogr2/")) return url

    val fragmentIndex = url.indexOf('#')
    val base = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
    val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""
    val separator = when {
        base.endsWith('?') || base.endsWith('&') -> ""
        '?' in base -> "&"
        else -> "?"
    }
    return "$base$separator$THUMBNAIL_OPERATION$fragment"
}

fun imageAspectRatio(width: Long?, height: Long?): Float {
    if (width == null || height == null || width <= 0 || height <= 0) {
        return FALLBACK_IMAGE_ASPECT_RATIO
    }
    val ratio = width.toDouble() / height.toDouble()
    return ratio.toFloat().takeIf { it.isFinite() && it > 0f }
        ?: FALLBACK_IMAGE_ASPECT_RATIO
}
