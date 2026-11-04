package com.blocknize.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun AppNavigationRail(
    destination: AppDestination,
    onSelect: (AppDestination) -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    NavigationRail(modifier = Modifier.fillMaxHeight().testTag("navigation-right"), containerColor = Color.Black,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppDestination.entries.forEach { tab ->
                NavigationRailItem(modifier = Modifier.testTag("destination-${tab.name}"),
                    selected = destination == tab, onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = tab.title) }, alwaysShowLabel = false)
            }
        }
        if (onOpenSettings != null) IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("focus-settings")) {
            Icon(Icons.Filled.Settings, contentDescription = "Configuración")
        }
    }
}
