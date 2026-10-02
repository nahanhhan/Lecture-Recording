package io.github.nahanhhan.lecturerecording.ui

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import io.github.nahanhhan.lecturerecording.*
import io.github.nahanhhan.lecturerecording.data.PhotoEntity
import io.github.nahanhhan.lecture.core.PhotoFilename
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@Composable fun CameraScreen(context: Context, graph: AppGraph, lessonId: String, onClose: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    val recording by graph.recording.collectAsStateWithLifecycle()
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(0) }
    val preview = remember { PreviewView(context) }
    DisposableEffect(lifecycle) {
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) try {
                val camera = future.get(); provider = camera
                val cameraPreview = Preview.Builder().build().apply { surfaceProvider = preview.surfaceProvider }
                camera.unbindAll(); camera.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, capture)
            } catch (exception: Exception) { error = exception.message ?: "无法打开相机" }
        }, executor)
        onDispose { disposed = true; provider?.unbindAll() }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(factory = { preview }, modifier = Modifier.weight(1f).fillMaxWidth())
        if (error.isNotBlank()) Text(error, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error)
        Text("已拍摄 $saved 张 · 照片位置按按下快门时的录音时间保存", Modifier.padding(horizontal = 20.dp))
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose, enabled = !busy) { Text("返回录音") }
            Button(enabled = provider != null && !busy && recording.lessonId == lessonId, onClick = {
                val audioMs = graph.recording.value.samples * 1000 / 16000
                val capturedAt = System.currentTimeMillis()
                busy = true; error = ""
                graph.scope.launch {
                    var file: File? = null
                    try {
                        val pair = graph.database.withTransaction {
                            val lesson = requireNotNull(graph.dao.lesson(lessonId))
                            var sequence = lesson.photoSequence + 1
                            var target = File(graph.lessonDir(lessonId), PhotoFilename.create(audioMs, sequence))
                            while (!target.createNewFile()) { sequence++; target = File(graph.lessonDir(lessonId), PhotoFilename.create(audioMs, sequence)) }
                            graph.dao.setSequence(lessonId, sequence)
                            target to sequence
                        }
                        file = pair.first
                        withContext(Dispatchers.Main) {
                            capture.takePicture(ImageCapture.OutputFileOptions.Builder(pair.first).build(), executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        graph.scope.launch {
                                            try {
                                                graph.database.withTransaction {
                                                    graph.dao.putPhoto(PhotoEntity(UUID.randomUUID().toString(), lessonId, pair.first.name, audioMs, capturedAt, pair.second))
                                                    graph.dao.revise(lessonId)
                                                }
                                                withContext(Dispatchers.Main) { busy = false; saved++ }
                                            } catch (exception: Exception) {
                                                pair.first.delete()
                                                withContext(Dispatchers.Main) { busy = false; error = "照片保存失败" }
                                            }
                                        }
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        pair.first.delete(); busy = false; error = exception.message ?: "拍照失败"
                                    }
                                })
                        }
                    } catch (exception: Exception) {
                        file?.delete(); withContext(Dispatchers.Main) { busy = false; error = exception.message ?: "无法保存照片" }
                    }
                }
            }) { Text(if (busy) "正在保存" else "拍照") }
        }
    }
}
