package com.blocknize.app.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blocknize.app.AppViewModel
import com.blocknize.app.data.AgendaOccurrence
import com.blocknize.app.domain.currentTasks
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FocusRoute(
    modifier: Modifier,
    appModel: AppViewModel,
    onOpenSettings: (() -> Unit)? = null,
    focusModel: FocusViewModel = viewModel()
) {
    val agenda by appModel.todayAgenda.collectAsStateWithLifecycle()
    val agendaDate by appModel.agendaDate.collectAsStateWithLifecycle()
    val selectedKey by focusModel.selectedOccurrenceKey.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val now by produceState(Instant.now(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Instant.now()
                delay(1_000)
            }
        }
    }
    val date = now.atZone(ZoneId.systemDefault()).toLocalDate()
    val ready = agendaDate == date
    LaunchedEffect(date) { appModel.refreshClock() }
    val candidates = currentTasks(agenda, now)
    val selected = candidates.firstOrNull { it.occurrenceKey == selectedKey } ?: candidates.firstOrNull()
    var chooseTask by remember { mutableStateOf(false) }
    var keepAwake by rememberSaveable { mutableStateOf(false) }
    KeepFocusScreenOn(keepAwake)

    FocusScreen(modifier, selected, now, ready, canChooseTask = candidates.size > 1,
        keepAwake = keepAwake, onKeepAwake = { keepAwake = it }, onChooseTask = { chooseTask = true },
        onOpenSettings = onOpenSettings)

    if (chooseTask) AlertDialog(onDismissRequest = { chooseTask = false }, title = { Text("Tareas en curso") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            candidates.forEach { task ->
                TextButton(onClick = { focusModel.selectTask(task.occurrenceKey); chooseTask = false }) { Text(task.title) }
            }
        } }, confirmButton = { TextButton(onClick = { chooseTask = false }) { Text("Cerrar") } })
}

@Composable
internal fun FocusScreen(
    modifier: Modifier,
    task: AgendaOccurrence?,
    now: Instant,
    ready: Boolean,
    canChooseTask: Boolean,
    keepAwake: Boolean,
    onKeepAwake: (Boolean) -> Unit,
    onChooseTask: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("focus-screen")) {
        val wide = maxWidth > maxHeight && maxWidth >= 440.dp
        val padding = if (maxHeight < 300.dp) 12.dp else 20.dp
        if (wide) {
            val diameter = minOf(maxHeight - padding * 2, (maxWidth - padding * 2) * 0.52f).coerceAtLeast(0.dp)
            Row(Modifier.fillMaxSize().padding(padding), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (task != null && ready) FocusRing(task, now, Modifier.size(diameter))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    FocusTaskInfo(task, ready)
                    FocusOptions(keepAwake, onKeepAwake, canChooseTask && ready, onChooseTask, onOpenSettings)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                FocusTaskInfo(task, ready)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (task != null && ready) {
                        val diameter = minOf(maxWidth, maxHeight).coerceAtLeast(0.dp)
                        FocusRing(task, now, Modifier.size(diameter))
                    }
                }
                FocusOptions(keepAwake, onKeepAwake, canChooseTask && ready, onChooseTask, onOpenSettings)
            }
        }
    }
}

@Composable
private fun FocusTaskInfo(task: AgendaOccurrence?, ready: Boolean) {
    Text(if (!ready) "Cargando agenda…" else task?.title ?: "Sin tarea en curso",
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (task != null && ready) {
        val format = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        Text("${format.format(task.start)} – ${format.format(task.end)}", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
    } else if (ready) Text("No hay una actividad programada para este momento.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun FocusOptions(
    keepAwake: Boolean, onKeepAwake: (Boolean) -> Unit, canChoose: Boolean, onChoose: () -> Unit, onOpenSettings: (() -> Unit)?
) {
    if (canChoose) TextButton(onClick = onChoose) { Text("Cambiar tarea en curso", maxLines = 1, overflow = TextOverflow.Ellipsis) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Checkbox(checked = keepAwake, onCheckedChange = onKeepAwake)
        Text("Mantener pantalla encendida", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (onOpenSettings != null) IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("focus-settings")) {
            Icon(Icons.Filled.Settings, contentDescription = "Configuración")
        }
    }
}

@Composable
private fun FocusRing(task: AgendaOccurrence, now: Instant, modifier: Modifier) {
    val progress = task.progressAt(now)
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.testTag("focus-ring").progressSemantics(progress), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, maxHeight)
        Canvas(Modifier.fillMaxSize().testTag("focus-ring-graphic")) {
            val stroke = (size.minDimension * 0.025f).coerceIn(2.dp.toPx(), 6.dp.toPx())
            val inset = stroke + 4.dp.toPx()
            // Rounding dp constraints can differ by one pixel between axes; draw a true circle anyway.
            val diameterPx = (size.minDimension - inset * 2).coerceAtLeast(0f)
            val bounds = Size(diameterPx, diameterPx)
            val origin = Offset((size.width - diameterPx) / 2, (size.height - diameterPx) / 2)
            drawArc(track, -90f, 360f, false, origin, bounds, style = Stroke(stroke))
            if (progress > 0f) drawArc(accent, -90f, progress * 360f, false, origin, bounds,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val text = formatFocusTime(remainingBlockMillis(task, now))
        Column(Modifier.fillMaxWidth(0.78f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light,
                fontSize = (diameter.value * 0.78f / (5.2f * fontScale)).coerceIn(1f, 96f).sp, maxLines = 1,
                modifier = Modifier.semantics { contentDescription = "Tiempo restante del bloque: $text" })
            if (diameter >= 120.dp) Text("TIEMPO RESTANTE", color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = (diameter.value * 0.05f / fontScale).coerceIn(1f, 14f).sp, maxLines = 1)
        }
    }
}

@Composable
private fun KeepFocusScreenOn(enabled: Boolean) {
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(view, owner, enabled) {
        val previous = view.keepScreenOn
        fun apply() { view.keepScreenOn = previous || enabled && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        val observer = LifecycleEventObserver { _, _ -> apply() }
        owner.lifecycle.addObserver(observer)
        apply()
        onDispose { owner.lifecycle.removeObserver(observer); view.keepScreenOn = previous }
    }
}
