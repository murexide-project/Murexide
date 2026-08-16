package com.juhao.murexide.repository

import com.juhao.murexide.proto.Status
import com.juhao.murexide.proto.list_message
import com.juhao.murexide.proto.list_message_send
import com.juhao.murexide.proto.conversation.ConversationList
import com.juhao.murexide.proto.conversation.ConversationListSend
import com.juhao.murexide.proto.conversation.ConversationStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRepositoryContractTest {
    @Test
    fun `conversation list sends md5 protobuf and maps the response`() = runBlocking {
        var captured: Request? = null
        val response = ConversationList(
            status = ConversationStatus(code = 1),
            data_ = listOf(
                ConversationList.ConversationData(
                    chat_id = "group-1",
                    chat_type = 2L,
                    name = "Group",
                    chat_content = "",
                    timestamp_ms = 100L,
                    send_timestamp = 0L,
                    unread_message = 3L,
                    at = 1L,
                    avatar_url = "avatar",
                    do_not_disturb = 1L,
                    certification_level = 2L
                )
            ),
            md5 = "server-md5"
        )
        val repository = repositoryReturning(
            response.encode(),
            onRequest = { captured = it }
        )

        val result = repository.getConversationList("token-value", md5 = "cached-md5")

        assertTrue(result.isSuccess)
        assertEquals("https://example.test/v1/conversation/list", captured?.url?.toString())
        assertEquals("token-value", captured?.header("token"))
        assertEquals("application/octet-stream", captured?.body?.contentType()?.toString())
        val request = ConversationListSend.ADAPTER.decode(captured!!.bodyBytes())
        assertEquals("cached-md5", request.md5)

        val item = result.getOrThrow().single()
        assertEquals("group-1", item.chatId)
        assertEquals(2, item.chatType)
        assertEquals("[消息]", item.chatContent)
        assertEquals(100L, item.sendTimestamp)
        assertEquals(3, item.unreadMessage)
        assertEquals(1, item.at)
        assertEquals(1, item.doNotDisturb)
        assertEquals(2, item.certificationLevel)
    }

    @Test
    fun `conversation business error is returned as failure`() = runBlocking {
        val repository = repositoryReturning(
            ConversationList(
                status = ConversationStatus(code = -101, msg = "not logged in")
            ).encode()
        )

        val result = repository.getConversationList("token-value")

        assertTrue(result.isFailure)
        assertEquals("not logged in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `malformed conversation protobuf is returned as failure`() = runBlocking {
        val repository = repositoryReturning(byteArrayOf(0x0A, 0x7F))

        val result = repository.getConversationList("token-value")

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `dismiss notification posts documented json and header`() = runBlocking {
        var captured: Request? = null
        val repository = repositoryReturning(
            "{\"code\":1,\"msg\":\"success\"}".toByteArray(),
            mediaType = "application/json",
            onRequest = { captured = it }
        )

        val result = repository.dismissNotification("token-value", "chat-1")

        assertTrue(result.isSuccess)
        assertEquals(
            "https://example.test/v1/conversation/dismiss-notification",
            captured?.url?.toString()
        )
        assertEquals("token-value", captured?.header("token"))
        assertTrue(captured?.body?.contentType()?.toString()?.startsWith("application/json") == true)
        val body = Json.parseToJsonElement(captured!!.bodyText()).jsonObject
        assertEquals("chat-1", body.getValue("chatId").jsonPrimitive.content)
    }

    @Test
    fun `sticky list maps an empty server page without network access`() = runBlocking {
        var captured: Request? = null
        val repository = repositoryReturning(
            "{\"code\":1,\"data\":{\"sticky\":[]},\"msg\":\"success\"}".toByteArray(),
            mediaType = "application/json",
            onRequest = { captured = it }
        )

        val result = repository.getStickyList("token-value", accountId = null)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertEquals("https://example.test/v1/sticky/list", captured?.url?.toString())
        assertEquals("token-value", captured?.header("token"))
        assertEquals("{}", captured!!.bodyText())
    }

    @Test
    fun `latest message request uses one-message protobuf boundary`() = runBlocking {
        var captured: Request? = null
        val repository = repositoryReturning(
            list_message(status = Status(code = 1)).encode(),
            onRequest = { captured = it }
        )

        val result = repository.getLatestMessage("token-value", "chat-1", 1)

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrThrow())
        assertEquals("https://example.test/v1/msg/list-message", captured?.url?.toString())
        assertEquals("token-value", captured?.header("token"))
        val request = list_message_send.ADAPTER.decode(captured!!.bodyBytes())
        assertEquals(1L, request.msg_count)
        assertEquals("", request.msg_id)
        assertEquals(1L, request.chat_type)
        assertEquals("chat-1", request.chat_id)
    }

    private fun repositoryReturning(
        responseBody: ByteArray,
        code: Int = 200,
        mediaType: String = "application/octet-stream",
        onRequest: (Request) -> Unit = {}
    ): ConversationRepository {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                onRequest(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "ERROR")
                    .body(responseBody.toResponseBody(mediaType.toMediaType()))
                    .build()
            }
            .build()
        return ConversationRepository().also { repository ->
            replacePrivateField(repository, "client", client)
            replacePrivateField(repository, "baseUrl", "https://example.test")
        }
    }

    private fun replacePrivateField(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private fun Request.bodyBytes(): ByteArray = Buffer().also { body?.writeTo(it) }.readByteArray()

    private fun Request.bodyText(): String = bodyBytes().toString(Charsets.UTF_8)
}
