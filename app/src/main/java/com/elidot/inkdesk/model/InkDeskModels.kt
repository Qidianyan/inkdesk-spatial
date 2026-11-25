package com.elidot.inkdesk.model

import kotlin.math.sqrt

data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun distanceTo(other: Vec3): Float = (this - other).length()

    fun normalized(): Vec3 {
        val len = length()
        return if (len <= 1e-6f) ZERO else Vec3(x / len, y / len, z / len)
    }

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun lerp(target: Vec3, t: Float): Vec3 {
        val clamped = t.coerceIn(0f, 1f)
        return Vec3(
            x + (target.x - x) * clamped,
            y + (target.y - y) * clamped,
            z + (target.z - z) * clamped
        )
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

enum class PanelId(val windowId: String, val tag: String, val titleZh: String) {
    STAGE_CENTER("inkdesk_stage", "panel_stage_center", "空间器物与榫卯拆解台"),
    AUDIO_FOCUS_LEFT("inkdesk_audio_dock", "panel_audio_left", "空间声景锚点与专注坞"),
    NOTES_INSPECTOR_RIGHT("inkdesk_notes_inspector", "panel_notes_right", "空间手记与结构批注")
}

enum class LayoutPreset(val labelZh: String, val subtitleZh: String) {
    SURROUND_ARC("环绕模式", "三窗弧形环绕，沉浸研学协同"),
    FOCUS_SINGLE("专注单窗", "聚焦 3D 器物主舞台，收起双翼"),
    SIDE_BY_SIDE_COMPARE("并排对比", "3D 拆解台与结构批注平行对照")
}

enum class AmbientLightingTheme(
    val labelZh: String,
    val kelvin: Int,
    val surfaceHex: Long,
    val textHex: Long,
    val accentHex: Long
) {
    DAWN("晨曦", 5400, 0xFFEAE8E3, 0xFF232220, 0xFFD64527),
    AFTERNOON("午后", 4300, 0xFFE4E0D8, 0xFF211F1D, 0xFFC98A3A),
    CANDLELIGHT("烛夜", 2700, 0xFF171614, 0xFFF2F0EC, 0xFFE35638)
}

data class PanelPose(
    val panelId: PanelId,
    val visible: Boolean,
    val positionMeters: Vec3,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val widthDp: Float,
    val heightDp: Float,
    val depthMeters: Float
)

data class ArtifactPart(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val dynastyOrEra: String,
    val materialZh: String,
    val materialHex: Long,
    val basePosition: Vec3,
    val explodeOffset: Vec3,
    val dimensionsMm: Vec3,
    val mechanicsPrinciple: String,
    val historicalNote: String,
    val glbNodeName: String = id
)

data class SpatialArtifact(
    val id: String,
    val glbAssetPath: String,
    val titleZh: String,
    val titleEn: String,
    val subtitleZh: String,
    val eraZh: String,
    val summaryZh: String,
    val totalDimensionsMm: Vec3,
    val parts: List<ArtifactPart>
)

enum class SoundAnchorId(
    val titleZh: String,
    val subtitleZh: String,
    val accentHex: Long,
    val defaultPosition: Vec3
) {
    PINE_WIND("古刹松风", "左前松林微风穿堂", 0xFF3F5E46, Vec3(-1.35f, 0.35f, -1.10f)),
    EAVES_RAIN("檐下雨滴", "右前瓦当细雨轻敲", 0xFFC8A055, Vec3(1.30f, 0.45f, -0.95f)),
    CHARCOAL_TEA("炭火烹茶", "近侧泥炉松炭沸水", 0xFFD9532F, Vec3(-0.75f, -0.25f, -0.65f)),
    CHIME_BELLS("编钟泛音", "远景青铜礼乐余韵", 0xFFB0853E, Vec3(0.95f, 0.20f, -1.85f))
}

data class SpatialAudioAnchor(
    val id: SoundAnchorId,
    val enabled: Boolean,
    val gain: Float,
    val positionMeters: Vec3
)

data class SpatialAudioRenderState(
    val anchorId: SoundAnchorId,
    val active: Boolean,
    val distanceMeters: Float,
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
    val attenuation: Float,
    val leftGain: Float,
    val rightGain: Float
)

data class SpatialNote(
    val id: String,
    val artifactId: String,
    val partId: String,
    val partNameZh: String,
    val content: String,
    val pinned: Boolean,
    val createdAtIso: String
)

data class FocusTimerState(
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val completedSessions: Int = 0
) {
    val progress: Float
        get() = if (totalSeconds <= 0) 0f else (totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()

    fun formattedClock(): String {
        val mins = (remainingSeconds.coerceAtLeast(0)) / 60
        val secs = (remainingSeconds.coerceAtLeast(0)) % 60
        return "%02d:%02d".format(mins, secs)
    }
}

data class InkDeskSessionState(
    val activeArtifactId: String,
    val selectedPartId: String,
    val comparePartId: String?,
    val explodeProgress: Float,
    val crossSectionEnabled: Boolean,
    val sectionCutY: Float,
    val orbitYawDegrees: Float,
    val orbitPitchDegrees: Float,
    val layoutPreset: LayoutPreset,
    val ambientTheme: AmbientLightingTheme,
    val headYawDegrees: Float,
    val soundAnchors: List<SpatialAudioAnchor>,
    val focusTimer: FocusTimerState,
    val notes: List<SpatialNote>
)

object ArtifactCatalog {
    val exhibits: List<SpatialArtifact> = listOf(
        SpatialArtifact(
            id = "dougong_bell",
            glbAssetPath = "models/yongle_dougong.glb",
            titleZh = "永乐大钟斗拱榫卯",
            titleEn = "Yongle Bell Dougong Joinery",
            subtitleZh = "明代官式重檐五踩斗拱 · 无钉柔性抗震节点",
            eraZh = "明永乐年间（约 1420 年）",
            summaryZh = "以层叠木构件通过燕尾榫与十字开口咬合，将大屋檐悬挑荷载逐级分散传导至立柱，在地震中以摩擦耗能实现百年不倒。",
            totalDimensionsMm = Vec3(840f, 680f, 780f),
            parts = listOf(
                ArtifactPart(
                    id = "ludou",
                    nameZh = "栌斗（坐斗）",
                    nameEn = "Ludou Capital Block",
                    dynastyOrEra = "明代官式",
                    materialZh = "老料楠木",
                    materialHex = 0xFF7B5438,
                    basePosition = Vec3(0f, -0.42f, 0f),
                    explodeOffset = Vec3(0f, -0.58f, 0f),
                    dimensionsMm = Vec3(320f, 200f, 320f),
                    mechanicsPrinciple = "位于整组斗拱最底部柱头之上，以十字斗口承托华拱与泥道拱，将上部弯矩转化为均匀轴向压应力。",
                    historicalNote = "《营造法式》称大斗，明清官式称坐斗，其斗耳与斗腰比例决定整攒斗拱模数（斗口制）。"
                ),
                ArtifactPart(
                    id = "huagong",
                    nameZh = "华拱（翘）",
                    nameEn = "Huagong Cantilever Arm",
                    dynastyOrEra = "明代官式",
                    materialZh = "深雪松木",
                    materialHex = 0xFF9C6B43,
                    basePosition = Vec3(0f, -0.14f, 0f),
                    explodeOffset = Vec3(0f, -0.10f, 0.56f),
                    dimensionsMm = Vec3(140f, 160f, 660f),
                    mechanicsPrinciple = "垂直于墙面伸出的双臂悬挑梁，承担屋檐外跳弯矩，与泥道拱形成正交十字锁定。",
                    historicalNote = "单翘双昂结构中，华拱率先向外迈出一跳，为屋檐排水与深远出檐奠定第一级杠杆。"
                ),
                ArtifactPart(
                    id = "nidaogong",
                    nameZh = "泥道拱（正心瓜拱）",
                    nameEn = "Nidaogong Transverse Arm",
                    dynastyOrEra = "明代官式",
                    materialZh = "黄花梨木",
                    materialHex = 0xFFB07C4F,
                    basePosition = Vec3(0f, -0.12f, 0f),
                    explodeOffset = Vec3(-0.62f, 0.05f, 0f),
                    dimensionsMm = Vec3(620f, 150f, 130f),
                    mechanicsPrinciple = "平行于建筑正面布置，与华拱在中心半榫咬合，抵御沿檐口方向的侧向剪切与扭转。",
                    historicalNote = "因嵌于墙面泥道之上得名，是连接相邻攒斗拱、形成整体空间桁架的关键横系件。"
                ),
                ArtifactPart(
                    id = "jiaohudou",
                    nameZh = "交互斗与散斗组",
                    nameEn = "Jiaohudou & Sandou Blocks",
                    dynastyOrEra = "明代官式",
                    materialZh = "温润柏木",
                    materialHex = 0xFFC59663,
                    basePosition = Vec3(0f, 0.14f, 0f),
                    explodeOffset = Vec3(0.55f, 0.24f, 0.28f),
                    dimensionsMm = Vec3(460f, 110f, 460f),
                    mechanicsPrinciple = "分布于拱端与正交节点的小斗群，如同机械万向垫片，在多向受力下保持木纤维不发生局部劈裂。",
                    historicalNote = "每只散斗底部均留暗销槽，与下层拱背木栓咬合，拆装全程不费一钉一铆。"
                ),
                ArtifactPart(
                    id = "ang_lever",
                    nameZh = "下昂斜杠杆梁",
                    nameEn = "Inclined Ang Lever",
                    dynastyOrEra = "明代官式",
                    materialZh = "朱漆松木",
                    materialHex = 0xFFD9532F,
                    basePosition = Vec3(0f, 0.28f, 0.06f),
                    explodeOffset = Vec3(0f, 0.42f, 0.62f),
                    dimensionsMm = Vec3(135f, 165f, 760f),
                    mechanicsPrinciple = "利用真实斜置杠杆原理，以内尾承接屋顶自重配重，平衡外檐挑出重量，极大减小立柱倾覆力矩。",
                    historicalNote = "昂嘴削成锋利象鼻斜坡，既是力学翘板，也是殿宇檐下最具识别度的雕塑剪影。"
                ),
                ArtifactPart(
                    id = "shuatou",
                    nameZh = "耍头与承重枋",
                    nameEn = "Shuatou & Lintel Beam",
                    dynastyOrEra = "明代官式",
                    materialZh = "古铜鎏金饰木",
                    materialHex = 0xFFC8A055,
                    basePosition = Vec3(0f, 0.50f, 0f),
                    explodeOffset = Vec3(0f, 0.72f, 0f),
                    dimensionsMm = Vec3(740f, 150f, 380f),
                    mechanicsPrinciple = "最顶层压盖构件将椽望与檩条荷载汇集下传，锁死下方全部榫卯的竖向自由度。",
                    historicalNote = "大钟寺藏经楼檐下重拱即借此收口，使木构屋盖与下方铸铜巨钟共享稳固声学空间。"
                ),
                ArtifactPart(
                    id = "yongle_crest",
                    nameZh = "永乐大钟蒲牢钟纽",
                    nameEn = "Yongle Bell Pulao Crown",
                    dynastyOrEra = "明永乐（北京大钟寺）",
                    materialZh = "响铜合金",
                    materialHex = 0xFFB88D44,
                    basePosition = Vec3(0f, -0.82f, 0f),
                    explodeOffset = Vec3(0f, -1.05f, 0f),
                    dimensionsMm = Vec3(360f, 320f, 360f),
                    mechanicsPrinciple = "钟体呈抛物线变壁厚壳体，口部加厚形成双基频拍音振动模态，传声数十里余音绵长。",
                    historicalNote = "致敬决赛举办地北京大钟寺：全钟铸铭文 23 万余字，铜锡铅配比达声学黄金比例。"
                )
            )
        ),
        SpatialArtifact(
            id = "armillary_sphere",
            glbAssetPath = "models/ming_armillary.glb",
            titleZh = "明代浑天仪",
            titleEn = "Ming Bronze Armillary Sphere",
            subtitleZh = "中国古代球面天文坐标系 · 三辰嵌套测量仪",
            eraZh = "明正统年间（1437—1442 年）",
            summaryZh = "通过六合仪、三辰仪与四游仪三层同心青铜刻度环嵌套旋转，直接测得天体赤经、赤纬与黄道坐标。",
            totalDimensionsMm = Vec3(920f, 960f, 920f),
            parts = listOf(
                ArtifactPart(
                    id = "horizon_ring",
                    nameZh = "地平经环",
                    nameEn = "Horizon Azimuth Ring",
                    dynastyOrEra = "明正统",
                    materialZh = "铸青铜",
                    materialHex = 0xFFC8A055,
                    basePosition = Vec3(0f, 0.12f, 0f),
                    explodeOffset = Vec3(0f, -0.35f, 0f),
                    dimensionsMm = Vec3(840f, 45f, 840f),
                    mechanicsPrinciple = "环面刻二十四方位与水槽水准沟，校平后构成整个天文仪器的基准参考水平面。",
                    historicalNote = "明代紫金山天文台实存浑仪仍保留精密水槽，注水即知四方是否水平。"
                ),
                ArtifactPart(
                    id = "meridian_ring",
                    nameZh = "子午双环",
                    nameEn = "Meridian Double Ring",
                    dynastyOrEra = "明正统",
                    materialZh = "古铜",
                    materialHex = 0xFFA67C3B,
                    basePosition = Vec3(0f, 0.12f, 0f),
                    explodeOffset = Vec3(0f, 0.45f, 0f),
                    dimensionsMm = Vec3(800f, 800f, 52f),
                    mechanicsPrinciple = "正南北竖立的刚性主框架，两端精密钻孔确立天球南北极转轴，承受内部旋转环全部重量。",
                    historicalNote = "北极出地高度依观象台纬度设定（北京约 39.9°），直观呈现天穹周日视运动。"
                ),
                ArtifactPart(
                    id = "equatorial_ring",
                    nameZh = "赤道刻度环",
                    nameEn = "Equatorial Ring",
                    dynastyOrEra = "明正统",
                    materialZh = "鎏金青铜",
                    materialHex = 0xFFD6AE60,
                    basePosition = Vec3(0f, 0.12f, 0f),
                    explodeOffset = Vec3(0.55f, 0.18f, 0.35f),
                    dimensionsMm = Vec3(710f, 42f, 710f),
                    mechanicsPrinciple = "与极轴严格正交，刻三百六十五度四分度之一，用于直读二十八宿入宿度（赤经）。",
                    historicalNote = "中国古代天文学以赤道坐标体系为核心，较欧洲第谷时代早逾千年。"
                ),
                ArtifactPart(
                    id = "ecliptic_ring",
                    nameZh = "黄道斜环",
                    nameEn = "Ecliptic Inclined Ring",
                    dynastyOrEra = "明正统",
                    materialZh = "朱铜合金",
                    materialHex = 0xFFD9532F,
                    basePosition = Vec3(0f, 0.12f, 0f),
                    explodeOffset = Vec3(-0.55f, 0.28f, 0.35f),
                    dimensionsMm = Vec3(660f, 38f, 660f),
                    mechanicsPrinciple = "与赤道环呈 23.5° 黄赤交角固定铆接，模拟太阳周年视运动轨迹与二十四节气节点。",
                    historicalNote = "春秋二分点恰位于黄赤双环交接处，使历法推算具备直观空间几何映射。"
                ),
                ArtifactPart(
                    id = "sight_tube",
                    nameZh = "四游仪与窥管",
                    nameEn = "Alidade Sighting Tube",
                    dynastyOrEra = "明正统",
                    materialZh = "精磨黄铜",
                    materialHex = 0xFFE2BE73,
                    basePosition = Vec3(0f, 0.12f, 0f),
                    explodeOffset = Vec3(0f, 0.12f, 0.72f),
                    dimensionsMm = Vec3(580f, 70f, 70f),
                    mechanicsPrinciple = "可绕极轴与去极轴二自由度转动，瞄准目标星体后即可在双环游标同时读取角坐标。",
                    historicalNote = "管内加设十字丝与细缝，减少肉眼视差，角度测量精度可达数角分。"
                ),
                ArtifactPart(
                    id = "dragon_base",
                    nameZh = "云龙托架与十字底座",
                    nameEn = "Dragon Pillar Base",
                    dynastyOrEra = "明正统",
                    materialZh = "深棕铸铜",
                    materialHex = 0xFF4F3828,
                    basePosition = Vec3(0f, -0.58f, 0f),
                    explodeOffset = Vec3(0f, -0.95f, 0f),
                    dimensionsMm = Vec3(760f, 320f, 760f),
                    mechanicsPrinciple = "四柱斜撑构成立体三角形稳定结构，降低整机重心并抑制大风引起的微幅晃动。",
                    historicalNote = "底座四角雕铸行云盘龙，融汇宫廷铸造工艺与结构抗倾覆力学。"
                )
            )
        ),
        SpatialArtifact(
            id = "xuande_censer",
            glbAssetPath = "models/xuande_censer.glb",
            titleZh = "宋式宣德炉",
            titleEn = "Song-Ming Xuande Censer",
            subtitleZh = "文人书房铜炉典范 · 热对流篆香声景容器",
            eraZh = "宋韵明制（15 世纪）",
            summaryZh = "融合失蜡法精铸厚胎铜器与自然空气热对流通道，香烟经镂空莲瓣盖缓升聚而不散，是东方书房的静心锚点。",
            totalDimensionsMm = Vec3(240f, 260f, 240f),
            parts = listOf(
                ArtifactPart(
                    id = "lotus_lid",
                    nameZh = "镂空莲瓣炉盖",
                    nameEn = "Openwork Lotus Lid",
                    dynastyOrEra = "明宣德制式",
                    materialZh = "鎏金古铜",
                    materialHex = 0xFFC8A055,
                    basePosition = Vec3(0f, 0.42f, 0f),
                    explodeOffset = Vec3(0f, 0.88f, 0f),
                    dimensionsMm = Vec3(176f, 78f, 176f),
                    mechanicsPrinciple = "顶孔与周缘莲瓣孔形成伯努利微压差，引导香烟形成缓慢层流烟柱而不受侧风吹散。",
                    historicalNote = "盖纽多作瑞兽或含苞青莲，开合严丝合缝以控氧延缓香篆燃速。"
                ),
                ArtifactPart(
                    id = "incense_tray",
                    nameZh = "沉香篆印灰盘",
                    nameEn = "Ash & Incense Seal Tray",
                    dynastyOrEra = "宋式书房",
                    materialZh = "香灰细砂",
                    materialHex = 0xFFD9CDB8,
                    basePosition = Vec3(0f, 0.18f, 0f),
                    explodeOffset = Vec3(0f, 0.46f, 0.32f),
                    dimensionsMm = Vec3(154f, 32f, 154f),
                    mechanicsPrinciple = "多孔轻质草木灰层具有极低导热系数，既向香粉底部缓供微氧，又隔绝高温传至炉底。",
                    historicalNote = "宋人打香篆常以一篆燃尽计时约两刻半（近 25 分钟），与现代番茄专注周期异曲同工。"
                ),
                ArtifactPart(
                    id = "censer_body",
                    nameZh = "蚰龙厚胎铜炉腹",
                    nameEn = "Thick-Walled Bronze Body",
                    dynastyOrEra = "明宣德",
                    materialZh = "栗壳色精铜",
                    materialHex = 0xFF7D4B2A,
                    basePosition = Vec3(0f, -0.02f, 0f),
                    explodeOffset = Vec3(0f, 0f, 0f),
                    dimensionsMm = Vec3(210f, 118f, 210f),
                    mechanicsPrinciple = "高热容厚铜壁在燃香阶段均匀蓄热，避免局部过热产生焦糊味，令沉香油脂温和挥发。",
                    historicalNote = "传说风磨铜经十二炼铸成，经手盘养后呈现出温润如秋栗般的包浆光泽。"
                ),
                ArtifactPart(
                    id = "bridge_ears",
                    nameZh = "双桥耳（冲天/蚰耳）",
                    nameEn = "Twin Arched Handles",
                    dynastyOrEra = "宋式明炉",
                    materialZh = "古铜包浆",
                    materialHex = 0xFFB07E43,
                    basePosition = Vec3(0f, 0.08f, 0f),
                    explodeOffset = Vec3(0.68f, 0.14f, 0f),
                    dimensionsMm = Vec3(240f, 65f, 38f),
                    mechanicsPrinciple = "对称弧耳截面积收窄形成热阻桥，减少炉腹热量传导至持握部位，兼顾移炉手感与轮廓张力。",
                    historicalNote = "宣德炉谱以耳式定品，桥耳、蚰耳最受明代江南文人案头推崇。"
                ),
                ArtifactPart(
                    id = "tripod_feet",
                    nameZh = "三乳鼎足",
                    nameEn = "Three Tripod Feet",
                    dynastyOrEra = "明宣德",
                    materialZh = "精铸铜足",
                    materialHex = 0xFF8C5835,
                    basePosition = Vec3(0f, -0.30f, 0f),
                    explodeOffset = Vec3(0f, -0.45f, 0.24f),
                    dimensionsMm = Vec3(170f, 52f, 170f),
                    mechanicsPrinciple = "三点定面几何原则保证在不平整木案上绝不晃动，同时在炉底形成空气对流散热间隙。",
                    historicalNote = "三足呈等边三角分布，承袭商周礼器鼎足的庄重气度而化繁为简。"
                ),
                ArtifactPart(
                    id = "cedar_stand",
                    nameZh = "紫檀海棠雕花座",
                    nameEn = "Carved Cedar Pedestal",
                    dynastyOrEra = "明清案头",
                    materialZh = "深雪松/紫檀",
                    materialHex = 0xFF3D2B1F,
                    basePosition = Vec3(0f, -0.52f, 0f),
                    explodeOffset = Vec3(0f, -0.88f, 0f),
                    dimensionsMm = Vec3(236f, 44f, 236f),
                    mechanicsPrinciple = "宽底矮座扩大支撑多边形并隔离铜炉余温，保护书案漆面不受烫伤。",
                    historicalNote = "器座相配为明式书房清供规矩，深木色反衬栗壳铜炉的暖金光晕。"
                )
            )
        ),
        SpatialArtifact(
            id = "origami_pagoda",
            glbAssetPath = "models/origami_pagoda.glb",
            titleZh = "空间折纸几何塔",
            titleEn = "Spatial Origami Pagoda",
            subtitleZh = "参数化折面薄壳塔 · 东方古塔与现代折纸力学融合",
            eraZh = "当代空间计算构型（2026）",
            summaryZh = "受应县木塔八角收分与三浦折纸（Miura-ori）薄壳启发，以零胶水可展折面层叠出高刚度轻量化空间结构。",
            totalDimensionsMm = Vec3(420f, 880f, 420f),
            parts = listOf(
                ArtifactPart(
                    id = "octa_plinth",
                    nameZh = "八角须弥折面基座",
                    nameEn = "Octagonal Folded Plinth",
                    dynastyOrEra = "参数化构型",
                    materialZh = "深雪松木板",
                    materialHex = 0xFF4B3425,
                    basePosition = Vec3(0f, -0.66f, 0f),
                    explodeOffset = Vec3(0f, -0.96f, 0f),
                    dimensionsMm = Vec3(420f, 120f, 420f),
                    mechanicsPrinciple = "八边形折边将塔身集中荷载均匀扩散至地基，抗风压各向同性远优于方形截面。",
                    historicalNote = "呼应辽代应县木塔八角平面，现代数字制造以单片激光切折成形。"
                ),
                ArtifactPart(
                    id = "tier1_shell",
                    nameZh = "初层折面筒壳",
                    nameEn = "Tier-1 Pleated Shell",
                    dynastyOrEra = "折纸薄壳",
                    materialZh = "雪花石膏宣纸板",
                    materialHex = 0xFFEFE6D6,
                    basePosition = Vec3(0f, -0.34f, 0f),
                    explodeOffset = Vec3(0f, -0.44f, 0.36f),
                    dimensionsMm = Vec3(350f, 200f, 350f),
                    mechanicsPrinciple = "交替山谷折痕大幅提高薄壁截面惯性矩，使 2mm 纸板承受自身百倍以上的竖向轴压。",
                    historicalNote = "展开后为单一连续平面网图，便于在空间计算界面中演示二维到三维的拓扑折叠。"
                ),
                ArtifactPart(
                    id = "tier2_wings",
                    nameZh = "中层菱形飞檐翼",
                    nameEn = "Tier-2 Diamond Eave Wings",
                    dynastyOrEra = "折纸薄壳",
                    materialZh = "竹叶墨绿染纸",
                    materialHex = 0xFF3F5E46,
                    basePosition = Vec3(0f, -0.02f, 0f),
                    explodeOffset = Vec3(0.56f, 0.02f, 0f),
                    dimensionsMm = Vec3(330f, 175f, 330f),
                    mechanicsPrinciple = "外挑菱形折翼充当环向加劲肋，有效抑制薄壳受压屈曲，并形成柔光漫反射遮阳檐。",
                    historicalNote = "以竹叶墨绿点缀暖白宣纸折面，降低 XR 头显长时间注视下的高光视觉疲劳。"
                ),
                ArtifactPart(
                    id = "waist_rib",
                    nameZh = "腰檐力学锁环",
                    nameEn = "Waist Compression Ring",
                    dynastyOrEra = "参数化构型",
                    materialZh = "朱柿漆木",
                    materialHex = 0xFFD9532F,
                    basePosition = Vec3(0f, 0.24f, 0f),
                    explodeOffset = Vec3(-0.54f, 0.28f, 0f),
                    dimensionsMm = Vec3(260f, 68f, 260f),
                    mechanicsPrinciple = "作为层间刚性箍环约束折纸壳体径向扩张，实现上下两层折面的快速卡扣装配。",
                    historicalNote = "借用古建平坐层构造思想，以一抹朱柿色标定整塔黄金分割腰线。"
                ),
                ArtifactPart(
                    id = "upper_dome",
                    nameZh = "顶层采光折穹",
                    nameEn = "Upper Origami Lantern Dome",
                    dynastyOrEra = "折纸薄壳",
                    materialZh = "暖象牙柔光纸",
                    materialHex = 0xFFF5EFE6,
                    basePosition = Vec3(0f, 0.48f, 0f),
                    explodeOffset = Vec3(0f, 0.64f, -0.34f),
                    dimensionsMm = Vec3(210f, 165f, 210f),
                    mechanicsPrinciple = "八瓣折面向上收拢成自承重穹顶壳，内部空腔可嵌入漫射柔光体作为空间灯笼。",
                    historicalNote = "在烛夜模式下成为室内柔和主光源，映射出温暖的几何折影。"
                ),
                ArtifactPart(
                    id = "bronze_spire",
                    nameZh = "鎏金相轮塔刹",
                    nameEn = "Gilt Bronze Finial Spire",
                    dynastyOrEra = "东方建筑意象",
                    materialZh = "香槟古铜",
                    materialHex = 0xFFC8A055,
                    basePosition = Vec3(0f, 0.78f, 0f),
                    explodeOffset = Vec3(0f, 1.08f, 0f),
                    dimensionsMm = Vec3(92f, 210f, 92f),
                    mechanicsPrinciple = "中心穿心轴杆拉结上层穹顶与塔身，兼具配重调谐阻尼与空间视觉垂直标尺作用。",
                    historicalNote = "由覆钵、相轮与宝珠抽象化演进而成，为整座参数化折纸塔赋予东方神韵。"
                )
            )
        )
    )

    fun findArtifact(id: String): SpatialArtifact =
        exhibits.firstOrNull { it.id == id } ?: exhibits.first()

    fun findPart(artifactId: String, partId: String): ArtifactPart {
        val artifact = findArtifact(artifactId)
        return artifact.parts.firstOrNull { it.id == partId } ?: artifact.parts.first()
    }
}
