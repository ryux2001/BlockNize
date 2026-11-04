package com.blocknize.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object CivilTimeChanges {
    private val revision = MutableStateFlow(0L)
    val revisions = revision.asStateFlow()
    fun notifyChanged() { revision.update { it + 1 } }
}
