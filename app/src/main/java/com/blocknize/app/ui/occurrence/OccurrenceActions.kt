package com.blocknize.app.ui.occurrence

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blocknize.app.AppViewModel
import com.blocknize.app.data.AgendaOccurrence
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import com.blocknize.app.domain.civilInstant
import com.blocknize.app.domain.parseScheduleMinute

internal fun exceptionLabel(task: AgendaOccurrence): String? = when (task.exceptionStatus) {
    "SKIP" -> "Omitida esta vez"
    "REPLACE" -> if (task.destinationDate == task.date) "Modificada esta vez" else "Reprogramada → ${task.destinationDate}"
    else -> task.exceptionId?.let { "Excepción · origen ${task.originalDate}" }
}

@Composable
internal fun OccurrenceActions(task: AgendaOccurrence, model: AppViewModel) {
    val now by model.now.collectAsStateWithLifecycle()
    val busy by model.isBusy.collectAsStateWithLifecycle()
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
    if (task.sourceType != "WEEKLY" || task.historical || task.date.isBefore(today)) return
    if (task.originalDate.isBefore(today)) {
        if (task.exceptionId != null) Text("Esta excepción conserva un origen pasado y no admite edición ni restauración.",
            style = MaterialTheme.typography.bodySmall)
        return
    }
    var menu by remember { mutableStateOf(false) }
    var action by rememberSaveable(task.occurrenceKey) { mutableStateOf<String?>(null) }
    var error by remember(action) { mutableStateOf<String?>(null) }
    Box {
        TextButton(onClick = { menu = true }, enabled = !busy) { Text("Solo esta ocurrencia") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (task.exceptionStatus == null) {
                DropdownMenuItem(text = { Text("Modificar solo esta vez") }, enabled = !task.completed,
                    onClick = { menu = false; action = "EDIT" })
                DropdownMenuItem(text = { Text("Reprogramar") }, enabled = !task.completed,
                    onClick = { menu = false; action = "MOVE" })
                DropdownMenuItem(text = { Text("Omitir esta vez") }, enabled = !task.completed,
                    onClick = { menu = false; action = "SKIP" })
            }
            if (task.exceptionId != null) DropdownMenuItem(text = { Text("Restaurar programación original") }, enabled = !task.completed,
                onClick = { menu = false; action = "RESTORE" })
            if (task.completed) DropdownMenuItem(text = { Text("Deshaz primero la finalización") }, onClick = {}, enabled = false)
        }
    }
    action?.let { selected ->
        if (selected == "EDIT" || selected == "MOVE") OccurrenceExceptionDialog(task, model, selected == "MOVE") { action = null }
        else AlertDialog(onDismissRequest = { action = null }, title = {
            Text(if (selected == "SKIP") "Omitir esta vez" else "Restaurar programación original")
        }, text = { Column {
            Text("Este cambio afecta únicamente a esta ocurrencia. La plantilla semanal no se modificará. El historial transcurrido se conserva.")
            if (selected == "RESTORE") Text("Se retirará la excepción. Si el bloque original fue eliminado o su semana está inactiva, no se recreará ni se activará automáticamente.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                model.saveException(task, selected) { if (it) action = null else error = model.message.value }
            }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun OccurrenceExceptionDialog(task: AgendaOccurrence, model: AppViewModel, moving: Boolean, onDismiss: () -> Unit) {
    val busy by model.isBusy.collectAsStateWithLifecycle()
    val now by model.now.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf(task.title) }
    var description by rememberSaveable { mutableStateOf(task.description) }
    var tag by rememberSaveable { mutableStateOf(task.tagName.orEmpty()) }
    var dateText by rememberSaveable { mutableStateOf(task.date.toString()) }
    var start by rememberSaveable { mutableStateOf(String.format(Locale.ROOT, "%02d:%02d", task.startMinute / 60, task.startMinute % 60)) }
    var end by rememberSaveable { mutableStateOf(String.format(Locale.ROOT, "%02d:%02d", task.endMinute / 60, task.endMinute % 60)) }
    var acknowledged by rememberSaveable(dateText, start, end) { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val startMinute = parseScheduleMinute(start)
    val endMinute = parseScheduleMinute(end, allowEndOfDay = true)
    var overlap by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(date, startMinute, endMinute) {
        checking = true
        loadError = false
        try {
            if (date != null) model.observeCalendarAgenda(date).collect { agenda ->
                overlap = agenda.any { other -> other.exceptionStatus == null && !other.historical &&
                    other.occurrenceKey != task.occurrenceKey &&
                    !(other.templateId == task.templateId && other.sourceId == task.sourceId && other.originalDate == task.originalDate) &&
                    startMinute != null && endMinute != null && other.startMinute < endMinute && other.endMinute > startMinute }
                checking = false
            } else checking = false
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { checking = false; loadError = true }
    }
    val changedStart = date != task.date || startMinute != task.startMinute
    val destinationInPast = date != null && (date.isBefore(now.atZone(ZoneId.systemDefault()).toLocalDate()) ||
        changedStart && startMinute != null && civilInstant(date, startMinute, ZoneId.systemDefault()).isBefore(now))
    val valid = !destinationInPast && title.isNotBlank() && date != null && startMinute != null && endMinute != null && startMinute < endMinute && startMinute < 1440
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (moving) "Reprogramar" else "Modificar solo esta vez") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Este cambio afecta únicamente a esta ocurrencia. La plantilla semanal no se modificará.")
            OutlinedTextField(title, { title = it }, label = { Text("Título *") })
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") })
            OutlinedTextField(tag, { tag = it }, label = { Text("Etiqueta") })
            OutlinedTextField(dateText, { dateText = it }, enabled = moving, label = { Text("Fecha · AAAA-MM-DD") })
            OutlinedTextField(start, { start = it }, label = { Text("Inicio · HH:mm") })
            OutlinedTextField(end, { end = it }, label = { Text("Fin · HH:mm") })
            if (!valid) Text("Revisa el título, la fecha y el horario. No se admite cruzar medianoche.", color = MaterialTheme.colorScheme.error)
            if (destinationInPast) Text("El destino no puede tener un inicio ya pasado.", color = MaterialTheme.colorScheme.error)
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loadError) Text("No se pudo comprobar la agenda. Vuelve a abrir el formulario.", color = MaterialTheme.colorScheme.error)
            if (overlap) Text("Se solapa con otra actividad. Se conservarán ambas.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = valid && !busy && !checking && !loadError, onClick = {
        if (overlap && !acknowledged) acknowledged = true
        else model.saveException(task, "REPLACE", date!!, title, description, tag, startMinute!!, endMinute!!) {
            if (it) onDismiss() else saveError = model.message.value
        }
    }) { Text(if (overlap && !acknowledged) "Continuar con solape" else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } })
}
