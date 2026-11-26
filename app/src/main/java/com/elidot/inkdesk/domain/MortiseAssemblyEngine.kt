package com.elidot.inkdesk.domain

import com.elidot.inkdesk.model.ArtifactPart
import com.elidot.inkdesk.model.SpatialArtifact
import com.elidot.inkdesk.model.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class CutawayAxis(val labelZh: String) {
    Y("水平剖面 Y"),
    X("纵切剖面 X"),
    Z("立面剖面 Z")
}

data class CutawayPlaneState(
    val enabled: Boolean = false,
    val axis: CutawayAxis = CutawayAxis.Y,
    val offsetMeters: Float = MortiseAssemblyEngine.DEFAULT_CUTAWAY_OFFSET
)

data class AssemblyStepSpec(
    val order: Int,
    val partId: String,
    val nameZh: String,
    val targetPosition: Vec3,
    val stagedPosition: Vec3,
    val initialYawDeg: Float,
    val guideHintZh: String,
    val prerequisitePartId: String?
)

data class AssemblyPartRuntime(
    val partId: String,
    val order: Int,
    val position: Vec3,
    val yawDeg: Float,
    val locked: Boolean
)

enum class SnapRejectReason {
    SNAPPED,
    OUT_OF_ORDER,
    OUT_OF_DISTANCE,
    OUT_OF_ANGLE,
    ALREADY_LOCKED,
    UNKNOWN_PART,
    INVALID_INPUT
}

data class SnapProximityMetrics(
    val valid: Boolean,
    val partId: String,
    val distanceMeters: Float,
    val angleErrorDeg: Float,
    val withinDistance: Boolean,
    val withinAngle: Boolean,
    val isOrderValid: Boolean,
    val canSnap: Boolean
)

data class MortiseAssemblyProgress(
    val lockedCount: Int,
    val totalSteps: Int,
    val progressRatio: Float,
    val isComplete: Boolean,
    val activeStep: AssemblyStepSpec?,
    val activePartId: String?
)

data class MortiseAssemblyState(
    val artifactId: String,
    val steps: List<AssemblyStepSpec>,
    val parts: Map<String, AssemblyPartRuntime>,
    val lockedOrder: List<String>,
    val activeStepIndex: Int,
    val snapDistanceMeters: Float,
    val snapAngleDeg: Float,
    val cutaway: CutawayPlaneState,
    val lastEventMessageZh: String
)

data class SnapAttemptResult(
    val snapped: Boolean,
    val reason: SnapRejectReason,
    val distanceMeters: Float,
    val angleErrorDeg: Float,
    val withinDistance: Boolean,
    val withinAngle: Boolean,
    val completedJustNow: Boolean,
    val messageZh: String,
    val state: MortiseAssemblyState
)

/**
 * Pure Kotlin interactive mortise-and-tenon magnetic snap assembly & real-time architectural
 * cutaway engine — 100% mathematically isomorphic with WebXR `src/assembly/mortiseAssembly.ts`.
 * Drives both PICO Spatial SDK hand-pinch 3D ray drag and accessible touch/controller nudges.
 */
class MortiseAssemblyEngine(
    val defaultSnapDistanceMeters: Float = DEFAULT_SNAP_DISTANCE_METERS,
    val defaultSnapAngleDeg: Float = DEFAULT_SNAP_ANGLE_DEG
) {
    fun buildSteps(artifact: SpatialArtifact): List<AssemblyStepSpec> {
        val preferredOrder = ARTIFACT_STEP_ORDER[artifact.id] ?: artifact.parts.map { it.id }
        val byId = artifact.parts.associateBy { it.id }
        val ordered = mutableListOf<ArtifactPart>()
        for (id in preferredOrder) {
            byId[id]?.let { ordered.add(it) }
        }
        for (part in artifact.parts) {
            if (ordered.none { it.id == part.id }) {
                ordered.add(part)
            }
        }
        return ordered.mapIndexed { idx, part ->
            AssemblyStepSpec(
                order = idx,
                partId = part.id,
                nameZh = part.nameZh,
                targetPosition = part.basePosition,
                stagedPosition = buildStagedPosition(part, idx),
                initialYawDeg = buildInitialYawDeg(idx),
                guideHintZh = STEP_GUIDE_HINTS[part.id]
                    ?: "拖拽或微调「${part.nameZh}」对准铜金磁吸环落位咬合。",
                prerequisitePartId = if (idx > 0) ordered[idx - 1].id else null
            )
        }
    }

    fun createSession(
        artifact: SpatialArtifact,
        snapDistanceMeters: Float = defaultSnapDistanceMeters,
        snapAngleDeg: Float = defaultSnapAngleDeg,
        cutaway: CutawayPlaneState = CutawayPlaneState()
    ): MortiseAssemblyState {
        val steps = buildSteps(artifact)
        val parts = steps.associate { step ->
            step.partId to AssemblyPartRuntime(
                partId = step.partId,
                order = step.order,
                position = step.stagedPosition,
                yawDeg = step.initialYawDeg,
                locked = false
            )
        }
        val safeDist = if (snapDistanceMeters.isFinite() && snapDistanceMeters > 0f) {
            snapDistanceMeters
        } else {
            DEFAULT_SNAP_DISTANCE_METERS
        }
        val safeAngle = if (snapAngleDeg.isFinite() && snapAngleDeg > 0f) {
            snapAngleDeg
        } else {
            DEFAULT_SNAP_ANGLE_DEG
        }
        val first = steps.firstOrNull()
        val msg = if (first != null) {
            "第 1 / ${steps.size} 步：请将「${first.nameZh}」拖至铜金磁吸环对齐咬合。"
        } else {
            "请开始榫卯拼装。"
        }
        return MortiseAssemblyState(
            artifactId = artifact.id,
            steps = steps,
            parts = parts,
            lockedOrder = emptyList(),
            activeStepIndex = 0,
            snapDistanceMeters = safeDist,
            snapAngleDeg = safeAngle,
            cutaway = sanitizeCutawayState(cutaway),
            lastEventMessageZh = msg
        )
    }

    fun progressOf(state: MortiseAssemblyState): MortiseAssemblyProgress {
        val total = state.steps.size
        val locked = state.lockedOrder.size
        val active = state.steps.getOrNull(state.activeStepIndex)
        return MortiseAssemblyProgress(
            lockedCount = locked,
            totalSteps = total,
            progressRatio = if (total == 0) 1f else locked.toFloat() / total.toFloat(),
            isComplete = total > 0 && locked >= total,
            activeStep = active,
            activePartId = active?.partId
        )
    }

    fun evaluateProximity(
        state: MortiseAssemblyState,
        partId: String,
        candidatePos: Vec3? = null,
        candidateYawDeg: Float? = null
    ): SnapProximityMetrics {
        val step = state.steps.firstOrNull { it.partId == partId }
        val runtime = state.parts[partId]
        if (step == null || runtime == null) {
            return SnapProximityMetrics(
                valid = false,
                partId = partId,
                distanceMeters = Float.POSITIVE_INFINITY,
                angleErrorDeg = 180f,
                withinDistance = false,
                withinAngle = false,
                isOrderValid = false,
                canSnap = false
            )
        }

        val pos = candidatePos ?: runtime.position
        val yaw = candidateYawDeg ?: runtime.yawDeg
        if (!isValidVec3(pos) || !yaw.isFinite()) {
            return SnapProximityMetrics(
                valid = false,
                partId = partId,
                distanceMeters = Float.POSITIVE_INFINITY,
                angleErrorDeg = 180f,
                withinDistance = false,
                withinAngle = false,
                isOrderValid = false,
                canSnap = false
            )
        }

        val distance = pos.distanceTo(step.targetPosition)
        val angleErr = abs(normalizeAngleDeg(yaw))
        val activeStep = state.steps.getOrNull(state.activeStepIndex)
        val orderValid = activeStep != null && activeStep.partId == partId && !runtime.locked
        val withinDist = distance <= state.snapDistanceMeters
        val withinAng = angleErr <= state.snapAngleDeg

        return SnapProximityMetrics(
            valid = true,
            partId = partId,
            distanceMeters = distance,
            angleErrorDeg = angleErr,
            withinDistance = withinDist,
            withinAngle = withinAng,
            isOrderValid = orderValid,
            canSnap = orderValid && withinDist && withinAng
        )
    }

    fun movePart(
        state: MortiseAssemblyState,
        partId: String,
        nextPos: Vec3,
        nextYawDeg: Float? = null
    ): MortiseAssemblyState {
        val runtime = state.parts[partId] ?: return state
        if (runtime.locked) return state

        val safePos = if (isValidVec3(nextPos)) clampStagePosition(nextPos) else runtime.position
        val safeYaw = when {
            nextYawDeg == null -> runtime.yawDeg
            nextYawDeg.isFinite() -> normalizeAngleDeg(nextYawDeg)
            else -> runtime.yawDeg
        }
        val updated = state.parts.toMutableMap()
        updated[partId] = runtime.copy(position = safePos, yawDeg = safeYaw)
        return state.copy(parts = updated)
    }

    /** Accessible fallback nudge for controller D-pad / Compose touch fine-tuning. */
    fun nudgePart(
        state: MortiseAssemblyState,
        partId: String,
        deltaPos: Vec3,
        deltaYawDeg: Float = 0f
    ): MortiseAssemblyState {
        val runtime = state.parts[partId] ?: return state
        if (runtime.locked || !isValidVec3(deltaPos) || !deltaYawDeg.isFinite()) return state
        return movePart(
            state = state,
            partId = partId,
            nextPos = runtime.position + deltaPos,
            nextYawDeg = runtime.yawDeg + deltaYawDeg
        )
    }

    fun setPartApproachRatio(
        state: MortiseAssemblyState,
        partId: String,
        approachRatio: Float,
        overrideYawDeg: Float? = null
    ): MortiseAssemblyState {
        val step = state.steps.firstOrNull { it.partId == partId } ?: return state
        val runtime = state.parts[partId] ?: return state
        if (runtime.locked || !approachRatio.isFinite()) return state
        val clamped = approachRatio.coerceIn(0f, 1f)
        val nextPos = step.stagedPosition.lerp(step.targetPosition, clamped)
        val nextYaw = if (overrideYawDeg != null && overrideYawDeg.isFinite()) overrideYawDeg else runtime.yawDeg
        return movePart(state, partId, nextPos, nextYaw)
    }

    fun attemptSnap(
        state: MortiseAssemblyState,
        partId: String,
        candidatePos: Vec3? = null,
        candidateYawDeg: Float? = null
    ): SnapAttemptResult {
        val step = state.steps.firstOrNull { it.partId == partId }
        val runtime = state.parts[partId]
        if (step == null || runtime == null) {
            return SnapAttemptResult(
                snapped = false,
                reason = SnapRejectReason.UNKNOWN_PART,
                distanceMeters = Float.POSITIVE_INFINITY,
                angleErrorDeg = 180f,
                withinDistance = false,
                withinAngle = false,
                completedJustNow = false,
                messageZh = "未找到构件 $partId。",
                state = state
            )
        }

        if (runtime.locked) {
            return SnapAttemptResult(
                snapped = false,
                reason = SnapRejectReason.ALREADY_LOCKED,
                distanceMeters = 0f,
                angleErrorDeg = 0f,
                withinDistance = true,
                withinAngle = true,
                completedJustNow = false,
                messageZh = "「${step.nameZh}」已完成咬合锁定。",
                state = state
            )
        }

        val pos = candidatePos ?: runtime.position
        val yaw = candidateYawDeg ?: runtime.yawDeg
        if (!isValidVec3(pos) || !yaw.isFinite()) {
            return SnapAttemptResult(
                snapped = false,
                reason = SnapRejectReason.INVALID_INPUT,
                distanceMeters = Float.POSITIVE_INFINITY,
                angleErrorDeg = 180f,
                withinDistance = false,
                withinAngle = false,
                completedJustNow = false,
                messageZh = "坐标或角度参数异常（NaN/Infinity），已保持上一有效位置。",
                state = state
            )
        }

        val activeStep = state.steps.getOrNull(state.activeStepIndex)
        val proximity = evaluateProximity(state, partId, pos, yaw)

        if (activeStep == null || activeStep.partId != partId) {
            val needName = activeStep?.nameZh ?: "前置构件"
            val msg = "拼装顺序保护：请先将「$needName」落位咬合，再安装「${step.nameZh}」。"
            return SnapAttemptResult(
                snapped = false,
                reason = SnapRejectReason.OUT_OF_ORDER,
                distanceMeters = proximity.distanceMeters,
                angleErrorDeg = proximity.angleErrorDeg,
                withinDistance = proximity.withinDistance,
                withinAngle = proximity.withinAngle,
                completedJustNow = false,
                messageZh = msg,
                state = state.copy(lastEventMessageZh = msg)
            )
        }

        val movedState = movePart(state, partId, pos, yaw)

        if (!proximity.withinDistance) {
            val msg = "距离榫口尚差 ${(proximity.distanceMeters * 1000f).toInt()} mm（需 ≤ ${(state.snapDistanceMeters * 1000f).toInt()} mm）。"
            return SnapAttemptResult(
                snapped = false,
                reason = SnapRejectReason.OUT_OF_DISTANCE,
                distanceMeters = proximity.distanceMeters,
                angleErrorDeg = proximity.angleErrorDeg,
                withinDistance = false,
                withinAngle = proximity.withinAngle,
                completedJustNow = false,
                messageZh = msg,
                state = movedState.copy(lastEventMessageZh = msg)
            )
        }

        if (!proximity.withinAngle) {
            val msg = "榫头偏角 ${"%.1f".format(proximity.angleErrorDeg)}° 尚未对齐（需 ≤ ${state.snapAngleDeg.toInt()}°）。"
            return SnapAttemptResult(
                snapped = false,
                reason = SnapRejectReason.OUT_OF_ANGLE,
                distanceMeters = proximity.distanceMeters,
                angleErrorDeg = proximity.angleErrorDeg,
                withinDistance = true,
                withinAngle = false,
                completedJustNow = false,
                messageZh = msg,
                state = movedState.copy(lastEventMessageZh = msg)
            )
        }

        val nextLockedOrder = state.lockedOrder + partId
        val nextActiveIndex = state.activeStepIndex + 1
        val completedJustNow = nextLockedOrder.size >= state.steps.size
        val nextStep = state.steps.getOrNull(nextActiveIndex)
        val msg = if (completedJustNow) {
            "全器 ${state.steps.size} 件榫卯严丝合缝咬合完成！"
        } else {
            "「${step.nameZh}」清脆咬合落位！下一步：对齐「${nextStep?.nameZh ?: ""}」。"
        }

        val nextParts = movedState.parts.toMutableMap()
        nextParts[partId] = movedState.parts.getValue(partId).copy(
            position = step.targetPosition,
            yawDeg = 0f,
            locked = true
        )
        val nextState = movedState.copy(
            parts = nextParts,
            lockedOrder = nextLockedOrder,
            activeStepIndex = nextActiveIndex,
            lastEventMessageZh = msg
        )

        return SnapAttemptResult(
            snapped = true,
            reason = SnapRejectReason.SNAPPED,
            distanceMeters = proximity.distanceMeters,
            angleErrorDeg = proximity.angleErrorDeg,
            withinDistance = true,
            withinAngle = true,
            completedJustNow = completedJustNow,
            messageZh = msg,
            state = nextState
        )
    }

    /** Accessibility one-tap snap assist for active step. */
    fun assistSnapActiveStep(state: MortiseAssemblyState): SnapAttemptResult {
        val active = state.steps.getOrNull(state.activeStepIndex)
            ?: return SnapAttemptResult(
                snapped = false,
                reason = SnapRejectReason.ALREADY_LOCKED,
                distanceMeters = 0f,
                angleErrorDeg = 0f,
                withinDistance = true,
                withinAngle = true,
                completedJustNow = false,
                messageZh = "全部构件均已咬合完成，可切换剖面切片透视内部榫槽。",
                state = state
            )
        return attemptSnap(state, active.partId, active.targetPosition, 0f)
    }

    fun reset(state: MortiseAssemblyState): MortiseAssemblyState {
        val parts = state.steps.associate { step ->
            step.partId to AssemblyPartRuntime(
                partId = step.partId,
                order = step.order,
                position = step.stagedPosition,
                yawDeg = step.initialYawDeg,
                locked = false
            )
        }
        val first = state.steps.firstOrNull()
        val msg = if (first != null) {
            "已一键复位散件：第 1 / ${state.steps.size} 步，请对齐「${first.nameZh}」。"
        } else {
            "已复位。"
        }
        return state.copy(
            parts = parts,
            lockedOrder = emptyList(),
            activeStepIndex = 0,
            lastEventMessageZh = msg
        )
    }

    fun updateCutaway(
        state: MortiseAssemblyState,
        enabled: Boolean = state.cutaway.enabled,
        axis: CutawayAxis = state.cutaway.axis,
        offsetMeters: Float = state.cutaway.offsetMeters
    ): MortiseAssemblyState {
        return state.copy(
            cutaway = sanitizeCutawayState(
                CutawayPlaneState(enabled = enabled, axis = axis, offsetMeters = offsetMeters),
                fallback = state.cutaway
            )
        )
    }

    fun cutawayPlaneNormal(axis: CutawayAxis): Vec3 = when (axis) {
        CutawayAxis.X -> Vec3(-1f, 0f, 0f)
        CutawayAxis.Y -> Vec3(0f, -1f, 0f)
        CutawayAxis.Z -> Vec3(0f, 0f, -1f)
    }

    fun isPointClipped(point: Vec3, cutaway: CutawayPlaneState): Boolean {
        if (!cutaway.enabled || !isValidVec3(point) || !cutaway.offsetMeters.isFinite()) {
            return false
        }
        val n = cutawayPlaneNormal(cutaway.axis)
        val signed = point.dot(n) + cutaway.offsetMeters
        return signed < 0f
    }

    fun sanitizeCutawayState(
        candidate: CutawayPlaneState,
        fallback: CutawayPlaneState = CutawayPlaneState()
    ): CutawayPlaneState {
        val safeOffset = if (candidate.offsetMeters.isFinite()) {
            candidate.offsetMeters.coerceIn(CUTAWAY_MIN_OFFSET, CUTAWAY_MAX_OFFSET)
        } else {
            fallback.offsetMeters.coerceIn(CUTAWAY_MIN_OFFSET, CUTAWAY_MAX_OFFSET)
        }
        return candidate.copy(offsetMeters = safeOffset)
    }

    private fun buildStagedPosition(part: ArtifactPart, orderIndex: Int): Vec3 {
        val lateralAngle = (if (orderIndex % 2 == 0) 1.0 else -1.0) * (0.45 + orderIndex * 0.24)
        val radialPush = 0.56f + (orderIndex % 3) * 0.14f
        val staged = Vec3(
            x = part.basePosition.x + part.explodeOffset.x * 0.72f + (sin(lateralAngle) * radialPush).toFloat(),
            y = part.basePosition.y + part.explodeOffset.y * 0.62f + 0.18f + orderIndex * 0.05f,
            z = part.basePosition.z + part.explodeOffset.z * 0.72f + (cos(lateralAngle) * 0.34).toFloat()
        )
        return clampStagePosition(staged)
    }

    private fun buildInitialYawDeg(orderIndex: Int): Float {
        val sign = if (orderIndex % 2 == 0) 1f else -1f
        return sign * (18f + (orderIndex % 3) * 8f)
    }

    companion object {
        const val DEFAULT_SNAP_DISTANCE_METERS: Float = 0.22f
        const val DEFAULT_SNAP_ANGLE_DEG: Float = 16f
        const val CUTAWAY_MIN_OFFSET: Float = -0.85f
        const val CUTAWAY_MAX_OFFSET: Float = 0.95f
        const val DEFAULT_CUTAWAY_OFFSET: Float = 0.22f
        const val MAX_STAGE_RADIUS_METERS: Float = 2.4f

        fun isValidVec3(v: Vec3): Boolean = v.x.isFinite() && v.y.isFinite() && v.z.isFinite()

        fun normalizeAngleDeg(deg: Float): Float {
            if (!deg.isFinite()) return 0f
            var normalized = (((deg + 180f) % 360f) + 360f) % 360f - 180f
            if (normalized == -180f) normalized = 180f
            return normalized
        }

        fun clampStagePosition(pos: Vec3): Vec3 {
            val len = sqrt(pos.x * pos.x + pos.y * pos.y + pos.z * pos.z)
            if (!len.isFinite() || len <= MAX_STAGE_RADIUS_METERS || len <= 1e-6f) {
                return pos
            }
            val scale = MAX_STAGE_RADIUS_METERS / len
            return Vec3(pos.x * scale, pos.y * scale, pos.z * scale)
        }

        private val ARTIFACT_STEP_ORDER: Map<String, List<String>> = mapOf(
            "dougong_bell" to listOf("yongle_crest", "ludou", "huagong", "nidaogong", "jiaohudou", "ang_lever", "shuatou"),
            "armillary_sphere" to listOf("dragon_base", "horizon_ring", "meridian_ring", "equatorial_ring", "ecliptic_ring", "sight_tube"),
            "xuande_censer" to listOf("cedar_stand", "tripod_feet", "censer_body", "bridge_ears", "incense_tray", "lotus_lid"),
            "origami_pagoda" to listOf("octa_plinth", "tier1_shell", "tier2_wings", "waist_rib", "upper_dome", "bronze_spire")
        )

        private val STEP_GUIDE_HINTS: Map<String, String> = mapOf(
            "yongle_crest" to "先安放永乐大钟蒲牢钟纽基准位，确立全构中轴。",
            "ludou" to "将栌斗（坐斗）对正中轴落位，展开十字斗口承托平面。",
            "huagong" to "将华拱沿前后向嵌入坐斗开口，形成第一级外挑悬臂。",
            "nidaogong" to "旋转泥道拱与华拱正交半榫咬合，锁定左右横系力。",
            "jiaohudou" to "落位交互斗与散斗垫群，分散拱端局部木纤维剪力。",
            "ang_lever" to "将朱漆下昂斜杠杆压入斗口，平衡屋檐外挑力矩。",
            "shuatou" to "压入顶层耍头与承重枋，彻底锁死整攒斗拱竖向自由度。"
        )
    }
}
