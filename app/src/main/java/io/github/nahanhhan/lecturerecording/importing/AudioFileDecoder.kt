package io.github.nahanhhan.lecturerecording.importing

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.nahanhhan.lecture.core.AudioResampler
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decode compressed containers or raw WAV through the platform, with bounded buffers. */
class AudioFileDecoder {
    fun decode(file: File, cancelled: () -> Unit = {}, output: (ShortArray) -> Unit): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val samples = ShortArray(640)
        var used = 0
        var converter: AudioResampler? = null
        var rate = 0; var channels = 0; var encoding = AudioFormat.ENCODING_PCM_16BIT
        var pending = ByteArray(0)
        fun configure(format: MediaFormat) {
            val nextRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val nextChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(nextChannels in 1..8) { "音频声道数不支持" }
            if (converter != null) require(rate == nextRate && channels == nextChannels) { "音频中途改变了采样格式，请先转换为普通音频文件" }
            rate = nextRate; channels = nextChannels
            encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
            require(encoding in setOf(AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT)) { "音频采样精度不支持" }
            if (converter == null) converter = AudioResampler(rate) { sample ->
                samples[used++] = sample
                if (used == samples.size) { output(samples.copyOf()); used = 0; cancelled() }
            }
        }
        fun pcm(buffer: ByteBuffer) {
            val width = when (encoding) { AudioFormat.ENCODING_PCM_8BIT -> 1; AudioFormat.ENCODING_PCM_16BIT -> 2; AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3; else -> 4 }
            val bytes = ByteArray(pending.size + buffer.remaining())
            pending.copyInto(bytes); buffer.get(bytes, pending.size, buffer.remaining())
            val frameWidth = width * channels
            val complete = bytes.size - bytes.size % frameWidth
            val input = ByteBuffer.wrap(bytes, 0, complete).order(ByteOrder.LITTLE_ENDIAN)
            while (input.remaining() >= frameWidth) {
                var mono = 0f
                repeat(channels) {
                    mono += when (encoding) {
                        AudioFormat.ENCODING_PCM_8BIT -> ((input.get().toInt() and 255) - 128) / 128f
                        AudioFormat.ENCODING_PCM_16BIT -> input.short / 32768f
                        AudioFormat.ENCODING_PCM_FLOAT -> input.float
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                            val value = (input.get().toInt() and 255) or ((input.get().toInt() and 255) shl 8) or (input.get().toInt() shl 16)
                            value / 8388608f
                        }
                        else -> input.int / 2147483648f
                    }
                }
                converter!!.accept(mono / channels)
            }
            pending = bytes.copyOfRange(complete, bytes.size)
        }
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("文件中没有可读取的音频")
            require((0 until extractor.trackCount).none { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }) { "请选择音频文件，当前不支持导入视频" }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            if (mime == "audio/raw") {
                configure(format)
                val buffer = ByteBuffer.allocateDirect(1024 * 1024)
                while (true) {
                    cancelled(); buffer.clear()
                    val count = extractor.readSampleData(buffer, 0)
                    if (count < 0) break
                    require(count <= buffer.capacity()) { "音频数据块过大" }
                    buffer.position(0); buffer.limit(count); pcm(buffer); extractor.advance()
                }
            } else {
                val decoder = MediaCodec.createDecoderByType(mime); codec = decoder
                decoder.configure(format, null, null, 0); decoder.start()
                val info = MediaCodec.BufferInfo()
                var inputEnded = false; var outputEnded = false
                var lastProgress = android.os.SystemClock.elapsedRealtime()
                while (!outputEnded) {
                    cancelled()
                    if (!inputEnded) {
                        val index = decoder.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = requireNotNull(decoder.getInputBuffer(index)); buffer.clear()
                            val count = extractor.readSampleData(buffer, 0)
                            if (count < 0) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true
                            } else {
                                decoder.queueInputBuffer(index, 0, count, extractor.sampleTime.coerceAtLeast(0), 0)
                                extractor.advance(); lastProgress = android.os.SystemClock.elapsedRealtime()
                            }
                        }
                    }
                    val index = decoder.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) configure(decoder.outputFormat)
                    else if (index >= 0) {
                        try {
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                if (converter == null) configure(decoder.outputFormat)
                                val buffer = requireNotNull(decoder.getOutputBuffer(index))
                                buffer.position(info.offset); buffer.limit(info.offset + info.size); pcm(buffer)
                                lastProgress = android.os.SystemClock.elapsedRealtime()
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally { decoder.releaseOutputBuffer(index, false) }
                    }
                    check(android.os.SystemClock.elapsedRealtime() - lastProgress < 30_000) { "音频解码长时间没有进展，文件可能损坏" }
                }
            }
            check(pending.isEmpty()) { "音频数据不完整" }
            val resampler = requireNotNull(converter) { "音频文件为空或格式不支持" }
            resampler.finish()
            if (used > 0) output(samples.copyOf(used))
            check(resampler.outputFrames > 0) { "音频文件为空" }
            return resampler.outputFrames
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }; extractor.release()
        }
    }
}
