package com.blocknize.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.blocknize.app.data.AgendaOccurrence
import com.blocknize.app.data.TemplateRow
import com.blocknize.app.ui.theme.BlockNizeTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VisualRefinementTest {
    @get:Rule val compose = createComposeRule()
    private val green = 0xFF4ADE80.toInt()

    @Test fun templateActionsRemainAvailableWithLargeTextOnNarrowScreen() {
        val events = mutableListOf<String>()
        val template = TemplateRow("a", "Primera semana de estudios y entrenamiento", 0, 9, false)
        compose.setContent {
            FixtureTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    TemplateManager(Modifier.width(320.dp).background(Color.Black), listOf(template), null,
                        onCreate = { events += "create" }, onEdit = { events += "edit" },
                        onRename = { events += "rename" }, onActivate = { events += "activate" },
                        onExport = { events += "export" }, onImport = { events += "import" })
                }
            }
        }
        capture("templates-large-text")
        compose.onNodeWithText("Editar bloques").assertIsDisplayed().performClick()
        compose.onNodeWithText("Activar").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Opciones de ${template.name}").performClick()
        compose.onNodeWithText("Exportar").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Opciones de ${template.name}").performClick()
        compose.onNodeWithText("Renombrar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Crear semana").performClick()
        compose.onNodeWithText("Importar JSON").performClick()
        compose.runOnIdle { assertEquals(listOf("edit", "activate", "export", "rename", "create", "import"), events) }
    }

    @Test fun allWeekdaysRemainVisibleAndSelectableInNarrowDialogs() {
        val mask = mutableIntStateOf(1)
        compose.setContent {
            FixtureTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    WeekdaySelector(mask.intValue, { mask.intValue = mask.intValue xor (1 shl it) },
                        Modifier.width(280.dp).background(Color.Black), multiple = true)
                }
            }
        }
        listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithContentDescription("Domingo").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Lunes").assertIsSelected()
        capture("weekdays-large-text")
    }

    @Test fun historicalCardsKeepFullSchedulesAndUndoAvailableWithLargeText() {
        var undoCount = 0
        val start = Instant.parse("2026-10-29T12:04:03Z")
        val item = AgendaOccurrence("key", "WEEKLY", "block", LocalDate.parse("2026-10-29"),
            "Instituto y preparación de exámenes", "", "Estudios", 510, 810,
            start, start.plusSeconds(35), "template", "revision", "event", true,
            historical = true, originalStart = start.minusSeconds(7200), sourceTemplateName = "Primera semana")
        compose.setContent {
            FixtureTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Column(Modifier.width(320.dp).background(Color.Black).padding(8.dp)) {
                        CalendarOccurrenceCard(item, start)
                        HistoryOccurrenceCard(item, "Completada", { undoCount++ }, true)
                    }
                }
            }
        }
        compose.onNodeWithText("Deshacer").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, undoCount) }
        capture("historical-cards-large-text")
    }

    @Test fun calendarStillOffersEveryDayAndMonthNavigation() {
        val date = LocalDate.parse("2026-10-29")
        var chosen = date
        var month = YearMonth.from(date)
        compose.setContent {
            FixtureTheme {
                Column(Modifier.width(360.dp).background(Color.Black).padding(8.dp)) {
                    MonthDateGrid(YearMonth.from(date), date, date, { month = it }, { chosen = it })
                }
            }
        }
        compose.onNodeWithText("31", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(date.withDayOfMonth(31), chosen) }
        compose.onNodeWithContentDescription("Mes siguiente").performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 11), month) }
        capture("calendar")
    }

    @Test fun accentRolesHaveReadableContrastAndDoNotFallBackToPurple() {
        val values = mutableListOf<Pair<Color, Color>>()
        compose.setContent {
            listOf(green, 0xFFA78BFA.toInt(), 0xFFFACC15.toInt(), 0xFF000000.toInt()).forEach { accent ->
                BlockNizeTheme(accent) {
                    val scheme = MaterialTheme.colorScheme
                    values.add(scheme.primary to scheme.onPrimary)
                    values.add(scheme.secondaryContainer to scheme.onSecondaryContainer)
                }
            }
        }
        compose.runOnIdle {
            values.forEach { (a, b) ->
                val contrast = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
                assertTrue("Insufficient text contrast: $contrast", contrast >= 4.5f)
            }
        }
    }

    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "refinement")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun FixtureTheme(content: @Composable () -> Unit) {
        BlockNizeTheme(green) { Surface(color = Color.Black, contentColor = Color.White, content = content) }
    }
}
