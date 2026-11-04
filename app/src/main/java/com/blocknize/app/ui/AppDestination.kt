package com.blocknize.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class AppDestination(val title: String, val icon: ImageVector) {
    TODAY("Inicio", Icons.Filled.Home),
    CALENDAR("Calendario", Icons.Filled.CalendarMonth),
    HISTORY("Registro", Icons.Filled.CheckCircle),
    BLOCKS("Bloques", Icons.AutoMirrored.Filled.EventNote),
    FOCUS("Concentración", Icons.Filled.Timer)
}
