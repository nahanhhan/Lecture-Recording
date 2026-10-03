package io.github.nahanhhan.lecturerecording

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.nahanhhan.lecturerecording.cloud.CloudCheck
import io.github.nahanhhan.lecturerecording.cloud.CloudConnectionTest
import io.github.nahanhhan.lecturerecording.data.*
import io.github.nahanhhan.lecturerecording.export.NotesRenderer
import io.github.nahanhhan.lecture.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CloudSettingsIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<LectureApp>()
    private val prefs get() = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private lateinit var original: Map<String, *>
    @Before fun prepare(): Unit = runBlocking {
        check(app.packageName.endsWith(".uitest")) { "Requires isolated test application" }
        app.graph.initialized.await()
        original = prefs.all.toMap()
        prefs.edit().clear().commit()
        Unit
    }
    @After fun restore() {
        val editor = prefs.edit().clear()
        original.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
        } }
        editor.commit()
    }
    @Test fun encryptedProviderProfilesMigrateAndInvalidateOnlyChangedConfiguration() {
        val store = SettingsStore(app)
        val deepseek = CloudSettings(" https://api.deepseek.com/v1/chat/completions/ ", " fixture-model ", "fixture-deepseek-key", true, CloudProvider.DEEPSEEK, false)
        store.saveCloud(deepseek)
        val cipher = prefs.getString("cloud_deepseek_key_cipher", null)!!
        prefs.edit().clear().putString("base_url", deepseek.baseUrl.trim()).putString("cloud_model", "fixture-model")
            .putString("key_cipher", cipher).putBoolean("strict", true).putBoolean("cloud_tested", true).commit()
        val migrated = store.cloud()
        Assert.assertEquals(CloudProvider.DEEPSEEK, migrated.provider)
        Assert.assertEquals("fixture-deepseek-key", migrated.key)
        Assert.assertFalse(migrated.strict)
        Assert.assertFalse(store.cloudTested)
        store.saveCloud(migrated.copy(includePhotos = false))
        val normalized = store.cloud()
        store.markCloudTested(normalized)
        store.saveCloud(normalized.copy(baseUrl = normalized.baseUrl + "/chat/completions/"))
        Assert.assertTrue(store.cloudTested)
        Assert.assertEquals("", store.cloud(CloudProvider.OPENROUTER).key)
        val router = CloudSettings(CloudProvider.OPENROUTER.baseUrl, "router-model", "fixture-router-key", false, CloudProvider.OPENROUTER)
        store.saveCloud(router); Assert.assertFalse(store.cloudTested)
        Assert.assertEquals("fixture-deepseek-key", store.cloud(CloudProvider.DEEPSEEK).key)
        store.saveCloud(normalized); Assert.assertTrue(store.cloudTested)
        store.saveCloud(normalized.copy(model = "another-model")); Assert.assertFalse(store.cloudTested)
        Assert.assertEquals("fixture-router-key", store.cloud(CloudProvider.OPENROUTER).key)
        Assert.assertFalse(prefs.all.values.any { it == "fixture-router-key" || it == "fixture-deepseek-key" })
    }

    private fun fakeResponse(request: JsonObject): JsonObject {
        val messages = request.getValue("messages").jsonArray
        val last = messages.last().jsonObject
        if (request["tools"] == null || last["role"]?.jsonPrimitive?.content == "tool") {
            if (last["role"]?.jsonPrimitive?.content == "tool") {
                val receipt = protocolJson.parseToJsonElement(last.getValue("content").jsonPrimitive.content).jsonObject
                Assert.assertTrue(receipt.getValue("ok").jsonPrimitive.boolean)
                Assert.assertTrue(app.cacheDir.listFiles()!!.any { it.name.startsWith("cloud-test-") && it.readText().contains("橡树") })
            }
            return protocolJson.parseToJsonElement("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"连接正常"}}]}""").jsonObject
        }
        val content = messages[1].jsonObject.getValue("content").jsonArray
        val text = content.first { it.jsonObject["text"]?.jsonPrimitive?.content?.contains("以下 JSON 是课堂资料：") == true }
            .jsonObject.getValue("text").jsonPrimitive.content
        val batch = protocolJson.decodeFromString<Batch>(text.substringAfter("以下 JSON 是课堂资料：\n"))
        val notes = Notes("橡树", listOf(NoteSection("课堂内容", "讨论橡树", listOf("test_text"), emptyList())))
        val assistant = buildJsonObject {
            put("role", "assistant"); put("content", JsonNull)
            put("tool_calls", buildJsonArray { add(buildJsonObject {
                put("id", "test_call"); put("type", "function")
                put("function", buildJsonObject { put("name", "save_class_notes"); put("arguments", protocolJson.encodeToString(notes)) })
            }) })
        }
        Assert.assertEquals("batch_001", batch.batchId)
        return buildJsonObject { put("choices", buildJsonArray { add(buildJsonObject { put("finish_reason", "tool_calls"); put("message", assistant) }) }) }
    }

    @Test fun textModeNeverSendsPhotosAndChecksRealLocalSaveAndReceipt() = runBlocking {
        val requests = mutableListOf<JsonObject>(); val results = mutableListOf<CloudCheck>()
        val config = CloudSettings(CloudProvider.DEEPSEEK.baseUrl, "fixture-model", "fake-key", false, CloudProvider.DEEPSEEK, false)
        CloudConnectionTest(app, config) { request -> requests += request; fakeResponse(request) }.run { results += it }
        Assert.assertEquals(3, requests.size)
        Assert.assertFalse(requests.any { it.toString().contains("image_url") || it.toString().contains("test_photo") })
        Assert.assertEquals(listOf("passed", "passed", "skipped"), results.filter { it.state != "checking" }.map { it.state })
        Assert.assertFalse(app.cacheDir.listFiles()!!.any { it.name.startsWith("cloud-test-") })
        val photo = PhotoEntity("local_photo", "lesson", "ast_000000000_001.jpg", 0, 0, 1)
        val markdown = NotesRenderer.markdown(listOf(Notes("橡树", listOf(NoteSection("内容", "课堂文字", emptyList(), emptyList())))), emptyList(), listOf(photo))
        Assert.assertTrue(markdown.contains("其他课堂照片") && markdown.contains(photo.filename))
    }

    @Test fun imageFailurePreservesSuccessfulTextStepWithoutApprovingConfiguration() = runBlocking {
        val results = mutableListOf<CloudCheck>()
        val config = CloudSettings(CloudProvider.OPENROUTER.baseUrl, "text-model", "fake-key", false, CloudProvider.OPENROUTER, true)
        val store = SettingsStore(app)
        store.saveCloud(config); store.markCloudTested(config)
        store.saveCloud(config, resetTest = true)
        try {
            CloudConnectionTest(app, config) { request ->
                if (request.toString().contains("image_url")) error("供应商不支持 image_url")
                fakeResponse(request)
            }.run { results += it }
            Assert.fail("Image test must fail")
        } catch (error: IllegalStateException) { Assert.assertTrue(error.message!!.contains("读图")) }
        Assert.assertEquals(listOf("passed", "passed", "failed"), results.filter { it.state != "checking" }.map { it.state })
        Assert.assertTrue(results.last().detail.contains("image_url"))
        Assert.assertFalse(SettingsStore(app).cloudTested)
    }

    @Test fun settingsUiSwitchesProviderAddressAndPhotoMode() {
        SettingsStore(app).saveCloud(CloudSettings(CloudProvider.DEEPSEEK.baseUrl, "fixture-model", "fake-key", false, CloudProvider.DEEPSEEK, false))
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)).use {
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithTag("cloud-provider").performScrollTo().performClick()
            compose.onNodeWithText("OpenRouter").performClick()
            compose.onNodeWithText("https://openrouter.ai/api/v1").assertExists()
            compose.onNodeWithTag("cloud-provider").performScrollTo().performClick()
            compose.onNodeWithText("OpenCode Zen").performClick()
            compose.onNodeWithText("https://opencode.ai/zen/v1").assertExists()
            compose.onNodeWithTag("cloud-photos").performScrollTo().performClick()
            compose.onNodeWithText("保存配置").performScrollTo().performClick()
            compose.waitForIdle()
            Assert.assertEquals(CloudProvider.OPENCODE, SettingsStore(app).cloud().provider)
            Assert.assertTrue(SettingsStore(app).cloud().includePhotos)
            Assert.assertEquals("", SettingsStore(app).cloud().key)
            Assert.assertEquals("fake-key", SettingsStore(app).cloud(CloudProvider.DEEPSEEK).key)
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(app.cacheDir, "cloud-settings-preview.png"))
        }
    }
}
