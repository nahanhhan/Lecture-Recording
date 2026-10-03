package io.github.nahanhhan.lecturerecording

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nahanhhan.lecturerecording.logging.AppLog
import io.github.nahanhhan.lecturerecording.recording.RecordingService
import io.github.nahanhhan.lecturerecording.ui.*
import io.github.nahanhhan.lecture.core.formatTime
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    val graph get() = (application as LectureApp).graph
    private var pendingRecording: Intent? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val intent = pendingRecording; pendingRecording = null
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && intent != null) {
            AppLog.i("MainActivity", "权限已授予，启动录音服务")
            ContextCompat.startForegroundService(this, intent)
        } else {
            AppLog.e("MainActivity", "麦克风权限被拒绝，无法录音")
            Toast.makeText(this, "需要麦克风权限才能录音", Toast.LENGTH_LONG).show()
        }
    }
    fun beginRecording(intent: Intent) {
        AppLog.i("MainActivity", "请求开始录音 action=${intent.action}")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            ContextCompat.startForegroundService(this, intent)
        else {
            AppLog.i("MainActivity", "麦克风权限未授予，请求权限")
            pendingRecording = intent
            permissions.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("MainActivity", "页面创建")
        setContent { LectureTheme { LectureRoot(this, graph) } }
    }
}

@Composable fun LectureTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF406F61), secondary = Color(0xFFB87342),
        background = Color(0xFFF8F7F2), surface = Color(0xFFF8F7F2), surfaceContainer = Color(0xFFEEEEE6)), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun LectureRoot(activity: MainActivity, graph: AppGraph) {
    var page by rememberSaveable { mutableStateOf("home") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var newRecording by remember { mutableStateOf(false) }
    val recording by graph.recording.collectAsStateWithLifecycle()
    val lessons by graph.dao.observeLessons().collectAsStateWithLifecycle(initialValue = emptyList())
    BackHandler(page != "home") { page = "home" }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (page == "settings") "设置" else if (page == "detail") "课堂记录" else "录课") },
            navigationIcon = { if (page != "home") IconButton(onClick = { page = "home" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = { if (page != "settings") IconButton(onClick = { page = "settings" }) { Icon(Icons.Default.Settings, "设置") } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "settings" -> SettingsScreen(activity, graph)
                "detail" -> selectedId?.let { DetailScreen(activity, graph, it) }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3ECE5)), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("把课堂留在身边", style = MaterialTheme.typography.headlineSmall)
                                Text("录下讲解，拍下板书。课后整理成可回听的图文笔记。", style = MaterialTheme.typography.bodyLarge)
                                Button(onClick = {
                                    if (recording.lessonId != null) { selectedId = recording.lessonId; page = "detail" }
                                    else newRecording = true
                                }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (recording.lessonId != null) "回到当前录音" else "开始课堂录音") }
                            }
                        }
                    }
                    item { Text("我的课堂 · ${lessons.size}", style = MaterialTheme.typography.titleMedium) }
                    if (lessons.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("还没有课堂记录", style = MaterialTheme.typography.titleLarge)
                            Text("开始录音后，音频、文字和照片会保存在这台手机上。")
                        }
                    }
                    items(lessons, key = { it.id }) { lesson ->
                        Card(Modifier.fillMaxWidth().clickable { selectedId = lesson.id; page = "detail" }) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(lesson.title, style = MaterialTheme.typography.titleLarge)
                                Text(listOf(lesson.course, SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(lesson.createdAt)).filter { it.isNotBlank() }.joinToString(" · "))
                                Text("${formatTime(lesson.samples * 1000 / 16000)} · ${statusLabel(lesson.status)}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
    if (newRecording) {
        var title by remember { mutableStateOf("课堂录音") }
        var course by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { newRecording = false }, title = { Text("新建课堂") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true)
                OutlinedTextField(course, { course = it }, label = { Text("课程名称") }, singleLine = true)
                Text("模型未安装时也可以录音；安装后可处理未完成的短段转写。", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(onClick = {
                activity.beginRecording(Intent(activity, RecordingService::class.java).setAction(RecordingService.START).putExtra("title", title).putExtra("course", course))
                newRecording = false
            }) { Text("开始") } }, dismissButton = { TextButton(onClick = { newRecording = false }) { Text("取消") } })
    }
}

fun statusLabel(status: String): String = when (status) {
    "recording" -> "正在录音"; "paused" -> "已暂停"; "processing" -> "正在完成转写"
    "completed" -> "已完成"; "interrupted" -> "已中断，可恢复"; "running" -> "正在整理"
    "waiting" -> "等待继续"; "pending" -> "待转写"; "ready" -> "已转写"; "error" -> "待核对"; else -> status
}
