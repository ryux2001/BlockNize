package com.blocknize.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import com.blocknize.app.AppViewModel
import com.blocknize.app.ui.theme.BlockNizeTheme
import org.junit.Rule
import org.junit.Test

class BackupSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun completeBackupIsClearlySeparatedFromTemplateTransferAndWarnsAboutPrivacy() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.setContent {
            BlockNizeTheme(0xFFA78BFA.toInt()) { Column { BackupSettingsSection(model) } }
        }
        compose.onNodeWithText("Datos y copias de seguridad").assertIsDisplayed()
        compose.onNodeWithText("Exportar copia completa").assertIsDisplayed()
        compose.onNodeWithText("Restaurar copia completa").assertIsDisplayed()
        compose.onNodeWithText("El archivo no está cifrado", substring = true).assertIsDisplayed()
    }
}
