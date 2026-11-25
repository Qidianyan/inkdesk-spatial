package com.elidot.inkdesk.spatial

import com.elidot.inkdesk.model.AmbientLightingTheme
import com.elidot.inkdesk.model.ArtifactCatalog
import com.elidot.inkdesk.model.LayoutPreset
import com.elidot.inkdesk.model.SoundAnchorId
import com.elidot.inkdesk.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialSessionStoreTest {

    @Test
    fun catalogContainsAllFourEasternSpatialExhibitsWithRichParts() {
        assertEquals(4, ArtifactCatalog.exhibits.size)
        ArtifactCatalog.exhibits.forEach { exhibit ->
            assertTrue("${exhibit.id} must have >= 6 structural parts", exhibit.parts.size >= 6)
            exhibit.parts.forEach { part ->
                assertTrue(part.mechanicsPrinciple.isNotBlank())
                assertTrue(part.historicalNote.isNotBlank())
            }
        }
    }

    @Test
    fun bidirectionalPartSelectionNotesAndMarkdownExportWorkEndToEnd() {
        val store = SpatialSessionStore()
        store.selectArtifact("armillary_sphere")
        assertEquals("armillary_sphere", store.state.activeArtifactId)

        store.selectPart("ecliptic_ring")
        assertEquals("ecliptic_ring", store.state.selectedPartId)
        store.setExplodeProgress(0.65f)
        store.setAmbientTheme(AmbientLightingTheme.CANDLELIGHT)
        store.setLayoutPreset(LayoutPreset.SIDE_BY_SIDE_COMPARE)

        assertNull("Blank notes must be ignored", store.addNote("   "))
        val added = store.addNote("黄道环与赤道环呈 23.5 度斜交，爆炸视图下观测极为直观。")
        assertNotNull(added)
        store.togglePinNote(added!!.id)

        val report = store.exportMarkdownReport()
        assertTrue(report.contains("明代浑天仪"))
        assertTrue(report.contains("黄道斜环"))
        assertTrue(report.contains("23.5 度斜交"))
        assertTrue(report.contains("烛夜"))
        assertTrue(report.contains("并排对比"))
        assertTrue(report.contains("65%"))

        store.deleteNote(added.id)
        assertFalse(store.state.notes.any { it.id == added.id })
    }

    @Test
    fun pomodoroTimerTicksToCompletionAndIncrementsSessionCount() {
        val store = SpatialSessionStore()
        store.resetFocusTimer(totalSeconds = 120)
        assertEquals("02:00", store.state.focusTimer.formattedClock())

        store.toggleFocusTimer()
        assertTrue(store.state.focusTimer.isRunning)

        store.tickFocusTimer(45)
        assertEquals("01:15", store.state.focusTimer.formattedClock())

        store.tickFocusTimer(90)
        assertEquals("00:00", store.state.focusTimer.formattedClock())
        assertFalse(store.state.focusTimer.isRunning)
        assertEquals(1, store.state.focusTimer.completedSessions)
    }

    @Test
    fun soundAnchorMutationAndWorkspaceResetOperateDeterministically() {
        val store = SpatialSessionStore()
        store.toggleSoundAnchor(SoundAnchorId.CHIME_BELLS)
        store.setSoundAnchorGain(SoundAnchorId.CHIME_BELLS, 0.88f)
        store.moveSoundAnchor(SoundAnchorId.CHIME_BELLS, Vec3(1.2f, 0.3f, -1.2f))

        val bells = store.state.soundAnchors.first { it.id == SoundAnchorId.CHIME_BELLS }
        assertTrue(bells.enabled)
        assertEquals(0.88f, bells.gain, 1e-4f)
        assertEquals(1.2f, bells.positionMeters.x, 1e-3f)

        store.setLayoutPreset(LayoutPreset.FOCUS_SINGLE)
        store.setHeadYaw(40f)
        store.resetWorkspaceLayout()
        assertEquals(LayoutPreset.SURROUND_ARC, store.state.layoutPreset)
        assertEquals(0f, store.state.headYawDegrees, 1e-4f)
    }

    @Test
    fun titaniumVisionThemeTokensStayStrictlyFreeOfCoolBluePurpleHues() {
        AmbientLightingTheme.entries.forEach { theme ->
            val r = (theme.surfaceHex shr 16) and 0xFF
            val g = (theme.surfaceHex shr 8) and 0xFF
            val b = theme.surfaceHex and 0xFF
            assertTrue("Theme ${theme.name} surface must stay neutral/warm (R >= B)", r >= b)
            assertTrue("Theme ${theme.name} surface must stay neutral/warm (G >= B)", g >= b)
        }
    }

    @Test
    fun allFourExhibitsProvideDistinctGlbAssetsAndNonZeroTotalDimensions() {
        val paths = ArtifactCatalog.exhibits.map { it.glbAssetPath }.toSet()
        assertEquals(4, paths.size)
        ArtifactCatalog.exhibits.forEach { exhibit ->
            assertTrue(exhibit.totalDimensionsMm.x > 100f)
            assertTrue(exhibit.totalDimensionsMm.y > 100f)
            assertTrue(exhibit.totalDimensionsMm.z > 100f)
        }
    }
}
