package com.blocknize.app.domain

import com.blocknize.app.data.AgendaOccurrence
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CurrentTaskSelectorTest {
    private val start = Instant.parse("2026-11-03T09:00:00Z")
    private fun task(key: String, offset: Long = 0) = AgendaOccurrence(key, "WEEKLY", key,
        LocalDate.parse("2026-11-03"), key, "", null, 540, 600,
        start.plusSeconds(offset), start.plusSeconds(3600 + offset), null, null, null, false)

    @Test fun completedHistoricalAndInterruptedTasksAreNotActionable() {
        val tasks = listOf(task("active"), task("done").copy(completed = true),
            task("old").copy(historical = true), task("interrupted").copy(interrupted = true))
        assertEquals(listOf("active"), currentTasks(tasks, start.plusSeconds(600)).map { it.occurrenceKey })
    }

    @Test fun latestStartedTaskIsFirstAndTiesAreDeterministic() {
        val tasks = listOf(task("a"), task("c", 300), task("b", 300))
        assertEquals(listOf("b", "c", "a"), currentTasks(tasks, start.plusSeconds(600)).map { it.occurrenceKey })
    }

    @Test fun startIsInclusiveAndEndIsExclusive() {
        assertEquals(1, currentTasks(listOf(task("a")), start).size)
        assertTrue(currentTasks(listOf(task("a")), start.plusSeconds(3600)).isEmpty())
    }

    @Test fun omittedAndMovedOriginsAreNeverCurrentOrNext() {
        val markers = listOf(task("skip").copy(exceptionStatus = "SKIP"), task("move").copy(exceptionStatus = "REPLACE"))
        assertTrue(currentTasks(markers, start.plusSeconds(1)).isEmpty())
        assertNull(nextTask(markers, start.minusSeconds(1)))
    }

    @Test fun nextTaskUsesTheSameEligibilityRules() {
        val tasks = listOf(task("done").copy(completed = true), task("old").copy(historical = true),
            task("next", 600), task("later", 1200))
        assertEquals("next", nextTask(tasks, start.minusSeconds(60))?.occurrenceKey)
        assertNull(nextTask(emptyList(), start))
    }
}
