package io.github.nahanhhan.lecture.core

import kotlin.math.*
import kotlin.test.*

class AudioAndRotationTest {
    @Test fun sameRatePcmPreservesSamplesAndClampsInvalidValues() {
        val result = mutableListOf<Short>()
        val converter = AudioResampler(16000) { result += it }
        listOf(0f, 0.5f, -0.5f, 1f, -1f, Float.NaN).forEach { converter.accept(it) }
        converter.finish()
        assertEquals(listOf<Short>(0, 16384, -16384, 32767, -32768, 0), result)
    }
    @Test fun sampleCountsFollowDurationAcrossCommonRates() {
        for (rate in listOf(8000, 16000, 22050, 44100, 48000, 96000)) {
            var count = 0
            val converter = AudioResampler(rate) { count++ }
            repeat(rate) { converter.accept(0.2f) }; converter.finish()
            assertEquals(16000, count, "source rate $rate")
        }
    }
    @Test fun downsamplingPreservesSpeechBandAndSuppressesAliasing() {
        fun amplitude(hz: Double): Double {
            val values = mutableListOf<Short>()
            val converter = AudioResampler(48000) { values += it }
            repeat(48000) { converter.accept((sin(2 * PI * hz * it / 48000) * 0.5).toFloat()) }
            converter.finish()
            return sqrt(values.drop(100).dropLast(100).map { (it / 32768.0).pow(2) }.average())
        }
        assertTrue(amplitude(1000.0) in 0.3..0.4)
        assertTrue(amplitude(12000.0) < 0.04)
    }
    @Test fun rotationFollowsAllDeviceDirectionsAndIgnoresUnknownOrientation() {
        assertNull(CameraRotation.fromOrientation(-1))
        assertEquals(0, CameraRotation.fromOrientation(0)); assertEquals(0, CameraRotation.fromOrientation(44))
        assertEquals(3, CameraRotation.fromOrientation(45)); assertEquals(3, CameraRotation.fromOrientation(134))
        assertEquals(2, CameraRotation.fromOrientation(135)); assertEquals(2, CameraRotation.fromOrientation(224))
        assertEquals(1, CameraRotation.fromOrientation(225)); assertEquals(1, CameraRotation.fromOrientation(314))
        assertEquals(0, CameraRotation.fromOrientation(315)); assertEquals(0, CameraRotation.fromOrientation(359))
    }
    @Test fun goUsesItsOwnEndpointAndKeepsGatewayPath() {
        val provider = CloudProvider.OPENCODE_GO
        assertEquals(provider, CloudProvider.detect(provider.baseUrl))
        assertEquals("https://opencode.ai/zen/go/v1/chat/completions", CloudEndpoint.completions(provider.baseUrl))
        assertEquals("https://opencode.ai/zen/go/v1/models", CloudEndpoint.models(provider.baseUrl))
        assertFalse(provider.defaultPhotos)
    }
}
