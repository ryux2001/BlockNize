package com.blocknize.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blocknize.app.data.AccentStore
import com.blocknize.app.data.ActiveSelectionEntity
import com.blocknize.app.data.AppDatabase
import com.blocknize.app.data.BlockRow
import com.blocknize.app.data.AgendaOccurrence
import com.blocknize.app.data.TagEntity
import com.blocknize.app.data.TemplateRepository
import com.blocknize.app.data.TemplateRow
import com.blocknize.app.data.TransferTemplate
import com.blocknize.app.data.FullBackupRepository
import com.blocknize.app.data.BackupSummary
import com.blocknize.app.data.LocalDataAccess
import com.blocknize.app.data.BlockNizeApiClient
import com.blocknize.app.data.NeonAuthClient
import com.blocknize.app.data.SessionVault
import com.blocknize.app.data.SyncConfiguration
import com.blocknize.app.data.SyncConfigurationStore
import com.blocknize.app.data.SyncStateEntity
import com.blocknize.app.data.SyncConflictEntity
import com.blocknize.app.data.CloudSyncScheduler
import com.blocknize.app.domain.SystemCivilClock
import com.blocknize.app.widget.WidgetRefreshWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class CloudSettingsState(
    val workspace: String = "LOCAL",
    val pendingOperations: Int = 0,
    val conflicts: Int = 0,
    val lastSyncAt: Long? = null,
    val lastSyncError: String? = null,
    val apiBaseUrl: String = "",
    val neonAuthBaseUrl: String = "",
    val authOrigin: String = "http://localhost:3000",
    val busy: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val workspaceZone = MutableStateFlow(ZoneId.systemDefault())
    private val clock: Clock = SystemCivilClock { workspaceZone.value }
    private var repository = TemplateRepository(AppDatabase.get(application), clock)
    private val workspaceGeneration = MutableStateFlow(0)
    private val accentStore = AccentStore(application)
    private var pendingBackup: String? = null
    private val _backupSummary = MutableStateFlow<BackupSummary?>(null)
    val backupSummary = _backupSummary.asStateFlow()

    val templates: StateFlow<List<TemplateRow>> = workspaceGeneration.flatMapLatest { repository.observeTemplates() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active: StateFlow<ActiveSelectionEntity?> = workspaceGeneration.flatMapLatest { repository.observeActive() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tags: StateFlow<List<TagEntity>> = workspaceGeneration.flatMapLatest { repository.observeTags() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accent = accentStore.accent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentStore.DEFAULT_ACCENT)
    val widgetOpacity = accentStore.widgetOpacity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentStore.DEFAULT_WIDGET_OPACITY)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _now = MutableStateFlow(clock.instant())
    val now: StateFlow<Instant> = _now.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    private val _agendaDate = MutableStateFlow<LocalDate?>(null)
    val agendaDate: StateFlow<LocalDate?> = _agendaDate.asStateFlow()
    private val _cloudSettings = MutableStateFlow(CloudSettingsState(workspace = AppDatabase.activeWorkspace(application)))
    val cloudSettings = _cloudSettings.asStateFlow()
    val syncConflicts: StateFlow<List<SyncConflictEntity>> = workspaceGeneration.flatMapLatest {
        AppDatabase.get(getApplication<Application>()).dao().observeSyncConflicts()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todayAgenda: StateFlow<List<AgendaOccurrence>> = combine(_now, workspaceGeneration) { instant, _ ->
            instant.atZone(clock.zone).toLocalDate()
        }
        .distinctUntilChanged()
        .flatMapLatest { date ->
            repository.observeAgenda(date).onEach { _agendaDate.value = date }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            try { backupRepository().recoverPending() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { _message.value = "No se pudieron recuperar las preferencias. Se reintentará antes de guardar." }
        }
        viewModelScope.launch {
            refreshCloudSettings()
            if (_cloudSettings.value.workspace.startsWith("CLOUD:")) {
                CloudSyncScheduler.ensurePeriodic(getApplication())
                CloudSyncScheduler.request(getApplication())
            }
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            workspaceGeneration.flatMapLatest {
                val dao = AppDatabase.get(context).dao()
                combine(dao.observeSyncOutboxCount(), dao.observeSyncConflictCount()) { pending, conflicts -> pending to conflicts }
            }.collect { (pending, conflicts) ->
                _cloudSettings.value = _cloudSettings.value.copy(pendingOperations = pending, conflicts = conflicts)
            }
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            workspaceGeneration.flatMapLatest { AppDatabase.get(context).dao().observeSyncState() }
                .collect { state ->
                    workspaceZone.value = if (state?.mode == "CLOUD")
                        state.accountTimeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
                    else ZoneId.systemDefault()
                    _cloudSettings.value = _cloudSettings.value.copy(
                        lastSyncAt = state?.lastSyncAt,
                        lastSyncError = state?.lastSyncError
                    )
                }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                _now.value = Clock.systemDefaultZone().instant()
            }
        }
    }

    fun blocks(templateId: String): Flow<List<BlockRow>> = workspaceGeneration.flatMapLatest { repository.observeBlocks(templateId) }
    suspend fun agendaForDate(date: LocalDate): List<AgendaOccurrence> = repository.getAgenda(date)
    suspend fun calendarAgenda(date: LocalDate): List<AgendaOccurrence> = repository.getCalendarAgenda(date)
    fun observeCalendarAgenda(date: LocalDate) = workspaceGeneration.flatMapLatest { repository.observeCalendarAgenda(date) }
    fun observeHistory(date: LocalDate) = workspaceGeneration.flatMapLatest { repository.observeHistory(date) }
    fun saveException(occurrence: AgendaOccurrence, action: String, date: LocalDate = occurrence.date,
                      title: String = occurrence.title, description: String = occurrence.description,
                      tag: String = occurrence.tagName.orEmpty(), start: Int = occurrence.startMinute,
                      end: Int = occurrence.endMinute, onComplete: (Boolean) -> Unit = {}) =
        runAction("No se pudo guardar la excepción.", onComplete) {
            repository.saveException(occurrence, action, date, title, description, tag, start, end)
        }
    fun observeWeek(startDate: LocalDate): Flow<Map<LocalDate, List<AgendaOccurrence>>> = workspaceGeneration.flatMapLatest { repository.observeWeek(startDate) }
    fun clearMessage() { _message.value = null }
    fun showMessage(text: String) { _message.value = text }
    fun refreshClock() { _now.value = Clock.systemDefaultZone().instant() }

    fun connectCloud(apiBaseUrl: String, authBaseUrl: String, authOrigin: String, email: String, password: String,
                     createAccount: Boolean, name: String = "") {
        if (_cloudSettings.value.busy || _isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            setCloudBusy(true)
            try {
                val context = getApplication<Application>()
                val configStore = SyncConfigurationStore(context)
                configStore.save(apiBaseUrl, authBaseUrl, authOrigin)
                val config = configStore.configuration.first()
                val auth = NeonAuthClient(SessionVault(context))
                val identity = if (createAccount) auth.signUp(config.neonAuthBaseUrl, name, email, password, config.authOrigin)
                    else auth.signIn(config.neonAuthBaseUrl, email, password, config.authOrigin)
                val api = BlockNizeApiClient(auth)
                var profile = api.request(config.apiBaseUrl, config.neonAuthBaseUrl, "/me", authOrigin = config.authOrigin).json()
                val remoteUserId = profile.optString("id").takeIf(String::isNotBlank) ?: error("La API no devolvió una identidad de cuenta.")
                val activeWorkspace = AppDatabase.activeWorkspace(context)
                val workspaceKey = "${config.apiBaseUrl}|${identity.issuer}|${identity.subject}|$remoteUserId"
                val workspaceName = "CLOUD:$workspaceKey"
                require(activeWorkspace == "LOCAL" || activeWorkspace == workspaceName) {
                    "Vuelve primero al espacio local antes de cambiar a otra cuenta."
                }

                val localBackup = if (activeWorkspace == "LOCAL") FullBackupRepository(context, AppDatabase.get(context)).export() else null
                var snapshot = api.request(config.apiBaseUrl, config.neonAuthBaseUrl, "/sync/bootstrap", authOrigin = config.authOrigin).json()
                val remoteIsEmpty = snapshot.optJSONObject("data")?.let(::isEmptyRemoteSnapshot) == true
                val remoteCursor = snapshot.optString("cursor", "0")
                if (remoteCursor == "0" && remoteIsEmpty) {
                    profile = api.request(config.apiBaseUrl, config.neonAuthBaseUrl, "/me", "PATCH",
                        org.json.JSONObject().put("timeZone", ZoneId.systemDefault().id), authOrigin = config.authOrigin).json()
                }
                val accountTimeZone = profile.optString("timeZone").ifBlank { ZoneId.systemDefault().id }
                if (localBackup != null && remoteCursor == "0" && remoteIsEmpty) {
                    val backupRoot = org.json.JSONObject(localBackup)
                    val localData = backupRoot.getJSONObject("data")
                    if (localData.length() > 0 && localData.keys().asSequence().any { (localData.optJSONArray(it)?.length() ?: 0) > 0 }) {
                        val localDeviceId = AppDatabase.get(context).dao().syncState()?.deviceId.orEmpty()
                        require(localDeviceId.isNotBlank()) { "No se pudo identificar este dispositivo para importar sus datos." }
                        val importId = UUID.nameUUIDFromBytes("${config.apiBaseUrl}:${identity.issuer}:${identity.subject}:$localDeviceId:initial-import-v1".toByteArray())
                        try {
                            api.request(config.apiBaseUrl, config.neonAuthBaseUrl, "/sync/import", "POST",
                                org.json.JSONObject().put("importId", importId.toString()).put("formatVersion", 1).put("data", localData),
                                mapOf("Idempotency-Key" to "bn-sync:$localDeviceId:$importId"), config.authOrigin)
                        } catch (error: com.blocknize.app.data.SyncHttpException) {
                            if (error.status != 409) throw error
                        }
                        snapshot = api.request(config.apiBaseUrl, config.neonAuthBaseUrl, "/sync/bootstrap", authOrigin = config.authOrigin).json()
                    }
                }

                LocalDataAccess.exclusive {
                    AppDatabase.selectCloudWorkspace(context, workspaceKey)
                    val cloudDatabase = AppDatabase.get(context)
                    val dao = cloudDatabase.dao()
                    val prior = dao.syncState() ?: error("No se pudo inicializar el espacio de nube.")
                    require(prior.authIssuer == null || (prior.authIssuer == identity.issuer && prior.authSubject == identity.subject && prior.remoteUserId == remoteUserId)) {
                        "Este espacio ya está asociado a otra identidad. Sus datos no se han cambiado."
                    }
                    dao.saveSyncState(prior.copy(mode = "CLOUD", authIssuer = identity.issuer, authSubject = identity.subject,
                        remoteUserId = remoteUserId, accountTimeZone = accountTimeZone,
                        // An empty local cursor forces bootstrap even for older accounts whose data
                        // predates the sync-head migration and therefore still report cursor zero.
                        cursor = if (prior.mode == "CLOUD") prior.cursor else "", updatedAt = System.currentTimeMillis()))
                    repository = TemplateRepository(cloudDatabase, clock)
                    workspaceGeneration.value += 1
                }
                CloudSyncScheduler.request(context)
                CloudSyncScheduler.ensurePeriodic(context)
                refreshCloudSettings()
                _message.value = if (snapshot.optJSONObject("data")?.let(::isEmptyRemoteSnapshot) == true)
                    "Cuenta conectada. La nube está lista para sincronizar." else "Cuenta conectada. Descargando los datos de la nube en segundo plano."
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _message.value = error.message ?: "No se pudo conectar la cuenta. Los datos locales siguen disponibles."
            } finally {
                _isBusy.value = false
                setCloudBusy(false)
                refreshCloudSettings()
            }
        }
    }

    fun useLocalWorkspace() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            LocalDataAccess.exclusive {
                AppDatabase.selectLocalWorkspace(context)
                repository = TemplateRepository(AppDatabase.get(context), clock)
                workspaceGeneration.value += 1
            }
            CloudSyncScheduler.pausePeriodic(context)
            WidgetRefreshWorker.request(context)
            refreshCloudSettings()
            _message.value = "Espacio local activado. La cuenta y sus datos sincronizados se conservan en este dispositivo."
        }
    }

    fun signOutCloud() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            setCloudBusy(true)
            try {
                val config = SyncConfigurationStore(context).configuration.first()
                LocalDataAccess.exclusive {
                    AppDatabase.selectLocalWorkspace(context)
                    repository = TemplateRepository(AppDatabase.get(context), clock)
                    workspaceGeneration.value += 1
                }
                CloudSyncScheduler.pausePeriodic(context)
                NeonAuthClient(SessionVault(context)).signOut(config.neonAuthBaseUrl, config.authOrigin)
                _message.value = "Sesión cerrada. El espacio local permanece disponible."
            } catch (error: Exception) {
                _message.value = error.message ?: "No se pudo cerrar sesión."
            } finally {
                setCloudBusy(false)
                refreshCloudSettings()
            }
        }
    }

    fun requestCloudSync() {
        if (AppDatabase.activeWorkspace(getApplication()).startsWith("CLOUD:")) CloudSyncScheduler.request(getApplication())
    }

    fun syncNow() {
        requestCloudSync()
        viewModelScope.launch { refreshCloudSettings(); _message.value = "Sincronización solicitada. Se ejecutará al haber conexión." }
    }

    fun keepLocalConflict(operationId: String) {
        viewModelScope.launch {
            try {
                LocalDataAccess.exclusive {
                    val dao = AppDatabase.get(getApplication()).dao()
                    val conflict = dao.syncConflict(operationId) ?: return@exclusive
                    val version = runCatching {
                        org.json.JSONObject(conflict.remotePayloadJson.orEmpty()).getJSONObject("details").getInt("currentVersion")
                    }.getOrNull() ?: error("El servidor no proporcionó una versión segura para reintentar esta propuesta.")
                    dao.rebaseSyncOperation(operationId, version)
                    dao.advancePendingSyncVersions(conflict.aggregateKey, version)
                    dao.saveSyncEntityVersion(com.blocknize.app.data.SyncEntityVersionEntity(conflict.aggregateKey, version, System.currentTimeMillis()))
                    dao.resolveSyncConflict(operationId)
                }
                CloudSyncScheduler.request(getApplication())
                refreshCloudSettings()
                _message.value = "Tu propuesta se conservará y se volverá a enviar sobre la versión actual."
            } catch (error: Exception) {
                _message.value = error.message ?: "No se pudo reintentar el cambio local."
            }
        }
    }

    fun useCloudConflict(operationId: String) {
        viewModelScope.launch {
            LocalDataAccess.exclusive {
                val dao = AppDatabase.get(getApplication()).dao()
                val conflict = dao.syncConflict(operationId) ?: return@exclusive
                dao.discardSyncAggregate(conflict.aggregateKey)
                dao.discardConflictsForAggregate(conflict.aggregateKey)
                dao.syncState()?.let { dao.saveSyncState(it.copy(cursor = "", updatedAt = System.currentTimeMillis())) }
            }
            CloudSyncScheduler.request(getApplication())
            refreshCloudSettings()
            _message.value = "Se descartó la cola local de ese elemento. Se descargará la versión de la nube."
        }
    }

    fun onAppResumed() {
        refreshClock()
        if (AppDatabase.activeWorkspace(getApplication()).startsWith("CLOUD:")) CloudSyncScheduler.ensurePeriodic(getApplication())
        requestCloudSync()
        viewModelScope.launch { refreshCloudSettings() }
    }

    private suspend fun refreshCloudSettings() {
        val context = getApplication<Application>()
        val config = runCatching { SyncConfigurationStore(context).configuration.first() }
            .getOrDefault(SyncConfiguration("", "", "http://localhost:3000"))
        val database = AppDatabase.get(context)
        val dao = database.dao()
        val state = dao.syncState()
        _cloudSettings.value = CloudSettingsState(
            workspace = if (state?.mode == "CLOUD") AppDatabase.activeWorkspace(context) else "LOCAL",
            pendingOperations = dao.syncOutboxCount(), conflicts = dao.syncConflictCount(),
            lastSyncAt = state?.lastSyncAt, lastSyncError = state?.lastSyncError,
            apiBaseUrl = config.apiBaseUrl, neonAuthBaseUrl = config.neonAuthBaseUrl, authOrigin = config.authOrigin,
            busy = _cloudSettings.value.busy
        )
    }

    private suspend fun setCloudBusy(busy: Boolean) {
        _cloudSettings.value = _cloudSettings.value.copy(busy = busy)
    }

    private fun isEmptyRemoteSnapshot(data: org.json.JSONObject): Boolean =
        listOf("templates", "tags", "taskDefinitions", "scheduleBlocks", "templateRevisions", "activeSelection",
            "scheduleChangeEvents", "oneOffTasks", "oneOffScheduleEvents", "completions", "occurrenceExceptions", "occurrenceExceptionEvents")
            .all { (data.optJSONArray(it)?.length() ?: 0) == 0 }

    private fun backupRepository() = FullBackupRepository(getApplication(), AppDatabase.get(getApplication()))

    fun exportBackup(uri: Uri) = backupAction {
        val json = backupRepository().export()
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
                it.flush()
            } ?: error("No se pudo abrir el documento de destino.")
        }
        _message.value = "Copia completa exportada. Guarda el archivo en un lugar seguro."
    }

    fun inspectBackup(uri: Uri) = backupAction {
        pendingBackup = null
        _backupSummary.value = null
        val json = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use(FullBackupRepository::read)
                ?: error("No se pudo abrir la copia.")
        }
        val summary = backupRepository().inspect(json)
        pendingBackup = json
        _backupSummary.value = summary
    }

    fun dismissBackupPreview() {
        if (_isBusy.value) return
        pendingBackup = null
        _backupSummary.value = null
    }

    fun restoreBackup() = backupAction {
        val json = pendingBackup ?: error("Selecciona de nuevo la copia.")
        val context = getApplication<Application>()
        if (AppDatabase.activeWorkspace(context).startsWith("CLOUD:")) {
            LocalDataAccess.exclusive {
                AppDatabase.selectLocalWorkspace(context)
                repository = TemplateRepository(AppDatabase.get(context), clock)
                workspaceGeneration.value += 1
            }
            CloudSyncScheduler.pausePeriodic(context)
        }
        backupRepository().restore(json)
        pendingBackup = null
        _backupSummary.value = null
        refreshClock()
        WidgetRefreshWorker.request(getApplication())
        refreshCloudSettings()
        _message.value = "Datos y preferencias restaurados correctamente."
    }

    private fun backupAction(action: suspend () -> Unit) {
        if (_isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (_: com.blocknize.app.data.RestorePreferencesPendingException) {
                WidgetRefreshWorker.request(getApplication())
                _message.value = "La agenda y el historial se restauraron. La apariencia quedó pendiente y se reintentará al reabrir o guardar."
            }
            catch (_: Exception) {
                _message.value = "No se pudo completar la operación. Comprueba que sea una copia completa compatible y que haya espacio disponible."
            }
            finally { _isBusy.value = false }
        }
    }
    fun setCompleted(occurrence: AgendaOccurrence, completed: Boolean) = runAction("No se pudo actualizar el estado.") {
        repository.setCompleted(occurrence, completed)
    }
    fun saveOneOff(id: String?, date: LocalDate, title: String, description: String, tag: String,
                   startMinute: Int, endMinute: Int, onComplete: (Boolean) -> Unit = {}) =
        runAction("No se pudo guardar la tarea temporal.", onComplete) {
            repository.saveOneOff(id, date, title, description, tag, startMinute, endMinute)
        }
    fun deleteOneOff(id: String) = runAction("No se pudo eliminar la tarea temporal.") { repository.deleteOneOff(id) }
    fun setAccent(argb: Int) = runAction("No se pudo guardar el color.") { accentStore.setAccent(argb) }
    fun setWidgetOpacity(percent: Int) = runAction("No se pudo guardar la opacidad.") { accentStore.setWidgetOpacity(percent) }
    fun createTemplate(name: String) = runAction("No se pudo crear la semana.") { repository.createTemplate(name) }
    fun activate(templateId: String) = runAction("No se pudo activar la semana.") { repository.activate(templateId) }
    fun renameTemplate(templateId: String, name: String) = runAction("No se pudo renombrar la semana.") { repository.renameTemplate(templateId, name) }
    fun exportTemplate(templateId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try { onResult(repository.exportTemplateJson(templateId)) }
            catch (error: Exception) { _message.value = error.message ?: "No se pudo exportar la semana."; onResult(null) }
        }
    }
    fun importTemplate(template: TransferTemplate, name: String, onComplete: (Boolean) -> Unit = {}) =
        runAction("No se pudo importar la semana.", onComplete) { repository.importTemplate(template, name) }
    fun saveBlock(templateId: String, blockId: String?, title: String, description: String, tag: String, daysMask: Int, startMinute: Int, endMinute: Int, onComplete: (Boolean) -> Unit = {}) =
        runAction("No se pudo guardar el bloque.", onComplete) { repository.saveBlock(templateId, blockId, title, description, tag, daysMask, startMinute, endMinute) }
    fun deleteBlock(templateId: String, blockId: String) = runAction("No se pudo eliminar el bloque.") { repository.deleteBlock(templateId, blockId) }

    private fun runAction(fallback: String, onComplete: (Boolean) -> Unit = {}, action: suspend () -> Unit) {
        if (_isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            try {
                LocalDataAccess.exclusive {
                    backupRepository().recoverPendingLocked()
                    action()
                }
                WidgetRefreshWorker.request(getApplication())
                if (AppDatabase.activeWorkspace(getApplication()).startsWith("CLOUD:")) CloudSyncScheduler.request(getApplication())
                if (AppDatabase.activeWorkspace(getApplication()).startsWith("CLOUD:")) CloudSyncScheduler.ensurePeriodic(getApplication())
                refreshCloudSettings()
                _message.value = "Guardado correctamente."
                onComplete(true)
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { _message.value = error.message ?: fallback; onComplete(false) }
            finally { _isBusy.value = false }
        }
    }
}
