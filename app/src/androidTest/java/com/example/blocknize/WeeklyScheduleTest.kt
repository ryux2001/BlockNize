package com.blocknize.app.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.blocknize.app.data.AgendaOccurrence
import com.blocknize.app.ui.theme.BlockNizeTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WeeklyScheduleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun weeklyGridShowsCurrentDayAndOpensTaskWithoutHidingOverlaps() {
        val date = LocalDate.parse("2026-11-02")
        val start = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant()
        val task = AgendaOccurrence("a", "WEEKLY", "a", date, "Estudiar", "", null,
            540, 600, start, start.plusSeconds(3600), null, null, null, false)
        var selected: AgendaOccurrence? = null
        compose.setContent {
            BlockNizeTheme(accentColor = 0xFFA78BFA.toInt()) {
                WeeklyScheduleGrid(Modifier.width(360.dp).height(600.dp), date,
                    mapOf(date to listOf(task, task.copy(occurrenceKey = "b", title = "Reunión"))),
                    start.plusSeconds(1800), onSelect = { selected = it })
            }
        }
        compose.onNodeWithContentDescription("Estudiar, 09:00–10:00, En curso, Semanal").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("a", selected?.occurrenceKey) }
        compose.onNodeWithContentDescription("Reunión, 09:00–10:00, En curso, Semanal").assertIsDisplayed()
    }
}
