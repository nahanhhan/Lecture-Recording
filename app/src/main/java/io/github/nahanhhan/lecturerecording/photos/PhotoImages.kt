package io.github.nahanhhan.lecturerecording.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

object PhotoImages {
    /** Keep the original JPEG and metadata; consumers receive pixels in the correct orientation. */
    fun decode(file: File, maxEdge: Int = 2048): Bitmap {
        require(maxEdge > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "照片无法读取" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })) { "照片无法读取" }
        try {
            val orientation = ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            }
            if (matrix.isIdentity) return bitmap
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented !== bitmap) bitmap.recycle()
            return oriented
        } catch (error: Exception) { bitmap.recycle(); throw error }
    }
}
