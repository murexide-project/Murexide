package com.juhao.murexide.ui.components

import com.flyjingfish.openimagelib.enums.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageViewerItemFactoryTest {
    @Test
    fun `image preview keeps identity and adds a YunHu thumbnail operation`() {
        val item = imageMessagePreviewItem(
            url = "https://chat-img.jwznb.com/path/photo.jpg",
            messageId = "message",
            imageId = 7L
        )

        assertEquals("https://chat-img.jwznb.com/path/photo.jpg", item.originalUrl)
        assertTrue(item.thumbnailUrl.contains("imageMogr2/thumbnail/840x840>"))
        assertEquals("message", item.messageId)
        assertEquals(7L, item.imageId)
        assertEquals(MediaType.IMAGE, item.mediaType)
        assertNull(item.playbackUrl)
    }

    @Test
    fun `full image preview does not rewrite its URL`() {
        val url = "https://example.com/photo.jpg?size=full"
        val item = fullImagePreviewItem(url)

        assertEquals(url, item.originalUrl)
        assertEquals(url, item.thumbnailUrl)
        assertNull(item.messageId)
        assertEquals(MediaType.IMAGE, item.mediaType)
    }

    @Test
    fun `video preview uses the playback URL and sequence as image identity`() {
        val item = videoMessagePreviewItem(
            url = "https://example.com/clip.mp4",
            messageId = "video-message",
            sequence = 42L
        )

        assertEquals(MediaType.VIDEO, item.mediaType)
        assertEquals("https://example.com/clip.mp4", item.originalUrl)
        assertEquals("https://example.com/clip.mp4", item.thumbnailUrl)
        assertEquals("https://example.com/clip.mp4", item.playbackUrl)
        assertEquals(42L, item.imageId)
    }

    @Test
    fun `source bounds are valid only with positive dimensions`() {
        assertTrue(ImageViewerSourceBounds(0, 0, 1, 1, false).isValid)
        assertFalse(ImageViewerSourceBounds(0, 0, 0, 1, false).isValid)
        assertFalse(ImageViewerSourceBounds(0, 0, 1, 0, true).isValid)
    }
}
