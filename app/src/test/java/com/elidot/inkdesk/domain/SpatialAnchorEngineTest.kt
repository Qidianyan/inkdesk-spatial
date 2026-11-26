package com.elidot.inkdesk.domain

import com.elidot.inkdesk.model.ArtifactCatalog
import com.elidot.inkdesk.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SpatialAnchorEngineTest {

    private val engine = SpatialAnchorEngine()

    @Test
    fun registersFourCuratedPinsForEveryExhibitWithValidParts() {
        val state = engine.createState("dougong_bell")
        assertEquals(16, SpatialAnchorEngine.BUILTIN_CALLOUT_ANCHORS.size)
        for (exhibit in ArtifactCatalog.exhibits) {
            val pins = engine.anchorsFor(state, exhibit.id)
            assertEquals(4, pins.size)
            for (pin in pins) {
                assertTrue(exhibit.parts.any { it.id == pin.partId })
                assertFalse(pin.isCustom)
                assertTrue(pin.titleZh.isNotBlank())
            }
        }
    }

    @Test
    fun supportsBidirectionalSelectionAndPartSynchronization() {
        val init = engine.createState("dougong_bell")
        val selected = engine.selectAnchor(init, "pin_dougong_ang")
        assertTrue(selected.found)
        assertEquals("ang_lever", selected.partId)
        assertNotNull(selected.cameraFocus)
        assertEquals("pin_dougong_ang", engine.selectedAnchor(selected.state)?.id)

        val synced = engine.syncSelectedAnchorToPart(selected.state, "dougong_bell", "yongle_crest")
        assertEquals("pin_dougong_bell", synced.selectedAnchorId)

        val missing = engine.selectAnchor(synced, "pin_unknown")
        assertFalse(missing.found)
        assertNull(missing.partId)
    }

    @Test
    fun addsCustomPinAndProtectsBuiltInMuseumPinsOnDelete() {
        val init = engine.createState("dougong_bell")
        val added = engine.addCustomAnchor(
            state = init,
            artifactId = "dougong_bell",
            partId = "shuatou",
            titleZh = "耍头承重榫槽",
            craftDetailZh = "顶层耍头锁死全部下层木构竖向自由度。",
            tone = CalloutPinTone.BRONZE
        )
        assertTrue(added.ok)
        assertNotNull(added.anchor)
        assertTrue(added.anchor!!.isCustom)
        assertEquals(5, engine.anchorsFor(added.state, "dougong_bell").size)
        assertEquals(added.anchor!!.id, added.state.selectedAnchorId)

        val denyBuiltin = engine.deleteCustomAnchor(added.state, "pin_dougong_ludou")
        assertFalse(denyBuiltin.ok)
        assertEquals(5, engine.anchorsFor(denyBuiltin.state, "dougong_bell").size)

        val deleted = engine.deleteCustomAnchor(added.state, added.anchor!!.id)
        assertTrue(deleted.ok)
        assertEquals(4, engine.anchorsFor(deleted.state, "dougong_bell").size)
        assertEquals("pin_dougong_ludou", deleted.state.selectedAnchorId)
    }

    @Test
    fun rejectsBlankTitleOrDetailWhenCreatingCustomPin() {
        val init = engine.createState("dougong_bell")
        val blankTitle = engine.addCustomAnchor(init, "dougong_bell", "ludou", "   ", "有效工艺说明")
        assertFalse(blankTitle.ok)
        assertTrue(blankTitle.errorZh!!.contains("标题"))

        val blankDetail = engine.addCustomAnchor(init, "dougong_bell", "ludou", "有效标题", " \t\n ")
        assertFalse(blankDetail.ok)
        assertTrue(blankDetail.errorZh!!.contains("内容"))
        assertEquals(4, engine.anchorsFor(blankDetail.state, "dougong_bell").size)
    }

    @Test
    fun enforcesWarmZeroBlueCyanPurpleSpectrumAcrossAllThreeLightingPresets() {
        assertEquals(
            listOf(
                StudioLightingPreset.KYOTO_MORNING,
                StudioLightingPreset.GALLERY_ZENITH,
                StudioLightingPreset.WARM_LANTERN
            ),
            StudioLightingPreset.entries
        )
        for (preset in StudioLightingPreset.entries) {
            assertTrue(preset.kelvin in 2200..5500)
            assertTrue(preset.keyIntensity > 1f)
            assertTrue(preset.exposure > 0.8f)
            val hexList = listOf(
                preset.keyColorHex,
                preset.rimColorHex,
                preset.hemiSkyHex,
                preset.hemiGroundHex,
                preset.emberColorHex
            )
            for (hex in hexList) {
                val r = ((hex shr 16) and 0xFF).toInt()
                val g = ((hex shr 8) and 0xFF).toInt()
                val b = (hex and 0xFF).toInt()
                assertTrue("Red must be >= Green for warm spectrum", r >= g)
                assertTrue("Green must be >= Blue (zero blue/cyan/purple)", g >= b)
            }
            val dir = engine.directionalLightVector(preset, 5.8f)
            val len = sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z)
            assertEquals(5.8f, len, 1e-3f)
            assertTrue(dir.y > 0f)
        }
    }

    @Test
    fun switchesStudioLightingAndPinVisibilityCleanly() {
        var state = engine.createState("dougong_bell")
        state = engine.setLightingPreset(state, StudioLightingPreset.WARM_LANTERN)
        assertEquals(StudioLightingPreset.WARM_LANTERN, state.lightingPreset)
        state = engine.setShowPinsIn3D(state, false)
        assertFalse(state.showPinsIn3D)
    }

    @Test
    fun sanitizesNaNAndOutOfBoundsOffsetsAndWorldPositions() {
        val state = engine.createState("dougong_bell")
        val added = engine.addCustomAnchor(
            state = state,
            artifactId = "dougong_bell",
            partId = "ludou",
            titleZh = "越界钳制验证",
            craftDetailZh = "超大偏移量钳制至安全范围。",
            localOffset = Vec3(88f, -77f, 0.2f)
        )
        assertTrue(added.ok)
        val pin = added.anchor!!
        assertEquals(SpatialAnchorEngine.MAX_LOCAL_OFFSET_M, pin.localOffset.x, 1e-5f)
        assertEquals(-SpatialAnchorEngine.MAX_LOCAL_OFFSET_M, pin.localOffset.y, 1e-5f)

        val worldFromNan = engine.resolveWorldPosition(pin, Vec3(Float.NaN, 0f, 0f))
        assertTrue(MortiseAssemblyEngine.isValidVec3(worldFromNan))
        assertEquals(SpatialAnchorEngine.MAX_LOCAL_OFFSET_M, worldFromNan.x, 1e-4f)
    }

    @Test
    fun switchesArtifactPreservingCustomPinsOnPreviousExhibit() {
        val init = engine.createState("dougong_bell")
        val added = engine.addCustomAnchor(init, "dougong_bell", "ludou", "坐斗标注", "切品后不丢失")
        val switched = engine.switchArtifact(added.state, "origami_pagoda")
        assertEquals("origami_pagoda", switched.activeArtifactId)
        assertEquals("pin_pagoda_shell", switched.selectedAnchorId)
        assertEquals(5, engine.anchorsFor(switched, "dougong_bell").size)
    }

    @Test
    fun truncatesOverlongTitleAndDetailSafely() {
        val init = engine.createState("xuande_censer")
        val longTitle = "宣德炉".repeat(30)
        val longDetail = "栗壳包浆工艺".repeat(80)
        val res = engine.addCustomAnchor(init, "xuande_censer", "censer_body", longTitle, longDetail)
        assertTrue(res.ok)
        assertEquals(SpatialAnchorEngine.MAX_TITLE_LENGTH, res.anchor!!.titleZh.length)
        assertEquals(SpatialAnchorEngine.MAX_DETAIL_LENGTH, res.anchor!!.craftDetailZh.length)
    }

    @Test
    fun coexistsWithMortiseAssemblyAndCutawayEngineWithoutConflict() {
        val mortise = MortiseAssemblyEngine()
        val exhibit = ArtifactCatalog.findArtifact("dougong_bell")
        var asm = mortise.createSession(exhibit)
        asm = mortise.updateCutaway(asm, enabled = true, axis = CutawayAxis.Y, offsetMeters = 0.16f)
        val first = asm.steps.first()
        val snapped = mortise.attemptSnap(asm, first.partId, first.targetPosition, 0f)
        assertTrue(snapped.snapped)

        var anchors = engine.createState(exhibit.id)
        anchors = engine.setLightingPreset(anchors, StudioLightingPreset.GALLERY_ZENITH)
        val focus = engine.selectAnchor(anchors, "pin_dougong_bell")
        assertTrue(focus.found)
        val livePos = snapped.state.parts.getValue(focus.partId!!).position
        val worldPin = engine.resolveWorldPosition(focus.anchor!!, livePos)
        assertTrue(MortiseAssemblyEngine.isValidVec3(worldPin))
        assertTrue(snapped.state.cutaway.enabled)
        assertEquals(StudioLightingPreset.GALLERY_ZENITH, focus.state.lightingPreset)
    }
}
