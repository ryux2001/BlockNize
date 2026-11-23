package com.blocknize.desktop

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.UUID

internal data class DesktopPendingMutation(
    val operationId: String,
    val aggregateKey: String,
    val type: String,
    val path: String,
    val method: String,
    val bodyJson: String?,
    val headers: Map<String, String>,
    val summary: String,
    val createdAtEpochMillis: Long,
    val conflictMessage: String? = null
) {
    init {
        require(runCatching { UUID.fromString(operationId) }.isSuccess) { "La cola contiene un ID de operación no válido." }
        require(aggregateKey.isNotBlank() && aggregateKey.length <= 180) { "La cola contiene un agregado no válido." }
        require(type in OPERATION_TYPES) { "La cola contiene un tipo de operación no compatible." }
        require(path.startsWith('/') && path.length <= 500 && '?' !in path && ".." !in path) { "La cola contiene una ruta no válida." }
        require(method in setOf("POST", "PATCH", "PUT", "DELETE")) { "La cola contiene un método no válido." }
        require(summary.isNotBlank() && summary.length <= 180) { "La cola contiene un resumen no válido." }
        require(headers.keys.all { it in ALLOWED_HEADERS }) { "La cola contiene cabeceras no válidas." }
        require(bodyJson == null || bodyJson.length <= MAX_BODY_LENGTH) { "La cola contiene una operación demasiado grande." }
    }

    companion object {
        const val MAX_BODY_LENGTH = 64 * 1024
        val OPERATION_TYPES = setOf("template.create", "template.rename", "block.save", "block.delete", "selection.activate")
        val ALLOWED_HEADERS = setOf("If-Match", "X-Client-Revision-Id", "X-Client-Event-Id", "X-Client-Action-At", "X-Client-Time-Zone")
    }
}

data class DesktopSyncConflict(
    val operationId: String,
    val aggregateKey: String,
    val summary: String,
    val message: String
)

internal class DesktopSyncStore(
    private val file: Path
) {
    @Synchronized
    fun pending(): List<DesktopPendingMutation> = load().sortedBy(DesktopPendingMutation::createdAtEpochMillis)

    @Synchronized
    fun find(operationId: String): DesktopPendingMutation? = load().firstOrNull { it.operationId == operationId }

    @Synchronized
    fun enqueue(mutation: DesktopPendingMutation) {
        val current = load().toMutableList()
        val duplicate = current.firstOrNull { it.operationId == mutation.operationId }
        if (duplicate != null) {
            require(duplicate == mutation) { "Esta clave idempotente ya está asociada a otro cambio." }
            return
        }
        require(current.size < MAX_OPERATIONS) { "La cola de sincronización alcanzó su límite. Sincroniza antes de editar más." }
        current += mutation
        save(current)
    }

    @Synchronized
    fun acknowledge(operationId: String) {
        save(load().filterNot { it.operationId == operationId })
    }

    @Synchronized
    fun markConflict(operationId: String, message: String) {
        val current = load()
        val updated = current.map { mutation ->
            if (mutation.operationId == operationId) mutation.copy(conflictMessage = message.take(MAX_ERROR_LENGTH)) else mutation
        }
        save(updated)
    }

    @Synchronized
    fun clearConflict(operationId: String) {
        val current = load()
        save(current.map { mutation ->
            if (mutation.operationId == operationId) mutation.copy(conflictMessage = null) else mutation
        })
    }

    @Synchronized
    fun rebase(operationId: String, replacement: DesktopPendingMutation) {
        val current = load().filterNot { it.operationId == operationId }.toMutableList()
        require(current.none { it.operationId == replacement.operationId }) { "El ID de operación ya existe en la cola." }
        current += replacement
        save(current.sortedBy(DesktopPendingMutation::createdAtEpochMillis))
    }

    @Synchronized
    fun discardAggregate(aggregateKey: String): Int {
        val current = load()
        val remaining = current.filterNot { it.aggregateKey == aggregateKey }
        save(remaining)
        return current.size - remaining.size
    }

    @Synchronized
    fun advancePendingVersions(aggregateKey: String, version: Int) {
        val current = load()
        val updated = current.map { mutation ->
            if (mutation.aggregateKey != aggregateKey || "If-Match" !in mutation.headers) mutation
            else mutation.copy(headers = mutation.headers + ("If-Match" to version.toString()))
        }
        if (updated != current) save(updated)
    }

    @Synchronized
    fun conflicts(): List<DesktopSyncConflict> = pending().mapNotNull { mutation ->
        mutation.conflictMessage?.let { DesktopSyncConflict(mutation.operationId, mutation.aggregateKey, mutation.summary, it) }
    }

    private fun load(): List<DesktopPendingMutation> {
        if (Files.notExists(file)) return emptyList()
        DesktopFileSecurity.restrict(file)
        require(Files.size(file) <= MAX_FILE_BYTES) { "La cola de sincronización supera el límite local." }
        val root = JSONObject(Files.readString(file))
        require(root.optInt("format") == FORMAT_VERSION) { "El formato de la cola local no es compatible." }
        val operations = root.optJSONArray("operations") ?: JSONArray()
        require(operations.length() <= MAX_OPERATIONS) { "La cola local contiene demasiadas operaciones." }
        return (0 until operations.length()).map { index ->
            val row = operations.getJSONObject(index)
            val headers = row.optJSONObject("headers") ?: JSONObject()
            DesktopPendingMutation(
                operationId = row.getString("operationId"),
                aggregateKey = row.getString("aggregateKey"),
                type = row.getString("type"),
                path = row.getString("path"),
                method = row.getString("method"),
                bodyJson = row.optString("bodyJson").takeUnless { row.isNull("bodyJson") || it == "" },
                headers = headers.keys().asSequence().associateWith(headers::getString),
                summary = row.getString("summary"),
                createdAtEpochMillis = row.getLong("createdAt"),
                conflictMessage = row.optString("conflictMessage").takeIf(String::isNotBlank)
            )
        }
    }

    private fun save(operations: List<DesktopPendingMutation>) {
        Files.createDirectories(file.parent)
        DesktopFileSecurity.restrict(file.parent)
        val root = JSONObject().put("format", FORMAT_VERSION).put("operations", JSONArray().apply {
            operations.forEach { mutation ->
                put(JSONObject()
                    .put("operationId", mutation.operationId)
                    .put("aggregateKey", mutation.aggregateKey)
                    .put("type", mutation.type)
                    .put("path", mutation.path)
                    .put("method", mutation.method)
                    .put("bodyJson", mutation.bodyJson ?: JSONObject.NULL)
                    .put("headers", JSONObject(mutation.headers))
                    .put("summary", mutation.summary)
                    .put("createdAt", mutation.createdAtEpochMillis)
                    .put("conflictMessage", mutation.conflictMessage ?: JSONObject.NULL))
            }
        })
        require(root.toString().toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
            "La cola de sincronización supera el límite local. Sincroniza antes de editar más."
        }
        val serialized = root.toString()
        val temporaryFile = Files.createTempFile(file.parent, "desktop-sync-", ".tmp")
        try {
            Files.writeString(temporaryFile, serialized)
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

    private companion object {
        const val FORMAT_VERSION = 1
        const val MAX_OPERATIONS = 5_000
        const val MAX_FILE_BYTES = 32L * 1024 * 1024
        const val MAX_ERROR_LENGTH = 500
    }
}

internal object DesktopFileSecurity {
    fun restrict(path: Path) {
        val acl = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView::class.java)
        if (acl != null) {
            val owner = Files.getOwner(path)
            val ownerOnly = java.nio.file.attribute.AclEntry.newBuilder()
                .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission::class.java))
                .build()
            acl.acl = listOf(ownerOnly)
            return
        }
        val posix = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView::class.java)
        if (posix != null) {
            val ownerOnly = if (Files.isDirectory(path)) "rwx------" else "rw-------"
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString(ownerOnly))
            return
        }
        error("No se pueden restringir los permisos del almacenamiento local.")
    }

    fun accountFile(identity: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        val accountKey = digest.joinToString("") { byte -> "%02x".format(byte) }
        return Path.of(System.getProperty("user.home"), ".blocknize", "sync", "$accountKey.json")
    }
}
