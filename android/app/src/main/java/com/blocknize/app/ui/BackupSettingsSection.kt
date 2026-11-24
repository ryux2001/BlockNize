package com.blocknize.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blocknize.app.AppViewModel
import java.time.LocalDate

@Composable
fun BackupSettingsSection(model: AppViewModel) {
    val busy by model.isBusy.collectAsStateWithLifecycle()
    val cloudState by model.cloudSettings.collectAsStateWithLifecycle()
    val summary by model.backupSummary.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        if (it != null) model.exportBackup(it)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) model.inspectBackup(it)
    }
    Text("Datos y copias de seguridad", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text("Incluye plantillas, temporales, completados, excepciones, historial y apariencia. No es una sincronización.")
    Spacer(Modifier.height(8.dp))
    Text("El archivo no está cifrado y contiene tu agenda. Guárdalo en un lugar privado. Máximo: 32 MB y 100.000 registros por tabla.",
        style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    Button(enabled = !busy, onClick = { export.launch("BlockNize-copia-${LocalDate.now()}.json") }) {
        Text("Exportar copia completa")
    }
    if (cloudState.workspace.startsWith("CLOUD:")) {
        Spacer(Modifier.height(6.dp))
        Text("La copia exporta este espacio. Si restauras un archivo, se aplicará al espacio local y dejará intactos los datos de nube.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedButton(enabled = !busy, onClick = { restore.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
        Text("Restaurar copia completa")
    }
    if (busy) CircularProgressIndicator()
    summary?.let { copy ->
        AlertDialog(onDismissRequest = { model.dismissBackupPreview() },
            title = { Text("¿Sustituir los datos locales?") },
            text = {
                Column {
                    Text("Copia: ${copy.exportedAt}")
                    Text("${copy.templates} plantillas · ${copy.oneOffTasks} temporales")
                    Text("${copy.completions} completados · ${copy.exceptions} excepciones · ${copy.events} eventos")
                    Spacer(Modifier.height(12.dp))
                    Text("Se sustituirán la agenda, el historial y la apariencia. No se mezclarán con los datos actuales.")
                    Text("Recomendamos exportar primero una copia de tus datos actuales.")
                    TextButton(enabled = !busy, onClick = { export.launch("BlockNize-antes-de-restaurar-${LocalDate.now()}.json") }) {
                        Text("Exportar datos actuales primero")
                    }
                }
            },
            confirmButton = { Button(enabled = !busy, onClick = model::restoreBackup) { Text(if (busy) "Restaurando…" else "Sustituir y restaurar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = model::dismissBackupPreview) { Text("Cancelar") } })
    }
}
