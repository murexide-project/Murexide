package com.juhao.murexide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickerModelsTest {
    @Test
    fun `sticker url preserves absolute urls and resolves image keys`() {
        assertEquals(
            "https://cdn.example.com/sticker.webp",
            resolveStickerUrl("https://cdn.example.com/sticker.webp")
        )
        assertEquals(
            "https://chat-img.jwznb.com/stickers/item.webp",
            resolveStickerUrl("/stickers/item.webp")
        )
        assertEquals(
            "https://chat-img2.jwznb.com/sticker.webp",
            resolveStickerUrl("//chat-img2.jwznb.com/sticker.webp")
        )
    }

    @Test
    fun `sticker message prefers compatible image url and falls back to sticker key`() {
        assertEquals(
            "https://chat-img.jwznb.com/legacy.webp",
            resolveStickerMessageUrl(
                imageUrl = "https://chat-img.jwznb.com/legacy.webp",
                stickerUrl = "stickers/new.webp"
            )
        )
        assertEquals(
            "https://chat-img.jwznb.com/stickers/new.webp",
            resolveStickerMessageUrl(imageUrl = null, stickerUrl = "stickers/new.webp")
        )
        assertNull(resolveStickerMessageUrl(imageUrl = " ", stickerUrl = null))
    }
}
