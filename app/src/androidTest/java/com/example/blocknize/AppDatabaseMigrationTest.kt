package com.blocknize.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blocknize.app.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun migrationTwoToThreePreservesExistingDataAndCreatesTemporalBaseline() {
        val dbName = "history-migration-test"
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO routine_templates(id,name,createdAt,updatedAt) VALUES('template','Rutina',1,1)")
            execSQL("INSERT INTO template_revisions(id,templateId,createdAt,payloadJson) VALUES('revision','template',1,'{\"schemaVersion\":1,\"templateId\":\"template\",\"name\":\"Rutina\",\"blocks\":[]}')")
            execSQL("INSERT INTO schedule_change_events(eventId,effectiveAt,previousTemplateId,templateId,revisionId,zoneId,changeType) VALUES('event',10,NULL,'template','revision','UTC','ACTIVATION')")
            execSQL("INSERT INTO active_template_selection(singletonId,templateId,revisionId,effectiveAt) VALUES(1,'template','revision',10)")
            execSQL("INSERT INTO one_off_tasks(id,dateEpochDay,title,description,tagId,startMinute,endMinute) VALUES('temporary',20000,'Dentista','',NULL,600,630)")
            execSQL("INSERT INTO completion_records(occurrenceKey,sourceType,sourceId,dateEpochDay,completedAt,titleSnapshot,startMinuteSnapshot,endMinuteSnapshot,sourceTemplateId,sourceRevisionId) VALUES('legacy-key','ONE_OFF','temporary',20000,20,'Dentista',600,630,NULL,NULL)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3).apply {
            query("SELECT title FROM one_off_tasks WHERE id='temporary'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("Dentista", cursor.getString(0))
            }
            query("SELECT sourceEventId FROM completion_records WHERE occurrenceKey='legacy-key'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(null, cursor.getString(0))
            }
            query("SELECT changeType,title FROM one_off_schedule_events WHERE taskId='temporary'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("UPSERT", cursor.getString(0))
                assertEquals("Dentista", cursor.getString(1))
            }
            query("SELECT COUNT(*) FROM schedule_change_events WHERE eventId='event'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }
}
