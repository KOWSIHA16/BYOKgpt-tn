package com.example.byokgpt.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.byokgpt.model.AiProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "byokgpt_secure_keys"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "BYOKGPT_MASTER_KEY"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    init {
        ensureKeyExists()
    }

    private var isJvmFallback = false
    private var jvmFallbackKey: SecretKey? = null

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // AndroidKeyStore is unavailable in local JVM/unit-test environments.
            // Fall back to in-memory AES-256 key for testing.
            isJvmFallback = true
            if (jvmFallbackKey == null) {
                val kg = KeyGenerator.getInstance("AES")
                kg.init(256)
                jvmFallbackKey = kg.generateKey()
            }
        }
    }

    private fun getSecretKey(): SecretKey {
        if (isJvmFallback && jvmFallbackKey != null) {
            return jvmFallbackKey!!
        }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey ?: jvmFallbackKey ?: throw IllegalStateException("Keystore entry missing")
        } catch (_: Exception) {
            jvmFallbackKey ?: throw IllegalStateException("Keystore entry missing")
        }
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Combine IV and CipherText
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return ""

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

            val cipherTextSize = combined.size - GCM_IV_LENGTH
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherTextSize)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun saveApiKey(provider: AiProvider, apiKey: String) {
        val encrypted = encrypt(apiKey.trim())
        prefs.edit().putString("key_${provider.name}", encrypted).apply()
    }

    fun getApiKey(provider: AiProvider): String {
        val encrypted = prefs.getString("key_${provider.name}", null)
        if (encrypted != null) {
            val decrypted = decrypt(encrypted)
            if (decrypted.isNotEmpty()) return decrypted
        }
        if (provider == AiProvider.GEMINI) {
            try {
                val field = com.example.BuildConfig::class.java.getField("GEMINI_API_KEY")
                val key = field.get(null) as? String ?: ""
                if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") {
                    return key.trim()
                }
            } catch (_: Exception) {}
            try {
                val field = com.example.BuildConfig::class.java.getField("API_KEY")
                val key = field.get(null) as? String ?: ""
                if (key.isNotBlank() && key != "MY_API_KEY") {
                    return key.trim()
                }
            } catch (_: Exception) {}
        }
        if (provider == AiProvider.OPENAI) {
            try {
                val field = com.example.BuildConfig::class.java.getField("OPENAI_API_KEY")
                val key = field.get(null) as? String ?: ""
                if (key.isNotBlank() && key != "MY_OPENAI_API_KEY") {
                    return key.trim()
                }
            } catch (_: Exception) {}
        }
        if (provider == AiProvider.CLAUDE) {
            try {
                val field = com.example.BuildConfig::class.java.getField("ANTHROPIC_API_KEY")
                val key = field.get(null) as? String ?: ""
                if (key.isNotBlank() && key != "MY_ANTHROPIC_API_KEY") {
                    return key.trim()
                }
            } catch (_: Exception) {}
        }
        return ""
    }

    fun hasApiKey(provider: AiProvider): Boolean {
        return getApiKey(provider).isNotEmpty()
    }

    fun removeApiKey(provider: AiProvider) {
        prefs.edit()
            .remove("key_${provider.name}")
            .remove("status_${provider.name}")
            .remove("status_time_${provider.name}")
            .remove("status_msg_${provider.name}")
            .apply()
    }

    fun saveProviderStatus(
        provider: AiProvider,
        status: com.example.byokgpt.model.ProviderConnectionStatus,
        message: String = ""
    ) {
        prefs.edit()
            .putString("status_${provider.name}", status.name)
            .putLong("status_time_${provider.name}", System.currentTimeMillis())
            .putString("status_msg_${provider.name}", message)
            .apply()
    }

    fun getProviderStatus(provider: AiProvider): com.example.byokgpt.model.ProviderStatusInfo {
        if (!hasApiKey(provider)) {
            return com.example.byokgpt.model.ProviderStatusInfo(
                status = com.example.byokgpt.model.ProviderConnectionStatus.NOT_CONFIGURED,
                message = "No API key configured."
            )
        }
        val statusName = prefs.getString("status_${provider.name}", null)
        val status = if (statusName != null) {
            try {
                com.example.byokgpt.model.ProviderConnectionStatus.valueOf(statusName)
            } catch (_: Exception) {
                com.example.byokgpt.model.ProviderConnectionStatus.UNKNOWN
            }
        } else {
            com.example.byokgpt.model.ProviderConnectionStatus.UNKNOWN
        }
        val time = prefs.getLong("status_time_${provider.name}", 0L)
        val msg = prefs.getString("status_msg_${provider.name}", "") ?: ""
        return com.example.byokgpt.model.ProviderStatusInfo(status, time, msg)
    }

    fun saveCustomEndpoint(provider: AiProvider, endpoint: String) {
        prefs.edit().putString("endpoint_${provider.name}", endpoint.trim()).apply()
    }

    fun getCustomEndpoint(provider: AiProvider): String {
        return prefs.getString("endpoint_${provider.name}", "") ?: ""
    }

    fun saveSelectedModel(provider: AiProvider, modelId: String) {
        prefs.edit().putString("model_${provider.name}", modelId).apply()
    }

    fun getSelectedModel(provider: AiProvider): String {
        return prefs.getString("model_${provider.name}", provider.defaultModel) ?: provider.defaultModel
    }

    fun saveCurrentProvider(provider: AiProvider) {
        prefs.edit().putString("current_provider", provider.name).apply()
    }

    fun getCurrentProvider(): AiProvider {
        val name = prefs.getString("current_provider", AiProvider.GEMINI.name) ?: AiProvider.GEMINI.name
        return AiProvider.fromString(name)
    }
}
