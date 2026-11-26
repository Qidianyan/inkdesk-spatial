package com.elidot.inkdesk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elidot.inkdesk.domain.CutawayAxis
import com.elidot.inkdesk.domain.StudioLightingPreset
import com.elidot.inkdesk.model.ArtifactCatalog
import com.elidot.inkdesk.model.LayoutPreset
import com.elidot.inkdesk.model.PanelId
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator

/**
 * Window A (Primary Center Stage):
 * Hosts the interactive 3D SpatialView stage for the 4 procedural Eastern artifacts,
 * exploded view disassembly slider, cross-section cut, caliper telemetry, and companion
 * WindowContainer lifecycle synchronization via LocalSpatialNavigator.
 */
@Composable
fun StagePanel(
    viewModel: InkDeskViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val assemblyMode by viewModel.assemblyMode.collectAsState()
    val assemblyState by viewModel.assemblyState.collectAsState()
    val anchorState by viewModel.anchorState.collectAsState()
    val assemblyProgress = viewModel.assemblyProgress(assemblyState)
    val palette = InkDeskThemeTokens.paletteFor(state.ambientTheme)
    val artifact = viewModel.activeArtifact(state)
    val selectedPart = viewModel.selectedPart(state)
    val glbBinding = viewModel.glbSceneBinding(state)
    val transforms = glbBinding.nodeTransforms.values.toList()
    val caliperMm = viewModel.caliperDistanceMm(state)
    val spatialNavigator = LocalSpatialNavigator.current
    val stageRootEntity = remember { Entity() }

    LaunchedEffect(state.layoutPreset) {
        when (state.layoutPreset) {
            LayoutPreset.SURROUND_ARC, LayoutPreset.SIDE_BY_SIDE_COMPARE -> {
                spatialNavigator.openWindowContainer(
                    id = PanelId.AUDIO_FOCUS_LEFT.windowId,
                    tag = PanelId.AUDIO_FOCUS_LEFT.tag
                )
                spatialNavigator.openWindowContainer(
                    id = PanelId.NOTES_INSPECTOR_RIGHT.windowId,
                    tag = PanelId.NOTES_INSPECTOR_RIGHT.tag
                )
            }
            LayoutPreset.FOCUS_SINGLE -> {
                spatialNavigator.closeWindowContainer(
                    id = PanelId.AUDIO_FOCUS_LEFT.windowId,
                    tag = PanelId.AUDIO_FOCUS_LEFT.tag
                )
                spatialNavigator.closeWindowContainer(
                    id = PanelId.NOTES_INSPECTOR_RIGHT.windowId,
                    tag = PanelId.NOTES_INSPECTOR_RIGHT.tag
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanUp()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .border(1.5.dp, palette.borderStroke, RoundedCornerShape(28.dp)),
        color = palette.panelSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Artifact Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "砚境空间 · 3D 器物拆解主台",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryText
                    )
                    Text(
                        text = "${artifact.titleZh} · ${artifact.eraZh} · ${glbBinding.glbAssetPath}",
                        fontSize = 13.sp,
                        color = palette.secondaryText
                    )
                }
                Text(
                    text = "视点 ${state.orbitYawDegrees.toInt()}° | 间距 ${"%.0f".format(caliperMm)} mm",
                    fontSize = 12.sp,
                    color = palette.bambooGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArtifactCatalog.exhibits.forEach { exhibit ->
                    val active = exhibit.id == artifact.id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) palette.persimmonAccent else palette.cardSurface)
                            .clickable { viewModel.selectArtifact(exhibit.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = exhibit.titleZh,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (active) InkDeskThemeTokens.Alabaster else palette.primaryText
                        )
                    }
                }
            }

            // Architectural Museum Studio Lighting 3-Preset Bar + 3D Callout Pin Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioLightingPreset.entries.forEach { preset ->
                    val active = anchorState.lightingPreset == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) palette.champagneBronze else palette.cardSurface)
                            .clickable { viewModel.setStudioLightingPreset(preset) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${preset.labelZh} ${preset.kelvin}K",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) InkDeskThemeTokens.DeepCedar else palette.primaryText
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (anchorState.showPinsIn3D) palette.bambooGreen else palette.cardSurface)
                        .clickable { viewModel.toggleShowPinsIn3D() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (anchorState.showPinsIn3D) "锚点开" else "锚点关",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (anchorState.showPinsIn3D) InkDeskThemeTokens.Alabaster else palette.primaryText
                    )
                }
            }

            // 3D SpatialView Volumetric Stage + Direct Pinch/Ray Orbit Drag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.cardSurface)
                    .border(1.dp, palette.borderStroke, RoundedCornerShape(22.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            viewModel.orbitBy(dragAmount.x * 0.35f, dragAmount.y * 0.25f)
                        }
                    }
            ) {
                SpatialView(
                    initial = { content, _ ->
                        content.addEntity(stageRootEntity)
                    }
                )

                // Interactive Part Pills overlaid at bottom of stage for ray/gaze quick pick
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    artifact.parts.take(5).forEach { part ->
                        val isSelected = part.id == selectedPart.id
                        val partTransform = transforms.firstOrNull { it.partId == part.id }
                        val clipped = partTransform?.clippedBySection == true
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isSelected -> palette.persimmonAccent
                                        clipped -> palette.elevatedSurface.copy(alpha = 0.55f)
                                        else -> palette.elevatedSurface
                                    }
                                )
                                .clickable { viewModel.selectPart(part.id) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = part.nameZh.substringBefore("（"),
                                fontSize = 11.sp,
                                color = if (isSelected) InkDeskThemeTokens.Alabaster else palette.primaryText
                            )
                        }
                    }
                }
            }

            // Exploded View / Interactive Mortise Snap Assembly + Multi-Axis Cutaway Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.setAssemblyMode(!assemblyMode) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (assemblyMode) palette.persimmonAccent else palette.cardSurface,
                        contentColor = if (assemblyMode) InkDeskThemeTokens.Alabaster else palette.primaryText
                    )
                ) {
                    Text(if (assemblyMode) "榫卯拼装中" else "切换榫卯拼装")
                }

                if (assemblyMode) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "榫卯磁吸进度：${assemblyProgress.lockedCount}/${assemblyProgress.totalSteps} · ${assemblyState.lastEventMessageZh}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                    }
                    Button(
                        onClick = { viewModel.assistSnapActivePart() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.champagneBronze,
                            contentColor = InkDeskThemeTokens.DeepCedar
                        )
                    ) {
                        Text(if (assemblyProgress.isComplete) "全器已合" else "磁吸落位")
                    }
                    Button(
                        onClick = { viewModel.resetAssembly() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.cardSurface,
                            contentColor = palette.primaryText
                        )
                    ) {
                        Text("复位")
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "爆炸视图拆解进度：${(state.explodeProgress * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.primaryText
                        )
                        Slider(
                            value = state.explodeProgress,
                            onValueChange = { viewModel.setExplodeProgress(it) },
                            colors = SliderDefaults.colors(
                                thumbColor = palette.persimmonAccent,
                                activeTrackColor = palette.persimmonAccent,
                                inactiveTrackColor = palette.borderStroke
                            )
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.setExplodeProgress(if (state.explodeProgress > 0.5f) 0f else 0.85f)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.champagneBronze,
                            contentColor = InkDeskThemeTokens.DeepCedar
                        )
                    ) {
                        Text(if (state.explodeProgress > 0.5f) "一键合榫" else "一键全拆")
                    }
                }

                Button(
                    onClick = {
                        viewModel.updateCutawayPlane(enabled = !assemblyState.cutaway.enabled)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (assemblyState.cutaway.enabled) palette.bambooGreen else palette.cardSurface,
                        contentColor = if (assemblyState.cutaway.enabled) InkDeskThemeTokens.Alabaster else palette.primaryText
                    )
                ) {
                    Text(if (assemblyState.cutaway.enabled) "关闭剖面" else "剖面切片")
                }
            }

            if (assemblyState.cutaway.enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CutawayAxis.entries.forEach { axis ->
                        val activeAxis = assemblyState.cutaway.axis == axis
                        Button(
                            onClick = { viewModel.updateCutawayPlane(enabled = true, axis = axis) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeAxis) palette.persimmonAccent else palette.cardSurface,
                                contentColor = if (activeAxis) InkDeskThemeTokens.Alabaster else palette.primaryText
                            )
                        ) {
                            Text(axis.name)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${assemblyState.cutaway.axis.labelZh}：${"%.2f".format(assemblyState.cutaway.offsetMeters)}m",
                            fontSize = 12.sp,
                            color = palette.secondaryText
                        )
                        Slider(
                            value = assemblyState.cutaway.offsetMeters,
                            valueRange = -0.85f..0.95f,
                            onValueChange = { nextOffset ->
                                viewModel.updateCutawayPlane(enabled = true, offsetMeters = nextOffset)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = palette.champagneBronze,
                                activeTrackColor = palette.champagneBronze,
                                inactiveTrackColor = palette.borderStroke
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
