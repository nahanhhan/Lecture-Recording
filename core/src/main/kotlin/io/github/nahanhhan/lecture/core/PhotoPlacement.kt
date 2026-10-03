package io.github.nahanhhan.lecture.core

data class PhotoArrangement(val sections: List<List<Photo>>, val remaining: List<Photo>)

/** Local timestamps associate original photos with note sections even without vision input. */
object PhotoPlacement {
    fun arrange(notes: List<Notes>, segments: List<Segment>, photos: List<Photo>): PhotoArrangement {
        val sections = notes.flatMap { it.sections }
        val assignments = List(sections.size) { mutableListOf<Photo>() }
        val remaining = mutableListOf<Photo>()
        val sectionBySegment = mutableMapOf<String, Int>()
        sections.forEachIndexed { index, section ->
            section.sourceSegmentIds.forEach { sectionBySegment.putIfAbsent(it, index) }
        }
        val referenced = segments.filter { it.id in sectionBySegment }
        photos.distinctBy { it.id }.sortedBy { it.audioTimeMs }.forEach { photo ->
            val explicit = sections.indexOfFirst { photo.id in it.photoIds }.takeIf { it >= 0 }
            val source = referenced.filter { photo.audioTimeMs in it.startMs..it.endMs }.maxByOrNull { it.startMs }
                ?: referenced.filter { it.startMs <= photo.audioTimeMs }.maxByOrNull { it.startMs }
                ?: referenced.minByOrNull { it.startMs }
            val index = explicit ?: source?.let { sectionBySegment[it.id] }
            if (index != null) assignments[index] += photo else remaining += photo
        }
        return PhotoArrangement(assignments, remaining)
    }
}
