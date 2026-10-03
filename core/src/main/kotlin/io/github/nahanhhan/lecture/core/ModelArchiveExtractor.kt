package io.github.nahanhhan.lecture.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

object ModelArchiveExtractor {
    fun extract(input: InputStream, destination: File, expected: Set<String>,
        onProgress: () -> Unit = {}, checkCancelled: () -> Unit = {}): Map<String, String> {
        require(expected.isNotEmpty() && expected.all { it.matches(Regex("[A-Za-z0-9_.-]+")) && it !in setOf(".", "..") })
        check(destination.isDirectory || destination.mkdirs()) { "无法创建模型安装目录" }
        val hashes = linkedMapOf<String, String>()
        val buffer = ByteArray(256 * 1024)
        TarArchiveInputStream(input.buffered(256 * 1024)).use { archive ->
            while (true) {
                checkCancelled()
                val entry = archive.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (entry.isFile && name in expected) {
                    require(name !in hashes) { "模型归档包含重复文件" }
                    require(entry.size in 1..1_500_000_000L) { "模型文件大小无效" }
                    val target = File(destination, name)
                    if (target.exists()) check(target.delete()) { "无法清理未完成的模型文件" }
                    check(destination.usableSpace >= entry.size + 8 * 1024 * 1024) { "空间不足，请清理存储后继续安装" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var copied = 0L
                    target.outputStream().buffered(256 * 1024).use { output ->
                        while (true) {
                            checkCancelled()
                            val count = archive.read(buffer); if (count < 0) break
                            output.write(buffer, 0, count); digest.update(buffer, 0, count)
                            copied += count; onProgress()
                        }
                    }
                    check(copied == entry.size) { "模型归档未完整解压" }
                    hashes[name] = digest.digest().joinToString("") { "%02x".format(it) }
                } else {
                    // Consume ignored entries in bounded chunks too, so cancellation stays responsive.
                    while (archive.read(buffer) >= 0) { checkCancelled(); onProgress() }
                }
                onProgress()
            }
        }
        check(hashes.keys == expected) { "模型归档缺少必要文件" }
        return hashes
    }
}
