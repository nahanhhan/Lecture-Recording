package io.github.nahanhhan.lecturerecording.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nahanhhan.lecturerecording.*
import io.github.nahanhhan.lecturerecording.cloud.CloudClient
import io.github.nahanhhan.lecturerecording.data.CloudSettings
import io.github.nahanhhan.lecturerecording.logging.AppLog
import io.github.nahanhhan.lecturerecording.models.*
import io.github.nahanhhan.lecture.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(activity: MainActivity, graph: AppGraph) {
    val initial = remember { graph.settings.cloud() }
    var base by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var key by remember { mutableStateOf(initial.key) }
    var strict by remember { mutableStateOf(initial.strict) }
    var glossary by remember { mutableStateOf(graph.settings.glossary) }
    var selectedModel by remember { mutableStateOf(graph.settings.modelId) }
    var source by remember { mutableStateOf(graph.settings.downloadSource) }
    var message by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var logLevel by remember { mutableStateOf(AppLog.level()) }
    var logKb by remember { mutableStateOf(currentLogKb(activity)) }
    var logMessage by remember { mutableStateOf("") }
    val download by graph.download.collectAsStateWithLifecycle()
    val recording by graph.recording.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("本地识别模型", style = MaterialTheme.typography.titleLarge)
        Text("模型下载完成后在手机本地转写。默认模型下载约 800 MB，安装后约 1.2 GB，请预留至少 3 GB 空间。若下载失败，请尝试切换下载源。", style = MaterialTheme.typography.bodyMedium)
        val switchEnabled = recording.lessonId == null && download.status !in setOf("downloading", "verifying")
        SingleChoiceSegmentedButtonRow(Modifier.alpha(if (switchEnabled) 1f else 0.38f)) {
            SegmentedButton(
                selected = source == DownloadSource.MODELSCOPE,
                onClick = { source = DownloadSource.MODELSCOPE; graph.settings.downloadSource = DownloadSource.MODELSCOPE },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = switchEnabled,
                icon = {},
                label = { Text("魔塔社区") }
            )
            SegmentedButton(
                selected = source == DownloadSource.GITHUB,
                onClick = { source = DownloadSource.GITHUB; graph.settings.downloadSource = DownloadSource.GITHUB },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = switchEnabled,
                icon = {},
                label = { Text("GitHub") }
            )
        }
        ModelCatalog.models.forEach { spec ->
            val installed = File(activity.filesDir, "models/${spec.id}/installed.json").exists()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        RadioButton(selected = selectedModel == spec.id, enabled = recording.lessonId == null, onClick = {
                            selectedModel = spec.id; graph.settings.modelId = spec.id
                        })
                        Column { Text(spec.label); Text(if (installed) "已安装" else "尚未安装", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (!installed) OutlinedButton(enabled = recording.lessonId == null && download.status !in setOf("downloading", "verifying"), onClick = {
                        ContextCompat.startForegroundService(activity, Intent(activity, ModelDownloadService::class.java).putExtra("model", spec.id))
                    }) { Text("下载 / 继续下载") }
                    if (download.modelId == spec.id && download.status != "idle") {
                        Text(when (download.status) { "verifying" -> "正在校验并安装"; "installed" -> "安装完成"; "error" -> download.error; else -> "${download.bytes / 1024 / 1024} / ${download.total / 1024 / 1024} MB" })
                        if (download.total > 0 && download.status == "downloading") LinearProgressIndicator(
                            progress = { (download.bytes.toFloat() / download.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        HorizontalDivider()
        Text("云端笔记整理", style = MaterialTheme.typography.titleLarge)
        Text("仅在手动整理时发送转写文字和选定照片。请选择支持图片与工具调用的模型。")
        OutlinedTextField(base, { base = it }, label = { Text("HTTPS 基础地址（通常以 /v1 结尾）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(model, { model = it }, label = { Text("模型名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(key, { key = it }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row { Checkbox(strict, { strict = it }); Text("严格参数模式", Modifier.padding(top = 14.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !testing, onClick = {
                try { graph.settings.saveCloud(CloudSettings(base, model, key, strict)); message = "配置已加密保存，请测试接口" }
                catch (exception: Exception) { message = "配置保存失败" }
            }) { Text("保存配置") }
            OutlinedButton(enabled = !testing && key.isNotBlank() && model.isNotBlank(), onClick = {
                testing = true; message = "正在测试文字、读图、工具调用和保存回执"
                scope.launch {
                    try {
                        graph.settings.saveCloud(CloudSettings(base, model, key, strict))
                        withContext(Dispatchers.IO) { testCloud(activity, graph) }
                        graph.settings.cloudTested = true; message = "接口测试通过，可以整理笔记"
                    } catch (exception: Exception) { message = "测试失败：${exception.message ?: "接口不兼容"}" }
                    finally { testing = false }
                }
            }) { Text(if (testing) "测试中…" else "测试接口") }
        }
        if (message.isNotBlank()) Text(message)
        HorizontalDivider()
        Text("课程术语", style = MaterialTheme.typography.titleLarge)
        Text("术语帮助云端理解已有课堂内容。用逗号或换行分隔。")
        OutlinedTextField(glossary, { glossary = it; graph.settings.glossary = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("例如 semaphore、Transformer") })
        HorizontalDivider()
        Text("后台运行", style = MaterialTheme.typography.titleLarge)
        val power = activity.getSystemService(PowerManager::class.java)
        Text(if (power.isIgnoringBatteryOptimizations(activity.packageName)) "系统电池优化已放行" else "系统电池优化仍启用，请结合手机后台设置检查")
        Text("录音期间请保留持续通知。在澎湃 OS / ColorOS 中允许后台运行，并检查省电模式。系统强制停止或关机后，再次打开可查看已保存资料。")
        OutlinedButton(onClick = { runCatching { activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }) { Text("打开电池设置") }
        OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))) }) { Text("打开应用系统设置") }
        HorizontalDivider()
        Text("日志", style = MaterialTheme.typography.titleLarge)
        Text("默认关闭。Info 记录关键事件并自动脱敏凭据；Debug 记录全部细节。导出后发给开发者排查问题。", style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = logLevel == LogLevel.NONE,
                onClick = { logLevel = LogLevel.NONE; AppLog.setLevel(LogLevel.NONE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                icon = {},
                label = { Text("None") }
            )
            SegmentedButton(
                selected = logLevel == LogLevel.INFO,
                onClick = { logLevel = LogLevel.INFO; AppLog.setLevel(LogLevel.INFO) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                icon = {},
                label = { Text("Info") }
            )
            SegmentedButton(
                selected = logLevel == LogLevel.DEBUG,
                onClick = { logLevel = LogLevel.DEBUG; AppLog.setLevel(LogLevel.DEBUG) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                icon = {},
                label = { Text("Debug") }
            )
        }
        if (logLevel == LogLevel.DEBUG) Text(
            "Debug 档会记录敏感信息（如 API Key 与转写内容），抓问题后请切回 Info 或 None。",
            color = MaterialTheme.colorScheme.error
        )
        Text("当前日志占用： ${logKb}KB")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                AppLog.flush()
                logFiles(activity).forEach { it.delete() }
                logKb = currentLogKb(activity)
                logMessage = "日志已清理"
            }) { Text("清理日志") }
            OutlinedButton(onClick = {
                AppLog.flush()
                val exported = exportLogs(activity)
                logKb = currentLogKb(activity)
                if (exported == null) {
                    logMessage = "无日志可导出"
                } else {
                    logMessage = "已生成 ${exported.name}"
                    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", exported)
                    activity.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "导出日志（可能含敏感信息）"))
                }
            }) { Text("导出日志") }
        }
        if (logMessage.isNotBlank()) Text(logMessage)
        Text("版本 ${BuildConfig.VERSION_NAME} · 资料保存在本机", style = MaterialTheme.typography.bodySmall)
    }
}

private fun logFiles(activity: MainActivity): List<File> =
    File(activity.filesDir, "log").listFiles { file -> file.isFile && file.extension == "log" }?.toList() ?: emptyList()

private fun currentLogKb(activity: MainActivity): Long =
    logFiles(activity).sumOf { it.length() } / 1024

/** 归并 `files/log` 目录下全部 `.log` 文件为 `cacheDir/exports/lecture-log_<时间戳>.log`（按行时间戳排序）；无内容返回 null。 */
private fun exportLogs(activity: MainActivity): File? {
    val lines = logFiles(activity).flatMap { it.readLines() }.sortedBy { it.take(23) }
    if (lines.isEmpty()) return null
    val exports = File(activity.cacheDir, "exports").apply { mkdirs() }
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    val file = File(exports, "lecture-log_$stamp.log")
    file.writeText(lines.joinToString("\n", postfix = "\n"))
    return file
}

private suspend fun testCloud(activity: MainActivity, graph: AppGraph) {
    val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap); canvas.drawColor(AndroidColor.WHITE)
    canvas.drawText("7", 80f, 185f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK; textSize = 160f })
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
    val image = ImageInput("image/png", Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
    val batch = Batch("capability_test", "batch_001", 1, "接口能力测试", emptyList(),
        listOf(Segment("test_text", 0, 1000, "本节只讨论所附图片中央的数字。")),
        listOf(Photo("test_photo", "ast_000000000_001.jpg", 0)))
    val definition = activity.assets.open("tool-definition.json").bufferedReader().use { protocolJson.parseToJsonElement(it.readText()).jsonObject }
    val settings = graph.settings.cloud()
    val initial = ChatProtocol.initial(settings.model, batch, listOf(image), definition, settings.strict)
    val messages = initial.getValue("messages").jsonArray.toMutableList()
    val user = messages[1].jsonObject
    val contents = user.getValue("content").jsonArray.toMutableList()
    contents.add(0, buildJsonObject { put("type", "text"); put("text", "这是接口测试。请读取图片中央的数字，并将该数字写入笔记标题或正文，引用照片编号。") })
    messages[1] = JsonObject(user + ("content" to JsonArray(contents)))
    val request = JsonObject(initial + ("messages" to JsonArray(messages)))
    val client = CloudClient(settings)
    val submission = ChatProtocol.submission(client.complete(request))
    val notes = NoteValidator.parseAndValidate(submission.arguments, batch)
    check((notes.title + notes.sections.joinToString { it.markdown }).contains("7") && notes.sections.any { "test_photo" in it.photoIds }) { "模型未正确读取测试图片" }
    val testFile = File(activity.cacheDir, "cloud-capability-test.json")
    testFile.writeText(submission.arguments)
    check(testFile.readText() == submission.arguments) { "测试结果未成功保存" }
    val receipt = ChatProtocol.receipt(submission.callId, true, batch.batchId, "capability_test")
    val final = client.complete(ChatProtocol.followup(request, submission.assistant, receipt))
    check(final.getValue("choices").jsonArray.first().jsonObject["finish_reason"]?.jsonPrimitive?.content == "stop") { "接口未完成工具回执确认" }
}
