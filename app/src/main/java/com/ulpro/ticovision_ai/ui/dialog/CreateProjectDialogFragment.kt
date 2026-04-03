package com.ulpro.ticovision_ai.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ulpro.ticovision_ai.R
import com.ulpro.ticovision_ai.databinding.DialogCreateProjectBinding
import com.ulpro.ticovision_ai.ui.home.HomeViewModel
import com.ulpro.ticovision_ai.ui.home.HomeViewModelFactory

class CreateProjectDialogFragment : DialogFragment() {

    private var _binding: DialogCreateProjectBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels {
        HomeViewModelFactory(requireContext().applicationContext)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCreateProjectBinding.inflate(LayoutInflater.from(requireContext()))

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.CustomCreateProjectDialog
        )
            .setView(binding.root)
            .create()

        dialog.setCanceledOnTouchOutside(true)
        setupListeners()

        return dialog
    }

    private fun setupListeners() {
        binding.btnCancelProject.setOnClickListener {
            dismiss()
        }

        binding.btnConfirmCreateProject.setOnClickListener {
            createProject()
        }
    }

    private fun createProject() {
        val title = binding.etProjectTitle.text?.toString()?.trim().orEmpty()
        val subtitle = binding.etProjectSubtitle.text?.toString()?.trim().orEmpty()

        var isValid = true

        if (title.isBlank()) {
            binding.tilProjectTitle.error = getString(R.string.error_project_title_required)
            isValid = false
        } else {
            binding.tilProjectTitle.error = null
        }

        if (subtitle.isBlank()) {
            binding.tilProjectSubtitle.error = getString(R.string.error_project_subtitle_required)
            isValid = false
        } else {
            binding.tilProjectSubtitle.error = null
        }

        if (!isValid) return

        viewModel.createProject(
            title = title,
            subtitle = subtitle
        )

        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateProjectDialogFragment"
    }
}