package com.blocknize.app.ui.focus

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.blocknize.app.media.MediaSessionRepository
import com.blocknize.app.media.MediaSessionUiState
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
    val context = LocalContext.current
    val mediaRepository = remember(context) { MediaSessionRepository(context) }
    var resumeCount by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val mediaState by produceState(
        initialValue = if (mediaRepository.hasNotificationAccess()) MediaSessionUiState.NoSession
        else MediaSessionUiState.AccessRequired,
        mediaRepository,
        resumeCount,
        owner
    ) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            mediaRepository.observe().collect { value = it }
        }
    }
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
        onOpenSettings = onOpenSettings, mediaState = mediaState,
        onRequestMediaAccess = {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        },
        onMediaPrevious = { mediaRepository.skipPrevious() },
        onMediaToggle = { playing -> mediaRepository.togglePlayback(playing) },
        onMediaNext = { mediaRepository.skipNext() })

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
    onOpenSettings: (() -> Unit)? = null,
    mediaState: MediaSessionUiState = MediaSessionUiState.AccessRequired,
    onRequestMediaAccess: () -> Unit = {},
    onMediaPrevious: () -> Unit = {},
    onMediaToggle: (Boolean) -> Unit = {},
    onMediaNext: () -> Unit = {}
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("focus-screen")) {
        val wide = maxWidth > maxHeight && maxWidth >= 440.dp
        val padding = if (maxHeight < 300.dp) 12.dp else 20.dp
        if (wide) {
            val compact = maxHeight < 300.dp
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp)) {
                    BoxWithConstraints(Modifier.weight(0.92f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val diameter = minOf(maxWidth, maxHeight).coerceAtLeast(0.dp)
                        FocusRing(task, now, ready, Modifier.size(diameter))
                    }
                    MediaPlayerPanel(mediaState, compact, Modifier.weight(1.08f).fillMaxHeight(),
                        onRequestMediaAccess, onMediaPrevious, onMediaToggle, onMediaNext)
                }
                FocusOptions(keepAwake, onKeepAwake, canChooseTask && ready, onChooseTask, onOpenSettings, compact = true)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxWithConstraints(Modifier.weight(1.05f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val diameter = minOf(maxWidth, maxHeight).coerceAtLeast(0.dp)
                    FocusRing(task, now, ready, Modifier.size(diameter))
                }
                MediaPlayerPanel(mediaState, compact = false, Modifier.weight(0.82f).fillMaxWidth(),
                    onRequestMediaAccess, onMediaPrevious, onMediaToggle, onMediaNext)
                FocusOptions(keepAwake, onKeepAwake, canChooseTask && ready, onChooseTask, onOpenSettings)
            }
        }
    }
}

@Composable
private fun FocusOptions(
    keepAwake: Boolean, onKeepAwake: (Boolean) -> Unit, canChoose: Boolean, onChoose: () -> Unit,
    onOpenSettings: (() -> Unit)?, compact: Boolean = false
) {
    if (compact) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            if (canChoose) TextButton(onClick = onChoose) {
                Text("Cambiar tarea en curso", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(checked = keepAwake, onCheckedChange = onKeepAwake)
            Text("Mantener pantalla encendida", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onOpenSettings != null) SettingsButton(onOpenSettings)
        }
    } else {
        if (canChoose) TextButton(onClick = onChoose) { Text("Cambiar tarea en curso", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Checkbox(checked = keepAwake, onCheckedChange = onKeepAwake)
            Text("Mantener pantalla encendida", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (onOpenSettings != null) SettingsButton(onOpenSettings)
        }
    }
}

@Composable
private fun SettingsButton(onOpenSettings: () -> Unit) {
    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("focus-settings")) {
        Icon(Icons.Filled.Settings, contentDescription = "Configuración")
    }
}

@Composable
private fun MediaPlayerPanel(
    state: MediaSessionUiState,
    compact: Boolean,
    modifier: Modifier,
    onRequestAccess: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        if (compact) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is MediaSessionUiState.Track -> {
                        Artwork(state.artwork, 52.dp)
                        Column(Modifier.weight(1f)) {
                            Text(if (state.isPlaying) "REPRODUCIENDO" else "EN PAUSA",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1)
                            Text(state.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(state.artist.ifBlank { state.playerName }, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        MediaControls(state, onPrevious, onToggle, onNext, compact = true)
                    }
                    MediaSessionUiState.AccessRequired -> {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Activa Acceso a notificaciones para detectar la música (solo datos multimedia).",
                                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = onRequestAccess, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Habilitar", maxLines = 1)
                            }
                        }
                    }
                    MediaSessionUiState.NoSession -> EmptyMediaPanel(Modifier.weight(1f), compact = true)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                when (state) {
                    is MediaSessionUiState.Track -> {
                        Text(if (state.isPlaying) "REPRODUCIENDO" else "EN PAUSA",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        Artwork(state.artwork, 76.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.artist.ifBlank { state.playerName }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MediaControls(state, onPrevious, onToggle, onNext, compact = false)
                    }
                    MediaSessionUiState.AccessRequired -> {
                        Artwork(null, 64.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("Activa Acceso a notificaciones para detectar la música (solo datos multimedia).",
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = onRequestAccess) { Text("Habilitar acceso") }
                    }
                    MediaSessionUiState.NoSession -> EmptyMediaPanel(Modifier.fillMaxWidth(), compact = false)
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaPanel(modifier: Modifier, compact: Boolean) {
    if (compact) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Nada reproduciéndose", style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Artwork(null, 76.dp)
            Spacer(Modifier.height(8.dp))
            Text("Nada reproduciéndose", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text("Inicia música en otra aplicación.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Artwork(bitmap: android.graphics.Bitmap?, size: androidx.compose.ui.unit.Dp) {
    Surface(modifier = Modifier.size(size), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
        if (bitmap == null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.48f))
            }
        } else {
            Image(bitmap.asImageBitmap(), contentDescription = "Carátula del álbum",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun MediaControls(
    track: MediaSessionUiState.Track,
    onPrevious: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onNext: () -> Unit,
    compact: Boolean
) {
    val buttonSize = if (compact) 36.dp else 44.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = onPrevious, enabled = track.canSkipPrevious, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Pista anterior")
        }
        FilledTonalIconButton(onClick = { onToggle(track.isPlaying) }, enabled = track.canTogglePlayback,
            modifier = Modifier.size(buttonSize)) {
            Icon(if (track.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (track.isPlaying) "Pausar" else "Reproducir")
        }
        IconButton(onClick = onNext, enabled = track.canSkipNext, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Pista siguiente")
        }
    }
}

@Composable
private fun FocusRing(task: AgendaOccurrence?, now: Instant, ready: Boolean, modifier: Modifier) {
    val progress = task?.progressAt(now) ?: 0f
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val fontScale = LocalDensity.current.fontScale
    val ringModifier = if (task != null && ready) Modifier.progressSemantics(progress) else Modifier
    BoxWithConstraints(modifier.then(ringModifier).testTag("focus-ring"), contentAlignment = Alignment.Center) {
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
        val title = when {
            !ready -> "Cargando agenda…"
            task != null -> task.title
            else -> "Sin tarea en curso"
        }
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        Column(Modifier.fillMaxWidth(0.72f), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                fontSize = (diameter.value * 0.105f / fontScale).coerceIn(10f, 20f).sp,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (task != null && ready) {
                val remaining = formatFocusTime(remainingBlockMillis(task, now))
                Text(remaining, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light,
                    fontSize = (diameter.value * 0.15f / fontScale).coerceIn(12f, 44f).sp, maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = "Tiempo restante del bloque: $remaining" })
                Text("${timeFormat.format(task.start)} – ${timeFormat.format(task.end)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (diameter.value * 0.065f / fontScale).coerceIn(9f, 14f).sp,
                    textAlign = TextAlign.Center, maxLines = 1)
            }
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
