package com.blocknize.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.Clock
import java.time.LocalDate
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.map
import com.blocknize.app.domain.civilInstant
import com.blocknize.app.domain.civilInterval
import com.blocknize.app.domain.SystemCivilClock
import com.blocknize.app.domain.CivilTimeChanges
import kotlinx.coroutines.flow.combine

data class AgendaOccurrence(
    val occurrenceKey: String,
    val sourceType: String,
    val sourceId: String,
    val date: LocalDate,
    val title: String,
    val description: String,
    val tagName: String?,
    val startMinute: Int,
    val endMinute: Int,
    val start: Instant,
    val end: Instant,
    val templateId: String?,
    val revisionId: String?,
    val sourceEventId: String?,
    val completed: Boolean,
    val historical: Boolean = false,
    val interrupted: Boolean = false,
    val originalStart: Instant = start,
    val originalEnd: Instant = end,
    val sourceTemplateName: String? = null,
    val exceptionId: String? = null,
    val originalDate: LocalDate = date,
    val exceptionStatus: String? = null,
    val destinationDate: LocalDate? = null,
    // Historical interval keys may change, but undo must still address the original completion.
    val completionKey: String = occurrenceKey
) {
    fun progressAt(now: Instant): Float = when {
        now <= start -> 0f
        now >= end -> 1f
        else -> (now.toEpochMilli() - start.toEpochMilli()).toFloat() /
            (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(1)
    }.coerceIn(0f, 1f)
}

class TemplateRepository(private val database: AppDatabase, private val clock: Clock = SystemCivilClock()) {
    private val dao = database.dao()
    fun observeTemplates(): Flow<List<TemplateRow>> = dao.observeTemplates()
    fun observeActive(): Flow<ActiveSelectionEntity?> = dao.observeActive()
    fun observeTags(): Flow<List<TagEntity>> = dao.observeTags()
    fun observeBlocks(templateId: String): Flow<List<BlockRow>> = dao.observeBlocks(templateId)
    private fun changes() = database.invalidationTracker.createFlow(
        "active_template_selection", "routine_templates", "task_definitions", "schedule_blocks",
        "tags", "template_revisions", "schedule_change_events", "one_off_tasks",
        "one_off_schedule_events", "completion_records", "occurrence_exceptions", "occurrence_exception_events"
    ).combine(CivilTimeChanges.revisions) { tables, _ -> tables }

    fun observeAgenda(date: LocalDate): Flow<List<AgendaOccurrence>> = changes().map {
        database.withTransaction { getAgenda(date) }
    }

    fun observeCalendarAgenda(date: LocalDate): Flow<List<AgendaOccurrence>> = changes().map {
        database.withTransaction {
            if (date == LocalDate.now(clock)) {
                val current = getAgenda(date)
                // Show actionable rows from the live agenda while retaining today's interrupted history.
                val prior = getHistoricalAgenda(date).filter { it.interrupted && current.none { live -> live.occurrenceKey == it.occurrenceKey } }
                (current + prior).sortedBy { it.start }
            } else getCalendarAgenda(date)
        }
    }

    fun observeHistory(date: LocalDate): Flow<List<AgendaOccurrence>> = changes().map {
        database.withTransaction { getHistoricalAgenda(date) }
    }

    suspend fun getAgenda(date: LocalDate): List<AgendaOccurrence> = database.withTransaction {
        val selection = dao.active()
        val blocks = selection?.let { dao.blocks(it.templateId) }.orEmpty()
        val eventId = selection?.let { dao.eventForRevision(it.revisionId)?.eventId }
        val records = dao.completions(date.toEpochDay())
        val base = buildAgenda(selection, eventId, blocks, dao.currentOneOffEvents(date.toEpochDay()), records, date)
        applyCurrentExceptions(base, dao.exceptions(), records, date)
    }

    suspend fun getCalendarAgenda(date: LocalDate): List<AgendaOccurrence> = database.withTransaction {
        if (date.isAfter(LocalDate.now(clock))) getAgenda(date) else getHistoricalAgenda(date)
    }

    fun observeWeek(startDate: LocalDate): Flow<Map<LocalDate, List<AgendaOccurrence>>> =
        changes().map {
            database.withTransaction {
                val today = LocalDate.now(clock)
                (0L..6L).associate { offset ->
                    val date = startDate.plusDays(offset)
                    date to if (date.isBefore(today)) getHistoricalAgenda(date)
                    else getAgenda(date).filterNot { it.historical }
                }
            }
        }

    private suspend fun getHistoricalAgenda(date: LocalDate): List<AgendaOccurrence> {
        val zone = clock.zone
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val dayStartMs = dayStart.toEpochMilli()
        val dayEndMs = dayEnd.toEpochMilli()
        val completionRecords = dao.completions(date.toEpochDay())
        val events = dao.scheduleEventsBefore(dayEndMs)
        val revisionMap = if (events.isEmpty()) emptyMap() else dao.revisions(events.map { it.revisionId }.distinct()).associateBy { it.id }
        val weeklyOccurrences = mutableListOf<AgendaOccurrence>()

        events.forEachIndexed { index, event ->
            val segmentStartMs = maxOf(dayStartMs, event.effectiveAt)
            val nextEvent = events.getOrNull(index + 1)
            val segmentEndMs = minOf(dayEndMs, nextEvent?.effectiveAt ?: dayEndMs)
            if (segmentStartMs >= segmentEndMs) return@forEachIndexed
            val payload = revisionMap[event.revisionId]?.payloadJson ?: return@forEachIndexed
            val blocks = runCatching { JSONObject(payload).optJSONArray("blocks") }.getOrNull() ?: return@forEachIndexed
            val eventZone = runCatching { ZoneId.of(event.zoneId) }.getOrDefault(zone)
            for (blockIndex in 0 until blocks.length()) {
                val block = blocks.optJSONObject(blockIndex) ?: continue
                val daysMask = block.optInt("daysMask")
                if (daysMask and (1 shl (date.dayOfWeek.value - 1)) == 0) continue
                val blockId = block.optString("blockId")
                val startMinute = block.optInt("startMinute")
                val endMinute = block.optInt("endMinute")
                if (blockId.isBlank() || startMinute !in 0..1439 || endMinute !in 1..1440 || endMinute <= startMinute) continue
                val (originalStart, originalEnd) = civilInterval(date, startMinute, endMinute, eventZone)
                val effectiveStart = maxOf(originalStart.toEpochMilli(), segmentStartMs)
                val effectiveEnd = minOf(originalEnd.toEpochMilli(), segmentEndMs)
                if (effectiveStart >= effectiveEnd) continue
                val key = "weekly:${event.eventId}:$blockId:$date"
                val completed = isCompleted(completionRecords, key, "WEEKLY", blockId, date, event.eventId, event.revisionId,
                    block.optString("title"), startMinute, endMinute)
                weeklyOccurrences += AgendaOccurrence(
                    occurrenceKey = key, sourceType = "WEEKLY", sourceId = blockId, date = date,
                    title = block.optString("title"), description = block.optString("description"),
                    tagName = block.optString("tagName").takeUnless { it == "null" || it.isEmpty() },
                    startMinute = startMinute, endMinute = endMinute,
                    start = Instant.ofEpochMilli(effectiveStart), end = Instant.ofEpochMilli(effectiveEnd),
                    templateId = event.templateId, revisionId = event.revisionId, sourceEventId = event.eventId,
                    completed = completed, historical = true,
                    interrupted = !completed && nextEvent != null && nextEvent.effectiveAt < originalEnd.toEpochMilli(),
                    originalStart = originalStart, originalEnd = originalEnd,
                    sourceTemplateName = JSONObject(payload).optString("name").takeIf(String::isNotBlank)
                )
            }
        }

        val oneOffEvents = dao.oneOffEventsBefore(dayEndMs).groupBy { it.taskId }
        val oneOffOccurrences = mutableListOf<AgendaOccurrence>()
        oneOffEvents.values.forEach { taskEvents ->
            taskEvents.forEachIndexed { index, event ->
                if (event.changeType != "UPSERT" || event.dateEpochDay != date.toEpochDay()) return@forEachIndexed
                val nextEvent = taskEvents.getOrNull(index + 1)
                val segmentStart = maxOf(dayStartMs, event.effectiveAt)
                val segmentEnd = minOf(dayEndMs, nextEvent?.effectiveAt ?: dayEndMs)
                if (segmentStart >= segmentEnd) return@forEachIndexed
                val eventZone = runCatching { ZoneId.of(event.zoneId) }.getOrDefault(zone)
                val taskDate = LocalDate.ofEpochDay(event.dateEpochDay)
                val (originalStart, originalEnd) = civilInterval(taskDate, event.startMinute, event.endMinute, eventZone)
                val effectiveStart = maxOf(originalStart.toEpochMilli(), segmentStart)
                val effectiveEnd = minOf(originalEnd.toEpochMilli(), segmentEnd)
                if (effectiveStart >= effectiveEnd) return@forEachIndexed
                val key = "oneoff:${event.eventId}:${event.taskId}:$date"
                val completed = isCompleted(completionRecords, key, "ONE_OFF", event.taskId, date, event.eventId, null,
                    event.title, event.startMinute, event.endMinute)
                oneOffOccurrences += AgendaOccurrence(
                    occurrenceKey = key, sourceType = "ONE_OFF", sourceId = event.taskId, date = date,
                    title = event.title, description = event.description, tagName = event.tagName,
                    startMinute = event.startMinute, endMinute = event.endMinute,
                    start = Instant.ofEpochMilli(effectiveStart), end = Instant.ofEpochMilli(effectiveEnd),
                    templateId = null, revisionId = null, sourceEventId = event.eventId,
                    completed = completed, historical = true,
                    interrupted = !completed && nextEvent != null && nextEvent.effectiveAt < originalEnd.toEpochMilli(),
                    originalStart = originalStart, originalEnd = originalEnd
                )
            }
        }
        val reconstructed = applyHistoricalExceptions(weeklyOccurrences + oneOffOccurrences,
            dao.exceptionEventsBefore(dayEndMs), completionRecords, date, zone)
        val unmatchedCompletions = completionRecords.filter { record ->
            reconstructed.none { occurrence -> isSameCompletion(record, occurrence) }
        }.map { record ->
            val sourceTemplateName = record.sourceRevisionId?.let { revisionMap[it]?.payloadJson }
                ?.let { runCatching { JSONObject(it).optString("name") }.getOrNull() }
            occurrence(record.occurrenceKey, record.sourceType, record.sourceId, date, record.titleSnapshot, "", null,
                record.startMinuteSnapshot, record.endMinuteSnapshot, record.sourceTemplateId,
                record.sourceRevisionId, record.sourceEventId, true, zone).copy(historical = true, sourceTemplateName = sourceTemplateName)
        }
        return (reconstructed + unmatchedCompletions).sortedWith(compareBy<AgendaOccurrence> { it.start }.thenBy { it.title })
    }

    private fun isCompleted(
        records: List<CompletionRecordEntity>, key: String, type: String, sourceId: String,
        date: LocalDate, eventId: String?, revisionId: String?, title: String, startMinute: Int, endMinute: Int
    ): Boolean = records.any { record ->
        record.occurrenceKey == key || (record.sourceEventId == null && record.sourceType == type &&
            record.sourceId == sourceId && record.dateEpochDay == date.toEpochDay() &&
            record.titleSnapshot == title && record.startMinuteSnapshot == startMinute && record.endMinuteSnapshot == endMinute &&
            (type != "WEEKLY" || record.sourceRevisionId == revisionId))
    }

    private fun isSameCompletion(record: CompletionRecordEntity, occurrence: AgendaOccurrence): Boolean =
        record.occurrenceKey == occurrence.occurrenceKey || (occurrence.completed && record.occurrenceKey == occurrence.completionKey) ||
            (occurrence.completed && occurrence.occurrenceKey.startsWith(record.occurrenceKey + ":")) || (record.sourceEventId == null &&
            record.sourceType == occurrence.sourceType && record.sourceId == occurrence.sourceId &&
            record.dateEpochDay == occurrence.date.toEpochDay() &&
            record.titleSnapshot == occurrence.title && record.startMinuteSnapshot == occurrence.startMinute &&
            record.endMinuteSnapshot == occurrence.endMinute &&
            (record.sourceType != "WEEKLY" || record.sourceRevisionId == occurrence.revisionId))

    private fun buildAgenda(
        selection: ActiveSelectionEntity?, eventId: String?, blocks: List<BlockRow>, oneOffs: List<OneOffRow>,
        records: List<CompletionRecordEntity>, date: LocalDate
    ): List<AgendaOccurrence> {
        val zone = clock.zone
        val routine = if (selection == null) emptyList() else blocks
            .filter { it.daysMask and (1 shl (date.dayOfWeek.value - 1)) != 0 }
            .map { block ->
                val key = "weekly:${eventId}:${block.blockId}:${date}"
                occurrence(key, "WEEKLY", block.blockId, date, block.title, block.description, block.tagName,
                    block.startMinute, block.endMinute, selection.templateId, selection.revisionId,
                    eventId, isCompleted(records, key, "WEEKLY", block.blockId, date, eventId, selection.revisionId,
                        block.title, block.startMinute, block.endMinute), zone)
            }
        val temporaries = oneOffs.map { task ->
            val key = "oneoff:${task.eventId}:${task.id}:${date}"
            occurrence(key, "ONE_OFF", task.id, date, task.title, task.description, task.tagName,
                task.startMinute, task.endMinute, null, null, task.eventId,
                isCompleted(records, key, "ONE_OFF", task.id, date, task.eventId, null,
                    task.title, task.startMinute, task.endMinute), zone)
        }
        val planned = routine + temporaries
        val historical = records.filter { record -> planned.none { isSameCompletion(record, it) } }.map { record ->
            occurrence(record.occurrenceKey, record.sourceType, record.sourceId, date, record.titleSnapshot, "", null,
                record.startMinuteSnapshot, record.endMinuteSnapshot, record.sourceTemplateId,
                record.sourceRevisionId, record.sourceEventId, true, zone).copy(historical = true)
        }
        return (planned + historical).sortedWith(compareBy<AgendaOccurrence> { it.start }.thenBy { it.title })
    }

    suspend fun setCompleted(occurrence: AgendaOccurrence, completed: Boolean) = database.withTransaction {
        require(occurrence.exceptionStatus == null) { "Esta ocurrencia está omitida o reprogramada." }
        if (completed && occurrence.sourceType == "WEEKLY") {
            require(getAgenda(occurrence.date).any { it.occurrenceKey == occurrence.occurrenceKey && it.exceptionStatus == null }) {
                "La actividad ha cambiado. Vuelve a abrirla."
            }
        }
        if (!completed) dao.deleteCompletion(occurrence.completionKey)
        else if (dao.completion(occurrence.completionKey) == null) dao.upsertCompletion(CompletionRecordEntity(
            occurrenceKey = occurrence.completionKey,
            sourceType = occurrence.sourceType,
            sourceId = occurrence.sourceId,
            dateEpochDay = occurrence.date.toEpochDay(),
            completedAt = clock.millis(),
            titleSnapshot = occurrence.title,
            startMinuteSnapshot = occurrence.startMinute,
            endMinuteSnapshot = occurrence.endMinute,
            sourceTemplateId = occurrence.templateId,
            sourceRevisionId = occurrence.revisionId,
            sourceEventId = occurrence.sourceEventId
        ))
    }

    suspend fun saveException(
        occurrence: AgendaOccurrence, action: String, date: LocalDate = occurrence.date,
        title: String = occurrence.title, description: String = occurrence.description,
        tagName: String = occurrence.tagName.orEmpty(), startMinute: Int = occurrence.startMinute,
        endMinute: Int = occurrence.endMinute
    ) = database.withTransaction {
        require(action in setOf("SKIP", "REPLACE", "RESTORE"))
        val templateId = requireNotNull(occurrence.templateId) { "La actividad no tiene plantilla de origen." }
        val today = LocalDate.now(clock)
        require(!occurrence.date.isBefore(today) && !occurrence.originalDate.isBefore(today)) { "No se pueden editar ocurrencias de días pasados." }
        val previous = dao.exceptionForOrigin(templateId, occurrence.sourceId, occurrence.originalDate.toEpochDay())
        // Re-read the agenda inside the transaction: stale dialogs must not overwrite newer decisions.
        val current = getAgenda(occurrence.date).firstOrNull { it.occurrenceKey == occurrence.occurrenceKey }
        require(current != null && current == occurrence) { "La actividad ha cambiado. Vuelve a abrirla." }
        require(!occurrence.completed && (previous == null || dao.completion("exception:${previous.id}") == null)) {
            "Deshaz la finalización antes de modificar, omitir o restaurar esta actividad."
        }
        require(action != "RESTORE" || previous != null && previous.action != "RESTORE") { "No hay una excepción que restaurar." }
        val payload = previous?.let { JSONObject(it.payloadJson) } ?: JSONObject().put("original",
            occurrenceSnapshot(occurrence).put("templateName", dao.template(templateId)?.name.orEmpty()))
        if (action == "REPLACE") {
            require(title.isNotBlank()) { "El título es obligatorio." }
            require(startMinute in 0..1439 && endMinute in 1..1440 && startMinute < endMinute) { "El inicio debe ser anterior al fin, sin cruzar medianoche." }
            val interval = civilInterval(date, startMinute, endMinute, clock.zone)
            require(interval.first < interval.second) { "El cambio de hora deja esta franja sin duración efectiva. Elige otro horario." }
            require(!date.isBefore(today)) { "El destino no puede estar en una fecha pasada." }
            val changedSchedule = date != occurrence.date || startMinute != occurrence.startMinute
            require(!changedSchedule || !civilInstant(date, startMinute, clock.zone).isBefore(clock.instant())) {
                "No se puede reprogramar hacia un inicio ya pasado."
            }
            payload.put("replacement", JSONObject().put("title", title.trim()).put("description", description.trim())
                .put("tagName", tagName.trim()).put("startMinute", startMinute).put("endMinute", endMinute))
        }
        val now = clock.millis()
        val state = OccurrenceExceptionEntity(previous?.id ?: UUID.randomUUID().toString(), templateId,
            occurrence.sourceId, occurrence.originalDate.toEpochDay(),
            if (action == "REPLACE") date.toEpochDay() else previous?.targetEpochDay ?: occurrence.date.toEpochDay(),
            action, payload.toString(), previous?.zoneId ?: clock.zone.id, previous?.createdAt ?: now, now)
        dao.upsertException(state)
        dao.insertExceptionEvent(OccurrenceExceptionEventEntity(exceptionId = state.id, effectiveAt = now, state = state))
    }

    suspend fun saveOneOff(
        id: String?, date: LocalDate, title: String, description: String, tagName: String,
        startMinute: Int, endMinute: Int
    ) = database.withTransaction {
        val cleanTitle = title.trim().takeIf(String::isNotEmpty) ?: error("El título es obligatorio.")
        require(!date.isBefore(LocalDate.now(clock))) { "No se pueden programar tareas temporales en una fecha pasada." }
        require(startMinute in 0..1439 && endMinute in 1..1440 && startMinute < endMinute) { "La hora de fin debe ser posterior al inicio; no se admiten bloques que crucen medianoche." }
        val interval = civilInterval(date, startMinute, endMinute, clock.zone)
        require(interval.first < interval.second) { "El cambio de hora deja esta franja sin duración efectiva. Elige otro horario." }
        val normalizedTag = tagName.trim()
        val tagId = if (normalizedTag.isEmpty()) null else {
            (dao.tagByName(normalizedTag) ?: TagEntity(UUID.randomUUID().toString(), normalizedTag).also { dao.upsertTag(it) }).id
        }
        val taskId = id ?: UUID.randomUUID().toString()
        val previous = dao.oneOff(taskId)
        val previousTagName = previous?.tagId?.let { dao.tagName(it) }
        val cleanDescription = description.trim()
        val changed = previous == null || previous.dateEpochDay != date.toEpochDay() || previous.title != cleanTitle ||
            previous.description != cleanDescription || previousTagName != normalizedTag.ifEmpty { null } ||
            previous.startMinute != startMinute || previous.endMinute != endMinute
        dao.upsertOneOff(OneOffTaskEntity(taskId, date.toEpochDay(), cleanTitle,
            cleanDescription, tagId, startMinute, endMinute))
        if (changed) dao.insertOneOffEvent(OneOffScheduleEventEntity(
            eventId = UUID.randomUUID().toString(), taskId = taskId, dateEpochDay = date.toEpochDay(),
            effectiveAt = clock.millis(), changeType = "UPSERT", title = cleanTitle,
            description = cleanDescription, tagName = normalizedTag.ifEmpty { null },
            startMinute = startMinute, endMinute = endMinute, zoneId = clock.zone.id
        ))
    }

    suspend fun deleteOneOff(id: String) = database.withTransaction {
        val task = dao.oneOff(id) ?: return@withTransaction
        dao.insertOneOffEvent(OneOffScheduleEventEntity(
            eventId = UUID.randomUUID().toString(), taskId = task.id, dateEpochDay = task.dateEpochDay,
            effectiveAt = clock.millis(), changeType = "DELETE", title = task.title,
            description = task.description, tagName = task.tagId?.let { dao.tagName(it) },
            startMinute = task.startMinute, endMinute = task.endMinute, zoneId = clock.zone.id
        ))
        dao.deleteOneOff(id)
    }

    suspend fun createTemplate(name: String) = database.withTransaction {
        val cleanName = name.trim().takeIf(String::isNotEmpty) ?: error("Escribe un nombre para la semana.")
        val now = clock.millis()
        dao.insertTemplate(RoutineTemplateEntity(UUID.randomUUID().toString(), cleanName, now, now))
    }

    suspend fun exportTemplateJson(templateId: String): String {
        val template = dao.template(templateId) ?: error("La semana ya no existe.")
        return TemplateTransferCodec.encode(template.name, dao.blocks(templateId))
    }

    suspend fun importTemplate(template: TransferTemplate, name: String = template.name): String = database.withTransaction {
        val cleanName = name.trim().takeIf(String::isNotEmpty) ?: error("Escribe un nombre para la semana.")
        require(cleanName.length <= 80) { "El nombre no puede superar 80 caracteres." }
        val templateId = TemplateTransferCodec.newId()
        val now = clock.millis()
        dao.insertTemplate(RoutineTemplateEntity(templateId, cleanName, now, now))
        template.blocks.forEach { block ->
            val tagName = block.tagName?.trim()?.takeIf(String::isNotEmpty)
            val tagId = tagName?.let { name ->
                (dao.tagByName(name) ?: TagEntity(TemplateTransferCodec.newId(), name).also { dao.upsertTag(it) }).id
            }
            val taskId = TemplateTransferCodec.newId()
            dao.insertTask(TaskDefinitionEntity(taskId, templateId, block.title, block.description, tagId))
            dao.insertBlock(ScheduleBlockEntity(TemplateTransferCodec.newId(), templateId, taskId,
                block.daysMask, block.startMinute, block.endMinute))
        }
        templateId
    }

    suspend fun activate(templateId: String) = database.withTransaction {
        val template = dao.template(templateId) ?: error("La semana ya no existe.")
        val current = dao.active()
        if (current?.templateId == templateId) return@withTransaction
        val now = clock.millis()
        val payload = snapshot(templateId, template.name)
        val revisionId = UUID.randomUUID().toString()
        dao.insertRevision(TemplateRevisionEntity(revisionId, templateId, now, payload))
        dao.insertEvent(ScheduleChangeEventEntity(
            eventId = UUID.randomUUID().toString(), effectiveAt = now,
            previousTemplateId = current?.templateId, templateId = templateId,
            revisionId = revisionId, zoneId = clock.zone.id, changeType = "ACTIVATION"
        ))
        dao.setActive(ActiveSelectionEntity(templateId = templateId, revisionId = revisionId, effectiveAt = now))
    }

    suspend fun renameTemplate(templateId: String, name: String) = database.withTransaction {
        val cleanName = name.trim().takeIf(String::isNotEmpty) ?: error("El nombre no puede estar vacío.")
        val existing = dao.template(templateId) ?: error("La semana ya no existe.")
        if (existing.name == cleanName) return@withTransaction
        val before = snapshot(templateId, existing.name)
        val now = clock.millis()
        dao.renameTemplate(templateId, cleanName, now)
        if (dao.active()?.templateId == templateId && before != snapshot(templateId, cleanName)) {
            recordActiveEdit(templateId, cleanName, now)
        }
    }

    suspend fun saveBlock(
        templateId: String, blockId: String?, title: String, description: String,
        tagName: String, daysMask: Int, startMinute: Int, endMinute: Int
    ) = database.withTransaction {
        val cleanTitle = title.trim().takeIf(String::isNotEmpty) ?: error("El título es obligatorio.")
        require(daysMask in 1..127) { "Selecciona al menos un día." }
        require(startMinute in 0..1439 && endMinute in 1..1440 && startMinute < endMinute) { "La hora de fin debe ser posterior al inicio y el bloque no puede cruzar medianoche." }
        val template = dao.template(templateId) ?: error("La semana ya no existe.")
        val before = snapshot(templateId, template.name)
        val id = blockId ?: UUID.randomUUID().toString()
        val previousBlock = blockId?.let { dao.block(it) }
        require(previousBlock == null || previousBlock.templateId == templateId) { "El bloque no pertenece a esta semana." }
        val taskId = previousBlock?.taskId ?: UUID.randomUUID().toString()
        val normalizedTag = tagName.trim()
        val tagId = if (normalizedTag.isEmpty()) null else {
            (dao.tagByName(normalizedTag) ?: TagEntity(UUID.randomUUID().toString(), normalizedTag).also { dao.upsertTag(it) }).id
        }
        dao.upsertTask(TaskDefinitionEntity(taskId, templateId, cleanTitle, description.trim(), tagId))
        dao.upsertBlock(ScheduleBlockEntity(id, templateId, taskId, daysMask, startMinute, endMinute))
        val now = clock.millis()
        dao.updateTemplateTimestamp(templateId, now)
        val after = snapshot(templateId, template.name)
        if (before != after && dao.active()?.templateId == templateId) recordActiveEdit(templateId, template.name, now)
    }

    suspend fun deleteBlock(templateId: String, blockId: String) = database.withTransaction {
        val template = dao.template(templateId) ?: error("La semana ya no existe.")
        val before = snapshot(templateId, template.name)
        dao.deleteBlock(blockId, templateId)
        val now = clock.millis()
        dao.updateTemplateTimestamp(templateId, now)
        val after = snapshot(templateId, template.name)
        if (before != after && dao.active()?.templateId == templateId) recordActiveEdit(templateId, template.name, now)
    }

    private suspend fun recordActiveEdit(templateId: String, name: String, now: Long) {
        val current = dao.active() ?: error("No hay una semana activa.")
        val revisionId = UUID.randomUUID().toString()
        dao.insertRevision(TemplateRevisionEntity(revisionId, templateId, now, snapshot(templateId, name)))
        dao.insertEvent(ScheduleChangeEventEntity(
            eventId = UUID.randomUUID().toString(), effectiveAt = now,
            previousTemplateId = templateId, templateId = templateId, revisionId = revisionId,
            zoneId = clock.zone.id, changeType = "ACTIVE_EDIT"
        ))
        dao.setActive(current.copy(revisionId = revisionId, effectiveAt = now))
    }

    private suspend fun snapshot(templateId: String, name: String): String {
        val blocks = dao.blocks(templateId)
        val jsonBlocks = JSONArray()
        blocks.forEach { row ->
            jsonBlocks.put(JSONObject().apply {
                put("blockId", row.blockId); put("taskId", row.taskId); put("title", row.title)
                put("description", row.description); put("tagName", row.tagName ?: JSONObject.NULL)
                put("daysMask", row.daysMask); put("startMinute", row.startMinute); put("endMinute", row.endMinute)
            })
        }
        return JSONObject().put("schemaVersion", 1).put("templateId", templateId)
            .put("name", name).put("blocks", jsonBlocks).toString()
    }

    private fun occurrence(
        key: String, type: String, id: String, date: LocalDate, title: String, description: String,
        tagName: String?, startMinute: Int, endMinute: Int, templateId: String?, revisionId: String?,
        eventId: String?, completed: Boolean, zone: ZoneId
    ): AgendaOccurrence {
        val (start, end) = civilInterval(date, startMinute, endMinute, zone)
        return AgendaOccurrence(
            occurrenceKey = key, sourceType = type, sourceId = id, date = date, title = title,
            description = description, tagName = tagName, startMinute = startMinute, endMinute = endMinute,
            start = start, end = end, templateId = templateId, revisionId = revisionId,
            sourceEventId = eventId, completed = completed
        )
    }
}
