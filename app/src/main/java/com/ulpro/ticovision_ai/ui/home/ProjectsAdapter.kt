package com.ulpro.ticovision_ai.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
import com.ulpro.ticovision_ai.databinding.ItemProjectBinding

/**
 * Adapter de proyectos para la pantalla Home.
 * Usa ListAdapter para aprovechar DiffUtil y actualizar
 * la lista de manera eficiente cuando cambian los datos.
 */
class ProjectsAdapter(
    private val onProjectClick: (ProjectEntity) -> Unit,
    private val onProjectMoreClick: (ProjectEntity) -> Unit
) : ListAdapter<ProjectEntity, ProjectsAdapter.ProjectViewHolder>(DiffCallback) {

    /**
     * Crea el ViewHolder inflando el layout del item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProjectViewHolder(binding)
    }

    /**
     * Vincula los datos del proyecto actual con la vista.
     */
    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder del item de proyecto.
     */
    inner class ProjectViewHolder(
        private val binding: ItemProjectBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Asigna la información del proyecto a las vistas del item
         * y conecta los eventos de clic.
         */
        fun bind(project: ProjectEntity) {
            binding.tvProjectTitle.text = project.title
            binding.tvProjectSubtitle.text = project.subtitle

            binding.root.setOnClickListener {
                onProjectClick(project)
            }

            binding.btnProjectMore.setOnClickListener {
                onProjectMoreClick(project)
            }
        }
    }

    /**
     * DiffUtil compara elementos para que RecyclerView
     * actualice solo los cambios necesarios.
     */
    private object DiffCallback : DiffUtil.ItemCallback<ProjectEntity>() {

        /**
         * Verifica si dos items representan el mismo proyecto.
         */
        override fun areItemsTheSame(
            oldItem: ProjectEntity,
            newItem: ProjectEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        /**
         * Verifica si el contenido completo del proyecto cambió.
         */
        override fun areContentsTheSame(
            oldItem: ProjectEntity,
            newItem: ProjectEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}