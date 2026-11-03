package com.blocknize.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blocknize.app.data.AppDatabase
import com.blocknize.app.data.TemplateRepository
import com.blocknize.app.data.TemplateTransferCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private class MutableTestClock(var current: Instant, private val currentZone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = currentZone
    override fun withZone(zone: ZoneId): Clock = MutableTestClock(current, zone)
    override fun instant(): Instant = current
}

@RunWith(AndroidJUnit4::class)
class TemplateRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: TemplateRepository
    private lateinit var testClock: MutableTestClock

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        testClock = MutableTestClock(Instant.parse("2026-03-02T08:30:00Z"), ZoneId.of("UTC"))
        repository = TemplateRepository(database, testClock)
    }

    @After fun tearDown() { database.close() }

    @Test fun activationIsSingularAndIdempotentAndActiveEditsCreateHistory() = runBlocking {
        repository.createTemplate("Clases")
        repository.createTemplate("Vacaciones")
        val ids = database.dao().templateIds()
        val classesId = ids[0]
        val holidaysId = ids[1]

        repository.activate(classesId)
        val afterFirst = database.dao().active()
        assertEquals(classesId, afterFirst?.templateId)
        assertEquals(listOf("ACTIVATION"), database.dao().eventTypes())
        repository.activate(classesId)
        assertEquals(1, database.dao().eventCount())

        repository.saveBlock(classesId, null, "Estudiar", "Kotlin", "Estudio", 0b0000101, 9 * 60, 10 * 60)
        assertEquals(listOf("ACTIVATION", "ACTIVE_EDIT"), database.dao().eventTypes())
        assertEquals(2, database.dao().revisionCount())
        val unchanged = repository.getAgenda(LocalDate.parse("2026-03-02")).first()
        repository.saveBlock(classesId, unchanged.sourceId, "Estudiar", "Kotlin", "Estudio", 0b0000101, 9 * 60, 10 * 60)
        assertEquals("Saving unchanged content must not create an event", 2, database.dao().eventCount())

        repository.activate(holidaysId)
        repository.saveBlock(classesId, null, "Leer", "", "", 0b0000010, 12 * 60, 13 * 60)
        assertEquals(holidaysId, database.dao().active()?.templateId)
        assertEquals(listOf("ACTIVATION", "ACTIVE_EDIT", "ACTIVATION"), database.dao().eventTypes())
    }

    @Test fun invalidSaveRollsBackWithoutCreatingAnEvent() = runBlocking {
        repository.createTemplate("Semana")
        val templateId = database.dao().templateIds().single()
        repository.activate(templateId)
        val activeBefore = database.dao().active()
        try {
            repository.saveBlock(templateId, null, "Inválido", "", "", 0, 10 * 60, 9 * 60)
            throw AssertionError("Expected validation failure")
        } catch (_: IllegalArgumentException) {
            // Expected: the full transaction is rolled back.
        }
        assertEquals(0, database.dao().blockCount())
        assertEquals(0, database.dao().taskCount())
        assertEquals(1, database.dao().eventCount())
        assertNotNull(database.dao().active())
        assertEquals(activeBefore?.revisionId, database.dao().active()?.revisionId)
    }

    @Test fun dayAgendaCombinesRecurringAndTemporaryOverlapsAndCompletionIsPerOccurrence() = runBlocking {
        val date = LocalDate.parse("2026-03-02") // Monday
        repository.createTemplate("Rutina")
        val templateId = database.dao().templateIds().single()
        repository.saveBlock(templateId, null, "Estudiar", "Compose", "Estudio", 1, 9 * 60, 11 * 60)
        repository.activate(templateId)
        repository.saveOneOff(null, date, "Cita", "Médico", "Personal", 10 * 60, 10 * 60 + 30)

        val agenda = repository.getAgenda(date)
        assertEquals(2, agenda.size)
        assertEquals(listOf("Estudiar", "Cita"), agenda.map { it.title })
        assertEquals(2, agenda.map { it.occurrenceKey }.distinct().size)

        val laterTask = agenda.single { it.title == "Cita" }
        repository.setCompleted(laterTask, true) // completing ahead of its time is supported
        repository.setCompleted(laterTask, true)
        assertEquals(1, database.dao().completionCount())
        assertEquals(true, repository.getAgenda(date).single { it.title == "Cita" }.completed)

        val weekly = repository.getAgenda(date).single { it.sourceType == "WEEKLY" }
        repository.setCompleted(weekly, true)
        assertEquals(2, database.dao().completionCount())
        val otherDate = date.plusDays(1)
        assertTrue(repository.getAgenda(otherDate).isEmpty())
        assertEquals(false, repository.getAgenda(otherDate).any { it.completed })
    }

    @Test fun completedActivitySurvivesAnActiveTemplateEditAsHistoricalOccurrence() = runBlocking {
        val date = LocalDate.parse("2026-03-02")
        repository.createTemplate("Rutina")
        val templateId = database.dao().templateIds().single()
        repository.saveBlock(templateId, null, "Leer", "", "", 1, 9 * 60, 10 * 60)
        repository.activate(templateId)
        val original = repository.getAgenda(date).single()
        repository.setCompleted(original, true)
        repository.saveBlock(templateId, original.sourceId, "Leer más", "", "", 1, 10 * 60, 11 * 60)
        val current = repository.getAgenda(date)
        assertEquals(2, current.size)
        assertEquals("Leer más", current.single { !it.historical }.title)
        assertEquals("Leer", current.single { it.historical }.title)
        assertEquals(true, current.single { it.historical }.completed)
        assertEquals(listOf("ACTIVATION", "ACTIVE_EDIT"), database.dao().eventTypes())
    }

    @Test fun calendarReconstructsMidBlockSwitchAndAtoBtoAAsDistinctOccurrences() = runBlocking {
        val date = LocalDate.parse("2026-03-02")
        repository.createTemplate("A")
        repository.createTemplate("B")
        val (aId, bId) = database.dao().templateIds()
        repository.saveBlock(aId, null, "A en curso", "", "", 1, 9 * 60, 12 * 60)
        repository.saveBlock(aId, null, "A más tarde", "", "", 1, 16 * 60, 17 * 60)
        repository.saveBlock(bId, null, "B en curso", "", "", 1, 9 * 60 + 30, 11 * 60)
        repository.activate(aId)

        testClock.current = Instant.parse("2026-03-02T10:00:00Z")
        repository.activate(bId)
        testClock.current = Instant.parse("2026-03-02T10:30:00Z")
        repository.activate(aId)

        val reconstructed = repository.getCalendarAgenda(date)
        val oldA = reconstructed.single { it.title == "A en curso" && it.start == Instant.parse("2026-03-02T09:00:00Z") }
        val b = reconstructed.single { it.title == "B en curso" }
        val newA = reconstructed.single { it.title == "A en curso" && it.start == Instant.parse("2026-03-02T10:30:00Z") }
        assertTrue(oldA.interrupted)
        assertEquals(Instant.parse("2026-03-02T10:00:00Z"), oldA.end)
        assertTrue(b.interrupted)
        assertEquals(Instant.parse("2026-03-02T10:30:00Z"), b.end)
        assertEquals(Instant.parse("2026-03-02T09:00:00Z"), newA.originalStart)
        assertTrue(oldA.occurrenceKey != newA.occurrenceKey)
        assertTrue(reconstructed.any { it.title == "A más tarde" && it.start == Instant.parse("2026-03-02T16:00:00Z") })
    }

    @Test fun editingAndDeletingATemporaryTaskPreservesItsEffectiveIntervals() = runBlocking {
        val date = LocalDate.parse("2026-03-02")
        repository.saveOneOff(null, date, "Cita", "Dentista", "Personal", 9 * 60, 11 * 60)
        val taskId = database.queryTaskIds().single()
        testClock.current = Instant.parse("2026-03-02T10:00:00Z")
        repository.saveOneOff(taskId, date, "Cita movida", "Dentista", "Personal", 10 * 60 + 30, 11 * 60 + 30)

        val afterEdit = repository.getCalendarAgenda(date)
        val old = afterEdit.single { it.title == "Cita" }
        val moved = afterEdit.single { it.title == "Cita movida" }
        assertTrue(old.interrupted)
        assertEquals(Instant.parse("2026-03-02T10:00:00Z"), old.end)
        assertEquals(Instant.parse("2026-03-02T10:30:00Z"), moved.start)

        testClock.current = Instant.parse("2026-03-02T10:45:00Z")
        repository.deleteOneOff(taskId)
        val afterDelete = repository.getCalendarAgenda(date)
        assertTrue(afterDelete.any { it.title == "Cita movida" && it.interrupted })
        assertTrue(afterDelete.none { it.title == "Cita" && !it.historical })
    }

    @Test fun templateTransferRoundTripImportsIndependentInactiveCopyWithoutChangingHistory() = runBlocking {
        repository.createTemplate("Semana original")
        val originalId = database.dao().templateIds().single()
        repository.saveBlock(originalId, null, "Estudiar", "Kotlin", "Estudio", 0b0010101, 540, 600)
        repository.activate(originalId)
        val activeBefore = database.dao().active()
        val eventCountBefore = database.dao().eventCount()

        val json = repository.exportTemplateJson(originalId)
        val decoded = TemplateTransferCodec.decode(json)
        assertEquals("Semana original", decoded.name)
        assertEquals(1, decoded.blocks.size)
        assertEquals(3, decoded.dayCount)
        val importedId = repository.importTemplate(decoded, "Copia importada")

        assertTrue(importedId != originalId)
        assertEquals(activeBefore, database.dao().active())
        assertEquals(eventCountBefore, database.dao().eventCount())
        assertEquals("Copia importada", database.dao().template(importedId)?.name)
        assertEquals("Estudio", database.dao().blocks(importedId).single().tagName)
        assertEquals(2, database.dao().templateIds().size)
    }

    @Test fun templateTransferRejectsUnsupportedVersionsAndInvalidSchedule() {
        val unsupported = """{"format":"blocknize-template","schemaVersion":99,"name":"X","tasks":[],"schedule":[]}"""
        val invalid = """{"format":"blocknize-template","schemaVersion":1,"name":"X","tasks":[{"taskId":"a","title":"T","description":"","tag":null}],"schedule":[{"taskId":"a","daysMask":128,"startMinute":60,"endMinute":30}]}"""
        listOf(unsupported, invalid).forEach { content ->
            try {
                TemplateTransferCodec.decode(content)
                throw AssertionError("Invalid template should be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }
}

private suspend fun AppDatabase.queryTaskIds(): List<String> = dao().oneOffTaskIds()
