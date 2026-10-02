package io.github.nahanhhan.lecture.core

import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File

class ContractTest {
    private val segment = Segment("s1", 0, 15000, "课堂原文")
    private val photo = Photo("p1", "ast_000015000_001.jpg", 15000)
    private val batch = Batch("lesson", "batch_001", 1, "操作系统", emptyList(), listOf(segment), listOf(photo))
    private val valid = Notes("课堂笔记", listOf(NoteSection("小节", "原文整理", listOf("s1"), listOf("p1"))))
    @Test fun photoTimeSurvivesLongLessonsAndLargeSequences() {
        val name = PhotoFilename.create(1_000_000_001, 1001)
        assertEquals(1_000_000_001L to 1001L, PhotoFilename.parse(name))
        assertFailsWith<IllegalArgumentException> { PhotoFilename.parse("ast_000015000_000.jpg") }
    }
    @Test fun pausedTimeDoesNotFollowWallClock() {
        val clock = SampleClock()
        clock.advance(16000); assertEquals(1000, clock.milliseconds)
        assertEquals(1000, clock.milliseconds)
        clock.advance(8000); assertEquals(1500, clock.milliseconds)
    }
    @Test fun batchesKeepEveryCharacterAndPhotoWithinLimits() {
        val sources = listOf(segment.copy(text = "课".repeat(12500)))
        val photos = (1..14).map { photo.copy(id = "p$it", filename = PhotoFilename.create(it.toLong(), it.toLong()), audioTimeMs = it.toLong()) }
        val planned = BatchPlanner.plan("lesson", 1, "", emptyList(), sources, photos)
        assertTrue(planned.all { it.photos.size <= 6 && it.segments.sumOf { s -> s.text.length } <= 6000 })
        assertEquals(sources.single().text, planned.flatMap { it.segments }.joinToString("") { it.text })
        assertEquals(photos.map { it.id }, planned.flatMap { it.photos }.map { it.id })
        planned.forEach { assertEquals(it.photos.indices.toList(), it.photos.map { p -> p.inputImageIndex }) }
    }
    @Test fun photoOnlyLessonCanBeOrganized() {
        val planned = BatchPlanner.plan("lesson", 1, "", emptyList(), emptyList(), listOf(photo))
        assertEquals(1, planned.size)
        val notes = valid.copy(sections = listOf(valid.sections.single().copy(sourceSegmentIds = emptyList())))
        assertEquals(notes, NoteValidator.parseAndValidate(protocolJson.encodeToString(notes), planned.single()))
    }
    @Test fun crossLessonSourcesAreRejected() {
        val notes = valid.copy(sections = listOf(valid.sections.single().copy(sourceSegmentIds = listOf("other_lesson"))))
        assertFailsWith<IllegalArgumentException> { NoteValidator.parseAndValidate(protocolJson.encodeToString(notes), batch) }
    }
    @Test fun unknownPhotoAndMismatchedTimestampAreRejected() {
        val notes = valid.copy(sections = listOf(valid.sections.single().copy(photoIds = listOf("other_photo"))))
        assertFailsWith<IllegalArgumentException> { NoteValidator.parseAndValidate(protocolJson.encodeToString(notes), batch) }
        assertFailsWith<IllegalArgumentException> { NoteValidator.parseAndValidate(protocolJson.encodeToString(valid), batch.copy(photos = listOf(photo.copy(audioTimeMs = 1)))) }
    }
    @Test fun extraFieldsAndEmptySectionsAreRejected() {
        assertFails { NoteValidator.parseAndValidate("{\"title\":\"x\",\"sections\":[],\"lesson_id\":\"other\"}", batch) }
        assertFailsWith<IllegalArgumentException> { NoteValidator.parseAndValidate("{\"title\":\"x\",\"sections\":[]}", batch) }
    }
    @Test fun sourceFreeAndBlankSectionsAreRejected() {
        listOf(valid.sections.single().copy(sourceSegmentIds = emptyList(), photoIds = emptyList()),
            valid.sections.single().copy(markdown = " ")).forEach {
            assertFailsWith<IllegalArgumentException> { NoteValidator.parseAndValidate(protocolJson.encodeToString(valid.copy(sections = listOf(it))), batch) }
        }
    }
    @Test fun modelCannotSupplyImagePathsOrScripts() {
        listOf("![x](file:///private.jpg)", "<script>bad()</script>").forEach {
            assertFailsWith<IllegalArgumentException> { NoteValidator.parseAndValidate(protocolJson.encodeToString(valid.copy(sections = listOf(valid.sections.single().copy(markdown = it)))), batch) }
        }
    }
    private fun example(name: String) = protocolJson.parseToJsonElement(File("../docs/openai-tool-calling/examples/$name").readText()).jsonObject
    @Test fun documentedRoundTripPreservesCallAndReceipt() {
        val request = example("01-request.json")
        val call = ChatProtocol.submission(example("02-response.json"))
        val receipt = example("03-tool-result.json")
        assertEquals(call.callId, receipt.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals(example("04-followup-request.json"), ChatProtocol.followup(request, call.assistant, receipt))
    }
    @Test fun truncatedNormalAndUnknownToolResponsesAreRejected() {
        val response = example("02-response.json")
        val choice = response.getValue("choices").jsonArray.first().jsonObject
        assertFailsWith<IllegalArgumentException> {
            ChatProtocol.submission(JsonObject(response + ("choices" to JsonArray(listOf(JsonObject(choice + ("finish_reason" to JsonPrimitive("length"))))))))
        }
        assertFailsWith<IllegalArgumentException> { ChatProtocol.submission(example("05-final-response.json")) }
        assertFailsWith<IllegalArgumentException> { ChatProtocol.submission(protocolJson.parseToJsonElement(response.toString().replace("save_class_notes", "delete_recording")).jsonObject) }
    }
    @Test fun compatibilityModeKeepsToolCallingAndDisablesStrictOnly() {
        val definition = protocolJson.parseToJsonElement(File("../docs/openai-tool-calling/tool-definition.json").readText()).jsonObject
        val request = ChatProtocol.initial("configured-model", batch, listOf(ImageInput("image/jpeg", "test")), definition, false)
        assertFalse(request.getValue("tools").jsonArray.first().jsonObject.getValue("function").jsonObject.getValue("strict").jsonPrimitive.boolean)
        assertEquals("save_class_notes", request.getValue("tool_choice").jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content)
    }
    @Test fun silenceDoesNotCreateSpeechAndContinuousSpeechIsCapped() {
        val segmenter = SpeechSegmenter()
        assertTrue(segmenter.accept(ShortArray(640), 0).isEmpty())
        val windows = mutableListOf<SpeechSegmenter.Window>()
        repeat(400) { windows += segmenter.accept(ShortArray(640) { 8000 }, 640L + it * 640) }
        assertTrue(windows.any { it.final })
        assertTrue(windows.filter { it.final }.all { it.samples.size <= 16000 * 15 + 640 })
        val final = segmenter.finish()
        assertNotNull(final); assertTrue(final.final)
        assertNull(segmenter.finish())
    }
}
