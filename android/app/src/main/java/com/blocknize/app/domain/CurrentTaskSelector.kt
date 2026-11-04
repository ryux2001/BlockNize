package com.blocknize.app.domain

import com.blocknize.app.data.AgendaOccurrence
import java.time.Instant

/** One definition of actionable, current activities for app screens and the widget. */
fun currentTasks(agenda: List<AgendaOccurrence>, now: Instant): List<AgendaOccurrence> = agenda
    .filter { it.exceptionStatus == null && !it.completed && !it.historical && !it.interrupted && now >= it.start && now < it.end }
    .sortedWith(compareByDescending<AgendaOccurrence> { it.start }.thenBy { it.title }.thenBy { it.occurrenceKey })

fun nextTask(agenda: List<AgendaOccurrence>, now: Instant): AgendaOccurrence? = agenda
    .filter { it.exceptionStatus == null && !it.completed && !it.historical && !it.interrupted && it.start > now }
    .minWithOrNull(compareBy<AgendaOccurrence> { it.start }.thenBy { it.title }.thenBy { it.occurrenceKey })
