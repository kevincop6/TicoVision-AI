package com.ulpro.ticovision_ai.ui.home

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ulpro.ticovision_ai.R
import com.ulpro.ticovision_ai.data.local.entity.ProjectEntity
import com.ulpro.ticovision_ai.databinding.FragmentHomeBinding
import com.ulpro.ticovision_ai.ui.dialog.CreateProjectDialogFragment
import com.ulpro.ticovision_ai.ui.editor.VideoEditorActivity
import kotlinx.coroutines.launch

/**
 * Fragment principal de la pantalla Home.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(requireContext().applicationContext)
    }

    private lateinit var projectsAdapter: ProjectsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        setupRecycler()
        setupClicks()
        observeProjects()
        observeErrors()
    }

    /**
     * Configura el RecyclerView.
     */
    private fun setupRecycler() {
        projectsAdapter = ProjectsAdapter(
            onProjectClick = { project ->
                openProject(project)
            },
            onProjectMoreClick = { anchorView, project ->
                showProjectOptions(anchorView, project)
            }
        )

        binding.recyclerProjects.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = projectsAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * Configura eventos de clic.
     */
    private fun setupClicks() {
        binding.btnCreateProject.setOnClickListener {
            CreateProjectDialogFragment().show(
                childFragmentManager,
                CreateProjectDialogFragment.TAG
            )
        }
    }

    /**
     * Observa la lista de proyectos.
     */
    private fun observeProjects() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.projects.collect { projectList ->
                    projectsAdapter.submitList(projectList)
                }
            }
        }
    }

    /**
     * Observa errores para mostrarlos en pantalla.
     */
    private fun observeErrors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorMessage.collect { message ->
                    if (!message.isNullOrBlank()) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    /**
     * Abre un proyecto existente.
     */
    private fun openProject(project: ProjectEntity) {
        startActivity(
            VideoEditorActivity.createIntent(
                requireContext(),
                project.projectId
            )
        )
    }

    /**
     * Muestra opciones adicionales del proyecto.
     */
    private fun showProjectOptions(anchorView: View, project: ProjectEntity) {
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.menu.add(0, 1, 0, "Eliminar proyecto")
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    viewModel.deleteProject(project.projectId)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}