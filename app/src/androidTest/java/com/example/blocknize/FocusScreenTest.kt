package com.blocknize.app.ui.focus

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.blocknize.app.data.AgendaOccurrence
import com.blocknize.app.ui.theme.BlockNizeTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class FocusScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val start = Instant.parse("2026-11-03T09:00:00Z")
    private val task = AgendaOccurrence("focus-fixture", "WEEKLY", "task", LocalDate.parse("2026-11-03"),
        "Estudiar Kotlin", "", null, 540, 600, start, start.plusSeconds(3600), null, null, null, false)

    @Test fun onlyTheBlockCounterIsDisplayedWithoutTimerControls() {
        compose.setContent {
            BlockNizeTheme(0xFFA78BFA.toInt()) {
                FocusScreen(Modifier.width(360.dp).height(640.dp), task, start.plusSeconds(900),
                    ready = true, canChooseTask = false, keepAwake = false, onKeepAwake = {}, onChooseTask = {})
            }
        }
        compose.onNodeWithText("Estudiar Kotlin").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tiempo restante del bloque: 00:45:00").assertIsDisplayed()
        compose.onNodeWithTag("focus-ring").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.25f, 0f..1f)))
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        for (label in listOf("Cronómetro", "Cuenta atrás", "Iniciar", "Pausar", "Reiniciar"))
            compose.onNodeWithText(label).assertDoesNotExist()
    }

    @Test fun noCurrentTaskDoesNotDisplayAFakeCounter() {
        compose.setContent {
            BlockNizeTheme(0xFFA78BFA.toInt()) {
                FocusScreen(Modifier.width(360.dp).height(640.dp), null, start,
                    ready = true, canChooseTask = false, keepAwake = false, onKeepAwake = {}, onChooseTask = {})
            }
        }
        compose.onNodeWithText("Sin tarea en curso").assertIsDisplayed()
        compose.onNodeWithText("00:00:00").assertDoesNotExist()
    }

    @Test fun clockChangesReflectTheBlockEndAndNotASession() {
        val now = mutableStateOf(start.plusSeconds(900))
        compose.setContent {
            BlockNizeTheme(0xFFA78BFA.toInt()) {
                FocusScreen(Modifier.width(360.dp).height(640.dp), task, now.value,
                    ready = true, canChooseTask = false, keepAwake = false, onKeepAwake = {}, onChooseTask = {})
            }
        }
        compose.onNodeWithContentDescription("Tiempo restante del bloque: 00:45:00").assertIsDisplayed()
        compose.runOnIdle { now.value = start.plusSeconds(2400) }
        compose.onNodeWithContentDescription("Tiempo restante del bloque: 00:20:00").assertIsDisplayed()
    }

    @Test fun shortLandscapeLayoutFitsTheRingAndControlsWithoutScrolling() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        try {
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.setContent {
                BlockNizeTheme(0xFFA78BFA.toInt()) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                        FocusScreen(Modifier.width(640.dp).height(220.dp), task, start.plusSeconds(900),
                            ready = true, canChooseTask = true, keepAwake = false, onKeepAwake = {}, onChooseTask = {})
                    }
                }
            }
            compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
            compose.onNodeWithContentDescription("Tiempo restante del bloque: 00:45:00").assertIsDisplayed()
            compose.onNodeWithText("Cambiar tarea en curso").assertIsDisplayed()
            compose.onNodeWithText("Mantener pantalla encendida").assertIsDisplayed()
            val screen = compose.onNodeWithTag("focus-screen").fetchSemanticsNode().boundsInRoot
            val ring = compose.onNodeWithTag("focus-ring-graphic", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("The ring must remain square within pixel rounding: $ring", kotlin.math.abs(ring.width - ring.height) <= 1f)
            assertTrue("The ring must fit the viewport", ring.left >= screen.left && ring.top >= screen.top &&
                ring.right <= screen.right && ring.bottom <= screen.bottom)
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        }
    }
}
