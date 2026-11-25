package com.elidot.inkdesk.ui

import com.elidot.inkdesk.model.SoundAnchorId
import com.elidot.inkdesk.model.SpatialAudioAnchor
import com.elidot.inkdesk.model.SpatialAudioRenderState
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.audio.AudioPlayerController

/**
 * Bridges InkDesk's pure Kotlin SpatialAudioEngine outputs to PICO Spatial SDK ECS
 * audio entities so each soundscape anchor emits positioned 3D audio in PICO OS 6.
 */
class PicoSpatialAudioBridge {
    private val anchorEntities = mutableMapOf<SoundAnchorId, Entity>()
    private val controllers = mutableMapOf<SoundAnchorId, AudioPlayerController>()

    fun registerAnchorEntity(
        id: SoundAnchorId,
        entity: Entity,
        controller: AudioPlayerController? = null
    ) {
        anchorEntities[id] = entity
        if (controller != null) {
            controllers[id] = controller
        }
    }

    fun applySpatialStates(
        anchors: List<SpatialAudioAnchor>,
        renderStates: List<SpatialAudioRenderState>
    ) {
        val stateMap = renderStates.associateBy { it.anchorId }
        anchors.forEach { anchor ->
            val render = stateMap[anchor.id] ?: return@forEach
            val controller = controllers[anchor.id] ?: return@forEach
            val combinedVolume = ((render.leftGain + render.rightGain) * 0.7071f).coerceIn(0f, 1f)
            if (render.active) {
                controller.setVolume(combinedVolume)
                controller.setLoop(true)
            } else {
                controller.stop()
            }
        }
    }

    fun release() {
        controllers.values.forEach { controller ->
            controller.stop()
            controller.close()
        }
        controllers.clear()
        anchorEntities.clear()
    }
}
