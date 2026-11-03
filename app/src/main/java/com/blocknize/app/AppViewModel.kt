package com.blocknize.app

import android.app.Application
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
import com.blocknize.app.widget.WidgetRefreshWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val clock: Clock = Clock.systemDefaultZone()
    private val repository = TemplateRepository(AppDatabase.get(application), clock)
    private val accentStore = AccentStore(application)

    val templates: StateFlow<List<TemplateRow>> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active: StateFlow<ActiveSelectionEntity?> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tags: StateFlow<List<TagEntity>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accent = accentStore.accent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentStore.DEFAULT_ACCENT)
    val widgetOpacity = accentStore.widgetOpacity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentStore.DEFAULT_WIDGET_OPACITY)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _now = MutableStateFlow(clock.instant())
    val now: StateFlow<Instant> = _now.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todayAgenda: StateFlow<List<AgendaOccurrence>> = _now
        .map { instant -> instant.atZone(ZoneId.systemDefault()).toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { date ->
            repository.observeAgenda(date)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                _now.value = Clock.systemDefaultZone().instant()
            }
        }
    }

    fun blocks(templateId: String): Flow<List<BlockRow>> = repository.observeBlocks(templateId)
    suspend fun agendaForDate(date: LocalDate): List<AgendaOccurrence> = repository.getAgenda(date)
    suspend fun calendarAgenda(date: LocalDate): List<AgendaOccurrence> = repository.getCalendarAgenda(date)
    fun observeWeek(startDate: LocalDate): Flow<Map<LocalDate, List<AgendaOccurrence>>> = repository.observeWeek(startDate)
    fun clearMessage() { _message.value = null }
    fun showMessage(text: String) { _message.value = text }
    fun refreshClock() { _now.value = Clock.systemDefaultZone().instant() }
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
        viewModelScope.launch {
            _isBusy.value = true
            try {
                action()
                WidgetRefreshWorker.request(getApplication())
                _message.value = "Guardado correctamente."
                onComplete(true)
            }
            catch (error: Exception) { _message.value = error.message ?: fallback; onComplete(false) }
            finally { _isBusy.value = false }
        }
    }
}
