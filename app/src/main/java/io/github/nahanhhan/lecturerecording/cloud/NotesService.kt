package io.github.nahanhhan.lecturerecording.cloud

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Base64
import androidx.core.app.ServiceCompat
import androidx.room.withTransaction
import io.github.nahanhhan.lecturerecording.*
import io.github.nahanhhan.lecturerecording.data.*
import io.github.nahanhhan.lecture.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class NotesService : Service() {
    private val graph get() = (application as LectureApp).graph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: Job? = null
    private var currentJobId: String? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (active?.isActive == true) return START_NOT_STICKY
        ServiceCompat.startForeground(this, 2, Notifications.build(this, "整理课堂笔记", "正在准备课堂资料").build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        active = scope.launch {
            try {
                graph.initialized.await()
                val settings = graph.settings.cloud()
                check(graph.settings.cloudTested) { "请先在设置中测试云端接口" }
                val job = intent?.getStringExtra("job_id")?.let { requireNotNull(graph.dao.job(it)) }
                    ?: createJob(requireNotNull(intent?.getStringExtra("lesson_id")))
                currentJobId = job.id
                runJob(job, settings)
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    currentJobId?.let { graph.dao.jobStatus(it, "waiting", if (error is CancellationException) "任务已暂停，可手动继续" else error.message ?: "整理失败，可手动重试") }
                }
            } finally { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }
    private suspend fun createJob(lessonId: String): JobEntity = graph.database.withTransaction {
        val lesson = requireNotNull(graph.dao.lesson(lessonId))
        check(lesson.status !in setOf("recording", "paused", "processing")) { "请等待录音和实时转写结束" }
        val segments = graph.dao.segments(lessonId).map { Segment(it.id, it.startMs, it.endMs,
            if (it.text.isBlank()) "【待核对：此段转写未完成】" else it.text) }
        val photos = graph.dao.photos(lessonId).filter { it.selected }.map { Photo(it.id, it.filename, it.audioTimeMs) }
        val batches = BatchPlanner.plan(lessonId, lesson.revision, lesson.course,
            graph.settings.glossary.split(',', '，', '\n').map { it.trim() }.filter { it.isNotBlank() }, segments, photos)
        check(batches.isNotEmpty()) { "没有可整理的文字或照片" }
        val job = JobEntity(UUID.randomUUID().toString(), lessonId, lesson.revision, protocolJson.encodeToString(batches), createdAt = System.currentTimeMillis())
        graph.dao.putJob(job); job
    }
    private suspend fun runJob(job: JobEntity, settings: CloudSettings) {
        val batches = protocolJson.decodeFromString<List<Batch>>(job.snapshotJson)
        val definition = assets.open("tool-definition.json").bufferedReader().use { protocolJson.parseToJsonElement(it.readText()).jsonObject }
        val client = CloudClient(settings)
        graph.dao.jobStatus(job.id, "running")
        batches.forEachIndexed { index, batch ->
            currentCoroutineContext().ensureActive()
            check(graph.dao.lesson(job.lessonId)?.revision == job.revision) { "课堂原稿已修改，请发起新的整理任务" }
            ServiceCompat.startForeground(this, 2, Notifications.build(this, "整理课堂笔记", "第 ${index + 1}/${batches.size} 批").build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            val images = batch.photos.map { photo -> encodeImage(File(graph.lessonDir(job.lessonId), photo.filename)) }
            val request = ChatProtocol.initial(settings.model, batch, images, definition, settings.strict)
            var saved = graph.dao.batch(job.id, batch.batchId, job.revision)
            if (saved == null) {
                val submission = ChatProtocol.submission(client.complete(request))
                try {
                    val notes = NoteValidator.parseAndValidate(submission.arguments, batch)
                    val receipt = ChatProtocol.receipt(submission.callId, true, batch.batchId, job.id)
                    graph.database.withTransaction {
                        check(graph.dao.lesson(job.lessonId)?.revision == job.revision) { "课堂来源版本已变化" }
                        graph.dao.insertBatch(NoteBatchEntity(job.id, batch.batchId, job.revision,
                            protocolJson.encodeToString(notes), submission.callId, submission.assistant.toString(), receipt.toString()))
                    }
                    saved = requireNotNull(graph.dao.batch(job.id, batch.batchId, job.revision))
                } catch (error: Exception) {
                    val failure = ChatProtocol.receipt(submission.callId, false, batch.batchId, error = "笔记结构、来源或本地保存校验失败")
                    runCatching { client.complete(ChatProtocol.followup(request, submission.assistant, failure)) }
                    throw error
                }
            }
            val stored = requireNotNull(saved)
            if (!stored.confirmed) {
                val assistant = protocolJson.parseToJsonElement(stored.assistantJson).jsonObject
                val receipt = protocolJson.parseToJsonElement(stored.receiptJson).jsonObject
                val confirmation = client.complete(ChatProtocol.followup(request, assistant, receipt))
                val choice = confirmation.getValue("choices").jsonArray.first().jsonObject
                check(choice["finish_reason"]?.jsonPrimitive?.content == "stop") { "服务未完成保存确认，已保存笔记可继续查看" }
                check(choice.getValue("message").jsonObject["tool_calls"] == null) { "完成确认不应再次调用工具" }
                graph.dao.confirmBatch(job.id, batch.batchId, job.revision)
            }
        }
        graph.dao.jobStatus(job.id, "completed")
        graph.dao.clearEditedNote(job.lessonId)
    }
    override fun onTimeout(startId: Int, fgsType: Int) { active?.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        fun encodeImage(file: File): ImageInput {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })) { "照片无法读取" }
            val bytes = ByteArrayOutputStream()
            try { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bytes) } finally { bitmap.recycle() }
            return ImageInput("image/jpeg", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
        }
    }
}
