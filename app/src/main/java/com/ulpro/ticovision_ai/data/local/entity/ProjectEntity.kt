package com.ulpro.ticovision_ai.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad principal de proyecto.
 * Esta clase representa el proyecto base que se crea, se lista,
 * se abre en el editor, se actualiza y se elimina.
 */
@Entity(tableName = "projects")
data class ProjectEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "project_id")
    val projectId: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "subtitle")
    val subtitle: String,

    @ColumnInfo(name = "source_uri")
    val sourceUri: String? = null,

    @ColumnInfo(name = "cover_uri")
    val coverUri: String? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    @ColumnInfo(name = "status")
    val status: String = "draft",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)