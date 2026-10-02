package io.github.nahanhhan.lecture.core

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
}
