package com.blocknize.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the Neon session cookie and short-lived API token encrypted with an Android Keystore key. */
class SessionVault(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_session_secure", Context.MODE_PRIVATE)

    @Synchronized
    fun save(session: NeonSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val plain = JSONObject().put("cookie", session.cookieHeader).put("accessToken", session.accessToken)
            .put("expiresAt", session.expiresAtEpochSeconds).toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(plain)
        check(preferences.edit().putString("nonce", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "No se pudo guardar la sesión segura."
        }
    }

    @Synchronized
    fun read(): NeonSession? {
        val nonce = preferences.getString("nonce", null) ?: return null
        val payload = preferences.getString("payload", null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(nonce, Base64.NO_WRAP)))
            val json = JSONObject(String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)), Charsets.UTF_8))
            NeonSession(json.getString("cookie"), json.getString("accessToken"), json.getLong("expiresAt"))
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "blocknize.cloud.session.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
