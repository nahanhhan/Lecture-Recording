package io.github.nahanhhan.lecturerecording.export

import io.github.nahanhhan.lecture.core.*
import io.github.nahanhhan.lecturerecording.data.*
import io.github.nahanhhan.lecturerecording.logging.AppLog
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension

object NotesRenderer {
    fun markdown(notes: List<Notes>, segments: List<SegmentEntity>, photos: List<PhotoEntity>): String {
        if (notes.isEmpty()) return ""
        val segmentMap = segments.associateBy { it.id }
        val photoMap = photos.associateBy { it.id }
        val used = mutableSetOf<String>()
        val result = buildString {
            append("# ").append(notes.first().title).append("\n\n")
            notes.forEach { batch -> batch.sections.forEach { section ->
                append("## ").append(section.heading).append("\n\n").append(section.markdown).append("\n\n")
                val sources = section.sourceSegmentIds.mapNotNull { segmentMap[it] }
                if (sources.isNotEmpty()) append("来源：").append(sources.joinToString("、") { formatTime(it.startMs) }).append("\n\n")
                section.photoIds.mapNotNull { photoMap[it] }.forEach { photo ->
                    append("![课堂照片 ").append(formatTime(photo.audioTimeMs)).append("](").append(photo.filename).append(")\n\n")
                    used += photo.id
                }
            } }
            val unused = photos.filter { it.id !in used }
            if (unused.isNotEmpty()) {
                append("## 其他课堂照片\n\n")
                unused.forEach { append("![课堂照片 ").append(formatTime(it.audioTimeMs)).append("](").append(it.filename).append(")\n\n") }
            }
        }
        AppLog.d("NotesRenderer") { "生成 Markdown ${result.length} 字" }
        return result
    }
    fun html(markdown: String, title: String): String {
        val extensions = listOf(TablesExtension.create())
        val parser = Parser.builder().extensions(extensions).build()
        val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true).build()
        val body = renderer.render(parser.parse(markdown))
        val page = """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src file: data:; style-src 'unsafe-inline' file:; font-src file:; script-src file: 'nonce-lecture';">
            <title>${escape(title)}</title>
            <link rel="stylesheet" href="file:///android_asset/vendor/katex/katex.min.css">
            <style>body{font-family:sans-serif;color:#263c34;background:white;line-height:1.8;margin:24px;overflow-wrap:anywhere}h1{font-size:28px}h2{margin-top:32px;font-size:21px}img{max-width:100%;max-height:900px;object-fit:contain}pre{background:#f3f5f3;padding:16px;white-space:pre-wrap}code{font-family:monospace}table{border-collapse:collapse;width:100%;margin:16px 0}td,th{border:1px solid #ccd6d1;padding:8px}.katex-display{overflow-x:auto;overflow-y:hidden}a{color:#406f61}@page{size:A4;margin:18mm}@media print{body{margin:0;font-size:11pt}h1,h2,h3{break-after:avoid}img,table,pre{break-inside:avoid}a{text-decoration:none;color:inherit}}</style>
            <script src="file:///android_asset/vendor/katex/katex.min.js"></script>
            <script src="file:///android_asset/vendor/katex/auto-render.min.js"></script>
            </head><body>$body<script nonce="lecture">if(window.renderMathInElement){renderMathInElement(document.body,{delimiters:[{left:'$$',right:'$$',display:true},{left:'\\[',right:'\\]',display:true},{left:'$',right:'$',display:false},{left:'\\(',right:'\\)',display:false}],throwOnError:false});}</script></body></html>""".trimIndent()
        AppLog.d("NotesRenderer") { "生成 HTML ${page.length} 字 标题=$title" }
        return page
    }
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
