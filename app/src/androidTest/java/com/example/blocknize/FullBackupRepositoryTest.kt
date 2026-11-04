package com.blocknize.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blocknize.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class FullBackupRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase
    private val date = LocalDate.parse("2026-11-02")
    private val clock = Clock.fixed(Instant.parse("2026-11-02T08:00:00Z"), ZoneOffset.UTC)
    private class Preferences : AppearanceStorage {
        var value = AppearancePreferences(AccentStore.DEFAULT_ACCENT, 30)
        var fail = false
        override suspend fun snapshot() = value
        override suspend fun restore(value: AppearancePreferences) {
            if (fail) error("Simulated DataStore failure")
            this.value = value
        }
    }
    private val sourcePreferences = Preferences()
    private val targetPreferences = Preferences()
    private fun sourceBackup() = FullBackupRepository(context, source, sourcePreferences)
    private fun targetBackup() = FullBackupRepository(context, target, targetPreferences)

    @Before fun setup() = runBlocking {
        source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = TemplateRepository(source, clock)
        repo.createTemplate("Original")
        val id = source.dao().templateIds().single()
        repo.saveBlock(id, null, "Estudiar", "Descripción", "Estudio", 1, 540, 660)
        repo.activate(id)
        val task = repo.getAgenda(date).single()
        repo.saveException(task, "REPLACE", date.plusDays(1), title = "Trasladada")
        repo.setCompleted(repo.getAgenda(date.plusDays(1)).single(), true)
        repo.saveOneOff(null, date, "Temporal", "", "Estudio", 720, 1440)
        // A real active edit, and a historical reference whose editable origin disappeared.
        repo.saveBlock(id, task.sourceId, "Editada", "", "Estudio", 1, 600, 700)
        repo.deleteBlock(id, task.sourceId)
        repo.saveBlock(id, null, "Otro bloque", "", "Estudio", 1, 840, 900)
        val other = repo.getAgenda(date).first { it.title == "Otro bloque" }
        repo.saveException(other, "SKIP")
        repo.saveException(repo.getAgenda(date).first { it.sourceId == other.sourceId }, "RESTORE")
        sourcePreferences.value = AppearancePreferences(0xFF38BDF8.toInt(), 71)
    }

    @After fun cleanup() { source.close(); target.close() }

    @Test fun completeRoundTripPreservesAllTablesIdentityAgendaAndHistory() = runBlocking {
        val json = sourceBackup().export()
        val summary = targetBackup().inspect(json)
        assertEquals(1, summary.templates)
        assertEquals(1, summary.completions)
        val data = JSONObject(json).getJSONObject("data")
        data.keys().forEach { assertTrue("Fixture must cover $it", data.getJSONArray(it).length() > 0) }
        assertTrue(summary.events >= 4)
        targetBackup().restore(json)
        val exported = targetBackup().export()
        assertEquals(JSONObject(json).getJSONObject("data").toString(), JSONObject(exported).getJSONObject("data").toString())
        assertEquals(sourcePreferences.value, targetPreferences.value)
        val original = TemplateRepository(source, clock)
        val restored = TemplateRepository(target, clock)
        for (day in listOf(date, date.plusDays(1), date.plusWeeks(1))) {
            assertEquals(original.getAgenda(day), restored.getAgenda(day))
            assertEquals(original.getCalendarAgenda(day), restored.getCalendarAgenda(day))
        }
        assertNull(target.dao().pendingRestorePreferences())
    }

    @Test fun replacingPopulatedDatabaseTwiceDoesNotMergeOrDuplicateEvents() = runBlocking {
        TemplateRepository(target, clock).createTemplate("Debe desaparecer")
        val json = sourceBackup().export()
        targetBackup().restore(json)
        targetBackup().restore(json)
        assertEquals(source.dao().templateIds(), target.dao().templateIds())
        assertEquals(source.dao().eventCount(), target.dao().eventCount())
        assertEquals(source.dao().completionCount(), target.dao().completionCount())
        assertEquals(source.dao().exceptionEventsBefore(Long.MAX_VALUE), target.dao().exceptionEventsBefore(Long.MAX_VALUE))
    }

    @Test fun invalidForeignKeyNeverDeletesExistingData() = runBlocking {
        TemplateRepository(target, clock).createTemplate("Conservar")
        val before = target.dao().templateIds()
        val root = JSONObject(sourceBackup().export())
        root.getJSONObject("data").getJSONArray("active_template_selection").getJSONObject(0).put("revisionId", "missing")
        rejects { targetBackup().restore(root.toString()) }
        assertEquals(before, target.dao().templateIds())
    }

    @Test fun malformedSnapshotsAndDuplicateSequencesAreRejected() = runBlocking {
        val json = sourceBackup().export()
        val badSnapshot = JSONObject(json)
        badSnapshot.getJSONObject("data").getJSONArray("occurrence_exceptions").getJSONObject(0).put("payloadJson", "{}")
        rejects { targetBackup().inspect(badSnapshot.toString()) }
        val duplicate = JSONObject(json)
        val events = duplicate.getJSONObject("data").getJSONArray("schedule_change_events")
        events.put(JSONObject(events.getJSONObject(0).toString()))
        rejects { targetBackup().inspect(duplicate.toString()) }
        assertEquals(0, target.dao().eventCount())
    }

    @Test fun unsupportedFormatTypesAndMissingTablesAreRejected() = runBlocking {
        val json = sourceBackup().export()
        rejects { targetBackup().inspect("not json") }
        rejects { targetBackup().inspect(JSONObject(json).put("formatVersion", 99).toString()) }
        val missing = JSONObject(json)
        missing.getJSONObject("data").remove("completion_records")
        rejects { targetBackup().inspect(missing.toString()) }
        val wrongType = JSONObject(json)
        wrongType.getJSONObject("preferences").put("widgetOpacity", "71")
        rejects { targetBackup().inspect(wrongType.toString()) }
    }

    @Test fun invalidSingletonAndUnrecognizedColumnsNeverReachLiveDatabase() = runBlocking {
        val json = sourceBackup().export()
        val invalid = JSONObject(json)
        invalid.getJSONObject("data").getJSONArray("active_template_selection").getJSONObject(0).put("singletonId", 2)
        rejects { targetBackup().restore(invalid.toString()) }
        val extra = JSONObject(json)
        extra.getJSONObject("data").getJSONArray("routine_templates").getJSONObject(0).put("unexpectedColumn", "value")
        rejects { targetBackup().restore(extra.toString()) }
        assertTrue(target.dao().templateIds().isEmpty())
    }

    @Test fun liveInsertFailureRollsBackDeletionAndDoesNotLeaveRecoveryMarker() = runBlocking {
        TemplateRepository(target, clock).createTemplate("Conservar")
        val before = target.dao().templateIds()
        target.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_backup BEFORE INSERT ON routine_templates BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        rejects { targetBackup().restore(sourceBackup().export()) }
        assertEquals(before, target.dao().templateIds())
        assertNull(target.dao().pendingRestorePreferences())
    }

    @Test fun preferenceFailureAfterRoomCommitIsRecoverableAndIdempotent() = runBlocking {
        val json = sourceBackup().export()
        targetPreferences.fail = true
        rejects { targetBackup().restore(json) }
        assertEquals(source.dao().templateIds(), target.dao().templateIds())
        assertNotNull(target.dao().pendingRestorePreferences())
        targetPreferences.fail = false
        // New coordinator simulates startup after interrupted restore.
        targetBackup().recoverPending()
        targetBackup().recoverPending()
        assertEquals(sourcePreferences.value, targetPreferences.value)
        assertNull(target.dao().pendingRestorePreferences())
    }

    @Test fun pendingPreferencesSurviveDatabaseCloseAndReopen() = runBlocking {
        val name = "backup-recovery-test-${java.util.UUID.randomUUID()}.db"
        var persistent = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            targetPreferences.fail = true
            rejects { FullBackupRepository(context, persistent, targetPreferences).restore(sourceBackup().export()) }
            persistent.close()
            persistent = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertNotNull(persistent.dao().pendingRestorePreferences())
            targetPreferences.fail = false
            FullBackupRepository(context, persistent, targetPreferences).recoverPending()
            assertEquals(source.dao().templateIds(), persistent.dao().templateIds())
            assertEquals(sourcePreferences.value, targetPreferences.value)
            assertNull(persistent.dao().pendingRestorePreferences())
        } finally {
            persistent.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun restorationEmitsReactiveAgendaInvalidation() = runBlocking {
        val repo = TemplateRepository(target, clock)
        val received = mutableListOf<List<AgendaOccurrence>>()
        kotlinx.coroutines.coroutineScope {
            val listener = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                repo.observeAgenda(date).take(2).collect { received += it }
            }
            // Wait for the initial empty snapshot, not a fixed timing delay.
            kotlinx.coroutines.withTimeout(10_000) { while (received.isEmpty()) kotlinx.coroutines.yield() }
            targetBackup().restore(sourceBackup().export())
            kotlinx.coroutines.withTimeout(10_000) { listener.join() }
        }
        assertTrue(received.first().isEmpty())
        assertTrue(received.last().isNotEmpty())
    }

    private suspend fun rejects(action: suspend () -> Unit) {
        var rejected = false
        try { action() } catch (_: Exception) { rejected = true }
        assertTrue("Expected operation to fail", rejected)
    }
}
