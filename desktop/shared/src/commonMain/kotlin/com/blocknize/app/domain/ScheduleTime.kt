package com.blocknize.app.domain

data class ScheduleBlock(
    val id: String,
    val title: String,
    val description: String,
    val tagName: String?,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int
) {
    val isValid: Boolean
        get() = id.isNotBlank() && title.isNotBlank() && daysMask in 1..127 &&
            ScheduleWindow(startMinute, endMinute).isValid
}

data class ScheduleTemplate(val id: String, val name: String, val blocks: List<ScheduleBlock>) {
    val isValid: Boolean
        get() = id.isNotBlank() && name.isNotBlank() && blocks.all(ScheduleBlock::isValid) &&
            blocks.map(ScheduleBlock::id).distinct().size == blocks.size
}

/** Validates a same-day schedule interval. 24:00 is allowed as an end, never as a start. */
data class ScheduleWindow(val startMinute: Int, val endMinute: Int) {
    val isValid: Boolean
        get() = startMinute in 0..1439 && endMinute in 1..1440 && startMinute < endMinute
}

/** Converts a wall-clock value into minutes after midnight; optionally accepts 24:00. */
fun parseScheduleMinute(input: String, allowEndOfDay: Boolean = false): Int? {
    val parts = input.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (allowEndOfDay && hour == 24 && minute == 0) return 1440
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
