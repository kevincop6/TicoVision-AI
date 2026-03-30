package com.ulpro.ticovision_ai.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ulpro.ticovision_ai.databinding.ItemProjectBinding

class ProjectsAdapter(
    private val items: List<Project>,
    private val onMenuClick: (Project) -> Unit,
    private val onItemClick: (Project) -> Unit
) : RecyclerView.Adapter<ProjectsAdapter.ProjectViewHolder>() {

    inner class ProjectViewHolder(
        private val binding: ItemProjectBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Project) = with(binding) {
            ivProject.setImageResource(item.imageRes)
            tvProjectTitle.text = item.title
            tvProjectSubtitle.text = item.subtitle
            tvProjectMeta.text = "${item.date}    ${item.duration}    ${item.size}"

            root.setOnClickListener {
                onItemClick(item)
            }

            btnMore.setOnClickListener {
                onMenuClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}