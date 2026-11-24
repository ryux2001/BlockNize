package com.blocknize.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blocknize.app.AppViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun CloudSettingsSection(model: AppViewModel) {
    val state by model.cloudSettings.collectAsStateWithLifecycle()
    val conflicts by model.syncConflicts.collectAsStateWithLifecycle()
    val inCloud = state.workspace.startsWith("CLOUD:")
    var apiUrl by rememberSaveable(state.apiBaseUrl) { mutableStateOf(state.apiBaseUrl) }
    var authUrl by rememberSaveable(state.neonAuthBaseUrl) { mutableStateOf(state.neonAuthBaseUrl) }
    var origin by rememberSaveable(state.authOrigin) { mutableStateOf(state.authOrigin) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var showConnectConfirmation by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text("Modo de uso", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("BlockNize funciona sin cuenta. Si conectas una cuenta, la agenda se guarda en este dispositivo y se sincroniza con los demás cuando hay conexión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        if (inCloud) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Espacio de nube activo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Puedes seguir trabajando sin conexión. Los cambios pendientes se enviarán al recuperar la red.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    val lastSync = state.lastSyncAt?.let {
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                    } ?: "Todavía no se ha sincronizado"
                    Text("Última sincronización correcta: $lastSync", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    state.lastSyncError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Operaciones pendientes: ${state.pendingOperations}")
                    if (state.conflicts > 0) Text("Cambios por revisar: ${state.conflicts}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = model::syncNow, enabled = !state.busy) { Text("Sincronizar") }
                        OutlinedButton(onClick = model::useLocalWorkspace, enabled = !state.busy) { Text("Usar espacio local") }
                    }
                    TextButton(onClick = model::signOutCloud, enabled = !state.busy) { Text("Cerrar sesión y volver a local") }
                }
            }
            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Cambios que requieren revisión", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                conflicts.forEach { conflict ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(conflict.operationType, style = MaterialTheme.typography.labelLarge)
                        Text(conflict.reason, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { model.keepLocalConflict(conflict.operationId) }) { Text("Conservar mi cambio") }
                            TextButton(onClick = { model.useCloudConflict(conflict.operationId) }) { Text("Usar versión de nube") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        } else {
            Text("Conectar una cuenta", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("La dirección de la API, la autenticación y el origen deben coincidir con la configuración del servidor. Si la cuenta está vacía, puedes importar en ella la agenda local; si ya tiene datos, se abrirá en un espacio separado y no se mezclarán.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(apiUrl, { apiUrl = it }, label = { Text("API de BlockNize") }, placeholder = { Text("https://servidor/api/v1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(authUrl, { authUrl = it }, label = { Text("Neon Auth") }, placeholder = { Text("https://.../auth") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(origin, { origin = it }, label = { Text("Origen permitido por el servidor") }, placeholder = { Text("https://app.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (createAccount) OutlinedButton(onClick = { createAccount = false }) { Text("Ya tengo cuenta") }
                else OutlinedButton(onClick = { createAccount = true }) { Text("Crear cuenta") }
                Text(if (createAccount) "Registro nuevo" else "Iniciar sesión", modifier = Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (createAccount) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Button(onClick = { showConnectConfirmation = true },
                enabled = !state.busy && apiUrl.isNotBlank() && authUrl.isNotBlank() && origin.isNotBlank() && email.isNotBlank() && password.isNotEmpty() && (!createAccount || name.isNotBlank())) {
                Text(if (state.busy) "Conectando…" else if (createAccount) "Continuar con la cuenta" else "Continuar con el inicio de sesión")
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))
    }

    if (showConnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showConnectConfirmation = false },
            title = { Text("Vincular BlockNize con una cuenta") },
            text = { Text("Si la cuenta está vacía, se importarán la agenda y el historial de este dispositivo. Si la cuenta ya tiene datos, se descargarán en un espacio aparte y la agenda local se conservará sin fusionarla. Puedes cancelar ahora; iniciar sesión por sí solo no cambiará ni borrará el espacio local.") },
            confirmButton = {
                Button(onClick = {
                    showConnectConfirmation = false
                    model.connectCloud(apiUrl, authUrl, origin, email, password, createAccount, name)
                    password = ""
                }) { Text("Conectar y continuar") }
            },
            dismissButton = {
                TextButton(onClick = { showConnectConfirmation = false; password = "" }) { Text("Cancelar") }
            }
        )
    }
}
