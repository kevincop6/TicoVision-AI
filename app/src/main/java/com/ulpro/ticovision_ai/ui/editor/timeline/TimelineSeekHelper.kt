package com.ulpro.ticovision_ai.ui.editor.timeline

import android.view.View
import android.widget.HorizontalScrollView

/**
 * Helper de cálculos para arrastre, scroll y posicionamiento del playhead.
 */
object TimelineSeekHelper {

    /**
     * Distancia fija desde el borde izquierdo visible del timeline
     * hasta la guía de reproducción.
     */
    private const val FIXED_GUIDE_OFFSET_PX = 48f

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
     * Calcula la posición global del timeline tomando como referencia
     * la guía fija a la izquierda del viewport visible.
     */
    fun calculateGlobalPositionFromScroll(
        timelineScrollX: Int,
        contentWidth: Int,
        totalDurationMs: Long
    ): Long {
        if (contentWidth <= 0 || totalDurationMs <= 0L) return 0L

        val globalX = (timelineScrollX.toFloat() + FIXED_GUIDE_OFFSET_PX)
            .coerceIn(0f, contentWidth.toFloat())

        val progress = globalX / contentWidth.toFloat()

        return (progress * totalDurationMs)
            .toLong()
            .coerceIn(0L, totalDurationMs)
    }

    /**
     * Actualiza la posición visual del playhead dentro del timeline.
     *
     * Importante:
     * - La guía queda fija en el viewport.
     * - Esta función NO hace scroll automático.
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

        timelineContainer.post {
            val trackWidth = timelineContent.width.coerceAtLeast(timelineVideoTrack.width)
            if (trackWidth <= 0) return@post

            val viewportWidth = timelineScroll.width
            if (viewportWidth <= 0) return@post

            val fixedGuideX = FIXED_GUIDE_OFFSET_PX
                .coerceAtMost(viewportWidth.toFloat())

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
}