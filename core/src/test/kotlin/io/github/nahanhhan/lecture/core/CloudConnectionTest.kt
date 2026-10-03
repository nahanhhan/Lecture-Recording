package io.github.nahanhhan.lecture.core

import kotlin.test.*
import kotlinx.serialization.json.*

class CloudConnectionTest {
    @Test fun providerAddressesAcceptRootVersionAndFullEndpoint() {
        listOf("https://api.deepseek.com", "https://api.deepseek.com/v1", "https://openrouter.ai/api/v1",
            "https://opencode.ai/zen/v1", "https://api.openai.com/v1", "https://custom.example/gateway/v3").forEach { base ->
            assertEquals("$base/chat/completions", CloudEndpoint.completions(base))
            assertEquals("$base/chat/completions", CloudEndpoint.completions("  $base/chat/completions/  "))
            assertEquals("$base/models", CloudEndpoint.models("$base/chat/completions"))
        }
        assertEquals("https://api.deepseek.com/chat/completions", CloudEndpoint.completions("HTTPS://API.DEEPSEEK.COM/"))
    }
    @Test fun unsafeOrDifferentProtocolAddressesAreRejected() {
        listOf("", "deepseek.com", "http://api.deepseek.com", "https://key@api.deepseek.com",
            "https://api.deepseek.com?api_key=test", "https://api.deepseek.com/#x", "https://api.deepseek.com:0",
            "https://api.deepseek.com:99999", "https://opencode.ai/zen/v1/responses", "https://example.com/v1/messages").forEach {
            assertFailsWith<IllegalArgumentException>(it) { CloudEndpoint.normalize(it) }
        }
    }
    private val request = protocolJson.parseToJsonElement("""{"model":"test","parallel_tool_calls":false,"tool_choice":{"type":"function","function":{"name":"save_class_notes"}},"tools":[{"type":"function","function":{"name":"save_class_notes","strict":true,"parameters":{"type":"object"}}}],"messages":[]}""").jsonObject
    @Test fun deepseekUsesNonThinkingCompatibleToolsEvenForLegacyCustomSettings() {
        val adapted = CloudRequests.adapt(request, "https://api.deepseek.com/v1", true)
        assertEquals("disabled", adapted.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
        assertFalse("parallel_tool_calls" in adapted)
        assertFalse("strict" in adapted.getValue("tools").jsonArray[0].jsonObject.getValue("function").jsonObject)
        assertEquals(request["tool_choice"], adapted["tool_choice"])
        val receipt = ChatProtocol.followup(adapted, buildJsonObject { put("role", "assistant") }, ChatProtocol.receipt("call", true, "batch"))
        assertEquals("none", receipt.getValue("tool_choice").jsonPrimitive.content)
        assertEquals(adapted["thinking"], CloudRequests.adapt(receipt, "https://api.deepseek.com", true)["thinking"])
    }
    @Test fun compatibilityOmitsStrictWithoutChangingToolSchemaOrReceipts() {
        val normal = CloudRequests.adapt(request, CloudProvider.OPENROUTER.baseUrl, false)
        assertFalse("strict" in normal.getValue("tools").jsonArray[0].jsonObject.getValue("function").jsonObject)
        assertEquals(request["messages"], normal["messages"])
        assertEquals(request["tool_choice"], normal["tool_choice"])
        assertEquals(request, CloudRequests.adapt(request, CloudProvider.OPENAI.baseUrl, true))
    }
    @Test fun errorsKeepProviderExplanationAndNeverDisplayKey() {
        val error = CloudErrors.message(400, """{"error":{"message":"Unsupported image_url; secret-custom-key; api_key=another-secret"}}""", "secret-custom-key")
        assertTrue(error.contains("HTTP 400") && error.contains("Unsupported image_url"))
        assertFalse(error.contains("secret-custom-key") || error.contains("another-secret"))
        assertTrue(CloudErrors.message(401, "not JSON", "key").contains("密钥"))
        assertTrue(CloudErrors.message(402, "{}", "key").contains("余额"))
        assertTrue(CloudErrors.message(404, "<html>404</html>", "key").contains("地址或模型"))
    }
    @Test fun providerDetectionUsesExactHostsAndSeparatesProfiles() {
        CloudProvider.entries.filter { it != CloudProvider.CUSTOM }.forEach { assertEquals(it, CloudProvider.detect(it.baseUrl)) }
        assertEquals(CloudProvider.CUSTOM, CloudProvider.detect("https://api.deepseek.com.example.org"))
        assertNull(CloudProvider.fromId("removed"))
    }
    @Test fun textOnlySnapshotsPreserveTextAndNeverSendOrAllowPhotoReferences() {
        val text = "课堂文字".repeat(2000)
        val segments = listOf(Segment("s1", 0, 1000, text))
        val photos = listOf(Photo("p1", "ast_000000000_001.jpg", 0))
        val batches = BatchPlanner.plan("lesson", 1, "课程", emptyList(), segments, photos, includePhotos = false)
        assertEquals(text, batches.flatMap { it.segments }.joinToString("") { it.text })
        assertTrue(batches.all { it.photos.isEmpty() })
        assertEquals("p1", photos.single().id)
        val tool = request.getValue("tools").jsonArray.single().jsonObject
        val body = ChatProtocol.initial("model", batches.first(), emptyList(), tool, false)
        assertFalse(body.toString().contains("image_url") || body.toString().contains("p1"))
        assertFailsWith<IllegalArgumentException> {
            NoteValidator.parseAndValidate("""{"title":"课堂","sections":[{"heading":"内容","markdown":"文字","source_segment_ids":["s1"],"photo_ids":["p1"]}]}""", batches.first())
        }
        assertTrue(BatchPlanner.plan("lesson", 1, "课程", emptyList(), emptyList(), photos, includePhotos = false).isEmpty())
    }
    @Test fun modelCatalogSupportsOptionalCapabilitiesAndDeduplicates() {
        val catalog = CloudModelCatalog.parse(protocolJson.parseToJsonElement("""{"data":[{"id":"b"},{"id":"a","architecture":{"input_modalities":["text","image"]},"supported_parameters":["tools"]},{"id":"text","architecture":{"input_modalities":["text"]},"supported_parameters":[]},{"id":"b"},{}]}""").jsonObject)
        assertEquals(listOf("a", "b", "text"), catalog.map { it.id })
        assertEquals(CloudModel("a", true, true), catalog[0])
        assertEquals(CloudModel("b", null, null), catalog[1])
        assertEquals(CloudModel("text", false, false), catalog[2])
    }
}
