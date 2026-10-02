package io.github.nahanhhan.lecturerecording.logging

import io.github.nahanhhan.lecture.core.LogLevel
import io.github.nahanhhan.lecture.core.logLevelOfWire
import io.github.nahanhhan.lecture.core.wireName
import java.io.File
import java.io.IOException

/**
 * 日志档位持久化：单行文件（临时文件 + rename 原子写），供主进程与 `:asr` 进程共享。
 * 读取带进程内 TTL（约 2s）缓存；切换立即生效于本进程，其他进程 TTL 内刷新。
 */
class LogLevelStore(private val file: File, private val ttlMs: Long = TTL_MS) {
    companion object {
        const val TTL_MS: Long = 2000L
    }

    private var cached: LogLevel = LogLevel.NONE
    private var cachedAtMs: Long? = null

    /** 当前档位：TTL 内用缓存，过期重读文件；文件缺失或损坏按默认 None。 */
    @Synchronized
    fun level(nowMs: Long = System.currentTimeMillis()): LogLevel {
        val loadedAt = cachedAtMs
        if (loadedAt != null && nowMs - loadedAt < ttlMs) return cached
        cached = readLevel()
        cachedAtMs = nowMs
        return cached
    }

    /** 持久化档位（临时文件 + rename 原子写），本进程立即生效。 */
    @Synchronized
    fun set(level: LogLevel) {
        writeLevel(level)
        cached = level
        cachedAtMs = System.currentTimeMillis()
    }

    private fun readLevel(): LogLevel {
        val text = try {
            if (file.isFile) file.readText() else null
        } catch (_: IOException) {
            null
        }
        return text?.let { logLevelOfWire(it) } ?: LogLevel.NONE
    }

    private fun writeLevel(level: LogLevel) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(level.wireName() + "\n")
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: IOException) {
            // 级别写入失败不影响业务
        }
    }
}
