package com.juhao.murexide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageModelsWorker1Test {
    @Test
    fun `button parser decodes rows and ignores unknown fields`() {
        val buttons = parseMessageButtons(
            """[[{"text":"Open","actionType":1,"url":"https://example.com","extra":true},{"text":"Copy","actionType":2,"value":"copied"}],[{"text":"Report","actionType":3,"value":"event"}]]"""
        )

        assertEquals(2, buttons.size)
        assertEquals(
            MessageButton(
                text = "Open",
                actionType = MessageButton.ACTION_JUMP,
                url = "https://example.com"
            ),
            buttons[0][0]
        )
        assertEquals(
            MessageButton(
                text = "Copy",
                actionType = MessageButton.ACTION_COPY,
                value = "copied"
            ),
            buttons[0][1]
        )
        assertEquals(MessageButton.ACTION_REPORT, buttons[1][0].actionType)
    }

    @Test
    fun `button parser treats absent and blank payloads as no buttons`() {
        assertTrue(parseMessageButtons(null).isEmpty())
        assertTrue(parseMessageButtons("").isEmpty())
        assertTrue(parseMessageButtons("  \n").isEmpty())
    }

    @Test
    fun `message display content maps every media type and preserves text`() {
        val expected = listOf(
            MessageItem.CONTENT_TYPE_IMAGE to "[图片消息]",
            MessageItem.CONTENT_TYPE_FILE to "[文件消息]",
            MessageItem.CONTENT_TYPE_STICKER to "[表情消息]",
            MessageItem.CONTENT_TYPE_VIDEO to "[视频消息]",
            MessageItem.CONTENT_TYPE_AUDIO to "[语音消息]",
            MessageItem.CONTENT_TYPE_MARKDOWN to "[Markdown消息]",
            MessageItem.CONTENT_TYPE_HTML to "[HTML消息]",
            MessageItem.CONTENT_TYPE_POST to "[文章]"
        )

        expected.forEach { (contentType, displayContent) ->
            assertEquals(displayContent, message(contentType = contentType).getDisplayContent())
        }
        assertEquals("plain text", message(content = "plain text").getDisplayContent())
        assertEquals("[消息]", message(content = "", contentType = 0).getDisplayContent())
    }

    @Test
    fun `recalled content wins over the original content type`() {
        val recalled = message(
            content = "original",
            contentType = MessageItem.CONTENT_TYPE_IMAGE,
            isRecalled = true
        )

        assertEquals("此消息已被撤回", recalled.getDisplayContent())
        assertEquals("此消息已被撤回", recalled.getRecallDisplayContent())
    }

    @Test
    fun `message direction determines ownership`() {
        assertTrue(message(direction = "right").isMine)
        assertFalse(message(direction = "left").isMine)
        assertFalse(message(direction = "").isMine)
    }

    @Test
    fun `media and content models retain optional metadata`() {
        val media = MessageMedia(
            fileKey = "images/photo.jpg",
            fileHash = "hash",
            fileType = "image/jpeg",
            width = 640,
            height = 480,
            fileSize = 1234,
            fileSuffix = "jpg"
        )
        val content = MessageContent(
            text = "caption",
            image = "images/photo.jpg",
            quoteMsgText = "quoted",
            quoteImageUrl = "quote.jpg",
            quoteImageName = "quote-name.jpg",
            fileKey = "files/report.pdf",
            fileName = "report.pdf",
            fileSize = 99,
            audio = "audio/voice.m4a",
            audioTime = 4,
            video = "video/clip.mp4",
            postType = "markdown",
            expressionId = "expression",
            stickerItemId = 7,
            stickerPackId = 8,
            mentionedId = listOf("u1", "u2"),
            commandId = 9,
            form = "{}",
            media = media
        )

        assertEquals(media, content.media)
        assertEquals(listOf("u1", "u2"), content.mentionedId)
        assertEquals("files/report.pdf", content.fileKey)
        assertEquals(4, content.audioTime)
        assertEquals(9L, content.commandId)
    }

    @Test
    fun `mention token exposes the display form`() {
        val token = MentionToken("user-1", "张三", start = 2, endExclusive = 5)

        assertEquals("@张三", token.displayText)
        assertEquals(2, token.start)
        assertEquals(5, token.endExclusive)
    }

    private fun message(
        content: String = "",
        contentType: Int = MessageItem.CONTENT_TYPE_TEXT,
        direction: String = "right",
        isRecalled: Boolean = false
    ) = MessageItem(
        msgId = "message-id",
        senderId = "sender-id",
        senderName = "Sender",
        senderAvatar = "avatar",
        content = content,
        contentType = contentType,
        timestamp = 1L,
        direction = direction,
        isRecalled = isRecalled
    )
}
