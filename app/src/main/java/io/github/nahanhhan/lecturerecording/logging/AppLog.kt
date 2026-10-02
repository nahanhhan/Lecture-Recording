package io.github.nahanhhan.lecturerecording.logging

import android.app.Application
import io.github.nahanhhan.lecture.core.LogEventLevel
import io.github.nahanhhan.lecture.core.LogLevel
import java.io.File

/** 全局日志入口：初始化后各组件用 [i]/[d]/[e] 记录，写入异步不阻塞调用点。 */
object AppLog {
    private val lock = Any()
    private var writer: LogWriter? = null
    private var store: LogLevelStore? = null

    /**
     * 按进程名推导来源标识：`:asr` 进程为 "asr"，主进程为 "main"。
     * 日志文件名为 `files/log/<来源>.log`，导出条目以此区分来源进程。
     */
    fun sourceNameOf(processName: String): String {
        val idx = processName.indexOf(':')
        return if (idx >= 0) processName.substring(idx + 1) else "main"
    }

    /** 两进程共用的初始化入口：按当前进程名确定来源标识。 */
    fun init(app: Application) {
        init(app.filesDir, sourceNameOf(Application.getProcessName()))
    }

    /**
     * 初始化日志：档位持久化于 `filesDir/log/level`，日志写入 `filesDir/log/<source>.log`
     * 并同步输出 logcat。主进程与 `:asr` 进程各自初始化，source 用于区分来源进程。
     */
    fun init(filesDir: File, source: String) {
        synchronized(lock) {
            val levelStore = LogLevelStore(File(filesDir, "log/level"))
            store = levelStore
            writer?.close()
            writer = LogWriter(File(filesDir, "log/$source.log"), source, configuredLevel = { levelStore.level() })
        }
    }

    /** 当前日志档位（未初始化或文件缺失时为默认 None）。 */
    fun level(): LogLevel = synchronized(lock) { store }?.level() ?: LogLevel.NONE

    /** 切换日志档位：本进程立即生效并持久化，`:asr` 进程 TTL（约 2s）内刷新。 */
    fun setLevel(level: LogLevel) {
        synchronized(lock) { store }?.set(level)
    }

    fun i(tag: String, message: String) = log(LogEventLevel.INFO, tag, message)

    fun d(tag: String, message: String) = log(LogEventLevel.DEBUG, tag, message)

    /** Debug 细节日志（按需构造消息）：仅 Debug 档求值 [message]，避免低档位下大正文拼接开销。 */
    fun d(tag: String, message: () -> String) {
        if (level() != LogLevel.DEBUG) return
        log(LogEventLevel.DEBUG, tag, message())
    }

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
