package io.github.nahanhhan.lecture.core

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
