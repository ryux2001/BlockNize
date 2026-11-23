package com.blocknize.app.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.glance.appwidget.updateAll
import com.blocknize.app.MainActivity
import com.blocknize.app.data.AccentStore
import com.blocknize.app.data.AgendaOccurrence
import com.blocknize.app.data.AppDatabase
import com.blocknize.app.data.TemplateRepository
import com.blocknize.app.data.widgetBackgroundArgb
import com.blocknize.app.domain.currentTasks
import com.blocknize.app.domain.nextTask
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

// ColorProvider(Int) expects a resource ID, not an ARGB value.
// Use Compose Color explicitly so RemoteViews receives literal colors.
private val White = ColorProvider(Color.White)
private val Muted = ColorProvider(Color(0xFFB8B8B8))

class CurrentTaskWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val clock = workspaceClock(context)
        val now = clock.instant()
        val repository = TemplateRepository(AppDatabase.get(context), clock)
        val today = repository.getAgenda(LocalDate.now(clock))
        val current = currentTasks(today, now)
        val next = if (current.isEmpty()) nextTask(today, now) else null
        val accent = AccentStore(context).accent.first()
        val opacity = AccentStore(context).widgetOpacity.first()

        provideContent {
            CurrentTaskWidgetContent(context, current, next, now, accent, opacity)
        }
    }
}

@Composable
internal fun CurrentTaskWidgetContent(
    context: Context, current: List<AgendaOccurrence>, next: AgendaOccurrence?, now: java.time.Instant, accent: Int,
    opacity: Int = AccentStore.DEFAULT_WIDGET_OPACITY
) {
    val height = LocalSize.current.height
    val compact = height < 140.dp
    val ringSize = (height - 64.dp).coerceIn(32.dp, 64.dp)
    Column(GlanceModifier.fillMaxSize().background(ColorProvider(Color(widgetBackgroundArgb(opacity)))).padding(12.dp)
        .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text("BLOCKNIZE", modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(Color(accent)), fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Text("Actualizar", modifier = GlanceModifier.padding(vertical = 2.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>()),
                style = TextStyle(color = Muted, fontSize = 11.sp))
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                val chosen = current.firstOrNull()
                Text(chosen?.title ?: "Sin tarea ahora", style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = if (compact) 1 else 2)
                Spacer(GlanceModifier.height(4.dp))
                if (chosen != null) {
                    Text("${chosen.startMinute.toClock()}–${chosen.endMinute.toClock()}${chosen.tagName?.let { " · $it" }.orEmpty()}",
                        style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
                    if (current.size > 1) Text("+${current.size - 1} más en curso", style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1)
                } else if (next != null) {
                    Text("Siguiente: ${next.title} · ${next.startMinute.toClock()}", style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
                }
            }
            current.firstOrNull()?.let { chosen ->
                val progress = chosen.progressAt(now)
                val percentage = (progress * 100).toInt()
                Spacer(GlanceModifier.width(12.dp))
                Box(GlanceModifier.size(ringSize), contentAlignment = Alignment.Center) {
                    Image(provider = ImageProvider(progressRing(progress, accent)), contentDescription = "Progreso $percentage por ciento",
                        contentScale = ContentScale.Fit, modifier = GlanceModifier.fillMaxSize())
                    Text("$percentage%", style = TextStyle(color = White, fontSize = if (ringSize < 48.dp) 10.sp else 14.sp,
                        fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

internal fun progressRing(progress: Float, accentArgb: Int): Bitmap {
    val size = 256
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
    val canvas = Canvas(bitmap)
    val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = android.graphics.Color.rgb(64, 64, 64)
    }
    val arc = Paint(track).apply { strokeCap = Paint.Cap.ROUND; color = accentArgb }
    // Leave transparent padding beyond the stroke, including its rounded caps.
    val bounds = RectF(16f, 16f, size - 16f, size - 16f)
    canvas.drawOval(bounds, track)
    val fraction = progress.coerceIn(0f, 1f)
    if (fraction >= 1f) canvas.drawOval(bounds, arc)
    else if (fraction > 0f) canvas.drawArc(bounds, -90f, fraction * 360f, false, arc)
    return bitmap
}

class CurrentTaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrentTaskWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefreshWorker.ensurePeriodic(context)
        WidgetRefreshWorker.request(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.ensurePeriodic(context)
        WidgetRefreshWorker.request(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(WidgetRefreshWorker.PERIODIC_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(WidgetRefreshWorker.TRANSITION_NAME)
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetRefreshWorker.request(context)
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        com.blocknize.app.data.FullBackupRepository(applicationContext).recoverPending()
        // Upgrade previously scheduled work even when the app has not been reopened.
        ensurePeriodic(applicationContext)
        CurrentTaskWidget().updateAll(applicationContext)
        scheduleNextTransition(applicationContext)
        android.util.Log.d("BlockNizeWidget", "Refresh completed; attempt=$runAttemptCount")
        Result.success()
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        // Do not log exception messages: provider/SQLite errors can include user data.
        android.util.Log.w("BlockNizeWidget", "Refresh failed; type=${error.javaClass.simpleName}; attempt=$runAttemptCount")
        Result.retry()
    }

    companion object {
        const val PERIODIC_NAME = "blocknize-widget-periodic"
        const val TRANSITION_NAME = "blocknize-widget-transition"
        private const val IMMEDIATE_NAME = "blocknize-widget-immediate"

        fun request(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private suspend fun scheduleNextTransition(context: Context) {
            val clock = workspaceClock(context)
            val now = clock.instant()
            val repository = TemplateRepository(AppDatabase.get(context), clock)
            val occurrences = (repository.getAgenda(LocalDate.now(clock)) + repository.getAgenda(LocalDate.now(clock).plusDays(1)))
                .filter { it.exceptionStatus == null && !it.historical && !it.completed }
            val next = occurrences.flatMap { listOf(it.start, it.end) }.filter { it.isAfter(now) }.minOrNull()
                ?: LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant()
            val delay = Duration.between(now, next).toMillis().coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
            android.util.Log.d("BlockNizeWidget", "Next transition=$next; delayMs=$delay")
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TRANSITION_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

private suspend fun workspaceClock(context: Context): Clock {
    val state = AppDatabase.get(context).dao().syncState()
    val zone = if (state?.mode == "CLOUD") {
        state.accountTimeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    } else ZoneId.systemDefault()
    return Clock.system(zone)
}

private fun Int.toClock(): String = "%02d:%02d".format(this / 60, this % 60)
