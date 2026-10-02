package io.github.nahanhhan.lecture.core

import kotlinx.serialization.decodeFromString

object NoteValidator {
    fun parseAndValidate(arguments: String, batch: Batch): Notes {
        require(arguments.length <= 200_000) { "笔记参数过长" }
        val notes = protocolJson.decodeFromString<Notes>(arguments)
        require(notes.title.isNotBlank() && notes.title.length <= 200) { "笔记标题无效" }
        require(notes.sections.isNotEmpty() && notes.sections.size <= 100) { "笔记分节为空或过多" }
        val segmentIds = batch.segments.map { it.id }.toSet()
        val photoIds = batch.photos.map { it.id }.toSet()
        batch.photos.forEach { require(PhotoFilename.parse(it.filename).first == it.audioTimeMs) { "照片时间不一致" } }
        notes.sections.forEach { section ->
            require(section.heading.isNotBlank() && section.markdown.isNotBlank()) { "笔记小标题或正文为空" }
            require(section.sourceSegmentIds.isNotEmpty() || section.photoIds.isNotEmpty()) { "笔记缺少课堂来源" }
            require(section.sourceSegmentIds.all { it in segmentIds }) { "引用了当前批次之外的段落" }
            require(section.photoIds.all { it in photoIds }) { "引用了当前批次之外的照片" }
            require(!Regex("!\\[[^]]*]\\(").containsMatchIn(section.markdown)) { "图片应通过照片编号关联" }
            require(!Regex("(?i)<\\s*(script|iframe|img|object|embed)").containsMatchIn(section.markdown)) { "正文包含不允许的 HTML" }
        }
        return notes
    }
}
