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
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"])
    ]
)
data class ProjectEditEntity(

    /**
     * Identificador único de la edición.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /**
     * Identificador del proyecto al que pertenece esta edición.
     */
    @ColumnInfo(name = "project_id")
    val projectId: Long,

    /**
     * Tipo de edición realizada.
     * Ejemplo: trim, split, volume, filter.
     */
    @ColumnInfo(name = "edit_type")
    val editType: String,

    /**
     * Tiempo inicial aplicado a la edición, si corresponde.
     */
    @ColumnInfo(name = "start_ms")
    val startMs: Long? = null,

    /**
     * Tiempo final aplicado a la edición, si corresponde.
     */
    @ColumnInfo(name = "end_ms")
    val endMs: Long? = null,

    /**
     * Valor numérico de la edición, por ejemplo volumen o velocidad.
     */
    @ColumnInfo(name = "value")
    val value: Float? = null,

    /**
     * Información adicional serializada si fuera necesaria.
     */
    @ColumnInfo(name = "extra_data")
    val extraData: String? = null,

    /**
     * Fecha en la que se registró la edición.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)