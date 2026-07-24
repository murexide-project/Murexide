package com.juhao.murexide.ui.chat

import com.juhao.murexide.data.MessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatImageGalleryTest {
    @Test
    fun gallery_skipsStickersRecalledMessagesAndBlankImages() {
        val messages = listOf(
            message("new-image", MessageItem.CONTENT_TYPE_IMAGE, imageUrl = "new.jpg", msgSeq = 30),
            message(
                "sticker",
                MessageItem.CONTENT_TYPE_STICKER,
                imageUrl = "sticker-in-image-field.webp",
                stickerUrl = "sticker.webp"
            ),
            message("blank-image", MessageItem.CONTENT_TYPE_IMAGE, imageUrl = "  "),
            message("recalled-image", MessageItem.CONTENT_TYPE_IMAGE, imageUrl = "recalled.jpg", isRecalled = true),
            message("old-image", MessageItem.CONTENT_TYPE_IMAGE, imageUrl = "old.jpg", msgSeq = 5)
        )

        val gallery = buildChatImageGallery(messages, selectedMessageId = "new-image")!!

        assertEquals(listOf("old-image", "new-image"), gallery.entries.map { it.messageId })
        assertEquals(listOf("old.jpg", "new.jpg"), gallery.entries.map { it.url })
        assertEquals(listOf(5L, 30L), gallery.entries.map { it.imageId })
        assertEquals(1, gallery.initialIndex)
        assertEquals("old-image", gallery.entries[gallery.initialIndex - 1].messageId)
    }

    @Test
    fun gallery_usesMessageIdWhenDifferentMessagesShareAUrl() {
        val messages = listOf(
            message("new-copy", MessageItem.CONTENT_TYPE_IMAGE, imageUrl = "same.jpg"),
            message("old-copy", MessageItem.CONTENT_TYPE_IMAGE, imageUrl = "same.jpg")
        )

        val gallery = buildChatImageGallery(messages, selectedMessageId = "new-copy")!!

        assertEquals(1, gallery.initialIndex)
    }

    @Test
    fun stickerSelection_doesNotCreateAPhotoGallery() {
        val messages = listOf(
            message("image", MessageItem.CONTENT_TYPE_IMAGE, imageUrl = "photo.jpg"),
            message("sticker", MessageItem.CONTENT_TYPE_STICKER, stickerUrl = "sticker.webp")
        )

        assertNull(buildChatImageGallery(messages, selectedMessageId = "sticker"))
    }

    private fun message(
        id: String,
        contentType: Int,
        imageUrl: String? = null,
        stickerUrl: String? = null,
        isRecalled: Boolean = false,
        msgSeq: Long = 0
    ) = MessageItem(
        msgId = id,
        senderId = "sender",
        senderName = "Sender",
        senderAvatar = "",
        contentType = contentType,
        timestamp = 0,
        msgSeq = msgSeq,
        direction = "left",
        isRecalled = isRecalled,
        imageUrl = imageUrl,
        stickerUrl = stickerUrl
    )
}
