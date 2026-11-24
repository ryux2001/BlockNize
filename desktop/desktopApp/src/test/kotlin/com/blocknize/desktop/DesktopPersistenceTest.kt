package com.blocknize.desktop

import com.blocknize.app.domain.ScheduleBlock
import com.blocknize.app.domain.ScheduleTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class DesktopPersistenceTest {
    @Test
    fun `outbox survives reopening and keeps idempotent operation`() = withTempDirectory { directory ->
        val file = directory.resolve("sync.json")
        val store = DesktopSyncStore(file)
        val mutation = mutation()

        store.enqueue(mutation)
        store.enqueue(mutation)

        assertEquals(listOf(mutation), DesktopSyncStore(file).pending())
        assertEquals("4", DesktopSyncStore(file).pending().single().headers["If-Match"])
    }

    @Test
    fun `conflicts persist and aggregate versions advance for later operations`() = withTempDirectory { directory ->
        val file = directory.resolve("sync.json")
        val store = DesktopSyncStore(file)
        val first = mutation(createdAt = 1)
        val second = mutation(createdAt = 2)
        store.enqueue(first)
        store.enqueue(second)

        store.markConflict(first.operationId, "La semana cambió en otro dispositivo.")
        store.advancePendingVersions(first.aggregateKey, 9)

        val reopened = DesktopSyncStore(file)
        assertEquals("La semana cambió en otro dispositivo.", reopened.find(first.operationId)?.conflictMessage)
        assertEquals("9", reopened.find(second.operationId)?.headers?.get("If-Match"))
        assertEquals(1, reopened.conflicts().size)

        reopened.acknowledge(second.operationId)
        assertEquals(listOf(first.operationId), reopened.pending().map(DesktopPendingMutation::operationId))
    }

    @Test
    fun `local schedule survives reopening and its file is restricted`() = withTempDirectory { directory ->
        val file = directory.resolve("schedule.properties")
        val store = DesktopScheduleStore(file)
        val block = ScheduleBlock("block-1", "Estudiar", "Álgebra", "Estudio", 3, 540, 600)
        val template = ScheduleTemplate("week-1", "Universidad", listOf(block))
        val schedule = DesktopScheduleData(listOf(template), template.id, template.id)

        store.save(schedule)

        assertEquals(schedule, DesktopScheduleStore(file).load())
        assertTrue(Files.exists(file))
        val posix = runCatching { Files.getPosixFilePermissions(file) }.getOrNull()
        if (posix != null) {
            assertTrue(posix.all { it.name.startsWith("OWNER_") })
            assertFalse(posix.any { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") })
        }
    }

    private fun mutation(createdAt: Long = 1): DesktopPendingMutation = DesktopPendingMutation(
        operationId = UUID.randomUUID().toString(),
        aggregateKey = "template:week-1",
        type = "template.rename",
        path = "/templates/week-1",
        method = "PATCH",
        bodyJson = "{\"name\":\"Universidad\"}",
        headers = mapOf("If-Match" to "4"),
        summary = "Renombrar semana",
        createdAtEpochMillis = createdAt
    )

    private fun withTempDirectory(block: (java.nio.file.Path) -> Unit) {
        val directory = Files.createTempDirectory("blocknize-desktop-test-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
