package com.blocknize.desktop

import com.blocknize.app.domain.ScheduleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.UUID

class DesktopCloudAcceptanceTest {
    @Test
    fun `desktop login writes syncs and resolves a real concurrent edit`() {
        assumeTrue(System.getenv("BLOCKNIZE_DESKTOP_CLOUD_ACCEPTANCE") == "1")
        val apiBaseUrl = requireEnv("BLOCKNIZE_API_BASE_URL")
        val authBaseUrl = requireEnv("BLOCKNIZE_AUTH_BASE_URL")
        val authOrigin = requireEnv("BLOCKNIZE_AUTH_ORIGIN")
        val email = requireEnv("BLOCKNIZE_ACCEPTANCE_EMAIL")
        val password = requireEnv("BLOCKNIZE_ACCEPTANCE_PASSWORD")
        val priorUserHome = System.getProperty("user.home")
        val testHome = Files.createTempDirectory("blocknize-cloud-acceptance-")

        try {
            System.setProperty("user.home", testHome.toString())
            val config = DesktopCloudConfig(apiBaseUrl, authBaseUrl, authOrigin)
            val deviceId = UUID.randomUUID().toString()
            val desktop = DesktopCloudClient()
            val secondDevice = DesktopCloudClient()
            var signedIn = false
            try {
                var workspace = desktop.connect(config, email, password, deviceId)
                signedIn = true
                assertNotNull(workspace.email)
                assertTrue(workspace.templates.isEmpty())

                val templateId = UUID.randomUUID().toString()
                workspace = desktop.createTemplate(workspace, "Aceptación desktop", templateId, UUID.randomUUID().toString())
                assertEquals("Aceptación desktop", workspace.templates.single().name)
                assertEquals(0, workspace.pendingOperationCount)

                var otherDeviceWorkspace = secondDevice.connect(config, email, password, UUID.randomUUID().toString())
                otherDeviceWorkspace = secondDevice.renameTemplate(
                    otherDeviceWorkspace,
                    templateId,
                    "Cambio desde otro dispositivo",
                    UUID.randomUUID().toString()
                )
                workspace = desktop.renameTemplate(
                    workspace,
                    templateId,
                    "Cambio local en conflicto",
                    UUID.randomUUID().toString()
                )
                assertEquals(1, workspace.pendingOperationCount)
                assertEquals(1, workspace.syncConflicts.size)

                workspace = desktop.resolveConflict(workspace.syncConflicts.single().operationId, keepLocal = true)
                assertEquals(0, workspace.pendingOperationCount)
                assertTrue(workspace.syncConflicts.isEmpty())
                otherDeviceWorkspace = secondDevice.refreshWorkspace()
                assertEquals("Cambio local en conflicto", otherDeviceWorkspace.templates.single().name)

                val block = ScheduleBlock(
                    id = UUID.randomUUID().toString(),
                    title = "Bloque de aceptación",
                    description = "Creado por el cliente desktop",
                    tagName = "Pruebas",
                    daysMask = 5,
                    startMinute = 600,
                    endMinute = 660
                )
                workspace = desktop.saveBlock(workspace, templateId, block, UUID.randomUUID().toString(), UUID.randomUUID().toString())
                assertEquals(block, workspace.templates.single().blocks.single())
                assertTrue(workspace.blockTaskIds.containsKey(block.id))
                assertNoPending(workspace)

                val editedBlock = block.copy(title = "Bloque editado desde desktop", startMinute = 660, endMinute = 720)
                workspace = desktop.saveBlock(workspace, templateId, editedBlock, UUID.randomUUID().toString(), UUID.randomUUID().toString())
                assertEquals(editedBlock, workspace.templates.single().blocks.single())
                assertNoPending(workspace)

                workspace = desktop.deleteBlock(workspace, templateId, block.id)
                assertTrue(workspace.templates.single().blocks.isEmpty())
                assertNoPending(workspace)

                workspace = desktop.activateTemplate(workspace, templateId)
                assertEquals(templateId, workspace.activeTemplateId)
                assertNoPending(workspace)

                desktop.signOut()
                signedIn = false
                secondDevice.signOut()
            } finally {
                if (signedIn) runCatching { desktop.signOut() }
                runCatching { secondDevice.signOut() }
            }
        } finally {
            System.setProperty("user.home", priorUserHome)
            Files.walk(testHome).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun requireEnv(name: String): String = System.getenv(name)?.takeIf(String::isNotBlank)
        ?: error("Falta la variable de aceptación $name.")

    private fun assertNoPending(workspace: DesktopRemoteWorkspace) {
        assertEquals(
            "Quedaron cambios pendientes: ${workspace.syncError}; conflictos=${workspace.syncConflicts}",
            0,
            workspace.pendingOperationCount
        )
    }
}
