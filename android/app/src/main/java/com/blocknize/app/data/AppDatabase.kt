package com.blocknize.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "routine_templates")
data class RoutineTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(@PrimaryKey val id: String, val name: String)

@Entity(
    tableName = "task_definitions",
    foreignKeys = [ForeignKey(entity = RoutineTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("templateId"), Index("tagId")]
)
data class TaskDefinitionEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val title: String,
    val description: String,
    val tagId: String?
)

@Entity(
    tableName = "schedule_blocks",
    foreignKeys = [ForeignKey(entity = TaskDefinitionEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("templateId"), Index("taskId")]
)
data class ScheduleBlockEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val taskId: String,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int
)

@Entity(
    tableName = "active_template_selection",
    foreignKeys = [
        ForeignKey(entity = RoutineTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TemplateRevisionEntity::class, parentColumns = ["id"], childColumns = ["revisionId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("templateId"), Index("revisionId")]
)
data class ActiveSelectionEntity(@PrimaryKey val singletonId: Int = 1, val templateId: String, val revisionId: String, val effectiveAt: Long)

@Entity(
    tableName = "template_revisions",
    foreignKeys = [ForeignKey(entity = RoutineTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("templateId")]
)
data class TemplateRevisionEntity(@PrimaryKey val id: String, val templateId: String, val createdAt: Long, val payloadJson: String)

@Entity(
    tableName = "schedule_change_events",
    foreignKeys = [
        ForeignKey(entity = RoutineTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TemplateRevisionEntity::class, parentColumns = ["id"], childColumns = ["revisionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RoutineTemplateEntity::class, parentColumns = ["id"], childColumns = ["previousTemplateId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("effectiveAt"), Index("templateId"), Index("revisionId"), Index("previousTemplateId"), Index(value = ["eventId"], unique = true)]
)
data class ScheduleChangeEventEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val eventId: String,
    val effectiveAt: Long,
    val previousTemplateId: String?,
    val templateId: String,
    val revisionId: String,
    val zoneId: String,
    val changeType: String
)

@Entity(
    tableName = "one_off_tasks",
    foreignKeys = [ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("dateEpochDay"), Index("tagId")]
)
data class OneOffTaskEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val title: String,
    val description: String,
    val tagId: String?,
    val startMinute: Int,
    val endMinute: Int
)

@Entity(
    tableName = "one_off_schedule_events",
    indices = [Index("taskId"), Index("dateEpochDay"), Index("effectiveAt"), Index(value = ["eventId"], unique = true)]
)
data class OneOffScheduleEventEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val eventId: String,
    val taskId: String,
    val dateEpochDay: Long,
    val effectiveAt: Long,
    val changeType: String,
    val title: String,
    val description: String,
    val tagName: String?,
    val startMinute: Int,
    val endMinute: Int,
    val zoneId: String
)

@Entity(tableName = "completion_records", indices = [Index("dateEpochDay"), Index("sourceId")])
data class CompletionRecordEntity(
    @PrimaryKey val occurrenceKey: String,
    val sourceType: String,
    val sourceId: String,
    val dateEpochDay: Long,
    val completedAt: Long,
    val titleSnapshot: String,
    val startMinuteSnapshot: Int,
    val endMinuteSnapshot: Int,
    val sourceTemplateId: String?,
    val sourceRevisionId: String?,
    val sourceEventId: String?
)

@Entity(tableName = "occurrence_exceptions", indices = [Index(value = ["templateId", "blockId", "originalEpochDay"], unique = true), Index("targetEpochDay")])
// Deliberately no cascading relation to editable blocks: commitments and their snapshots outlive them.
data class OccurrenceExceptionEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val blockId: String,
    val originalEpochDay: Long,
    val targetEpochDay: Long,
    val action: String,
    val payloadJson: String,
    val zoneId: String,
    val createdAt: Long,
    val modifiedAt: Long
)

@Entity(tableName = "occurrence_exception_events", indices = [Index("effectiveAt"), Index("exceptionId")])
data class OccurrenceExceptionEventEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val exceptionId: String,
    val effectiveAt: Long,
    @Embedded(prefix = "state_") val state: OccurrenceExceptionEntity
)

// Committed with restored Room data; removed only after DataStore has been applied.
@Entity(tableName = "pending_restore_preferences")
data class PendingRestorePreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val accent: Int,
    val widgetOpacity: Int
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val mode: String = "LOCAL",
    val authIssuer: String? = null,
    val authSubject: String? = null,
    val remoteUserId: String? = null,
    val accountTimeZone: String? = null,
    val cursor: String = "0",
    val lastSyncAt: Long? = null,
    val lastSyncError: String? = null,
    val deviceId: String,
    val updatedAt: Long
)

@Entity(tableName = "sync_outbox", indices = [Index("createdAt"), Index("aggregateKey")])
data class SyncOutboxEntity(
    @PrimaryKey val operationId: String,
    val aggregateKey: String,
    val operationType: String,
    val payloadJson: String,
    val createdAt: Long,
    val baseVersion: Int?,
    val attempts: Int = 0,
    val lastError: String? = null
)

@Entity(tableName = "sync_conflicts", indices = [Index("createdAt")])
data class SyncConflictEntity(
    @PrimaryKey val operationId: String,
    val aggregateKey: String,
    val operationType: String,
    val localPayloadJson: String,
    val remotePayloadJson: String?,
    val reason: String,
    val createdAt: Long
)

@Entity(tableName = "sync_entity_versions")
data class SyncEntityVersionEntity(
    @PrimaryKey val aggregateKey: String,
    val version: Int,
    val updatedAt: Long
)

data class TemplateRow(val id: String, val name: String, val updatedAt: Long, val blockCount: Int, val active: Boolean)
data class BlockRow(
    val blockId: String,
    val taskId: String,
    val templateId: String,
    val title: String,
    val description: String,
    val tagId: String?,
    val tagName: String?,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int
)
data class OneOffRow(val id: String, val eventId: String, val dateEpochDay: Long, val title: String, val description: String, val tagName: String?, val startMinute: Int, val endMinute: Int)

@Dao
interface AppDao {
    @Query("SELECT * FROM pending_restore_preferences WHERE id=1")
    suspend fun pendingRestorePreferences(): PendingRestorePreferencesEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePendingRestorePreferences(value: PendingRestorePreferencesEntity)
    @Query("DELETE FROM pending_restore_preferences")
    suspend fun clearPendingRestorePreferences()
    @Query("SELECT * FROM occurrence_exceptions") suspend fun exceptions(): List<OccurrenceExceptionEntity>
    @Query("DELETE FROM completion_records") suspend fun clearCompletions()
    @Query("DELETE FROM occurrence_exception_events") suspend fun clearExceptionEvents()
    @Query("DELETE FROM occurrence_exceptions") suspend fun clearExceptions()
    @Query("DELETE FROM one_off_schedule_events") suspend fun clearOneOffEvents()
    @Query("DELETE FROM one_off_tasks") suspend fun clearOneOffTasks()
    @Query("DELETE FROM schedule_change_events") suspend fun clearScheduleEvents()
    @Query("DELETE FROM active_template_selection") suspend fun clearActiveSelection()
    @Query("DELETE FROM schedule_blocks") suspend fun clearScheduleBlocks()
    @Query("DELETE FROM task_definitions") suspend fun clearTaskDefinitions()
    @Query("DELETE FROM template_revisions") suspend fun clearTemplateRevisions()
    @Query("DELETE FROM routine_templates") suspend fun clearTemplates()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceTemplates(rows: List<RoutineTemplateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceTags(rows: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceTaskDefinitions(rows: List<TaskDefinitionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceScheduleBlocks(rows: List<ScheduleBlockEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceTemplateRevisions(rows: List<TemplateRevisionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceScheduleEvents(rows: List<ScheduleChangeEventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceOneOffTasks(rows: List<OneOffTaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceOneOffEvents(rows: List<OneOffScheduleEventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceCompletions(rows: List<CompletionRecordEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceExceptions(rows: List<OccurrenceExceptionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceExceptionEvents(rows: List<OccurrenceExceptionEventEntity>)
    @Query("SELECT * FROM occurrence_exceptions WHERE templateId=:templateId AND blockId=:blockId AND originalEpochDay=:day")
    suspend fun exceptionForOrigin(templateId: String, blockId: String, day: Long): OccurrenceExceptionEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExceptionEvent(event: OccurrenceExceptionEventEntity)
    @Query("SELECT COUNT(*) FROM occurrence_exception_events WHERE exceptionId=:exceptionId") suspend fun exceptionEventCount(exceptionId: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertException(state: OccurrenceExceptionEntity)
    @Query("SELECT * FROM occurrence_exception_events WHERE effectiveAt < :before ORDER BY effectiveAt, sequence")
    suspend fun exceptionEventsBefore(before: Long): List<OccurrenceExceptionEventEntity>
    @Query("SELECT t.id, t.name, t.updatedAt, (SELECT COUNT(*) FROM schedule_blocks b WHERE b.templateId=t.id) AS blockCount, (SELECT templateId FROM active_template_selection WHERE singletonId=1)=t.id AS active FROM routine_templates t ORDER BY t.name COLLATE NOCASE")
    fun observeTemplates(): Flow<List<TemplateRow>>

    @Query("SELECT * FROM routine_templates WHERE id=:id") suspend fun template(id: String): RoutineTemplateEntity?
    @Query("SELECT * FROM schedule_blocks WHERE id=:id") suspend fun block(id: String): ScheduleBlockEntity?
    @Query("SELECT * FROM task_definitions WHERE id=:id") suspend fun task(id: String): TaskDefinitionEntity?
    @Query("SELECT * FROM active_template_selection WHERE singletonId=1") suspend fun active(): ActiveSelectionEntity?
    @Query("SELECT * FROM active_template_selection WHERE singletonId=1") fun observeActive(): Flow<ActiveSelectionEntity?>
    @Query("SELECT * FROM schedule_change_events WHERE revisionId=:revisionId ORDER BY sequence DESC LIMIT 1")
    suspend fun eventForRevision(revisionId: String): ScheduleChangeEventEntity?
    @Query("SELECT COUNT(*) FROM schedule_change_events") suspend fun eventCount(): Int
    @Query("SELECT changeType FROM schedule_change_events ORDER BY sequence") suspend fun eventTypes(): List<String>
    @Query("SELECT COUNT(*) FROM template_revisions") suspend fun revisionCount(): Int
    @Query("SELECT id FROM routine_templates ORDER BY name") suspend fun templateIds(): List<String>
    @Query("SELECT COUNT(*) FROM schedule_blocks") suspend fun blockCount(): Int
    @Query("SELECT COUNT(*) FROM task_definitions") suspend fun taskCount(): Int
    @Query("SELECT COUNT(*) FROM completion_records") suspend fun completionCount(): Int
    @Query("SELECT e.taskId AS id, e.eventId, e.dateEpochDay, e.title, e.description, e.tagName, e.startMinute, e.endMinute FROM one_off_schedule_events e WHERE e.changeType='UPSERT' AND e.sequence=(SELECT MAX(x.sequence) FROM one_off_schedule_events x WHERE x.taskId=e.taskId) AND e.dateEpochDay=:dateEpochDay ORDER BY e.startMinute")
    fun observeOneOffEvents(dateEpochDay: Long): Flow<List<OneOffRow>>
    @Query("SELECT e.taskId AS id, e.eventId, e.dateEpochDay, e.title, e.description, e.tagName, e.startMinute, e.endMinute FROM one_off_schedule_events e WHERE e.changeType='UPSERT' AND e.sequence=(SELECT MAX(x.sequence) FROM one_off_schedule_events x WHERE x.taskId=e.taskId) AND e.dateEpochDay=:dateEpochDay ORDER BY e.startMinute")
    suspend fun currentOneOffEvents(dateEpochDay: Long): List<OneOffRow>
    @Query("SELECT * FROM completion_records WHERE dateEpochDay=:dateEpochDay")
    fun observeCompletions(dateEpochDay: Long): Flow<List<CompletionRecordEntity>>
    @Query("SELECT * FROM completion_records WHERE dateEpochDay=:dateEpochDay")
    suspend fun completions(dateEpochDay: Long): List<CompletionRecordEntity>
    @Query("SELECT * FROM completion_records WHERE occurrenceKey=:key")
    suspend fun completion(key: String): CompletionRecordEntity?
    @Query("SELECT * FROM schedule_change_events WHERE effectiveAt < :before ORDER BY effectiveAt, sequence")
    suspend fun scheduleEventsBefore(before: Long): List<ScheduleChangeEventEntity>
    @Query("SELECT * FROM template_revisions WHERE id IN (:ids)")
    suspend fun revisions(ids: List<String>): List<TemplateRevisionEntity>
    @Query("SELECT * FROM one_off_schedule_events WHERE effectiveAt < :before ORDER BY effectiveAt, sequence")
    suspend fun oneOffEventsBefore(before: Long): List<OneOffScheduleEventEntity>
    @Query("SELECT * FROM one_off_tasks WHERE id=:id") suspend fun oneOff(id: String): OneOffTaskEntity?
    @Query("SELECT id FROM one_off_tasks") suspend fun oneOffTaskIds(): List<String>
    @Query("SELECT name FROM tags WHERE id=:id") suspend fun tagName(id: String): String?
    @Insert suspend fun insertOneOffEvent(event: OneOffScheduleEventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertOneOff(task: OneOffTaskEntity)
    @Query("DELETE FROM one_off_tasks WHERE id=:id") suspend fun deleteOneOff(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCompletion(record: CompletionRecordEntity)
    @Query("DELETE FROM completion_records WHERE occurrenceKey=:key") suspend fun deleteCompletion(key: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTemplate(template: RoutineTemplateEntity)
    @Query("UPDATE routine_templates SET name=:name, updatedAt=:now WHERE id=:id") suspend fun renameTemplate(id: String, name: String, now: Long)
    @Query("UPDATE routine_templates SET updatedAt=:now WHERE id=:id") suspend fun updateTemplateTimestamp(id: String, now: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTag(tag: TagEntity)
    @Query("SELECT * FROM tags WHERE name=:name COLLATE NOCASE LIMIT 1") suspend fun tagByName(name: String): TagEntity?
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE") fun observeTags(): Flow<List<TagEntity>>
    @Query("SELECT b.id AS blockId, d.id AS taskId, b.templateId, d.title, d.description, d.tagId, g.name AS tagName, b.daysMask, b.startMinute, b.endMinute FROM schedule_blocks b JOIN task_definitions d ON d.id=b.taskId LEFT JOIN tags g ON g.id=d.tagId WHERE b.templateId=:templateId ORDER BY b.startMinute")
    fun observeBlocks(templateId: String): Flow<List<BlockRow>>
    @Query("SELECT b.id AS blockId, d.id AS taskId, b.templateId, d.title, d.description, d.tagId, g.name AS tagName, b.daysMask, b.startMinute, b.endMinute FROM schedule_blocks b JOIN task_definitions d ON d.id=b.taskId LEFT JOIN tags g ON g.id=d.tagId WHERE b.templateId=:templateId ORDER BY b.startMinute")
    suspend fun blocks(templateId: String): List<BlockRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTask(task: TaskDefinitionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBlock(block: ScheduleBlockEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTask(task: TaskDefinitionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBlock(block: ScheduleBlockEntity)
    @Query("DELETE FROM schedule_blocks WHERE id=:blockId AND templateId=:templateId") suspend fun deleteBlock(blockId: String, templateId: String)
    @Insert suspend fun insertRevision(revision: TemplateRevisionEntity)
    @Insert suspend fun insertEvent(event: ScheduleChangeEventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setActive(selection: ActiveSelectionEntity)
    @Query("SELECT * FROM sync_state WHERE id=1") suspend fun syncState(): SyncStateEntity?
    @Query("SELECT * FROM sync_state WHERE id=1") fun observeSyncState(): Flow<SyncStateEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSyncState(state: SyncStateEntity)
    @Query("SELECT version FROM sync_entity_versions WHERE aggregateKey=:aggregateKey") suspend fun syncEntityVersion(aggregateKey: String): Int?
    @Query("DELETE FROM sync_entity_versions") suspend fun clearSyncEntityVersions()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSyncEntityVersion(version: SyncEntityVersionEntity)
    @Query("UPDATE sync_outbox SET baseVersion=:version WHERE aggregateKey=:aggregateKey") suspend fun advancePendingSyncVersions(aggregateKey: String, version: Int)
    @Query("SELECT o.* FROM sync_outbox o WHERE NOT EXISTS (SELECT 1 FROM sync_conflicts c WHERE c.aggregateKey=o.aggregateKey) ORDER BY o.createdAt, o.operationId LIMIT :limit") suspend fun pendingSyncOperations(limit: Int): List<SyncOutboxEntity>
    @Query("SELECT MAX(createdAt) FROM sync_outbox") suspend fun latestSyncOutboxTime(): Long?
    @Query("SELECT COUNT(*) FROM sync_outbox") suspend fun syncOutboxCount(): Int
    @Query("SELECT COUNT(*) FROM sync_conflicts") suspend fun syncConflictCount(): Int
    @Query("SELECT COUNT(*) FROM sync_outbox") fun observeSyncOutboxCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM sync_conflicts") fun observeSyncConflictCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun enqueueSyncOperation(operation: SyncOutboxEntity)
    @Query("UPDATE sync_outbox SET attempts=attempts+1, lastError=:error WHERE operationId=:operationId") suspend fun noteSyncAttempt(operationId: String, error: String?)
    @Query("DELETE FROM sync_outbox WHERE operationId=:operationId") suspend fun acknowledgeSyncOperation(operationId: String)
    @Query("SELECT * FROM sync_outbox WHERE operationId=:operationId") suspend fun syncOperation(operationId: String): SyncOutboxEntity?
    @Query("UPDATE sync_outbox SET baseVersion=:version, attempts=0, lastError=NULL WHERE operationId=:operationId") suspend fun rebaseSyncOperation(operationId: String, version: Int)
    @Query("DELETE FROM sync_outbox WHERE aggregateKey=:aggregateKey") suspend fun discardSyncAggregate(aggregateKey: String)
    @Query("DELETE FROM sync_conflicts WHERE aggregateKey=:aggregateKey") suspend fun discardConflictsForAggregate(aggregateKey: String)
    @Query("SELECT * FROM sync_conflicts WHERE operationId=:operationId") suspend fun syncConflict(operationId: String): SyncConflictEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSyncConflict(conflict: SyncConflictEntity)
    @Query("SELECT * FROM sync_conflicts ORDER BY createdAt, operationId") fun observeSyncConflicts(): Flow<List<SyncConflictEntity>>
    @Query("SELECT * FROM sync_conflicts ORDER BY createdAt, operationId") suspend fun syncConflicts(): List<SyncConflictEntity>
    @Query("DELETE FROM sync_conflicts WHERE operationId=:operationId") suspend fun resolveSyncConflict(operationId: String)
}

@Database(
    entities = [RoutineTemplateEntity::class, TagEntity::class, TaskDefinitionEntity::class, ScheduleBlockEntity::class, ActiveSelectionEntity::class, TemplateRevisionEntity::class, ScheduleChangeEventEntity::class, OneOffTaskEntity::class, OneOffScheduleEventEntity::class, CompletionRecordEntity::class, OccurrenceExceptionEntity::class, OccurrenceExceptionEventEntity::class, PendingRestorePreferencesEntity::class, SyncStateEntity::class, SyncOutboxEntity::class, SyncConflictEntity::class, SyncEntityVersionEntity::class],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_state ADD COLUMN accountTimeZone TEXT")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_state ADD COLUMN lastSyncAt INTEGER")
                db.execSQL("ALTER TABLE sync_state ADD COLUMN lastSyncError TEXT")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_entity_versions (aggregateKey TEXT NOT NULL PRIMARY KEY, version INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (id INTEGER NOT NULL PRIMARY KEY, mode TEXT NOT NULL, authIssuer TEXT, authSubject TEXT, remoteUserId TEXT, cursor TEXT NOT NULL, deviceId TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox (operationId TEXT NOT NULL PRIMARY KEY, aggregateKey TEXT NOT NULL, operationType TEXT NOT NULL, payloadJson TEXT NOT NULL, createdAt INTEGER NOT NULL, baseVersion INTEGER, attempts INTEGER NOT NULL, lastError TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_createdAt ON sync_outbox(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_aggregateKey ON sync_outbox(aggregateKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_conflicts (operationId TEXT NOT NULL PRIMARY KEY, aggregateKey TEXT NOT NULL, operationType TEXT NOT NULL, localPayloadJson TEXT NOT NULL, remotePayloadJson TEXT, reason TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_createdAt ON sync_conflicts(createdAt)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_restore_preferences (id INTEGER NOT NULL PRIMARY KEY, accent INTEGER NOT NULL, widgetOpacity INTEGER NOT NULL)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS occurrence_exceptions (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, blockId TEXT NOT NULL, originalEpochDay INTEGER NOT NULL, targetEpochDay INTEGER NOT NULL, action TEXT NOT NULL, payloadJson TEXT NOT NULL, zoneId TEXT NOT NULL, createdAt INTEGER NOT NULL, modifiedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_occurrence_exceptions_templateId_blockId_originalEpochDay ON occurrence_exceptions(templateId, blockId, originalEpochDay)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_occurrence_exceptions_targetEpochDay ON occurrence_exceptions(targetEpochDay)")
                db.execSQL("CREATE TABLE IF NOT EXISTS occurrence_exception_events (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, exceptionId TEXT NOT NULL, effectiveAt INTEGER NOT NULL, state_id TEXT NOT NULL, state_templateId TEXT NOT NULL, state_blockId TEXT NOT NULL, state_originalEpochDay INTEGER NOT NULL, state_targetEpochDay INTEGER NOT NULL, state_action TEXT NOT NULL, state_payloadJson TEXT NOT NULL, state_zoneId TEXT NOT NULL, state_createdAt INTEGER NOT NULL, state_modifiedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_occurrence_exception_events_effectiveAt ON occurrence_exception_events(effectiveAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_occurrence_exception_events_exceptionId ON occurrence_exception_events(exceptionId)")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        @Volatile private var instanceName: String? = null
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS one_off_tasks (id TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, tagId TEXT, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_one_off_tasks_dateEpochDay ON one_off_tasks(dateEpochDay)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_one_off_tasks_tagId ON one_off_tasks(tagId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS completion_records (occurrenceKey TEXT NOT NULL, sourceType TEXT NOT NULL, sourceId TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, completedAt INTEGER NOT NULL, titleSnapshot TEXT NOT NULL, startMinuteSnapshot INTEGER NOT NULL, endMinuteSnapshot INTEGER NOT NULL, sourceTemplateId TEXT, sourceRevisionId TEXT, PRIMARY KEY(occurrenceKey))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_completion_records_dateEpochDay ON completion_records(dateEpochDay)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_completion_records_sourceId ON completion_records(sourceId)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS one_off_schedule_events (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, eventId TEXT NOT NULL, taskId TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, effectiveAt INTEGER NOT NULL, changeType TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, tagName TEXT, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, zoneId TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_one_off_schedule_events_taskId ON one_off_schedule_events(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_one_off_schedule_events_dateEpochDay ON one_off_schedule_events(dateEpochDay)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_one_off_schedule_events_effectiveAt ON one_off_schedule_events(effectiveAt)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_one_off_schedule_events_eventId ON one_off_schedule_events(eventId)")
                db.execSQL("ALTER TABLE completion_records ADD COLUMN sourceEventId TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_schedule_change_events_eventId ON schedule_change_events(eventId)")
                // Existing one-off rows have no trustworthy creation timestamp. Seed their current state at the migration cutover;
                // older dates remain represented only by completion snapshots rather than fabricated schedule history.
                db.execSQL("INSERT INTO one_off_schedule_events (eventId, taskId, dateEpochDay, effectiveAt, changeType, title, description, tagName, startMinute, endMinute, zoneId) SELECT lower(hex(randomblob(16))), o.id, o.dateEpochDay, ?, 'UPSERT', o.title, o.description, t.name, o.startMinute, o.endMinute, ? FROM one_off_tasks o LEFT JOIN tags t ON t.id=o.tagId", arrayOf(System.currentTimeMillis(), java.time.ZoneId.systemDefault().id))
            }
        }
        fun activeWorkspace(context: Context): String = context.getSharedPreferences("workspace_selection", Context.MODE_PRIVATE)
            .getString("workspace", "LOCAL") ?: "LOCAL"

        fun selectLocalWorkspace(context: Context) = selectWorkspace(context, "LOCAL")

        fun selectCloudWorkspace(context: Context, remoteUserId: String) {
            require(remoteUserId.isNotBlank())
            selectWorkspace(context, "CLOUD:$remoteUserId")
        }

        private fun selectWorkspace(context: Context, workspace: String) {
            synchronized(this) {
                if (activeWorkspace(context) == workspace) return
                instance?.close()
                instance = null
                instanceName = null
                check(context.getSharedPreferences("workspace_selection", Context.MODE_PRIVATE)
                    .edit().putString("workspace", workspace).commit()) { "No se pudo cambiar de espacio de datos." }
            }
        }

        private fun databaseName(context: Context): String {
            val workspace = activeWorkspace(context)
            if (workspace == "LOCAL") return "blocknize.db"
            val userId = workspace.removePrefix("CLOUD:")
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(userId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            return "blocknize-cloud-$digest.db"
        }

        fun get(context: Context): AppDatabase {
            val appContext = context.applicationContext
            val name = databaseName(appContext)
            return instance?.takeIf { instanceName == name } ?: synchronized(this) {
                if (instance != null && instanceName != name) instance?.close()
                if (instance == null || instanceName != name) {
                    instance = Room.databaseBuilder(appContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TRIGGER active_selection_insert_guard BEFORE INSERT ON active_template_selection WHEN NEW.singletonId != 1 BEGIN SELECT RAISE(ABORT, 'active selection id must be 1'); END")
                        db.execSQL("CREATE TRIGGER active_selection_update_guard BEFORE UPDATE ON active_template_selection WHEN NEW.singletonId != 1 BEGIN SELECT RAISE(ABORT, 'active selection id must be 1'); END")
                    }
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("INSERT OR IGNORE INTO sync_state (id, mode, authIssuer, authSubject, remoteUserId, cursor, deviceId, updatedAt) VALUES (1, 'LOCAL', NULL, NULL, NULL, '0', lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(6))), strftime('%s','now') * 1000)")
                    }
                }).build().also { instanceName = name }
                }
                requireNotNull(instance)
            }
        }
    }
}
