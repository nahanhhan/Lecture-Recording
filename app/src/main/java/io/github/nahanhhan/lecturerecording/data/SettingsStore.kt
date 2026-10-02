package io.github.nahanhhan.lecturerecording.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CloudSettings(val baseUrl: String, val model: String, val key: String, val strict: Boolean)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var modelId: String
        get() = preferences.getString("asr_model", "aed")!!
        set(value) { preferences.edit().putString("asr_model", value).apply() }
    var glossary: String
        get() = preferences.getString("glossary", "")!!
        set(value) { preferences.edit().putString("glossary", value).apply() }
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("lecture_api", null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder("lecture_api", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }
    fun cloud(): CloudSettings {
        val key = runCatching {
            val encoded = preferences.getString("key_cipher", null) ?: return@runCatching ""
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }.getOrDefault("")
        return CloudSettings(preferences.getString("base_url", "https://api.openai.com/v1")!!,
            preferences.getString("cloud_model", "")!!, key, preferences.getBoolean("strict", true))
    }
    fun saveCloud(settings: CloudSettings) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.iv + cipher.doFinal(settings.key.toByteArray())
        preferences.edit().putString("base_url", settings.baseUrl.trim().trimEnd('/'))
            .putString("cloud_model", settings.model.trim()).putBoolean("strict", settings.strict)
            .putString("key_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP)).putBoolean("cloud_tested", false).apply()
    }
    var cloudTested: Boolean
        get() = preferences.getBoolean("cloud_tested", false)
        set(value) { preferences.edit().putBoolean("cloud_tested", value).apply() }
}
