package io.github.nahanhhan.lecturerecording.export

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.*
import io.github.nahanhhan.lecturerecording.*
import kotlinx.coroutines.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporter {
    suspend fun markdownZip(graph: AppGraph, lessonId: String, markdown: String): File = withContext(Dispatchers.IO) {
        val directory = File(graph.app.cacheDir, "exports").apply { mkdirs() }
        val output = File(directory, "lecture_${lessonId}.zip")
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("notes.md")); zip.write(markdown.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            graph.dao.photos(lessonId).forEach { photo ->
                val file = File(graph.lessonDir(lessonId), photo.filename)
                if (file.exists()) { zip.putNextEntry(ZipEntry(photo.filename)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
            }
        }
        output
    }
    fun print(activity: Activity, base: File, markdown: String, title: String) {
        val web = WebView(activity)
        configureWeb(web, base)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript("document.fonts.ready.then(function(){window.printReady=true;});", null)
                view.postDelayed({
                    val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val delegate = view.createPrintDocumentAdapter(title)
                    val adapter = object : android.print.PrintDocumentAdapter() {
                        override fun onStart() = delegate.onStart()
                        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: android.os.CancellationSignal?, callback: LayoutResultCallback?, extras: android.os.Bundle?) = delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
                        override fun onWrite(pages: Array<out android.print.PageRange>?, destination: android.os.ParcelFileDescriptor?, cancellationSignal: android.os.CancellationSignal?, callback: WriteResultCallback?) = delegate.onWrite(pages, destination, cancellationSignal, callback)
                        override fun onFinish() { delegate.onFinish(); web.destroy() }
                    }
                    manager.print(title, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build())
                }, 800)
            }
        }
        web.loadDataWithBaseURL("file://${base.path}/", NotesRenderer.html(markdown, title), "text/html", "UTF-8", null)
    }
    fun configureWeb(web: WebView, base: File) {
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = false
        web.settings.blockNetworkLoads = true
        web.settings.setSupportMultipleWindows(false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
        }
    }
}
