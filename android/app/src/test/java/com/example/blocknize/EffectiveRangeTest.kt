package com.blocknize.app.ui

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveRangeTest {
    @Test fun shortSegmentsShowSecondsInsteadOfIdenticalMinutes() {
        assertEquals("12:04:03–12:04:47", formatEffectiveRange(
            Instant.parse("2026-10-29T12:04:03Z"), Instant.parse("2026-10-29T12:04:47Z"), ZoneId.of("UTC")))
    }

    @Test fun normalSegmentsKeepTheCompactTimeRange() {
        assertEquals("12:03–12:06", formatEffectiveRange(
            Instant.parse("2026-10-29T12:03:10Z"), Instant.parse("2026-10-29T12:06:40Z"), ZoneId.of("UTC")))
    }
}
