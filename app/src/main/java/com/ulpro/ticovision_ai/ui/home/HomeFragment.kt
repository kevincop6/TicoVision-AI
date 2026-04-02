package com.ulpro.ticovision_ai.ui.home

import android.os.Bundle
import android.view.View
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
 * Se encarga de inicializar la lista de proyectos,
 * escuchar cambios del ViewModel y abrir el diálogo de creación.
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
    }

    /**
     * Configura el RecyclerView con un LinearLayoutManager vertical
     * y conecta el adapter que renderiza la lista de proyectos.
     */
    private fun setupRecycler() {
        projectsAdapter = ProjectsAdapter(
            onProjectClick = { project ->
                openProject(project)
            },
            onProjectMoreClick = { project ->
                showProjectOptions(project)
            }
        )

        binding.recyclerProjects.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = projectsAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * Configura los eventos de clic de la pantalla.
     * El botón principal abre el diálogo para crear un proyecto nuevo.
     */
    private fun setupClicks() {
        binding.btnCreateProject.setOnClickListener {
            CreateProjectDialogFragment().show(
                parentFragmentManager,
                CreateProjectDialogFragment.TAG
            )
        }
    }

    /**
     * Observa la lista de proyectos usando el ciclo de vida de la vista.
     * Esto evita fugas de memoria y actualiza la UI solo cuando la vista está activa.
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
     * Abre un proyecto existente.
     * Más adelante aquí navegarás a la pantalla del editor.
     */
    private fun openProject(project: ProjectEntity) {
        startActivity(
            VideoEditorActivity.createIntent(
                requireContext(),
                project.id
            )
        )
    }
    /**
     * Muestra opciones adicionales de un proyecto.
     * Más adelante aquí podrás agregar acciones como renombrar o eliminar.
     */
    private fun showProjectOptions(project: ProjectEntity) {
        // TODO: Mostrar menú de opciones del proyecto
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}