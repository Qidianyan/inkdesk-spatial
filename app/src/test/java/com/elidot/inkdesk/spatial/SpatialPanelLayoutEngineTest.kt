package com.elidot.inkdesk.spatial

import com.elidot.inkdesk.model.LayoutPreset
import com.elidot.inkdesk.model.PanelId
import com.elidot.inkdesk.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialPanelLayoutEngineTest {

    private val engine = SpatialPanelLayoutEngine()

    @Test
    fun surroundArcPositionsThreeVisiblePanelsWithoutOverlap() {
        val poses = engine.computeLayout(LayoutPreset.SURROUND_ARC)
        assertEquals(3, poses.size)

        val left = poses.getValue(PanelId.AUDIO_FOCUS_LEFT)
        val center = poses.getValue(PanelId.STAGE_CENTER)
        val right = poses.getValue(PanelId.NOTES_INSPECTOR_RIGHT)

        assertTrue(left.visible && center.visible && right.visible)
        assertTrue("Left wing must be at negative X", left.positionMeters.x < -0.5f)
        assertEquals(0f, center.positionMeters.x, 1e-3f)
        assertTrue("Right wing must be at positive X", right.positionMeters.x > 0.5f)

        assertFalse("Left and center panels must not overlap", engine.hasHorizontalOverlap(left, center))
        assertFalse("Center and right panels must not overlap", engine.hasHorizontalOverlap(center, right))
        assertTrue(engine.isWithinComfortZone(left))
        assertTrue(engine.isWithinComfortZone(center))
        assertTrue(engine.isWithinComfortZone(right))
    }

    @Test
    fun focusSingleHidesCompanionWingsAndBringsStageCloser() {
        val surround = engine.computeLayout(LayoutPreset.SURROUND_ARC)
        val focus = engine.computeLayout(LayoutPreset.FOCUS_SINGLE)

        val focusStage = focus.getValue(PanelId.STAGE_CENTER)
        val surroundStage = surround.getValue(PanelId.STAGE_CENTER)

        assertTrue(focusStage.visible)
        assertFalse(focus.getValue(PanelId.AUDIO_FOCUS_LEFT).visible)
        assertFalse(focus.getValue(PanelId.NOTES_INSPECTOR_RIGHT).visible)
        assertTrue(
            "Focus stage should sit closer than surround stage",
            focusStage.depthMeters < surroundStage.depthMeters
        )
        assertTrue(
            "Focus stage should widen for detail inspection",
            focusStage.widthDp > surroundStage.widthDp
        )
    }

    @Test
    fun sideBySideAlignsStageAndNotesSymmetricallyWithoutCollision() {
        val poses = engine.computeLayout(LayoutPreset.SIDE_BY_SIDE_COMPARE)
        val stage = poses.getValue(PanelId.STAGE_CENTER)
        val notes = poses.getValue(PanelId.NOTES_INSPECTOR_RIGHT)
        val audio = poses.getValue(PanelId.AUDIO_FOCUS_LEFT)

        assertTrue(stage.visible && notes.visible && audio.visible)
        assertTrue(stage.positionMeters.x < 0f)
        assertTrue(notes.positionMeters.x > 0f)
        assertFalse(engine.hasHorizontalOverlap(stage, notes))
    }

    @Test
    fun radiusIsClampedToComfortEnvelopeAndFollowsHeadOffset() {
        val head = Vec3(0.4f, 1.6f, -0.3f)
        val tooClose = engine.computeLayout(LayoutPreset.SURROUND_ARC, headPosition = head, customRadiusMeters = 0.2f)
        val tooFar = engine.computeLayout(LayoutPreset.SURROUND_ARC, headPosition = head, customRadiusMeters = 8.0f)

        val closeStage = tooClose.getValue(PanelId.STAGE_CENTER)
        val farStage = tooFar.getValue(PanelId.STAGE_CENTER)

        assertEquals(SpatialPanelLayoutEngine.MIN_COMFORT_RADIUS_M, closeStage.depthMeters, 1e-4f)
        assertEquals(SpatialPanelLayoutEngine.MAX_COMFORT_RADIUS_M, farStage.depthMeters, 1e-4f)
        assertTrue(engine.isWithinComfortZone(closeStage, head))
        assertTrue(engine.isWithinComfortZone(farStage, head))
    }
}
