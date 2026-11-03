package com.blocknize.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AgendaOccurrenceTest {
    @Test fun progressIsElapsedTimeClampedToTheBlock() {
        val start = Instant.parse("2026-03-02T10:00:00Z")
        val occurrence = AgendaOccurrence(
            occurrenceKey = "key", sourceType = "WEEKLY", sourceId = "block",
            date = LocalDate.parse("2026-03-02"), title = "Estudiar", description = "", tagName = null,
            startMinute = 600, endMinute = 660, start = start, end = start.plusSeconds(3600),
            templateId = "template", revisionId = "revision", sourceEventId = "event", completed = false
        )

        assertEquals(0f, occurrence.progressAt(start.minusSeconds(60)), 0f)
        assertEquals(0.25f, occurrence.progressAt(start.plusSeconds(900)), 0.001f)
        assertEquals(1f, occurrence.progressAt(start.plusSeconds(4000)), 0f)
    }
}
