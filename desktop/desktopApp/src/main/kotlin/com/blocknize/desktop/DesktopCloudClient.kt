package com.blocknize.desktop

import com.blocknize.app.domain.ScheduleBlock
import com.blocknize.app.domain.ScheduleTemplate
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Duration
import java.util.Base64
import java.util.Properties
import java.util.UUID

data class DesktopCloudConfig(val apiBaseUrl: String, val authBaseUrl: String, val authOrigin: String) {
    companion object {
        fun empty() = DesktopCloudConfig("", "", "http://localhost")
    }
}

data class DesktopRemoteWorkspace(
    val email: String?,
    val templates: List<ScheduleTemplate>,
    val activeTemplateId: String?,
    val cursor: String,
    val timeZone: String,
    val templateVersions: Map<String, Int>,
    val blockTaskIds: Map<String, String>,
    val activeSelectionVersion: Int,
    val pendingOperationCount: Int = 0,
    val syncConflicts: List<DesktopSyncConflict> = emptyList(),
    val syncError: String? = null
)

class DesktopCloudSettingsStore(
    private val file: Path = Path.of(System.getProperty("user.home"), ".blocknize", "desktop-cloud.properties")
) {
    fun load(): DesktopCloudConfig {
        if (Files.notExists(file)) return DesktopCloudConfig.empty()
        DesktopFileSecurity.restrict(file)
        val properties = Properties().also { values -> Files.newInputStream(file).use { input -> values.load(input) } }
        return DesktopCloudConfig(
            apiBaseUrl = properties.getProperty("apiBaseUrl").orEmpty(),
            authBaseUrl = properties.getProperty("authBaseUrl").orEmpty(),
            authOrigin = properties.getProperty("authOrigin").orEmpty().ifBlank { "http://localhost" }
        )
    }

    fun save(config: DesktopCloudConfig) {
        val properties = readProperties().apply {
            setProperty("apiBaseUrl", config.apiBaseUrl.trim().trimEnd('/'))
            setProperty("authBaseUrl", config.authBaseUrl.trim().trimEnd('/'))
            setProperty("authOrigin", config.authOrigin.trim().trimEnd('/'))
        }
        writeAtomically(properties)
    }

    @Synchronized
    fun syncDeviceId(): String {
        val properties = readProperties()
        val existing = properties.getProperty("syncDeviceId")
        if (!existing.isNullOrBlank() && runCatching { UUID.fromString(existing) }.isSuccess) return existing
        val deviceId = UUID.randomUUID().toString()
        properties.setProperty("syncDeviceId", deviceId)
        writeAtomically(properties)
        return deviceId
    }

    private fun readProperties(): Properties = Properties().also { values ->
        if (Files.exists(file)) Files.newInputStream(file).use { values.load(it) }
    }

    private fun writeAtomically(properties: Properties) {
        Files.createDirectories(file.parent)
        DesktopFileSecurity.restrict(file.parent)
        val temporaryFile = Files.createTempFile(file.parent, "desktop-cloud-", ".tmp")
        try {
            Files.newOutputStream(temporaryFile).use { properties.store(it, "BlockNize desktop cloud endpoints; no credentials") }
            DesktopFileSecurity.restrict(temporaryFile)
            try {
                Files.move(temporaryFile, file, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryFile, file, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }
}

class DesktopCloudClient {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Volatile private var session: DesktopSession? = null
    private val operationMetadata = LinkedHashMap<String, JSONObject>()
    private var syncStore: DesktopSyncStore? = null
    private var lastSyncError: String? = null

    @Synchronized
    fun connect(config: DesktopCloudConfig, email: String, password: String, deviceId: String): DesktopRemoteWorkspace {
        require(email.isNotBlank() && password.isNotEmpty()) { "Introduce tu correo y contraseña." }
        require(runCatching { UUID.fromString(deviceId) }.isSuccess) { "El identificador de este equipo no es válido." }
        val normalized = config.normalized()
        val response = authRequest(
            normalized,
            "${normalized.authBaseUrl}/sign-in/email",
            "POST",
            body = JSONObject().put("email", email.trim()).put("password", password).put("rememberMe", true).toString()
        )
        requireSuccessful(response, "No se pudo iniciar sesión.")
        val cookies = response.headers().allValues("set-cookie")
            .map { it.substringBefore(';').trim() }
            .filter(String::isNotBlank)
        require(cookies.isNotEmpty()) { "El servicio de acceso no devolvió una sesión." }
        session = DesktopSession(normalized, cookies.joinToString("; "), "", 0L, deviceId, "", "")
        return try {
            refreshSession()
            val identity = requireNotNull(session).identityKey
            syncStore = DesktopSyncStore(DesktopFileSecurity.accountFile(identity))
            syncPendingOperations()
            loadWorkspaceWithSyncState()
        } catch (error: Throwable) {
            session = null
            syncStore = null
            throw error
        }
    }

    @Synchronized
    fun refreshWorkspace(): DesktopRemoteWorkspace {
        requireNotNull(session) { "Conecta una cuenta para actualizar la agenda." }
        syncPendingOperations()
        return loadWorkspaceWithSyncState()
    }

    @Synchronized
    fun signOut() {
        val current = session ?: return
        try {
            val response = authRequest(current.config, "${current.config.authBaseUrl}/sign-out", "POST",
                current.cookieHeader, "{}")
            requireSuccessful(response, "No se pudo cerrar la sesión en el servidor.")
        } finally {
            session = null
            syncStore = null
            operationMetadata.clear()
        }
    }

    @Synchronized
    fun createTemplate(workspace: DesktopRemoteWorkspace, name: String, templateId: String, operationId: String): DesktopRemoteWorkspace {
        require(name.isNotBlank()) { "El nombre de la semana no puede estar vacío." }
        require(runCatching { UUID.fromString(templateId) }.isSuccess) { "El identificador de la semana no es válido." }
        require(runCatching { UUID.fromString(operationId) }.isSuccess) { "El identificador de la operación no es válido." }
        if (templateId in workspace.templateVersions) {
            val existing = workspace.templates.firstOrNull { it.id == templateId }
                ?: error("Esta semana ya no está disponible en la cuenta. Actualiza la agenda.")
            return if (existing.name == name.trim()) workspace
            else renameTemplate(workspace, templateId, name, operationId)
        }
        val metadata = actionMetadata(operationId, workspace.timeZone)
        val body = JSONObject().put("id", templateId).put("name", name.trim())
            .put("clientActionAt", metadata.getString("clientActionAt"))
            .put("clientTimeZone", metadata.getString("clientTimeZone"))
        return enqueueAndSync(workspace, DesktopPendingMutation(
            operationId = operationId,
            aggregateKey = "template:$templateId",
            type = "template.create",
            path = "/templates",
            method = "POST",
            bodyJson = body.toString(),
            headers = emptyMap(),
            summary = "Crear semana «${name.trim()}»",
            createdAtEpochMillis = System.currentTimeMillis()
        ))
    }

    @Synchronized
    fun renameTemplate(workspace: DesktopRemoteWorkspace, templateId: String, name: String, operationId: String): DesktopRemoteWorkspace {
        require(name.isNotBlank()) { "El nombre de la semana no puede estar vacío." }
        val existing = workspace.templates.firstOrNull { it.id == templateId }
            ?: error("La semana ya no está en esta cuenta. Actualiza la agenda.")
        if (existing.name == name.trim()) return workspace
        val version = workspace.templateVersions[templateId]
            ?: error("No se encontró la versión actual de esta semana. Actualiza la cuenta y vuelve a intentar.")
        val body = actionMetadata(operationId, workspace.timeZone)
            .put("name", name.trim())
        return enqueueAndSync(workspace, DesktopPendingMutation(
            operationId = operationId,
            aggregateKey = "template:$templateId",
            type = "template.rename",
            path = "/templates/$templateId",
            method = "PATCH",
            bodyJson = body.toString(),
            headers = mapOf("If-Match" to version.toString()),
            summary = "Renombrar semana a «${name.trim()}»",
            createdAtEpochMillis = System.currentTimeMillis()
        ))
    }

    @Synchronized
    fun saveBlock(
        workspace: DesktopRemoteWorkspace,
        templateId: String,
        block: ScheduleBlock,
        newTaskId: String,
        operationId: String
    ): DesktopRemoteWorkspace {
        require(block.isValid) { "El bloque contiene datos no válidos." }
        val existingBlock = workspace.templates.firstOrNull { it.id == templateId }
            ?.blocks?.firstOrNull { it.id == block.id }
        if (existingBlock == block) return workspace
        val version = workspace.templateVersions[templateId]
            ?: error("No se encontró la versión actual de esta semana. Actualiza la cuenta y vuelve a intentar.")
        val taskId = workspace.blockTaskIds[block.id] ?: newTaskId
        require(runCatching { UUID.fromString(taskId) }.isSuccess) { "El identificador de la actividad no es válido." }
        val body = actionMetadata(operationId, workspace.timeZone)
            .put("id", block.id)
            .put("taskId", taskId)
            .put("title", block.title.trim())
            .put("description", block.description.trim())
            .put("tagName", block.tagName?.trim()?.takeIf(String::isNotEmpty) ?: JSONObject.NULL)
            .put("daysMask", block.daysMask)
            .put("startMinute", block.startMinute)
            .put("endMinute", block.endMinute)
        val existing = block.id in workspace.blockTaskIds
        val path = "/templates/$templateId/blocks" + if (existing) "/${block.id}" else ""
        val method = if (existing) "PATCH" else "POST"
        return enqueueAndSync(workspace, DesktopPendingMutation(
            operationId = operationId,
            aggregateKey = "template:$templateId",
            type = "block.save",
            path = path,
            method = method,
            bodyJson = body.toString(),
            headers = mapOf("If-Match" to version.toString()),
            summary = if (existing) "Editar «${block.title}»" else "Añadir «${block.title}»",
            createdAtEpochMillis = System.currentTimeMillis()
        ))
    }

    @Synchronized
    fun deleteBlock(
        workspace: DesktopRemoteWorkspace,
        templateId: String,
        blockId: String,
        operationId: String = UUID.randomUUID().toString()
    ): DesktopRemoteWorkspace {
        if (blockId !in workspace.blockTaskIds) return workspace
        val version = workspace.templateVersions[templateId]
            ?: error("No se encontró la versión actual de esta semana. Actualiza la cuenta y vuelve a intentar.")
        require(blockId in workspace.blockTaskIds) { "El bloque ya no está en esta cuenta. Actualiza la agenda." }
        val metadata = actionMetadata(operationId, workspace.timeZone)
        val requestHeaders = mapOf(
            "If-Match" to version.toString(),
            "X-Client-Revision-Id" to metadata.getString("revisionId"),
            "X-Client-Event-Id" to metadata.getString("eventId"),
            "X-Client-Action-At" to metadata.getString("clientActionAt"),
            "X-Client-Time-Zone" to metadata.getString("clientTimeZone")
        )
        return enqueueAndSync(workspace, DesktopPendingMutation(
            operationId = operationId,
            aggregateKey = "template:$templateId",
            type = "block.delete",
            path = "/templates/$templateId/blocks/$blockId",
            method = "DELETE",
            bodyJson = null,
            headers = requestHeaders,
            summary = "Eliminar bloque «${workspace.templates.flatMap { it.blocks }.firstOrNull { it.id == blockId }?.title.orEmpty()}»",
            createdAtEpochMillis = System.currentTimeMillis()
        ))
    }

    @Synchronized
    fun activateTemplate(
        workspace: DesktopRemoteWorkspace,
        templateId: String,
        operationId: String = UUID.randomUUID().toString()
    ): DesktopRemoteWorkspace {
        require(workspace.templateVersions.containsKey(templateId)) { "La semana ya no está en esta cuenta. Actualiza la agenda." }
        if (workspace.activeTemplateId == templateId) return workspace
        val body = actionMetadata(operationId, workspace.timeZone).put("templateId", templateId)
        return enqueueAndSync(workspace, DesktopPendingMutation(
            operationId = operationId,
            aggregateKey = "selection",
            type = "selection.activate",
            path = "/active-selection",
            method = "PUT",
            bodyJson = body.toString(),
            headers = mapOf("If-Match" to workspace.activeSelectionVersion.toString()),
            summary = "Activar «${workspace.templates.first { it.id == templateId }.name}»",
            createdAtEpochMillis = System.currentTimeMillis()
        ))
    }

    @Synchronized
    fun resolveConflict(
        operationId: String,
        keepLocal: Boolean
    ): DesktopRemoteWorkspace {
        val store = requireNotNull(syncStore) { "Conecta la cuenta para resolver este conflicto." }
        val conflict = store.find(operationId) ?: return loadWorkspaceWithSyncState()
        val latest = loadWorkspace()
        if (!keepLocal) {
            store.discardAggregate(conflict.aggregateKey)
            lastSyncError = null
            return withSyncState(latest)
        }

        val version = if (conflict.aggregateKey == "selection") latest.activeSelectionVersion
        else {
            val templateId = conflict.aggregateKey.removePrefix("template:")
            latest.templateVersions[templateId]
                ?: error("La semana ya no existe en la cuenta. Elige usar la versión de la cuenta para descartar estos cambios.")
        }
        val newOperationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val newHeaders = conflict.headers.toMutableMap().apply {
            if ("If-Match" in this) this["If-Match"] = version.toString()
        }
        var newBody = conflict.bodyJson
        val metadata = JSONObject()
            .put("revisionId", UUID.randomUUID().toString())
            .put("eventId", UUID.randomUUID().toString())
            .put("clientActionAt", java.time.Instant.ofEpochMilli(now).toString())
            .put("clientTimeZone", latest.timeZone)
        if (newBody != null) {
            val body = JSONObject(newBody)
            for (key in listOf("revisionId", "eventId", "clientActionAt", "clientTimeZone")) {
                if (body.has(key)) body.put(key, metadata.getString(key))
            }
            newBody = body.toString()
        }
        if ("X-Client-Revision-Id" in newHeaders) newHeaders["X-Client-Revision-Id"] = metadata.getString("revisionId")
        if ("X-Client-Event-Id" in newHeaders) newHeaders["X-Client-Event-Id"] = metadata.getString("eventId")
        if ("X-Client-Action-At" in newHeaders) newHeaders["X-Client-Action-At"] = metadata.getString("clientActionAt")
        if ("X-Client-Time-Zone" in newHeaders) newHeaders["X-Client-Time-Zone"] = metadata.getString("clientTimeZone")
        store.rebase(operationId, conflict.copy(
            operationId = newOperationId,
            bodyJson = newBody,
            headers = newHeaders,
            createdAtEpochMillis = now,
            conflictMessage = null
        ))
        syncPendingOperations()
        return loadWorkspaceWithSyncState()
    }

    private fun enqueueAndSync(
        workspace: DesktopRemoteWorkspace,
        mutation: DesktopPendingMutation
    ): DesktopRemoteWorkspace {
        val store = requireNotNull(syncStore) { "La cola necesita una cuenta conectada." }
        store.enqueue(mutation)
        syncPendingOperations()
        return runCatching { loadWorkspaceWithSyncState() }.getOrElse { error ->
            lastSyncError = error.message?.take(500) ?: "El cambio quedó guardado en este equipo y se enviará al reconectar."
            withSyncState(applyPendingMutation(workspace, mutation))
        }
    }

    private fun syncPendingOperations() {
        val store = syncStore ?: return
        lastSyncError = null
        val blockedAggregates = mutableSetOf<String>()
        for (queued in store.pending()) {
            val mutation = store.find(queued.operationId) ?: continue
            if (mutation.conflictMessage != null) {
                blockedAggregates += mutation.aggregateKey
                continue
            }
            if (mutation.aggregateKey in blockedAggregates) continue
            try {
                val headers = mutation.headers + ("Idempotency-Key" to idempotencyKey(mutation.operationId))
                val response = apiCall(
                    mutation.path,
                    mutation.method,
                    mutation.bodyJson?.let(::JSONObject),
                    headers
                )
                requireSuccessful(response, "El servidor rechazó ${mutation.summary.lowercase()}.")
                val version = response.headers().firstValue("ETag").orElse(null)?.trim()?.trim('"')?.toIntOrNull()
                    ?: runCatching { JSONObject(response.body()).optInt("version").takeIf { it > 0 } }.getOrNull()
                store.acknowledge(mutation.operationId)
                if (version != null) store.advancePendingVersions(mutation.aggregateKey, version)
            } catch (error: DesktopCloudHttpException) {
                if (error.status == 401 || error.status == 408 || error.status == 429 || error.status >= 500) {
                    lastSyncError = error.message?.take(500) ?: "La conexión no está disponible; los cambios siguen guardados en este equipo."
                    break
                }
                store.markConflict(mutation.operationId, error.message ?: "El servidor rechazó este cambio.")
                blockedAggregates += mutation.aggregateKey
                lastSyncError = error.message ?: "Hay cambios que necesitan resolución."
            } catch (error: Exception) {
                lastSyncError = error.message?.take(500) ?: "La conexión no está disponible; los cambios siguen guardados en este equipo."
                break
            }
        }
        if (store.pending().isEmpty()) lastSyncError = null
        else if (lastSyncError == null) lastSyncError = store.conflicts().firstOrNull()?.message
    }

    private fun loadWorkspaceWithSyncState(): DesktopRemoteWorkspace = withSyncState(loadWorkspace())

    private fun withSyncState(workspace: DesktopRemoteWorkspace): DesktopRemoteWorkspace {
        val store = syncStore ?: return workspace
        val pending = store.pending()
        val optimistic = pending.fold(workspace, ::applyPendingMutation)
        return optimistic.copy(
            pendingOperationCount = pending.size,
            syncConflicts = store.conflicts(),
            syncError = lastSyncError
        )
    }

    private fun applyPendingMutation(
        workspace: DesktopRemoteWorkspace,
        mutation: DesktopPendingMutation
    ): DesktopRemoteWorkspace {
        val body = mutation.bodyJson?.let(::JSONObject)
        return when (mutation.type) {
            "template.create" -> {
                val id = requireNotNull(body).getString("id")
                if (workspace.templates.any { it.id == id }) workspace
                else workspace.copy(
                    templates = workspace.templates + ScheduleTemplate(id, body.getString("name"), emptyList()),
                    templateVersions = workspace.templateVersions + (id to 1)
                )
            }
            "template.rename" -> {
                val id = mutation.path.removePrefix("/templates/")
                val name = requireNotNull(body).getString("name")
                workspace.copy(templates = workspace.templates.map { if (it.id == id) it.copy(name = name) else it })
            }
            "block.save" -> {
                val templateId = mutation.path.split('/').getOrNull(2).orEmpty()
                val values = requireNotNull(body)
                val block = ScheduleBlock(
                    id = values.getString("id"),
                    title = values.getString("title"),
                    description = values.optString("description"),
                    tagName = nullableString(values, "tagName"),
                    daysMask = values.getInt("daysMask"),
                    startMinute = values.getInt("startMinute"),
                    endMinute = values.getInt("endMinute")
                )
                workspace.copy(
                    templates = workspace.templates.map { template ->
                        if (template.id != templateId) template
                        else template.copy(blocks = template.blocks.filterNot { it.id == block.id } + block)
                    },
                    blockTaskIds = workspace.blockTaskIds + (block.id to values.getString("taskId"))
                )
            }
            "block.delete" -> {
                val parts = mutation.path.split('/')
                val templateId = parts.getOrNull(2).orEmpty()
                val blockId = parts.getOrNull(4).orEmpty()
                workspace.copy(
                    templates = workspace.templates.map { template ->
                        if (template.id != templateId) template else template.copy(blocks = template.blocks.filterNot { it.id == blockId })
                    },
                    blockTaskIds = workspace.blockTaskIds - blockId
                )
            }
            "selection.activate" -> workspace.copy(activeTemplateId = requireNotNull(body).getString("templateId"))
            else -> workspace
        }
    }

    private fun loadWorkspace(): DesktopRemoteWorkspace {
        val profile = JSONObject(apiGet("/me"))
        val bootstrap = JSONObject(apiGet("/sync/bootstrap"))
        val data = bootstrap.getJSONObject("data")
        val tags = data.getJSONArray("tags").objects().associate { it.getString("id") to it.getString("name") }
        val tasks = data.getJSONArray("taskDefinitions").objects()
            .filter(::isNotDeleted).associateBy { it.getString("id") }
        val blocks = data.getJSONArray("scheduleBlocks").objects().filter(::isNotDeleted)
        val templateRows = data.getJSONArray("templates").objects().filter(::isNotDeleted)
        val templateVersions = templateRows.associate { it.getString("id") to it.optInt("version", 1) }
        val blockTaskIds = mutableMapOf<String, String>()
        val templates = templateRows.map { template ->
            val templateId = template.getString("id")
            val templateBlocks = blocks.asSequence()
                .filter { it.getString("templateId") == templateId }
                .mapNotNull { block ->
                    val taskId = block.getString("taskId")
                    val task = tasks[taskId] ?: return@mapNotNull null
                    val blockId = block.getString("id")
                    blockTaskIds[blockId] = taskId
                    ScheduleBlock(
                        id = blockId,
                        title = task.getString("title"),
                        description = task.optString("description"),
                        tagName = nullableString(task, "tagId")?.let(tags::get),
                        daysMask = block.getInt("daysMask"),
                        startMinute = block.getInt("startMinute"),
                        endMinute = block.getInt("endMinute")
                    )
                }.toList()
            ScheduleTemplate(templateId, template.getString("name"), templateBlocks)
        }
        require(templates.all(ScheduleTemplate::isValid)) { "La cuenta contiene un bloque con datos no válidos." }
        val activeSelection = data.getJSONArray("activeSelection").objects().firstOrNull()
        val active = activeSelection?.optString("templateId")?.takeIf(String::isNotBlank)
        return DesktopRemoteWorkspace(
            email = profile.optString("email").ifBlank { null },
            templates = templates,
            activeTemplateId = active,
            cursor = bootstrap.optString("cursor"),
            timeZone = bootstrap.optString("timeZone", "UTC"),
            templateVersions = templateVersions,
            blockTaskIds = blockTaskIds,
            activeSelectionVersion = activeSelection?.optInt("version", 0) ?: 0
        )
    }

    private fun apiGet(path: String): String {
        val response = apiCall(path)
        requireSuccessful(response, "No se pudo cargar la agenda de la cuenta.")
        return response.body()
    }

    private fun apiCall(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse<String> {
        val current = requireNotNull(session) { "La sesión no está conectada." }
        var token = validAccessToken()
        var response = apiRequest(current.config, path, token, method, body, headers)
        if (response.statusCode() == 401) {
            token = refreshSession()
            response = apiRequest(current.config, path, token, method, body, headers)
        }
        return response
    }

    private fun apiRequest(
        config: DesktopCloudConfig,
        path: String,
        token: String,
        method: String,
        body: JSONObject?,
        headers: Map<String, String>
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("${config.apiBaseUrl}/${path.trimStart('/')}"))
            .timeout(Duration.ofSeconds(25))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody())
        else builder.header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body.toString()))
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun mutationHeaders(version: Int, operationId: String = UUID.randomUUID().toString()): Map<String, String> = mapOf(
        "If-Match" to version.toString(),
        "Idempotency-Key" to idempotencyKey(operationId)
    )

    private fun idempotencyKey(operationId: String): String {
        val current = requireNotNull(session) { "La sesión no está conectada." }
        require(runCatching { UUID.fromString(operationId) }.isSuccess) { "El identificador de la operación no es válido." }
        return "bn-sync:${current.deviceId}:$operationId"
    }

    private fun actionMetadata(operationId: String, timeZone: String): JSONObject {
        require(runCatching { UUID.fromString(operationId) }.isSuccess) { "El identificador de la operación no es válido." }
        return operationMetadata.getOrPut(operationId) {
            JSONObject()
                .put("revisionId", UUID.randomUUID().toString())
                .put("eventId", UUID.randomUUID().toString())
                .put("clientActionAt", java.time.Instant.now().toString())
                .put("clientTimeZone", timeZone)
        }.also {
            while (operationMetadata.size > MAX_REMEMBERED_OPERATIONS) {
                operationMetadata.remove(operationMetadata.keys.first())
            }
        }
    }

    private fun validAccessToken(): String {
        val current = requireNotNull(session) { "La sesión no está conectada." }
        return if (current.expiresAtEpochSeconds > System.currentTimeMillis() / 1000 + 60) current.accessToken
        else refreshSession()
    }

    private fun refreshSession(): String {
        val current = requireNotNull(session) { "La sesión no está conectada." }
        val response = authRequest(current.config, "${current.config.authBaseUrl}/token", "GET", current.cookieHeader)
        requireSuccessful(response, "La sesión caducó. Vuelve a iniciar sesión.")
        val decoded = JSONTokener(response.body()).nextValue()
        val token = when (decoded) {
            is String -> decoded
            is JSONObject -> decoded.optString("access_token").ifBlank {
                decoded.optString("token").ifBlank {
                    decoded.optJSONObject("session")?.optString("access_token")
                        ?.ifBlank { decoded.optJSONObject("data")?.optString("token").orEmpty() }.orEmpty()
                }
            }
            else -> ""
        }
        val claims = decodeClaims(token)
        val issuer = claims.optString("iss")
        val subject = claims.optString("sub")
        require(issuer.isNotBlank() && subject.isNotBlank()) { "El servicio de acceso devolvió un token sin identidad." }
        if (current.subject.isNotBlank()) {
            require(current.issuer == issuer && current.subject == subject) {
                "La sesión pertenece a otra cuenta. Cierra sesión y vuelve a conectar."
            }
        }
        session = current.copy(
            accessToken = token,
            expiresAtEpochSeconds = claims.optLong("exp", 0L),
            issuer = issuer,
            subject = subject
        )
        return token
    }

    private fun authRequest(
        config: DesktopCloudConfig,
        url: String,
        method: String,
        cookieHeader: String? = null,
        body: String? = null
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("Origin", config.authOrigin)
            .apply { if (!cookieHeader.isNullOrBlank()) header("Cookie", cookieHeader) }
            .apply {
                if (body == null) method(method, HttpRequest.BodyPublishers.noBody())
                else header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body))
            }
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun requireSuccessful(response: HttpResponse<String>, fallback: String) {
        if (response.statusCode() in 200..299) return
        val message = runCatching {
            val decoded = JSONTokener(response.body()).nextValue()
            if (decoded is JSONObject) decoded.optString("message").ifBlank { fallback } else fallback
        }.getOrDefault(fallback)
        throw DesktopCloudHttpException(response.statusCode(), message)
    }

    private fun decodeClaims(token: String): JSONObject {
        val parts = token.split('.')
        require(parts.size == 3) { "El servicio de acceso no devolvió un JWT válido." }
        return JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
    }

    private fun isNotDeleted(row: JSONObject): Boolean = row.opt("deletedAt") in listOf(null, JSONObject.NULL)

    private fun nullableString(row: JSONObject, key: String): String? =
        if (row.isNull(key)) null else row.optString(key).takeIf(String::isNotBlank)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun DesktopCloudConfig.normalized(): DesktopCloudConfig = DesktopCloudConfig(
        apiBaseUrl = normalizeBase(apiBaseUrl, "La dirección de la API"),
        authBaseUrl = normalizeBase(authBaseUrl, "La dirección de Neon Auth"),
        authOrigin = normalizeOrigin(authOrigin)
    )

    private fun normalizeBase(value: String, label: String): String {
        val uri = URI(value.trim().trimEnd('/'))
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_HOSTS)) {
            "$label requiere HTTPS; HTTP solo se permite en localhost."
        }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "$label no es válida."
        }
        return uri.toString().trimEnd('/')
    }

    private fun normalizeOrigin(value: String): String {
        val uri = URI(value.trim().trimEnd('/'))
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_HOSTS)) {
            "El origen de Neon Auth requiere HTTPS; HTTP solo se permite en localhost."
        }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.isNullOrEmpty()) {
            "El origen debe contener solo esquema, host y puerto."
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme}://${uri.host}$port"
    }

    private data class DesktopSession(
        val config: DesktopCloudConfig,
        val cookieHeader: String,
        val accessToken: String,
        val expiresAtEpochSeconds: Long,
        val deviceId: String,
        val issuer: String,
        val subject: String
    ) {
        val identityKey: String get() = "$issuer|$subject"
    }

    private class DesktopCloudHttpException(val status: Int, message: String) : IllegalStateException(message)

    private companion object {
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")
        const val MAX_REMEMBERED_OPERATIONS = 256
    }
}
