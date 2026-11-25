package com.elidot.inkdesk.ui

import com.elidot.inkdesk.model.AmbientLightingTheme
import com.elidot.inkdesk.model.ArtifactCatalog
import com.elidot.inkdesk.model.ArtifactPart
import com.elidot.inkdesk.model.InkDeskSessionState
import com.elidot.inkdesk.model.LayoutPreset
import com.elidot.inkdesk.model.PanelId
import com.elidot.inkdesk.model.PanelPose
import com.elidot.inkdesk.model.SoundAnchorId
import com.elidot.inkdesk.model.SpatialArtifact
import com.elidot.inkdesk.model.SpatialAudioRenderState
import com.elidot.inkdesk.model.Vec3
import com.elidot.inkdesk.spatial.ExplodedModelController
import com.elidot.inkdesk.spatial.GltfSceneBinding
import com.elidot.inkdesk.spatial.PartRenderTransform
import com.elidot.inkdesk.spatial.SpatialAudioEngine
import com.elidot.inkdesk.spatial.SpatialPanelLayoutEngine
import com.elidot.inkdesk.spatial.SpatialSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shared cross-window ViewModel synchronizing Window A (Stage), Window B (Audio & Focus),
 * and Window C (Inspector & Notes) via unidirectional Kotlin StateFlow.
 */
class InkDeskViewModel(
    val layoutEngine: SpatialPanelLayoutEngine = SpatialPanelLayoutEngine(),
    val explodedController: ExplodedModelController = ExplodedModelController(),
    val audioEngine: SpatialAudioEngine = SpatialAudioEngine(),
    val audioBridge: PicoSpatialAudioBridge = PicoSpatialAudioBridge()
) {
    private val store = SpatialSessionStore(audioEngine, explodedController)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val _uiState = MutableStateFlow(store.state)
    val uiState: StateFlow<InkDeskSessionState> = _uiState.asStateFlow()

    init {
        store.subscribe { snapshot ->
            _uiState.value = snapshot
            audioBridge.applySpatialStates(
                anchors = snapshot.soundAnchors,
                renderStates = evaluateAudio(snapshot)
            )
        }
    }

    fun activeArtifact(state: InkDeskSessionState = uiState.value): SpatialArtifact =
        ArtifactCatalog.findArtifact(state.activeArtifactId)

    fun selectedPart(state: InkDeskSessionState = uiState.value): ArtifactPart =
        ArtifactCatalog.findPart(state.activeArtifactId, state.selectedPartId)

    fun panelPoses(state: InkDeskSessionState = uiState.value): Map<PanelId, PanelPose> =
        layoutEngine.computeLayout(
            preset = state.layoutPreset,
            headYawDegrees = state.headYawDegrees
        )

    fun partTransforms(state: InkDeskSessionState = uiState.value): List<PartRenderTransform> =
        explodedController.computeTransforms(
            artifact = activeArtifact(state),
            explodeProgress = state.explodeProgress,
            selectedPartId = state.selectedPartId,
            yawDegrees = state.orbitYawDegrees,
            pitchDegrees = state.orbitPitchDegrees,
            crossSectionEnabled = state.crossSectionEnabled,
            sectionCutY = state.sectionCutY
        )

    fun glbSceneBinding(state: InkDeskSessionState = uiState.value): GltfSceneBinding =
        explodedController.resolveGlbSceneBinding(
            artifact = activeArtifact(state),
            explodeProgress = state.explodeProgress,
            selectedPartId = state.selectedPartId,
            yawDegrees = state.orbitYawDegrees,
            pitchDegrees = state.orbitPitchDegrees,
            crossSectionEnabled = state.crossSectionEnabled,
            sectionCutY = state.sectionCutY
        )

    fun evaluateAudio(state: InkDeskSessionState = uiState.value): List<SpatialAudioRenderState> =
        audioEngine.evaluateAll(
            anchors = state.soundAnchors,
            listenerYawDegrees = state.headYawDegrees
        )

    fun caliperDistanceMm(state: InkDeskSessionState = uiState.value): Float {
        val compareId = state.comparePartId ?: return 0f
        return explodedController.measureDistanceMm(
            artifact = activeArtifact(state),
            partAId = state.selectedPartId,
            partBId = compareId,
            explodeProgress = state.explodeProgress
        )
    }

    fun selectArtifact(id: String) = store.selectArtifact(id)
    fun selectPart(partId: String) = store.selectPart(partId)
    fun setComparePart(partId: String?) = store.setComparePart(partId)
    fun setExplodeProgress(progress: Float) = store.setExplodeProgress(progress)
    fun setCrossSection(enabled: Boolean, cutY: Float = uiState.value.sectionCutY) =
        store.setCrossSection(enabled, cutY)
    fun orbitBy(deltaYawDeg: Float, deltaPitchDeg: Float) = store.orbitBy(deltaYawDeg, deltaPitchDeg)
    fun setLayoutPreset(preset: LayoutPreset) = store.setLayoutPreset(preset)
    fun resetWorkspaceLayout() = store.resetWorkspaceLayout()
    fun setAmbientTheme(theme: AmbientLightingTheme) = store.setAmbientTheme(theme)
    fun setHeadYaw(yawDegrees: Float) = store.setHeadYaw(yawDegrees)
    fun toggleSoundAnchor(id: SoundAnchorId) = store.toggleSoundAnchor(id)
    fun setSoundAnchorGain(id: SoundAnchorId, gain: Float) = store.setSoundAnchorGain(id, gain)
    fun moveSoundAnchor(id: SoundAnchorId, pos: Vec3) = store.moveSoundAnchor(id, pos)
    fun addNote(content: String) = store.addNote(content)
    fun togglePinNote(noteId: String) = store.togglePinNote(noteId)
    fun deleteNote(noteId: String) = store.deleteNote(noteId)
    fun exportMarkdownReport(): String = store.exportMarkdownReport()

    fun toggleFocusTimer() {
        store.toggleFocusTimer()
        if (uiState.value.focusTimer.isRunning) {
            startTicker()
        } else {
            timerJob?.cancel()
            timerJob = null
        }
    }

    fun resetFocusTimer() {
        timerJob?.cancel()
        timerJob = null
        store.resetFocusTimer()
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && uiState.value.focusTimer.isRunning) {
                delay(1000L)
                store.tickFocusTimer(1)
            }
        }
    }

    fun cleanUp() {
        timerJob?.cancel()
        timerJob = null
        audioBridge.release()
    }

    companion object {
        val shared: InkDeskViewModel by lazy { InkDeskViewModel() }
    }
}
