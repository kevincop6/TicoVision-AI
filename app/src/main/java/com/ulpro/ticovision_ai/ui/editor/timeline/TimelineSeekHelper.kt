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
     * Actualiza la posición visual del playhead dentro del timeline.
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

            val progress = currentMs.toFloat() / totalMs.toFloat()
            val xInContent = (trackWidth * progress).coerceIn(0f, trackWidth.toFloat())

            playhead.translationX = xInContent - (playhead.width / 2f)
            playheadHandle.translationX = xInContent - (playheadHandle.width / 2f)

            if (!isDraggingPlayhead) {
                val scrollViewWidth = timelineScroll.width
                val desiredScroll = (xInContent - scrollViewWidth / 2f)
                    .toInt()
                    .coerceAtLeast(0)

                timelineScroll.smoothScrollTo(desiredScroll, 0)
            }
        }
    }
}