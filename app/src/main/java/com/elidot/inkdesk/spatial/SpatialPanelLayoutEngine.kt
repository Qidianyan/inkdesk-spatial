package com.elidot.inkdesk.spatial

import com.elidot.inkdesk.model.LayoutPreset
import com.elidot.inkdesk.model.PanelId
import com.elidot.inkdesk.model.PanelPose
import com.elidot.inkdesk.model.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Computes ergonomic 3D curved-arc transforms for the three InkDesk spatial windows.
 * Uses PICO Spatial dynamic scale convention (~1160 dp per world meter at 1.45 m viewing depth).
 */
class SpatialPanelLayoutEngine(
    private val defaultArcRadiusMeters: Float = 1.55f,
    private val eyeHeightOffsetMeters: Float = -0.06f,
    private val dpPerMeter: Float = 1160f
) {
    fun computeLayout(
        preset: LayoutPreset,
        headPosition: Vec3 = Vec3.ZERO,
        headYawDegrees: Float = 0f,
        customRadiusMeters: Float = defaultArcRadiusMeters
    ): Map<PanelId, PanelPose> {
        val radius = customRadiusMeters.coerceIn(MIN_COMFORT_RADIUS_M, MAX_COMFORT_RADIUS_M)
        return when (preset) {
            LayoutPreset.SURROUND_ARC -> buildSurroundArc(headPosition, headYawDegrees, radius)
            LayoutPreset.FOCUS_SINGLE -> buildFocusSingle(headPosition, headYawDegrees, radius)
            LayoutPreset.SIDE_BY_SIDE_COMPARE -> buildSideBySide(headPosition, headYawDegrees, radius)
        }
    }

    private fun buildSurroundArc(
        headPos: Vec3,
        headYawDeg: Float,
        radius: Float
    ): Map<PanelId, PanelPose> {
        val wingAngleDeg = 35f
        val center = panelOnArc(
            id = PanelId.STAGE_CENTER,
            headPos = headPos,
            headYawDeg = headYawDeg,
            azimuthOffsetDeg = 0f,
            radius = radius,
            yOffset = eyeHeightOffsetMeters,
            widthDp = 820f,
            heightDp = 640f,
            visible = true
        )
        val left = panelOnArc(
            id = PanelId.AUDIO_FOCUS_LEFT,
            headPos = headPos,
            headYawDeg = headYawDeg,
            azimuthOffsetDeg = -wingAngleDeg,
            radius = radius * 0.96f,
            yOffset = eyeHeightOffsetMeters - 0.02f,
            widthDp = 420f,
            heightDp = 600f,
            visible = true
        )
        val right = panelOnArc(
            id = PanelId.NOTES_INSPECTOR_RIGHT,
            headPos = headPos,
            headYawDeg = headYawDeg,
            azimuthOffsetDeg = wingAngleDeg,
            radius = radius * 0.96f,
            yOffset = eyeHeightOffsetMeters - 0.02f,
            widthDp = 440f,
            heightDp = 600f,
            visible = true
        )
        return linkedMapOf(
            PanelId.AUDIO_FOCUS_LEFT to left,
            PanelId.STAGE_CENTER to center,
            PanelId.NOTES_INSPECTOR_RIGHT to right
        )
    }

    private fun buildFocusSingle(
        headPos: Vec3,
        headYawDeg: Float,
        radius: Float
    ): Map<PanelId, PanelPose> {
        val closerRadius = (radius - 0.18f).coerceAtLeast(MIN_COMFORT_RADIUS_M)
        val center = panelOnArc(
            id = PanelId.STAGE_CENTER,
            headPos = headPos,
            headYawDeg = headYawDeg,
            azimuthOffsetDeg = 0f,
            radius = closerRadius,
            yOffset = eyeHeightOffsetMeters,
            widthDp = 1040f,
            heightDp = 720f,
            visible = true
        )
        val leftParked = panelOnArc(
            id = PanelId.AUDIO_FOCUS_LEFT,
            headPos = headPos,
            headYawDeg = headYawDeg,
            azimuthOffsetDeg = -58f,
            radius = radius + 0.25f,
            yOffset = eyeHeightOffsetMeters - 0.12f,
            widthDp = 380f,
            heightDp = 540f,
            visible = false
        )
        val rightParked = panelOnArc(
            id = PanelId.NOTES_INSPECTOR_RIGHT,
            headPos = headPos,
            headYawDeg = headYawDeg,
            azimuthOffsetDeg = 58f,
            radius = radius + 0.25f,
            yOffset = eyeHeightOffsetMeters - 0.12f,
            widthDp = 400f,
            heightDp = 540f,
            visible = false
        )
        return linkedMapOf(
            PanelId.AUDIO_FOCUS_LEFT to leftParked,
            PanelId.STAGE_CENTER to center,
            PanelId.NOTES_INSPECTOR_RIGHT to rightParked
        )
    }

    private fun buildSideBySide(
        headPos: Vec3,
        headYawDeg: Float,
        radius: Float
    ): Map<PanelId, PanelPose> {
        val stage = panelOnArc(
            id = PanelId.STAGE_CENTER,
            headPos = headPos,
            headYawDeg = headYawDeg,
            azimuthOffsetDeg = -17f,
            radius = radius,
            yOffset = eyeHeightOffsetMeters,
            widthDp = 760f,
            heightDp = 640f,
            visible = true
        )
        val notes = panelOnArc(
            id = PanelId.NOTES_INSPECTOR_RIGHT,
            headPos = headPos,
            headYawDeg = headYawDeg,
            azimuthOffsetDeg = 19f,
            radius = radius,
            yOffset = eyeHeightOffsetMeters,
            widthDp = 540f,
            heightDp = 640f,
            visible = true
        )
        val audioCompact = panelOnArc(
            id = PanelId.AUDIO_FOCUS_LEFT,
            headPos = headPos,
            headYawDeg = headYawDeg,
            azimuthOffsetDeg = -46f,
            radius = radius * 1.04f,
            yOffset = eyeHeightOffsetMeters - 0.14f,
            widthDp = 380f,
            heightDp = 460f,
            visible = true
        )
        return linkedMapOf(
            PanelId.AUDIO_FOCUS_LEFT to audioCompact,
            PanelId.STAGE_CENTER to stage,
            PanelId.NOTES_INSPECTOR_RIGHT to notes
        )
    }

    private fun panelOnArc(
        id: PanelId,
        headPos: Vec3,
        headYawDeg: Float,
        azimuthOffsetDeg: Float,
        radius: Float,
        yOffset: Float,
        widthDp: Float,
        heightDp: Float,
        visible: Boolean
    ): PanelPose {
        val totalAngleRad = ((headYawDeg + azimuthOffsetDeg) * PI / 180.0)
        val x = headPos.x + (sin(totalAngleRad) * radius).toFloat()
        val z = headPos.z - (cos(totalAngleRad) * radius).toFloat()
        val y = headPos.y + yOffset
        val faceUserYaw = -(headYawDeg + azimuthOffsetDeg)
        val gentlePitch = if (yOffset < -0.10f) 7f else 3f
        return PanelPose(
            panelId = id,
            visible = visible,
            positionMeters = Vec3(x, y, z),
            yawDegrees = faceUserYaw,
            pitchDegrees = gentlePitch,
            widthDp = widthDp,
            heightDp = heightDp,
            depthMeters = radius
        )
    }

    fun panelWidthMeters(pose: PanelPose): Float = pose.widthDp / dpPerMeter

    fun hasHorizontalOverlap(a: PanelPose, b: PanelPose, minGapMeters: Float = 0.04f): Boolean {
        if (!a.visible || !b.visible) return false
        val centerDistance = a.positionMeters.distanceTo(b.positionMeters)
        val requiredHalfWidths = (panelWidthMeters(a) + panelWidthMeters(b)) * 0.5f + minGapMeters
        return centerDistance < requiredHalfWidths
    }

    fun isWithinComfortZone(pose: PanelPose, headPosition: Vec3 = Vec3.ZERO): Boolean {
        val dx = pose.positionMeters.x - headPosition.x
        val dy = pose.positionMeters.y - headPosition.y
        val dz = pose.positionMeters.z - headPosition.z
        val horizontalDist = kotlin.math.sqrt(dx * dx + dz * dz)
        return horizontalDist in (MIN_COMFORT_RADIUS_M - 1e-3f)..(MAX_COMFORT_RADIUS_M + 1e-3f) &&
            abs(dy) <= 0.35f &&
            abs(pose.pitchDegrees) <= 18f
    }

    companion object {
        const val MIN_COMFORT_RADIUS_M = 1.15f
        const val MAX_COMFORT_RADIUS_M = 2.40f
    }
}
