package com.juhao.murexide.utils

import com.juhao.murexide.ui.components.imageMessagePreviewItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUrlUtilsTest {
    @Test
    fun `yunhu image url gets qiniu thumbnail operation`() {
        val original = "https://chat-img.jwznb.com/messages/photo.jpg"

        assertEquals(
            "$original?imageMogr2/thumbnail/840x840>",
            imageThumbnailUrl(original)
        )
    }

    @Test
    fun `thumbnail operation preserves query and fragment`() {
        val original = "https://chat-img.jwznb.com/messages/photo.jpg?token=abc#preview"

        assertEquals(
            "https://chat-img.jwznb.com/messages/photo.jpg?token=abc&" +
                "imageMogr2/thumbnail/840x840>#preview",
            imageThumbnailUrl(original)
        )
    }

    @Test
    fun `existing transform and external urls stay unchanged`() {
        val transformed = "https://chat-img.jwznb.com/photo.jpg?imageMogr2/thumbnail/320x"
        val external = "https://images.example.com/photo.jpg"
        val lookalike = "https://chat-img.jwznb.com.example.com/photo.jpg"

        assertEquals(transformed, imageThumbnailUrl(transformed))
        assertEquals(external, imageThumbnailUrl(external))
        assertEquals(lookalike, imageThumbnailUrl(lookalike))
    }

    @Test
    fun `preview item keeps separate thumbnail and original urls`() {
        val original = "https://chat-img.jwznb.com/photo.jpg"
        val item = imageMessagePreviewItem(original)

        assertEquals(original, item.originalUrl)
        assertEquals(original, item.getImageUrl())
        assertEquals(imageThumbnailUrl(original), item.thumbnailUrl)
        assertEquals(imageThumbnailUrl(original), item.getCoverImageUrl())
    }

    @Test
    fun `aspect ratio uses valid dimensions and falls back for missing dimensions`() {
        assertEquals(16f / 9f, imageAspectRatio(1920, 1080), 0.0001f)
        assertEquals(4f / 3f, imageAspectRatio(null, 1080), 0.0001f)
        assertEquals(4f / 3f, imageAspectRatio(1920, 0), 0.0001f)
        assertEquals(4f / 3f, imageAspectRatio(-1, 1080), 0.0001f)
    }

    @Test
    fun `referer host matching rejects lookalike domains`() {
        assertTrue(isYunhuImageUrl("https://chat-img.jwznb.com/photo.jpg"))
        assertTrue(isYunhuImageUrl("https://cdn.jwzhd.com/photo.jpg"))
        assertFalse(isYunhuImageUrl("https://jwznb.com.example.com/photo.jpg"))
        assertFalse(isYunhuImageUrl("not a url"))
    }
}
