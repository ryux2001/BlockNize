package com.blocknize.app.ui.focus

import com.blocknize.app.data.AgendaOccurrence
import java.time.Duration
import java.time.Instant
import java.util.Locale

fun remainingBlockMillis(task: AgendaOccurrence, now: Instant): Long =
    Duration.between(now, task.end).toMillis().coerceAtLeast(0)

/** Round upward so the block is not shown as finished a fraction of a second early. */
fun formatFocusTime(millis: Long): String {
    val positive = millis.coerceAtLeast(0)
    val seconds = positive / 1000 + if (positive % 1000 != 0L) 1 else 0
    return "%02d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
}
