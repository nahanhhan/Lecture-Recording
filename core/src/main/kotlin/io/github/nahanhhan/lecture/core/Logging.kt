package io.github.nahanhhan.lecture.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 日志档位：用户可选的三档，控制记录范围。 */
enum class LogLevel { NONE, INFO, DEBUG }

/** 日志条目级别：写入点的严重程度。 */
enum class LogEventLevel { DEBUG, INFO, ERROR }

/** [configured] 档位下 [event] 级别的条目是否记录：None 全拒、Info 收关键事件（Info/Error）、Debug 全收。 */
fun logLevelAllows(configured: LogLevel, event: LogEventLevel): Boolean = when (configured) {
    LogLevel.NONE -> false
    LogLevel.INFO -> event == LogEventLevel.INFO || event == LogEventLevel.ERROR
    LogLevel.DEBUG -> true
}

/** 级别持久化单行文件使用的档位名（小写）。 */
fun LogLevel.wireName(): String = name.lowercase()

/** 解析级别持久化单行文件中的档位名；无法识别返回 null。 */
fun logLevelOfWire(text: String): LogLevel? = when (text.trim().lowercase()) {
    "none" -> LogLevel.NONE
    "info" -> LogLevel.INFO
    "debug" -> LogLevel.DEBUG
    else -> null
}

private val credentialKeyValue = Regex(
    "(?i)([\\w.\\-]*(?:api[_-]?key|key|token|secret|passwd|password|pwd|authorization|auth|credential)[\\w.\\-]*\\s*[=:]\\s*)" +
        "(\"[^\"]*\"|'[^']*'|[^\\s,;&\"']+)"
)
private val credentialBearer = Regex("(?i)(\\b(?:bearer|basic)\\s+)([A-Za-z0-9\\-._~+/]+=*)")
private val credentialSk = Regex("\\bsk-[A-Za-z0-9_\\-]{8,}")

/** 凭据脱敏掩码：把 `key=`/`token=`/`Bearer …`/`sk-…` 等凭据值替换为 `***`。 */
fun maskCredentials(text: String): String {
    var out = credentialKeyValue.replace(text) { it.groupValues[1] + "***" }
    out = credentialBearer.replace(out) { it.groupValues[1] + "***" }
    out = credentialSk.replace(out) { "sk-***" }
    return out
}

private val timestampFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

/** 日志行时间戳（固定宽度、可按字典序排序，供导出归并使用）。 */
fun formatLogTimestamp(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    timestampFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

/**
 * 格式化日志行：`<时间戳> <级别>/<来源> <消息>`。
 * [configured] 为 Info 档时对消息做凭据掩码；Debug 档保留原文以便还原现场。
 */
fun formatLogLine(
    configured: LogLevel,
    epochMs: Long,
    level: LogEventLevel,
    source: String,
    message: String,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val letter = when (level) {
        LogEventLevel.DEBUG -> "D"
        LogEventLevel.INFO -> "I"
        LogEventLevel.ERROR -> "E"
    }
    val body = if (configured == LogLevel.INFO) maskCredentials(message) else message
    return "${formatLogTimestamp(epochMs, zone)} $letter/$source $body"
}

/**
 * 日志容量截断：总量超过 [maxBytes] 时丢弃最旧内容，保留最近内容（列表尾部）。
 * 每行按 UTF-8 字节数加换行符计入总量；最新一行自身超限时仍保留该行。
 */
fun truncateLogLines(lines: List<String>, maxBytes: Long): List<String> {
    require(maxBytes > 0) { "上限必须为正数" }
    val kept = ArrayDeque<String>()
    var total = 0L
    for (line in lines.asReversed()) {
        val cost = line.toByteArray(Charsets.UTF_8).size + 1L
        if (total + cost > maxBytes && kept.isNotEmpty()) break
        kept.addFirst(line)
        total += cost
        if (total > maxBytes) break
    }
    return kept.toList()
}
