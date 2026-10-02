package io.github.nahanhhan.lecture.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val protocolJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }

@Serializable
data class Segment(val id: String, @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long, val text: String)

@Serializable
data class Photo(val id: String, val filename: String,
    @SerialName("audio_time_ms") val audioTimeMs: Long,
    @SerialName("audio_time_label") val audioTimeLabel: String = formatTime(audioTimeMs),
    @SerialName("input_image_index") val inputImageIndex: Int = 0)

@Serializable
data class Batch(@SerialName("lesson_id") val lessonId: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("source_revision") val sourceRevision: Int,
    @SerialName("course_name") val courseName: String, val glossary: List<String>,
    val segments: List<Segment>, val photos: List<Photo>)

@Serializable
data class Notes(val title: String, val sections: List<NoteSection>)

@Serializable
data class NoteSection(val heading: String, val markdown: String,
    @SerialName("source_segment_ids") val sourceSegmentIds: List<String>,
    @SerialName("photo_ids") val photoIds: List<String>)

fun formatTime(ms: Long): String {
    require(ms >= 0)
    val seconds = ms / 1000
    return "%02d:%02d.%03d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60, ms % 1000)
}

object PhotoFilename {
    private val pattern = Regex("ast_(\\d{9,})_(\\d{3,})\\.jpg")
    fun create(audioMs: Long, sequence: Long): String {
        require(audioMs >= 0 && sequence > 0)
        return "ast_${audioMs.toString().padStart(9, '0')}_${sequence.toString().padStart(3, '0')}.jpg"
    }
    fun parse(filename: String): Pair<Long, Long> {
        val match = requireNotNull(pattern.matchEntire(filename)) { "照片文件名不符合约定" }
        val time = match.groupValues[1].toLong()
        val sequence = match.groupValues[2].toLong()
        require(sequence > 0)
        return time to sequence
    }
}

class SampleClock(private val rate: Int = 16000, initialSamples: Long = 0) {
    init { require(rate > 0 && initialSamples >= 0) }
    var samples: Long = initialSamples
        private set
    val milliseconds: Long get() = samples * 1000 / rate
    fun advance(count: Int) { require(count >= 0); samples += count }
}
