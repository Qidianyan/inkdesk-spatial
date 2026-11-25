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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elidot.inkdesk.model.AmbientLightingTheme
import com.elidot.inkdesk.model.Vec3

/**
 * Window B (Left Wing Floating Panel):
 * Manages the 4 movable 3D spatial soundscape anchors (HRTF azimuth & distance attenuation),
 * the 25-minute immersive Pomodoro focus timer, and ambient light color-temperature switching.
 */
@Composable
fun AudioFocusDockPanel(
    viewModel: InkDeskViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val palette = InkDeskThemeTokens.paletteFor(state.ambientTheme)
    val audioStates = viewModel.evaluateAudio(state).associateBy { it.anchorId }

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
                text = "空间声景锚点 · 专注坞",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryText
            )

            // 4 Spatial Sound Anchors
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.soundAnchors.forEach { anchor ->
                    val render = audioStates[anchor.id]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.cardSurface)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = anchor.id.titleZh,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.primaryText
                                )
                                Text(
                                    text = "方位 ${render?.azimuthDegrees?.toInt() ?: 0}° · 距离 ${"%.2f".format(render?.distanceMeters ?: 1f)}m",
                                    fontSize = 11.sp,
                                    color = palette.secondaryText
                                )
                            }
                            Switch(
                                checked = anchor.enabled,
                                onCheckedChange = { viewModel.toggleSoundAnchor(anchor.id) }
                            )
                        }
                        Slider(
                            value = anchor.gain,
                            onValueChange = { viewModel.setSoundAnchorGain(anchor.id, it) },
                            colors = SliderDefaults.colors(
                                thumbColor = palette.bambooGreen,
                                activeTrackColor = palette.bambooGreen,
                                inactiveTrackColor = palette.borderStroke
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("左前" to Vec3(-1.3f, 0.25f, -1.0f), "近侧" to Vec3(-0.6f, -0.1f, -0.7f), "右前" to Vec3(1.3f, 0.25f, -1.0f)).forEach { (label, pos) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.elevatedSurface)
                                        .clickable { viewModel.moveSoundAnchor(anchor.id, pos) }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 11.sp, color = palette.secondaryText)
                                }
                            }
                        }
                    }
                }
            }

            // 25-Minute Immersive Pomodoro Focus Timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.elevatedSurface)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("沉浸番茄钟", fontSize = 12.sp, color = palette.secondaryText)
                    Text(
                        text = state.focusTimer.formattedClock(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.persimmonAccent
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.toggleFocusTimer() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.persimmonAccent,
                            contentColor = InkDeskThemeTokens.Alabaster
                        )
                    ) {
                        Text(if (state.focusTimer.isRunning) "暂停" else "开始")
                    }
                    Button(
                        onClick = { viewModel.resetFocusTimer() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.cardSurface,
                            contentColor = palette.primaryText
                        )
                    ) {
                        Text("重置")
                    }
                }
            }

            // Ambient Color Temperature Switcher (Dawn / Afternoon / Candlelight)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AmbientLightingTheme.entries.forEach { theme ->
                    val selected = theme == state.ambientTheme
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) palette.champagneBronze else palette.cardSurface)
                            .clickable { viewModel.setAmbientTheme(theme) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${theme.labelZh} ${theme.kelvin}K",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) InkDeskThemeTokens.DeepCedar else palette.primaryText
                        )
                    }
                }
            }
        }
    }
}
