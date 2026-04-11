package com.ulpro.ticovision_ai.ui.editor.timeline

import android.view.View
import android.widget.HorizontalScrollView

/**
 * Helper de cálculos para arrastre y posicionamiento del playhead.
 */
object TimelineSeekHelper {

    /**
     * Convierte la posición táctil global en coordenada local dentro del contenido del timeline.
     */
    fun getTimelineTouchXInsideContent(
        timelineContent: View,
        timelineScrollX: Int,
        rawX: Float
    ): Float {
        val location = IntArray(2)
        timelineContent.getLocationOnScreen(location)
        val contentStartX = location[0]
        return (rawX - contentStartX + timelineScrollX).coerceAtLeast(0f)
    }

    /**
     * Calcula la posición global en milisegundos dentro del timeline a partir del toque.
     */
    fun calculateGlobalPositionFromTouch(
        rawLocalX: Float,
        contentWidth: Int,
        totalDurationMs: Long
    ): Long {
        if (contentWidth <= 0 || totalDurationMs <= 0L) return 0L

        val clampedX = rawLocalX.coerceIn(0f, contentWidth.toFloat())
        val progress = clampedX / contentWidth.toFloat()

        return (progress * totalDurationMs)
            .toLong()
            .coerceIn(0L, totalDurationMs)
    }

    /**
     * Calcula la posición global a partir del scroll del timeline y una guía fija.
     */
    fun calculateGlobalPositionFromScroll(
        timelineScrollX: Int,
        viewportWidth: Int,
        contentWidth: Int,
        totalDurationMs: Long,
        fixedGuideX: Float
    ): Long {
        if (contentWidth <= 0 || totalDurationMs <= 0L) return 0L
        if (viewportWidth <= 0) return 0L

        val safeGuideX = fixedGuideX.coerceIn(0f, viewportWidth.toFloat())
        val xInContent = (timelineScrollX + safeGuideX)
            .coerceIn(0f, contentWidth.toFloat())

        val progress = xInContent / contentWidth.toFloat()

        return (progress * totalDurationMs)
            .toLong()
            .coerceIn(0L, totalDurationMs)
    }

    /**
     * Actualiza visualmente la guía/playhead sin forzar scroll del timeline.
     */
    fun updatePlayhead(
        timelineContainer: View,
        timelineScroll: HorizontalScrollView,
        timelineContent: View,
        timelineVideoTrack: View,
        playhead: View,
        playheadHandle: View,
        currentMs: Long,
        totalMs: Long,
        isDraggingPlayhead: Boolean
    ) {
        if (totalMs <= 0L) return

        val trackWidth = timelineContent.width.coerceAtLeast(timelineVideoTrack.width)
        if (trackWidth <= 0) return

        val viewportWidth = timelineScroll.width
        if (viewportWidth <= 0) return

        val fixedGuideX = 48f.coerceAtMost(viewportWidth.toFloat())

        playhead.translationX = fixedGuideX - (playhead.width / 2f)
        playheadHandle.translationX = fixedGuideX - (playheadHandle.width / 2f)

        if (playhead.visibility != View.VISIBLE) {
            playhead.visibility = View.VISIBLE
        }

        if (playheadHandle.visibility != View.VISIBLE) {
            playheadHandle.visibility = View.VISIBLE
        }

        playhead.bringToFront()
        playheadHandle.bringToFront()
    }
}