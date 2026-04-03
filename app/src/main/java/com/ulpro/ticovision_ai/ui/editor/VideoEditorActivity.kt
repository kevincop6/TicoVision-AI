package com.ulpro.ticovision_ai.ui.editor

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ulpro.ticovision_ai.R
import com.ulpro.ticovision_ai.data.local.db.TicoVisionDatabase
import com.ulpro.ticovision_ai.data.repository.ProjectRepository
import com.ulpro.ticovision_ai.databinding.ActivityVideoEditorBinding
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class VideoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEditorBinding
    private lateinit var repository: ProjectRepository

    private var projectId: Long = INVALID_PROJECT_ID

    /**
     * Selector de documentos para escoger videos desde el almacenamiento
     * usando el sistema moderno de Android.
     */
    private val pickVideoLauncher = registerForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            handleSelectedVideo(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = TicoVisionDatabase.getInstance(applicationContext)
        repository = ProjectRepository(
            projectDao = database.projectDao(),
            projectEditDao = database.projectEditDao(),
            timelineItemDao = database.timelineItemDao()
        )

        projectId = intent.getLongExtra(EXTRA_PROJECT_ID, INVALID_PROJECT_ID)

        setupToolbar()
        setupPreviewControls()
        setupBottomTools()
        loadProjectInfo()
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
            registerEdit("undo")
            showToast(R.string.msg_undo_pending)
        }

        binding.btnRedo.setOnClickListener {
            registerEdit("redo")
            showToast(R.string.msg_redo_pending)
        }

        /**
         * Ahora este botón abre el selector para agregar un video al timeline.
         */
        binding.btnAddTrack.setOnClickListener {
            openVideoPicker()
        }
    }

    private fun setupBottomTools() {
        binding.toolAudio.setOnClickListener {
            registerEdit("audio")
            showToast(R.string.msg_tool_audio)
        }

        binding.toolText.setOnClickListener {
            registerEdit("text")
            showToast(R.string.msg_tool_text)
        }

        binding.toolAnimate.setOnClickListener {
            registerEdit("animate")
            showToast(R.string.msg_tool_animate)
        }

        binding.toolEffects.setOnClickListener {
            registerEdit("effects")
            showToast(R.string.msg_tool_effects)
        }

        binding.toolShapes.setOnClickListener {
            registerEdit("shapes")
            showToast(R.string.msg_tool_shapes)
        }

        binding.toolPip.setOnClickListener {
            registerEdit("pip")
            showToast(R.string.msg_tool_pip)
        }
    }

    /**
     * Abre el selector de videos del sistema.
     */
    private fun openVideoPicker() {
        if (projectId == INVALID_PROJECT_ID) {
            renderInvalidProject()
            return
        }

        pickVideoLauncher.launch(arrayOf("video/*"))
    }

    /**
     * Maneja el video seleccionado y lo agrega al timeline.
     */
    private fun handleSelectedVideo(uri: Uri) {
        if (projectId == INVALID_PROJECT_ID) {
            renderInvalidProject()
            return
        }

        takePersistablePermission(uri)

        lifecycleScope.launch {
            try {
                val durationMs = getVideoDuration(uri)

                repository.addVideoToTimeline(
                    projectId = projectId,
                    sourceUri = uri.toString(),
                    durationMs = durationMs,
                    thumbnailUri = null
                )

                repository.addEdit(
                    projectId = projectId,
                    editType = "add_video",
                    startMs = 0L,
                    endMs = durationMs,
                    extraData = uri.toString()
                )

                loadProjectInfo()

                Toast.makeText(
                    this@VideoEditorActivity,
                    "Video agregado al timeline correctamente.",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@VideoEditorActivity,
                    e.message ?: "No se pudo agregar el video al timeline.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Toma permiso persistente de lectura para poder reusar el Uri
     * aunque el proyecto se vuelva a abrir más adelante.
     */
    private fun takePersistablePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Algunos proveedores no permiten permisos persistentes.
        }
    }

    /**
     * Obtiene la duración real del video en milisegundos.
     */
    private fun getVideoDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(this, uri)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )
            duration?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    /**
     * Carga la información real del proyecto desde la base de datos.
     */
    private fun loadProjectInfo() {
        if (projectId == INVALID_PROJECT_ID) {
            renderInvalidProject()
            return
        }

        lifecycleScope.launch {
            val project = repository.getProjectById(projectId)

            if (project == null) {
                renderInvalidProject()
                return@launch
            }

            binding.tvProjectTitle.text = project.title
            binding.tvTimeCurrent.text = getString(R.string.time_zero)
            binding.tvTimeTotal.text = formatDuration(project.durationMs)
        }
    }

    /**
     * Registra una edición simple para dejar trazabilidad del uso del editor.
     */
    private fun registerEdit(editType: String) {
        if (projectId == INVALID_PROJECT_ID) return

        lifecycleScope.launch {
            try {
                repository.addEdit(
                    projectId = projectId,
                    editType = editType
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun renderInvalidProject() {
        binding.tvProjectTitle.text = getString(R.string.project_unidentified)
        binding.tvTimeCurrent.text = getString(R.string.time_zero)
        binding.tvTimeTotal.text = getString(R.string.time_zero)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
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