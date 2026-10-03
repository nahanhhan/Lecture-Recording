package io.github.nahanhhan.lecturerecording.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.nahanhhan.lecturerecording.models.DownloadSource
import io.github.nahanhhan.lecture.core.CloudEndpoint
import io.github.nahanhhan.lecture.core.CloudProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CloudSettings(val baseUrl: String, val model: String, val key: String, val strict: Boolean,
    val provider: CloudProvider = CloudProvider.CUSTOM, val includePhotos: Boolean = true) {
    fun normalized() = copy(baseUrl = CloudEndpoint.normalize(baseUrl), model = model.trim(), key = key.trim(),
        strict = strict && CloudProvider.detect(baseUrl) != CloudProvider.DEEPSEEK)
}

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var modelId: String
        get() = preferences.getString("asr_model", "aed")!!
        set(value) { preferences.edit().putString("asr_model", value).apply() }

    /** 模型下载源，键 `download_source`，缺省 GitHub；未知值回落 GITHUB。 */
    var downloadSource: DownloadSource
        get() = DownloadSource.fromStorage(preferences.getString("download_source", null))
        set(value) { preferences.edit().putString("download_source", value.storage).apply() }
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
    fun cloud(provider: CloudProvider? = null): CloudSettings {
        val legacyBase = preferences.getString("base_url", CloudProvider.OPENAI.baseUrl)!!
        val legacyProvider = CloudProvider.detect(legacyBase)
        val selected = provider ?: CloudProvider.fromId(preferences.getString("cloud_provider", null)) ?: legacyProvider
        val prefix = "cloud_${selected.id}_"
        val legacy = !preferences.contains(prefix + "base_url") && selected == legacyProvider
        val key = runCatching {
            val encoded = preferences.getString(if (legacy) "key_cipher" else prefix + "key_cipher", null) ?: return@runCatching ""
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }.getOrDefault("")
        return CloudSettings(
            preferences.getString(if (legacy) "base_url" else prefix + "base_url", selected.baseUrl)!!,
            preferences.getString(if (legacy) "cloud_model" else prefix + "model", "")!!, key,
            if (selected == CloudProvider.DEEPSEEK) false else preferences.getBoolean(if (legacy) "strict" else prefix + "strict", selected.defaultStrict),
            selected, preferences.getBoolean(prefix + "photos", if (legacy) true else selected.defaultPhotos))
    }
    fun saveCloud(settings: CloudSettings, resetTest: Boolean = false) {
        val value = settings.normalized()
        val prefix = "cloud_${value.provider.id}_"
        val unchanged = value == runCatching { cloud(value.provider).normalized() }.getOrNull()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.iv + cipher.doFinal(value.key.toByteArray())
        preferences.edit().putString("cloud_provider", value.provider.id).putString(prefix + "base_url", value.baseUrl)
            .putString(prefix + "model", value.model).putBoolean(prefix + "strict", value.strict)
            .putBoolean(prefix + "photos", value.includePhotos)
            .putString(prefix + "key_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putBoolean(prefix + "tested", !resetTest && unchanged && preferences.getBoolean(prefix + "tested", false)).apply()
    }
    val cloudTested: Boolean
        get() = preferences.getBoolean("cloud_${cloud().provider.id}_tested", false)

    fun markCloudTested(settings: CloudSettings) {
        val value = settings.normalized()
        check(cloud() == value) { "配置已变化，请重新测试当前配置" }
        preferences.edit().putBoolean("cloud_${value.provider.id}_tested", true).apply()
    }
}
