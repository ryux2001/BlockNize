package com.blocknize.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.blocknize.app.domain.ScheduleBlock
import com.blocknize.app.domain.ScheduleTemplate
import com.blocknize.app.domain.ScheduleWindow
import com.blocknize.app.domain.parseScheduleMinute
import java.awt.EventQueue
import java.util.UUID
import kotlin.concurrent.thread

private enum class TemplateDialogMode { CREATE, RENAME }

private val weekdays = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val weekdayShort = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
private val ink = Color(0xFFF4F1FA)
private val muted = Color(0xFFA7A2B1)
private val canvas = Color(0xFF0D0C10)
private val sidebar = Color(0xFF131117)
private val panel = Color(0xFF1A1820)
private val selectedPanel = Color(0xFF282333)
private val accent = Color(0xFFB49AFF)
private val danger = Color(0xFFB94848)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BlockNize",
        state = rememberWindowState(width = 1420.dp, height = 900.dp)
    ) {
        DesktopApp()
    }
}

@Composable
private fun DesktopApp() {
    val localStore = remember { DesktopScheduleStore() }
    val cloudSettingsStore = remember { DesktopCloudSettingsStore() }
    val cloudClient = remember { DesktopCloudClient() }
    val loadResult = remember { runCatching { localStore.load() } }
    val cloudConfigResult = remember { runCatching { cloudSettingsStore.load() } }
    var schedule by remember { mutableStateOf(loadResult.getOrElse { DesktopScheduleData.empty() }) }
    var cloudConfig by remember { mutableStateOf(cloudConfigResult.getOrElse { DesktopCloudConfig.empty() }) }
    var remoteWorkspace by remember { mutableStateOf<DesktopRemoteWorkspace?>(null) }
    var remoteTemplateId by remember { mutableStateOf<String?>(null) }
    var cloudView by remember { mutableStateOf(false) }
    var storageError by remember {
        mutableStateOf(loadResult.exceptionOrNull()?.let { "No se pudo leer el horario local. No se sobrescribirá: ${it.message.orEmpty()}" })
    }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var cloudDialogError by remember { mutableStateOf<String?>(cloudConfigResult.exceptionOrNull()?.message) }
    var cloudLoading by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var blockBeingEdited by remember { mutableStateOf<ScheduleBlock?>(null) }
    var blockToDelete by remember { mutableStateOf<ScheduleBlock?>(null) }
    var blockDeleteOperationId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var templateDialogMode by remember { mutableStateOf<TemplateDialogMode?>(null) }
    var templateToDelete by remember { mutableStateOf<ScheduleTemplate?>(null) }
    val localCanPersist = loadResult.isSuccess
    val selectedTemplate = if (cloudView) {
        remoteWorkspace?.templates?.firstOrNull { it.id == remoteTemplateId }
    } else {
        schedule.templates.firstOrNull { it.id == schedule.selectedTemplateId } ?: schedule.templates.first()
    }
    val canEdit = !cloudLoading && if (cloudView) remoteWorkspace != null else localCanPersist

    fun persistLocal(next: DesktopScheduleData): Boolean {
        if (!localCanPersist) return false
        return runCatching { localStore.save(next) }.fold(
            onSuccess = { schedule = next; storageError = null; true },
            onFailure = { storageError = "No se pudo guardar el cambio: ${it.message.orEmpty()}"; false }
        )
    }

    fun mutateCloud(
        mutation: (DesktopRemoteWorkspace) -> DesktopRemoteWorkspace,
        onSuccess: (DesktopRemoteWorkspace) -> Unit = {}
    ) {
        val current = remoteWorkspace ?: return
        if (cloudLoading) return
        cloudLoading = true
        cloudError = null
        thread(isDaemon = true, name = "blocknize-cloud-mutation") {
            val result = runCatching { mutation(current) }
            val recoveredWorkspace = if (result.isFailure) {
                runCatching { cloudClient.refreshWorkspace() }.getOrNull()
            } else null
            EventQueue.invokeLater {
                cloudLoading = false
                result.onSuccess { remote ->
                    remoteWorkspace = remote
                    if (remote.templates.none { it.id == remoteTemplateId }) {
                        remoteTemplateId = remote.activeTemplateId ?: remote.templates.firstOrNull()?.id
                    }
                    cloudError = remote.syncError
                    onSuccess(remote)
                }.onFailure { error ->
                    if (recoveredWorkspace != null) {
                        remoteWorkspace = recoveredWorkspace
                        if (recoveredWorkspace.templates.none { it.id == remoteTemplateId }) {
                            remoteTemplateId = recoveredWorkspace.activeTemplateId ?: recoveredWorkspace.templates.firstOrNull()?.id
                        }
                    }
                    cloudError = error.message ?: "No se pudo guardar el cambio en la cuenta. Actualiza y vuelve a intentarlo."
                }
            }
        }
    }

    fun connect(config: DesktopCloudConfig, email: String, password: String) {
        cloudLoading = true
        cloudDialogError = null
        cloudError = null
        thread(isDaemon = true, name = "blocknize-cloud-connect") {
            val result = runCatching {
                cloudSettingsStore.save(config)
                cloudClient.connect(config, email, password, cloudSettingsStore.syncDeviceId())
            }
            EventQueue.invokeLater {
                cloudLoading = false
                result.onSuccess { remote ->
                    cloudConfig = config
                    remoteWorkspace = remote
                    remoteTemplateId = remote.activeTemplateId ?: remote.templates.firstOrNull()?.id
                    cloudView = true
                    cloudError = remote.syncError
                    cloudDialogError = null
                    showCloudDialog = false
                }.onFailure { error ->
                    cloudDialogError = error.message ?: "No se pudo conectar con la cuenta."
                }
            }
        }
    }

    fun refreshCloud() {
        if (cloudLoading || remoteWorkspace == null) return
        cloudLoading = true
        cloudError = null
        thread(isDaemon = true, name = "blocknize-cloud-refresh") {
            val result = runCatching { cloudClient.refreshWorkspace() }
            EventQueue.invokeLater {
                cloudLoading = false
                result.onSuccess { remote ->
                    remoteWorkspace = remote
                    if (remote.templates.none { it.id == remoteTemplateId }) {
                        remoteTemplateId = remote.activeTemplateId ?: remote.templates.firstOrNull()?.id
                    }
                    cloudError = remote.syncError
                }.onFailure { error ->
                    cloudError = error.message ?: "No se pudo actualizar la cuenta."
                }
            }
        }
    }

    fun signOutCloud() {
        if (cloudLoading) return
        cloudLoading = true
        thread(isDaemon = true, name = "blocknize-cloud-sign-out") {
            val result = runCatching { cloudClient.signOut() }
            EventQueue.invokeLater {
                remoteWorkspace = null
                remoteTemplateId = null
                cloudView = false
                cloudLoading = false
                cloudError = result.exceptionOrNull()?.let {
                    "Se cerró la sesión en este equipo, pero el servidor no confirmó el cierre: ${it.message.orEmpty()}"
                }
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF24183E),
        background = canvas,
        surface = panel,
        onSurface = ink
    )) {
        Row(Modifier.fillMaxSize().background(canvas)) {
            Sidebar(
                schedule = schedule,
                remoteWorkspace = remoteWorkspace,
                cloudView = cloudView,
                selectedRemoteTemplateId = remoteTemplateId,
                canPersist = canEdit,
                onSelectLocal = { templateId -> persistLocal(schedule.copy(selectedTemplateId = templateId)) },
                onSelectRemote = { remoteTemplateId = it },
                onUseLocal = { cloudView = false },
                onUseCloud = { if (remoteWorkspace != null) cloudView = true },
                onCreate = { templateDialogMode = TemplateDialogMode.CREATE }
            )
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 32.dp, vertical = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Agenda semanal", color = ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        Text("Organiza tus bloques de lunes a domingo.", color = muted, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 5.dp))
                    }
                    Surface(shape = RoundedCornerShape(50), color = selectedPanel) {
                        val syncStatus = remoteWorkspace?.let { workspace ->
                            when {
                                cloudLoading -> "sincronizando"
                                workspace.syncConflicts.isNotEmpty() -> "${workspace.syncConflicts.size} conflicto(s)"
                                workspace.pendingOperationCount > 0 -> "${workspace.pendingOperationCount} cambio(s) pendiente(s)"
                                cloudView -> "Nube · sincronizada"
                                else -> "Solo este equipo · nube sincronizada"
                            }
                        } ?: "Solo este equipo"
                        Text(syncStatus, color = if (remoteWorkspace?.syncConflicts?.isNotEmpty() == true) Color(0xFFFFC4C4) else accent, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    if (remoteWorkspace == null) {
                        OutlinedButton(onClick = { showCloudDialog = true }, enabled = !cloudLoading) { Text("Conectar cuenta") }
                    } else if (cloudView) {
                        TextButton(onClick = ::refreshCloud, enabled = !cloudLoading) {
                            Text(if (cloudLoading) "Actualizando…" else "Actualizar")
                        }
                        TextButton(onClick = ::signOutCloud, enabled = !cloudLoading) { Text("Desconectar") }
                    } else {
                        OutlinedButton(onClick = { cloudView = true }, enabled = !cloudLoading) { Text("Abrir cuenta nube") }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { blockBeingEdited = null; showBlockDialog = true }, enabled = canEdit) {
                        Text("Añadir bloque")
                    }
                }

                storageError?.let { message ->
                    Spacer(Modifier.height(16.dp))
                    ErrorBanner(message)
                }
                cloudError?.let { message ->
                    Spacer(Modifier.height(16.dp))
                    ErrorBanner(message)
                }
                remoteWorkspace?.syncConflicts?.forEach { conflict ->
                    Spacer(Modifier.height(12.dp))
                    SyncConflictBanner(
                        conflict = conflict,
                        enabled = !cloudLoading,
                        onKeepLocal = {
                            mutateCloud({ cloudClient.resolveConflict(conflict.operationId, keepLocal = true) })
                        },
                        onUseAccount = {
                            mutateCloud({ cloudClient.resolveConflict(conflict.operationId, keepLocal = false) })
                        }
                    )
                }

                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(selectedTemplate?.name ?: "Sin semanas", color = ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Text(blockCountLabel(selectedTemplate?.blocks?.size ?: 0), color = muted, fontSize = 12.sp)
                    }
                    val activeTemplateId = if (cloudView) remoteWorkspace?.activeTemplateId else schedule.activeTemplateId
                    if (selectedTemplate != null && activeTemplateId == selectedTemplate.id) {
                        Surface(shape = RoundedCornerShape(50), color = Color(0xFF24342D)) {
                            Text("Activa", color = Color(0xFF9EE0B4), fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    } else if (selectedTemplate != null && cloudView) {
                        OutlinedButton(onClick = {
                            mutateCloud({ workspace -> cloudClient.activateTemplate(workspace, selectedTemplate.id) })
                        }, enabled = canEdit) { Text("Activar semana") }
                    } else if (selectedTemplate != null) {
                        OutlinedButton(onClick = {
                            persistLocal(schedule.copy(activeTemplateId = selectedTemplate.id))
                        }, enabled = canEdit) { Text("Activar semana") }
                    }
                    if (selectedTemplate != null) {
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { templateDialogMode = TemplateDialogMode.RENAME }, enabled = canEdit) {
                            Text("Renombrar")
                        }
                        if (!cloudView) TextButton(onClick = { templateToDelete = selectedTemplate },
                            enabled = canEdit && schedule.templates.size > 1) { Text("Eliminar semana") }
                    }
                }

                Spacer(Modifier.height(12.dp))
                WeekAgenda(
                    modifier = Modifier.weight(1f),
                    template = selectedTemplate,
                    canEdit = canEdit,
                    cloudView = cloudView,
                    onAdd = { blockBeingEdited = null; showBlockDialog = true },
                    onEdit = { block -> blockBeingEdited = block; showBlockDialog = true },
                    onDelete = { block -> blockToDelete = block; blockDeleteOperationId = UUID.randomUUID().toString() }
                )
            }
        }

        if (showCloudDialog) {
            CloudConnectionDialog(
                initial = cloudConfig,
                isConnecting = cloudLoading,
                error = cloudDialogError,
                onDismiss = { if (!cloudLoading) { showCloudDialog = false; cloudDialogError = null } },
                onConnect = ::connect
            )
        }

        if (showBlockDialog && selectedTemplate != null && canEdit) {
            BlockEditorDialog(
                initial = blockBeingEdited,
                error = cloudError.takeIf { cloudView },
                onDismiss = { showBlockDialog = false; blockBeingEdited = null },
                onSave = { block, taskId, operationId ->
                    if (cloudView) {
                        mutateCloud(
                            { workspace -> cloudClient.saveBlock(workspace, selectedTemplate.id, block, taskId, operationId) },
                            onSuccess = { showBlockDialog = false; blockBeingEdited = null }
                        )
                    } else {
                        val updatedTemplate = selectedTemplate.copy(
                            blocks = selectedTemplate.blocks.filterNot { it.id == block.id } + block
                        )
                        val updatedSchedule = schedule.copy(templates = schedule.templates.map {
                            if (it.id == selectedTemplate.id) updatedTemplate else it
                        })
                        if (persistLocal(updatedSchedule)) {
                            showBlockDialog = false
                            blockBeingEdited = null
                        }
                    }
                }
            )
        }

        when (templateDialogMode) {
            TemplateDialogMode.CREATE -> TemplateNameDialog(
                title = "Nueva semana",
                initialName = "",
                error = cloudError.takeIf { cloudView },
                onDismiss = { templateDialogMode = null },
                onSave = { name, templateId, operationId ->
                    val template = ScheduleTemplate(templateId, name, emptyList())
                    if (cloudView) {
                        val existingIds = remoteWorkspace?.templates?.map(ScheduleTemplate::id).orEmpty().toSet()
                        mutateCloud(
                            { workspace -> cloudClient.createTemplate(workspace, name, templateId, operationId) },
                            onSuccess = { remote ->
                                remoteTemplateId = remote.templates.firstOrNull { it.id == templateId }?.id
                                    ?: remote.templates.firstOrNull { it.id !in existingIds }?.id
                                    ?: remote.activeTemplateId ?: remote.templates.firstOrNull()?.id
                                templateDialogMode = null
                            }
                        )
                    } else if (persistLocal(schedule.copy(templates = schedule.templates + template, selectedTemplateId = template.id))) {
                        templateDialogMode = null
                    }
                }
            )
            TemplateDialogMode.RENAME -> selectedTemplate?.let { selected ->
                TemplateNameDialog(
                    title = "Renombrar semana",
                    initialName = selected.name,
                    error = cloudError.takeIf { cloudView },
                    onDismiss = { templateDialogMode = null },
                    onSave = { name, _, operationId ->
                        if (cloudView) {
                            mutateCloud(
                                { workspace -> cloudClient.renameTemplate(workspace, selected.id, name, operationId) },
                                onSuccess = { templateDialogMode = null }
                            )
                        } else if (persistLocal(schedule.copy(templates = schedule.templates.map {
                                if (it.id == selected.id) selected.copy(name = name) else it
                            }))) {
                            templateDialogMode = null
                        }
                    }
                )
            }
            null -> Unit
        }

        blockToDelete?.let { block ->
            AlertDialog(
                onDismissRequest = { blockToDelete = null },
                title = { Text("Eliminar bloque") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Se quitará «${block.title}» de todos los días en los que se repite.")
                        cloudError.takeIf { cloudView }?.let { Text(it, color = Color(0xFFFF9A9A), fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    Button(colors = ButtonDefaults.buttonColors(containerColor = danger), onClick = {
                        if (cloudView) {
                            val current = selectedTemplate ?: return@Button
                            mutateCloud(
                                { workspace -> cloudClient.deleteBlock(workspace, current.id, block.id, blockDeleteOperationId) },
                                onSuccess = { blockToDelete = null }
                            )
                        } else {
                            val current = schedule.templates.firstOrNull { it.id == schedule.selectedTemplateId } ?: return@Button
                            val updatedTemplate = current.copy(blocks = current.blocks.filterNot { it.id == block.id })
                            val updated = schedule.copy(templates = schedule.templates.map {
                                if (it.id == current.id) updatedTemplate else it
                            })
                            if (persistLocal(updated)) blockToDelete = null
                        }
                    }) { Text("Eliminar") }
                },
                dismissButton = { TextButton(onClick = { blockToDelete = null }) { Text("Cancelar") } }
            )
        }

        templateToDelete?.let { template ->
            AlertDialog(
                onDismissRequest = { templateToDelete = null },
                title = { Text("Eliminar semana") },
                text = { Text("Se eliminarán ${template.blocks.size} bloques guardados en «${template.name}».") },
                confirmButton = {
                    Button(colors = ButtonDefaults.buttonColors(containerColor = danger), onClick = {
                        val remaining = schedule.templates.filterNot { it.id == template.id }
                        if (remaining.isEmpty()) return@Button
                        val nextSelected = if (schedule.selectedTemplateId == template.id) remaining.first().id else schedule.selectedTemplateId
                        val nextActive = if (schedule.activeTemplateId == template.id) null else schedule.activeTemplateId
                        if (persistLocal(schedule.copy(templates = remaining, selectedTemplateId = nextSelected, activeTemplateId = nextActive))) {
                            templateToDelete = null
                        }
                    }) { Text("Eliminar semana") }
                },
                dismissButton = { TextButton(onClick = { templateToDelete = null }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
private fun Sidebar(
    schedule: DesktopScheduleData,
    remoteWorkspace: DesktopRemoteWorkspace?,
    cloudView: Boolean,
    selectedRemoteTemplateId: String?,
    canPersist: Boolean,
    onSelectLocal: (String) -> Unit,
    onSelectRemote: (String) -> Unit,
    onUseLocal: () -> Unit,
    onUseCloud: () -> Unit,
    onCreate: () -> Unit
) {
    val visibleTemplates = if (cloudView) remoteWorkspace?.templates.orEmpty() else schedule.templates
    val selectedId = if (cloudView) selectedRemoteTemplateId else schedule.selectedTemplateId
    val activeId = if (cloudView) remoteWorkspace?.activeTemplateId else schedule.activeTemplateId

    Column(
        Modifier.width(250.dp).fillMaxHeight().background(sidebar).padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(Modifier.size(40.dp), shape = RoundedCornerShape(13.dp), color = accent) {
                Box(contentAlignment = Alignment.Center) {
                    Text("B", color = Color(0xFF24183E), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column {
                Text("BlockNize", color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Escritorio", color = muted, fontSize = 12.sp)
            }
        }
        if (remoteWorkspace != null) {
            Spacer(Modifier.height(22.dp))
            TextButton(onClick = onUseLocal, enabled = cloudView) { Text("Agenda local") }
            TextButton(onClick = onUseCloud, enabled = !cloudView) {
                Text(remoteWorkspace.email?.let { "Cuenta nube · $it" } ?: "Cuenta nube")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(if (cloudView) "SEMANAS DE LA CUENTA" else "MIS SEMANAS", color = muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            visibleTemplates.forEach { template ->
                val selected = template.id == selectedId
                Surface(shape = RoundedCornerShape(11.dp), color = if (selected) selectedPanel else Color.Transparent) {
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = if (cloudView) true else canPersist) {
                            if (cloudView) onSelectRemote(template.id) else onSelectLocal(template.id)
                        }.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(template.name, color = if (selected) ink else muted, fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                            if (activeId == template.id) Text("Activa", color = Color(0xFF9EE0B4), fontSize = 10.sp)
                        }
                        Text(blockCountLabel(template.blocks.size), color = muted, fontSize = 11.sp)
                    }
                }
            }
            if (visibleTemplates.isEmpty()) {
                Text("Esta cuenta todavía no tiene semanas.", color = muted, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        Button(onClick = onCreate, enabled = canPersist, modifier = Modifier.fillMaxWidth()) { Text("Crear semana") }
        Spacer(Modifier.height(15.dp))
        Text(if (cloudView) "Los cambios se guardan en línea" else "Horario local de este equipo", color = muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun WeekAgenda(
    modifier: Modifier = Modifier,
    template: ScheduleTemplate?,
    canEdit: Boolean,
    cloudView: Boolean,
    onAdd: () -> Unit,
    onEdit: (ScheduleBlock) -> Unit,
    onDelete: (ScheduleBlock) -> Unit
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (template == null) {
            Text(if (cloudView) "La cuenta no tiene una semana para mostrar." else "Crea una semana para empezar.",
                color = muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 18.dp))
            return@Column
        }
        weekdays.forEachIndexed { index, day ->
            val bit = 1 shl index
            val blocks = template.blocks.filter { it.daysMask and bit != 0 }.sortedBy { it.startMinute }
            Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(day, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    Text(blockCountLabel(blocks.size), color = muted, fontSize = 11.sp)
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = onAdd, enabled = canEdit) { Text("Añadir") }
                }
                if (blocks.isEmpty()) {
                    Text("Día libre", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        blocks.forEach { block -> BlockRow(block, canEdit, onEdit, onDelete) }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF302D36))
        }
    }
}

@Composable
private fun BlockRow(
    block: ScheduleBlock,
    canEdit: Boolean,
    onEdit: (ScheduleBlock) -> Unit,
    onDelete: (ScheduleBlock) -> Unit
) {
    Surface(color = panel, shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = selectedPanel) {
                Text("${formatMinute(block.startMinute)} – ${formatMinute(block.endMinute)}", color = accent, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(block.title, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (block.description.isNotBlank()) Text(block.description, color = muted, fontSize = 11.sp, maxLines = 1)
            }
            block.tagName?.takeIf(String::isNotBlank)?.let { Text(it, color = muted, fontSize = 11.sp) }
            if (canEdit) {
                TextButton(onClick = { onEdit(block) }) { Text("Editar") }
                TextButton(onClick = { onDelete(block) }) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun BlockEditorDialog(
    initial: ScheduleBlock?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (ScheduleBlock, String, String) -> Unit
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var tagName by remember(initial?.id) { mutableStateOf(initial?.tagName.orEmpty()) }
    var start by remember(initial?.id) { mutableStateOf(initial?.let { formatMinute(it.startMinute) }.orEmpty().ifBlank { "09:00" }) }
    var end by remember(initial?.id) { mutableStateOf(initial?.let { formatMinute(it.endMinute) }.orEmpty().ifBlank { "10:00" }) }
    var daysMask by remember(initial?.id) { mutableStateOf(initial?.daysMask ?: 0) }
    val blockId = remember(initial?.id) { initial?.id ?: UUID.randomUUID().toString() }
    val taskId = remember(blockId) { UUID.randomUUID().toString() }
    var operationId by remember(initial?.id) { mutableStateOf(UUID.randomUUID().toString()) }
    val startMinute = parseScheduleMinute(start)
    val endMinute = parseScheduleMinute(end, allowEndOfDay = true)
    val validWindow = startMinute != null && endMinute != null && ScheduleWindow(startMinute, endMinute).isValid
    val isValid = title.isNotBlank() && daysMask in 1..127 && validWindow

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Añadir bloque" else "Editar bloque") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                error?.let { Text(it, color = Color(0xFFFF9A9A), fontSize = 12.sp) }
                OutlinedTextField(title, {
                    if (it.length <= 160) { title = it; operationId = UUID.randomUUID().toString() }
                }, label = { Text("Actividad") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, {
                    if (it.length <= 4000) { description = it; operationId = UUID.randomUUID().toString() }
                }, label = { Text("Descripción") },
                    minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tagName, {
                    if (it.length <= 80) { tagName = it; operationId = UUID.randomUUID().toString() }
                }, label = { Text("Etiqueta (opcional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Column {
                    Text("Se repite", color = muted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        weekdayShort.forEachIndexed { index, day ->
                            val bit = 1 shl index
                            FilterChip(selected = daysMask and bit != 0, onClick = {
                                daysMask = daysMask xor bit
                                operationId = UUID.randomUUID().toString()
                            }, label = { Text(day) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(start, {
                        if (it.length <= 5) { start = it; operationId = UUID.randomUUID().toString() }
                    }, label = { Text("Inicio") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.weight(1f))
                    OutlinedTextField(end, {
                        if (it.length <= 5) { end = it; operationId = UUID.randomUUID().toString() }
                    }, label = { Text("Fin") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.weight(1f))
                }
                if (!validWindow) Text("El fin debe ser posterior al inicio. Puedes usar 24:00 como hora final.", color = Color(0xFFFF9A9A), fontSize = 11.sp)
                if (daysMask == 0) Text("Elige al menos un día.", color = Color(0xFFFF9A9A), fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(ScheduleBlock(
                    id = blockId,
                    title = title.trim(),
                    description = description.trim(),
                    tagName = tagName.trim().ifBlank { null },
                    daysMask = daysMask,
                    startMinute = startMinute!!,
                    endMinute = endMinute!!
                ), taskId, operationId)
            }, enabled = isValid) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun TemplateNameDialog(
    title: String,
    initialName: String,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    val templateId = remember(title, initialName) { UUID.randomUUID().toString() }
    var operationId by remember(title, initialName) { mutableStateOf(UUID.randomUUID().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = Color(0xFFFF9A9A), fontSize = 12.sp) }
                OutlinedTextField(name, {
                    if (it.length <= 120) { name = it; operationId = UUID.randomUUID().toString() }
                }, label = { Text("Nombre de la semana") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), templateId, operationId) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun CloudConnectionDialog(
    initial: DesktopCloudConfig,
    isConnecting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConnect: (DesktopCloudConfig, String, String) -> Unit
) {
    var apiBaseUrl by remember(initial.apiBaseUrl) { mutableStateOf(initial.apiBaseUrl) }
    var authBaseUrl by remember(initial.authBaseUrl) { mutableStateOf(initial.authBaseUrl) }
    var authOrigin by remember(initial.authOrigin) { mutableStateOf(initial.authOrigin) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text("Conectar una cuenta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(apiBaseUrl, { apiBaseUrl = it }, label = { Text("URL de la API") },
                    placeholder = { Text("https://servidor/api/v1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(authBaseUrl, { authBaseUrl = it }, label = { Text("URL de Neon Auth") },
                    placeholder = { Text("https://servidor/auth") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(authOrigin, { authOrigin = it }, label = { Text("Origen permitido") },
                    placeholder = { Text("https://tu-app") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HorizontalDivider(color = Color(0xFF302D36))
                OutlinedTextField(email, { email = it }, label = { Text("Correo") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Contraseña") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("La contraseña no se guarda. La sesión solo dura mientras esta app esté abierta.",
                    color = muted, fontSize = 11.sp, lineHeight = 16.sp)
                if (isConnecting) Text("Conectando y descargando la agenda…", color = accent, fontSize = 12.sp)
                error?.let { Text(it, color = Color(0xFFFF9A9A), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConnect(DesktopCloudConfig(apiBaseUrl, authBaseUrl, authOrigin), email, password)
            }, enabled = !isConnecting && apiBaseUrl.isNotBlank() && authBaseUrl.isNotBlank() && authOrigin.isNotBlank() &&
                email.isNotBlank() && password.isNotEmpty()) {
                Text(if (isConnecting) "Conectando…" else "Conectar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isConnecting) { Text("Cancelar") } }
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(color = Color(0xFF382020), shape = RoundedCornerShape(12.dp)) {
        Text(message, color = Color(0xFFFFC4C4), fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(14.dp))
    }
}

@Composable
private fun SyncConflictBanner(
    conflict: DesktopSyncConflict,
    enabled: Boolean,
    onKeepLocal: () -> Unit,
    onUseAccount: () -> Unit
) {
    Surface(color = Color(0xFF302817), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conflicto: ${conflict.summary}", color = Color(0xFFFFD98A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(conflict.message, color = ink, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onKeepLocal, enabled = enabled) { Text("Conservar mi cambio") }
                OutlinedButton(onClick = onUseAccount, enabled = enabled) { Text("Usar versión de la cuenta") }
            }
        }
    }
}

private fun blockCountLabel(count: Int): String = if (count == 1) "1 bloque" else "$count bloques"
private fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
