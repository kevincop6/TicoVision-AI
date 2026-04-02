package com.ulpro.ticovision_ai.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ulpro.ticovision_ai.data.local.dao.ProjectDao
import com.ulpro.ticovision_ai.data.local.dao.ProjectEditDao
import com.ulpro.ticovision_ai.data.local.entity.ProjectEditEntity
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity

/**
 * Base de datos principal de la aplicación.
 */
@Database(
    entities = [
        ProjectEntity::class,
        ProjectEditEntity::class
    ],
    version = 1,
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

    companion object {

        @Volatile
        private var INSTANCE: TicoVisionDatabase? = null

        /**
         * Devuelve una única instancia de la base de datos.
         */
        fun getInstance(context: Context): TicoVisionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TicoVisionDatabase::class.java,
                    "tico_vision_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}