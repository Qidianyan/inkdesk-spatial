package com.elidot.inkdesk.ui

import com.elidot.inkdesk.domain.AnchorMutationResult
import com.elidot.inkdesk.domain.CalloutPinTone
import com.elidot.inkdesk.domain.CutawayAxis
import com.elidot.inkdesk.domain.CutawayPlaneState
import com.elidot.inkdesk.domain.MortiseAssemblyEngine
import com.elidot.inkdesk.domain.MortiseAssemblyProgress
import com.elidot.inkdesk.domain.MortiseAssemblyState
import com.elidot.inkdesk.domain.SelectAnchorResult
import com.elidot.inkdesk.domain.SnapAttemptResult
import com.elidot.inkdesk.domain.SpatialAnchorEngine
import com.elidot.inkdesk.domain.SpatialAnchorState
import com.elidot.inkdesk.domain.SpatialCalloutAnchor
import com.elidot.inkdesk.domain.StudioLightingPreset
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
    val assemblyEngine: MortiseAssemblyEngine = MortiseAssemblyEngine(),
    val anchorEngine: SpatialAnchorEngine = SpatialAnchorEngine(),
    val audioBridge: PicoSpatialAudioBridge = PicoSpatialAudioBridge()
) {
    private val store = SpatialSessionStore(audioEngine, explodedController)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val _uiState = MutableStateFlow(store.state)
    val uiState: StateFlow<InkDeskSessionState> = _uiState.asStateFlow()

    private val _assemblyMode = MutableStateFlow(false)
    val assemblyMode: StateFlow<Boolean> = _assemblyMode.asStateFlow()

    private val _assemblyState = MutableStateFlow(
        assemblyEngine.createSession(ArtifactCatalog.findArtifact(store.state.activeArtifactId))
    )
    val assemblyState: StateFlow<MortiseAssemblyState> = _assemblyState.asStateFlow()

    private val _anchorState = MutableStateFlow(
        anchorEngine.syncSelectedAnchorToPart(
            anchorEngine.createState(store.state.activeArtifactId),
            store.state.activeArtifactId,
            store.state.selectedPartId
        )
    )
    val anchorState: StateFlow<SpatialAnchorState> = _anchorState.asStateFlow()

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

    fun assemblyProgress(state: MortiseAssemblyState = assemblyState.value): MortiseAssemblyProgress =
        assemblyEngine.progressOf(state)

    fun setAssemblyMode(enabled: Boolean) {
        _assemblyMode.value = enabled
        if (enabled) {
            assemblyProgress().activePartId?.let { selectPart(it) }
        }
    }

    fun moveAssemblyPart(partId: String, nextPos: Vec3, nextYawDeg: Float? = null) {
        _assemblyState.value = assemblyEngine.movePart(_assemblyState.value, partId, nextPos, nextYawDeg)
    }

    fun nudgeActiveAssemblyPart(deltaPos: Vec3, deltaYawDeg: Float = 0f) {
        val activeId = assemblyProgress().activePartId ?: return
        _assemblyState.value = assemblyEngine.nudgePart(_assemblyState.value, activeId, deltaPos, deltaYawDeg)
    }

    fun attemptSnapAssemblyPart(partId: String, candidatePos: Vec3? = null, candidateYawDeg: Float? = null): SnapAttemptResult {
        val result = assemblyEngine.attemptSnap(_assemblyState.value, partId, candidatePos, candidateYawDeg)
        _assemblyState.value = result.state
        if (result.snapped) {
            assemblyProgress(result.state).activePartId?.let { selectPart(it) }
        }
        return result
    }

    fun assistSnapActivePart(): SnapAttemptResult {
        val result = assemblyEngine.assistSnapActiveStep(_assemblyState.value)
        _assemblyState.value = result.state
        if (result.snapped) {
            assemblyProgress(result.state).activePartId?.let { selectPart(it) }
        }
        return result
    }

    fun resetAssembly() {
        val next = assemblyEngine.reset(_assemblyState.value)
        _assemblyState.value = next
        assemblyProgress(next).activePartId?.let { selectPart(it) }
    }

    fun updateCutawayPlane(
        enabled: Boolean = _assemblyState.value.cutaway.enabled,
        axis: CutawayAxis = _assemblyState.value.cutaway.axis,
        offsetMeters: Float = _assemblyState.value.cutaway.offsetMeters
    ) {
        val updated = assemblyEngine.updateCutaway(_assemblyState.value, enabled, axis, offsetMeters)
        _assemblyState.value = updated
        store.setCrossSection(updated.cutaway.enabled, updated.cutaway.offsetMeters)
    }

    fun calloutAnchors(state: SpatialAnchorState = anchorState.value): List<SpatialCalloutAnchor> =
        anchorEngine.anchorsFor(state, uiState.value.activeArtifactId)

    fun selectCalloutAnchor(anchorId: String): SelectAnchorResult {
        val res = anchorEngine.selectAnchor(_anchorState.value, anchorId)
        if (res.found && res.anchor != null) {
            if (res.anchor.artifactId != uiState.value.activeArtifactId) {
                store.selectArtifact(res.anchor.artifactId)
                val exhibit = ArtifactCatalog.findArtifact(res.anchor.artifactId)
                _assemblyState.value = assemblyEngine.createSession(
                    artifact = exhibit,
                    cutaway = CutawayPlaneState(enabled = false, axis = _assemblyState.value.cutaway.axis)
                )
            }
            _anchorState.value = res.state
            res.partId?.let { store.selectPart(it) }
        }
        return res
    }

    fun addCustomCalloutAnchor(
        titleZh: String,
        craftDetailZh: String,
        tone: CalloutPinTone = CalloutPinTone.CINNABAR
    ): AnchorMutationResult {
        val current = uiState.value
        val res = anchorEngine.addCustomAnchor(
            state = _anchorState.value,
            artifactId = current.activeArtifactId,
            partId = current.selectedPartId,
            titleZh = titleZh,
            craftDetailZh = craftDetailZh,
            tone = tone
        )
        if (res.ok) {
            _anchorState.value = res.state
        }
        return res
    }

    fun deleteCustomCalloutAnchor(anchorId: String): AnchorMutationResult {
        val res = anchorEngine.deleteCustomAnchor(_anchorState.value, anchorId)
        if (res.ok) {
            _anchorState.value = res.state
            anchorEngine.selectedAnchor(res.state)?.partId?.let { store.selectPart(it) }
        }
        return res
    }

    fun setStudioLightingPreset(preset: StudioLightingPreset) {
        _anchorState.value = anchorEngine.setLightingPreset(_anchorState.value, preset)
    }

    fun toggleShowPinsIn3D() {
        _anchorState.value = anchorEngine.setShowPinsIn3D(_anchorState.value, !_anchorState.value.showPinsIn3D)
    }

    fun selectArtifact(id: String) {
        store.selectArtifact(id)
        val exhibit = ArtifactCatalog.findArtifact(id)
        val nextAssembly = assemblyEngine.createSession(
            artifact = exhibit,
            cutaway = CutawayPlaneState(enabled = false, axis = _assemblyState.value.cutaway.axis)
        )
        _assemblyState.value = nextAssembly
        val initialPartId = if (_assemblyMode.value) {
            assemblyProgress(nextAssembly).activePartId ?: exhibit.parts.first().id
        } else {
            exhibit.parts.first().id
        }
        store.selectPart(initialPartId)
        _anchorState.value = anchorEngine.syncSelectedAnchorToPart(
            anchorEngine.switchArtifact(_anchorState.value, exhibit.id),
            exhibit.id,
            initialPartId
        )
    }

    fun selectPart(partId: String) {
        store.selectPart(partId)
        _anchorState.value = anchorEngine.syncSelectedAnchorToPart(
            _anchorState.value,
            uiState.value.activeArtifactId,
            partId
        )
    }
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
    fun exportMarkdownReport(): String = store.exportMarkdownReport(
        lightingPreset = _anchorState.value.lightingPreset,
        calloutAnchors = calloutAnchors(_anchorState.value)
    )

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
