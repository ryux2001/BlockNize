package com.blocknize.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

data class NeonSession(val cookieHeader: String, val accessToken: String, val expiresAtEpochSeconds: Long)
data class NeonIdentity(val issuer: String, val subject: String, val email: String?)

class NeonAuthClient(private val sessionVault: SessionVault) {
    suspend fun signIn(baseUrl: String, email: String, password: String, originUrl: String = baseUrl): NeonIdentity = withContext(Dispatchers.IO) {
        val response = request("${baseUrl.trimEnd('/')}/sign-in/email", "POST", null,
            JSONObject().put("email", email.trim()).put("password", password).put("rememberMe", true), originUrl)
        require(response.status in 200..299) { authError(response.body, "No se pudo iniciar sesión.") }
        val cookie = response.cookies.joinToString("; ")
        require(cookie.isNotBlank()) { "Neon Auth no devolvió una sesión. Comprueba la configuración del origen permitido." }
        saveFreshSession(baseUrl, cookie, originUrl)
    }

    suspend fun signUp(baseUrl: String, name: String, email: String, password: String, originUrl: String = baseUrl): NeonIdentity = withContext(Dispatchers.IO) {
        val response = request("${baseUrl.trimEnd('/')}/sign-up/email", "POST", null,
            JSONObject().put("name", name.trim()).put("email", email.trim()).put("password", password), originUrl)
        require(response.status in 200..299) { authError(response.body, "No se pudo crear la cuenta.") }
        val cookie = response.cookies.joinToString("; ")
        require(cookie.isNotBlank()) { "Neon Auth no devolvió una sesión." }
        saveFreshSession(baseUrl, cookie, originUrl)
    }

    suspend fun refresh(baseUrl: String, originUrl: String = baseUrl): NeonIdentity? = withContext(Dispatchers.IO) {
        val session = sessionVault.read() ?: return@withContext null
        val identity = runCatching { saveFreshSession(baseUrl, session.cookieHeader, originUrl) }.getOrNull()
        identity
    }

    suspend fun signOut(baseUrl: String, originUrl: String = baseUrl) = withContext(Dispatchers.IO) {
        val session = sessionVault.read()
        try {
            if (session != null) request("${baseUrl.trimEnd('/')}/sign-out", "POST", session.cookieHeader, JSONObject(), originUrl)
        } finally {
            sessionVault.clear()
        }
    }

    suspend fun validAccessToken(baseUrl: String, originUrl: String = baseUrl): String = withContext(Dispatchers.IO) {
        val session = sessionVault.read() ?: error("Inicia sesión para sincronizar.")
        if (session.expiresAtEpochSeconds > System.currentTimeMillis() / 1000 + 60) return@withContext session.accessToken
        saveFreshSession(baseUrl, session.cookieHeader, originUrl)
        sessionVault.read()?.accessToken ?: error("La sesión de Neon Auth caducó. Inicia sesión de nuevo.")
    }

    suspend fun identity(baseUrl: String, originUrl: String = baseUrl): NeonIdentity? = withContext(Dispatchers.IO) {
        if (sessionVault.read() == null) return@withContext null
        val token = validAccessToken(baseUrl, originUrl)
        val parts = token.split('.')
        if (parts.size != 3) return@withContext null
        val claims = JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
        NeonIdentity(claims.optString("iss"), claims.optString("sub"), claims.optString("email").ifBlank { null })
    }

    fun clearSession() = sessionVault.clear()

    private fun saveFreshSession(baseUrl: String, cookie: String, originUrl: String): NeonIdentity {
        val response = request("${baseUrl.trimEnd('/')}/token", "GET", cookie, null, originUrl)
        require(response.status in 200..299) { authError(response.body, "No se pudo renovar la sesión.") }
        val decoded = JSONTokener(response.body).nextValue()
        val payload = decoded as? JSONObject
        val token = when (decoded) {
            is String -> decoded
            is JSONObject -> payload!!.optString("access_token").ifBlank {
                payload.optString("token").ifBlank { payload.optJSONObject("session")?.optString("access_token").orEmpty() }
            }.ifBlank { payload.optJSONObject("data")?.optString("token").orEmpty() }
            else -> ""
        }
        require(token.split('.').size == 3) { "Neon Auth no devolvió un token JWT válido para la API." }
        val claims = JSONObject(String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8))
        val issuer = claims.optString("iss")
        val subject = claims.optString("sub")
        require(issuer.isNotBlank() && subject.isNotBlank()) { "El token no contiene identidad de Neon Auth." }
        val expiresAt = claims.optLong("exp", 0)
        sessionVault.save(NeonSession(cookie, token, expiresAt))
        return NeonIdentity(issuer, subject, claims.optString("email").ifBlank { null })
    }

    private fun request(url: String, method: String, cookie: String?, body: JSONObject?, originUrl: String = url): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Origin", URL(originUrl).let { "${it.protocol}://${it.host}${if (it.port == -1) "" else ":${it.port}"}" })
            if (!cookie.isNullOrBlank()) connection.setRequestProperty("Cookie", cookie)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val cookies = connection.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }
                .flatMap { it.value.orEmpty() }.map { it.substringBefore(';') }.filter(String::isNotBlank)
            return HttpResponse(status, responseBody, cookies)
        } finally {
            connection.disconnect()
        }
    }

    private fun authError(body: String, fallback: String): String = runCatching {
        JSONObject(body).optString("message").ifBlank { fallback }
    }.getOrDefault(fallback)

    private data class HttpResponse(val status: Int, val body: String, val cookies: List<String>)
}
