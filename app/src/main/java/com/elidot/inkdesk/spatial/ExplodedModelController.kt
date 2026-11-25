package com.elidot.inkdesk.spatial

import com.elidot.inkdesk.model.ArtifactPart
import com.elidot.inkdesk.model.SpatialArtifact
import com.elidot.inkdesk.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class PartRenderTransform(
    val partId: String,
    val glbNodeName: String,
    val localPosition: Vec3,
    val worldPosition: Vec3,
    val highlightWeight: Float,
    val clippedBySection: Boolean
)

data class GltfSceneBinding(
    val artifactId: String,
    val glbAssetPath: String,
    val nodeTransforms: Map<String, PartRenderTransform>
)

/**
 * State machine & 3D math engine for exploded view disassembly, 360-degree orbit,
 * cross-section clipping, and structural caliper measurements.
 */
class ExplodedModelController {

    fun easeOutCubic(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return 1f - (1f - t).pow(3)
    }

    fun clampOrbit(yawDegrees: Float, pitchDegrees: Float): Pair<Float, Float> {
        var normalizedYaw = yawDegrees % 360f
        if (normalizedYaw < 0f) normalizedYaw += 360f
        val clampedPitch = pitchDegrees.coerceIn(-75f, 75f)
        return normalizedYaw to clampedPitch
    }

    fun computePartPosition(part: ArtifactPart, explodeProgress: Float): Vec3 {
        val eased = easeOutCubic(explodeProgress)
        return part.basePosition + (part.explodeOffset * eased)
    }

    fun rotateAroundOrigin(point: Vec3, yawDegrees: Float, pitchDegrees: Float): Vec3 {
        val yawRad = yawDegrees * PI / 180.0
        val pitchRad = pitchDegrees * PI / 180.0

        val cosY = cos(yawRad).toFloat()
        val sinY = sin(yawRad).toFloat()
        val x1 = point.x * cosY + point.z * sinY
        val z1 = -point.x * sinY + point.z * cosY

        val cosP = cos(pitchRad).toFloat()
        val sinP = sin(pitchRad).toFloat()
        val y2 = point.y * cosP - z1 * sinP
        val z2 = point.y * sinP + z1 * cosP

        return Vec3(x1, y2, z2)
    }

    fun computeTransforms(
        artifact: SpatialArtifact,
        explodeProgress: Float,
        selectedPartId: String,
        yawDegrees: Float,
        pitchDegrees: Float,
        crossSectionEnabled: Boolean,
        sectionCutY: Float
    ): List<PartRenderTransform> {
        val (yaw, pitch) = clampOrbit(yawDegrees, pitchDegrees)
        return artifact.parts.map { part ->
            val localPos = computePartPosition(part, explodeProgress)
            val worldPos = rotateAroundOrigin(localPos, yaw, pitch)
            val clipped = crossSectionEnabled && localPos.y > sectionCutY
            val highlight = if (part.id == selectedPartId) 1.0f else 0.0f
            PartRenderTransform(
                partId = part.id,
                glbNodeName = part.glbNodeName,
                localPosition = localPos,
                worldPosition = worldPos,
                highlightWeight = highlight,
                clippedBySection = clipped
            )
        }
    }

    /**
     * Resolves a complete glTF 2.0 (.glb) asset binding keyed by glTF node name
     * for direct PICO Spatial SDK GltfModel / Entity transform application.
     */
    fun resolveGlbSceneBinding(
        artifact: SpatialArtifact,
        explodeProgress: Float,
        selectedPartId: String,
        yawDegrees: Float,
        pitchDegrees: Float,
        crossSectionEnabled: Boolean,
        sectionCutY: Float
    ): GltfSceneBinding {
        val transforms = computeTransforms(
            artifact = artifact,
            explodeProgress = explodeProgress,
            selectedPartId = selectedPartId,
            yawDegrees = yawDegrees,
            pitchDegrees = pitchDegrees,
            crossSectionEnabled = crossSectionEnabled,
            sectionCutY = sectionCutY
        )
        return GltfSceneBinding(
            artifactId = artifact.id,
            glbAssetPath = artifact.glbAssetPath,
            nodeTransforms = transforms.associateBy { it.glbNodeName }
        )
    }

    /**
     * Returns true physical center-to-center caliper distance in millimeters between two parts
     * at the given exploded progress, scaled to the exhibit's total physical span.
     */
    fun measureDistanceMm(
        artifact: SpatialArtifact,
        partAId: String,
        partBId: String,
        explodeProgress: Float
    ): Float {
        val partA = artifact.parts.firstOrNull { it.id == partAId } ?: return 0f
        val partB = artifact.parts.firstOrNull { it.id == partBId } ?: return 0f
        if (partA.id == partB.id) return 0f

        val posA = computePartPosition(partA, explodeProgress)
        val posB = computePartPosition(partB, explodeProgress)
        val normalizedDist = posA.distanceTo(posB)
        val scaleMmPerUnit = maxOf(
            artifact.totalDimensionsMm.x,
            artifact.totalDimensionsMm.y,
            artifact.totalDimensionsMm.z
        ) * 0.5f
        return normalizedDist * scaleMmPerUnit
    }
}
