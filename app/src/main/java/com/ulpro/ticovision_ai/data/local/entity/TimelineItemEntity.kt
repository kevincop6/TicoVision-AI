package com.ulpro.ticovision_ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad que representa un elemento dentro del timeline del editor.
 * Puede ser video, imagen, audio, texto, efecto u otro recurso.
 */
@Entity(
    tableName = "timeline_items",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["project_id"],
            childColumns = ["project_owner_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_owner_id"])
    ]
)
data class TimelineItemEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "item_id")
    val itemId: Long = 0L,

    @ColumnInfo(name = "project_owner_id")
    val projectOwnerId: Long,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "track_index")
    val trackIndex: Int = 0,

    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long = 0L,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    @ColumnInfo(name = "source_uri")
    val sourceUri: String? = null,

    @ColumnInfo(name = "thumbnail_uri")
    val thumbnailUri: String? = null,

    @ColumnInfo(name = "text_value")
    val textValue: String? = null,

    @ColumnInfo(name = "effect_name")
    val effectName: String? = null,

    @ColumnInfo(name = "extra_json")
    val extraJson: String? = null
)