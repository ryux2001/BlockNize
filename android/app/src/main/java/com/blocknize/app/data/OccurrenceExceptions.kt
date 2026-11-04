package com.blocknize.app.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.blocknize.app.domain.civilInterval

internal fun occurrenceSnapshot(task: AgendaOccurrence): JSONObject = JSONObject()
    .put("title", task.title).put("description", task.description).put("tagName", task.tagName.orEmpty())
    .put("startMinute", task.startMinute).put("endMinute", task.endMinute)
    .put("revisionId", task.revisionId.orEmpty()).put("sourceEventId", task.sourceEventId.orEmpty())
    .put("templateName", task.sourceTemplateName.orEmpty())

private fun OccurrenceExceptionEntity.matches(task: AgendaOccurrence) =
    task.sourceType == "WEEKLY" && task.templateId == templateId && task.sourceId == blockId &&
        task.date.toEpochDay() == originalEpochDay

private fun OccurrenceExceptionEntity.task(marker: Boolean, historical: Boolean = false): AgendaOccurrence {
    val payload = JSONObject(payloadJson)
    val original = payload.getJSONObject("original")
    val snapshot = if (marker) original else payload.getJSONObject("replacement")
    val origin = LocalDate.ofEpochDay(originalEpochDay)
    val date = if (marker) origin else LocalDate.ofEpochDay(targetEpochDay)
    val zone = ZoneId.of(zoneId)
    val startMinute = snapshot.getInt("startMinute")
    val endMinute = snapshot.getInt("endMinute")
    val (start, end) = civilInterval(date, startMinute, endMinute, zone)
    return AgendaOccurrence(
        occurrenceKey = if (marker) "exception-origin:$id" else "exception:$id",
        sourceType = "WEEKLY", sourceId = blockId, date = date, title = snapshot.getString("title"),
        description = snapshot.optString("description"), tagName = snapshot.optString("tagName").ifBlank { null },
        startMinute = startMinute, endMinute = endMinute, start = start, end = end,
        templateId = templateId, revisionId = original.optString("revisionId").ifBlank { null },
        sourceEventId = original.optString("sourceEventId").ifBlank { null }, completed = false,
        historical = historical, exceptionId = id, originalDate = origin,
        exceptionStatus = if (marker) action else null,
        destinationDate = if (marker && action == "REPLACE") LocalDate.ofEpochDay(targetEpochDay) else null,
        sourceTemplateName = original.optString("templateName").ifBlank { null }
    )
}

internal fun applyCurrentExceptions(
    base: List<AgendaOccurrence>, states: List<OccurrenceExceptionEntity>,
    records: List<CompletionRecordEntity>, date: LocalDate
): List<AgendaOccurrence> {
    val active = states.filter { it.action != "RESTORE" }
    val result = base.filterNot { task -> active.any { it.matches(task) } ||
        task.historical && active.any { task.occurrenceKey == "exception:${it.id}" } }.mapNotNull { task ->
        val restored = states.firstOrNull { it.action == "RESTORE" && it.matches(task) }
        if (restored == null) task else {
            val start = maxOf(task.start, Instant.ofEpochMilli(restored.modifiedAt))
            if (start >= task.end) null else task.copy(start = start)
        }
    }.toMutableList()
    active.forEach { state ->
        if (state.originalEpochDay == date.toEpochDay()) result += state.task(marker = true)
        if (state.action == "REPLACE" && state.targetEpochDay == date.toEpochDay()) {
            val task = state.task(marker = false)
            val start = maxOf(task.start, Instant.ofEpochMilli(state.modifiedAt))
            // Keep ended commitments visible for completion, but do not fabricate elapsed intervals
            // for a change made after the entire scheduled block had already ended.
            result += task.copy(start = minOf(start, task.end), completed = records.any { it.occurrenceKey == task.occurrenceKey })
        }
    }
    return result.sortedWith(compareBy<AgendaOccurrence> { it.start }.thenBy { it.occurrenceKey })
}

internal fun applyHistoricalExceptions(
    base: List<AgendaOccurrence>, events: List<OccurrenceExceptionEventEntity>,
    records: List<CompletionRecordEntity>, date: LocalDate, zone: ZoneId
): List<AgendaOccurrence> {
    var result = base
    val additions = mutableListOf<AgendaOccurrence>()
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    events.groupBy { it.exceptionId }.values.forEach { history ->
        history.forEachIndexed { index, event ->
            val state = event.state
            if (state.action == "RESTORE") return@forEachIndexed
            val from = maxOf(dayStart, Instant.ofEpochMilli(event.effectiveAt))
            val until = minOf(dayEnd, history.getOrNull(index + 1)?.let { Instant.ofEpochMilli(it.effectiveAt) } ?: dayEnd)
            if (from >= until) return@forEachIndexed
            result = result.flatMap { task ->
                if (!state.matches(task) || task.end <= from || task.start >= until) listOf(task)
                else buildList {
                    val completion = records.firstOrNull { record -> task.completed &&
                        (task.occurrenceKey == record.occurrenceKey || task.occurrenceKey.startsWith(record.occurrenceKey + ":") ||
                            record.sourceEventId == null && record.sourceId == task.sourceId && record.sourceRevisionId == task.revisionId &&
                            record.titleSnapshot == task.title && record.startMinuteSnapshot == task.startMinute && record.endMinuteSnapshot == task.endMinute) }
                    if (task.start < from) {
                        val done = completion != null && completion.completedAt < from.toEpochMilli()
                        add(task.copy(end = from, completed = done, interrupted = !done,
                            occurrenceKey = "${task.occurrenceKey}:before:${event.sequence}"))
                    }
                    if (task.end > until) {
                        val done = completion != null && completion.completedAt >= until.toEpochMilli()
                        add(task.copy(start = until, completed = done, interrupted = task.interrupted && !done,
                            occurrenceKey = "${task.occurrenceKey}:after:${event.sequence}"))
                    }
                }
            }
            if (state.originalEpochDay == date.toEpochDay()) additions += state.task(marker = true, historical = true)
                .copy(occurrenceKey = "exception-origin:${state.id}:${event.sequence}")
            if (state.action == "REPLACE" && state.targetEpochDay == date.toEpochDay()) {
                val task = state.task(marker = false, historical = true)
                val start = maxOf(task.start, from)
                val end = minOf(task.end, until)
                if (start < end) {
                    val completed = records.any { it.occurrenceKey == task.occurrenceKey && it.completedAt >= event.effectiveAt &&
                        (index == history.lastIndex || it.completedAt < history[index + 1].effectiveAt) }
                    additions += task.copy(start = start, end = end, completed = completed,
                        interrupted = !completed && end < task.end,
                        occurrenceKey = if (index == history.lastIndex) task.occurrenceKey else "${task.occurrenceKey}:segment:${event.sequence}")
                }
            }
        }
    }
    return (result + additions).sortedWith(compareBy<AgendaOccurrence> { it.start }.thenBy { it.occurrenceKey })
}
