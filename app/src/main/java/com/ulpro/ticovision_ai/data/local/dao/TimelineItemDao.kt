package com.ulpro.ticovision_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para manejar los elementos del timeline.
 */
@Dao
interface TimelineItemDao {

    /**
     * Observa todos los items de un proyecto ordenados por pista y tiempo de inicio.
     */
    @Query(
        """
        SELECT * FROM timeline_items
        WHERE project_owner_id = :projectId
        ORDER BY track_index ASC, start_time_ms ASC, item_id ASC
        """
    )
    fun observeItemsByProjectId(projectId: Long): Flow<List<TimelineItemEntity>>

    /**
     * Obtiene todos los items de un proyecto.
     */
    @Query(
        """
        SELECT * FROM timeline_items
        WHERE project_owner_id = :projectId
        ORDER BY track_index ASC, start_time_ms ASC, item_id ASC
        """
    )
    suspend fun getItemsByProjectId(projectId: Long): List<TimelineItemEntity>

    /**
     * Inserta un nuevo item en el timeline.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: TimelineItemEntity): Long

    /**
     * Inserta varios items.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TimelineItemEntity>)

    /**
     * Actualiza un item del timeline.
     */
    @Update
    suspend fun updateItem(item: TimelineItemEntity)

    /**
     * Elimina un item específico.
     */
    @Delete
    suspend fun deleteItem(item: TimelineItemEntity)

    /**
     * Elimina todos los items de un proyecto.
     */
    @Query("DELETE FROM timeline_items WHERE project_owner_id = :projectId")
    suspend fun deleteItemsByProjectId(projectId: Long)
}