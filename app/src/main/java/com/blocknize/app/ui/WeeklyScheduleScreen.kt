package com.blocknize.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blocknize.app.AppViewModel
import com.blocknize.app.data.AgendaOccurrence
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface WeekLoad {
    data object Loading : WeekLoad
    data class Loaded(val days: Map<LocalDate, List<AgendaOccurrence>>) : WeekLoad
    data class Failed(val message: String) : WeekLoad
}

@Composable
internal fun WeeklyScheduleScreen(modifier: Modifier, model: AppViewModel) {
    val now by model.now.collectAsStateWithLifecycle()
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
    var startText by rememberSaveable { mutableStateOf(weekStart(today).toString()) }
    val start = LocalDate.parse(startText)
    var retry by remember { mutableIntStateOf(0) }
    var focusToday by remember { mutableIntStateOf(0) }
    val load by produceState<WeekLoad>(WeekLoad.Loading, start, today, retry) {
        value = WeekLoad.Loading
        try {
            model.observeWeek(start).collect { value = WeekLoad.Loaded(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            value = WeekLoad.Failed(error.message ?: "No se pudo cargar la semana.")
        }
    }
    var selected by remember(start) { mutableStateOf<AgendaOccurrence?>(null) }
    val days = (load as? WeekLoad.Loaded)?.days
    // Keep an open detail in sync with edits/completions, and dismiss removed activities.
    val detail = selected?.let { old -> days?.get(old.date)?.firstOrNull { it.occurrenceKey == old.occurrenceKey } }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { startText = start.minusWeeks(1).toString() }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Semana anterior")
            }
            val format = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es"))
            Text("${start.format(format)} – ${start.plusDays(6).format(format)}",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { startText = start.plusWeeks(1).toString() }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Semana siguiente")
            }
            TextButton(onClick = { startText = weekStart(today).toString(); focusToday++; model.refreshClock() }) { Text("Hoy") }
        }
        Text("Pasado: historial · Hoy y futuro: agenda vigente",
            modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (val result = load) {
            WeekLoad.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            is WeekLoad.Failed -> Column(Modifier.padding(16.dp)) {
                Text(result.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text("Reintentar") }
            }
            is WeekLoad.Loaded -> {
                if (result.days.values.all { it.isEmpty() }) Text("No hay actividades esta semana.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                WeeklyScheduleGrid(Modifier.weight(1f), start, result.days, now, onSelect = { selected = it }, focusRequest = focusToday)
            }
        }
    }
    if (detail != null) AlertDialog(onDismissRequest = { selected = null },
        title = { Text(detail.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("es")))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CalendarOccurrenceCard(detail, now)
                if (detail.date == today) Text("Se muestra la programación vigente. La actividad anterior está en Registro.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Cerrar") } })
}

@Composable
internal fun WeeklyScheduleGrid(
    modifier: Modifier,
    start: LocalDate,
    days: Map<LocalDate, List<AgendaOccurrence>>,
    now: Instant,
    onSelect: (AgendaOccurrence) -> Unit,
    focusRequest: Int = 0
) {
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()
    val dates = remember(start) { (0L..6L).map(start::plusDays) }
    val placements = remember(days, zone) { days.mapValues { (date, tasks) -> schedulePlacements(date, tasks, zone) } }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val density = LocalDensity.current
    val minuteHeight = 1.2.dp
    val dayHeight = minuteHeight * 1440
    val gutter = 52.dp
    val accent = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(modifier.fillMaxSize()) {
        val visibleDays = if (maxWidth >= 700.dp) 7 else 3
        val dayWidth = ((maxWidth - gutter) / visibleDays).coerceAtLeast(92.dp)
        LaunchedEffect(start, dayWidth, focusRequest) {
            val targetDay = if (today in dates) today.dayOfWeek.value - 1 else 0
            horizontal.scrollTo(with(density) { (dayWidth * targetDay).roundToPx() })
            val targetMinute = if (today in dates) (now.atZone(zone).hour * 60 - 60).coerceAtLeast(0)
                else (placements.values.flatten().minOfOrNull { it.startMinute.toInt() } ?: 480) - 30
            vertical.scrollTo(with(density) { (minuteHeight * targetMinute.coerceAtLeast(0)).roundToPx() })
        }
        Column {
            Row(Modifier.fillMaxWidth().height(64.dp)) {
                Box(Modifier.width(gutter).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("Hora", style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
                    dates.forEach { date ->
                        Column(Modifier.width(dayWidth).fillMaxHeight()
                            .background(if (date == today) accent.copy(alpha = 0.15f) else Color(0xFF101010))
                            .border(0.5.dp, gridColor).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.format(DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("es"))),
                                color = if (date == today) accent else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold, maxLines = 1)
                            val minutes = scheduledMinutes(placements[date].orEmpty())
                            Text("${minutes / 60}h ${minutes % 60}m", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.weight(1f).verticalScroll(vertical)) {
                Box(Modifier.width(gutter).height(dayHeight)) {
                    for (hour in 0..23) Text("%02d:00".format(hour),
                        modifier = Modifier.offset(y = minuteHeight * (hour * 60)).padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
                    dates.forEach { date ->
                        Box(Modifier.width(dayWidth).height(dayHeight)) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawLine(gridColor, Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
                                for (hour in 0..24) {
                                    val y = (minuteHeight * (hour * 60)).toPx()
                                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx())
                                }
                            }
                            placements[date].orEmpty().forEach { placement ->
                                val width = dayWidth / placement.laneCount
                                ScheduleBlock(placement, now, Modifier
                                    .offset(x = width * placement.lane, y = minuteHeight * placement.startMinute)
                                    .width(width).height(minuteHeight * (placement.endMinute - placement.startMinute)), onSelect)
                            }
                            if (date == today) {
                                val time = now.atZone(zone).toLocalTime()
                                Box(Modifier.offset(y = minuteHeight * (time.toSecondOfDay() / 60f))
                                    .fillMaxWidth().height(2.dp).background(accent)
                                    .semantics { contentDescription = "Hora actual ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}" })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleBlock(placement: SchedulePlacement, now: Instant, modifier: Modifier, onSelect: (AgendaOccurrence) -> Unit) {
    val item = placement.occurrence
    val accent = MaterialTheme.colorScheme.primary
    val current = !item.completed && now >= item.start && now < item.end
    val status = when {
        item.completed -> "Completada"
        item.interrupted -> "Interrumpida"
        current -> "En curso"
        now < item.start -> "Próxima"
        else -> "Pendiente"
    }
    val duration = placement.endMinute - placement.startMinute
    val schedule = "%02d:%02d–%02d:%02d".format(item.startMinute / 60, item.startMinute % 60, item.endMinute / 60, item.endMinute % 60)
    Column(modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(4.dp))
        .background(if (current) accent.copy(alpha = 0.28f) else Color(0xFF202020))
        .border(if (current) 2.dp else 0.5.dp, if (current || item.completed) accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
        .clickable { onSelect(item) }
        .semantics { contentDescription = "${item.title}, $schedule, $status, ${if (item.sourceType == "ONE_OFF") "Temporal" else "Semanal"}" }
        .padding(if (duration >= 20) 5.dp else 0.dp)) {
        Text("${if (item.completed) "✓ " else ""}${item.title}", color = Color.White,
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            maxLines = if (duration >= 50) 3 else 1, overflow = TextOverflow.Ellipsis)
        if (duration >= 45) Text(schedule, color = Color(0xFFB8B8B8), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (duration >= 75) Text(if (item.sourceType == "ONE_OFF") "$status · Temporal" else status,
            color = Color(0xFFB8B8B8), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (duration >= 100 && !item.tagName.isNullOrBlank()) Text(item.tagName,
            color = Color(0xFFB8B8B8), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
