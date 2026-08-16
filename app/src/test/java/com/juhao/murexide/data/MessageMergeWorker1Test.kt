package com.juhao.murexide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMergeWorker1Test {
    @Test
    fun `reconcile loaded recall keeps a reliable local sender identity`() {
        val existing = message("same", "before").copy(
            senderId = "member",
            senderName = "Member",
            senderAvatar = "member.png",
            senderType = 2,
            tags = listOf(MessageTag(1, "member", "blue")),
            direction = "left",
            hasReliableSender = true
        )
        val loaded = existing.copy(
            content = "recalled",
            senderId = "operator",
            senderName = "Operator",
            senderAvatar = "operator.png",
            senderType = 3,
            tags = emptyList(),
            direction = "right",
            isRecalled = true,
            hasReliableSender = false
        )

        val result = reconcileLoadedMessages(listOf(existing), listOf(loaded)).single()

        assertEquals("member", result.senderId)
        assertEquals("Member", result.senderName)
        assertEquals("member.png", result.senderAvatar)
        assertEquals(2, result.senderType)
        assertEquals(existing.tags, result.tags)
        assertEquals("left", result.direction)
        assertTrue(result.hasReliableSender)
        assertTrue(result.isRecalled)
    }

    @Test
    fun `reconcile loaded non-recall does not merge unrelated identity`() {
        val existing = message("same", "local").copy(senderName = "Local")
        val loaded = message("same", "server").copy(senderName = "Server")

        assertEquals(loaded, reconcileLoadedMessages(listOf(existing), listOf(loaded)).single())
    }

    @Test
    fun `incremental merge chooses the newest duplicate update and ignores stale data`() {
        val existing = message("same", "old").copy(updateTimestamp = 10L)
        val stale = message("same", "stale").copy(updateTimestamp = 9L)
        val newest = message("same", "new").copy(updateTimestamp = 11L)
        val anchor = message("anchor", "anchor").copy(timestamp = 100L, msgSeq = 10L)

        val result = mergeIncrementalMessages(
            existingMessages = listOf(existing, anchor),
            updatedMessages = listOf(stale, newest, message("", "ignored")),
            anchorMessage = anchor
        )

        assertEquals(listOf("new", "anchor"), result.map(MessageItem::content))
        assertEquals(11L, result.first().updateTimestamp)
    }

    @Test
    fun `incremental merge only prepends messages newer by sequence or timestamp`() {
        val anchor = message("anchor", "anchor").copy(timestamp = 100L, msgSeq = 10L)
        val newerSequence = message("seq", "sequence").copy(timestamp = 1L, msgSeq = 11L)
        val olderSequence = message("old-seq", "old sequence").copy(timestamp = 999L, msgSeq = 9L)
        val newerTimestamp = message("time", "timestamp").copy(timestamp = 101L, msgSeq = 0L)
        val olderTimestamp = message("old-time", "old timestamp").copy(timestamp = 99L, msgSeq = 0L)

        val result = mergeIncrementalMessages(
            existingMessages = listOf(anchor),
            updatedMessages = listOf(newerSequence, olderSequence, newerTimestamp, olderTimestamp),
            anchorMessage = anchor
        )

        assertEquals(listOf("time", "seq", "anchor"), result.map(MessageItem::msgId))
    }

    @Test
    fun `incremental merge returns the original list when there is no usable update`() {
        val existing = listOf(message("existing", "content"))
        val anchor = existing.single()

        assertSame(existing, mergeIncrementalMessages(existing, emptyList(), anchor))
        assertSame(
            existing,
            mergeIncrementalMessages(existing, listOf(message("", "blank id")), anchor)
        )
    }

    @Test
    fun `outgoing media keys handle blank and protocol relative values`() {
        val result = createOutgoingMessage(
            msgId = "media",
            senderId = "me",
            senderName = "Me",
            senderAvatar = "",
            chatId = "chat",
            chatType = 1,
            content = MessageContent(
                image = " //cdn.example/image.jpg ",
                audio = " ",
                video = "HTTP://cdn.example/video.mp4",
                fileKey = "/files/report.pdf"
            ),
            contentType = MessageItem.CONTENT_TYPE_IMAGE,
            quoteMsgId = null,
            timestamp = 100L
        )

        assertEquals("https://cdn.example/image.jpg", result.imageUrl)
        assertEquals(null, result.audioUrl)
        assertEquals("HTTP://cdn.example/video.mp4", result.videoUrl)
        assertEquals("https://chat-file.jwznb.com/files/report.pdf", result.fileUrl)
    }

    private fun message(id: String, content: String) = MessageItem(
        msgId = id,
        senderId = "sender",
        senderName = "Sender",
        senderAvatar = "",
        chatId = "chat",
        chatType = 1,
        content = content,
        contentType = MessageItem.CONTENT_TYPE_TEXT,
        timestamp = 1L,
        direction = "left"
    )
}
