package com.blocknize.app.ui.focus

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class FocusViewModelTest {
    @Test fun noTaskIsSelectedByDefault() {
        assertNull(FocusViewModel(SavedStateHandle()).selectedOccurrenceKey.value)
    }

    @Test fun selectionCanBeRestoredWithoutStoringAnyTimer() {
        val saved = SavedStateHandle()
        val original = FocusViewModel(saved)
        original.selectTask("selected-task")
        val snapshot = saved.keys().associateWith { saved.get<Any?>(it) }
        val restored = FocusViewModel(SavedStateHandle(snapshot))
        assertEquals("selected-task", restored.selectedOccurrenceKey.value)
        assertEquals(setOf("focus.selectedOccurrenceKey"), saved.keys())
    }
}
