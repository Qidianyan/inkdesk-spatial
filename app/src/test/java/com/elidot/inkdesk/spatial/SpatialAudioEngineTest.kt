package com.elidot.inkdesk.spatial

import com.elidot.inkdesk.model.SoundAnchorId
import com.elidot.inkdesk.model.SpatialAudioAnchor
import com.elidot.inkdesk.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialAudioEngineTest {

    private val engine = SpatialAudioEngine()

    @Test
    fun leftAndRightSourcesProduceSymmetricBinauralPanWeights() {
        val leftAnchor = SpatialAudioAnchor(SoundAnchorId.PINE_WIND, true, 0.8f, Vec3(-1.4f, 0f, -1.0f))
        val rightAnchor = SpatialAudioAnchor(SoundAnchorId.EAVES_RAIN, true, 0.8f, Vec3(1.4f, 0f, -1.0f))

        val leftState = engine.evaluateAnchor(leftAnchor, listenerYawDegrees = 0f)
        val rightState = engine.evaluateAnchor(rightAnchor, listenerYawDegrees = 0f)

        assertTrue("Left anchor should have negative azimuth", leftState.azimuthDegrees < -40f)
        assertTrue("Left anchor should favor left ear", leftState.leftGain > leftState.rightGain)
        assertTrue("Right anchor should have positive azimuth", rightState.azimuthDegrees > 40f)
        assertTrue("Right anchor should favor right ear", rightState.rightGain > rightState.leftGain)
        assertEquals(leftState.leftGain, rightState.rightGain, 1e-4f)
    }

    @Test
    fun rotatingHeadRightShiftsFrontSourceTowardLeftEar() {
        val frontAnchor = SpatialAudioAnchor(SoundAnchorId.CHIME_BELLS, true, 1.0f, Vec3(0f, 0f, -1.5f))
        val centered = engine.evaluateAnchor(frontAnchor, listenerYawDegrees = 0f)
        val turnedRight = engine.evaluateAnchor(frontAnchor, listenerYawDegrees = 45f)

        assertEquals(centered.leftGain, centered.rightGain, 1e-4f)
        assertTrue(
            "Turning head right (+45 deg) should shift front source to left azimuth",
            turnedRight.azimuthDegrees < -40f
        )
        assertTrue(turnedRight.leftGain > turnedRight.rightGain)
    }

    @Test
    fun inverseDistanceAttenuationDecreasesWithDistanceAndMutedAnchorProducesZero() {
        val nearAnchor = SpatialAudioAnchor(SoundAnchorId.CHARCOAL_TEA, true, 1.0f, Vec3(0f, 0f, -0.7f))
        val farAnchor = SpatialAudioAnchor(SoundAnchorId.CHARCOAL_TEA, true, 1.0f, Vec3(0f, 0f, -3.0f))
        val mutedAnchor = nearAnchor.copy(enabled = false)

        val nearState = engine.evaluateAnchor(nearAnchor)
        val farState = engine.evaluateAnchor(farAnchor)
        val mutedState = engine.evaluateAnchor(mutedAnchor)

        assertTrue(nearState.attenuation > farState.attenuation)
        assertTrue(nearState.leftGain > farState.leftGain)
        assertFalse(mutedState.active)
        assertEquals(0f, mutedState.leftGain, 1e-6f)
        assertEquals(0f, mutedState.rightGain, 1e-6f)
    }

    @Test
    fun anchorPositionIsClampedToValidStudyRoomBounds() {
        val tooNear = engine.clampAnchorPosition(Vec3(0.05f, 3.0f, -0.05f))
        val tooFar = engine.clampAnchorPosition(Vec3(12f, -4.0f, -12f))

        assertTrue(tooNear.y <= 1.40f)
        assertTrue(tooFar.y >= -0.80f)
        val nearHoriz = kotlin.math.sqrt(tooNear.x * tooNear.x + tooNear.z * tooNear.z)
        val farHoriz = kotlin.math.sqrt(tooFar.x * tooFar.x + tooFar.z * tooFar.z)
        assertEquals(0.45f, nearHoriz, 1e-3f)
        assertEquals(3.50f, farHoriz, 1e-3f)
    }
}
