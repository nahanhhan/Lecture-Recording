package io.github.nahanhhan.lecture.core

import kotlin.math.sqrt

/** Light energy gate. Keeps a pre-roll and finalizes at silence or the 15-second cap. */
class SpeechSegmenter(private val rate: Int = 16000, private val threshold: Double = 0.009) {
    data class Window(val startSample: Long, val samples: ShortArray, val final: Boolean)
    private val pending = ArrayList<Short>()
    private val preRoll = ArrayDeque<Short>()
    private var start = 0L
    private var silence = 0
    private var lastPreview = 0
    fun accept(samples: ShortArray, offset: Long): List<Window> {
        val output = mutableListOf<Window>()
        val energy = sqrt(samples.sumOf { val f = it / 32768.0; f * f } / samples.size.coerceAtLeast(1))
        if (pending.isEmpty() && energy < threshold) {
            samples.forEach { preRoll.addLast(it) }
            while (preRoll.size > rate / 4) preRoll.removeFirst()
            return output
        }
        if (pending.isEmpty()) {
            start = (offset - preRoll.size).coerceAtLeast(0)
            pending.addAll(preRoll); preRoll.clear()
        }
        samples.forEach { pending += it }
        silence = if (energy < threshold) silence + samples.size else 0
        if (silence >= rate * 3 / 5 || pending.size >= rate * 15) {
            output += finish()!!
        } else if (pending.size - lastPreview >= rate * 2) {
            lastPreview = pending.size
            output += Window(start, pending.toShortArray(), false)
        }
        return output
    }
    fun finish(): Window? {
        if (pending.isEmpty()) { preRoll.clear(); return null }
        val result = Window(start, pending.toShortArray(), true)
        pending.clear(); preRoll.clear(); silence = 0; lastPreview = 0
        return result
    }
}
