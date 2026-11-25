package com.elidot.inkdesk.ui

import androidx.compose.ui.graphics.Color
import com.elidot.inkdesk.model.AmbientLightingTheme

/**
 * VisionOS-grade light warm-stone titanium frosted glass & Kyoto Hinoki architectural tokens.
 * Strictly avoids blue, cyan, teal, and purple hues to minimize XR optical fatigue.
 */
data class InkDeskPalette(
    val panelSurface: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val borderStroke: Color,
    val persimmonAccent: Color,
    val champagneBronze: Color,
    val bambooGreen: Color
)

object InkDeskThemeTokens {
    val Persimmon = Color(0xFFD64527)
    val ChampagneBronze = Color(0xFFC98A3A)
    val BambooGreen = Color(0xFF4F6253)
    val DeepCedar = Color(0xFF232220)
    val Alabaster = Color(0xFFF3F2EE)

    fun paletteFor(theme: AmbientLightingTheme): InkDeskPalette = when (theme) {
        AmbientLightingTheme.DAWN -> InkDeskPalette(
            panelSurface = Color(0xDCEAE8E3),
            cardSurface = Color(0xE8F4F3F0),
            elevatedSurface = Color(0xF5FAF9F7),
            primaryText = Color(0xFF232220),
            secondaryText = Color(0xFF63605B),
            borderStroke = Color(0x4DFFFFFF),
            persimmonAccent = Persimmon,
            champagneBronze = ChampagneBronze,
            bambooGreen = BambooGreen
        )
        AmbientLightingTheme.AFTERNOON -> InkDeskPalette(
            panelSurface = Color(0xE0E5E1D9),
            cardSurface = Color(0xECEFECE6),
            elevatedSurface = Color(0xF5F7F5F0),
            primaryText = Color(0xFF211F1D),
            secondaryText = Color(0xFF5F5B54),
            borderStroke = Color(0x55C98A3A),
            persimmonAccent = Color(0xFFC93E22),
            champagneBronze = Color(0xFFBD7F32),
            bambooGreen = BambooGreen
        )
        AmbientLightingTheme.CANDLELIGHT -> InkDeskPalette(
            panelSurface = Color(0xE0171614),
            cardSurface = Color(0xE622211E),
            elevatedSurface = Color(0xF02C2A27),
            primaryText = Color(0xFFF2F0EC),
            secondaryText = Color(0xFFA8A39B),
            borderStroke = Color(0x40C98A3A),
            persimmonAccent = Color(0xFFE35638),
            champagneBronze = Color(0xFFDDA04F),
            bambooGreen = Color(0xFF7B947F)
        )
    }
}
