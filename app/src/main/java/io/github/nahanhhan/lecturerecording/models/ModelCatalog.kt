package io.github.nahanhhan.lecturerecording.models

data class ModelSpec(val id: String, val label: String, val archive: String, val bytes: Long,
    val sha256: String, val files: Set<String>,
    val modelscope: String, val modelscopeBranch: String = "master") {
    val url get() = urlFor(DownloadSource.GITHUB)

    /** 按下载源返回同一归档的直链：GitHub Releases 或魔塔社区镜像。 */
    fun urlFor(source: DownloadSource) = when (source) {
        DownloadSource.GITHUB -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$archive"
        DownloadSource.MODELSCOPE -> "https://modelscope.cn/models/$modelscope/resolve/$modelscopeBranch/$archive"
    }
}

object ModelCatalog {
    val models = listOf(
        ModelSpec("aed", "FireRedASR2 AED · 默认", "sherpa-onnx-fire-red-asr2-zh_en-int8-2026-02-26.tar.bz2",
            838589068, "43015b3f1643a5688b4821e8ed323473d38b798c4ec291471fe00df1bcfc4f1c",
            setOf("encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt"),
            "adaada88/sherpa-onnx-fire-red-asr2-aed-zh-en-int8"),
        ModelSpec("ctc", "FireRedASR2 CTC · 备用", "sherpa-onnx-fire-red-asr2-ctc-zh_en-int8-2026-02-25.tar.bz2",
            520516278, "1da8b737ecc5e29f36759a4460c754863e7c919a4ba325aea187331fbfc83274",
            setOf("model.int8.onnx", "tokens.txt"),
            "adaada88/sherpa-onnx-fire-red-asr2-ctc-zh-en-int8")
    )
    fun get(id: String) = models.first { it.id == id }
}
