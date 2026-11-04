package com.blocknize.app.ui.focus

import com.blocknize.app.data.AgendaOccurrence
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusTimeTest {
    private val start = Instant.parse("2026-11-03T09:00:00Z")
    private val task = AgendaOccurrence("task", "WEEKLY", "task", LocalDate.parse("2026-11-03"), "Estudiar", "", null,
        540, 600, start, start.plusSeconds(3600), null, null, null, false)

    @Test fun remainingTimeUsesTheScheduledEndInsteadOfAnIndependentTimer() {
        assertEquals(2_700_000L, remainingBlockMillis(task, start.plusSeconds(900)))
        assertEquals("00:45:00", formatFocusTime(remainingBlockMillis(task, start.plusSeconds(900))))
    }

    @Test fun returningLaterRecalculatesWithoutReplayingTicks() {
        assertEquals(1_200_000L, remainingBlockMillis(task, start.plusSeconds(2400)))
        assertEquals("00:20:00", formatFocusTime(remainingBlockMillis(task, start.plusSeconds(2400))))
    }

    @Test fun finishedBlocksNeverShowNegativeTime() {
        assertEquals(0L, remainingBlockMillis(task, task.end))
        assertEquals(0L, remainingBlockMillis(task, task.end.plusSeconds(500)))
    }

    @Test fun formattingSupportsHoursAndDoesNotFinishEarly() {
        assertEquals("01:01:01", formatFocusTime(3_661_000))
        assertEquals("00:00:01", formatFocusTime(1))
        assertEquals("00:00:01", formatFocusTime(999))
        assertEquals("00:00:00", formatFocusTime(-1000))
    }
}
