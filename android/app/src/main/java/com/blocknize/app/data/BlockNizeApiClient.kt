package com.blocknize.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ApiResponse(val status: Int, val body: String, val headers: Map<String, String>) {
    fun json(): JSONObject = if (body.isBlank()) JSONObject() else JSONObject(body)
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
}

class SyncHttpException(val status: Int, val responseBody: String) : Exception(
    runCatching { JSONObject(responseBody).optString("message") }.getOrDefault("").ifBlank { "Error HTTP $status al sincronizar." }
)

class BlockNizeApiClient(private val auth: NeonAuthClient) {
    suspend fun request(
        apiBaseUrl: String,
        authBaseUrl: String,
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
        authOrigin: String = authBaseUrl
    ): ApiResponse = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken(authBaseUrl, authOrigin)
        var response = perform(apiBaseUrl, path, method, body, headers + ("Authorization" to "Bearer $token"))
        if (response.status == 401 && auth.refresh(authBaseUrl, authOrigin) != null) {
            val refreshed = auth.validAccessToken(authBaseUrl, authOrigin)
            response = perform(apiBaseUrl, path, method, body, headers + ("Authorization" to "Bearer $refreshed"))
        }
        if (response.status !in 200..299) throw SyncHttpException(response.status, response.body)
        response
    }

    private fun perform(base: String, path: String, method: String, body: JSONObject?, headers: Map<String, String>): ApiResponse {
        val connection = URL(base.trimEnd('/') + "/" + path.trimStart('/')).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val responseHeaders = connection.headerFields.entries.filter { it.key != null }
                .associate { it.key to it.value.orEmpty().joinToString(", ") }
            return ApiResponse(status, responseBody, responseHeaders)
        } finally {
            connection.disconnect()
        }
    }
}
