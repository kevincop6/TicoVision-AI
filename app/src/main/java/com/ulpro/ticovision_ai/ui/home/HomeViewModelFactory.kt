package com.ulpro.ticovision_ai.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ulpro.ticovision_ai.data.local.db.TicoVisionDatabase
import com.ulpro.ticovision_ai.data.repository.ProjectRepository

/**
 * Factory personalizada para construir HomeViewModel.
 */
class HomeViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            val database = TicoVisionDatabase.getInstance(context)
            val repository = ProjectRepository(
                projectDao = database.projectDao(),
                projectEditDao = database.projectEditDao(),
                timelineItemDao = database.timelineItemDao()
            )
            return HomeViewModel(repository) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}