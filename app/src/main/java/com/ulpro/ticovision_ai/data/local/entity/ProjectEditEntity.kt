package com.ulpro.ticovision_ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad que representa una acción o edición realizada sobre un proyecto.
 */
@Entity(
    tableName = "project_edits",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["project_id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"])
    ]
)
data class ProjectEditEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "edit_id")
    val editId: Long = 0L,

    @ColumnInfo(name = "project_id")
    val projectId: Long,

    @ColumnInfo(name = "edit_type")
    val editType: String,

    @ColumnInfo(name = "start_ms")
    val startMs: Long? = null,

    @ColumnInfo(name = "end_ms")
    val endMs: Long? = null,

    @ColumnInfo(name = "value")
    val value: Float? = null,

    @ColumnInfo(name = "extra_data")
    val extraData: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)