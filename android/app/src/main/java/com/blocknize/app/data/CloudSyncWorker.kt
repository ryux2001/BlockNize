package com.blocknize.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.room.withTransaction
import com.blocknize.app.widget.WidgetRefreshWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object CloudSyncScheduler {
    private const val UNIQUE_NAME = "blocknize-cloud-sync"
    private const val PERIODIC_NAME = "blocknize-cloud-sync-periodic"

    fun request(context: Context) {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, androidx.work.ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun pausePeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
    }
}

class CloudSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        var workspaceForFailure: String? = null
        return try {
        val configuration = SyncConfigurationStore(applicationContext).configuration
            .first()
        if (configuration.apiBaseUrl.isBlank() || configuration.neonAuthBaseUrl.isBlank() || configuration.authOrigin.isBlank()) return Result.failure()
        val initialWorkspace = AppDatabase.activeWorkspace(applicationContext)
        workspaceForFailure = initialWorkspace
        val initialDatabase = AppDatabase.get(applicationContext)
        val initialState = initialDatabase.dao().syncState() ?: return Result.success()
        if (initialState.mode != "CLOUD") return Result.success()
        val vault = SessionVault(applicationContext)
        val auth = NeonAuthClient(vault)
        val api = BlockNizeApiClient(auth)
        val identity = auth.identity(configuration.neonAuthBaseUrl, configuration.authOrigin) ?: return Result.retry()
        if (identity.issuer != initialState.authIssuer || identity.subject != initialState.authSubject) return Result.failure()
        val profile = api.request(configuration.apiBaseUrl, configuration.neonAuthBaseUrl, "/me", authOrigin = configuration.authOrigin).json()
        if (profile.optString("id") != initialState.remoteUserId) return Result.failure()

        var workspaceChanged = false
        LocalDataAccess.exclusive {
            if (AppDatabase.activeWorkspace(applicationContext) != initialWorkspace) {
                workspaceChanged = true
                return@exclusive
            }
            val database = AppDatabase.get(applicationContext)
            val dao = database.dao()
            val state = dao.syncState() ?: return@exclusive
            if (state.mode != "CLOUD" || state.authIssuer != identity.issuer || state.authSubject != identity.subject || state.remoteUserId != profile.optString("id")) {
                workspaceChanged = true
                return@exclusive
            }
            repeat(8) {
                val pending = dao.pendingSyncOperations(1).firstOrNull() ?: return@repeat
                try {
                    val version = dispatch(api, configuration, pending)
                    database.withTransaction {
                        dao.acknowledgeSyncOperation(pending.operationId)
                        if (version != null) {
                            dao.saveSyncEntityVersion(SyncEntityVersionEntity(pending.aggregateKey, version, System.currentTimeMillis()))
                            dao.advancePendingSyncVersions(pending.aggregateKey, version)
                        }
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: SyncHttpException) {
                    when {
                        error.status == 409 || error.status == 412 -> {
                            dao.noteSyncAttempt(pending.operationId, "Conflicto de versión (${error.status}).")
                            dao.saveSyncConflict(SyncConflictEntity(pending.operationId, pending.aggregateKey,
                                pending.operationType, pending.payloadJson, error.responseBody,
                                "El estado de la cuenta cambió antes de aplicar esta edición.", System.currentTimeMillis()))
                        }
                        error.status in 400..499 && error.status != 408 && error.status != 429 -> {
                            dao.noteSyncAttempt(pending.operationId, error.message ?: "Operación rechazada.")
                            dao.saveSyncConflict(SyncConflictEntity(pending.operationId, pending.aggregateKey,
                                pending.operationType, pending.payloadJson, error.responseBody,
                                error.message ?: "El servidor rechazó la operación.", System.currentTimeMillis()))
                        }
                        else -> {
                            dao.noteSyncAttempt(pending.operationId, "Error HTTP ${error.status}.")
                            throw error
                        }
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    dao.noteSyncAttempt(pending.operationId, error.message?.take(240))
                    throw error
                }
            }

            // The server feed contains commit markers, so a consistent full snapshot is applied
            // only after the local outbox is clear. A conflict keeps its local proposal intact.
            if (dao.syncOutboxCount() == 0 && dao.syncConflictCount() == 0) {
                val remoteState = api.request(configuration.apiBaseUrl, configuration.neonAuthBaseUrl, "/sync/state", authOrigin = configuration.authOrigin).json()
                val serverCursor = remoteState.optString("cursor", "0")
                if (serverCursor != state.cursor) {
                    val snapshot = api.request(configuration.apiBaseUrl, configuration.neonAuthBaseUrl, "/sync/bootstrap", authOrigin = configuration.authOrigin).json()
                    require(snapshot.optInt("protocolVersion") == 1) { "La versión de sync del servidor no es compatible." }
                    applySnapshot(database, dao, snapshot)
                    dao.saveSyncState(state.copy(cursor = snapshot.getString("cursor"),
                        accountTimeZone = snapshot.optString("timeZone").takeIf(String::isNotBlank) ?: state.accountTimeZone,
                        updatedAt = System.currentTimeMillis()))
                    WidgetRefreshWorker.request(applicationContext)
                }
            }
            if (!workspaceChanged) {
                val latestState = dao.syncState() ?: state
                dao.saveSyncState(latestState.copy(lastSyncAt = System.currentTimeMillis(), lastSyncError = null))
            }
        }
        if (workspaceChanged) Result.success() else if (AppDatabase.get(applicationContext).dao().pendingSyncOperations(1).isNotEmpty()) Result.retry() else Result.success()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        recordSyncFailure(workspaceForFailure, error)
        Result.retry()
    }
    }

    private suspend fun recordSyncFailure(workspace: String?, error: Exception) {
        if (workspace == null) return
        val message = when (error) {
            is SyncHttpException -> if (error.status == 401) {
                "La sesión no está disponible. Cierra sesión y vuelve a iniciar para sincronizar."
            } else {
                "El servidor no pudo sincronizar los cambios (HTTP ${error.status}). Se reintentará automáticamente."
            }
            else -> "No se pudo contactar con BlockNize. Los cambios siguen guardados y se reintentará automáticamente."
        }
        try {
            LocalDataAccess.exclusive {
                if (AppDatabase.activeWorkspace(applicationContext) != workspace) return@exclusive
                val dao = AppDatabase.get(applicationContext).dao()
                val state = dao.syncState() ?: return@exclusive
                if (state.mode == "CLOUD") dao.saveSyncState(state.copy(lastSyncError = message, updatedAt = System.currentTimeMillis()))
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // A transient database error must not mask the WorkManager retry.
        }
    }

    private suspend fun dispatch(api: BlockNizeApiClient, config: SyncConfiguration, operation: SyncOutboxEntity): Int? {
        val payload = JSONObject(operation.payloadJson)
        val deviceId = payload.optString("_syncDeviceId").takeIf(String::isNotBlank)
        // Keep the original idempotency key for outbox rows written by older builds.
        val key = deviceId?.let { "bn-sync:$it:${operation.operationId}" } ?: operation.operationId
        fun headers(version: Int? = operation.baseVersion): Map<String, String> = buildMap {
            put("Idempotency-Key", key)
            if (version != null || operation.operationType in setOf("template.rename", "block.upsert", "block.delete", "selection.activate", "one-off.upsert", "one-off.delete", "exception")) {
                put("If-Match", (version ?: 0).toString())
            }
        }
        fun pathId(key: String) = requireNotNull(payload.optString(key).takeIf(String::isNotBlank)) { "Falta '$key' en la operación." }
        fun nullableString(key: String): String? = if (payload.isNull(key)) null else payload.optString(key).takeIf(String::isNotBlank)
        fun resultVersion(response: ApiResponse, fallback: Int? = operation.baseVersion): Int? =
            response.header("ETag")?.trim()?.trim('"')?.toIntOrNull() ?: runCatching { response.json().optInt("version", fallback ?: 0) }.getOrNull()

        suspend fun send(path: String, method: String = "GET", body: JSONObject? = null, extra: Map<String, String> = emptyMap()) =
            api.request(config.apiBaseUrl, config.neonAuthBaseUrl, path, method, body, extra, config.authOrigin)

        return when (operation.operationType) {
            "template.create" -> {
                val response = send("/templates", "POST", JSONObject().put("id", pathId("id")).put("name", payload.getString("name"))
                    .put("clientActionAt", payload.opt("clientActionAt") ?: JSONObject.NULL)
                    .put("clientTimeZone", payload.opt("clientTimeZone") ?: JSONObject.NULL), headers(null))
                resultVersion(response, 1)
            }
            "template.import" -> importTemplate(api, config, operation, payload, key, ::send, ::headers, ::resultVersion)
            "template.rename" -> {
                val id = pathId("templateId")
                val response = send("/templates/$id", "PATCH", JSONObject().put("name", payload.getString("name"))
                    .put("revisionId", payload.opt("revisionId") ?: JSONObject.NULL).put("eventId", payload.opt("eventId") ?: JSONObject.NULL)
                    .put("clientActionAt", payload.opt("clientActionAt") ?: JSONObject.NULL).put("clientTimeZone", payload.opt("clientTimeZone") ?: JSONObject.NULL), headers())
                resultVersion(response)
            }
            "block.upsert" -> {
                val templateId = pathId("templateId")
                val blockId = pathId("blockId")
                val current = send("/templates/$templateId")
                val currentVersion = resultVersion(current) ?: operation.baseVersion ?: 0
                val exists = current.json().optJSONArray("blocks").orEmpty().containsObject("id", blockId)
                val body = JSONObject().put("id", blockId).put("taskId", pathId("taskId"))
                    .put("title", payload.getString("title")).put("description", payload.optString("description"))
                    .put("tagName", nullableString("tagName") ?: JSONObject.NULL).put("daysMask", payload.getInt("daysMask"))
                    .put("startMinute", payload.getInt("startMinute")).put("endMinute", payload.getInt("endMinute"))
                    .put("revisionId", payload.opt("revisionId") ?: JSONObject.NULL).put("eventId", payload.opt("eventId") ?: JSONObject.NULL)
                    .put("clientActionAt", payload.opt("clientActionAt") ?: JSONObject.NULL).put("clientTimeZone", payload.opt("clientTimeZone") ?: JSONObject.NULL)
                val response = send("/templates/$templateId/blocks" + if (exists) "/$blockId" else "",
                    if (exists) "PATCH" else "POST", body, headers(operation.baseVersion ?: currentVersion))
                resultVersion(response, currentVersion + 1)
            }
            "block.delete" -> {
                val templateId = pathId("templateId")
                val blockId = pathId("blockId")
                val response = send("/templates/$templateId/blocks/$blockId", "DELETE", extra = headers() + mapOf(
                    "X-Client-Revision-Id" to payload.optString("revisionId"), "X-Client-Event-Id" to payload.optString("eventId"),
                    "X-Client-Action-At" to payload.optString("clientActionAt"), "X-Client-Time-Zone" to payload.optString("clientTimeZone")))
                resultVersion(response)
            }
            "selection.activate" -> {
                val response = send("/active-selection", "PUT", JSONObject().put("templateId", pathId("templateId"))
                    .put("revisionId", payload.opt("revisionId") ?: JSONObject.NULL).put("eventId", payload.opt("eventId") ?: JSONObject.NULL)
                    .put("clientActionAt", payload.opt("clientActionAt") ?: JSONObject.NULL).put("clientTimeZone", payload.opt("clientTimeZone") ?: JSONObject.NULL))
                resultVersion(response, (operation.baseVersion ?: 0) + 1)
            }
            "one-off.upsert" -> {
                val id = pathId("id")
                val body = JSONObject().put("id", id).put("eventId", pathId("eventId")).put("date", payload.getString("date"))
                    .put("title", payload.getString("title")).put("description", payload.optString("description"))
                    .put("tagName", nullableString("tagName") ?: JSONObject.NULL).put("startMinute", payload.getInt("startMinute"))
                    .put("endMinute", payload.getInt("endMinute")).put("clientActionAt", payload.opt("clientActionAt") ?: JSONObject.NULL)
                    .put("clientTimeZone", payload.opt("clientTimeZone") ?: JSONObject.NULL)
                val existing = try { send("/one-off-tasks/$id"); true } catch (error: SyncHttpException) { if (error.status == 404) false else throw error }
                val response = if (existing) send("/one-off-tasks/$id", "PATCH", body, headers())
                    else send("/one-off-tasks", "POST", body, headers(null))
                resultVersion(response, (operation.baseVersion ?: 0) + 1)
            }
            "one-off.delete" -> {
                val id = pathId("id")
                val response = send("/one-off-tasks/$id", "DELETE", extra = headers() + mapOf("X-Client-Event-Id" to pathId("eventId"),
                    "X-Client-Action-At" to payload.optString("clientActionAt"), "X-Client-Time-Zone" to payload.optString("clientTimeZone")))
                resultVersion(response, (operation.baseVersion ?: 0) + 1)
            }
            "completion" -> {
                send("/completions", "POST", JSONObject().put("date", payload.getString("date"))
                    .put("occurrenceKey", payload.getString("occurrenceKey")).put("completed", payload.getBoolean("completed"))
                    .put("clientActionAt", payload.opt("clientActionAt") ?: JSONObject.NULL)
                    .put("clientTimeZone", payload.opt("clientTimeZone") ?: JSONObject.NULL), headers(null))
                null
            }
            "exception" -> {
                val expected = operation.baseVersion ?: payload.optInt("expectedVersion", 0)
                val body = JSONObject().put("exceptionId", payload.opt("exceptionId") ?: JSONObject.NULL)
                    .put("eventId", payload.getString("eventId")).put("date", payload.getString("date"))
                    .put("occurrenceKey", payload.getString("occurrenceKey")).put("action", payload.getString("action"))
                    .put("targetDate", payload.opt("targetDate") ?: JSONObject.NULL).put("title", payload.opt("title") ?: JSONObject.NULL)
                    .put("description", payload.opt("description") ?: JSONObject.NULL).put("tagName", payload.opt("tagName") ?: JSONObject.NULL)
                    .put("startMinute", payload.opt("startMinute") ?: JSONObject.NULL).put("endMinute", payload.opt("endMinute") ?: JSONObject.NULL)
                    .put("clientActionAt", payload.opt("clientActionAt") ?: JSONObject.NULL).put("clientTimeZone", payload.opt("clientTimeZone") ?: JSONObject.NULL)
                val response = send("/occurrence-exceptions", "POST", body, headers(expected))
                resultVersion(response, expected + 1)
            }
            else -> error("Tipo de operación de sync no compatible: ${operation.operationType}")
        }
    }

    private suspend fun importTemplate(
        api: BlockNizeApiClient,
        config: SyncConfiguration,
        operation: SyncOutboxEntity,
        payload: JSONObject,
        idempotencyPrefix: String,
        send: suspend (String, String, JSONObject?, Map<String, String>) -> ApiResponse,
        headers: (Int?) -> Map<String, String>,
        resultVersion: (ApiResponse, Int?) -> Int?
    ): Int? {
        val templateId = payload.getString("id")
        send("/templates", "POST", JSONObject().put("id", templateId).put("name", payload.getString("name"))
            .put("clientActionAt", payload.opt("clientActionAt") ?: JSONObject.NULL)
            .put("clientTimeZone", payload.opt("clientTimeZone") ?: JSONObject.NULL), headers(null))
        val blocks = payload.getJSONArray("blocks")
        var version = operation.baseVersion ?: 1
        for (i in 0 until blocks.length()) {
            val block = blocks.getJSONObject(i)
            val current = api.request(config.apiBaseUrl, config.neonAuthBaseUrl, "/templates/$templateId", authOrigin = config.authOrigin)
            val currentVersion = current.header("ETag")?.trim()?.trim('"')?.toIntOrNull() ?: version
            val body = JSONObject().put("id", block.getString("blockId")).put("taskId", block.getString("taskId"))
                .put("title", block.getString("title")).put("description", block.optString("description"))
                .put("tagName", block.opt("tagName") ?: JSONObject.NULL).put("daysMask", block.getInt("daysMask"))
                .put("startMinute", block.getInt("startMinute")).put("endMinute", block.getInt("endMinute"))
                .put("clientActionAt", payload.opt("clientActionAt") ?: JSONObject.NULL)
                .put("clientTimeZone", payload.opt("clientTimeZone") ?: JSONObject.NULL)
            val response = send("/templates/$templateId/blocks", "POST", body,
                mapOf("Idempotency-Key" to "$idempotencyPrefix:block:$i", "If-Match" to currentVersion.toString()))
            version = resultVersion(response, currentVersion + 1) ?: currentVersion + 1
        }
        return version
    }

    private suspend fun applySnapshot(database: AppDatabase, dao: AppDao, root: JSONObject) {
        val data = root.getJSONObject("data")
        val templates = data.array("templates").map { RoutineTemplateEntity(it.getString("id"), it.getString("name"), it.instant("createdAt"), it.instant("updatedAt")) }
        val tags = data.array("tags").map { TagEntity(it.getString("id"), it.getString("name")) }
        val tasks = data.array("taskDefinitions").map { TaskDefinitionEntity(it.getString("id"), it.getString("templateId"), it.getString("title"), it.optString("description"), it.nullableString("tagId")) }
        val blocks = data.array("scheduleBlocks").map { ScheduleBlockEntity(it.getString("id"), it.getString("templateId"), it.getString("taskId"), it.getInt("daysMask"), it.getInt("startMinute"), it.getInt("endMinute")) }
        val revisions = data.array("templateRevisions").map { TemplateRevisionEntity(it.getString("id"), it.getString("templateId"), it.instant("createdAt"), it.getJSONObject("payload").toString()) }
        val activeRows = data.array("activeSelection")
        val active = activeRows.firstOrNull()?.let { ActiveSelectionEntity(1, it.getString("templateId"), it.getString("revisionId"), it.instant("effectiveAt")) }
        val scheduleEvents = data.array("scheduleChangeEvents").map { ScheduleChangeEventEntity(
            sequence = it.getLong("sequence"), eventId = it.getString("eventId"), effectiveAt = it.instant("effectiveAt"),
            previousTemplateId = it.nullableString("previousTemplateId"), templateId = it.getString("templateId"),
            revisionId = it.getString("revisionId"), zoneId = it.getString("zoneId"), changeType = it.getString("changeType")) }
        val oneOffTasks = data.array("oneOffTasks").map { OneOffTaskEntity(it.getString("id"), LocalDate.parse(it.getString("date")).toEpochDay(),
            it.getString("title"), it.optString("description"), it.nullableString("tagId"), it.getInt("startMinute"), it.getInt("endMinute")) }
        val oneOffEvents = data.array("oneOffScheduleEvents").map { row ->
            val snapshot = row.getJSONObject("snapshot")
            OneOffScheduleEventEntity(sequence = row.getLong("sequence"), eventId = row.getString("eventId"), taskId = row.getString("taskId"),
                dateEpochDay = LocalDate.parse(row.getString("date")).toEpochDay(), effectiveAt = row.instant("effectiveAt"),
                changeType = row.getString("changeType"), title = snapshot.getString("title"), description = snapshot.optString("description"),
                tagName = snapshot.nullableString("tagName"), startMinute = snapshot.getInt("startMinute"), endMinute = snapshot.getInt("endMinute"), zoneId = row.getString("zoneId"))
        }
        val completions = data.array("completions").map { CompletionRecordEntity(
            occurrenceKey = it.getString("occurrenceKey"), sourceType = it.getString("sourceType"), sourceId = it.getString("sourceId"),
            dateEpochDay = LocalDate.parse(it.getString("date")).toEpochDay(), completedAt = it.instant("completedAt"),
            titleSnapshot = it.getString("titleSnapshot"), startMinuteSnapshot = it.getInt("startMinuteSnapshot"), endMinuteSnapshot = it.getInt("endMinuteSnapshot"),
            sourceTemplateId = it.nullableString("sourceTemplateId"), sourceRevisionId = it.nullableString("sourceRevisionId"), sourceEventId = it.nullableString("sourceEventId")) }
        val exceptions = data.array("occurrenceExceptions").map { OccurrenceExceptionEntity(
            id = it.getString("id"), templateId = it.getString("templateId"), blockId = it.getString("blockId"),
            originalEpochDay = LocalDate.parse(it.getString("originalDate")).toEpochDay(), targetEpochDay = LocalDate.parse(it.getString("targetDate")).toEpochDay(),
            action = it.getString("action"), payloadJson = it.getJSONObject("payload").toString(), zoneId = it.getString("zoneId"),
            createdAt = it.instant("createdAt"), modifiedAt = it.instant("modifiedAt")) }
        val exceptionEvents = data.array("occurrenceExceptionEvents").map { row ->
            val snapshot = row.getJSONObject("snapshot")
            val state = OccurrenceExceptionEntity(snapshot.getString("id"), snapshot.getString("templateId"), snapshot.getString("blockId"),
                LocalDate.parse(snapshot.getString("originalDate")).toEpochDay(), LocalDate.parse(snapshot.getString("targetDate")).toEpochDay(),
                snapshot.getString("action"), snapshot.getJSONObject("payload").toString(), snapshot.getString("zoneId"),
                snapshot.instant("createdAt"), snapshot.instant("modifiedAt"))
            OccurrenceExceptionEventEntity(sequence = row.getLong("sequence"), exceptionId = row.getString("exceptionId"), effectiveAt = row.instant("effectiveAt"), state = state)
        }
        val versions = buildList {
            data.array("templates").forEach { add(SyncEntityVersionEntity("template:${it.getString("id")}", it.optInt("version", 1), System.currentTimeMillis())) }
            data.array("oneOffTasks").forEach { add(SyncEntityVersionEntity("one-off:${it.getString("id")}", it.optInt("version", 1), System.currentTimeMillis())) }
            activeRows.firstOrNull()?.let { add(SyncEntityVersionEntity("selection", it.optInt("version", 1), System.currentTimeMillis())) }
            data.array("occurrenceExceptions").forEach { row ->
                val origin = "exception:${row.getString("templateId")}:${row.getString("blockId")}:${row.getString("originalDate")}"
                add(SyncEntityVersionEntity(origin, row.optInt("version", 1), System.currentTimeMillis()))
            }
        }
        database.withTransaction {
            dao.clearSyncEntityVersions()
            dao.clearCompletions(); dao.clearExceptionEvents(); dao.clearExceptions(); dao.clearOneOffEvents(); dao.clearOneOffTasks()
            dao.clearScheduleEvents(); dao.clearActiveSelection(); dao.clearScheduleBlocks(); dao.clearTaskDefinitions()
            dao.clearTemplateRevisions(); dao.clearTemplates(); dao.clearTags()
            dao.replaceTags(tags); dao.replaceTemplates(templates); dao.replaceTaskDefinitions(tasks); dao.replaceScheduleBlocks(blocks)
            dao.replaceTemplateRevisions(revisions); if (active != null) dao.setActive(active)
            dao.replaceScheduleEvents(scheduleEvents); dao.replaceOneOffTasks(oneOffTasks); dao.replaceOneOffEvents(oneOffEvents)
            dao.replaceCompletions(completions); dao.replaceExceptions(exceptions); dao.replaceExceptionEvents(exceptionEvents)
            versions.forEach { dao.saveSyncEntityVersion(it) }
        }
    }

    private fun JSONObject.array(key: String): List<JSONObject> {
        val values = optJSONArray(key) ?: JSONArray()
        return (0 until values.length()).map { values.getJSONObject(it) }
    }

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.instant(key: String): Long {
        val value = get(key)
        return when (value) {
            is Number -> value.toLong()
            is String -> Instant.parse(value).toEpochMilli()
            else -> error("Instante no válido: $key")
        }
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private fun JSONArray.containsObject(key: String, value: String): Boolean = (0 until length()).any { getJSONObject(it).optString(key) == value }
}
