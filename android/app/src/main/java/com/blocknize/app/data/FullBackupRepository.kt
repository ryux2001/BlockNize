package com.blocknize.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Logical, versioned backup. Table and column names always come from the installed schema. */
class FullBackupRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.get(context),
    private val appearance: AppearanceStorage = AccentStore(context)
) {
    companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
        const val MAX_ROWS = 100_000
        // Dependency order. Historical references intentionally have no extra foreign keys.
        internal val TABLES = listOf("routine_templates", "tags", "task_definitions", "schedule_blocks",
            "template_revisions", "schedule_change_events", "active_template_selection", "one_off_tasks",
            "one_off_schedule_events", "completion_records", "occurrence_exceptions", "occurrence_exception_events")

        fun read(input: InputStream): String {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "La copia supera el límite de 32 MB." }
                output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            require(bytes.size <= MAX_BYTES) { "La copia supera el límite de 32 MB." }
            return bytes.toString(Charsets.UTF_8)
        }
    }

    suspend fun export(): String = withContext(Dispatchers.IO) {
        LocalDataAccess.exclusive {
            recoverPendingLocked()
            val preferences = appearance.snapshot()
            val data = database.withTransaction {
                val sql = database.openHelper.writableDatabase
                JSONObject().apply {
                    TABLES.forEach { table ->
                        val rows = JSONArray()
                        sql.query("SELECT * FROM `$table` ORDER BY ${primaryKey(sql, table)}").use { cursor ->
                            while (cursor.moveToNext()) {
                                require(rows.length() < MAX_ROWS) { "Hay demasiados registros para esta versión del backup." }
                                rows.put(JSONObject().apply {
                                    cursor.columnNames.forEachIndexed { index, name ->
                                        put(name, when (cursor.getType(index)) {
                                            android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                            android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                                            else -> error("Tipo de dato no compatible en $table.")
                                        })
                                    }
                                })
                            }
                        }
                        put(table, rows)
                    }
                }
            }
            val json = JSONObject().put("format", "blocknize-backup").put("formatVersion", 1)
                .put("databaseVersion", 5).put("appVersion", "1.0")
                .put("exportedAt", Instant.now().toString()).put("data", data)
                .put("preferences", JSONObject().put("accent", preferences.accent)
                    .put("widgetOpacity", preferences.widgetOpacity)).toString()
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "La copia supera el límite de 32 MB." }
            validateInStaging(decode(json))
            json
        }
    }

    suspend fun inspect(json: String): BackupSummary = withContext(Dispatchers.IO) {
        val root = decode(json)
        validateInStaging(root)
        val data = root.getJSONObject("data")
        BackupSummary(root.getString("exportedAt"), data.getJSONArray("routine_templates").length(),
            data.getJSONArray("one_off_tasks").length(), data.getJSONArray("completion_records").length(),
            data.getJSONArray("occurrence_exceptions").length(),
            listOf("schedule_change_events", "one_off_schedule_events", "occurrence_exception_events")
                .sumOf { data.getJSONArray(it).length() })
    }

    suspend fun restore(json: String) = withContext(Dispatchers.IO) {
        // Complete validation (including SQLite constraints) before touching the live database.
        val root = decode(json)
        validateInStaging(root)
        LocalDataAccess.exclusive {
            recoverPendingLocked()
            val preferences = preferences(root)
            database.withTransaction {
                val sql = database.openHelper.writableDatabase
                TABLES.asReversed().forEach { sql.execSQL("DELETE FROM `$it`") }
                insertRows(sql, root.getJSONObject("data"))
                database.dao().savePendingRestorePreferences(PendingRestorePreferencesEntity(
                    accent = preferences.accent, widgetOpacity = preferences.widgetOpacity))
            }
            // If this fails or the process dies, the marker above survives and startup retries it.
            try { recoverPendingLocked() }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) { throw RestorePreferencesPendingException(error) }
        }
    }

    suspend fun recoverPending() = withContext(Dispatchers.IO) {
        LocalDataAccess.exclusive { recoverPendingLocked() }
    }

    /** Caller must own LocalDataAccess; exposed for the application's write coordinator. */
    internal suspend fun recoverPendingLocked() {
        val pending = database.dao().pendingRestorePreferences() ?: return
        appearance.restore(AppearancePreferences(pending.accent, pending.widgetOpacity))
        database.dao().clearPendingRestorePreferences()
    }

    private fun decode(json: String): JSONObject {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "La copia supera el límite de 32 MB." }
        val root = JSONObject(json)
        require(root.getString("format") == "blocknize-backup") { "No es una copia completa de BlockNize." }
        require(root.get("formatVersion") == 1 && root.get("databaseVersion") == 5) {
            "Versión de copia no compatible."
        }
        Instant.parse(root.getString("exportedAt"))
        preferences(root)
        val data = root.getJSONObject("data")
        require(data.keys().asSequence().toSet() == TABLES.toSet()) { "La copia no contiene las tablas esperadas." }
        TABLES.forEach { require(data.getJSONArray(it).length() <= MAX_ROWS) { "Demasiados registros en $it." } }
        return root
    }

    private fun preferences(root: JSONObject): AppearancePreferences {
        val prefs = root.getJSONObject("preferences")
        val accent = strictLong(prefs.get("accent"))
        val opacity = strictLong(prefs.get("widgetOpacity"))
        require(accent in Int.MIN_VALUE..Int.MAX_VALUE && (accent.toInt() ushr 24) == 255)
        require(opacity in 0..100) { "Opacidad de widget inválida." }
        return AppearancePreferences(accent.toInt(), opacity.toInt())
    }

    private suspend fun validateInStaging(root: JSONObject) {
        val staging = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            staging.withTransaction {
                val sql = staging.openHelper.writableDatabase
                insertRows(sql, root.getJSONObject("data"))
                sql.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Referencias inválidas en la copia." } }
                sql.query("SELECT singletonId FROM active_template_selection").use {
                    while (it.moveToNext()) require(it.getInt(0) == 1) { "Selección activa inválida." }
                }
                sql.query("SELECT 1 FROM schedule_blocks b JOIN task_definitions t ON t.id=b.taskId WHERE b.templateId != t.templateId UNION ALL SELECT 1 FROM active_template_selection a JOIN template_revisions r ON r.id=a.revisionId WHERE a.templateId != r.templateId UNION ALL SELECT 1 FROM schedule_change_events e JOIN template_revisions r ON r.id=e.revisionId WHERE e.templateId != r.templateId").use {
                    require(!it.moveToFirst()) { "Las relaciones de plantilla no son coherentes." }
                }
            }
        } finally { staging.close() }
    }

    private fun insertRows(sql: SupportSQLiteDatabase, data: JSONObject) {
        TABLES.forEach { table ->
            val columns = columns(sql, table)
            val rows = data.getJSONArray(table)
            val statement = sql.compileStatement("INSERT INTO `$table` (${columns.joinToString { "`${it.name}`" }}) VALUES (${columns.joinToString { "?" }})")
            try {
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    require(row.keys().asSequence().toSet() == columns.map { it.name }.toSet()) { "Columnas inválidas en $table." }
                    validateRow(table, row)
                    statement.clearBindings()
                    columns.forEachIndexed { columnIndex, column ->
                        val value = row.get(column.name)
                        when {
                            value == JSONObject.NULL -> {
                                require(!column.required) { "Falta ${column.name} en $table." }
                                statement.bindNull(columnIndex + 1)
                            }
                            column.type == "INTEGER" -> statement.bindLong(columnIndex + 1, strictLong(value))
                            column.type == "TEXT" && value is String -> statement.bindString(columnIndex + 1, value)
                            else -> error("Tipo inválido en $table.${column.name}.")
                        }
                    }
                    statement.executeInsert()
                }
            } finally { statement.close() }
        }
    }

    private fun validateRow(table: String, row: JSONObject) {
        row.keys().forEach { key ->
            if (row.isNull(key)) return@forEach
            when {
                key == "sequence" -> require(strictLong(row.get(key)) > 0) { "Secuencia histórica inválida." }
                key.endsWith("EpochDay") -> LocalDate.ofEpochDay(strictLong(row.get(key)))
                key.endsWith("zoneId", ignoreCase = true) -> ZoneId.of(row.getString(key))
                key == "id" || key == "state_id" || key == "occurrenceKey" || key.endsWith("Id") -> require(row.getString(key).isNotBlank())
                key.endsWith("payloadJson", ignoreCase = true) -> JSONObject(row.getString(key))
            }
        }
        fun interval(start: String, end: String) {
            val a = strictLong(row.get(start)); val b = strictLong(row.get(end))
            require(a in 0..1439 && b in 1..1440 && a < b) { "Horario inválido en $table." }
        }
        if (row.has("startMinute")) interval("startMinute", "endMinute")
        if (row.has("startMinuteSnapshot")) interval("startMinuteSnapshot", "endMinuteSnapshot")
        if (row.has("daysMask")) require(strictLong(row.get("daysMask")) in 1..127)
        if (row.has("action")) validateException(row, "")
        if (row.has("state_action")) {
            validateException(row, "state_")
            require(row.getString("exceptionId") == row.getString("state_id"))
        }
        if (table == "one_off_schedule_events") require(row.getString("changeType") in setOf("UPSERT", "DELETE"))
        if (table == "schedule_change_events") require(row.getString("changeType") in setOf("ACTIVATION", "ACTIVE_EDIT"))
        if (table == "completion_records") require(row.getString("sourceType") in setOf("WEEKLY", "ONE_OFF"))
        if (table == "template_revisions") {
            val payload = JSONObject(row.getString("payloadJson"))
            require(payload.getString("templateId") == row.getString("templateId"))
            val blocks = payload.getJSONArray("blocks")
            for (i in 0 until blocks.length()) {
                val block = blocks.getJSONObject(i)
                require(block.getString("blockId").isNotBlank())
                require(block.getString("title").isNotBlank())
                val start = strictLong(block.get("startMinute")); val end = strictLong(block.get("endMinute"))
                require(start in 0..1439 && end in 1..1440 && start < end)
                require(strictLong(block.get("daysMask")) in 1..127)
            }
        }
    }

    private fun validateException(row: JSONObject, prefix: String) {
        val action = row.getString(prefix + "action")
        require(action in setOf("SKIP", "REPLACE", "RESTORE"))
        val payload = JSONObject(row.getString(prefix + "payloadJson"))
        val snapshots = buildList {
            add(payload.getJSONObject("original"))
            if (action == "REPLACE") add(payload.getJSONObject("replacement"))
        }
        snapshots.forEach {
            require(it.getString("title").isNotBlank())
            val start = strictLong(it.get("startMinute")); val end = strictLong(it.get("endMinute"))
            require(start in 0..1439 && end in 1..1440 && start < end)
        }
    }

    private data class Column(val name: String, val type: String, val required: Boolean, val keyOrder: Int)
    private fun columns(sql: SupportSQLiteDatabase, table: String): List<Column> = buildList {
        sql.query("PRAGMA table_info(`$table`)").use {
            while (it.moveToNext()) add(Column(it.getString(1), it.getString(2), it.getInt(3) != 0, it.getInt(5)))
        }
    }
    private fun primaryKey(sql: SupportSQLiteDatabase, table: String): String =
        columns(sql, table).filter { it.keyOrder > 0 }.sortedBy { it.keyOrder }.joinToString { "`${it.name}`" }

    private fun strictLong(value: Any): Long {
        require(value is Int || value is Long) { "Se esperaba un número entero." }
        return (value as Number).toLong()
    }
}

data class BackupSummary(val exportedAt: String, val templates: Int, val oneOffTasks: Int,
    val completions: Int, val exceptions: Int, val events: Int)

class RestorePreferencesPendingException(cause: Throwable) : Exception("Restored agenda; preferences pending", cause)
