package io.github.nahanhhan.lecturerecording.models

import java.io.File
import java.io.IOException
import java.io.InputStream

class NativeBzip2InputStream(file: File) : InputStream() {
    private var handle = try { openNative(file.path) } catch (error: IOException) { throw IOException("模型归档无法打开，请重新安装", error) }
    private val single = ByteArray(1)
    val compressedBytesRead: Long get() = positionNative(handle)
    override fun read(): Int = if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 255
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (handle == 0L) throw IOException("模型解压流已关闭")
        return try { readNative(handle, bytes, offset, length) } catch (error: IOException) { throw IOException("模型归档解压失败，文件可能损坏，请重试", error) }
    }
    override fun close() {
        if (handle != 0L) { closeNative(handle); handle = 0 }
    }
    private external fun openNative(path: String): Long
    private external fun readNative(handle: Long, bytes: ByteArray, offset: Int, length: Int): Int
    private external fun positionNative(handle: Long): Long
    private external fun closeNative(handle: Long)
    companion object { init { System.loadLibrary("recnote_archive") } }
}
