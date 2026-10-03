package io.github.nahanhhan.lecturerecording

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiScrollable
import io.github.nahanhhan.lecture.core.ModelArchiveExtractor
import io.github.nahanhhan.lecturerecording.data.*
import io.github.nahanhhan.lecturerecording.models.NativeBzip2InputStream
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

@RunWith(AndroidJUnit4::class)
class StorageIntegrationTest {
    private val app get() = ApplicationProvider.getApplicationContext<LectureApp>()
    private val graph get() = app.graph
    @Before fun prepare() = runBlocking {
        check(app.packageName.endsWith(".uitest")) { "These destructive fixture tests require -PisolatedTest=true" }
        graph.initialized.await()
        graph.recording.value = RecordingState()
        graph.cloudLessonId.value = null
        graph.database.clearAllTables()
    }
    private fun seeded(id: String, title: String = id, status: String = "completed") = runBlocking {
        graph.dao.putLesson(LessonEntity(id, title, "测试课程", System.currentTimeMillis(), status))
        val folder = graph.lessonDir(id)
        File(folder, "audio.wav").writeText("fixture audio")
        graph.dao.putChunk(ChunkEntity("${id}_chunk", id, File(folder, "audio.wav").path, 0))
        graph.dao.putSegment(SegmentEntity("${id}_segment", id, 0, 1000, "", "测试原稿", "ready"))
        graph.dao.putPhoto(PhotoEntity("${id}_photo", id, "fixture.jpg", 0, 0, 1))
        File(folder, "fixture.jpg").writeText("fixture photo")
        graph.dao.putJob(JobEntity("${id}_job", id, 1, "[]", "completed", createdAt = 0))
        graph.dao.insertBatch(NoteBatchEntity("${id}_job", "batch_001", 1, "{}", "call", "{}", "{}"))
        graph.dao.putEditedNote(EditedNoteEntity(id, "测试笔记"))
        File(app.cacheDir, "exports").mkdirs()
        File(app.cacheDir, "exports/lecture_$id.zip").writeText("fixture export")
    }
    @Test fun deletionRemovesAllAssociationsAndPreservesAnotherRecording() = runBlocking {
        seeded("delete_one"); seeded("keep_one")
        Assert.assertEquals(1, graph.recordings.delete(listOf("delete_one")))
        Assert.assertNull(graph.dao.lesson("delete_one"))
        Assert.assertTrue(graph.dao.chunks("delete_one").isEmpty())
        Assert.assertTrue(graph.dao.segments("delete_one").isEmpty())
        Assert.assertTrue(graph.dao.photos("delete_one").isEmpty())
        Assert.assertTrue(graph.dao.jobs("delete_one").isEmpty())
        Assert.assertTrue(graph.dao.batches("delete_one_job").isEmpty())
        Assert.assertNull(graph.dao.editedNote("delete_one"))
        Assert.assertFalse(File(app.filesDir, "lessons/delete_one").exists())
        Assert.assertFalse(File(app.cacheDir, "exports/lecture_delete_one.zip").exists())
        Assert.assertNotNull(graph.dao.lesson("keep_one"))
        Assert.assertTrue(File(app.filesDir, "lessons/keep_one/audio.wav").exists())
    }
    @Test fun bulkDeletionRejectsBusyRecordsWithoutDeletingAnySelection() = runBlocking {
        seeded("idle_one"); seeded("busy_one", status = "recording")
        try { graph.recordings.delete(listOf("idle_one", "busy_one")); Assert.fail("Busy deletion must fail") }
        catch (_: IllegalStateException) { }
        Assert.assertNotNull(graph.dao.lesson("idle_one"))
        Assert.assertNotNull(graph.dao.lesson("busy_one"))
        Assert.assertTrue(File(app.filesDir, "lessons/idle_one/audio.wav").exists())
    }
    @Test fun nativeDecompressionMatchesJavaAndRejectsTruncatedInput() {
        val bytes = ByteArray(2 * 1024 * 1024).also { Random(9).nextBytes(it) }
        val archive = File(app.cacheDir, "native-comparison.bz2")
        BZip2CompressorOutputStream(archive.outputStream()).use { it.write(bytes) }
        val javaStart = SystemClock.elapsedRealtime()
        val reference = BZip2CompressorInputStream(archive.inputStream()).use { it.readBytes() }
        val javaMs = SystemClock.elapsedRealtime() - javaStart
        val nativeStart = SystemClock.elapsedRealtime()
        val actual = NativeBzip2InputStream(archive).use { it.readBytes() }
        val nativeMs = SystemClock.elapsedRealtime() - nativeStart
        Assert.assertArrayEquals(reference, actual)
        Assert.assertArrayEquals(bytes, actual)
        android.util.Log.i("RecNoteBenchmark", "2MiB random fixture Java=${javaMs}ms native=${nativeMs}ms")
        val broken = File(app.cacheDir, "native-truncated.bz2")
        broken.writeBytes(archive.readBytes().copyOf(archive.length().toInt() / 2))
        try { NativeBzip2InputStream(broken).use { it.readBytes() }; Assert.fail("Truncated archive must fail") }
        catch (_: java.io.IOException) { }
    }
    @Test fun detailDeletionAndLongPressBulkSelectionWork() = runBlocking {
        seeded("ui_one", "Record One"); seeded("ui_two", "Record Two")
        seeded("ui_three", "Record Three"); seeded("ui_busy", "Busy Record", "recording")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun click(text: String) {
            val control = device.findObject(UiSelector().text(text))
            if (!control.waitForExists(1_000)) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(text))
            Assert.assertTrue("Control missing: $text", control.waitForExists(10_000))
            control.click(); device.waitForIdle()
        }
        click("Record One"); click("删除录音"); click("取消")
        Assert.assertNotNull(graph.dao.lesson("ui_one"))
        click("删除录音"); click("删除")
        repeat(50) { if (graph.dao.lesson("ui_one") != null) SystemClock.sleep(100) }
        Assert.assertNull(graph.dao.lesson("ui_one"))
        val record = device.findObject(UiSelector().text("Record Two"))
        if (!record.waitForExists(1_000)) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Record Two"))
        Assert.assertTrue(record.waitForExists(10_000)); record.longClick(); device.waitForIdle()
        click("全选")
        Assert.assertTrue(device.findObject(UiSelector().text("已选择 2 条")).waitForExists(5_000))
        device.findObject(UiSelector().description("删除所选")).click(); device.waitForIdle()
        click("删除")
        repeat(50) { if (graph.dao.lesson("ui_two") != null) SystemClock.sleep(100) }
        Assert.assertNull(graph.dao.lesson("ui_two")); Assert.assertNull(graph.dao.lesson("ui_three"))
        Assert.assertNotNull(graph.dao.lesson("ui_busy"))
    }
}
