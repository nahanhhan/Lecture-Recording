package io.github.nahanhhan.lecturerecording.recording

import io.github.nahanhhan.lecturerecording.logging.AppLog
import java.io.File
import java.io.RandomAccessFile

class WavFile(val file: File) : AutoCloseable {
    private val output = RandomAccessFile(file, "rw")
    var samples = 0L
        private set
    init { output.setLength(0); header(output, 0); output.seek(44) }
    fun append(buffer: ShortArray, count: Int) {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) { bytes[i * 2] = buffer[i].toByte(); bytes[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte() }
        output.write(bytes); samples += count
    }
    fun checkpoint() {
        val position = output.filePointer
        header(output, samples); output.seek(position); output.fd.sync()
    }
    override fun close() { checkpoint(); output.close() }
    companion object {
        private fun header(output: RandomAccessFile, samples: Long) {
            fun int(value: Int) = output.write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
            fun short(value: Int) = output.write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
            output.seek(0); output.writeBytes("RIFF"); int((samples * 2 + 36).toInt()); output.writeBytes("WAVEfmt ")
            int(16); short(1); short(1); int(16000); int(32000); short(2); short(16); output.writeBytes("data"); int((samples * 2).toInt())
        }
        fun repair(file: File): Long = RandomAccessFile(file, "rw").use { output ->
            if (output.length() < 44) return 0
            val samples = (output.length() - 44) / 2
            output.setLength(44 + samples * 2); header(output, samples); output.fd.sync()
            AppLog.i("WavFile", "修复文件头 file=${file.name} 样本=$samples")
            samples
        }
        fun write(file: File, samples: ShortArray) { WavFile(file).use { it.append(samples, samples.size) } }
        fun read(file: File): FloatArray = RandomAccessFile(file, "r").use { input ->
            require(input.length() in 44..(44L + 16000L * 16 * 2)) { "识别音频长度无效" }
            input.seek(44)
            FloatArray(((input.length() - 44) / 2).toInt()) {
                val low = input.readUnsignedByte(); val high = input.readUnsignedByte()
                ((high shl 8) or low).toShort() / 32768f
            }
        }
    }
}
