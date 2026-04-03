package com.ulpro.ticovision_ai.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
import com.ulpro.ticovision_ai.databinding.ItemProjectBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Adapter de proyectos para la pantalla Home.
 */
class ProjectsAdapter(
    private val onProjectClick: (ProjectEntity) -> Unit,
    private val onProjectMoreClick: (View, ProjectEntity) -> Unit
) : ListAdapter<ProjectEntity, ProjectsAdapter.ProjectViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProjectViewHolder(
        private val binding: ItemProjectBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(project: ProjectEntity) {
            binding.tvProjectTitle.text = project.title
            binding.tvProjectSubtitle.text = buildSubtitle(project)

            binding.root.setOnClickListener {
                onProjectClick(project)
            }

            binding.btnProjectMore.setOnClickListener {
                onProjectMoreClick(it, project)
            }
        }

        /**
         * Construye un subtítulo amigable para mostrar fecha y duración.
         */
        private fun buildSubtitle(project: ProjectEntity): String {
            val dateText = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(Date(project.updatedAt))

            val durationText = formatDuration(project.durationMs)

            return "${project.subtitle} • $dateText • $durationText"
        }

        /**
         * Convierte milisegundos a un texto legible.
         */
        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProjectEntity>() {

        override fun areItemsTheSame(
            oldItem: ProjectEntity,
            newItem: ProjectEntity
        ): Boolean {
            return oldItem.projectId == newItem.projectId
        }

        override fun areContentsTheSame(
            oldItem: ProjectEntity,
            newItem: ProjectEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}