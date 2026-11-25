package com.elidot.inkdesk.spatial

import com.elidot.inkdesk.model.ArtifactCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplodedModelControllerTest {

    private val controller = ExplodedModelController()

    @Test
    fun easeOutCubicIsMonotonicAndBounded() {
        assertEquals(0f, controller.easeOutCubic(-0.5f), 1e-5f)
        assertEquals(0f, controller.easeOutCubic(0f), 1e-5f)
        assertEquals(0.875f, controller.easeOutCubic(0.5f), 1e-5f)
        assertEquals(1f, controller.easeOutCubic(1f), 1e-5f)
        assertEquals(1f, controller.easeOutCubic(2.0f), 1e-5f)
    }

    @Test
    fun explodedDisassemblyIncreasesInterPartCaliperDistance() {
        val dougong = ArtifactCatalog.findArtifact("dougong_bell")
        val assembledMm = controller.measureDistanceMm(dougong, "ludou", "shuatou", 0f)
        val explodedMm = controller.measureDistanceMm(dougong, "ludou", "shuatou", 1f)

        assertTrue("Assembled distance must be positive", assembledMm > 200f)
        assertTrue(
            "Exploded distance ($explodedMm mm) must exceed assembled distance ($assembledMm mm)",
            explodedMm > assembledMm * 1.8f
        )
        assertEquals(0f, controller.measureDistanceMm(dougong, "ludou", "ludou", 0.5f), 1e-5f)
    }

    @Test
    fun crossSectionClipsUpperPartsWhileKeepingLowerPartsVisible() {
        val dougong = ArtifactCatalog.findArtifact("dougong_bell")
        val transforms = controller.computeTransforms(
            artifact = dougong,
            explodeProgress = 0.2f,
            selectedPartId = "ang_lever",
            yawDegrees = 390f,
            pitchDegrees = 95f,
            crossSectionEnabled = true,
            sectionCutY = 0.10f
        )

        val ludou = transforms.first { it.partId == "ludou" }
        val shuatou = transforms.first { it.partId == "shuatou" }
        val ang = transforms.first { it.partId == "ang_lever" }

        assertFalse("Bottom Ludou should remain unclipped", ludou.clippedBySection)
        assertTrue("Top Shuatou should be clipped above section plane", shuatou.clippedBySection)
        assertEquals(1.0f, ang.highlightWeight, 1e-5f)
        assertEquals(0.0f, ludou.highlightWeight, 1e-5f)
    }

    @Test
    fun orbitClampWrapsYawAndConstrainsPitch() {
        val (yaw1, pitch1) = controller.clampOrbit(-30f, 120f)
        assertEquals(330f, yaw1, 1e-4f)
        assertEquals(75f, pitch1, 1e-4f)

        val (yaw2, pitch2) = controller.clampOrbit(725f, -110f)
        assertEquals(5f, yaw2, 1e-4f)
        assertEquals(-75f, pitch2, 1e-4f)
    }

    @Test
    fun glbSceneBindingMapsEveryArtifactPartToUniqueGltfNodeName() {
        ArtifactCatalog.exhibits.forEach { artifact ->
            assertTrue("${artifact.id} must point to .glb asset", artifact.glbAssetPath.endsWith(".glb"))
            val binding = controller.resolveGlbSceneBinding(
                artifact = artifact,
                explodeProgress = 0.6f,
                selectedPartId = artifact.parts.first().id,
                yawDegrees = 24f,
                pitchDegrees = 14f,
                crossSectionEnabled = false,
                sectionCutY = 0.2f
            )
            assertEquals(artifact.glbAssetPath, binding.glbAssetPath)
            assertEquals(artifact.parts.size, binding.nodeTransforms.size)
            artifact.parts.forEach { part ->
                val tr = binding.nodeTransforms[part.glbNodeName]
                assertTrue("Missing GLB node binding for ${part.glbNodeName}", tr != null)
            }
        }
    }

    @Test
    fun packagedGlbBinaryAssetsExistWithValidGltf2MagicHeader() {
        val assetsDir = listOf(
            java.io.File("code/inkdesk-spatial/app/src/main/assets"),
            java.io.File("app/src/main/assets")
        ).firstOrNull { it.exists() }
        assertTrue("Assets directory must exist", assetsDir != null)
        ArtifactCatalog.exhibits.forEach { artifact ->
            val glb = java.io.File(assetsDir!!, artifact.glbAssetPath)
            assertTrue("Missing GLB file: ${glb.path}", glb.exists())
            assertTrue("GLB file too small: ${glb.path}", glb.length() > 50_000L)
            val header = glb.inputStream().use { stream ->
                val buf = ByteArray(8)
                stream.read(buf)
                buf
            }
            val magic = String(header, 0, 4, Charsets.US_ASCII)
            assertEquals("glTF", magic)
        }
    }
}
