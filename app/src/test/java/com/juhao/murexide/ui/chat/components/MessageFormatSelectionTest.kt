package com.juhao.murexide.ui.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageFormatSelectionTest {
    @Test
    fun noHorizontalMovement_keepsInitialTextOption() {
        assertEquals(
            2,
            sendFormatOptionIndex(
                horizontalDrag = 0f,
                initialIndex = 2,
                optionWidth = 76f,
                optionCount = 3
            )
        )
    }

    @Test
    fun slidingHorizontally_selectsRelativeOptionsWithoutVerticalMovement() {
        assertEquals(1, sendFormatOptionIndex(-50f, 2, 76f, 3))
        assertEquals(0, sendFormatOptionIndex(-130f, 2, 76f, 3))
        assertEquals(2, sendFormatOptionIndex(50f, 1, 76f, 3))
    }

    @Test
    fun largeHorizontalMovement_clampsToEdgeOptions() {
        assertEquals(0, sendFormatOptionIndex(-500f, 2, 76f, 3))
        assertEquals(2, sendFormatOptionIndex(500f, 0, 76f, 3))
    }

    @Test
    fun downwardMovementAtThreshold_cancelsAndUpwardRecoveryRestoresSending() {
        org.junit.Assert.assertFalse(shouldCancelFormatSend(-20f, 32f))
        org.junit.Assert.assertFalse(shouldCancelFormatSend(31.9f, 32f))
        org.junit.Assert.assertTrue(shouldCancelFormatSend(32f, 32f))
    }
}
