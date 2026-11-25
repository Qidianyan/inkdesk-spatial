package com.elidot.inkdesk.spatial

import com.elidot.inkdesk.model.AmbientLightingTheme
import com.elidot.inkdesk.model.ArtifactCatalog
import com.elidot.inkdesk.model.FocusTimerState
import com.elidot.inkdesk.model.InkDeskSessionState
import com.elidot.inkdesk.model.LayoutPreset
import com.elidot.inkdesk.model.SoundAnchorId
import com.elidot.inkdesk.model.SpatialAudioAnchor
import com.elidot.inkdesk.model.SpatialNote
import com.elidot.inkdesk.model.Vec3

/**
 * Pure Kotlin single-source-of-truth session state store synchronizing all three spatial windows:
 * Stage A (3D artifact), Left Wing B (Spatial audio & timer), Right Wing C (Notes & Inspector).
 */
class SpatialSessionStore(
    private val audioEngine: SpatialAudioEngine = SpatialAudioEngine(),
    private val explodedController: ExplodedModelController = ExplodedModelController()
) {
    private val listeners = mutableListOf<(InkDeskSessionState) -> Unit>()
    private var noteCounter = 3

    var state: InkDeskSessionState = createInitialState()
        private set

    fun subscribe(listener: (InkDeskSessionState) -> Unit): () -> Unit {
        listeners.add(listener)
        listener(state)
        return { listeners.remove(listener) }
    }

    private fun update(transform: (InkDeskSessionState) -> InkDeskSessionState) {
        state = transform(state)
        listeners.forEach { it(state) }
    }

    fun selectArtifact(artifactId: String) {
        val exhibit = ArtifactCatalog.findArtifact(artifactId)
        update {
            it.copy(
                activeArtifactId = exhibit.id,
                selectedPartId = exhibit.parts.first().id,
                comparePartId = exhibit.parts.getOrNull(1)?.id,
                explodeProgress = 0f,
                crossSectionEnabled = false,
                orbitYawDegrees = 24f,
                orbitPitchDegrees = 14f
            )
        }
    }

    fun selectPart(partId: String) {
        val exhibit = ArtifactCatalog.findArtifact(state.activeArtifactId)
        val matched = exhibit.parts.firstOrNull { it.id == partId } ?: return
        update {
            val nextCompare = if (it.comparePartId == matched.id) {
                exhibit.parts.firstOrNull { p -> p.id != matched.id }?.id
            } else {
                it.comparePartId
            }
            it.copy(selectedPartId = matched.id, comparePartId = nextCompare)
        }
    }

    fun setComparePart(partId: String?) {
        update { it.copy(comparePartId = partId) }
    }

    fun setExplodeProgress(progress: Float) {
        update { it.copy(explodeProgress = progress.coerceIn(0f, 1f)) }
    }

    fun setCrossSection(enabled: Boolean, cutY: Float = state.sectionCutY) {
        update {
            it.copy(
                crossSectionEnabled = enabled,
                sectionCutY = cutY.coerceIn(-0.8f, 0.8f)
            )
        }
    }

    fun orbitBy(deltaYawDeg: Float, deltaPitchDeg: Float) {
        val (yaw, pitch) = explodedController.clampOrbit(
            state.orbitYawDegrees + deltaYawDeg,
            state.orbitPitchDegrees + deltaPitchDeg
        )
        update { it.copy(orbitYawDegrees = yaw, orbitPitchDegrees = pitch) }
    }

    fun setLayoutPreset(preset: LayoutPreset) {
        update { it.copy(layoutPreset = preset) }
    }

    fun resetWorkspaceLayout() {
        update {
            it.copy(
                layoutPreset = LayoutPreset.SURROUND_ARC,
                headYawDegrees = 0f,
                orbitYawDegrees = 24f,
                orbitPitchDegrees = 14f
            )
        }
    }

    fun setAmbientTheme(theme: AmbientLightingTheme) {
        update { it.copy(ambientTheme = theme) }
    }

    fun setHeadYaw(yawDegrees: Float) {
        update { it.copy(headYawDegrees = yawDegrees.coerceIn(-180f, 180f)) }
    }

    fun toggleSoundAnchor(id: SoundAnchorId) {
        update { current ->
            current.copy(
                soundAnchors = current.soundAnchors.map {
                    if (it.id == id) it.copy(enabled = !it.enabled) else it
                }
            )
        }
    }

    fun setSoundAnchorGain(id: SoundAnchorId, gain: Float) {
        val clamped = gain.coerceIn(0f, 1f)
        update { current ->
            current.copy(
                soundAnchors = current.soundAnchors.map {
                    if (it.id == id) it.copy(gain = clamped) else it
                }
            )
        }
    }

    fun moveSoundAnchor(id: SoundAnchorId, newPosition: Vec3) {
        val clampedPos = audioEngine.clampAnchorPosition(newPosition)
        update { current ->
            current.copy(
                soundAnchors = current.soundAnchors.map {
                    if (it.id == id) it.copy(positionMeters = clampedPos) else it
                }
            )
        }
    }

    fun toggleFocusTimer() {
        update { current ->
            val timer = current.focusTimer
            val nextRemaining = if (timer.remainingSeconds <= 0) timer.totalSeconds else timer.remainingSeconds
            current.copy(
                focusTimer = timer.copy(
                    remainingSeconds = nextRemaining,
                    isRunning = !timer.isRunning
                )
            )
        }
    }

    fun resetFocusTimer(totalSeconds: Int = 25 * 60) {
        val valid = totalSeconds.coerceIn(60, 90 * 60)
        update {
            it.copy(
                focusTimer = it.focusTimer.copy(
                    totalSeconds = valid,
                    remainingSeconds = valid,
                    isRunning = false
                )
            )
        }
    }

    fun tickFocusTimer(elapsedSeconds: Int = 1) {
        if (!state.focusTimer.isRunning || elapsedSeconds <= 0) return
        update { current ->
            val timer = current.focusTimer
            val next = (timer.remainingSeconds - elapsedSeconds).coerceAtLeast(0)
            if (next == 0) {
                current.copy(
                    focusTimer = timer.copy(
                        remainingSeconds = 0,
                        isRunning = false,
                        completedSessions = timer.completedSessions + 1
                    )
                )
            } else {
                current.copy(focusTimer = timer.copy(remainingSeconds = next))
            }
        }
    }

    fun addNote(content: String, timestampIso: String = "2026-11-23T18:00:00+08:00"): SpatialNote? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        val artifact = ArtifactCatalog.findArtifact(state.activeArtifactId)
        val part = ArtifactCatalog.findPart(artifact.id, state.selectedPartId)
        noteCounter += 1
        val note = SpatialNote(
            id = "note_$noteCounter",
            artifactId = artifact.id,
            partId = part.id,
            partNameZh = part.nameZh,
            content = trimmed,
            pinned = false,
            createdAtIso = timestampIso
        )
        update { it.copy(notes = listOf(note) + it.notes) }
        return note
    }

    fun togglePinNote(noteId: String) {
        update { current ->
            current.copy(
                notes = current.notes.map {
                    if (it.id == noteId) it.copy(pinned = !it.pinned) else it
                }
            )
        }
    }

    fun deleteNote(noteId: String) {
        update { current ->
            current.copy(notes = current.notes.filterNot { it.id == noteId })
        }
    }

    fun exportMarkdownReport(): String {
        val artifact = ArtifactCatalog.findArtifact(state.activeArtifactId)
        val selectedPart = ArtifactCatalog.findPart(artifact.id, state.selectedPartId)
        val artifactNotes = state.notes.filter { it.artifactId == artifact.id }
        val activeSounds = state.soundAnchors.filter { it.enabled }
        val caliperMm = state.comparePartId?.let { cmp ->
            explodedController.measureDistanceMm(artifact, selectedPart.id, cmp, state.explodeProgress)
        } ?: 0f

        return buildString {
            appendLine("# 砚境空间 · 研学报告：${artifact.titleZh}")
            appendLine()
            appendLine("- **英文名称**：${artifact.titleEn}")
            appendLine("- **年代与制式**：${artifact.eraZh}")
            appendLine("- **当前空间布局**：${state.layoutPreset.labelZh} | 环境光：${state.ambientTheme.labelZh}（${state.ambientTheme.kelvin}K）")
            appendLine("- **拆解进度**：${(state.explodeProgress * 100).toInt()}% | 剖面模式：${if (state.crossSectionEnabled) "开启" else "关闭"}")
            appendLine("- **专注计时**：已累计完成 ${state.focusTimer.completedSessions} 个番茄钟（当前剩余 ${state.focusTimer.formattedClock()}）")
            appendLine()
            appendLine("## 器物综述")
            appendLine(artifact.summaryZh)
            appendLine()
            appendLine("## 当前检视构件：${selectedPart.nameZh}（${selectedPart.nameEn}）")
            appendLine("- **材质**：${selectedPart.materialZh}")
            appendLine("- **尺寸（mm）**：${selectedPart.dimensionsMm.x.toInt()} × ${selectedPart.dimensionsMm.y.toInt()} × ${selectedPart.dimensionsMm.z.toInt()}")
            if (caliperMm > 0f && state.comparePartId != null) {
                val cmpPart = ArtifactCatalog.findPart(artifact.id, state.comparePartId!!)
                appendLine("- **空间卡尺测距（至 ${cmpPart.nameZh}）**：${"%.1f".format(caliperMm)} mm")
            }
            appendLine("- **力学原理**：${selectedPart.mechanicsPrinciple}")
            appendLine("- **历史注记**：${selectedPart.historicalNote}")
            appendLine()
            appendLine("## 空间声景锚点配置")
            if (activeSounds.isEmpty()) {
                appendLine("- 当前所有空间声源处于静音状态")
            } else {
                activeSounds.forEach { anchor ->
                    val pos = anchor.positionMeters
                    appendLine(
                        "- **${anchor.id.titleZh}**：音量 ${(anchor.gain * 100).toInt()}% | 空间坐标 (${"%.2f".format(pos.x)}m, ${"%.2f".format(pos.y)}m, ${"%.2f".format(pos.z)}m)"
                    )
                }
            }
            appendLine()
            appendLine("## 空间手记（共 ${artifactNotes.size} 条）")
            if (artifactNotes.isEmpty()) {
                appendLine("- 暂无手记记录")
            } else {
                artifactNotes.forEach { note ->
                    val pinTag = if (note.pinned) "[置顶] " else ""
                    appendLine("- ${pinTag}**[${note.partNameZh}]** ${note.content} _(${note.createdAtIso})_")
                }
            }
        }
    }

    companion object {
        fun createInitialState(): InkDeskSessionState {
            val firstExhibit = ArtifactCatalog.exhibits.first()
            return InkDeskSessionState(
                activeArtifactId = firstExhibit.id,
                selectedPartId = "ang_lever",
                comparePartId = "ludou",
                explodeProgress = 0.28f,
                crossSectionEnabled = false,
                sectionCutY = 0.25f,
                orbitYawDegrees = 24f,
                orbitPitchDegrees = 14f,
                layoutPreset = LayoutPreset.SURROUND_ARC,
                ambientTheme = AmbientLightingTheme.DAWN,
                headYawDegrees = 0f,
                soundAnchors = listOf(
                    SpatialAudioAnchor(SoundAnchorId.PINE_WIND, true, 0.72f, SoundAnchorId.PINE_WIND.defaultPosition),
                    SpatialAudioAnchor(SoundAnchorId.EAVES_RAIN, true, 0.56f, SoundAnchorId.EAVES_RAIN.defaultPosition),
                    SpatialAudioAnchor(SoundAnchorId.CHARCOAL_TEA, true, 0.64f, SoundAnchorId.CHARCOAL_TEA.defaultPosition),
                    SpatialAudioAnchor(SoundAnchorId.CHIME_BELLS, false, 0.45f, SoundAnchorId.CHIME_BELLS.defaultPosition)
                ),
                focusTimer = FocusTimerState(),
                notes = listOf(
                    SpatialNote(
                        id = "note_1",
                        artifactId = "dougong_bell",
                        partId = "ang_lever",
                        partNameZh = "下昂斜杠杆梁",
                        content = "将拆解滑块拉至 65% 可清晰观测下昂斜尾与坐斗的杠杆支点关系，无需铁钉即可平衡挑檐弯矩。",
                        pinned = true,
                        createdAtIso = "2026-11-23T14:20:00+08:00"
                    ),
                    SpatialNote(
                        id = "note_2",
                        artifactId = "dougong_bell",
                        partId = "ludou",
                        partNameZh = "栌斗（坐斗）",
                        content = "坐斗十字开口构成模数基准，华拱与泥道拱在此正交锁定，形成抗震阻尼核心。",
                        pinned = false,
                        createdAtIso = "2026-11-23T14:28:00+08:00"
                    ),
                    SpatialNote(
                        id = "note_3",
                        artifactId = "armillary_sphere",
                        partId = "ecliptic_ring",
                        partNameZh = "黄道斜环",
                        content = "黄赤交角 23.5° 在空间环绕视野中一目了然，二分点正好与赤道环相交。",
                        pinned = true,
                        createdAtIso = "2026-11-23T15:05:00+08:00"
                    )
                )
            )
        }
    }
}
