package com.juhao.murexide.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStorageIntegrationTest {
    private lateinit var storage: SettingsStorage

    @Before
    fun setUp() {
        storage = SettingsStorage(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking {
            storage.setThemeMode("system")
            storage.setThemeColor("DYNAMIC")
            storage.setNotificationEnabled(true)
            storage.setBubbleCornerRadius(18f)
            storage.setBubbleOpacity(0.9f)
            storage.setBackgroundOpacity(0.4f)
            storage.setShowMyBubbleAvatar(true)
            storage.setLiquidGlassEnabled(false)
            storage.setLiquidGlassBlur(3f)
        }
    }

    @Test
    fun defaultValuesAreReadableThroughFlowsAndGetters() = runBlocking {
        assertEquals("system", storage.getThemeMode())
        assertEquals("DYNAMIC", storage.getThemeColor())
        assertTrue(storage.getNotificationEnabled())
        assertEquals(18f, storage.getBubbleCornerRadius())
        assertEquals(0.9f, storage.getBubbleOpacity())
        assertEquals(0.4f, storage.getBackgroundOpacity())
        assertTrue(storage.showMyBubbleAvatarFlow.first())
    }

    @Test
    fun settersUpdateTheirFlowsAndGetterValues() = runBlocking {
        storage.setThemeMode("dark")
        storage.setThemeColor("RED")
        storage.setNotificationEnabled(false)
        storage.setBubbleCornerRadius(24f)
        storage.setBubbleOpacity(0.65f)
        storage.setBackgroundOpacity(0.25f)
        storage.setShowMyBubbleAvatar(false)

        assertEquals("dark", storage.themeModeFlow.first { it == "dark" })
        assertEquals("RED", storage.themeColorFlow.first { it == "RED" })
        assertFalse(storage.getNotificationEnabled())
        assertEquals(24f, storage.getBubbleCornerRadius())
        assertEquals(0.65f, storage.getBubbleOpacity())
        assertEquals(0.25f, storage.getBackgroundOpacity())
        assertFalse(storage.showMyBubbleAvatarFlow.first { !it })
    }

    @Test
    fun blurIsClampedAndRecentEmojiFlowUsesStorageOrdering() = runBlocking {
        storage.setLiquidGlassBlur(-2f)
        assertEquals(0f, storage.getLiquidGlassBlur())

        storage.setLiquidGlassBlur(10f)
        assertEquals(4f, storage.getLiquidGlassBlur())

        storage.recordRecentDefaultEmoji("integration-emoji-a")
        storage.recordRecentDefaultEmoji("integration-emoji-b")

        assertEquals(
            listOf("integration-emoji-b", "integration-emoji-a"),
            storage.recentDefaultEmojiNamesFlow.first {
                it.take(2) == listOf("integration-emoji-b", "integration-emoji-a")
            }.take(2)
        )
    }
}
