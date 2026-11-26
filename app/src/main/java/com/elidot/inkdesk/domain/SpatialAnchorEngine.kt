package com.elidot.inkdesk.domain

import com.elidot.inkdesk.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class StudioLightingPreset(
    val labelZh: String,
    val labelEn: String,
    val kelvin: Int,
    val descriptionZh: String,
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val keyColorHex: Long,
    val keyIntensity: Float,
    val rimColorHex: Long,
    val rimIntensity: Float,
    val hemiSkyHex: Long,
    val hemiGroundHex: Long,
    val hemiIntensity: Float,
    val emberColorHex: Long,
    val emberIntensity: Float,
    val exposure: Float,
    val envMapIntensity: Float
) {
    KYOTO_MORNING(
        labelZh = "京都晨光",
        labelEn = "Kyoto Morning",
        kelvin = 5200,
        descriptionZh = "斜角 48° 柔暖自然晨光，凸显桧木年轮漫反射与清晰榫槽层次",
        azimuthDeg = 38f,
        elevationDeg = 48f,
        keyColorHex = 0xFFF6E8L,
        keyIntensity = 1.95f,
        rimColorHex = 0xE6BD80L,
        rimIntensity = 1.05f,
        hemiSkyHex = 0xFAF7F1L,
        hemiGroundHex = 0x625A4EL,
        hemiIntensity = 0.94f,
        emberColorHex = 0xD98548L,
        emberIntensity = 0.38f,
        exposure = 1.06f,
        envMapIntensity = 1.22f
    ),
    GALLERY_ZENITH(
        labelZh = "美术馆顶光",
        labelEn = "Gallery Zenith",
        kelvin = 4400,
        descriptionZh = "74° 高位展馆洗墙顶光，拉高倒角卷杀雕塑明暗比与古铜金属光泽",
        azimuthDeg = 14f,
        elevationDeg = 74f,
        keyColorHex = 0xFFF0D6L,
        keyIntensity = 2.28f,
        rimColorHex = 0xD8A85CL,
        rimIntensity = 1.28f,
        hemiSkyHex = 0xF6F1E7L,
        hemiGroundHex = 0x4A453DL,
        hemiIntensity = 0.68f,
        emberColorHex = 0xC98A3AL,
        emberIntensity = 0.48f,
        exposure = 1.09f,
        envMapIntensity = 1.38f
    ),
    WARM_LANTERN(
        labelZh = "宋代烛映",
        labelEn = "Warm Lantern",
        kelvin = 2450,
        descriptionZh = "24° 低角度摇曳暖烛侧光，勾勒宋明书房沉静金铜高光与深邃木影",
        azimuthDeg = -52f,
        elevationDeg = 24f,
        keyColorHex = 0xFFBE76L,
        keyIntensity = 2.38f,
        rimColorHex = 0xE8924CL,
        rimIntensity = 1.65f,
        hemiSkyHex = 0xEBD2B0L,
        hemiGroundHex = 0x2D261FL,
        hemiIntensity = 0.46f,
        emberColorHex = 0xD64527L,
        emberIntensity = 1.48f,
        exposure = 1.16f,
        envMapIntensity = 1.46f
    )
}

enum class CalloutPinTone {
    CINNABAR,
    BRONZE
}

data class CameraFocusTarget(
    val yawRad: Float,
    val pitchRad: Float,
    val zoom: Float
)

data class SpatialCalloutAnchor(
    val id: String,
    val artifactId: String,
    val partId: String,
    val localOffset: Vec3,
    val titleZh: String,
    val craftCategoryZh: String,
    val eraOrPeriodZh: String,
    val mechanicsSummaryZh: String,
    val craftDetailZh: String,
    val tone: CalloutPinTone,
    val cameraFocus: CameraFocusTarget,
    val isCustom: Boolean,
    val createdAtIso: String
)

data class SpatialAnchorState(
    val activeArtifactId: String,
    val anchorsByArtifact: Map<String, List<SpatialCalloutAnchor>>,
    val selectedAnchorId: String?,
    val showPinsIn3D: Boolean,
    val lightingPreset: StudioLightingPreset
)

data class SelectAnchorResult(
    val found: Boolean,
    val anchor: SpatialCalloutAnchor?,
    val partId: String?,
    val cameraFocus: CameraFocusTarget?,
    val state: SpatialAnchorState
)

data class AnchorMutationResult(
    val ok: Boolean,
    val anchor: SpatialCalloutAnchor?,
    val errorZh: String?,
    val state: SpatialAnchorState
)

/**
 * Pure Kotlin bidirectional 3D spatial callout anchor & architectural lighting studio engine,
 * 100% mathematically isomorphic with WebXR `src/spatial/calloutAnchors.ts`.
 */
class SpatialAnchorEngine {
    private var customSeq = 0

    fun createState(
        activeArtifactId: String = "dougong_bell",
        customAnchors: List<SpatialCalloutAnchor> = emptyList(),
        lightingPreset: StudioLightingPreset = StudioLightingPreset.KYOTO_MORNING
    ): SpatialAnchorState {
        val grouped = mutableMapOf<String, MutableList<SpatialCalloutAnchor>>()
        for (pin in BUILTIN_CALLOUT_ANCHORS) {
            grouped.getOrPut(pin.artifactId) { mutableListOf() }.add(pin)
        }
        for (custom in customAnchors) {
            val cleanId = custom.id.trim()
            if (cleanId.isEmpty() || cleanId in BUILTIN_ANCHOR_IDS) continue
            if (custom.artifactId.isBlank() || custom.partId.isBlank() || custom.titleZh.isBlank()) continue
            val bucket = grouped.getOrPut(custom.artifactId) { mutableListOf() }
            if (bucket.any { it.id == cleanId }) continue
            bucket.add(
                custom.copy(
                    id = cleanId,
                    localOffset = sanitizeLocalOffset(custom.localOffset),
                    cameraFocus = sanitizeCameraFocus(custom.cameraFocus),
                    isCustom = true
                )
            )
        }
        val immutable = grouped.mapValues { it.value.toList() }
        val activeFirst = immutable[activeArtifactId]?.firstOrNull()?.id
        return SpatialAnchorState(
            activeArtifactId = activeArtifactId,
            anchorsByArtifact = immutable,
            selectedAnchorId = activeFirst,
            showPinsIn3D = true,
            lightingPreset = lightingPreset
        )
    }

    fun anchorsFor(state: SpatialAnchorState, artifactId: String = state.activeArtifactId): List<SpatialCalloutAnchor> =
        state.anchorsByArtifact[artifactId].orEmpty()

    fun selectedAnchor(state: SpatialAnchorState): SpatialCalloutAnchor? {
        val id = state.selectedAnchorId ?: return null
        return state.anchorsByArtifact.values.flatten().firstOrNull { it.id == id }
    }

    fun switchArtifact(state: SpatialAnchorState, nextArtifactId: String): SpatialAnchorState {
        val firstId = state.anchorsByArtifact[nextArtifactId]?.firstOrNull()?.id
        return state.copy(activeArtifactId = nextArtifactId, selectedAnchorId = firstId)
    }

    fun selectAnchor(state: SpatialAnchorState, anchorId: String): SelectAnchorResult {
        for ((artifactId, list) in state.anchorsByArtifact) {
            val found = list.firstOrNull { it.id == anchorId }
            if (found != null) {
                val next = state.copy(activeArtifactId = artifactId, selectedAnchorId = found.id)
                return SelectAnchorResult(
                    found = true,
                    anchor = found,
                    partId = found.partId,
                    cameraFocus = found.cameraFocus,
                    state = next
                )
            }
        }
        return SelectAnchorResult(false, null, null, null, state)
    }

    fun syncSelectedAnchorToPart(
        state: SpatialAnchorState,
        artifactId: String,
        partId: String
    ): SpatialAnchorState {
        val matched = state.anchorsByArtifact[artifactId]?.firstOrNull { it.partId == partId } ?: return state
        return state.copy(activeArtifactId = artifactId, selectedAnchorId = matched.id)
    }

    fun addCustomAnchor(
        state: SpatialAnchorState,
        artifactId: String,
        partId: String,
        titleZh: String,
        craftDetailZh: String,
        craftCategoryZh: String = "自定义空间钉",
        eraOrPeriodZh: String = "现场研学批注",
        localOffset: Vec3? = null,
        tone: CalloutPinTone = CalloutPinTone.CINNABAR,
        cameraFocus: CameraFocusTarget? = null,
        nowIso: String = "2026-11-25T10:00:00+08:00"
    ): AnchorMutationResult {
        val cleanTitle = titleZh.trim()
        val cleanDetail = craftDetailZh.trim()
        if (cleanTitle.isEmpty()) {
            return AnchorMutationResult(false, null, "请先输入空间批注钉标题（不能为空）。", state)
        }
        if (cleanDetail.isEmpty()) {
            return AnchorMutationResult(false, null, "请补充构造力学或工艺批注内容（不能为空）。", state)
        }
        if (artifactId.isBlank() || partId.isBlank()) {
            return AnchorMutationResult(false, null, "未指定绑定的三维展品或构件。", state)
        }

        val existing = state.anchorsByArtifact[artifactId].orEmpty()
        val customCount = existing.count { it.isCustom }
        var candidateId: String
        do {
            customSeq += 1
            candidateId = "pin_custom_${artifactId}_$customSeq"
        } while (existing.any { it.id == candidateId } || candidateId in BUILTIN_ANCHOR_IDS)
        val defaultOffset = Vec3(
            x = (if (customCount % 2 == 0) 1f else -1f) * (0.26f + (customCount % 3) * 0.08f),
            y = 0.22f + (customCount % 3) * 0.07f,
            z = 0.24f
        )
        val defaultFocus = CameraFocusTarget(
            yawRad = (if (customCount % 2 == 0) 1f else -1f) * 0.62f,
            pitchRad = 0.26f,
            zoom = 3.45f
        )
        val safeTitle = cleanTitle.take(MAX_TITLE_LENGTH)
        val safeDetail = cleanDetail.take(MAX_DETAIL_LENGTH)
        val anchor = SpatialCalloutAnchor(
            id = candidateId,
            artifactId = artifactId,
            partId = partId,
            localOffset = sanitizeLocalOffset(localOffset ?: defaultOffset),
            titleZh = safeTitle,
            craftCategoryZh = craftCategoryZh.trim().ifEmpty { "自定义空间钉" },
            eraOrPeriodZh = eraOrPeriodZh.trim().ifEmpty { "现场研学批注" },
            mechanicsSummaryZh = safeDetail.take(64),
            craftDetailZh = safeDetail,
            tone = tone,
            cameraFocus = sanitizeCameraFocus(cameraFocus, defaultFocus),
            isCustom = true,
            createdAtIso = nowIso
        )

        val updatedMap = state.anchorsByArtifact.toMutableMap()
        updatedMap[artifactId] = existing + anchor
        val next = state.copy(
            activeArtifactId = artifactId,
            anchorsByArtifact = updatedMap,
            selectedAnchorId = anchor.id
        )
        return AnchorMutationResult(true, anchor, null, next)
    }

    fun deleteCustomAnchor(state: SpatialAnchorState, anchorId: String): AnchorMutationResult {
        for ((artifactId, list) in state.anchorsByArtifact) {
            val target = list.firstOrNull { it.id == anchorId } ?: continue
            if (!target.isCustom) {
                return AnchorMutationResult(
                    ok = false,
                    anchor = target,
                    errorZh = "馆藏标准构造锚点不可删除，仅支持删除自定义空间批注钉。",
                    state = state
                )
            }
            val filtered = list.filterNot { it.id == anchorId }
            val updatedMap = state.anchorsByArtifact.toMutableMap()
            updatedMap[artifactId] = filtered
            val nextSelected = if (state.selectedAnchorId == anchorId) {
                filtered.firstOrNull()?.id
            } else {
                state.selectedAnchorId
            }
            return AnchorMutationResult(
                ok = true,
                anchor = target,
                errorZh = null,
                state = state.copy(anchorsByArtifact = updatedMap, selectedAnchorId = nextSelected)
            )
        }
        return AnchorMutationResult(false, null, "未找到锚点 $anchorId。", state)
    }

    fun setLightingPreset(state: SpatialAnchorState, preset: StudioLightingPreset): SpatialAnchorState =
        state.copy(lightingPreset = preset)

    fun setShowPinsIn3D(state: SpatialAnchorState, visible: Boolean): SpatialAnchorState =
        state.copy(showPinsIn3D = visible)

    fun directionalLightVector(preset: StudioLightingPreset, radius: Float = 5.8f): Vec3 {
        val safeR = if (radius.isFinite() && radius > 0f) radius else 5.8f
        val az = preset.azimuthDeg * PI / 180.0
        val el = preset.elevationDeg * PI / 180.0
        return Vec3(
            x = (safeR * cos(el) * sin(az)).toFloat(),
            y = (safeR * sin(el)).toFloat(),
            z = (safeR * cos(el) * cos(az)).toFloat()
        )
    }

    fun resolveWorldPosition(anchor: SpatialCalloutAnchor, partLivePosition: Vec3): Vec3 {
        val safePart = if (MortiseAssemblyEngine.isValidVec3(partLivePosition)) partLivePosition else Vec3.ZERO
        return safePart + sanitizeLocalOffset(anchor.localOffset)
    }

    fun sanitizeCameraFocus(
        focus: CameraFocusTarget?,
        fallback: CameraFocusTarget = CameraFocusTarget(0.56f, 0.25f, 3.5f)
    ): CameraFocusTarget {
        if (focus == null || !focus.yawRad.isFinite() || !focus.pitchRad.isFinite() || !focus.zoom.isFinite()) {
            return fallback
        }
        return CameraFocusTarget(
            yawRad = focus.yawRad,
            pitchRad = focus.pitchRad.coerceIn(-1.0f, 1.0f),
            zoom = focus.zoom.coerceIn(2.4f, 5.2f)
        )
    }

    fun sanitizeLocalOffset(offset: Vec3): Vec3 {
        if (!MortiseAssemblyEngine.isValidVec3(offset)) {
            return Vec3(0.28f, 0.16f, 0.24f)
        }
        return Vec3(
            x = offset.x.coerceIn(-MAX_LOCAL_OFFSET_M, MAX_LOCAL_OFFSET_M),
            y = offset.y.coerceIn(-MAX_LOCAL_OFFSET_M, MAX_LOCAL_OFFSET_M),
            z = offset.z.coerceIn(-MAX_LOCAL_OFFSET_M, MAX_LOCAL_OFFSET_M)
        )
    }

    companion object {
        const val MAX_LOCAL_OFFSET_M: Float = 0.85f
        const val MAX_TITLE_LENGTH: Int = 36
        const val MAX_DETAIL_LENGTH: Int = 260

        val BUILTIN_CALLOUT_ANCHORS: List<SpatialCalloutAnchor> = listOf(
            // Yongle Bell Dougong (4 pins)
            SpatialCalloutAnchor(
                id = "pin_dougong_ludou",
                artifactId = "dougong_bell",
                partId = "ludou",
                localOffset = Vec3(0.34f, 0.14f, 0.24f),
                titleZh = "十字斗口模数基座",
                craftCategoryZh = "力学支点",
                eraOrPeriodZh = "明永乐十八年（约 1420）· 官式斗口制",
                mechanicsSummaryZh = "上部双轴悬挑弯矩在此转化为柱头均匀轴压。",
                craftDetailZh = "坐斗开正交十字斗口，华拱与泥道拱互锁咬合，在地震水平剪力下通过木纤维微幅摩擦耗能。",
                tone = CalloutPinTone.BRONZE,
                cameraFocus = CameraFocusTarget(0.42f, 0.22f, 3.45f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_dougong_ang",
                artifactId = "dougong_bell",
                partId = "ang_lever",
                localOffset = Vec3(0.18f, 0.22f, 0.42f),
                titleZh = "象鼻下昂斜杠杆",
                craftCategoryZh = "悬挑配重",
                eraOrPeriodZh = "明代官式重檐五踩 · 无铁钉结构",
                mechanicsSummaryZh = "以屋顶内侧自重为配重，抵消深远外檐悬挑倾覆力矩。",
                craftDetailZh = "昂嘴削制象鼻斜坡，兼具雨水导流与力流指示作用，是藏经楼檐下最具辨识度的朱漆雕塑剪影。",
                tone = CalloutPinTone.CINNABAR,
                cameraFocus = CameraFocusTarget(0.88f, 0.28f, 3.35f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_dougong_huagong",
                artifactId = "dougong_bell",
                partId = "huagong",
                localOffset = Vec3(-0.28f, 0.18f, 0.32f),
                titleZh = "卷杀曲拱半榫锁口",
                craftCategoryZh = "木作工艺",
                eraOrPeriodZh = "营造法式四瓣卷杀 · 桧木/楠木精作",
                mechanicsSummaryZh = "拱底弧线削减受拉边缘应力集中，提升外挑刚度。",
                craftDetailZh = "与泥道拱正交开半榫，不借一钉一铆即锁定水平扭转自由度，便于百年后无损拆换单件糟朽木料。",
                tone = CalloutPinTone.BRONZE,
                cameraFocus = CameraFocusTarget(-0.62f, 0.24f, 3.55f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_dougong_bell",
                artifactId = "dougong_bell",
                partId = "yongle_crest",
                localOffset = Vec3(0.28f, 0.08f, 0.28f),
                titleZh = "大钟寺永乐蒲牢钟纽",
                craftCategoryZh = "声学地标",
                eraOrPeriodZh = "明永乐年间 · 铸造工期逾三年",
                mechanicsSummaryZh = "变壁厚抛物线铜壳激发出双基频拍音，声传数十里。",
                craftDetailZh = "致敬 PICO Space Program 决赛地标北京大钟寺：全钟内外阳铸铭文 23 万余字，铜锡铅配比达声学黄金律。",
                tone = CalloutPinTone.CINNABAR,
                cameraFocus = CameraFocusTarget(0.36f, -0.14f, 3.3f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            // Ming Bronze Armillary Sphere (4 pins)
            SpatialCalloutAnchor(
                id = "pin_armillary_horizon",
                artifactId = "armillary_sphere",
                partId = "horizon_ring",
                localOffset = Vec3(0.48f, 0.14f, 0.26f),
                titleZh = "二十四方位水准沟",
                craftCategoryZh = "基准校准",
                eraOrPeriodZh = "明正统二年—七年（1437—1442）",
                mechanicsSummaryZh = "环面精刻环形水槽，注水即成绝对水平天文参考面。",
                craftDetailZh = "紫金山天文台实存浑仪仍保留精密水准槽与 24 方位凸耳，保证数百年观星零倾角漂移。",
                tone = CalloutPinTone.BRONZE,
                cameraFocus = CameraFocusTarget(0.55f, 0.32f, 3.6f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_armillary_ecliptic",
                artifactId = "armillary_sphere",
                partId = "ecliptic_ring",
                localOffset = Vec3(-0.32f, 0.34f, 0.28f),
                titleZh = "23.5° 黄赤交角铆接",
                craftCategoryZh = "天球几何",
                eraOrPeriodZh = "明正统官铸 · 朱铜刻度镶嵌",
                mechanicsSummaryZh = "与赤道环固定交角 23.5°，直映太阳周年视运动轨迹。",
                craftDetailZh = "春秋二分点恰落于黄赤双环交接铆钉处，使二十四节气推演具备三维直观实体坐标。",
                tone = CalloutPinTone.CINNABAR,
                cameraFocus = CameraFocusTarget(-0.48f, 0.26f, 3.4f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_armillary_meridian",
                artifactId = "armillary_sphere",
                partId = "meridian_ring",
                localOffset = Vec3(0.08f, 0.46f, 0.34f),
                titleZh = "北京 39.9° 北极出地轴",
                craftCategoryZh = "旋转主架",
                eraOrPeriodZh = "北京古观象台纬度制式",
                mechanicsSummaryZh = "双子午环夹持 12 枚铜铆柱，承载三辰仪全重并定极轴。",
                craftDetailZh = "北极轴仰角按观象台地理纬度钻孔精配，令天穹周日视运动在双环间平滑复现。",
                tone = CalloutPinTone.BRONZE,
                cameraFocus = CameraFocusTarget(1.05f, 0.18f, 3.55f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_armillary_dragon",
                artifactId = "armillary_sphere",
                partId = "dragon_base",
                localOffset = Vec3(0.36f, 0.1f, -0.22f),
                titleZh = "四柱云龙抗风底座",
                craftCategoryZh = "铸铜结构",
                eraOrPeriodZh = "明代宫廷失蜡精铸 · 重逾数吨",
                mechanicsSummaryZh = "四柱 S 形斜撑构成空间三角稳固基座，抑制高台风振。",
                craftDetailZh = "行云盘龙既是皇家仪制纹饰，也通过变截面铸铜腿把数吨环体荷载稳稳导入十字底梁。",
                tone = CalloutPinTone.CINNABAR,
                cameraFocus = CameraFocusTarget(0.68f, -0.1f, 3.65f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            // Song-Ming Xuande Censer (4 pins)
            SpatialCalloutAnchor(
                id = "pin_censer_lid",
                artifactId = "xuande_censer",
                partId = "lotus_lid",
                localOffset = Vec3(0.28f, 0.18f, 0.18f),
                titleZh = "十二瓣莲盖伯努利烟道",
                craftCategoryZh = "热流体力学",
                eraOrPeriodZh = "明宣德三年（1428）· 失蜡镂空铸造",
                mechanicsSummaryZh = "顶孔与周缘莲瓣形成微压差，稳定层流香烟笔直缓升。",
                craftDetailZh = "盖沿子母口严密咬合以控氧防侧风，让沉香细烟聚而不散，营造书房静心视觉焦点。",
                tone = CalloutPinTone.BRONZE,
                cameraFocus = CameraFocusTarget(0.5f, 0.34f, 3.35f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_censer_tray",
                artifactId = "xuande_censer",
                partId = "incense_tray",
                localOffset = Vec3(-0.32f, 0.14f, 0.24f),
                titleZh = "四圈沉香篆 · 两刻半计时",
                craftCategoryZh = "书房仪式",
                eraOrPeriodZh = "宋代文人香谱 · 约 25 分钟一篆",
                mechanicsSummaryZh = "低导热多孔香灰床供氧隔热，匀速缓燃不焦不熄。",
                craftDetailZh = "古人打香篆以一盘燃尽约两刻半（近 25 分钟）计功课，与左翼沉浸番茄专注钟跨越千年同频。",
                tone = CalloutPinTone.CINNABAR,
                cameraFocus = CameraFocusTarget(-0.45f, 0.42f, 3.2f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_censer_body",
                artifactId = "xuande_censer",
                partId = "censer_body",
                localOffset = Vec3(0.38f, 0.04f, 0.22f),
                titleZh = "十二炼栗壳包浆厚胎",
                craftCategoryZh = "冶金工艺",
                eraOrPeriodZh = "明代风磨铜十二炼 · 栗壳色皮壳",
                mechanicsSummaryZh = "高热容厚铜壁均匀蓄热，令沉香油脂温和挥发无烟焦味。",
                craftDetailZh = "在宋代烛映 2450K 侧光下，64 段精密旋削铜腹呈现深邃温润的秋栗金铜高光过渡。",
                tone = CalloutPinTone.BRONZE,
                cameraFocus = CameraFocusTarget(0.72f, 0.12f, 3.3f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_censer_ears",
                artifactId = "xuande_censer",
                partId = "bridge_ears",
                localOffset = Vec3(0.42f, 0.16f, -0.14f),
                titleZh = "对称桥耳热阻结构",
                craftCategoryZh = "人因设计",
                eraOrPeriodZh = "宋韵明炉 · 江南案头清供典范",
                mechanicsSummaryZh = "收窄截面积构成热阻桥，减少炉腹高温传至双耳持握区。",
                craftDetailZh = "宣德炉谱以耳式定雅俗，流畅弧耳兼顾移炉防烫手功能与东方器物轮廓张力。",
                tone = CalloutPinTone.CINNABAR,
                cameraFocus = CameraFocusTarget(1.24f, 0.2f, 3.4f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            // Spatial Origami Pagoda (4 pins)
            SpatialCalloutAnchor(
                id = "pin_pagoda_shell",
                artifactId = "origami_pagoda",
                partId = "tier1_shell",
                localOffset = Vec3(0.36f, 0.12f, 0.24f),
                titleZh = "三浦交替山谷折痕筒壳",
                craftCategoryZh = "薄壳力学",
                eraOrPeriodZh = "当代参数化计算构型（2026）",
                mechanicsSummaryZh = "折面几何显著提高截面惯性矩，薄板可承受百倍自重轴压。",
                craftDetailZh = "单片平面折网无需胶水即可空间展开锁形，将古典八角塔身转译为可计算折纸薄壳。",
                tone = CalloutPinTone.BRONZE,
                cameraFocus = CameraFocusTarget(0.52f, 0.18f, 3.55f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_pagoda_wings",
                artifactId = "origami_pagoda",
                partId = "tier2_wings",
                localOffset = Vec3(-0.36f, 0.16f, 0.26f),
                titleZh = "菱形飞檐环向加劲肋",
                craftCategoryZh = "抗屈曲构造",
                eraOrPeriodZh = "致敬应县木塔八角收分比例",
                mechanicsSummaryZh = "外挑菱形折翼抑制筒壳侧向局部屈曲，同时柔化顶部漫反射。",
                craftDetailZh = "桧木暖色与竹叶苔绿折面搭配，避免高反光视觉疲劳，适配 XR 头显长时间研读。",
                tone = CalloutPinTone.BRONZE,
                cameraFocus = CameraFocusTarget(-0.64f, 0.26f, 3.45f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_pagoda_waist",
                artifactId = "origami_pagoda",
                partId = "waist_rib",
                localOffset = Vec3(0.32f, 0.1f, -0.22f),
                titleZh = "朱砂腰檐力学锁环",
                craftCategoryZh = "层间卡扣",
                eraOrPeriodZh = "仿古建平坐层构造 · 黄金分割腰线",
                mechanicsSummaryZh = "刚性闭环箍紧上下折面径向扩张，实现快速插接装配。",
                craftDetailZh = "以极克制朱砂漆木环点睛全塔腰线，在空间剖面切片模式下可清晰透视内部锁槽。",
                tone = CalloutPinTone.CINNABAR,
                cameraFocus = CameraFocusTarget(0.84f, 0.24f, 3.35f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            ),
            SpatialCalloutAnchor(
                id = "pin_pagoda_spire",
                artifactId = "origami_pagoda",
                partId = "bronze_spire",
                localOffset = Vec3(0.24f, 0.18f, 0.24f),
                titleZh = "鎏金七重相轮塔刹",
                craftCategoryZh = "竖向调谐",
                eraOrPeriodZh = "东方古塔相轮意象 · 古铜精铸",
                mechanicsSummaryZh = "中心穿心轴杆拉结采光折穹并充当顶部配重调谐标尺。",
                craftDetailZh = "在美术馆顶光 4400K 照射下，七重相轮边缘呈现锐利古铜高光，收束整塔竖向节奏。",
                tone = CalloutPinTone.CINNABAR,
                cameraFocus = CameraFocusTarget(0.3f, 0.38f, 3.3f),
                isCustom = false,
                createdAtIso = "2026-11-25T09:00:00+08:00"
            )
        )

        val BUILTIN_ANCHOR_IDS: Set<String> = BUILTIN_CALLOUT_ANCHORS.map { it.id }.toSet()
    }
}
