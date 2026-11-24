package com.blocknize.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Comparator
import java.util.UUID

class DesktopAndroidConvergenceTest {
    @Test
    fun `desktop reads Android block and publishes its edit`() {
        assumeTrue(System.getenv("BLOCKNIZE_DESKTOP_ANDROID_CONVERGENCE") == "1")
        val apiBaseUrl = requireEnv("BLOCKNIZE_API_BASE_URL")
        val authBaseUrl = requireEnv("BLOCKNIZE_AUTH_BASE_URL")
        val authOrigin = requireEnv("BLOCKNIZE_AUTH_ORIGIN")
        val email = requireEnv("BLOCKNIZE_ACCEPTANCE_EMAIL")
        val password = requireEnv("BLOCKNIZE_ACCEPTANCE_PASSWORD")
        val templateName = requireEnv("BLOCKNIZE_ANDROID_TEST_TEMPLATE")
        val blockTitle = requireEnv("BLOCKNIZE_ANDROID_TEST_BLOCK")
        val expectBlockMissing = System.getenv("BLOCKNIZE_EXPECT_TEST_BLOCK_MISSING") == "1"
        val desktopTitle = System.getenv("BLOCKNIZE_DESKTOP_WRITE_TITLE")?.takeIf(String::isNotBlank)
        val priorUserHome = System.getProperty("user.home")
        val testHome = Files.createTempDirectory("blocknize-android-convergence-")
        System.setProperty("user.home", testHome.toString())

        try {
            val client = DesktopCloudClient()
            val config = DesktopCloudConfig(apiBaseUrl, authBaseUrl, authOrigin)
            val workspace = client.connect(config, email, password, UUID.randomUUID().toString())
            try {
                val template = workspace.templates.singleOrNull { it.name == templateName }
                    ?: error("Android no sincronizó la semana de prueba.")
                if (expectBlockMissing) {
                    assertTrue("Android no sincronizó el borrado del bloque de prueba.", template.blocks.none { it.title == blockTitle })
                } else {
                    val block = template.blocks.singleOrNull { it.title == blockTitle }
                        ?: error("Android no sincronizó el bloque de prueba.")
                    assertNotNull(workspace.blockTaskIds[block.id])
                    assertEquals(blockTitle, block.title)
                    if (desktopTitle != null) {
                        val edited = block.copy(title = desktopTitle)
                        val updated = client.saveBlock(
                            workspace,
                            template.id,
                            edited,
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString()
                        )
                        assertEquals(edited, updated.templates.single { it.id == template.id }.blocks.single { it.id == block.id })
                        assertEquals(0, updated.pendingOperationCount)
                        assertTrue(updated.syncConflicts.isEmpty())
                    }
                }
            } finally {
                client.signOut()
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
}
