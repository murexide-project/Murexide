package com.juhao.murexide.network

import com.juhao.murexide.proto.chat_ws_go.INFO
import com.juhao.murexide.proto.chat_ws_go.WsMsg
import com.juhao.murexide.proto.chat_ws_go.WsTag
import com.juhao.murexide.proto.chat_ws_go.bot_board_message
import com.juhao.murexide.proto.chat_ws_go.edit_message
import com.juhao.murexide.proto.chat_ws_go.heartbeat_ack
import com.juhao.murexide.proto.chat_ws_go.push_message
import com.juhao.murexide.proto.chat_ws_go.stream_message
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketManagerProtobufDispatchWorker3Test {
    private val manager: WebSocketManager
        get() = WebSocketManager.getInstance()

    @After
    fun tearDown() {
        manager.disconnect()
    }

    @Test
    fun pushConversion_preservesPayloadFieldsDirectionAndTimestamps() {
        val event = WsMsg(
            msg_id = "push-direct",
            sender = WsMsg.WsSender(
                chat_id = "me",
                chat_type = 1,
                name = "Me",
                avatar_url = "https://example.test/avatar",
                tag = listOf(WsTag(id = 7, text = "VIP", color = "#00aa00"))
            ),
            recv_id = "group-1",
            chat_id = "group-1",
            chat_type = 2,
            content = WsMsg.WsContent(
                text = "hello",
                buttons = "[[{\"text\":\"Open\",\"actionType\":1,\"url\":\"https://example.test\"}]]",
                image_url = "https://example.test/image",
                quote_msg_text = "quoted text",
                quote_image_url = "https://example.test/quoted",
                file_size = 2048,
                width = 640,
                height = 480,
                post_id = "post-1",
                post_title = "Title",
                post_content = "Body",
                post_content_type = "2"
            ),
            content_type = 2,
            timestamp = 1_000,
            cmd = WsMsg.WsCmd(id = 99, name = "/help"),
            quote_msg_id = "quoted-id",
            msg_seq = 12,
            edit_time = 2_500
        )

        val converted = (event.toPushEvent(currentUserId = "me") as WebSocketManager.WsEvent.NewMessage).message

        assertEquals("push-direct", converted.msgId)
        assertEquals("me", converted.senderId)
        assertEquals("Me", converted.senderName)
        assertEquals("https://example.test/avatar", converted.senderAvatar)
        assertEquals(1, converted.senderType)
        assertEquals("group-1", converted.chatId)
        assertEquals(2, converted.chatType)
        assertEquals("hello", converted.content)
        assertEquals(2, converted.contentType)
        assertEquals(1_000L, converted.timestamp)
        assertEquals(12L, converted.msgSeq)
        assertEquals("right", converted.direction)
        assertTrue(converted.isMine)
        assertTrue(converted.hasReliableSender)
        assertTrue(converted.isEdited)
        assertEquals("quoted-id", converted.quoteMsgId)
        assertEquals("quoted text", converted.quoteMsgText)
        assertEquals("https://example.test/quoted", converted.quoteImageUrl)
        assertEquals("https://example.test/image", converted.imageUrl)
        assertEquals(640L, converted.imageWidth)
        assertEquals(480L, converted.imageHeight)
        assertEquals(2048L, converted.fileSize)
        assertEquals("/help", converted.cmdName)
        assertEquals(99L, converted.cmdId)
        assertEquals("post-1", converted.postId)
        assertEquals("Title", converted.postTitle)
        assertEquals("Body", converted.postContent)
        assertEquals(2, converted.postContentType)
        assertEquals(2_500L, converted.updateTimestamp)
        assertEquals(1, converted.tags.size)
        assertEquals("VIP", converted.tags.single().text)
        assertEquals("Open", converted.buttons.single().single().text)
    }

    @Test
    fun editConversion_usesEditTimeAsUpdateTimestamp() {
        val message = WsMsg(
            msg_id = "edit-direct",
            chat_id = "chat-edit",
            chat_type = 2,
            content = WsMsg.WsContent(text = "edited"),
            content_type = 1,
            timestamp = 10_000,
            msg_seq = 3,
            edit_time = 12_000
        )

        val event = decodeEditMessageEvent(encodeEdit(message))

        assertTrue(event is WebSocketManager.WsEvent.EditMessage)
        val converted = (event as WebSocketManager.WsEvent.EditMessage).message
        assertEquals("edit-direct", converted.msgId)
        assertEquals("chat-edit", converted.chatId)
        assertEquals("edited", converted.content)
        assertEquals(10_000L, converted.timestamp)
        assertEquals(12_000L, converted.updateTimestamp)
        assertEquals("left", converted.direction)
        assertFalse(converted.isRecalled)
        assertTrue(converted.isEdited)
        assertFalse(converted.hasReliableSender)
    }

    @Test
    fun recallConversion_keepsRecallActorAndDeleteTimestamp() {
        val message = WsMsg(
            msg_id = "recall-direct",
            sender = WsMsg.WsSender(chat_id = "moderator", name = "Moderator"),
            chat_id = "chat-recall",
            chat_type = 2,
            content = WsMsg.WsContent(text = "removed"),
            content_type = 1,
            timestamp = 10_000,
            delete_time = 13_000,
            msg_seq = 8,
            edit_time = 12_000
        )

        val event = message.toPushEvent(currentUserId = "author")

        assertTrue(event is WebSocketManager.WsEvent.MessageDeleted)
        val deleted = event as WebSocketManager.WsEvent.MessageDeleted
        assertEquals("recall-direct", deleted.msgId)
        assertEquals("chat-recall", deleted.message.chatId)
        assertEquals(10_000L, deleted.message.timestamp)
        assertEquals(13_000L, deleted.message.deleteTime)
        assertEquals(13_000L, deleted.message.updateTimestamp)
        assertTrue(deleted.message.isRecalled)
        assertFalse(deleted.message.hasReliableSender)
        assertEquals("left", deleted.message.direction)
        assertEquals(RecallActor("moderator", "Moderator"), deleted.actor)
    }

    @Test
    fun pushBinaryMessage_isPublishedAsNewMessage() {
        val wsManager = manager
        setCurrentUserId(wsManager, "me")
        val payload = push_message(
            info = INFO(seq = "push-seq", cmd = "push_message"),
            data_ = push_message.PushData(
                msg = WsMsg(
                    msg_id = "push-wire",
                    sender = WsMsg.WsSender(chat_id = "me", name = "Me"),
                    chat_id = "chat-wire",
                    chat_type = 1,
                    content = WsMsg.WsContent(text = "wire message"),
                    content_type = 1,
                    timestamp = 7_777,
                    msg_seq = 21
                )
            )
        )

        val event = dispatchAndAwaitEvent(wsManager, push_message.ADAPTER.encode(payload)) {
            it is WebSocketManager.WsEvent.NewMessage && it.message.msgId == "push-wire"
        }

        assertTrue(event is WebSocketManager.WsEvent.NewMessage)
        val message = (event as WebSocketManager.WsEvent.NewMessage).message
        assertEquals("wire message", message.content)
        assertEquals("right", message.direction)
        assertEquals(7_777L, message.timestamp)
        assertEquals(7_777L, message.updateTimestamp)
    }

    @Test
    fun editBinaryMessage_isPublishedAsEditedMessage() {
        val wsManager = manager
        val payload = edit_message(
            info = INFO(cmd = "edit_message"),
            data_ = edit_message.EditData(
                msg = WsMsg(
                    msg_id = "edit-wire",
                    chat_id = "chat-wire",
                    chat_type = 2,
                    content = WsMsg.WsContent(text = "edited wire"),
                    content_type = 1,
                    timestamp = 8_000,
                    msg_seq = 22,
                    edit_time = 9_000
                )
            )
        )

        val event = dispatchAndAwaitEvent(wsManager, edit_message.ADAPTER.encode(payload)) {
            it is WebSocketManager.WsEvent.EditMessage && it.message.msgId == "edit-wire"
        }

        assertTrue(event is WebSocketManager.WsEvent.EditMessage)
        val message = (event as WebSocketManager.WsEvent.EditMessage).message
        assertEquals("edited wire", message.content)
        assertEquals(8_000L, message.timestamp)
        assertEquals(9_000L, message.updateTimestamp)
        assertTrue(message.isEdited)
    }

    @Test
    fun recallBinaryMessage_isPublishedAsDeletedMessage() {
        val wsManager = manager
        val payload = edit_message(
            info = INFO(cmd = "edit_message"),
            data_ = edit_message.EditData(
                msg = WsMsg(
                    msg_id = "recall-wire",
                    sender = WsMsg.WsSender(chat_id = "operator", name = "Moderator"),
                    chat_id = "chat-wire",
                    chat_type = 2,
                    timestamp = 8_000,
                    delete_time = 11_000,
                    msg_seq = 23
                )
            )
        )

        val event = dispatchAndAwaitEvent(wsManager, edit_message.ADAPTER.encode(payload)) {
            it is WebSocketManager.WsEvent.MessageDeleted && it.message.msgId == "recall-wire"
        }

        assertTrue(event is WebSocketManager.WsEvent.MessageDeleted)
        val deleted = event as WebSocketManager.WsEvent.MessageDeleted
        assertTrue(deleted.message.isRecalled)
        assertFalse(deleted.message.hasReliableSender)
        assertEquals(11_000L, deleted.message.deleteTime)
        assertEquals(11_000L, deleted.message.updateTimestamp)
        assertEquals(RecallActor("operator", "Moderator"), deleted.actor)
    }

    @Test
    fun streamBinaryMessage_publishesIncrement() {
        val wsManager = manager
        val payload = stream_message(
            info = INFO(cmd = "stream_message"),
            data_ = stream_message.Data(
                msg = stream_message.Data.StreamMsg(
                    msg_id = "stream-wire",
                    recv_id = "receiver",
                    chat_id = "chat-stream",
                    content = "delta"
                )
            )
        )

        val event = dispatchAndAwaitEvent(wsManager, stream_message.ADAPTER.encode(payload)) {
            it is WebSocketManager.WsEvent.StreamContent && it.msgId == "stream-wire"
        }

        assertTrue(event is WebSocketManager.WsEvent.StreamContent)
        val stream = event as WebSocketManager.WsEvent.StreamContent
        assertEquals("stream-wire", stream.msgId)
        assertEquals("chat-stream", stream.chatId)
        assertEquals("delta", stream.content)
    }

    @Test
    fun boardBinaryMessage_publishesAllBoardFields() {
        val wsManager = manager
        val payload = bot_board_message(
            info = INFO(cmd = "bot_board_message"),
            data_ = bot_board_message.BoardData(
                board = bot_board_message.BoardData.BoardContent(
                    bot_id = "bot-1",
                    chat_id = "chat-board",
                    chat_type = 2,
                    content = "announcement",
                    content_type = 1,
                    last_update_time = 1_234_567,
                    bot_name = "Board bot"
                )
            )
        )

        val event = dispatchAndAwaitEvent(wsManager, bot_board_message.ADAPTER.encode(payload)) {
            it is WebSocketManager.WsEvent.BoardUpdate && it.chatId == "chat-board"
        }

        assertTrue(event is WebSocketManager.WsEvent.BoardUpdate)
        val board = event as WebSocketManager.WsEvent.BoardUpdate
        assertEquals("chat-board", board.chatId)
        assertEquals(2, board.chatType)
        assertEquals("bot-1", board.botId)
        assertEquals("Board bot", board.botName)
        assertEquals("announcement", board.content)
        assertEquals(1, board.contentType)
        assertEquals(1_234_567L, board.lastUpdateTime)
    }

    @Test
    fun inviteBinaryMessage_emitsInvitationSignal() = runBlocking {
        val wsManager = manager
        val signal = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) { wsManager.invitationFlow.first() }
        }

        dispatch(wsManager, heartbeat_ack(INFO(cmd = "invite_apply")).let(heartbeat_ack.ADAPTER::encode))

        signal.await()
        assertTrue(true)
    }

    @Test
    fun heartbeatBinaryMessage_refreshesAckTimestamp() {
        val wsManager = manager
        setHeartbeatTimestamp(wsManager, 0L)
        val payload = heartbeat_ack(INFO(seq = "heartbeat-seq", cmd = "heartbeat_ack"))

        dispatch(wsManager, heartbeat_ack.ADAPTER.encode(payload))

        val ackTimestamp = heartbeatTimestamp(wsManager)
        assertTrue(ackTimestamp > 0L)
        assertTrue(ackTimestamp <= System.currentTimeMillis())
    }

    @Test
    fun malformedBinaryMessage_isIgnoredWithoutPublishing() {
        val wsManager = manager
        val malformed = byteArrayOf(0x0A, 0x05, 0x01)

        dispatch(wsManager, malformed)

        val event = runBlocking {
            withTimeoutOrNull(300) {
                wsManager.messageFlow.first {
                    it is WebSocketManager.WsEvent.NewMessage && it.message.msgId == "malformed-wire"
                }
            }
        }
        assertNull(event)
        assertNotNull(wsManager)
    }

    private fun dispatchAndAwaitEvent(
        wsManager: WebSocketManager,
        payload: ByteArray,
        predicate: (WebSocketManager.WsEvent) -> Boolean
    ): WebSocketManager.WsEvent = runBlocking {
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) { wsManager.messageFlow.first(predicate) }
        }
        dispatch(wsManager, payload)
        event.await()
    }

    private fun dispatch(wsManager: WebSocketManager, payload: ByteArray) {
        val method = WebSocketManager::class.java.getDeclaredMethod(
            "handleBinaryMessage",
            ByteArray::class.java
        )
        method.isAccessible = true
        method.invoke(wsManager, payload)
    }

    private fun setCurrentUserId(wsManager: WebSocketManager, userId: String?) {
        WebSocketManager::class.java.getDeclaredField("currentUserId").apply {
            isAccessible = true
            set(wsManager, userId)
        }
    }

    private fun setHeartbeatTimestamp(wsManager: WebSocketManager, timestamp: Long) {
        WebSocketManager::class.java.getDeclaredField("lastHeartbeatAckTime").apply {
            isAccessible = true
            setLong(wsManager, timestamp)
        }
    }

    private fun heartbeatTimestamp(wsManager: WebSocketManager): Long {
        return WebSocketManager::class.java.getDeclaredField("lastHeartbeatAckTime").apply {
            isAccessible = true
        }.getLong(wsManager)
    }

    private fun encodeEdit(message: WsMsg): ByteArray {
        return edit_message(
            info = INFO(cmd = "edit_message"),
            data_ = edit_message.EditData(msg = message)
        ).let(edit_message.ADAPTER::encode)
    }
}
