package com.ulpro.ticovision_ai.ui.home

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ulpro.ticovision_ai.R
import com.ulpro.ticovision_ai.databinding.FragmentHomeBinding

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var projectsAdapter: ProjectsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentHomeBinding.bind(view)

        setupInsets()
        setupQuickActions()
        setupProjectsList()
        setupBottomNavigation()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.headerContainer.setPadding(
                binding.headerContainer.paddingLeft,
                systemBars.top + binding.headerContainer.paddingTop,
                binding.headerContainer.paddingRight,
                binding.headerContainer.paddingBottom
            )

            binding.bottomNavigation.setPadding(
                binding.bottomNavigation.paddingLeft,
                binding.bottomNavigation.paddingTop,
                binding.bottomNavigation.paddingRight,
                systemBars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_extra_padding)
            )

            insets
        }
    }

    private fun setupQuickActions() {
        binding.cardAiEditor.setOnClickListener {
            Toast.makeText(requireContext(), "AI editor", Toast.LENGTH_SHORT).show()
        }

        binding.cardPhotoEditor.setOnClickListener {
            Toast.makeText(requireContext(), "Photo editor", Toast.LENGTH_SHORT).show()
        }

        binding.cardVideoEditor.setOnClickListener {
            Toast.makeText(requireContext(), "Video editor", Toast.LENGTH_SHORT).show()
        }

        binding.cardMoreOptions.setOnClickListener {
            Toast.makeText(requireContext(), "More options", Toast.LENGTH_SHORT).show()
        }

        binding.btnPremium.setOnClickListener {
            Toast.makeText(requireContext(), "Get premium", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Settings", Toast.LENGTH_SHORT).show()
        }

        binding.btnCreateProject.setOnClickListener {
            Toast.makeText(requireContext(), "Nuevo proyecto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupProjectsList() {
        val projects = listOf(
            Project(
                title = "Project 981",
                subtitle = "Mistery template",
                date = "12 Jan 25",
                duration = "00:01:12",
                size = "44MB",
                imageRes = R.drawable.default_project_1
            ),
            Project(
                title = "My daily lifestyle",
                subtitle = "Car star",
                date = "19 Dec 24",
                duration = "00:58:45",
                size = "37MB",
                imageRes = R.drawable.default_project_1
            ),
            Project(
                title = "Friday_01",
                subtitle = "Morning Coffee Vibe",
                date = "13 Dec 24",
                duration = "00:01:41",
                size = "52MB",
                imageRes = R.drawable.default_project_1
            ),
            Project(
                title = "My day",
                subtitle = "Urban travel reel",
                date = "03 Dec 24",
                duration = "00:00:58",
                size = "18MB",
                imageRes = R.drawable.default_project_1
            )
        )

        projectsAdapter = ProjectsAdapter(
            items = projects,
            onMenuClick = { project ->
                Toast.makeText(requireContext(), "Opciones: ${project.title}", Toast.LENGTH_SHORT).show()
            },
            onItemClick = { project ->
                Toast.makeText(requireContext(), "Abrir: ${project.title}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerProjects.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = projectsAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> true
                R.id.menu_media -> {
                    Toast.makeText(requireContext(), "Media", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_files -> {
                    Toast.makeText(requireContext(), "Files", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_profile -> {
                    Toast.makeText(requireContext(), "Profile", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        binding.bottomNavigation.selectedItemId = R.id.menu_home
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}