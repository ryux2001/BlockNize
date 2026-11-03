package com.blocknize.app.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ImageView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.GlanceId
import android.content.Context
import com.blocknize.app.data.AgendaOccurrence
import java.time.Instant
import java.time.LocalDate
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    @Test fun progressRingIsSquareFullyVisibleAndHasACenteredPercentage() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val start = Instant.parse("2026-10-29T08:30:00Z")
        val task = AgendaOccurrence("widget-fixture", "WEEKLY", "task", LocalDate.parse("2026-10-29"),
            "Instituto", "", "Estudios", 510, 810, start, start.plusSeconds(18_000),
            "template", "revision", "event", false)
        for (size in listOf(DpSize(250.dp, 110.dp), DpSize(320.dp, 110.dp), DpSize(320.dp, 160.dp), DpSize(400.dp, 180.dp))) {
            for (percent in listOf(0, 25, 100)) {
                val now = start.plusSeconds(180L * percent)
                val widget = object : GlanceAppWidget() {
                    override suspend fun provideGlance(context: Context, id: GlanceId) {
                        provideContent { CurrentTaskWidgetContent(context, listOf(task), null, now, 0xFF4ADE80.toInt()) }
                    }
                }
                val remoteViews = widget.compose(context, size = size)
                instrumentation.runOnMainSync {
                    val view = remoteViews.apply(context, FrameLayout(context))
                    val density = context.resources.displayMetrics.density
                    val width = (size.width.value * density).toInt()
                    val height = (size.height.value * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    val image = descendants(view).filterIsInstance<ImageView>().single {
                        it.contentDescription?.toString() == "Progreso $percent por ciento"
                    }
                    val label = descendants(view).filterIsInstance<TextView>().single { it.text.toString() == "$percent%" }
                    assertEquals("Progress image must remain square", image.width, image.height)
                    val imageBounds = Rect(0, 0, image.width, image.height)
                    (view as ViewGroup).offsetDescendantRectToMyCoords(image, imageBounds)
                    assertTrue("Ring is clipped vertically: $imageBounds in $height", imageBounds.top >= 0 && imageBounds.bottom <= height)
                    assertTrue("Ring is clipped horizontally", imageBounds.left >= 0 && imageBounds.right <= width)
                    val textBounds = Rect(0, 0, label.width, label.height)
                    view.offsetDescendantRectToMyCoords(label, textBounds)
                    assertTrue("Percentage must be centered", kotlin.math.abs(imageBounds.centerX() - textBounds.centerX()) <= 2 &&
                        kotlin.math.abs(imageBounds.centerY() - textBounds.centerY()) <= 2)
                    if (percent == 25) {
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        view.draw(Canvas(bitmap))
                        val dir = File(context.getExternalFilesDir(null), "widget-review").apply { mkdirs() }
                        File(dir, "widget-${size.width.value.toInt()}x${size.height.value.toInt()}.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                }
            }
        }
    }

    @Test fun ringBitmapHasSafeTransparentMarginsAndDistinctEmptyAndFullStates() {
        for (progress in listOf(0f, 0.25f, 1f)) {
            val bitmap = progressRing(progress, 0xFF4ADE80.toInt())
            assertEquals(bitmap.width, bitmap.height)
            for (index in 0 until bitmap.width) {
                assertEquals(0, bitmap.getPixel(index, 0))
                assertEquals(0, bitmap.getPixel(index, bitmap.height - 1))
                assertEquals(0, bitmap.getPixel(0, index))
                assertEquals(0, bitmap.getPixel(bitmap.width - 1, index))
            }
        }
        assertEquals(0xFF404040.toInt(), progressRing(0f, 0xFF4ADE80.toInt()).getPixel(128, 16))
        assertEquals(0xFF4ADE80.toInt(), progressRing(1f, 0xFF4ADE80.toInt()).getPixel(128, 16))
    }

    @Test fun widgetRemoteViewsInflateInAHostWithoutTreatingArgbAsResourceIds() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val provider = AppWidgetManager.getInstance(appContext).getInstalledProviders()
            .firstOrNull { it.provider == ComponentName(appContext, CurrentTaskWidgetReceiver::class.java) }
        assertNotNull("Widget provider must be registered", provider)

        for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val configuration = Configuration(appContext.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            val context = appContext.createConfigurationContext(configuration)
            for (size in listOf(DpSize(250.dp, 110.dp), DpSize(320.dp, 160.dp))) {
                val remoteViews = CurrentTaskWidget().compose(context, size = size)
                instrumentation.runOnMainSync {
                    // Direct apply must throw on broken resource IDs, instead of allowing
                    // AppWidgetHostView to swallow the exception and show its error layout.
                    val inflated = remoteViews.apply(context, FrameLayout(context))
                    val labels = collectText(inflated)
                    assertTrue("Expected widget content, got $labels", "BLOCKNIZE" in labels)
                    assertTrue("Refresh action is missing", "Actualizar" in labels)
                    val host = AppWidgetHostView(context)
                    host.setAppWidget(-1, provider)
                    host.updateAppWidget(remoteViews)
                    assertTrue("Host displayed an error instead of content", "BLOCKNIZE" in collectText(host))
                }
            }
        }
    }

    private fun collectText(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(collectText(view.getChildAt(index)))
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
}
