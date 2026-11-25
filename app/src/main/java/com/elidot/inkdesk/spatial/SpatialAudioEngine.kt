package com.elidot.inkdesk.spatial

import com.elidot.inkdesk.model.SpatialAudioAnchor
import com.elidot.inkdesk.model.SpatialAudioRenderState
import com.elidot.inkdesk.model.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spatial acoustic engine computing HRTF-aligned azimuth, elevation, inverse-distance rolloff,
 * and binaural left/right energy distribution for all 4 movable soundscape anchors.
 */
class SpatialAudioEngine(
    private val refDistanceMeters: Float = 0.70f,
    private val rolloffFactor: Float = 1.15f,
    private val minAnchorRadiusMeters: Float = 0.45f,
    private val maxAnchorRadiusMeters: Float = 3.50f
) {
    fun clampAnchorPosition(position: Vec3): Vec3 {
        val horizontalLen = sqrt(position.x * position.x + position.z * position.z)
        val clampedY = position.y.coerceIn(-0.80f, 1.40f)
        if (horizontalLen < 1e-5f) {
            return Vec3(0f, clampedY, -minAnchorRadiusMeters)
        }
        val clampedRadius = horizontalLen.coerceIn(minAnchorRadiusMeters, maxAnchorRadiusMeters)
        val scale = clampedRadius / horizontalLen
        return Vec3(position.x * scale, clampedY, position.z * scale)
    }

    fun evaluateAnchor(
        anchor: SpatialAudioAnchor,
        listenerPosition: Vec3 = Vec3.ZERO,
        listenerYawDegrees: Float = 0f,
        masterDucking: Float = 1.0f
    ): SpatialAudioRenderState {
        val rel = anchor.positionMeters - listenerPosition
        val distance = max(0.15f, rel.length())

        // Transform world-relative vector into listener head space:
        // +listenerYawDegrees rotates head clockwise (turning right) viewed from +Y.
        // Right basis R = (cos(yaw), 0, sin(yaw)); Forward basis F = (sin(yaw), 0, -cos(yaw)).
        val yawRad = listenerYawDegrees * PI / 180.0
        val cosY = cos(yawRad).toFloat()
        val sinY = sin(yawRad).toFloat()
        val localRight = rel.x * cosY + rel.z * sinY
        val localForward = rel.x * sinY - rel.z * cosY
        val localY = rel.y

        val azimuthRad = atan2(localRight.toDouble(), localForward.toDouble())
        val azimuthDeg = (azimuthRad * 180.0 / PI).toFloat()
        val horizontalDist = max(1e-4f, sqrt(localRight * localRight + localForward * localForward))
        val elevationDeg = (atan2(localY.toDouble(), horizontalDist.toDouble()) * 180.0 / PI).toFloat()

        // Inverse-distance rolloff (aligned with WebAudio PannerNode distanceModel = "inverse")
        val attenuation = (refDistanceMeters / (refDistanceMeters + rolloffFactor * max(0f, distance - refDistanceMeters)))
            .coerceIn(0.05f, 1.0f)

        val effectiveGain = if (anchor.enabled) {
            anchor.gain.coerceIn(0f, 1f) * attenuation * masterDucking.coerceIn(0.1f, 1f)
        } else {
            0f
        }

        // Equal-power binaural pan with mild head-shadowing for behind-head sources
        val pan = sin(azimuthRad).toFloat().coerceIn(-1f, 1f)
        val panAngle = ((pan + 1f) * 0.25f * PI).toFloat()
        val backShadow = if (localForward < 0f) 0.88f else 1.0f
        val leftGain = effectiveGain * cos(panAngle) * backShadow
        val rightGain = effectiveGain * sin(panAngle) * backShadow

        return SpatialAudioRenderState(
            anchorId = anchor.id,
            active = anchor.enabled && effectiveGain > 0.001f,
            distanceMeters = distance,
            azimuthDegrees = azimuthDeg,
            elevationDegrees = elevationDeg,
            attenuation = attenuation,
            leftGain = leftGain,
            rightGain = rightGain
        )
    }

    fun evaluateAll(
        anchors: List<SpatialAudioAnchor>,
        listenerPosition: Vec3 = Vec3.ZERO,
        listenerYawDegrees: Float = 0f,
        masterDucking: Float = 1.0f
    ): List<SpatialAudioRenderState> = anchors.map {
        evaluateAnchor(it, listenerPosition, listenerYawDegrees, masterDucking)
    }
}
