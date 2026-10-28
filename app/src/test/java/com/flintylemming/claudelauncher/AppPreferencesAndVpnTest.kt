package com.flintylemming.claudelauncher

import com.flintylemming.claudelauncher.data.AppPreferences
import com.flintylemming.claudelauncher.util.VpnChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesAndVpnTest {

    @Test
    fun testModeConstants() {
        assertEquals("vpn", AppPreferences.MODE_VPN)
        assertEquals("claude_trace", AppPreferences.MODE_CLAUDE_TRACE)
        assertEquals("claude_trace", AppPreferences.DEFAULT_DETECTION_MODE)
    }

    @Test
    fun testVpnInterfaceCheckDoesNotCrash() {
        // hasVpnNetworkInterface should run safely on any host (JVM/Android)
        val result = VpnChecker.hasVpnNetworkInterface()
        // Result is boolean depending on host machine interfaces, but must execute without error
        assertNotNull(result)
    }
}
