package io.github.nahanhhan.lecturerecording.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.nahanhhan.lecturerecording.AppGraph
import kotlinx.coroutines.*

@Composable fun DeleteRecordingsDialog(graph: AppGraph, ids: List<String>, onDismiss: () -> Unit,
    beforeDelete: () -> Unit = {}, onDeleted: () -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(if (ids.size == 1) "删除这条录音？" else "删除 ${ids.size} 条录音？") },
        text = { Text(error.ifBlank { "录音、照片、原始转写和笔记将一起删除，删除后无法恢复。" }) },
        confirmButton = { TextButton(enabled = !deleting, onClick = {
            deleting = true; error = ""
            try { beforeDelete() } catch (exception: Exception) { error = exception.message ?: "无法停止回听"; deleting = false; return@TextButton }
            graph.scope.launch {
                try { graph.recordings.delete(ids); withContext(Dispatchers.Main) { onDeleted() } }
                catch (exception: Exception) { withContext(Dispatchers.Main) { error = exception.message ?: "删除失败，请重试"; deleting = false } }
            }
        }) { Text(if (deleting) "正在删除…" else "删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(enabled = !deleting, onClick = onDismiss) { Text("取消") } })
}
