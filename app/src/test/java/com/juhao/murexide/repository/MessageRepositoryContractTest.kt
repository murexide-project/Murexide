package com.juhao.murexide.repository

import com.juhao.murexide.data.MessageContent
import com.juhao.murexide.proto.Msg
import com.juhao.murexide.proto.Status
import com.juhao.murexide.proto.button_report
import com.juhao.murexide.proto.button_report_send
import com.juhao.murexide.proto.edit_message
import com.juhao.murexide.proto.edit_message_send
import com.juhao.murexide.proto.list_message
import com.juhao.murexide.proto.list_message_by_update_send
import com.juhao.murexide.proto.list_message_send
import com.juhao.murexide.proto.recall_msg
import com.juhao.murexide.proto.recall_msg_send
import com.juhao.murexide.proto.send_message
import com.juhao.murexide.proto.send_message_send
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryContractTest {
    @Test
    fun `message list posts documented protobuf and maps a successful response`() = runBlocking {
        var captured: Request? = null
        val response = list_message(
            status = Status(code = 1),
            msg = listOf(
                Msg(
                    msg_id = "message-1",
                    content_type = 1,
                    content = Msg.Content(text = "hello"),
                    send_time = 1234L,
                    msg_seq = 9L
                )
            )
        )
        val repository = MessageRepository(
            client = clientReturning(response.encode(), onRequest = { captured = it }),
            baseUrl = "https://example.test"
        )

        val result = repository.getMessageList(
            token = "token-value",
            chatId = "chat-1",
            chatType = 2,
            msgId = "before-message"
        )

        assertTrue(result.isSuccess)
        assertEquals("https://example.test/v1/msg/list-message", captured?.url?.toString())
        assertEquals("token-value", captured?.header("token"))
        assertEquals("application/octet-stream", captured?.body?.contentType()?.toString())

        val request = list_message_send.ADAPTER.decode(captured!!.bodyBytes())
        assertEquals(20L, request.msg_count)
        assertEquals("before-message", request.msg_id)
        assertEquals(2L, request.chat_type)
        assertEquals("chat-1", request.chat_id)
        assertEquals("message-1", result.getOrThrow().single().msgId)
        assertEquals("hello", result.getOrThrow().single().content)
    }

    @Test
    fun `incremental message request clamps negative pagination inputs`() = runBlocking {
        var captured: Request? = null
        val repository = MessageRepository(
            client = clientReturning(
                list_message(status = Status(code = 1)).encode(),
                onRequest = { captured = it }
            ),
            baseUrl = "https://example.test"
        )

        val result = repository.getMessagesByUpdate(
            token = "token-value",
            chatId = "chat-1",
            chatType = 1,
            updateTime = -1L,
            msgCount = 0
        )

        assertTrue(result.isSuccess)
        assertEquals("https://example.test/v1/msg/list-message-by-update", captured?.url?.toString())
        assertEquals("token-value", captured?.header("token"))
        val request = list_message_by_update_send.ADAPTER.decode(captured!!.bodyBytes())
        assertEquals(0L, request.update_time)
        assertEquals(1L, request.msg_count)
        assertEquals(1L, request.chat_type)
        assertEquals("chat-1", request.chat_id)
    }

    @Test
    fun `send message encodes content and returns generated message id`() = runBlocking {
        var captured: Request? = null
        val repository = MessageRepository(
            client = clientReturning(
                send_message(status = Status(code = 1)).encode(),
                onRequest = { captured = it }
            ),
            baseUrl = "https://example.test"
        )

        val result = repository.sendMessage(
            token = "token-value",
            chatId = "chat-1",
            chatType = 3,
            content = MessageContent(
                text = "hello",
                mentionedId = listOf("user-1"),
                quoteMsgText = "quoted",
                form = "{}"
            ),
            contentType = 5,
            quoteMsgId = "quoted-message",
            commandId = 42L
        )

        assertTrue(result.isSuccess)
        assertEquals("https://example.test/v1/msg/send-message", captured?.url?.toString())
        assertEquals("token-value", captured?.header("token"))
        val request = send_message_send.ADAPTER.decode(captured!!.bodyBytes())
        assertFalse(request.msg_id.isBlank())
        assertEquals(request.msg_id, result.getOrThrow())
        assertEquals("chat-1", request.chat_id)
        assertEquals(3L, request.chat_type)
        assertEquals(5L, request.content_type)
        assertEquals("quoted-message", request.quote_msg_id)
        assertEquals(42L, request.command_id)
        assertEquals("hello", request.content?.text)
        assertEquals(listOf("user-1"), request.content?.mentioned_id)
        assertEquals("{}", request.content?.form)
    }

    @Test
    fun `send message returns protobuf business errors`() = runBlocking {
        val repository = MessageRepository(
            client = clientReturning(
                send_message(status = Status(code = 9, msg = "quota exceeded")).encode()
            ),
            baseUrl = "https://example.test"
        )

        val result = repository.sendMessage(
            token = "token-value",
            chatId = "chat-1",
            chatType = 2,
            content = MessageContent(text = "hello"),
            contentType = 1
        )

        assertTrue(result.isFailure)
        assertEquals("quota exceeded", result.exceptionOrNull()?.message)
    }

    @Test
    fun `malformed message protobuf is returned as failure`() = runBlocking {
        val repository = MessageRepository(
            client = clientReturning(byteArrayOf(0x0A, 0x7F)),
            baseUrl = "https://example.test"
        )

        val result = repository.getMessageList("token", "chat-1", 2)

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `edit message uses documented protobuf route and header`() = runBlocking {
        var captured: Request? = null
        val repository = MessageRepository(
            client = clientReturning(
                edit_message(status = Status(code = 1)).encode(),
                onRequest = { captured = it }
            ),
            baseUrl = "https://example.test"
        )

        val result = repository.editMessage(
            token = "token-value",
            msgId = "message-1",
            chatId = "chat-1",
            chatType = 2,
            content = MessageContent(text = "edited", quoteMsgText = "quote"),
            contentType = 1,
            quoteMsgId = "quote-1"
        )

        assertTrue(result.isSuccess)
        assertEquals("https://example.test/v1/msg/edit-message", captured?.url?.toString())
        assertEquals("token-value", captured?.header("token"))
        val request = edit_message_send.ADAPTER.decode(captured!!.bodyBytes())
        assertEquals("message-1", request.msg_id)
        assertEquals("chat-1", request.chat_id)
        assertEquals(2, request.chat_type)
        assertEquals("edited", request.content?.text)
        assertEquals("quote", request.content?.quote_msg_text)
        assertEquals("quote-1", request.quote_msg_id)
    }

    @Test
    fun `button report uses documented protobuf fields`() = runBlocking {
        var captured: Request? = null
        val repository = MessageRepository(
            client = clientReturning(
                button_report(status = Status(code = 1)).encode(),
                onRequest = { captured = it }
            ),
            baseUrl = "https://example.test"
        )

        val result = repository.reportButtonClick(
            token = "token-value",
            msgId = "message-1",
            chatId = "chat-1",
            chatType = 2,
            userId = "user-1",
            buttonValue = "confirm"
        )

        assertTrue(result.isSuccess)
        assertEquals("https://example.test/v1/msg/button-report", captured?.url?.toString())
        assertEquals("token-value", captured?.header("token"))
        val request = button_report_send.ADAPTER.decode(captured!!.bodyBytes())
        assertEquals("message-1", request.msg_id)
        assertEquals(2L, request.chat_type)
        assertEquals("chat-1", request.chat_id)
        assertEquals("user-1", request.user_id)
        assertEquals("confirm", request.button_value)
    }

    @Test
    fun `recall message returns business error from protobuf response`() = runBlocking {
        var captured: Request? = null
        val repository = MessageRepository(
            client = clientReturning(
                recall_msg(status = Status(code = 4, msg = "already recalled")).encode(),
                onRequest = { captured = it }
            ),
            baseUrl = "https://example.test"
        )

        val result = repository.recallMessage(
            token = "token-value",
            msgId = "message-1",
            chatId = "chat-1",
            chatType = 2
        )

        assertTrue(result.isFailure)
        assertEquals("already recalled", result.exceptionOrNull()?.message)
        assertEquals("https://example.test/v1/msg/recall-msg", captured?.url?.toString())
        assertEquals("token-value", captured?.header("token"))
        val request = recall_msg_send.ADAPTER.decode(captured!!.bodyBytes())
        assertEquals(listOf("message-1"), request.msg_id)
        assertEquals("chat-1", request.chat_id)
        assertEquals(2L, request.chat_type)
    }

    private fun clientReturning(
        responseBody: ByteArray,
        code: Int = 200,
        onRequest: (Request) -> Unit = {}
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            onRequest(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "ERROR")
                .body(responseBody.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }
        .build()

    private fun Request.bodyBytes(): ByteArray = Buffer().also { body?.writeTo(it) }.readByteArray()
}
