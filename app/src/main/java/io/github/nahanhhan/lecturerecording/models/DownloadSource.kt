package io.github.nahanhhan.lecturerecording.models

/** 模型归档的下载源；[storage] 为 SharedPreferences 中持久化的字符串值。 */
enum class DownloadSource(val storage: String) {
    GITHUB("github"),
    MODELSCOPE("modelscope");

    companion object {
        /** 缺失或未知的持久化值一律回落到 [GITHUB]，保证升级后行为与现状一致。 */
        fun fromStorage(value: String?): DownloadSource =
            values().firstOrNull { it.storage == value } ?: GITHUB
    }
}