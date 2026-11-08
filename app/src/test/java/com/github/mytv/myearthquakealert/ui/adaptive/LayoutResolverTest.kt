package com.github.mytv.myearthquakealert.ui.adaptive

import androidx.compose.ui.geometry.Rect
import com.github.mytv.myearthquakealert.data.model.LayoutOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayoutResolverTest {

    private val verticalFoldHalf = FoldInfo(halfOpened = true, vertical = true, separating = true, occludesFully = false, bounds = Rect(1080f, 0f, 1080f, 2000f))
    private val horizontalFoldHalf = FoldInfo(halfOpened = true, vertical = false, separating = true, occludesFully = false, bounds = Rect(0f, 1000f, 2160f, 1000f))
    private val dualHinge = FoldInfo(halfOpened = false, vertical = true, separating = true, occludesFully = true, bounds = Rect(1350f, 0f, 1434f, 1800f))
    private val flatFold = FoldInfo(halfOpened = false, vertical = true, separating = false, occludesFully = false, bounds = Rect(1080f, 0f, 1080f, 2000f))

    private fun resolve(
        width: Int,
        height: Int = 800,
        tv: Boolean = false,
        folds: List<FoldInfo> = emptyList(),
        settings: LayoutOverride = LayoutOverride.AUTO,
        session: LayoutOverride? = null,
    ) = LayoutResolver.resolve(LayoutInputs(width, height, isTelevision = tv, folds = folds, settingsOverride = settings, sessionOverride = session))

    @Test
    fun widthClasses() {
        assertEquals(LayoutKind.PHONE, resolve(390).kind)
        assertEquals(LayoutKind.PHONE, resolve(599).kind)
        assertEquals(LayoutKind.MEDIUM, resolve(600).kind)
        assertEquals(LayoutKind.MEDIUM, resolve(839).kind)
        assertEquals(LayoutKind.TABLET, resolve(840).kind)
        assertEquals(LayoutKind.TABLET, resolve(1280).kind)
    }

    @Test
    fun televisionBeatsWidthAndPosture() {
        val layout = resolve(960, tv = true, folds = listOf(verticalFoldHalf))
        assertEquals(LayoutKind.TV, layout.kind)
        assertEquals(LayoutKind.TV, layout.autoKind)
        assertEquals(true, layout.isTelevision)
    }

    @Test
    fun priorityOrder() {
        // settings beats device type
        val fromSettings = resolve(390, tv = true, settings = LayoutOverride.KIOSK)
        assertEquals(LayoutKind.KIOSK, fromSettings.kind)
        assertEquals(LayoutKind.TV, fromSettings.autoKind)
        assertEquals(LayoutSource.SETTINGS, fromSettings.source)
        // session beats settings
        val fromSession = resolve(390, settings = LayoutOverride.KIOSK, session = LayoutOverride.TABLET)
        assertEquals(LayoutKind.TABLET, fromSession.kind)
        assertEquals(LayoutSource.SESSION, fromSession.source)
        // a session "自动" ignores the persistent pick for this session
        val sessionAuto = resolve(1280, settings = LayoutOverride.PHONE, session = LayoutOverride.AUTO)
        assertEquals(LayoutKind.TABLET, sessionAuto.kind)
        assertEquals(LayoutSource.AUTO, sessionAuto.source)
        // nothing picked
        assertEquals(LayoutSource.AUTO, resolve(390).source)
    }

    @Test
    fun postures() {
        assertEquals(Posture.BOOK, resolve(673, folds = listOf(verticalFoldHalf)).posture)
        assertEquals(LayoutKind.MEDIUM, resolve(900, folds = listOf(verticalFoldHalf)).kind)
        assertEquals(Posture.TABLETOP, resolve(840, folds = listOf(horizontalFoldHalf)).posture)
        assertEquals(LayoutKind.TABLET, resolve(840, folds = listOf(horizontalFoldHalf)).kind)
        val dual = resolve(1114, folds = listOf(dualHinge))
        assertEquals(Posture.DUAL, dual.posture)
        assertEquals(dualHinge.bounds, dual.hingeBounds)
        assertEquals(true, dual.hingeIsVertical)
        val flat = resolve(673, folds = listOf(flatFold))
        assertEquals(Posture.FLAT, flat.posture)
        assertNull(flat.hingeBounds)
    }

    @Test
    fun arrangementsFollowSwitches() {
        val book = resolve(673, folds = listOf(verticalFoldHalf))
        assertEquals(ShellArrangement.BOOK_SPLIT, LayoutResolver.arrangement(book, spanHinge = true, tabletopSplit = true, singleScreen = false))
        assertEquals(ShellArrangement.STANDARD, LayoutResolver.arrangement(book, spanHinge = false, tabletopSplit = true, singleScreen = false))

        val tabletop = resolve(840, folds = listOf(horizontalFoldHalf))
        assertEquals(ShellArrangement.TABLETOP_SPLIT, LayoutResolver.arrangement(tabletop, spanHinge = true, tabletopSplit = true, singleScreen = false))
        assertEquals(ShellArrangement.STANDARD, LayoutResolver.arrangement(tabletop, spanHinge = true, tabletopSplit = false, singleScreen = false))

        val dual = resolve(1114, folds = listOf(dualHinge))
        assertEquals(ShellArrangement.DUAL_SPAN, LayoutResolver.arrangement(dual, spanHinge = true, tabletopSplit = true, singleScreen = false))
        assertEquals(ShellArrangement.DUAL_SINGLE, LayoutResolver.arrangement(dual, spanHinge = true, tabletopSplit = true, singleScreen = true))
    }

    @Test
    fun manualLayoutIgnoresPosture() {
        val forced = resolve(673, folds = listOf(verticalFoldHalf), settings = LayoutOverride.PHONE)
        assertEquals(LayoutKind.PHONE, forced.kind)
        assertEquals(LayoutKind.MEDIUM, forced.autoKind)
        assertEquals(ShellArrangement.STANDARD, LayoutResolver.arrangement(forced, spanHinge = true, tabletopSplit = true, singleScreen = false))
    }

    @Test
    fun flatWithoutHingeIsStandard() {
        val tablet = resolve(1280)
        assertEquals(ShellArrangement.STANDARD, LayoutResolver.arrangement(tablet, spanHinge = true, tabletopSplit = true, singleScreen = true))
    }
}
