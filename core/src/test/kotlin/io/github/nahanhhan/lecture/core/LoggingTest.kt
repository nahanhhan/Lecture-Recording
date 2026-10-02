package io.github.nahanhhan.lecture.core

import java.time.ZoneId
import kotlin.test.*

class LoggingTest {
    @Test fun noneRejectsEveryEvent() {
        LogEventLevel.entries.forEach { assertFalse(logLevelAllows(LogLevel.NONE, it)) }
    }
    @Test fun infoKeepsKeyEventsAndRejectsDetail() {
        assertTrue(logLevelAllows(LogLevel.INFO, LogEventLevel.INFO))
        assertTrue(logLevelAllows(LogLevel.INFO, LogEventLevel.ERROR))
        assertFalse(logLevelAllows(LogLevel.INFO, LogEventLevel.DEBUG))
    }
    @Test fun debugKeepsEverything() {
        LogEventLevel.entries.forEach { assertTrue(logLevelAllows(LogLevel.DEBUG, it)) }
    }
    @Test fun levelSurvivesWireRoundTrip() {
        LogLevel.entries.forEach { assertEquals(it, logLevelOfWire(it.wireName())) }
        assertNull(logLevelOfWire("verbose"))
        assertEquals(LogLevel.INFO, logLevelOfWire(" INFO "))
    }
    @Test fun maskingHidesEveryCredentialPattern() {
        val raw = "save api_key=sk-abcDEF1234567890 token=ghp_0123456789abcdef Authorization: Bearer eyJhbGciOi.abc secret=\"p@ss w0rd\""
        val masked = maskCredentials(raw)
        listOf("sk-abcDEF1234567890", "ghp_0123456789abcdef", "eyJhbGciOi.abc", "p@ss w0rd").forEach {
            assertFalse(masked.contains(it), "掩码后不应出现 $it：$masked")
        }
        assertTrue(masked.contains("api_key=***"))
        assertTrue(masked.contains("Bearer ***"))
    }
    @Test fun infoLineNeverContainsApiKeyWhileDebugKeepsRawText() {
        val message = "model call key=sk-secretValue12345 done"
        val info = formatLogLine(LogLevel.INFO, 0L, LogEventLevel.INFO, "main", message, ZoneId.of("UTC"))
        val debug = formatLogLine(LogLevel.DEBUG, 0L, LogEventLevel.DEBUG, "main", message, ZoneId.of("UTC"))
        assertFalse(info.contains("sk-secretValue12345"), "Info 行不出现 API Key 原文：$info")
        assertTrue(debug.contains("sk-secretValue12345"), "Debug 行保留原文：$debug")
    }
    @Test fun lineFormatCarriesTimestampLevelAndSource() {
        val line = formatLogLine(LogLevel.INFO, 0L, LogEventLevel.ERROR, "asr", "识别失败", ZoneId.of("UTC"))
        assertEquals("1970-01-01 00:00:00.000 E/asr 识别失败", line)
        assertTrue(line.startsWith("1970-01-01 00:00:00.000 "), "时间戳应在行首以便导出归并：$line")
    }
}
