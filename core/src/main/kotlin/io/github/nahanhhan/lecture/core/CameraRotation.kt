package io.github.nahanhhan.lecture.core

object CameraRotation {
    /** Surface rotation values, independent of Android classes for boundary tests. */
    fun fromOrientation(degrees: Int): Int? = when (degrees) {
        in 45..134 -> 3
        in 135..224 -> 2
        in 225..314 -> 1
        in 0..44, in 315..359 -> 0
        else -> null
    }
}
