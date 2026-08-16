package com.juhao.murexide.utils

import android.os.Build
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadVersionWorker1Test {
    @Test
    fun `download name sanitizer keeps only a safe leaf name`() {
        assertEquals("report.pdf", sanitizeDownloadFileName(" /private\\report.pdf "))
        assertEquals("report_bad__.pdf", sanitizeDownloadFileName("report<bad>?.pdf"))
        assertEquals("download", sanitizeDownloadFileName("..."))
        assertEquals("__", sanitizeDownloadFileName("\u0000\u0001"))
    }

    @Test
    fun `download display suffix handles dotfiles and compound extensions`() {
        assertEquals(".config(1)", downloadDisplayName(".config", collisionIndex = 1))
        assertEquals("archive.tar(2).gz", downloadDisplayName("archive.tar.gz", collisionIndex = 2))
        assertEquals("archive(3)", downloadDisplayName("archive", collisionIndex = 3))
        assertEquals("report.pdf", downloadDisplayName("report.pdf", collisionIndex = 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative collision index is rejected`() {
        downloadDisplayName("report.pdf", collisionIndex = -1)
    }

    @Test
    fun `legacy write permission boundary is based on Android P`() {
        assertTrue(requiresLegacyWritePermission(Build.VERSION_CODES.P, permissionGranted = false))
        assertFalse(requiresLegacyWritePermission(Build.VERSION_CODES.P, permissionGranted = true))
        assertFalse(requiresLegacyWritePermission(Build.VERSION_CODES.Q, permissionGranted = false))
        assertFalse(requiresLegacyWritePermission(Build.VERSION_CODES.Q, permissionGranted = true))
        assertTrue(requiresLegacyWritePermission(Build.VERSION_CODES.M, permissionGranted = false))
    }

    @Test
    fun `version info is a value model for stable and snapshot metadata`() {
        val stable = AppVersionInfo("v1.2.3", 12L, false, "1.2.3", "")
        val snapshot = AppVersionInfo("1.2.3-abc123", 13L, true, "1.2.3", "abc123")

        assertEquals("1.2.3", stable.baseVersion)
        assertFalse(stable.isSnapShotVersion)
        assertEquals("abc123", snapshot.commitHash)
        assertTrue(snapshot.isSnapShotVersion)
        assertEquals(13L, snapshot.versionCode)
    }

    @Test
    fun `version comparison ignores v prefix and prerelease suffix`() {
        assertTrue(compareVersion("v1.10.0", "1.2.9") > 0)
        assertTrue(compareVersion("1.2.1", "1.2") > 0)
        assertEquals(0, compareVersion("1.2.0", "v1.2"))
        assertEquals(0, compareVersion("1.bad.3", "1.0.3"))
        assertTrue(compareVersion("2.0-beta", "1.99.99") > 0)
        assertTrue(compareVersion("1.9", "2.0") < 0)
    }

    private fun compareVersion(first: String, second: String): Int {
        val method: Method = Class.forName("com.juhao.murexide.utils.UpdateCheckerKt")
            .getDeclaredMethod("compareVersion", String::class.java, String::class.java)
            .also { it.isAccessible = true }
        return method.invoke(null, first, second) as Int
    }
}
