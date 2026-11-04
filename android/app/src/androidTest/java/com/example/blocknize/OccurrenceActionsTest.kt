package com.blocknize.app.ui.occurrence

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.blocknize.app.AppViewModel
import com.blocknize.app.data.AgendaOccurrence
import com.blocknize.app.ui.theme.BlockNizeTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OccurrenceActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(completed: Boolean = false, marker: Boolean = false) {
        val date = LocalDate.now().plusDays(1)
        val start = date.atStartOfDay(ZoneId.systemDefault()).plusHours(9).toInstant()
        val task = AgendaOccurrence("ui-fixture", "WEEKLY", "ui-block", date, "Actividad de prueba", "", null,
            540, 600, start, start.plusSeconds(3600), "ui-template", null, null, completed,
            exceptionId = if (marker) "ui-exception" else null, exceptionStatus = if (marker) "SKIP" else null)
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.setContent { BlockNizeTheme(0xFFA78BFA.toInt()) { OccurrenceActions(task, model) } }
        compose.onNodeWithText("Solo esta ocurrencia").performClick()
    }

    @Test fun weeklyActionsOfferScopedEditMoveAndSkip() {
        show()
        for (label in listOf("Modificar solo esta vez", "Reprogramar", "Omitir esta vez")) compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText("Modificar solo esta vez").performClick()
        compose.onNodeWithText("Este cambio afecta únicamente a esta ocurrencia. La plantilla semanal no se modificará.").assertIsDisplayed()
        compose.onNodeWithText("Fecha · AAAA-MM-DD").assertIsNotEnabled()
    }

    @Test fun reprogrammingAllowsChangingTheDate() {
        show()
        compose.onNodeWithText("Reprogramar").performClick()
        compose.onNodeWithText("Fecha · AAAA-MM-DD").assertIsEnabled()
    }

    @Test fun completedActivityRequiresUndoBeforeChangingIt() {
        show(completed = true)
        compose.onNodeWithText("Reprogramar").assertIsNotEnabled()
        compose.onNodeWithText("Omitir esta vez").assertIsNotEnabled()
        compose.onNodeWithText("Deshaz primero la finalización").assertIsDisplayed()
    }

    @Test fun omittedActivityOffersRestoreWithExplicitConfirmation() {
        show(marker = true)
        compose.onNodeWithText("Restaurar programación original").performClick()
        compose.onNodeWithText("Confirmar").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Confirmar").assertDoesNotExist()
    }
}
