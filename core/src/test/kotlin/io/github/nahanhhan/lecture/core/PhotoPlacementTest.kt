package io.github.nahanhhan.lecture.core

import kotlin.test.*

class PhotoPlacementTest {
    private val segments = listOf(Segment("s1", 1000, 5000, "第一段"), Segment("s2", 10000, 20000, "第二段"))
    private val notes = listOf(Notes("笔记", listOf(
        NoteSection("第一小节", "第一段整理", listOf("s1"), emptyList()),
        NoteSection("第二小节", "第二段整理", listOf("s2"), emptyList()))))
    private fun photo(id: String, time: Long) = Photo(id, PhotoFilename.create(time, 1), time)
    @Test fun timestampsPlacePhotosInsideCorrespondingTextSectionsWithoutVision() {
        val arrangement = PhotoPlacement.arrange(notes, segments,
            listOf(photo("later", 18000), photo("first", 3000), photo("earlier", 12000)))
        assertEquals(listOf("first"), arrangement.sections[0].map { it.id })
        assertEquals(listOf("earlier", "later"), arrangement.sections[1].map { it.id })
        assertTrue(arrangement.remaining.isEmpty())
    }
    @Test fun speechGapsUsePrecedingSourceAndEdgesUseNearestAvailableSource() {
        val arrangement = PhotoPlacement.arrange(notes, segments,
            listOf(photo("before", 0), photo("gap", 7000), photo("after", 30000)))
        assertEquals(listOf("before", "gap"), arrangement.sections[0].map { it.id })
        assertEquals(listOf("after"), arrangement.sections[1].map { it.id })
    }
    @Test fun explicitModelReferencesArePreservedAndEachPhotoAppearsOnlyOnce() {
        val explicit = notes.map { it.copy(sections = it.sections.map { section -> section.copy(photoIds = listOf("p1")) }) }
        val p1 = photo("p1", 18000)
        val arrangement = PhotoPlacement.arrange(explicit, segments, listOf(p1, p1))
        assertEquals(listOf("p1"), arrangement.sections[0].map { it.id })
        assertTrue(arrangement.sections[1].isEmpty() && arrangement.remaining.isEmpty())
    }
    @Test fun photosWithNoMatchingTextSourcesRemainAvailableWithoutInventingAssociation() {
        val arrangement = PhotoPlacement.arrange(notes, listOf(Segment("other", 0, 1000, "另一堂课")), listOf(photo("p1", 10)))
        assertTrue(arrangement.sections.all { it.isEmpty() })
        assertEquals(listOf("p1"), arrangement.remaining.map { it.id })
    }
    @Test fun topicReorderingUsesActualSourceIdsRatherThanRenderedSectionOrder() {
        val reordered = notes.map { it.copy(sections = it.sections.reversed()) }
        val arrangement = PhotoPlacement.arrange(reordered, segments, listOf(photo("p1", 3000)))
        assertTrue(arrangement.sections[0].isEmpty())
        assertEquals(listOf("p1"), arrangement.sections[1].map { it.id })
    }
}
