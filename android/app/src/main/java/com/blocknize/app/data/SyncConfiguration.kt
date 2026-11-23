package com.blocknize.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncConfigurationDataStore by preferencesDataStore(name = "sync_configuration")

data class SyncConfiguration(val apiBaseUrl: String, val neonAuthBaseUrl: String, val authOrigin: String)

class SyncConfigurationStore(private val context: Context) {
    private val apiBaseUrlKey = stringPreferencesKey("api_base_url")
    private val authBaseUrlKey = stringPreferencesKey("neon_auth_base_url")
    private val authOriginKey = stringPreferencesKey("auth_origin")

    val configuration: Flow<SyncConfiguration> = context.syncConfigurationDataStore.data.map {
        SyncConfiguration(it[apiBaseUrlKey].orEmpty().trimEnd('/'), it[authBaseUrlKey].orEmpty().trimEnd('/'), it[authOriginKey].orEmpty().trimEnd('/'))
    }

    suspend fun save(apiBaseUrl: String, neonAuthBaseUrl: String, authOrigin: String = "http://localhost:3000") {
        val api = normalizeBaseUrl(apiBaseUrl)
        val auth = normalizeBaseUrl(neonAuthBaseUrl)
        val origin = normalizeOrigin(authOrigin)
        context.syncConfigurationDataStore.edit {
            it[apiBaseUrlKey] = api
            it[authBaseUrlKey] = auth
            it[authOriginKey] = origin
        }
    }

    private fun normalizeBaseUrl(value: String): String {
        val uri = java.net.URI(value.trim().trimEnd('/'))
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("10.0.2.2", "localhost", "127.0.0.1"))) {
            "Usa HTTPS para servidores remotos. HTTP solo se permite en desarrollo local."
        }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "La dirección del servidor no es válida."
        }
        return uri.toString().trimEnd('/')
    }

    private fun normalizeOrigin(value: String): String {
        val uri = java.net.URI(value.trim().trimEnd('/'))
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("10.0.2.2", "localhost", "127.0.0.1"))) {
            "Usa un origen HTTPS remoto o HTTP local de desarrollo."
        }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.isNullOrEmpty()) {
            "El origen debe ser solo esquema, host y puerto, sin ruta."
        }
        return uri.toString().trimEnd('/')
    }
}
