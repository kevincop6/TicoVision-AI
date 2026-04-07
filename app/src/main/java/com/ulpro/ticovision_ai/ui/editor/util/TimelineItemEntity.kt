package com.ulpro.ticovision_ai.ui.editor.util

import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity

/**
 * Genera una clave estable para identificar un item del timeline sin depender de un id.
 */
fun TimelineItemEntity.timelineKey(): String {
    return "${type}_${sourceUri.orEmpty()}_${durationMs}"
}