package com.jeff.tclcolorcontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelWindowPositionerTest {
    private val bounds = PanelWindowBounds(
        screenWidthPx = 1200,
        screenHeightPx = 800,
        windowWidthPx = 300,
        windowHeightPx = 200,
        marginPx = 16,
    )

    @Test
    fun clampKeepsPositionInsideScreenMargins() {
        assertEquals(
            PanelWindowCoordinates(16, 16),
            PanelWindowPositioner.clamp(PanelWindowCoordinates(-200, -80), bounds),
        )
        assertEquals(
            PanelWindowCoordinates(884, 584),
            PanelWindowPositioner.clamp(PanelWindowCoordinates(2000, 1000), bounds),
        )
    }

    @Test
    fun moveByAppliesRoundedDeltaAndClampsResult() {
        assertEquals(
            PanelWindowCoordinates(111, 47),
            PanelWindowPositioner.moveBy(
                coordinates = PanelWindowDragCoordinates(100f, 50f),
                deltaX = 10.6f,
                deltaY = -3.2f,
                bounds = bounds,
            ).windowCoordinates,
        )
        assertEquals(
            PanelWindowCoordinates(16, 584),
            PanelWindowPositioner.moveBy(
                coordinates = PanelWindowDragCoordinates(100f, 50f),
                deltaX = -500f,
                deltaY = 1000f,
                bounds = bounds,
            ).windowCoordinates,
        )
    }

    @Test
    fun moveByAccumulatesSubPixelDeltasDuringDrag() {
        val firstMove = PanelWindowPositioner.moveBy(
            coordinates = PanelWindowDragCoordinates(100f, 100f),
            deltaX = 0.3f,
            deltaY = 0.3f,
            bounds = bounds,
        )
        val secondMove = PanelWindowPositioner.moveBy(
            coordinates = firstMove.dragCoordinates,
            deltaX = 0.3f,
            deltaY = 0.3f,
            bounds = bounds,
        )

        assertEquals(PanelWindowCoordinates(100, 100), firstMove.windowCoordinates)
        assertEquals(PanelWindowCoordinates(101, 101), secondMove.windowCoordinates)
    }

    @Test
    fun anchorCreatesLegacyStartPositionWhenNoExactPositionExists() {
        assertEquals(
            PanelWindowCoordinates(450, 72),
            PanelWindowPositioner.fromAnchor(
                anchor = PanelWindowAnchor.TopCenter,
                bounds = bounds,
                topOffsetPx = 72,
                endOffsetPx = 16,
            ),
        )
        assertEquals(
            PanelWindowCoordinates(884, 528),
            PanelWindowPositioner.fromAnchor(
                anchor = PanelWindowAnchor.BottomRight,
                bounds = bounds,
                topOffsetPx = 72,
                endOffsetPx = 16,
            ),
        )
    }

    @Test
    fun legacyPositionKeyCreatesStartPositionWhenNoExactPositionExists() {
        assertEquals(
            PanelWindowCoordinates(884, 528),
            PanelWindowPositioner.fromLegacyPositionKey(
                positionKey = "bottom_right",
                bounds = bounds,
                topOffsetPx = 72,
                endOffsetPx = 16,
            ),
        )
        assertEquals(
            null,
            PanelWindowPositioner.fromLegacyPositionKey(
                positionKey = "unknown",
                bounds = bounds,
                topOffsetPx = 72,
                endOffsetPx = 16,
            ),
        )
    }

    @Test
    fun exactSavedPositionCanBeClampedWithoutUsingLegacyAnchor() {
        val saved = PanelWindowCoordinates(240, 320)

        assertEquals(saved, PanelWindowPositioner.clamp(saved, bounds))
    }
}
