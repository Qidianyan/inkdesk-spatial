package com.elidot.inkdesk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elidot.inkdesk.model.LayoutPreset

/**
 * Window C (Right Wing Floating Panel):
 * Real-time bidirectional structural inspector synced to Stage A's selected 3D component,
 * spatial sticky notes CRUD, 3-window layout preset switcher, and Markdown report export.
 */
@Composable
fun NotesInspectorPanel(
    viewModel: InkDeskViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val anchorState by viewModel.anchorState.collectAsState()
    val calloutAnchors = viewModel.calloutAnchors(anchorState)
    val palette = InkDeskThemeTokens.paletteFor(state.ambientTheme)
    val selectedPart = viewModel.selectedPart(state)
    val artifactNotes = state.notes.filter { it.artifactId == state.activeArtifactId }
    var draftPinTitle by remember { mutableStateOf("") }
    var draftNote by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var exportedPreview by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.activeArtifactId) {
        pinError = null
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(26.dp))
            .border(1.5.dp, palette.borderStroke, RoundedCornerShape(26.dp)),
        color = palette.panelSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "结构批注与空间手记",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryText
            )

            // Layout Preset Switcher (Surround / Focus / Side-by-Side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LayoutPreset.entries.forEach { preset ->
                    val active = preset == state.layoutPreset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) palette.bambooGreen else palette.cardSurface)
                            .clickable { viewModel.setLayoutPreset(preset) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.labelZh,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (active) InkDeskThemeTokens.Alabaster else palette.primaryText
                        )
                    }
                }
            }

            // Live Synced Component Inspector Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.elevatedSurface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${selectedPart.nameZh} · ${selectedPart.materialZh}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.persimmonAccent
                )
                Text(
                    text = "力学原理：${selectedPart.mechanicsPrinciple}",
                    fontSize = 12.sp,
                    color = palette.primaryText
                )
                Text(
                    text = "历史注记：${selectedPart.historicalNote}",
                    fontSize = 12.sp,
                    color = palette.secondaryText
                )
            }

            // Bidirectional 3D Spatial Callout Anchor Composer + Sticky Note
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draftPinTitle,
                    onValueChange = {
                        draftPinTitle = it
                        if (pinError != null) pinError = null
                    },
                    modifier = Modifier.weight(0.9f),
                    singleLine = true,
                    placeholder = { Text("3D 批注钉标题") }
                )
                OutlinedTextField(
                    value = draftNote,
                    onValueChange = {
                        draftNote = it
                        if (pinError != null) pinError = null
                    },
                    modifier = Modifier.weight(1.1f),
                    singleLine = true,
                    placeholder = { Text("工艺/力学批注") }
                )
                Button(
                    onClick = {
                        val res = viewModel.addCustomCalloutAnchor(draftPinTitle, draftNote)
                        if (res.ok) {
                            pinError = null
                            draftPinTitle = ""
                            draftNote = ""
                        } else {
                            pinError = res.errorZh
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.persimmonAccent,
                        contentColor = InkDeskThemeTokens.Alabaster
                    )
                ) {
                    Text("钉入")
                }
                Button(
                    onClick = {
                        if (viewModel.addNote(draftNote) != null) {
                            pinError = null
                            draftNote = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.cardSurface,
                        contentColor = palette.primaryText
                    )
                ) {
                    Text("记手记")
                }
            }
            if (pinError != null) {
                Text(
                    text = pinError!!,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.persimmonAccent
                )
            }

            // Spatial Callout Anchors & Sticky Notes List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(calloutAnchors, key = { it.id }) { anchor ->
                    val isFocused = anchor.id == anchorState.selectedAnchorId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isFocused) palette.elevatedSurface else palette.cardSurface)
                            .border(
                                width = if (isFocused) 1.5.dp else 0.dp,
                                color = if (isFocused) palette.champagneBronze else palette.cardSurface,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.selectCalloutAnchor(anchor.id) }
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${anchor.titleZh} · ${anchor.craftCategoryZh}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFocused) palette.persimmonAccent else palette.primaryText
                            )
                            if (anchor.isCustom) {
                                Text(
                                    text = "删除",
                                    fontSize = 11.sp,
                                    color = palette.persimmonAccent,
                                    modifier = Modifier.clickable { viewModel.deleteCustomCalloutAnchor(anchor.id) }
                                )
                            }
                        }
                        Text(
                            text = if (isFocused) "${anchor.eraOrPeriodZh}\n${anchor.craftDetailZh}" else anchor.mechanicsSummaryZh,
                            fontSize = 11.sp,
                            color = palette.secondaryText
                        )
                    }
                }
                items(artifactNotes, key = { it.id }) { note ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.cardSurface)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = (if (note.pinned) "[置顶] " else "") + note.partNameZh,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.bambooGreen,
                                modifier = Modifier.clickable { viewModel.selectPart(note.partId) }
                            )
                            Text(
                                text = "删除",
                                fontSize = 11.sp,
                                color = palette.persimmonAccent,
                                modifier = Modifier.clickable { viewModel.deleteNote(note.id) }
                            )
                        }
                        Text(
                            text = note.content,
                            fontSize = 12.sp,
                            color = palette.primaryText
                        )
                    }
                }
            }

            // Export Markdown Report & Reset Workspace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { exportedPreview = viewModel.exportMarkdownReport() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.champagneBronze,
                        contentColor = InkDeskThemeTokens.DeepCedar
                    )
                ) {
                    Text("导出 Markdown 研学报告")
                }
                Button(
                    onClick = { viewModel.resetWorkspaceLayout() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.cardSurface,
                        contentColor = palette.primaryText
                    )
                ) {
                    Text("重置三窗")
                }
            }

            if (exportedPreview != null) {
                Text(
                    text = "已生成研学报告（${exportedPreview!!.length} 字符）",
                    fontSize = 11.sp,
                    color = palette.bambooGreen
                )
            }
        }
    }
}
