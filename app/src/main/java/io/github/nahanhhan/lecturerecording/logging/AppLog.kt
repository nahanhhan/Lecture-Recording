package io.github.nahanhhan.lecturerecording.logging

import io.github.nahanhhan.lecture.core.LogEventLevel
import io.github.nahanhhan.lecture.core.LogLevel
import java.io.File

/** 全局日志入口：初始化后各组件用 [i]/[d]/[e] 记录，写入异步不阻塞调用点。 */
object AppLog {
    private val lock = Any()
    private var writer: LogWriter? = null

    /**
     * 初始化日志写入器：写入 `filesDir/log/<source>.log` 并同步输出 logcat。
     * [levelProvider] 返回当前日志档位（None 全拒、Info 脱敏、Debug 全量）。
     */
    fun init(filesDir: File, source: String, levelProvider: () -> LogLevel) {
        synchronized(lock) {
            writer?.close()
            writer = LogWriter(File(filesDir, "log/$source.log"), source, levelProvider)
        }
    }

    fun i(tag: String, message: String) = log(LogEventLevel.INFO, tag, message)

    fun d(tag: String, message: String) = log(LogEventLevel.DEBUG, tag, message)

    fun e(tag: String, message: String, error: Throwable? = null) =
        log(LogEventLevel.ERROR, tag, if (error == null) message else "$message: $error")

    /** 排空队列确保日志落盘；服务销毁/进程退出时调用。 */
    fun flush() {
        synchronized(lock) { writer }?.flush()
    }

    private fun log(level: LogEventLevel, tag: String, message: String) {
        synchronized(lock) { writer }?.log(level, tag, message)
    }
}
