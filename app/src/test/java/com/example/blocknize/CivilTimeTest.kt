package com.blocknize.app

import com.blocknize.app.domain.SystemCivilClock
import com.blocknize.app.domain.civilInstant
import com.blocknize.app.domain.civilInterval
import com.blocknize.app.domain.parseScheduleMinute
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CivilTimeTest {
    @Test fun endOfDayOnlyAllowedAsEndAndResolvesToNextMidnight() {
        assertNull(parseScheduleMinute("24:00"))
        assertEquals(1440, parseScheduleMinute("24:00", true))
        assertNull(parseScheduleMinute("24:01", true))
        assertNull(parseScheduleMinute("25:00", true))
        assertEquals(0, parseScheduleMinute("00:00"))
        assertEquals(Instant.parse("2026-11-03T00:00:00Z"),
            civilInstant(LocalDate.parse("2026-11-02"), 1440, ZoneId.of("UTC")))
    }

    @Test fun springGapUsesWallTimeRatherThanElapsedMinutesSinceMidnight() {
        val zone = ZoneId.of("Europe/Madrid")
        val date = LocalDate.parse("2026-03-29")
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), civilInstant(date, 210, zone))
        // Nonexistent 02:30 is shifted to 03:30 by java.time's explicit chosen policy.
        assertEquals(civilInstant(date, 210, zone), civilInstant(date, 150, zone))
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), civilInstant(date, 1440, zone))
    }

    @Test fun autumnOverlapUsesEarlierOffsetAndMidnightRemainsCivil() {
        val zone = ZoneId.of("Europe/Madrid")
        val date = LocalDate.parse("2026-10-25")
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), civilInstant(date, 150, zone))
        assertEquals(Instant.parse("2026-10-25T02:30:00Z"), civilInstant(date, 210, zone))
        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), civilInstant(date, 1440, zone))
    }

    @Test fun nonexistentSpringSlotNeverProducesNegativeDuration() {
        val (start, end) = civilInterval(LocalDate.parse("2026-03-29"), 150, 180, ZoneId.of("Europe/Madrid"))
        assertEquals(start, end)
    }

    @Test fun longLivedClockObservesZoneChangesWithoutRecreation() {
        var zone = ZoneId.of("UTC")
        val clock = SystemCivilClock { zone }
        assertEquals(zone, clock.zone)
        zone = ZoneId.of("America/Lima")
        assertEquals(zone, clock.zone)
        assertEquals(ZoneId.of("Europe/Madrid"), clock.withZone(ZoneId.of("Europe/Madrid")).zone)
    }
}
