package com.ulpro.ticovision_ai.ui.editor.timeline

import android.view.View
import android.widget.HorizontalScrollView

/**
 * Helper de calculos para arrastre y posicionamiento del playhead.
 */
object TimelineSeekHelper {

    /**
     * Convierte la posicion tactil global en coordenada local dentro del contenido del timeline.
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
     * Calcula la posicion global en milisegundos dentro del timeline a partir del toque.
     */
    fun calculateGlobalPositionFromTouch(
        rawLocalX: Float,
        trackWidth: Int,
        totalDurationMs: Long,
        contentStartOffsetPx: Int = 0
    ): Long {
        if (trackWidth <= 0 || totalDurationMs <= 0L) return 0L

        val xInsideTrack = (rawLocalX - contentStartOffsetPx)
            .coerceIn(0f, trackWidth.toFloat())
        val progress = xInsideTrack / trackWidth.toFloat()

        return (progress * totalDurationMs)
            .toLong()
            .coerceIn(0L, totalDurationMs)
    }

    /**
     * Calcula la posicion global a partir del scroll del timeline y una guia fija.
     */
    fun calculateGlobalPositionFromScroll(
        timelineScrollX: Int,
        viewportWidth: Int,
        trackWidth: Int,
        totalDurationMs: Long,
        fixedGuideX: Float,
        contentStartOffsetPx: Int = 0
    ): Long {
        if (trackWidth <= 0 || totalDurationMs <= 0L) return 0L
        if (viewportWidth <= 0) return 0L

        val safeGuideX = fixedGuideX.coerceIn(0f, viewportWidth.toFloat())
        val xInsideTrack = (timelineScrollX + safeGuideX - contentStartOffsetPx)
            .coerceIn(0f, trackWidth.toFloat())
        val progress = xInsideTrack / trackWidth.toFloat()

        return (progress * totalDurationMs)
            .toLong()
            .coerceIn(0L, totalDurationMs)
    }

    /**
     * Actualiza visualmente la guia/playhead y sincroniza el scroll con el tiempo actual.
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
        isDraggingPlayhead: Boolean,
        fixedGuideX: Float
    ) {
        if (totalMs <= 0L) return

        val trackWidth = timelineVideoTrack.width
        if (trackWidth <= 0) return

        val viewportWidth = timelineScroll.width
        if (viewportWidth <= 0) return

        val safeGuideX = fixedGuideX.coerceIn(0f, viewportWidth.toFloat())

        playhead.translationX = safeGuideX - (playhead.width / 2f)
        playheadHandle.translationX = safeGuideX - (playheadHandle.width / 2f)

        if (!isDraggingPlayhead) {
            val progress = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            val globalPixelX = progress * trackWidth
            val targetScrollX = (timelineContent.paddingStart + globalPixelX - safeGuideX).toInt()
            val maxScrollX = (timelineContent.width - viewportWidth).coerceAtLeast(0)

            timelineScroll.scrollTo(targetScrollX.coerceIn(0, maxScrollX), 0)
        }

        if (playhead.visibility != View.VISIBLE) playhead.visibility = View.VISIBLE
        if (playheadHandle.visibility != View.VISIBLE) playheadHandle.visibility = View.VISIBLE

        playhead.bringToFront()
        playheadHandle.bringToFront()
    }
}
