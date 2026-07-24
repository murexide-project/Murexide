package com.juhao.murexide.ui.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageBubbleVideoDurationTest {
    @Test
    fun `formats video durations for chat overlays`() {
        assertEquals("0:00", formatVideoDuration(0))
        assertEquals("0:09", formatVideoDuration(9))
        assertEquals("1:05", formatVideoDuration(65))
        assertEquals("1:01:01", formatVideoDuration(3661))
    }

    @Test
    fun `omits missing or invalid video durations`() {
        assertNull(formatVideoDuration(null))
        assertNull(formatVideoDuration(-1))
    }
}
