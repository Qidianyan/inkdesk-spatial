package com.elidot.inkdesk.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elidot.inkdesk.model.PanelId
import com.elidot.inkdesk.ui.AudioFocusDockPanel
import com.elidot.inkdesk.ui.InkDeskViewModel
import com.elidot.inkdesk.ui.NotesInspectorPanel
import com.elidot.inkdesk.ui.StagePanel
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.platform.resize.ContainerResizeType

/**
 * Registers the three synchronized spatial WindowContainers for InkDesk Spatial:
 *  - Window A (Default): 3D Spatial Artifact & Dougong Exploded Stage
 *  - Window B (Left Wing): Spatial Audio Anchors & Pomodoro Focus Dock
 *  - Window C (Right Wing): Bidirectional Structural Inspector & Spatial Notes
 */
fun inkDeskMainApp(scope: SpatialAppScope) = with(scope) {
    val sharedViewModel = InkDeskViewModel.shared

    DefaultWindowContainer {
        PicoTheme {
            StagePanel(
                viewModel = sharedViewModel,
                modifier = Modifier.windowConstraints(minWidth = 820.dp, minHeight = 640.dp)
            )
        }
    }

    WindowContainer(
        id = PanelId.AUDIO_FOCUS_LEFT.windowId,
        form = Form.Planar,
        defaultSize = WindowContainerSize(420.dp, 600.dp),
        resizeType = ContainerResizeType.ContentSize,
        enableMaterialBackground = true
    ) {
        PicoTheme {
            AudioFocusDockPanel(
                viewModel = sharedViewModel,
                modifier = Modifier.windowConstraints(width = 420.dp, height = 600.dp)
            )
        }
    }

    WindowContainer(
        id = PanelId.NOTES_INSPECTOR_RIGHT.windowId,
        form = Form.Planar,
        defaultSize = WindowContainerSize(440.dp, 600.dp),
        resizeType = ContainerResizeType.ContentSize,
        enableMaterialBackground = true
    ) {
        PicoTheme {
            NotesInspectorPanel(
                viewModel = sharedViewModel,
                modifier = Modifier.windowConstraints(width = 440.dp, height = 600.dp)
            )
        }
    }
}
