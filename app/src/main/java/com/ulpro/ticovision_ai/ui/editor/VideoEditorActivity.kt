package com.ulpro.ticovision_ai.ui.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ulpro.ticovision_ai.R
import com.ulpro.ticovision_ai.databinding.ActivityVideoEditorBinding

class VideoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEditorBinding
    private var projectId: Long = INVALID_PROJECT_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getLongExtra(EXTRA_PROJECT_ID, INVALID_PROJECT_ID)

        setupToolbar()
        setupPreviewControls()
        setupBottomTools()
        renderProjectInfo()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMore.setOnClickListener {
            showToast(R.string.msg_project_options)
        }

        binding.btnExport.setOnClickListener {
            showToast(R.string.msg_export_pending)
        }
    }

    private fun setupPreviewControls() {
        binding.btnMute.setOnClickListener {
            showToast(R.string.msg_volume_pending)
        }

        binding.btnPlayPause.setOnClickListener {
            showToast(R.string.msg_playback_pending)
        }

        binding.btnUndo.setOnClickListener {
            showToast(R.string.msg_undo_pending)
        }

        binding.btnRedo.setOnClickListener {
            showToast(R.string.msg_redo_pending)
        }

        binding.btnAddTrack.setOnClickListener {
            showToast(R.string.msg_add_track_pending)
        }
    }

    private fun setupBottomTools() {

        binding.toolAudio.setOnClickListener {
            showToast(R.string.msg_tool_audio)
        }

        binding.toolText.setOnClickListener {
            showToast(R.string.msg_tool_text)
        }

        binding.toolAnimate.setOnClickListener {
            showToast(R.string.msg_tool_animate)
        }

        binding.toolEffects.setOnClickListener {
            showToast(R.string.msg_tool_effects)
        }

        binding.toolShapes.setOnClickListener {
            showToast(R.string.msg_tool_shapes)
        }

        binding.toolPip.setOnClickListener {
            showToast(R.string.msg_tool_pip)
        }
    }

    private fun renderProjectInfo() {
        if (projectId == INVALID_PROJECT_ID) {
            binding.tvProjectTitle.text = getString(R.string.project_unidentified)
            binding.tvTimeCurrent.text = getString(R.string.time_zero)
            binding.tvTimeTotal.text = getString(R.string.time_zero)
            return
        }

        binding.tvProjectTitle.text = getString(R.string.project_with_id, projectId)
        binding.tvTimeCurrent.text = getString(R.string.time_current_sample)
        binding.tvTimeTotal.text = getString(R.string.time_total_sample)
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "extra_project_id"
        private const val INVALID_PROJECT_ID = -1L

        fun createIntent(context: Context, projectId: Long): Intent {
            return Intent(context, VideoEditorActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
        }
    }
}