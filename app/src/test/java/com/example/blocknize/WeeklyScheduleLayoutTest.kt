package com.blocknize.app.ui

import com.blocknize.app.data.AgendaOccurrence
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class WeeklyScheduleLayoutTest {
    private val date = LocalDate.parse("2026-11-03")
    private fun task(id: String, start: Int, end: Int): AgendaOccurrence {
        val midnight = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        return AgendaOccurrence(id, "WEEKLY", id, date, id, "", null, start, end,
            midnight.plusSeconds(start * 60L), midnight.plusSeconds(end * 60L), null, null, null, false)
    }
    private fun layout(vararg tasks: AgendaOccurrence) = schedulePlacements(date, tasks.toList(), ZoneOffset.UTC)

    @Test fun weekAlwaysStartsOnMondayAcrossMonthAndYearBoundaries() {
        assertEquals(LocalDate.parse("2026-10-26"), weekStart(LocalDate.parse("2026-11-01")))
        assertEquals(LocalDate.parse("2026-12-28"), weekStart(LocalDate.parse("2027-01-01")))
        assertEquals(LocalDate.parse("2026-11-02"), weekStart(LocalDate.parse("2026-11-02")))
    }

    @Test fun consecutiveBlocksDoNotOverlapAndShortBlocksKeepTheirDuration() {
        val result = layout(task("a", 480, 481), task("b", 481, 540))
        assertTrue(result.all { it.lane == 0 && it.laneCount == 1 })
        assertEquals(1f, result.first().endMinute - result.first().startMinute, 0f)
    }

    @Test fun overlappingGroupsUseSeparateLanesAndReuseFreeLanes() {
        val result = layout(task("a", 480, 600), task("b", 510, 540), task("c", 540, 660), task("d", 720, 780))
        assertEquals(listOf(0, 1, 1, 0), result.map { it.lane })
        assertEquals(listOf(2, 2, 2, 1), result.map { it.laneCount })
        assertEquals(240, scheduledMinutes(result))
    }

    @Test fun threeSimultaneousTasksDoNotHideEachOther() {
        val result = layout(task("a", 480, 600), task("b", 480, 600), task("c", 480, 600))
        assertEquals(setOf(0, 1, 2), result.map { it.lane }.toSet())
        assertTrue(result.all { it.laneCount == 3 })
        assertEquals(120, scheduledMinutes(result))
    }

    @Test fun historicalSegmentsUseEffectiveTimesInsteadOfOriginalSchedule() {
        val original = task("a", 480, 600)
        val result = layout(original.copy(start = original.start.plusSeconds(1800), end = original.end.minusSeconds(1800)))
        assertEquals(510f, result.single().startMinute, 0f)
        assertEquals(570f, result.single().endMinute, 0f)
    }

    @Test fun dayEndAndEmptyDaysAreHandled() {
        assertEquals(1440f, layout(task("a", 1380, 1440)).single().endMinute, 0f)
        assertTrue(layout().isEmpty())
        assertEquals(0, scheduledMinutes(emptyList()))
        assertTrue(layout(task("outside", 1440, 1500)).isEmpty())
    }
}
