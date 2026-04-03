package com.ulpro.ticovision_ai.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity

/**
 * Relación entre un proyecto y todos sus elementos del timeline.
 */
data class ProjectWithTimelineItems(

    @Embedded
    val project: ProjectEntity,

    @Relation(
        parentColumn = "project_id",
        entityColumn = "project_owner_id"
    )
    val timelineItems: List<TimelineItemEntity>
)