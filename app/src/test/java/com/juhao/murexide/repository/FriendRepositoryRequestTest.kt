package com.juhao.murexide.repository

import com.juhao.murexide.proto.friend.request_list_send
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendRepositoryRequestTest {

    @Test
    fun requestListBodyUsesEmptyProtobufPayload() {
        val body = createFriendRequestListBody()
        val buffer = Buffer()

        body.writeTo(buffer)
        val payload = buffer.readByteArray()

        assertEquals("application/octet-stream", body.contentType().toString())
        assertEquals(0L, body.contentLength())
        assertArrayEquals(request_list_send().encode(), payload)
        assertEquals(request_list_send(), request_list_send.ADAPTER.decode(payload))
    }
}
