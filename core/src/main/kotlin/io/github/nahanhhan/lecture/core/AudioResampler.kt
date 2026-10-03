package io.github.nahanhhan.lecture.core

import kotlin.math.*

/** Streaming mono resampler, with a phase-bank low-pass filter when downsampling. */
class AudioResampler(private val sourceRate: Int, private val targetRate: Int = 16000,
    private val output: (Short) -> Unit) {
    init { require(sourceRate in 8000..192000 && targetRate in 8000..48000) }
    private val radius = 16
    private val ring = FloatArray(128)
    private val filters = Array(128) { phase ->
        val fraction = phase / 128.0
        val cutoff = minOf(1.0, targetRate.toDouble() / sourceRate) * 0.47
        val weights = FloatArray(radius * 2 + 1) { tap ->
            val distance = tap - radius - fraction
            val sinc = if (abs(distance) < 1e-9) 2 * cutoff else sin(2 * PI * cutoff * distance) / (PI * distance)
            (sinc * (0.5 + 0.5 * cos(PI * distance / (radius + 1)))).toFloat()
        }
        val sum = weights.sum()
        weights.map { it / sum }.toFloatArray()
    }
    private var inputFrames = 0L
    var outputFrames = 0L
        private set
    private var finished = false
    fun accept(sample: Float) {
        check(!finished)
        ring[(inputFrames % ring.size).toInt()] = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
        inputFrames++
        if (sourceRate == targetRate) { emit(sample); return }
        while (position() + radius < inputFrames) resample()
    }
    fun finish() {
        check(!finished); finished = true
        if (sourceRate == targetRate) return
        val total = (inputFrames * targetRate + sourceRate / 2) / sourceRate
        while (outputFrames < total) resample()
    }
    private fun position() = outputFrames.toDouble() * sourceRate / targetRate
    private fun resample() {
        val position = position()
        val center = floor(position).toLong()
        val filter = filters[((position - center) * filters.size).toInt().coerceIn(filters.indices)]
        var value = 0f
        filter.forEachIndexed { tap, weight ->
            val index = center + tap - radius
            if (index >= 0 && index < inputFrames) value += ring[(index % ring.size).toInt()] * weight
        }
        emit(value)
    }
    private fun emit(value: Float) {
        val safe = if (value.isFinite()) value else 0f
        output((safe * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        outputFrames++
    }
}
