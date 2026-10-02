package io.github.nahanhhan.lecturerecording.logging

import android.util.Log
import io.github.nahanhhan.lecture.core.LogEventLevel
import io.github.nahanhhan.lecture.core.LogLevel
import io.github.nahanhhan.lecture.core.formatLogLine
import io.github.nahanhhan.lecture.core.logLevelAllows
import io.github.nahanhhan.lecture.core.logMessageBody
import io.github.nahanhhan.lecture.core.truncateLogLines
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 日志写入器：单线程队列异步写 [file]（`files/log/<进程>.log`），同时输出 logcat。
 * 级别 None 不输出；文件超 [maxBytes] 触发截断保留最近内容；[flush] 排空队列落盘。
 * 队列有界，写满即丢弃新日志：宁丢日志不阻塞业务线程。
 */
class LogWriter(
    private val file: File,
    private val source: String,
    private val configuredLevel: () -> LogLevel,
    private val maxBytes: Long = MAX_BYTES
) {
    companion object {
        /** 单个日志文件容量上限：5 MB。 */
        const val MAX_BYTES: Long = 5L * 1024 * 1024

        /** 写入队列容量上限。 */
        const val QUEUE_CAPACITY: Int = 1024
    }

    private val queue = LinkedBlockingQueue<Runnable>(QUEUE_CAPACITY)

    @Volatile
    private var running = true

    private val thread = Thread({ consume() }, "app-log-$source").apply {
        isDaemon = true
        start()
    }

    /** 异步记录一条日志；档位不允许时立即返回，队列满时丢弃，调用点不阻塞。 */
    fun log(level: LogEventLevel, tag: String, message: String) {
        val configured = configuredLevel()
        if (!logLevelAllows(configured, level)) return
        val tagged = "$tag $message"
        val line = formatLogLine(configured, System.currentTimeMillis(), level, source, tagged)
        val body = logMessageBody(configured, tagged)
        queue.offer(Runnable {
            writeLogcat(level, tag, body)
            appendFile(line)
        })
    }

    /** 排空写入队列，确保此前日志全部落盘。 */
    fun flush() {
        val latch = CountDownLatch(1)
        try {
            queue.put(Runnable { latch.countDown() })
            latch.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** flush 后停止写入线程。 */
    fun close() {
        flush()
        running = false
        thread.join(TimeUnit.SECONDS.toMillis(5))
    }

    private fun consume() {
        while (running || queue.isNotEmpty()) {
            val task = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
            runCatching { task.run() }
        }
    }

    private fun writeLogcat(level: LogEventLevel, tag: String, body: String) {
        val priority = when (level) {
            LogEventLevel.DEBUG -> Log.DEBUG
            LogEventLevel.INFO -> Log.INFO
            LogEventLevel.ERROR -> Log.ERROR
        }
        runCatching { Log.println(priority, tag, body) }
    }

    private fun appendFile(line: String) {
        try {
            file.parentFile?.mkdirs()
            file.appendText(line + "\n")
            if (file.length() > maxBytes) truncate()
        } catch (_: IOException) {
            // 日志写入失败不影响业务
        }
    }

    private fun truncate() {
        val kept = truncateLogLines(file.readLines(), maxBytes)
        file.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
    }
}
