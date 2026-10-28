package com.flintylemming.claudelauncher

import com.flintylemming.claudelauncher.model.TraceResult
import com.flintylemming.claudelauncher.net.ClaudeTraceChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeTraceCheckerTest {

    @Test
    fun testParseTraceResponse_allowedRegionUS() {
        val sampleTrace = """
            fl=532f118
            h=claude.ai
            ip=104.28.19.45
            ts=1730000000
            visit_scheme=https
            uag=Mozilla/5.0
            colo=SJC
            http=http/2
            loc=US
            tls=TLSv1.3
            sni=plaintext
            warp=off
        """.trimIndent()

        val result = ClaudeTraceChecker.parseTraceResponse(sampleTrace, setOf("CN", "HK"))

        assertTrue(result.isSuccess)
        assertEquals("104.28.19.45", result.ip)
        assertEquals("US", result.loc)
        assertFalse(result.isBlocked)
        assertEquals("美国 (US)", result.displayLocation)
    }

    @Test
    fun testParseTraceResponse_blockedRegionCN() {
        val sampleTrace = """
            fl=532f119
            h=claude.ai
            ip=202.108.22.5
            ts=1730000010
            visit_scheme=https
            uag=Mozilla/5.0
            colo=BJS
            http=http/2
            loc=CN
            tls=TLSv1.3
            sni=plaintext
            warp=off
        """.trimIndent()

        val result = ClaudeTraceChecker.parseTraceResponse(sampleTrace, setOf("CN", "HK"))

        assertTrue(result.isSuccess)
        assertEquals("202.108.22.5", result.ip)
        assertEquals("CN", result.loc)
        assertTrue(result.isBlocked)
        assertEquals("中国大陆 (CN)", result.displayLocation)
    }

    @Test
    fun testParseTraceResponse_blockedRegionHK() {
        val sampleTrace = """
            fl=532f120
            h=claude.ai
            ip=45.12.34.56
            ts=1730000020
            visit_scheme=https
            uag=Mozilla/5.0
            colo=HKG
            http=http/2
            loc=HK
            tls=TLSv1.3
            sni=plaintext
            warp=off
        """.trimIndent()

        // When HK is in blocked list
        val resultBlocked = ClaudeTraceChecker.parseTraceResponse(sampleTrace, setOf("CN", "HK"))
        assertTrue(resultBlocked.isBlocked)

        // When only CN is in blocked list
        val resultAllowed = ClaudeTraceChecker.parseTraceResponse(sampleTrace, setOf("CN"))
        assertFalse(resultAllowed.isBlocked)
    }

    @Test
    fun testParseTraceResponse_invalidOrEmpty() {
        val emptyResult = ClaudeTraceChecker.parseTraceResponse("", setOf("CN", "HK"))
        assertFalse(emptyResult.isSuccess)
        assertTrue(emptyResult.isBlocked)

        val invalidResult = ClaudeTraceChecker.parseTraceResponse("Some error page 502 Bad Gateway", setOf("CN", "HK"))
        assertFalse(invalidResult.isSuccess)
        assertTrue(invalidResult.isBlocked)
    }
}
