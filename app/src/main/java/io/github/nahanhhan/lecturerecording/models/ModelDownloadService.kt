package io.github.nahanhhan.lecturerecording.models

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.nahanhhan.lecturerecording.*
import io.github.nahanhhan.lecturerecording.logging.AppLog
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloadService : Service() {
    private val graph get() = (application as LectureApp).graph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: Job? = null
    private var call: Call? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (active?.isActive == true) return START_NOT_STICKY
        ServiceCompat.startForeground(this, 3, Notifications.build(this, "下载识别模型", "正在连接下载源").build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val modelId = intent?.getStringExtra("model") ?: "aed"
        val source = graph.settings.downloadSource
        AppLog.i("ModelDownloadService", "开始下载 model=$modelId 下载源=$source")
        active = scope.launch {
            try {
                check(graph.recording.value.lessonId == null) { "请在录音结束后安装模型" }
                install(ModelCatalog.get(modelId), source)
                graph.download.value = graph.download.value.copy(status = "installed")
                AppLog.i("ModelDownloadService", "模型安装完成 model=$modelId")
            } catch (error: Exception) {
                AppLog.e("ModelDownloadService", "下载失败 model=$modelId", error)
                graph.download.value = graph.download.value.copy(modelId = modelId, status = "error", error = if (error is CancellationException) "下载已暂停，可继续" else error.message ?: "下载失败，可继续")
            } finally { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }
    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private suspend fun install(model: ModelSpec, source: DownloadSource) {
        val root = File(filesDir, "models").apply { mkdirs() }
        // 旧格式残留不带下载源，无法归属到具体源，直接清理避免跨源续传
        File(root, "${model.id}.part").delete()
        File(root, "${model.id}.etag").delete()
        val partial = File(root, "${model.id}.${source.storage}.part")
        val etagFile = File(root, "${model.id}.${source.storage}.etag")
        val offset = if (partial.exists()) partial.length() else 0
        AppLog.i("ModelDownloadService", "下载 model=${model.id} 已有=${offset}B 总计=${model.bytes}B")
        graph.download.value = DownloadState(model.id, offset, model.bytes, "downloading")
        val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        if (offset < model.bytes) {
            val builder = Request.Builder().url(model.urlFor(source))
            if (offset > 0) {
                builder.header("Range", "bytes=$offset-")
                if (etagFile.exists()) builder.header("If-Range", etagFile.readText())
            }
            call = client.newCall(builder.build())
            call!!.execute().use { response ->
                AppLog.i("ModelDownloadService", "下载响应 model=${model.id} HTTP=${response.code}")
                check(response.isSuccessful) { "下载失败（HTTP ${response.code}）" }
                val resume = response.code == 206
                if (resume) check(response.header("Content-Range")?.startsWith("bytes $offset-") == true) { "下载续传位置不一致" }
                response.header("ETag")?.let { etagFile.writeText(it) }
                RandomAccessFile(partial, "rw").use { output ->
                    if (!resume) output.setLength(0)
                    output.seek(if (resume) offset else 0)
                    requireNotNull(response.body).byteStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        var lastSync = output.length()
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer); if (count < 0) break
                            check(output.filePointer + count <= model.bytes) { "模型下载大小超出预期" }
                            output.write(buffer, 0, count)
                            graph.download.value = DownloadState(model.id, output.filePointer, model.bytes, "downloading")
                            if (output.filePointer - lastSync > 4 * 1024 * 1024) {
                                output.fd.sync(); lastSync = output.filePointer
                                AppLog.d("ModelDownloadService", "下载进度 ${output.filePointer}/${model.bytes}")
                            }
                        }
                        output.fd.sync()
                    }
                }
            }
        }
        graph.download.value = DownloadState(model.id, partial.length(), model.bytes, "verifying")
        AppLog.i("ModelDownloadService", "下载完成 model=${model.id} 字节=${partial.length()}，开始校验")
        check(partial.length() == model.bytes) { "下载尚未完成，可继续" }
        if (digest(partial) != model.sha256) {
            AppLog.e("ModelDownloadService", "模型校验失败 model=${model.id}")
            partial.delete(); throw IllegalStateException("模型校验失败，请重新下载")
        }
        AppLog.i("ModelDownloadService", "校验通过 model=${model.id}，开始解压安装")
        val staging = File(root, "${model.id}_installing").apply { mkdirs() }
        // Only whitelisted regular files are copied; archive paths are never used as destinations.
        TarArchiveInputStream(BZip2CompressorInputStream(partial.inputStream().buffered())).use { archive ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = archive.nextTarEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (!entry.isFile || name !in model.files) continue
                check(entry.size in 1..1_500_000_000L) { "模型文件大小无效" }
                val target = File(staging, name)
                target.outputStream().buffered().use { archive.copyTo(it) }
            }
        }
        val hashes = model.files.associateWith { name ->
            val file = File(staging, name)
            check(file.exists() && file.length() > 0) { "模型缺少 $name" }
            digest(file)
        }
        val destination = File(root, model.id).apply { mkdirs() }
        File(destination, "installed.json").delete()
        model.files.forEach { name ->
            val target = File(destination, name)
            if (target.exists()) check(target.delete())
            check(File(staging, name).renameTo(target)) { "模型安装失败" }
        }
        File(destination, "installed.json").writeText(buildJsonObject {
            put("id", model.id); put("archive_sha256", model.sha256); put("runtime", "1.12.27")
            put("files", buildJsonObject { hashes.forEach { (name, hash) -> put(name, hash) } })
        }.toString())
        staging.delete(); partial.delete(); etagFile.delete()
        AppLog.i("ModelDownloadService", "解压安装完成 model=${model.id} 文件=${model.files.size}")
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        AppLog.e("ModelDownloadService", "前台服务超时，取消下载")
        call?.cancel(); active?.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        AppLog.i("ModelDownloadService", "下载服务销毁")
        call?.cancel(); scope.cancel()
        AppLog.flush()
        super.onDestroy()
    }
}
