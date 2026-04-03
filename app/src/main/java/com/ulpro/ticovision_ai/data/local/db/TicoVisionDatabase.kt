package com.ulpro.ticovision_ai.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ulpro.ticovision_ai.data.local.dao.ProjectDao
import com.ulpro.ticovision_ai.data.local.dao.ProjectEditDao
import com.ulpro.ticovision_ai.data.local.dao.TimelineItemDao
import com.ulpro.ticovision_ai.data.local.entity.ProjectEditEntity
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity

/**
 * Base de datos principal de la aplicación.
 */
@Database(
    entities = [
        ProjectEntity::class,
        ProjectEditEntity::class,
        TimelineItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TicoVisionDatabase : RoomDatabase() {

    /**
     * DAO de proyectos.
     */
    abstract fun projectDao(): ProjectDao

    /**
     * DAO de ediciones de proyecto.
     */
    abstract fun projectEditDao(): ProjectEditDao

    /**
     * DAO de elementos del timeline.
     */
    abstract fun timelineItemDao(): TimelineItemDao

    companion object {

        @Volatile
        private var INSTANCE: TicoVisionDatabase? = null

        /**
         * Devuelve una única instancia de la base de datos.
         * En fase de desarrollo se usa recreación destructiva para evitar
         * bloqueos por cambio de esquema mientras estabilizas el modelo.
         */
        fun getInstance(context: Context): TicoVisionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TicoVisionDatabase::class.java,
                    "tico_vision_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}