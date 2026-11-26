package com.elidot.inkdesk.domain

import com.elidot.inkdesk.model.ArtifactCatalog
import com.elidot.inkdesk.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class MortiseAssemblyEngineTest {

    private val engine = MortiseAssemblyEngine()

    @Test
    fun initializesOrderedStepsForAllFourExhibits() {
        for (exhibit in ArtifactCatalog.exhibits) {
            val session = engine.createSession(exhibit)
            val progress = engine.progressOf(session)
            assertEquals(exhibit.parts.size, session.steps.size)
            assertEquals(0, progress.lockedCount)
            assertEquals(exhibit.parts.size, progress.totalSteps)
            assertEquals(0f, progress.progressRatio, 1e-5f)
            assertFalse(progress.isComplete)
            assertEquals(session.steps.first().partId, progress.activePartId)
        }

        val dougong = engine.createSession(ArtifactCatalog.findArtifact("dougong_bell"))
        assertEquals(
            listOf("yongle_crest", "ludou", "huagong", "nidaogong", "jiaohudou", "ang_lever", "shuatou"),
            dougong.steps.map { it.partId }
        )
    }

    @Test
    fun rejectsSnapWhenDistanceExceedsTolerance() {
        val state = engine.createSession(ArtifactCatalog.findArtifact("dougong_bell"))
        val first = state.steps.first()
        val farPos = Vec3(
            first.targetPosition.x + MortiseAssemblyEngine.DEFAULT_SNAP_DISTANCE_METERS + 0.15f,
            first.targetPosition.y,
            first.targetPosition.z
        )
        val result = engine.attemptSnap(state, first.partId, farPos, 0f)
        assertFalse(result.snapped)
        assertEquals(SnapRejectReason.OUT_OF_DISTANCE, result.reason)
        assertFalse(result.withinDistance)
        assertTrue(result.withinAngle)
    }

    @Test
    fun rejectsSnapWhenAngleExceedsTolerance() {
        val state = engine.createSession(ArtifactCatalog.findArtifact("dougong_bell"))
        val first = state.steps.first()
        val result = engine.attemptSnap(
            state,
            first.partId,
            first.targetPosition,
            MortiseAssemblyEngine.DEFAULT_SNAP_ANGLE_DEG + 10f
        )
        assertFalse(result.snapped)
        assertEquals(SnapRejectReason.OUT_OF_ANGLE, result.reason)
        assertTrue(result.withinDistance)
        assertFalse(result.withinAngle)
    }

    @Test
    fun snapsWithinDualToleranceAndLocksExactTargetPose() {
        val state = engine.createSession(ArtifactCatalog.findArtifact("dougong_bell"))
        val first = state.steps.first()
        val nearPos = first.targetPosition + Vec3(0.06f, -0.05f, 0.04f)
        val result = engine.attemptSnap(state, first.partId, nearPos, -8f)
        assertTrue(result.snapped)
        assertEquals(SnapRejectReason.SNAPPED, result.reason)
        assertFalse(result.completedJustNow)
        val lockedPart = result.state.parts.getValue(first.partId)
        assertTrue(lockedPart.locked)
        assertEquals(first.targetPosition, lockedPart.position)
        assertEquals(0f, lockedPart.yawDeg, 1e-5f)
        assertEquals(state.steps[1].partId, engine.progressOf(result.state).activePartId)
    }

    @Test
    fun enforcesStrictSequenceGuardForOutOfOrderSnap() {
        val state = engine.createSession(ArtifactCatalog.findArtifact("dougong_bell"))
        val third = state.steps[2]
        val result = engine.attemptSnap(state, third.partId, third.targetPosition, 0f)
        assertFalse(result.snapped)
        assertEquals(SnapRejectReason.OUT_OF_ORDER, result.reason)
        assertTrue(result.state.lockedOrder.isEmpty())
    }

    @Test
    fun completesFullAssemblySequenceAndDetectsCompletion() {
        var state = engine.createSession(ArtifactCatalog.findArtifact("dougong_bell"))
        state.steps.forEachIndexed { idx, step ->
            val result = engine.attemptSnap(state, step.partId, step.targetPosition, 0f)
            assertTrue(result.snapped)
            assertEquals(idx == state.steps.lastIndex, result.completedJustNow)
            state = result.state
        }
        val progress = engine.progressOf(state)
        assertTrue(progress.isComplete)
        assertEquals(7, progress.lockedCount)
        assertEquals(1f, progress.progressRatio, 1e-5f)
        assertNull(progress.activePartId)

        val repeated = engine.attemptSnap(state, "ludou", state.steps[1].targetPosition, 0f)
        assertFalse(repeated.snapped)
        assertEquals(SnapRejectReason.ALREADY_LOCKED, repeated.reason)
    }

    @Test
    fun supportsAccessibleTouchNudgeApproachRatioAssistAndReset() {
        var state = engine.createSession(ArtifactCatalog.findArtifact("armillary_sphere"))
        val first = state.steps.first()

        state = engine.setPartApproachRatio(state, first.partId, 0.5f, 6f)
        state = engine.nudgePart(state, first.partId, Vec3(0.02f, -0.02f, 0f), -4f)
        assertEquals(2f, state.parts.getValue(first.partId).yawDeg, 1e-4f)

        val assisted = engine.assistSnapActiveStep(state)
        assertTrue(assisted.snapped)
        assertEquals(1, engine.progressOf(assisted.state).lockedCount)

        val reset = engine.reset(assisted.state)
        assertEquals(0, engine.progressOf(reset).lockedCount)
        assertEquals(first.partId, engine.progressOf(reset).activePartId)
    }

    @Test
    fun clampsMultiAxisCutawayOffsetsAndClipsPointsCorrectly() {
        val state = engine.createSession(ArtifactCatalog.findArtifact("dougong_bell"))
        val high = engine.updateCutaway(state, enabled = true, axis = CutawayAxis.Y, offsetMeters = 99f)
        assertEquals(MortiseAssemblyEngine.CUTAWAY_MAX_OFFSET, high.cutaway.offsetMeters, 1e-5f)

        val low = engine.updateCutaway(state, enabled = true, axis = CutawayAxis.X, offsetMeters = -50f)
        assertEquals(MortiseAssemblyEngine.CUTAWAY_MIN_OFFSET, low.cutaway.offsetMeters, 1e-5f)
        assertEquals(Vec3(-1f, 0f, 0f), engine.cutawayPlaneNormal(CutawayAxis.X))
        assertEquals(Vec3(0f, -1f, 0f), engine.cutawayPlaneNormal(CutawayAxis.Y))
        assertEquals(Vec3(0f, 0f, -1f), engine.cutawayPlaneNormal(CutawayAxis.Z))

        val yPlane = CutawayPlaneState(enabled = true, axis = CutawayAxis.Y, offsetMeters = 0.1f)
        assertTrue(engine.isPointClipped(Vec3(0f, 0.45f, 0f), yPlane))
        assertFalse(engine.isPointClipped(Vec3(0f, -0.15f, 0f), yPlane))
    }

    @Test
    fun guardsAgainstNaNAndInfinityInPositionAnglesAndCutaway() {
        val state = engine.createSession(ArtifactCatalog.findArtifact("dougong_bell"))
        val first = state.steps.first()
        val origPos = state.parts.getValue(first.partId).position
        val origYaw = state.parts.getValue(first.partId).yawDeg

        val movedNan = engine.movePart(state, first.partId, Vec3(Float.NaN, 0f, 0f), Float.POSITIVE_INFINITY)
        assertEquals(origPos, movedNan.parts.getValue(first.partId).position)
        assertEquals(origYaw, movedNan.parts.getValue(first.partId).yawDeg, 1e-5f)

        val snapNan = engine.attemptSnap(state, first.partId, Vec3(0f, Float.NaN, 0f), 0f)
        assertFalse(snapNan.snapped)
        assertEquals(SnapRejectReason.INVALID_INPUT, snapNan.reason)

        val nanCut = engine.updateCutaway(state, enabled = true, offsetMeters = Float.NaN)
        assertTrue(nanCut.cutaway.offsetMeters.isFinite())
        assertEquals(0f, MortiseAssemblyEngine.normalizeAngleDeg(Float.NaN), 1e-5f)
        assertEquals(10f, MortiseAssemblyEngine.normalizeAngleDeg(370f), 1e-4f)
    }

    @Test
    fun preventsMovingLockedPartAndClampsExtremeDragRadius() {
        var state = engine.createSession(ArtifactCatalog.findArtifact("xuande_censer"))
        val first = state.steps[0]
        val second = state.steps[1]

        state = engine.attemptSnap(state, first.partId, first.targetPosition, 0f).state
        val moveLocked = engine.movePart(state, first.partId, Vec3(2f, 2f, 2f), 30f)
        assertEquals(first.targetPosition, moveLocked.parts.getValue(first.partId).position)

        val farMove = engine.movePart(state, second.partId, Vec3(50f, 50f, 50f), 10f)
        val p = farMove.parts.getValue(second.partId).position
        val radius = sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
        assertTrue(radius <= MortiseAssemblyEngine.MAX_STAGE_RADIUS_METERS + 1e-3f)
    }

    @Test
    fun clampsFloatOverflowCoordinatesWithoutReturningUnclampedVector() {
        val clamped = MortiseAssemblyEngine.clampStagePosition(Vec3(1e20f, 1e20f, 1e20f))
        assertTrue(MortiseAssemblyEngine.isValidVec3(clamped))
        val radius = sqrt(clamped.x * clamped.x + clamped.y * clamped.y + clamped.z * clamped.z)
        assertEquals(MortiseAssemblyEngine.MAX_STAGE_RADIUS_METERS, radius, 1e-3f)
    }

    @Test
    fun sanitizesDoubleNaNCutawayFallbackToSafeDefault() {
        val sanitized = engine.sanitizeCutawayState(
            candidate = CutawayPlaneState(enabled = true, axis = CutawayAxis.Z, offsetMeters = Float.NaN),
            fallback = CutawayPlaneState(enabled = false, axis = CutawayAxis.Y, offsetMeters = Float.NaN)
        )
        assertTrue(sanitized.offsetMeters.isFinite())
        assertEquals(MortiseAssemblyEngine.DEFAULT_CUTAWAY_OFFSET, sanitized.offsetMeters, 1e-5f)
    }

    @Test
    fun exposesCompleteAllApproachRatioAndDedicatedHintsAcrossAllExhibits() {
        for (exhibit in ArtifactCatalog.exhibits) {
            val session = engine.createSession(exhibit)
            for (step in session.steps) {
                assertFalse("Missing dedicated hint for ${step.partId}", step.guideHintZh.contains("对准铜金磁吸环"))
            }
            val firstId = session.steps.first().partId
            assertEquals(0f, engine.computePartApproachRatio(session, firstId), 1e-4f)
            val half = engine.setPartApproachRatio(session, firstId, 0.5f)
            assertEquals(0.5f, engine.computePartApproachRatio(half, firstId), 2e-2f)
            val full = engine.completeAll(session)
            assertTrue(engine.progressOf(full).isComplete)
            assertEquals(1f, engine.computePartApproachRatio(full, firstId), 1e-5f)
        }
    }
}
