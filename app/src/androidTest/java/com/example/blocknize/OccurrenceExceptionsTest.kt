package com.blocknize.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blocknize.app.data.*
import com.blocknize.app.domain.currentTasks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

private class ExceptionTestClock(var time: Instant = Instant.parse("2026-11-02T08:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(time, zone)
    override fun instant(): Instant = time
}

@RunWith(AndroidJUnit4::class)
class OccurrenceExceptionsTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TemplateRepository
    private val clock = ExceptionTestClock()
    private val monday = LocalDate.parse("2026-11-02")
    private lateinit var template: String

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repo = TemplateRepository(db, clock)
        repo.createTemplate("Original")
        template = db.dao().templateIds().single()
        repo.saveBlock(template, null, "Estudiar", "Original", "Estudio", 1, 540, 660)
        repo.activate(template)
    }
    @After fun cleanup() { db.close() }
    private suspend fun original() = repo.getAgenda(monday).first { it.exceptionStatus == null }
    private suspend fun replacement(date: LocalDate) = repo.getAgenda(date).single { it.exceptionId != null && it.exceptionStatus == null }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }

    @Test fun skipAffectsOnlyOneMondayAndRestoreDoesNotEditTemplate() = runBlocking {
        val task = original()
        repo.saveException(task, "SKIP")
        val skipped = repo.getAgenda(monday).single()
        assertEquals("SKIP", skipped.exceptionStatus)
        assertTrue(currentTasks(listOf(skipped), Instant.parse("2026-11-02T09:30:00Z")).isEmpty())
        assertNull(repo.getAgenda(monday.plusWeeks(1)).single().exceptionStatus)
        repo.saveException(skipped, "RESTORE")
        assertEquals(task, original())
        assertEquals(1, db.dao().blockCount())
        assertEquals(2, db.dao().exceptionEventsBefore(Long.MAX_VALUE).size)
    }

    @Test fun moveToEmptyDayAndAcrossYearKeepsStableIdentityAndSingleCompletion() = runBlocking {
        repo.saveException(original(), "REPLACE", monday.plusDays(1), startMinute = 720, endMinute = 780)
        val moved = replacement(monday.plusDays(1))
        val nextYear = LocalDate.parse("2027-01-02")
        repo.saveException(moved, "REPLACE", nextYear, startMinute = 800, endMinute = 850)
        assertTrue(repo.getAgenda(monday.plusDays(1)).isEmpty())
        val final = replacement(nextYear)
        assertEquals(moved.occurrenceKey, final.occurrenceKey)
        repo.setCompleted(final, true)
        repo.setCompleted(final, true)
        assertEquals(1, db.dao().completionCount())
        assertTrue(replacement(nextYear).completed)
        rejected { repo.saveException(replacement(nextYear), "RESTORE") }
        repo.setCompleted(replacement(nextYear), false)
        repo.saveException(replacement(nextYear), "RESTORE")
        assertTrue(repo.getAgenda(nextYear).isEmpty())
    }

    @Test fun replacementSurvivesActivationEditsAndDeletionOfOriginalBlock() = runBlocking {
        val task = original()
        repo.saveException(task, "REPLACE", monday.plusDays(1), title = "Compromiso")
        repo.saveBlock(template, task.sourceId, "Editada", "", "", 1, 600, 700)
        assertEquals("Compromiso", replacement(monday.plusDays(1)).title)
        repo.deleteBlock(template, task.sourceId)
        repo.createTemplate("Nueva")
        val other = db.dao().templateIds().first { it != template }
        repo.saveBlock(other, null, "Otra", "", "", 1, 540, 660)
        repo.activate(other)
        assertEquals(listOf("Estudiar", "Otra"), repo.getAgenda(monday).map { it.title }.sorted())
        assertEquals("Compromiso", replacement(monday.plusDays(1)).title)
        assertEquals("Original", replacement(monday.plusDays(1)).sourceTemplateName)
    }

    @Test fun explicitEditWinsOverWeeklyRevisionAndDoesNotLeakIntoExport() = runBlocking {
        val task = original()
        val json = repo.exportTemplateJson(template)
        repo.saveException(task, "REPLACE", title = "Solo hoy", description = "Excepción", tagName = "Personal")
        repo.saveBlock(template, task.sourceId, "Base nueva", "", "", 1, 600, 700)
        val edited = replacement(monday)
        assertEquals("Solo hoy", edited.title)
        assertEquals("Personal", edited.tagName)
        assertEquals("Excepción", edited.description)
        assertEquals(540, edited.startMinute)
        assertFalse(repo.exportTemplateJson(template).contains("Solo hoy"))
        assertTrue(json.contains("Estudiar"))
        assertEquals("Base nueva", repo.getAgenda(monday.plusWeeks(1)).single().title)
    }

    @Test fun changingDuringActivityAndRestoringPreservesEffectiveSegments() = runBlocking {
        clock.time = Instant.parse("2026-11-02T09:30:00Z")
        repo.saveException(original(), "REPLACE", title = "Modificada")
        clock.time = Instant.parse("2026-11-02T10:00:00Z")
        repo.saveException(replacement(monday), "RESTORE")
        clock.time = Instant.parse("2026-11-03T08:00:00Z")
        val history = repo.getCalendarAgenda(monday).filter { it.exceptionStatus == null }.sortedBy { it.start }
        assertEquals(listOf("Estudiar", "Modificada", "Estudiar"), history.map { it.title })
        assertEquals(Instant.parse("2026-11-02T09:30:00Z"), history[0].end)
        assertEquals(history[0].end, history[1].start)
        assertEquals(Instant.parse("2026-11-02T10:00:00Z"), history[1].end)
        assertEquals(history[1].end, history[2].start)
        assertEquals(history.size, history.map { it.occurrenceKey }.distinct().size)
    }

    @Test fun completedSubstitutionHasOneHistoricalCompletionEvenAfterEdits() = runBlocking {
        clock.time = Instant.parse("2026-11-02T09:20:00Z")
        repo.saveException(original(), "REPLACE", title = "Primera")
        clock.time = Instant.parse("2026-11-02T09:40:00Z")
        repo.saveException(replacement(monday), "REPLACE", title = "Segunda")
        clock.time = Instant.parse("2026-11-02T10:20:00Z")
        repo.setCompleted(replacement(monday), true)
        clock.time = Instant.parse("2026-11-03T08:00:00Z")
        val history = repo.getCalendarAgenda(monday)
        assertEquals(1, history.count { it.completed })
        assertTrue(history.any { it.title == "Primera" && it.interrupted })
    }

    @Test fun completionAfterRestoreBelongsOnlyToTheRestoredInterval() = runBlocking {
        clock.time = Instant.parse("2026-11-02T09:20:00Z")
        repo.saveException(original(), "SKIP")
        clock.time = Instant.parse("2026-11-02T09:40:00Z")
        repo.saveException(repo.getAgenda(monday).single(), "RESTORE")
        val restored = original()
        assertEquals(clock.time, restored.start)
        clock.time = Instant.parse("2026-11-02T10:20:00Z")
        repo.setCompleted(restored, true)
        clock.time = Instant.parse("2026-11-03T08:00:00Z")
        val history = repo.getCalendarAgenda(monday)
        assertEquals(1, history.count { it.completed })
        assertEquals(Instant.parse("2026-11-02T09:40:00Z"), history.single { it.completed }.start)
        assertTrue(history.any { it.end == Instant.parse("2026-11-02T09:20:00Z") && it.interrupted })
        repo.setCompleted(history.single { it.completed }, false)
        assertEquals(0, db.dao().completionCount())
        assertTrue(repo.getCalendarAgenda(monday).none { it.completed })
    }

    @Test fun staleDialogCompletedOriginsInvalidDatesAndPastStartsAreRejected() = runBlocking {
        val task = original()
        rejected { repo.saveException(task, "REPLACE", title = "") }
        rejected { repo.saveException(task, "REPLACE", startMinute = 600, endMinute = 500) }
        rejected { repo.saveException(task, "REPLACE", monday.minusDays(1)) }
        clock.time = Instant.parse("2026-11-02T10:00:00Z")
        rejected { repo.saveException(task, "REPLACE", startMinute = 570, endMinute = 660) }
        repo.setCompleted(task, true)
        rejected { repo.saveException(original(), "SKIP") }
        repo.setCompleted(original(), false)
        repo.saveException(original(), "SKIP")
        rejected { repo.saveException(task, "SKIP") }
        assertEquals(1, db.dao().exceptions().size)
        assertEquals(1, db.dao().exceptionEventsBefore(Long.MAX_VALUE).size)
    }

    @Test fun exceptionAndEventRollbackTogether() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_exception_event BEFORE INSERT ON occurrence_exception_events BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repo.saveException(original(), "SKIP"); fail("Expected database failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertTrue(db.dao().exceptions().isEmpty())
        assertTrue(db.dao().exceptionEventsBefore(Long.MAX_VALUE).isEmpty())
        assertNull(original().exceptionId)
    }

    @Test fun calendarAndHistoryFlowsInvalidateWithoutChangingDate() = runBlocking {
        var first = true
        val calendar = withTimeout(5_000) { repo.observeCalendarAgenda(monday).onEach {
            if (first) { first = false; repo.saveException(it.single(), "SKIP") }
        }.take(2).toList() }
        assertEquals("SKIP", calendar.last().single().exceptionStatus)
        first = true
        val history = withTimeout(5_000) { repo.observeHistory(monday).onEach {
            if (first) { first = false; repo.saveException(repo.getAgenda(monday).single(), "RESTORE") }
        }.take(2).toList() }
        assertEquals(2, history.size)
        assertNull(original().exceptionStatus)
    }

    @Test fun movingOntoExistingBlockPreservesBothActivities() = runBlocking {
        repo.saveBlock(template, null, "Habitual", "", "", 2, 720, 800)
        repo.saveException(original(), "REPLACE", monday.plusDays(1), startMinute = 720, endMinute = 780)
        assertEquals(setOf("Habitual", "Estudiar"), repo.getAgenda(monday.plusDays(1)).map { it.title }.toSet())
    }
}
