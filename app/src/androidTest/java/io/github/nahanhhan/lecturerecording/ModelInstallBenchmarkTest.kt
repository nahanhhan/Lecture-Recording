package io.github.nahanhhan.lecturerecording

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nahanhhan.lecture.core.ModelArchiveExtractor
import io.github.nahanhhan.lecturerecording.models.ModelCatalog
import io.github.nahanhhan.lecturerecording.models.NativeBzip2InputStream
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ModelInstallBenchmarkTest {
    @Test fun officialFullModelExtractsAndReportsElapsedTime() {
        val filename = InstrumentationRegistry.getArguments().getString("benchmark_archive")
        Assume.assumeTrue("Full model archive is optional", filename != null)
        val app = ApplicationProvider.getApplicationContext<LectureApp>()
        check(app.packageName.endsWith(".uitest"))
        val file = File(requireNotNull(filename))
        val model = ModelCatalog.get("aed")
        Assert.assertEquals(model.bytes, file.length())
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        Assert.assertEquals(model.sha256, digest.digest().joinToString("") { "%02x".format(it) })
        val destination = File(app.cacheDir, "full-model-benchmark").apply { mkdirs() }
        val start = SystemClock.elapsedRealtime()
        var reports = 0
        val hashes = NativeBzip2InputStream(file).use { stream ->
            ModelArchiveExtractor.extract(stream, destination, model.files, onProgress = { reports++ })
        }
        val elapsed = SystemClock.elapsedRealtime() - start
        Assert.assertEquals(model.files, hashes.keys)
        Assert.assertTrue(reports > 100)
        Assert.assertTrue(model.files.all { File(destination, it).length() > 1000 })
        android.util.Log.i("RecNoteBenchmark", "Full AED extraction=${elapsed}ms output=${model.files.sumOf { File(destination, it).length() }}B progress_callbacks=$reports")
    }
}
