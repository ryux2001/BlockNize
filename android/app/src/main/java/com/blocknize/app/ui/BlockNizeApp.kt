package com.blocknize.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blocknize.app.AppViewModel
import com.blocknize.app.data.BlockRow
import com.blocknize.app.data.AgendaOccurrence
import com.blocknize.app.data.TemplateRow
import com.blocknize.app.data.TemplateTransferCodec
import com.blocknize.app.data.TransferTemplate
import com.blocknize.app.data.parseAccentHex
import com.blocknize.app.data.widgetBackgroundArgb
import com.blocknize.app.domain.currentTasks
import com.blocknize.app.ui.focus.FocusRoute
import com.blocknize.app.ui.occurrence.OccurrenceActions
import com.blocknize.app.ui.occurrence.exceptionLabel
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weekdayNames = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val weekdayShort = listOf("L", "M", "X", "J", "V", "S", "D")
private data class Preset(val name: String, val hex: String) { val argb get() = parseAccentHex(hex)!! }
private val presets = listOf(
    Preset("Violeta", "A78BFA"), Preset("Celeste", "38BDF8"), Preset("Turquesa", "2DD4BF"),
    Preset("Naranja", "FB923C"), Preset("Verde", "4ADE80"), Preset("Rosa", "FB7185"),
    Preset("Amarillo", "FACC15"), Preset("Azul", "60A5FA")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockNizeApp(modifier: Modifier = Modifier, model: AppViewModel) {
    val accent by model.accent.collectAsStateWithLifecycle()
    val templates by model.templates.collectAsStateWithLifecycle()
    val active by model.active.collectAsStateWithLifecycle()
    val todayAgenda by model.todayAgenda.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.TODAY.name) }
    val destination = AppDestination.entries.firstOrNull { it.name == destinationName } ?: AppDestination.TODAY
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var settings by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var editingTemplate by rememberSaveable { mutableStateOf<String?>(null) }
    var createTemplate by remember { mutableStateOf(false) }
    var renameTemplate by remember { mutableStateOf<TemplateRow?>(null) }
    var activateTemplate by remember { mutableStateOf<TemplateRow?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<TransferTemplate?>(null) }
    var importName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri != null && json != null) {
            val success = writeDocument(context, uri, json)
            model.showMessage(if (success) "Plantilla exportada correctamente." else "No se pudo guardar el archivo exportado.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val json = context.contentResolver.openInputStream(uri)?.use(::readLimitedUtf8)
                ?: error("No se pudo leer el archivo.")
            val decoded = TemplateTransferCodec.decode(json)
            pendingImport = decoded
            importName = decoded.name
        } catch (error: Exception) {
            model.showMessage(error.message ?: "El archivo no se pudo importar.")
        }
    }
    var showOneOff by rememberSaveable { mutableStateOf(false) }
    var editOneOff by remember { mutableStateOf<AgendaOccurrence?>(null) }
    var deleteOneOff by remember { mutableStateOf<AgendaOccurrence?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.clearMessage() } }
    BackHandler(enabled = settings || editingTemplate != null) {
        if (settings) settings = false else editingTemplate = null
    }
    Row(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            containerColor = Color.Black,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (destination != AppDestination.FOCUS || settings || editingTemplate != null) TopAppBar(
                    modifier = Modifier.testTag("app-top-bar"),
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                    title = {
                        Text(when {
                            settings -> "Ajustes"
                            editingTemplate != null -> templates.firstOrNull { it.id == editingTemplate }?.name ?: "Editar semana"
                            destination == AppDestination.BLOCKS -> "Semanas y bloques"
                            destination == AppDestination.CALENDAR -> "Calendario"
                            destination == AppDestination.HISTORY -> "Registro"
                            destination == AppDestination.FOCUS -> "Concentración"
                            else -> "BlockNize"
                        }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        if (editingTemplate != null) IconButton(onClick = { editingTemplate = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a las semanas")
                        }
                    },
                    actions = {
                        IconButton(onClick = { settings = !settings; editingTemplate = null }) {
                            Icon(if (settings) Icons.Filled.ColorLens else Icons.Filled.Settings, contentDescription = if (settings) "Cerrar apariencia" else "Configuración")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!settings && editingTemplate == null && destination == AppDestination.TODAY) {
                    ExtendedFloatingActionButton(
                        onClick = { showOneOff = true },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Añadir temporal") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            bottomBar = {
                if (!landscape && !settings && editingTemplate == null) NavigationBar(
                    modifier = Modifier.testTag("navigation-bottom"), containerColor = Color.Black) {
                    AppDestination.entries.forEach { tab ->
                        NavigationBarItem(modifier = Modifier.testTag("destination-${tab.name}"),
                            selected = destination == tab, onClick = { destinationName = tab.name },
                            icon = { Icon(tab.icon, contentDescription = tab.title) }, label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            alwaysShowLabel = destination == tab)
                    }
                }
            }
        ) { insets ->
            when {
                settings -> AppearanceScreen(Modifier.padding(insets), accent, model) { picker = true }
                editingTemplate != null -> WeeklyEditor(
                    Modifier.padding(insets), model, editingTemplate!!,
                    isActive = active?.templateId == editingTemplate,
                    onActivate = { templates.firstOrNull { it.id == editingTemplate }?.let { activateTemplate = it } }
                )
                destination == AppDestination.TODAY -> TodayScreen(
                    Modifier.padding(insets), templates.firstOrNull { it.id == active?.templateId }?.name,
                    model, onEditOneOff = { editOneOff = it },
                    onDeleteOneOff = { deleteOneOff = it }
                )
                destination == AppDestination.BLOCKS -> TemplateManager(
                    Modifier.padding(insets), templates, active?.templateId,
                    onCreate = { createTemplate = true }, onEdit = { editingTemplate = it },
                    onRename = { renameTemplate = it }, onActivate = { activateTemplate = it },
                    onExport = { template -> model.exportTemplate(template.id) { json ->
                        if (json != null) { pendingExport = json; exportLauncher.launch("${safeFileName(template.name)}.json") }
                    } }, onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) }
                )
                destination == AppDestination.CALENDAR -> CalendarScreen(Modifier.padding(insets), model)
                destination == AppDestination.HISTORY -> HistoryScreen(Modifier.padding(insets), model)
                destination == AppDestination.FOCUS -> FocusRoute(Modifier.padding(insets), model,
                    onOpenSettings = if (landscape) null else ({ settings = true }))
            }
        }
        if (landscape && !settings && editingTemplate == null) {
            AppNavigationRail(destination, onSelect = { destinationName = it.name },
                onOpenSettings = if (destination == AppDestination.FOCUS) ({ settings = true }) else null)
        }
    }
    if (picker) AccentPickerDialog(accent, { picker = false }) { model.setAccent(it); picker = false }
    if (showOneOff) OneOffDialog(
        initial = null, agenda = todayAgenda,
        model = model, onDismiss = { showOneOff = false }, onSaved = { showOneOff = false }
    )
    editOneOff?.let { occurrence -> OneOffDialog(
        initial = occurrence, agenda = todayAgenda,
        model = model, onDismiss = { editOneOff = null }, onSaved = { editOneOff = null }
    ) }
    deleteOneOff?.let { occurrence ->
        AlertDialog(onDismissRequest = { deleteOneOff = null }, title = { Text("Eliminar ${occurrence.title}?") },
            text = { Text("Esta tarea temporal se quitará de su fecha.") },
            confirmButton = { Button(onClick = { model.deleteOneOff(occurrence.sourceId); deleteOneOff = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteOneOff = null }) { Text("Cancelar") } })
    }
    if (createTemplate) NameDialog(title = "Nueva semana", label = "Nombre", confirm = "Crear", onDismiss = { createTemplate = false }) {
        model.createTemplate(it); createTemplate = false
    }
    renameTemplate?.let { template ->
        NameDialog(title = "Renombrar semana", label = "Nombre", confirm = "Guardar", initialValue = template.name, onDismiss = { renameTemplate = null }) {
            model.renameTemplate(template.id, it); renameTemplate = null
        }
    }
    activateTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { activateTemplate = null },
            title = { Text("Activar ${template.name}?") },
            text = { Text("El cambio se aplicará ahora. La semana anterior seguirá guardada y su historial se conservará.") },
            confirmButton = { Button(onClick = { model.activate(template.id); activateTemplate = null }) { Text("Activar ahora") } },
            dismissButton = { TextButton(onClick = { activateTemplate = null }) { Text("Cancelar") } }
        )
    }
    pendingImport?.let { template ->
        AlertDialog(onDismissRequest = { pendingImport = null },
            title = { Text("Importar plantilla") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${template.blocks.size} bloques · ${template.dayCount} días con actividad. Se importará como copia inactiva; no se incluyen tareas temporales ni historial.")
                    if (template.overlapCount > 0) Text("Aviso: ${template.overlapCount} pares de bloques se solapan. Se conservarán tal como están.", color = Color(0xFFFFC857))
                    OutlinedTextField(importName, { importName = it }, label = { Text("Nombre de la semana") }, singleLine = true)
                    Text("Las etiquetas existentes con el mismo nombre se reutilizarán. Los solapes se conservarán.", color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(enabled = importName.isNotBlank(), onClick = {
                model.importTemplate(template, importName) { success -> if (success) pendingImport = null }
            }) { Text("Importar copia") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancelar") } })
    }
}

private fun writeDocument(context: Context, uri: android.net.Uri, contents: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(contents) }
            ?: error("No se pudo guardar el archivo.")
        true
    } catch (_: Exception) { false }

private fun readLimitedUtf8(input: java.io.InputStream): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= TemplateTransferCodec.MAX_BYTES) { "El archivo supera el tamaño máximo de 1 MB." }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

private fun safeFileName(name: String): String = name.trim()
    .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(70).ifBlank { "BlockNize" }

@Composable
private fun EmptySection(modifier: Modifier, title: String, accent: Int) {
    Column(modifier.fillMaxSize().padding(24.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(accent))
        Spacer(Modifier.height(22.dp))
        Text("Tu tiempo,\ncon intención.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("La agenda se conectará aquí en la siguiente entrega.", color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Surface(color = Color(0xFF101010), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("PREPARA TU SEMANA", color = Color(accent), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text("Crea tus primeras plantillas desde Bloques.", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TodayScreen(
    modifier: Modifier, activeTemplateName: String?, model: AppViewModel,
    onEditOneOff: (AgendaOccurrence) -> Unit, onDeleteOneOff: (AgendaOccurrence) -> Unit
) {
    val agenda by model.todayAgenda.collectAsStateWithLifecycle()
    val now by model.now.collectAsStateWithLifecycle()
    val busy by model.isBusy.collectAsStateWithLifecycle()
    val date = now.atZone(ZoneId.systemDefault()).toLocalDate()
    val currentKeys = currentTasks(agenda, now).map { it.occurrenceKey }.toSet()
    val spanishLocale = remember { Locale.forLanguageTag("es-ES") }
    val headerDate = remember(date, spanishLocale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", spanishLocale).format(date).replaceFirstChar { it.uppercase(spanishLocale) } }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(headerDate, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(activeTemplateName?.let { "Semana activa · $it" } ?: "Sin semana activa",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (agenda.isEmpty()) {
            Surface(color = Color(0xFF101010), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Un día despejado", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("No hay bloques para hoy. Puedes añadir una tarea temporal.", color = Color(0xFFB8B8B8))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(agenda, key = { it.occurrenceKey }) { item ->
                    val current = item.occurrenceKey in currentKeys
                    val progress = item.progressAt(now)
                    val stateLabel = when {
                        item.exceptionStatus != null -> exceptionLabel(item).orEmpty()
                        item.completed -> "Completada"
                        current -> "En curso · ${(progress * 100).toInt()}%"
                        now < item.start -> "Próxima"
                        else -> "Finalizada · pendiente"
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 10.dp, top = 12.dp, end = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.completed, onCheckedChange = { model.setCompleted(item, it) }, enabled = !busy && item.exceptionStatus == null,
                                modifier = Modifier.semantics { contentDescription = "Finalización de ${item.title}" })
                            Column(Modifier.width(66.dp)) {
                                Text(formatMinute(item.startMinute), fontWeight = FontWeight.SemiBold, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(formatMinute(item.endMinute), color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    if (item.sourceType == "ONE_OFF") Text("TEMPORAL", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                                if (item.description.isNotBlank()) Text(item.description, color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
                                item.tagName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                                if (item.exceptionId != null && item.exceptionStatus == null) Text(exceptionLabel(item).orEmpty(), style = MaterialTheme.typography.labelSmall)
                                Text(if (item.historical) "Actividad anterior · completada" else stateLabel,
                                    color = if (item.completed || current) MaterialTheme.colorScheme.primary else Color(0xFFB8B8B8),
                                    style = MaterialTheme.typography.labelSmall)
                                if (current) {
                                    Spacer(Modifier.height(5.dp))
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                                }
                                if (item.sourceType == "ONE_OFF" && !item.historical) Row {
                                    TextButton(onClick = { onEditOneOff(item) }, enabled = !busy) { Text("Editar") }
                                    TextButton(onClick = { onDeleteOneOff(item) }, enabled = !busy) { Text("Eliminar") }
                                }
                                OccurrenceActions(item, model)
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface AgendaLoad {
    data object Loading : AgendaLoad
    data class Loaded(val occurrences: List<AgendaOccurrence>) : AgendaLoad
    data class Failed(val message: String) : AgendaLoad
}

@Composable
private fun CalendarScreen(modifier: Modifier, model: AppViewModel) {
    var weekly by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("Mes") })
            androidx.compose.material3.FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Semana") })
        }
        if (weekly) WeeklyScheduleScreen(Modifier.weight(1f), model)
        else MonthlyCalendarScreen(Modifier.weight(1f), model)
    }
}

@Composable
private fun MonthlyCalendarScreen(modifier: Modifier, model: AppViewModel) {
    val now by model.now.collectAsStateWithLifecycle()
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var retry by remember { mutableStateOf(0) }
    val selectedDate = remember(selectedText) { runCatching { LocalDate.parse(selectedText) }.getOrDefault(LocalDate.now()) }
    val month = remember(monthText) { runCatching { YearMonth.parse(monthText) }.getOrDefault(YearMonth.from(selectedDate)) }
    val load by produceState<AgendaLoad>(AgendaLoad.Loading, selectedDate, retry, now.atZone(ZoneId.systemDefault()).toLocalDate()) {
        value = AgendaLoad.Loading
        try { model.observeCalendarAgenda(selectedDate).collect { value = AgendaLoad.Loaded(it) } }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { value = AgendaLoad.Failed(error.message ?: "No se pudo cargar la fecha.") }
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { MonthDateGrid(month, selectedDate, now.atZone(ZoneId.systemDefault()).toLocalDate(),
            onMonthChange = { monthText = it.toString() }, onSelectDate = {
                selectedText = it.toString(); monthText = YearMonth.from(it).toString()
            }) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp)) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatLongDate(selectedDate), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(when {
                    selectedDate.isBefore(now.atZone(ZoneId.systemDefault()).toLocalDate()) -> "Historial reconstruido desde revisiones y cambios"
                    selectedDate.isAfter(now.atZone(ZoneId.systemDefault()).toLocalDate()) -> "Proyección con la semana activa"
                    else -> "Agenda del día y actividad anterior"
                }, color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = {
                val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
                selectedText = today.toString(); monthText = YearMonth.from(today).toString()
            }) { Text("Hoy") }
        } }
        when (val result = load) {
            AgendaLoad.Loading -> item { Text("Cargando agenda…", color = Color(0xFFB8B8B8), modifier = Modifier.padding(12.dp)) }
            is AgendaLoad.Failed -> item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { retry++ }) { Text("Reintentar") }
            } }
            is AgendaLoad.Loaded -> {
                val items = result.occurrences
                val completed = items.count { it.completed }
                val interrupted = items.count { it.interrupted && !it.completed }
                item { Text("$completed completadas · $interrupted interrumpidas · ${items.size} actividades",
                    color = Color(0xFFB8B8B8), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp)) }
                if (items.isEmpty()) item { Text("No hay actividades en esta fecha.", color = Color(0xFFB8B8B8), modifier = Modifier.padding(12.dp)) }
                else items(items, key = { it.occurrenceKey }) { item -> CalendarOccurrenceCard(item, now, model) }
            }
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, model: AppViewModel) {
    val now by model.now.collectAsStateWithLifecycle()
    val busy by model.isBusy.collectAsStateWithLifecycle()
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var retry by remember { mutableStateOf(0) }
    val selectedDate = remember(selectedText) { runCatching { LocalDate.parse(selectedText) }.getOrDefault(LocalDate.now()) }
    val month = remember(monthText) { runCatching { YearMonth.parse(monthText) }.getOrDefault(YearMonth.from(selectedDate)) }
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
    val load by produceState<AgendaLoad>(AgendaLoad.Loading, selectedDate, retry, today) {
        value = AgendaLoad.Loading
        try { model.observeHistory(selectedDate).collect { value = AgendaLoad.Loaded(it) } }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { value = AgendaLoad.Failed(error.message ?: "No se pudo cargar el registro.") }
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { MonthDateGrid(month, selectedDate, today, onMonthChange = { monthText = it.toString() }, onSelectDate = {
            selectedText = it.toString(); monthText = YearMonth.from(it).toString()
        }) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text(formatLongDate(selectedDate), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        when (val result = load) {
            AgendaLoad.Loading -> item { Text("Cargando registro…", color = Color(0xFFB8B8B8)) }
            is AgendaLoad.Failed -> item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { retry++ }) { Text("Reintentar") }
            } }
            is AgendaLoad.Loaded -> {
                if (selectedDate.isAfter(today)) item { Text("El registro aún no está disponible para fechas futuras.", color = Color(0xFFB8B8B8)) }
                else {
                    val completed = result.occurrences.filter { it.completed }
                    val exceptions = result.occurrences.filter { it.exceptionStatus != null }
                    val actionable = result.occurrences.filter { it.exceptionStatus == null }
                    val interrupted = actionable.filter { !it.completed && it.interrupted }
                    val pending = actionable.filter { !it.completed && !it.interrupted && it.end <= now }
                    val ongoing = actionable.filter { !it.completed && !it.interrupted && it.start <= now && now < it.end }
                    if (exceptions.isNotEmpty()) item { HistoryGroupTitle("Omitidas y reprogramadas", exceptions.size, Color(0xFFFFC857)) }
                    items(exceptions, key = { "exception:${it.occurrenceKey}" }) { HistoryOccurrenceCard(it, exceptionLabel(it).orEmpty(), enabled = false) }
                    if (completed.isNotEmpty()) item { HistoryGroupTitle("Completadas", completed.size, MaterialTheme.colorScheme.primary) }
                    items(completed, key = { "done:${it.occurrenceKey}" }) { item ->
                        HistoryOccurrenceCard(item, "Completada", onUndo = { model.setCompleted(item, false) }, enabled = !busy)
                    }
                    if (interrupted.isNotEmpty()) item { HistoryGroupTitle("Interrumpidas por un cambio", interrupted.size, Color(0xFFFFC857)) }
                    items(interrupted, key = { "interrupted:${it.occurrenceKey}" }) { HistoryOccurrenceCard(it, "Interrumpida", enabled = false) }
                    if (pending.isNotEmpty()) item { HistoryGroupTitle("Pendientes", pending.size, Color(0xFFB8B8B8)) }
                    items(pending, key = { "pending:${it.occurrenceKey}" }) { HistoryOccurrenceCard(it, "Pendiente", enabled = false) }
                    if (ongoing.isNotEmpty()) item { HistoryGroupTitle("En curso", ongoing.size, MaterialTheme.colorScheme.primary) }
                    items(ongoing, key = { "ongoing:${it.occurrenceKey}" }) { HistoryOccurrenceCard(it, "En curso", enabled = false) }
                    if (exceptions.isEmpty() && completed.isEmpty() && interrupted.isEmpty() && pending.isEmpty() && ongoing.isEmpty()) item {
                        Text("No hay actividad registrada para esta fecha.", color = Color(0xFFB8B8B8), modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MonthDateGrid(
    month: YearMonth, selected: LocalDate, today: LocalDate,
    onMonthChange: (YearMonth) -> Unit, onSelectDate: (LocalDate) -> Unit
) {
    val locale = remember { Locale.forLanguageTag("es-ES") }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes anterior") }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.uppercase(locale) },
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Mes siguiente") }
    }
    Row(Modifier.fillMaxWidth()) {
        weekdayShort.forEach { Box(Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        } }
    }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    Column {
        repeat(count / 7) { week -> Row(Modifier.fillMaxWidth()) {
          repeat(7) { weekday ->
            val index = week * 7 + weekday
            val dayOfMonth = index - offset + 1
            if (dayOfMonth !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
            else {
                val date = month.atDay(dayOfMonth)
                val isSelected = date == selected
                val isToday = date == today
                Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .then(if (!isSelected && isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelectDate(date) })
                    .semantics { contentDescription = formatLongDate(date) }
                    .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(dayOfMonth.toString(), color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
          }
        } }
    }
}

@Composable
internal fun CalendarOccurrenceCard(item: AgendaOccurrence, now: java.time.Instant, model: AppViewModel? = null) {
    val status = when {
        item.exceptionStatus != null -> exceptionLabel(item).orEmpty()
        item.completed -> "Completada"
        item.interrupted -> "Interrumpida por cambio"
        now < item.start -> "Próxima"
        now < item.end -> "En curso"
        else -> "Pendiente"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            OccurrenceSchedule(item)
            if (item.description.isNotBlank()) Text(item.description, color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
            item.tagName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Text("$status${if (item.sourceType == "ONE_OFF") " · Temporal" else ""}", color = Color(0xFFB8B8B8), style = MaterialTheme.typography.labelSmall)
            item.sourceTemplateName?.let { Text("Semana · $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
            if (item.exceptionId != null && item.exceptionStatus == null) Text(exceptionLabel(item).orEmpty(), style = MaterialTheme.typography.labelSmall)
            if (model != null) OccurrenceActions(item, model)
        }
    }
}

@Composable
private fun HistoryGroupTitle(title: String, count: Int, color: Color) {
    Text("$title · $count", color = color, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
internal fun HistoryOccurrenceCard(item: AgendaOccurrence, status: String, onUndo: (() -> Unit)? = null, enabled: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onUndo != null) Checkbox(checked = true, onCheckedChange = { if (!it) onUndo() }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Finalización de ${item.title}" })
            Column(Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                OccurrenceSchedule(item)
                if (item.description.isNotBlank()) Text(item.description, color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
                item.sourceTemplateName?.let { Text("Semana · $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                Text(status, color = Color(0xFFB8B8B8), style = MaterialTheme.typography.labelSmall)
                if (onUndo != null) TextButton(onClick = onUndo, enabled = enabled) { Text("Deshacer") }
            }
        }
    }
}

@Composable
private fun OccurrenceSchedule(item: AgendaOccurrence) {
    Text("${formatMinute(item.startMinute)}–${formatMinute(item.endMinute)}",
        color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
    if (item.start != item.originalStart || item.end != item.originalEnd) {
        Text("Tramo efectivo · ${formatEffectiveRange(item.start, item.end)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

internal fun formatEffectiveRange(start: java.time.Instant, end: java.time.Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val minutes = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    val formatter = if (minutes.format(start) == minutes.format(end)) DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone) else minutes
    return "${formatter.format(start)}–${formatter.format(end)}"
}

private fun formatLongDate(date: LocalDate): String {
    val locale = Locale.forLanguageTag("es-ES")
    return date.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale)).replaceFirstChar { it.uppercase(locale) }
}

@Composable
private fun OneOffDialog(
    initial: AgendaOccurrence?, agenda: List<AgendaOccurrence>, model: AppViewModel,
    onDismiss: () -> Unit, onSaved: () -> Unit
) {
    val now by model.now.collectAsStateWithLifecycle()
    val busy by model.isBusy.collectAsStateWithLifecycle()
    var title by rememberSaveable(initial?.occurrenceKey) { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable(initial?.occurrenceKey) { mutableStateOf(initial?.description.orEmpty()) }
    var tag by rememberSaveable(initial?.occurrenceKey) { mutableStateOf(initial?.tagName.orEmpty()) }
    var dateText by rememberSaveable(initial?.occurrenceKey) {
        mutableStateOf((initial?.date ?: now.atZone(ZoneId.systemDefault()).toLocalDate()).toString())
    }
    var start by rememberSaveable(initial?.occurrenceKey) { mutableStateOf(initial?.startMinute?.let(::formatMinute) ?: "09:00") }
    var end by rememberSaveable(initial?.occurrenceKey) { mutableStateOf(initial?.endMinute?.let(::formatMinute) ?: "10:00") }
    var overlapAcknowledged by remember(initial?.occurrenceKey) { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
    val agendaForDate by produceState(initialValue = agenda, date, agenda) {
        if (date != null) value = runCatching { model.agendaForDate(date) }.getOrDefault(emptyList())
        else value = emptyList()
    }
    val startMinute = parseMinute(start); val endMinute = parseMinute(end, allowEndOfDay = true)
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
    val valid = date != null && !date.isBefore(today) && title.isNotBlank() && startMinute != null && endMinute != null && startMinute < endMinute
    val hasOverlap = valid && agendaForDate.any { other ->
        !other.historical && other.exceptionStatus == null && other.sourceId != initial?.sourceId && other.startMinute < endMinute!! && other.endMinute > startMinute!!
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Tarea temporal" else "Editar tarea temporal") }, text = {
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(title, { title = it; overlapAcknowledged = false }, label = { Text("Título *") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(tag, { tag = it }, label = { Text("Etiqueta (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(dateText, { dateText = it; overlapAcknowledged = false }, label = { Text("Fecha · AAAA-MM-DD") }, singleLine = true, isError = date == null, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlarmTimeField("Inicio", parseMinute(start) ?: 540, Modifier.weight(1f)) { start = formatMinute(it); overlapAcknowledged = false }
                AlarmTimeField("Fin", parseMinute(end, allowEndOfDay = true) ?: 600, Modifier.weight(1f)) { end = formatMinute(it); overlapAcknowledged = false }
            } }
            item {
                if (date == null) Text("Escribe una fecha válida, por ejemplo 2026-10-29.", color = MaterialTheme.colorScheme.error)
                else if (date.isBefore(today)) Text("No se pueden programar tareas temporales en una fecha pasada.", color = MaterialTheme.colorScheme.error)
                if (startMinute == null || endMinute == null || startMinute >= endMinute) Text("La hora debe tener formato HH:mm y la hora final ser posterior.", color = MaterialTheme.colorScheme.error)
                if (hasOverlap) Text("Se solapa con otra actividad. Ambas se mostrarán en Inicio.", color = Color(0xFFFFC857))
            }
        }
    }, confirmButton = {
        Button(enabled = valid && !busy, onClick = {
            if (hasOverlap && !overlapAcknowledged) overlapAcknowledged = true
            else model.saveOneOff(initial?.sourceId, date!!, title, description, tag, startMinute!!, endMinute!!) { success -> if (success) onSaved() }
        }) { Text(if (busy) "Guardando…" else if (hasOverlap && !overlapAcknowledged) "Continuar con solape" else "Guardar") }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun TemplateManager(
    modifier: Modifier, templates: List<TemplateRow>, activeId: String?,
    onCreate: () -> Unit, onEdit: (String) -> Unit, onRename: (TemplateRow) -> Unit,
    onActivate: (TemplateRow) -> Unit, onExport: (TemplateRow) -> Unit, onImport: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tus semanas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Edita una semana o activa una para usarla ahora.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Crear semana") }
            TextButton(onClick = onImport) { Text("Importar JSON") }
        }
        if (templates.isEmpty()) {
            Surface(color = Color(0xFF101010), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Aún no hay semanas", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp)); Text("Crea una semana completa; puedes dejar días vacíos.", color = Color(0xFFB8B8B8))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(templates, key = { it.id }) { template ->
                    val isActive = template.id == activeId
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${template.blockCount} bloques", color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
                                }
                                TemplateActions(template, onRename, onExport)
                            }
                            if (isActive) ActiveTemplateLabel(Modifier.padding(top = 8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                                TextButton(onClick = { onEdit(template.id) }) { Text("Editar bloques") }
                                if (!isActive) Button(onClick = { onActivate(template) }) { Text("Activar") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateActions(template: TemplateRow, onRename: (TemplateRow) -> Unit, onExport: (TemplateRow) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Opciones de ${template.name}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Renombrar") }, onClick = { expanded = false; onRename(template) })
            DropdownMenuItem(text = { Text("Exportar") }, onClick = { expanded = false; onExport(template) })
        }
    }
}

@Composable
private fun ActiveTemplateLabel(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("Plantilla activa", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun WeekdaySelector(mask: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, multiple: Boolean = false) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Wrap on narrow dialogs instead of hiding Sunday or shrinking touch targets.
        val columns = if (maxWidth < 348.dp) 4 else 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            weekdayShort.chunked(columns).forEachIndexed { row, days ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    days.forEachIndexed { column, day ->
                        val index = row * columns + column
                        val checked = mask and (1 shl index) != 0
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .selectable(selected = checked, role = if (multiple) Role.Checkbox else Role.Tab, onClick = { onSelect(index) })
                            .semantics { contentDescription = weekdayNames[index] }
                            .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(day, style = MaterialTheme.typography.labelLarge,
                                color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    repeat(columns - days.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun WeeklyEditor(modifier: Modifier, model: AppViewModel, templateId: String, isActive: Boolean, onActivate: () -> Unit) {
    val blocks by model.blocks(templateId).collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedDay by rememberSaveable(templateId) { mutableStateOf(0) }
    var editBlock by remember { mutableStateOf<BlockRow?>(null) }
    var addBlock by rememberSaveable(templateId) { mutableStateOf(false) }
    var deleteBlock by remember { mutableStateOf<BlockRow?>(null) }
    val busy by model.isBusy.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isActive) ActiveTemplateLabel() else Text("Plantilla inactiva", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (isActive) "Los cambios se aplican al guardar" else "Los bloques no aparecen en Inicio hasta activarla", color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
                if (!isActive) Button(onClick = onActivate) { Text("Activar plantilla") }
        }
        WeekdaySelector(1 shl selectedDay, { selectedDay = it }, Modifier.padding(top = 16.dp, bottom = 8.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(weekdayNames[selectedDay], style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { addBlock = true }) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Bloque") }
        }
        val dayBlocks = blocks.filter { it.daysMask and (1 shl selectedDay) != 0 }.sortedBy { it.startMinute }
        if (dayBlocks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 26.dp)) {
                Text("${weekdayNames[selectedDay]} sin bloques", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp)); Text("Añade una actividad o elige otro día.", color = Color(0xFFB8B8B8))
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(dayBlocks, key = { it.blockId }) { block ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { editBlock = block }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(78.dp)) {
                            Text(formatMinute(block.startMinute), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(formatMinute(block.endMinute), color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(block.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            if (block.description.isNotBlank()) Text(block.description, color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
                            block.tagName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onClick = { deleteBlock = block }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar ${block.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
    if (addBlock) BlockDialog(null, selectedDay, blocks, busy, onDismiss = { addBlock = false }) { title, desc, tag, mask, start, end ->
        model.saveBlock(templateId, null, title, desc, tag, mask, start, end) { success -> if (success) addBlock = false }
    }
    editBlock?.let { block -> BlockDialog(block, selectedDay, blocks, busy, onDismiss = { editBlock = null }) { title, desc, tag, mask, start, end ->
        model.saveBlock(templateId, block.blockId, title, desc, tag, mask, start, end) { success -> if (success) editBlock = null }
    } }
    deleteBlock?.let { block -> AlertDialog(
        onDismissRequest = { deleteBlock = null }, title = { Text("Eliminar ${block.title}?") },
        text = { Text("Se quitará de esta semana. Las revisiones históricas anteriores se conservarán.") },
        confirmButton = { Button(onClick = { model.deleteBlock(templateId, block.blockId); deleteBlock = null }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleteBlock = null }) { Text("Cancelar") } }
    ) }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AlarmTimeField(label: String, minuteOfDay: Int, modifier: Modifier = Modifier, onTimeSelected: (Int) -> Unit) {
    var open by remember(label) { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.heightIn(min = 64.dp), shape = RoundedCornerShape(12.dp)) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = Color(0xFFB8B8B8), style = MaterialTheme.typography.labelSmall)
            Text(formatMinute(minuteOfDay), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
    if (open) {
        val initial = minuteOfDay.coerceIn(0, 1439)
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(if (label == "Inicio") "Elige la hora de inicio" else "Elige la hora de fin") },
            text = {
                Column {
                    TimePicker(state = state)
                    if (label == "Fin") TextButton(onClick = { onTimeSelected(1440); open = false }) {
                        Text("Fin del día · 24:00")
                    }
                }
            },
            confirmButton = { Button(onClick = {
                onTimeSelected(state.hour * 60 + state.minute)
                open = false
            }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun BlockDialog(
    block: BlockRow?, selectedDay: Int, existing: List<BlockRow>, saving: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Int, Int) -> Unit
) {
    var title by rememberSaveable(block?.blockId) { mutableStateOf(block?.title.orEmpty()) }
    var description by rememberSaveable(block?.blockId) { mutableStateOf(block?.description.orEmpty()) }
    var tag by rememberSaveable(block?.blockId) { mutableStateOf(block?.tagName.orEmpty()) }
    var mask by rememberSaveable(block?.blockId) { mutableStateOf(block?.daysMask ?: (1 shl selectedDay)) }
    var start by rememberSaveable(block?.blockId) { mutableStateOf(block?.startMinute?.let(::formatMinute) ?: "09:00") }
    var end by rememberSaveable(block?.blockId) { mutableStateOf(block?.endMinute?.let(::formatMinute) ?: "10:00") }
    var warning by remember(block?.blockId) { mutableStateOf(false) }
    var error by remember(block?.blockId) { mutableStateOf<String?>(null) }
    val startMin = parseMinute(start); val endMin = parseMinute(end, allowEndOfDay = true)
    val valid = title.isNotBlank() && mask != 0 && startMin != null && endMin != null && startMin < endMin
    val overlap = valid && existing.any { other ->
        other.blockId != block?.blockId && other.daysMask and mask != 0 && startMin!! < other.endMinute && endMin!! > other.startMinute
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (block == null) "Nuevo bloque" else "Editar bloque") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { OutlinedTextField(title, { title = it; error = null }, label = { Text("Título *") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(tag, { tag = it }, label = { Text("Etiqueta (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { Text("Días", style = MaterialTheme.typography.labelLarge) }
                item { WeekdaySelector(mask, { day -> mask = mask xor (1 shl day) }, multiple = true) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlarmTimeField("Inicio", parseMinute(start) ?: 540, Modifier.weight(1f)) { start = formatMinute(it); error = null; warning = false }
                    AlarmTimeField("Fin", parseMinute(end, allowEndOfDay = true) ?: 600, Modifier.weight(1f)) { end = formatMinute(it); error = null; warning = false }
                } }
                item {
                    if (!valid) Text("Completa título, días y un horario válido (fin posterior al inicio; sin cruce de medianoche).", color = MaterialTheme.colorScheme.error)
                    if (overlap) Text("Aviso: este horario se solapa con otro bloque. Ambos se conservarán.", color = Color(0xFFFFC857))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (overlap && !warning) warning = true
                else if (valid) onSave(title, description, tag, mask, startMin!!, endMin!!)
            }, enabled = valid && !saving) { Text(if (saving) "Guardando…" else if (overlap && !warning) "Revisar solape" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } }
    )
}

@Composable
private fun NameDialog(title: String, label: String, confirm: String, initialValue: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(title, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AppearanceScreen(modifier: Modifier, accentColor: Int, model: AppViewModel, onOpenPicker: () -> Unit) {
    val widgetOpacity by model.widgetOpacity.collectAsStateWithLifecycle()
    val busy by model.isBusy.collectAsStateWithLifecycle()
    var opacityDraft by rememberSaveable(widgetOpacity) { mutableFloatStateOf(widgetOpacity.toFloat()) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        CloudSettingsSection(model)
        Text("Color de acento", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp)); Text("Elige una muestra o crea un color personalizado. El fondo AMOLED permanece negro.", color = Color(0xFFB8B8B8))
        Spacer(Modifier.height(24.dp)); Text("PALETA", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB8B8B8))
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            presets.forEach { preset ->
                val selected = accentColor == preset.argb
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color(preset.argb)).then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { model.setAccent(preset.argb) })
                        .semantics { contentDescription = preset.name }, contentAlignment = Alignment.Center) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = if (Color(preset.argb).luminance() > 0.179f) Color.Black else Color.White)
                    }
                    Spacer(Modifier.height(6.dp)); Text(preset.name, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else Color(0xFFB8B8B8))
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Surface(color = Color(0xFF101010), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPicker)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(accentColor)).border(1.dp, Color.White, CircleShape))
                Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                    Text(if (presets.any { it.argb == accentColor }) "Color seleccionado" else "Color personalizado", fontWeight = FontWeight.Medium)
                    Text("#${accentColor.toUInt().toString(16).takeLast(6).uppercase()}", color = Color(0xFFB8B8B8))
                }; Text("Cambiar", color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(14.dp)); TextButton(onClick = { model.setAccent(0xFFA78BFA.toInt()) }) { Text("Restablecer violeta predeterminado") }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))
        Text("Widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Fondo negro puro. La opacidad solo afecta al fondo, no al texto ni al progreso. Se aplica a todos los widgets.", color = Color(0xFFB8B8B8))
        Spacer(Modifier.height(16.dp))
        Text("Opacidad del fondo: ${opacityDraft.roundToInt()} %", fontWeight = FontWeight.Medium)
        Slider(value = opacityDraft, onValueChange = { opacityDraft = it }, valueRange = 0f..100f, steps = 99,
            modifier = Modifier.semantics { contentDescription = "Opacidad del fondo del widget" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 % · Transparente", style = MaterialTheme.typography.bodySmall)
            Text("100 % · Negro sólido", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Text("Vista previa sobre un fondo de ejemplo", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8B8B8))
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF63899C), Color(0xFFB18A78))))
            .padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color(widgetBackgroundArgb(opacityDraft.roundToInt()))).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("BLOCKNIZE", color = Color(accentColor), style = MaterialTheme.typography.labelSmall)
                    Text("Actualizar", color = Color(0xFFB8B8B8), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tarea de ejemplo", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("09:00–10:00 · Estudio", color = Color(0xFFB8B8B8), style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { 0.45f }, modifier = Modifier.fillMaxSize(), color = Color(accentColor), trackColor = Color(0xFF404040))
                        Text("45%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Button(onClick = { model.setWidgetOpacity(opacityDraft.roundToInt()) },
            enabled = !busy && opacityDraft.roundToInt() != widgetOpacity) { Text("Aplicar") }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))
        BackupSettingsSection(model)
    }
}

@Composable
private fun AccentPickerDialog(initial: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val hsv = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by remember(initial) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(hsv[1]) }
    var value by remember(initial) { mutableFloatStateOf(hsv[2]) }
    val generated = remember(hue, saturation, value) { AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)) }
    var hex by remember(initial) { mutableStateOf(colorHex(initial)) }
    var hexEdited by remember { mutableStateOf(false) }
    val parsed = parseAccentHex(hex)
    val previewColor = if (hexEdited) parsed ?: generated else generated
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Color personalizado") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Surface(color = Color(0xFF101010), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(previewColor)).border(1.dp, Color.White, CircleShape))
                    Spacer(Modifier.width(12.dp)); Text("Vista previa · BlockNize", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("Matiz"); Slider(value = hue, onValueChange = { hue = it; hexEdited = false; hex = colorHex(AndroidColor.HSVToColor(floatArrayOf(it, saturation, value))) }, valueRange = 0f..360f)
            Text("Saturación"); Slider(value = saturation, onValueChange = { saturation = it; hexEdited = false; hex = colorHex(AndroidColor.HSVToColor(floatArrayOf(hue, it, value))) })
            Text("Brillo"); Slider(value = value, onValueChange = { value = it; hexEdited = false; hex = colorHex(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, it))) })
            OutlinedTextField(hex, { hex = it.uppercase(); hexEdited = true }, label = { Text("HEX · #RRGGBB") }, singleLine = true,
                isError = parsed == null, supportingText = { if (parsed == null) Text("Introduce seis dígitos hexadecimales") }, modifier = Modifier.fillMaxWidth())
            if (Color(previewColor).luminance() < 0.18f) Text("Este color puede tener poco contraste sobre negro; los controles críticos usarán una variante accesible.", color = Color(0xFFFFC857), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { Button(onClick = { onApply(if (hexEdited) parsed!! else generated) }, enabled = !hexEdited || parsed != null) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

private fun parseMinute(input: String, allowEndOfDay: Boolean = false): Int? =
    com.blocknize.app.domain.parseScheduleMinute(input, allowEndOfDay)

private fun formatMinute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
private fun colorHex(color: Int): String = "#${color.toUInt().toString(16).takeLast(6).uppercase()}"
