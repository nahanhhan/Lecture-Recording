package io.github.nahanhhan.lecturerecording.asr

import android.app.Service
import android.content.Intent
import android.os.*
import com.k2fsa.sherpa.onnx.*
import io.github.nahanhhan.lecturerecording.recording.WavFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class AsrService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var loadedModel = ""
    private val messenger by lazy { Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val data = Bundle(message.data)
            val reply = message.replyTo ?: return
            scope.launch {
                lock.withLock {
                    val result = Bundle().apply { putString("token", data.getString("token")) }
                    try {
                        val audio = File(requireNotNull(data.getString("path"))).canonicalFile
                        require(audio.path.startsWith(File(filesDir, "lessons").canonicalPath + File.separator))
                        val modelId = data.getString("model") ?: "aed"
                        require(modelId in setOf("aed", "ctc"))
                        val folder = File(filesDir, "models/$modelId")
                        require(File(folder, "installed.json").exists()) { "识别模型尚未准备好" }
                        if (loadedModel != modelId || recognizer == null) {
                            recognizer?.release()
                            val model = OfflineModelConfig(tokens = File(folder, "tokens.txt").path,
                                numThreads = 4, provider = "cpu", debug = false)
                            if (modelId == "aed") model.fireRedAsr = OfflineFireRedAsrModelConfig(
                                File(folder, "encoder.int8.onnx").path, File(folder, "decoder.int8.onnx").path)
                            else model.fireRedAsrCtc = OfflineFireRedAsrCtcModelConfig(File(folder, "model.int8.onnx").path)
                            recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = model))
                            loadedModel = modelId
                        }
                        val engine = requireNotNull(recognizer)
                        val stream = engine.createStream()
                        try {
                            stream.acceptWaveform(WavFile.read(audio), 16000)
                            engine.decode(stream)
                            result.putString("text", engine.getResult(stream).text)
                        } finally { stream.release() }
                    } catch (error: Exception) { result.putString("error", error.message ?: "识别失败") }
                    runCatching { reply.send(Message.obtain(null, 1).apply { this.data = result }) }
                }
            }
        }
    }) }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onDestroy() {
        scope.cancel()
        // Release on the serial recognition lane, after any native call has returned.
        CoroutineScope(Dispatchers.IO).launch { lock.withLock { recognizer?.release(); recognizer = null } }
        super.onDestroy()
    }
}
