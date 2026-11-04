package com.blocknize.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Wall-clock scheduling: gaps shift forward; overlaps use the earlier offset (java.time policy). */
fun civilInstant(date: LocalDate, minute: Int, zone: ZoneId): Instant {
    require(minute in 0..1440)
    return date.atStartOfDay().plusMinutes(minute.toLong()).atZone(zone).toInstant()
}

/** A spring gap can collapse a local interval; never return a negative real duration. */
fun civilInterval(date: LocalDate, start: Int, end: Int, zone: ZoneId): Pair<Instant, Instant> {
    val from = civilInstant(date, start, zone)
    return from to maxOf(from, civilInstant(date, end, zone))
}

fun parseScheduleMinute(input: String, allowEndOfDay: Boolean = false): Int? {
    val parts = input.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (allowEndOfDay && hour == 24 && minute == 0) return 1440
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
