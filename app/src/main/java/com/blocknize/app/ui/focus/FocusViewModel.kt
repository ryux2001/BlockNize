package com.blocknize.app.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** Only the task selection needs saving: the clock is always derived from the scheduled end. */
class FocusViewModel(private val saved: SavedStateHandle) : ViewModel() {
    val selectedOccurrenceKey = saved.getStateFlow<String?>("focus.selectedOccurrenceKey", null)

    fun selectTask(key: String) {
        saved["focus.selectedOccurrenceKey"] = key
    }
}
