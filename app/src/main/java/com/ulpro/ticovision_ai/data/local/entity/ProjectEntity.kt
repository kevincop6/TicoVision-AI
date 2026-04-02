package com.ulpro.ticovision_ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad que representa un proyecto dentro de la base de datos local.
 */
@Entity(tableName = "projects")
data class ProjectEntity(

    /**
     * Identificador único del proyecto.
     * Se genera automáticamente en la base de datos.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /**
     * Título principal del proyecto.
     */
    @ColumnInfo(name = "title")
    val title: String,

    /**
     * Subtítulo o descripción corta del proyecto.
     */
    @ColumnInfo(name = "subtitle")
    val subtitle: String,

    /**
     * URI del archivo fuente de video o imagen asociado al proyecto.
     */
    @ColumnInfo(name = "source_uri")
    val sourceUri: String? = null,

    /**
     * Ruta local de la miniatura del proyecto.
     */
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,

    /**
     * Duración total del proyecto en milisegundos.
     */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    /**
     * Fecha de creación en milisegundos.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /**
     * Fecha de última actualización en milisegundos.
     */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    /**
     * Estado actual del proyecto.
     * Ejemplo: draft, editing, exported.
     */
    @ColumnInfo(name = "status")
    val status: String = "draft"
)