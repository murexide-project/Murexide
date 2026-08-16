package com.juhao.murexide.utils

import com.juhao.murexide.data.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAndMediaWorker1Test {
    @Test
    fun `yunhu scheme check accepts only the documented lowercase scheme`() {
        assertTrue(UrlSchemeHandler.isYunhuScheme("yunhu://chat-add?id=123&type=user"))
        assertTrue(UrlSchemeHandler.isYunhuScheme("yunhu://post-detail?id=1"))
        assertFalse(UrlSchemeHandler.isYunhuScheme("YUNHU://post-detail?id=1"))
        assertFalse(UrlSchemeHandler.isYunhuScheme("https://example.com"))
        assertFalse(UrlSchemeHandler.isYunhuScheme("yunhu:/post-detail?id=1"))
    }

    @Test
    fun `image url recognition is based on an exact or subdomain host`() {
        assertTrue(isYunhuImageUrl("https://CHAT-IMG.JWZNB.COM/photo.jpg"))
        assertTrue(isYunhuImageUrl("https://cdn.jwznb.com/photo.jpg"))
        assertTrue(isYunhuImageUrl("https://cdn.jwzhd.com/photo.jpg"))
        assertTrue(isYunhuImageUrl("https://chat-img.jwznb.com:443/photo.jpg"))
        assertFalse(isYunhuImageUrl("https://jwznb.com/photo.jpg"))
        assertFalse(isYunhuImageUrl("https://jwznb.com.example/photo.jpg"))
        assertFalse(isYunhuImageUrl("relative/photo.jpg"))
    }

    @Test
    fun `thumbnail operation handles empty query separators and fragments`() {
        assertEquals(
            "https://chat-img.jwznb.com/photo.jpg?imageMogr2/thumbnail/840x840>",
            imageThumbnailUrl("https://chat-img.jwznb.com/photo.jpg?")
        )
        assertEquals(
            "https://chat-img.jwznb.com/photo.jpg?token=abc&imageMogr2/thumbnail/840x840>",
            imageThumbnailUrl("https://chat-img.jwznb.com/photo.jpg?token=abc&")
        )
        assertEquals(
            "https://chat-img.jwznb.com/photo.jpg?imageMogr2/thumbnail/840x840>#fragment",
            imageThumbnailUrl("https://chat-img.jwznb.com/photo.jpg#fragment")
        )
        assertEquals("not a url", imageThumbnailUrl("not a url"))
    }

    @Test
    fun `media aspect ratios reject invalid dimensions`() {
        assertEquals(1f, imageAspectRatio(1L, 1L), 0.0001f)
        assertEquals(4f / 3f, imageAspectRatio(0L, 1L), 0.0001f)
        assertEquals(4f / 3f, imageAspectRatio(1L, -1L), 0.0001f)
        assertEquals(16f / 9f, videoAspectRatio(null, 1080L), 0.0001f)
    }

    @Test
    fun `oriented media dimensions normalize all rotation directions`() {
        assertEquals(1920L to 1080L, orientedMediaDimensions(1920L, 1080L))
        assertEquals(1080L to 1920L, orientedMediaDimensions(1920L, 1080L, 90))
        assertEquals(1080L to 1920L, orientedMediaDimensions(1920L, 1080L, 270))
        assertEquals(1080L to 1920L, orientedMediaDimensions(1920L, 1080L, -90))
        assertEquals(1080L to 1920L, orientedMediaDimensions(1920L, 1080L, 450))
        assertEquals(1920L to 1080L, orientedMediaDimensions(1920L, 1080L, 180))
    }

    @Test
    fun `oriented media dimensions return null for incomplete metadata`() {
        assertEquals(null, orientedMediaDimensions(null, 1080L))
        assertEquals(null, orientedMediaDimensions(1920L, null))
        assertEquals(null, orientedMediaDimensions(0L, 1080L))
        assertEquals(null, orientedMediaDimensions(1920L, -1L))
    }

    @Test
    fun `qiniu upload response converts every media field`() {
        val response = QiniuUploadResponse(
            key = "images/photo.webp",
            hash = "hash",
            fsize = 42L,
            fileType = "image/webp",
            width = 1200L,
            height = 800L,
            fileSuffix = "webp"
        )

        assertEquals(
            MessageMedia(
                fileKey = "images/photo.webp",
                fileHash = "hash",
                fileType = "image/webp",
                width = 1200L,
                height = 800L,
                fileSize = 42L,
                fileSuffix = "webp"
            ),
            response.toMessageMedia()
        )
    }
}
