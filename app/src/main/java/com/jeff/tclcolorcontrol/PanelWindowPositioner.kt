package com.jeff.tclcolorcontrol

import kotlin.math.roundToInt

data class PanelWindowCoordinates(
    val xPx: Int,
    val yPx: Int,
)

data class PanelWindowDragCoordinates(
    val xPx: Float,
    val yPx: Float,
)

data class PanelWindowMove(
    val dragCoordinates: PanelWindowDragCoordinates,
    val windowCoordinates: PanelWindowCoordinates,
)

data class PanelWindowBounds(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val windowWidthPx: Int,
    val windowHeightPx: Int,
    val marginPx: Int,
)

enum class PanelWindowAnchor {
    TopCenter,
    TopRight,
    BottomRight,
    BottomCenter,
}

object PanelWindowPositioner {
    fun fromAnchor(
        anchor: PanelWindowAnchor,
        bounds: PanelWindowBounds,
        topOffsetPx: Int,
        endOffsetPx: Int,
    ): PanelWindowCoordinates {
        val centeredX = (bounds.screenWidthPx - bounds.windowWidthPx) / 2
        val endX = bounds.screenWidthPx - bounds.windowWidthPx - endOffsetPx
        val bottomY = bounds.screenHeightPx - bounds.windowHeightPx - topOffsetPx
        val coordinates = when (anchor) {
            PanelWindowAnchor.TopCenter -> PanelWindowCoordinates(centeredX, topOffsetPx)
            PanelWindowAnchor.TopRight -> PanelWindowCoordinates(endX, topOffsetPx)
            PanelWindowAnchor.BottomRight -> PanelWindowCoordinates(endX, bottomY)
            PanelWindowAnchor.BottomCenter -> PanelWindowCoordinates(centeredX, bottomY)
        }
        return clamp(coordinates, bounds)
    }

    fun moveBy(
        coordinates: PanelWindowDragCoordinates,
        deltaX: Float,
        deltaY: Float,
        bounds: PanelWindowBounds,
    ): PanelWindowMove {
        val dragCoordinates = PanelWindowDragCoordinates(
            xPx = coordinates.xPx + deltaX,
            yPx = coordinates.yPx + deltaY,
        )
        val rawWindowCoordinates =
            PanelWindowCoordinates(
                xPx = dragCoordinates.xPx.roundToInt(),
                yPx = dragCoordinates.yPx.roundToInt(),
            )
        val windowCoordinates = clamp(rawWindowCoordinates, bounds)
        return PanelWindowMove(
            dragCoordinates = if (windowCoordinates == rawWindowCoordinates) {
                dragCoordinates
            } else {
                windowCoordinates.toDragCoordinates()
            },
            windowCoordinates = windowCoordinates,
        )
    }

    fun clamp(
        coordinates: PanelWindowCoordinates,
        bounds: PanelWindowBounds,
    ): PanelWindowCoordinates {
        val maxX = (bounds.screenWidthPx - bounds.windowWidthPx - bounds.marginPx)
            .coerceAtLeast(bounds.marginPx)
        val maxY = (bounds.screenHeightPx - bounds.windowHeightPx - bounds.marginPx)
            .coerceAtLeast(bounds.marginPx)
        return PanelWindowCoordinates(
            xPx = coordinates.xPx.coerceIn(bounds.marginPx, maxX),
            yPx = coordinates.yPx.coerceIn(bounds.marginPx, maxY),
        )
    }
}

fun PanelWindowCoordinates.toDragCoordinates(): PanelWindowDragCoordinates =
    PanelWindowDragCoordinates(xPx = xPx.toFloat(), yPx = yPx.toFloat())
