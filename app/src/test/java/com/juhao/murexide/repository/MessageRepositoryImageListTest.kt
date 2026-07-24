package com.juhao.murexide.repository

import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.proto.Msg
import com.juhao.murexide.proto.Status
import com.juhao.murexide.proto.pic_list_message_by_mid_seq
import com.juhao.murexide.proto.pic_list_message_by_mid_seq_send
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRepositoryImageListTest {
    @Test
    fun imageListRequest_usesDocumentedFieldsAndUnsignedImageId() {
        val body = createImageMessageListRequestBody(
            imageId = ULong.MAX_VALUE.toLong(),
            chatId = "chat-id",
            chatType = 2,
            earlierQuantities = 20,
            latestQuantities = 5
        )
        val buffer = Buffer()
        body.writeTo(buffer)

        val request = pic_list_message_by_mid_seq_send.ADAPTER.decode(buffer.readByteArray())

        assertEquals(ULong.MAX_VALUE.toLong(), request.image_id)
        assertEquals(2L, request.chat_type)
        assertEquals("chat-id", request.chat_id)
        assertEquals(20L, request.earlier_quantities)
        assertEquals(5L, request.latest_quantities)
    }

    @Test
    fun imageListResponse_keepsOnlyRegularImagesInChronologicalOrder() {
        val response = pic_list_message_by_mid_seq(
            status = Status(code = 1),
            msg = listOf(
                message("new", MessageItem.CONTENT_TYPE_IMAGE, "new.jpg", timestamp = 30),
                message("sticker", MessageItem.CONTENT_TYPE_STICKER, "sticker.webp", timestamp = 20),
                message("blank", MessageItem.CONTENT_TYPE_IMAGE, "", timestamp = 15),
                message("recalled", MessageItem.CONTENT_TYPE_IMAGE, "gone.jpg", timestamp = 10, recalled = true),
                message("old", MessageItem.CONTENT_TYPE_IMAGE, "old.jpg", timestamp = 5)
            )
        )

        val images = response.toConversationImages()

        assertEquals(listOf("old", "new"), images.map { it.messageId })
        assertEquals(listOf("old.jpg", "new.jpg"), images.map { it.url })
    }

    private fun message(
        id: String,
        contentType: Int,
        url: String,
        timestamp: Long,
        recalled: Boolean = false
    ) = Msg(
        msg_id = id,
        content_type = contentType,
        content = Msg.Content(image_url = url),
        send_time = timestamp,
        msg_seq = timestamp,
        msg_delete_time = if (recalled) 1 else 0
    )
}
