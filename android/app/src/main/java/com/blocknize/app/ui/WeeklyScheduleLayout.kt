package com.blocknize.app.ui

import com.blocknize.app.data.AgendaOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Duration
import java.time.temporal.TemporalAdjusters

internal fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal data class SchedulePlacement(
    val occurrence: AgendaOccurrence,
    val startMinute: Float,
    val endMinute: Float,
    val lane: Int,
    val laneCount: Int
)

/** Uses effective historical intervals, and never expands short blocks into neighbouring tasks. */
internal fun schedulePlacements(
    date: LocalDate,
    occurrences: List<AgendaOccurrence>,
    zone: ZoneId = ZoneId.systemDefault()
): List<SchedulePlacement> {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    val intervals = occurrences.flatMap { occurrence ->
        val start = maxOf(dayStart, occurrence.start)
        val end = minOf(dayEnd, occurrence.end)
        if (start >= end) return@flatMap emptyList()
        // Split at offset transitions: a repeated hour must not make a real interval disappear.
        val midnight = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        buildList {
            var cursor = start
            while (cursor < end) {
                val transition = zone.rules.nextTransition(cursor)
                val until = minOf(end, transition?.instant ?: end)
                val offset = zone.rules.getOffset(cursor).totalSeconds.toLong()
                val from = (Duration.between(midnight, cursor).seconds + offset) / 60f
                val to = (Duration.between(midnight, until).seconds + offset) / 60f
                if (to > from) add(SchedulePlacement(occurrence, from.coerceIn(0f, 1440f), to.coerceIn(0f, 1440f), 0, 1))
                cursor = until
            }
        }
    }.sortedWith(compareBy<SchedulePlacement> { it.startMinute }.thenBy { it.endMinute }.thenBy { it.occurrence.occurrenceKey })
    val result = mutableListOf<SchedulePlacement>()
    val group = mutableListOf<SchedulePlacement>()
    val laneEnds = mutableListOf<Float>()
    var groupEnd = -1f
    fun flush() {
        result += group.map { it.copy(laneCount = laneEnds.size) }
        group.clear()
        laneEnds.clear()
    }
    for (interval in intervals) {
        if (group.isNotEmpty() && interval.startMinute >= groupEnd) flush()
        val freeLane = laneEnds.indexOfFirst { it <= interval.startMinute }
        val lane = if (freeLane < 0) laneEnds.size.also { laneEnds += interval.endMinute }
        else freeLane.also { laneEnds[it] = interval.endMinute }
        group += interval.copy(lane = lane)
        groupEnd = if (group.size == 1) interval.endMinute else maxOf(groupEnd, interval.endMinute)
    }
    flush()
    return result
}

/** Union duration: simultaneous activities do not count the same clock time twice. */
internal fun scheduledMinutes(placements: List<SchedulePlacement>): Int {
    var end = 0f
    var total = 0f
    for (placement in placements.filter { it.occurrence.exceptionStatus == null }.sortedBy { it.startMinute }) {
        total += (placement.endMinute - maxOf(end, placement.startMinute)).coerceAtLeast(0f)
        end = maxOf(end, placement.endMinute)
    }
    return total.toInt()
}
