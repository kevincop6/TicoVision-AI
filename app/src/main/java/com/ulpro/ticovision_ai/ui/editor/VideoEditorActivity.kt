package com.ulpro.ticovision_ai.ui.editor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ulpro.ticovision_ai.R
import com.ulpro.ticovision_ai.data.local.db.TicoVisionDatabase
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity
import com.ulpro.ticovision_ai.data.repository.ProjectRepository
import com.ulpro.ticovision_ai.databinding.ActivityVideoEditorBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.TimeUnit

class VideoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEditorBinding
    private lateinit var repository: ProjectRepository
    private lateinit var player: ExoPlayer

    private var projectId: Long = INVALID_PROJECT_ID
    private var currentTimelineItem: TimelineItemEntity? = null
    private var autoSaveJob: Job? = null
    private var playbackUiJob: Job? = null
    private var actionTagsJob: Job? = null

    private val actionMessages = LinkedList<String>()

    private val pickVideoLauncher = registerForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            handleSelectedVideo(uri)
        }
    }

    private val pickImageLauncher = registerForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            handleSelectedImage(uri)
        }
    }

    private val requestMediaPermissionsLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { result ->
            val granted = result.entries.any { it.value }

            if (granted) {
                showTemporaryAction("Permisos de medios concedidos")
            } else {
                showTemporaryAction("Permisos de medios denegados")
            }
        }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon(isPlaying)
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

        initializePlayer()
        setupToolbar()
        setupPreviewControls()
        setupBottomTools()
        loadProjectInfo()
        restoreTimelineAndPreview()
        startAutoSaveLoop()
    }

    /**
     * Inicializa el reproductor y prepara el estado visual inicial.
     */
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)
        binding.playerView.player = player
        binding.playerView.useController = false
        updatePreviewVisibility(hasMedia = false)
        updatePlayPauseIcon(isPlaying = false)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMore.setOnClickListener {
            requestMediaPermissionsIfNeeded()
        }

        binding.btnExport.setOnClickListener {
            showTemporaryAction(getString(R.string.msg_export_pending))
        }
    }

    private fun setupPreviewControls() {
        binding.btnMute.setOnClickListener {
            player.volume = if (player.volume > 0f) 0f else 1f
            showTemporaryAction(getString(R.string.msg_volume_pending))
        }

        binding.btnPlayPause.setOnClickListener {
            if (currentTimelineItem == null) {
                showTemporaryAction("No hay video cargado para reproducir")
                updatePlayPauseIcon(isPlaying = false)
                return@setOnClickListener
            }

            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }

        binding.btnUndo.setOnClickListener {
            registerEdit("undo", "Deshacer realizado")
        }

        binding.btnRedo.setOnClickListener {
            registerEdit("redo", "Rehacer realizado")
        }

        binding.btnAddTrack.setOnClickListener {
            openVideoPicker()
        }
    }

    private fun setupBottomTools() {
        binding.toolAudio.setOnClickListener {
            registerEdit("audio", "Edición de audio aplicada")
        }

        binding.toolText.setOnClickListener {
            registerEdit("text", "Texto agregado")
        }

        binding.toolAnimate.setOnClickListener {
            registerEdit("animate", "Animación agregada")
        }

        binding.toolEffects.setOnClickListener {
            registerEdit("effects", "Efecto aplicado")
        }

        binding.toolShapes.setOnClickListener {
            openImagePicker()
        }

        binding.toolPip.setOnClickListener {
            registerEdit("pip", "Modo PIP agregado")
        }
    }

    private fun openVideoPicker() {
        if (projectId == INVALID_PROJECT_ID) {
            renderInvalidProject()
            return
        }
        pickVideoLauncher.launch(arrayOf("video/*"))
    }

    private fun openImagePicker() {
        if (projectId == INVALID_PROJECT_ID) {
            renderInvalidProject()
            return
        }
        pickImageLauncher.launch(arrayOf("image/*"))
    }

    private fun requestMediaPermissionsIfNeeded() {
        val permissions = getRequiredMediaPermissions()
        if (permissions.isEmpty()) return

        val alreadyGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (alreadyGranted) {
            showTemporaryAction("Los permisos ya fueron concedidos")
            return
        }

        requestMediaPermissionsLauncher.launch(permissions)
    }

    private fun getRequiredMediaPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    /**
     * Agrega el video al timeline, lo guarda y lo deja cargado en preview sin reproducirlo automáticamente.
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
                restoreTimelineAndPreview(playLastAdded = true)
                showTemporaryAction("Video agregado al timeline")
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
     * Carga una imagen en el preview y registra la edición.
     */
    private fun handleSelectedImage(uri: Uri) {
        if (projectId == INVALID_PROJECT_ID) {
            renderInvalidProject()
            return
        }

        takePersistablePermission(uri)

        lifecycleScope.launch {
            try {
                repository.addEdit(
                    projectId = projectId,
                    editType = "add_image",
                    extraData = uri.toString()
                )

                binding.ivPreviewPlaceholder.setImageURI(uri)
                updatePreviewVisibility(hasMedia = true)
                showTemporaryAction("Imagen agregada al proyecto")
            } catch (e: Exception) {
                Toast.makeText(
                    this@VideoEditorActivity,
                    e.message ?: "No se pudo abrir la imagen.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Restaura el timeline real del proyecto y carga el elemento elegido sin autoplay.
     */
    private fun restoreTimelineAndPreview(playLastAdded: Boolean = false) {
        if (projectId == INVALID_PROJECT_ID) return

        lifecycleScope.launch {
            val items = repository.getTimelineItems(projectId)

            renderTimelineItems(items)

            val videoItems = items.filter { it.type == "video" }

            if (items.isEmpty()) {
                currentTimelineItem = null
                player.clearMediaItems()
                updatePreviewVisibility(hasMedia = false)
                updatePlayPauseIcon(isPlaying = false)
                return@launch
            }

            updatePreviewVisibility(hasMedia = true)

            if (videoItems.isEmpty()) {
                currentTimelineItem = null
                player.clearMediaItems()
                updatePlayPauseIcon(isPlaying = false)
                return@launch
            }

            val selectedItem = if (playLastAdded) videoItems.last() else videoItems.first()
            currentTimelineItem = selectedItem
            prepareTimelineItem(selectedItem)
        }
    }

    /**
     * Prepara un video en preview, pero no lo reproduce automáticamente.
     */
    private fun prepareTimelineItem(item: TimelineItemEntity) {
        val uriString = item.sourceUri ?: return
        val mediaItem = MediaItem.fromUri(Uri.parse(uriString))

        player.setMediaItem(mediaItem)
        player.prepare()
        player.seekTo(item.startTimeMs.coerceAtLeast(0L))
        player.playWhenReady = false

        updatePreviewVisibility(hasMedia = true)
        updatePlayPauseIcon(isPlaying = false)
        binding.tvTimeCurrent.text = formatDuration(item.startTimeMs)
        binding.tvTimeTotal.text = formatDuration(item.durationMs)

        startPlaybackUiSync()
    }

    /**
     * Sincroniza playhead, tiempo actual y desplazamiento horizontal del timeline.
     */
    private fun startPlaybackUiSync() {
        playbackUiJob?.cancel()
        playbackUiJob = lifecycleScope.launch {
            while (isActive) {
                val item = currentTimelineItem
                if (item != null && player.duration > 0) {
                    val position = player.currentPosition.coerceAtLeast(0L)
                    binding.tvTimeCurrent.text = formatDuration(position)
                    updatePlayhead(position, item.durationMs)
                }
                delay(50L)
            }
        }
    }

    /**
     * Mueve el playhead y desplaza el timeline según el progreso.
     */
    private fun updatePlayhead(currentMs: Long, totalMs: Long) {
        if (totalMs <= 0L) return

        val timelineWidth = binding.timelineVideoTrack.width
        if (timelineWidth <= 0) return

        val progress = currentMs.toFloat() / totalMs.toFloat()
        val clampedProgress = progress.coerceIn(0f, 1f)

        binding.timelineContainer.post {
            val usableWidth = timelineWidth - binding.playhead.width
            val targetX = (usableWidth * clampedProgress).toInt()
            binding.playhead.translationX = targetX.toFloat()

            val scrollTarget = (timelineWidth * clampedProgress).toInt() - (binding.timelineScroll.width / 2)
            binding.timelineScroll.smoothScrollTo(scrollTarget.coerceAtLeast(0), 0)
        }
    }

    /**
     * Dibuja miniaturas reales en la pista de video.
     * Reemplaza los elementos por defecto del XML.
     */
    private fun renderTimelineItems(items: List<TimelineItemEntity>) {
        binding.timelineVideoTrack.removeAllViews()

        if (items.isEmpty()) {
            return
        }

        items.forEach { item ->
            when (item.type) {
                "video" -> {
                    val thumbView = createVideoThumbnailView(item)
                    binding.timelineVideoTrack.addView(thumbView)
                }

                "image" -> {
                    val imageView = createImageThumbnailView(item)
                    binding.timelineVideoTrack.addView(imageView)
                }
            }
        }
    }

    private fun createVideoThumbnailView(item: TimelineItemEntity): View {
        val frameLayout = FrameLayout(this)
        val params = LinearLayout.LayoutParams(TIMELINE_ITEM_WIDTH_DP.dp(this), ViewGroup.LayoutParams.MATCH_PARENT)
        frameLayout.layoutParams = params
        frameLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(getVideoThumbnail(Uri.parse(item.sourceUri)))
            setOnClickListener {
                currentTimelineItem = item
                prepareTimelineItem(item)
            }
        }

        frameLayout.addView(imageView)
        return frameLayout
    }

    private fun createImageThumbnailView(item: TimelineItemEntity): View {
        return ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                TIMELINE_ITEM_WIDTH_DP.dp(this@VideoEditorActivity),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageURI(Uri.parse(item.sourceUri))
            setOnClickListener {
                binding.ivPreviewPlaceholder.setImageURI(Uri.parse(item.sourceUri))
                updatePreviewVisibility(hasMedia = true)
                player.pause()
                player.clearMediaItems()
                currentTimelineItem = null
                updatePlayPauseIcon(isPlaying = false)
                showTemporaryAction("Imagen cargada en preview")
            }
        }
    }

    private fun getVideoThumbnail(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Controla cuándo se ve el placeholder y cuándo se ve el reproductor.
     */
    private fun updatePreviewVisibility(hasMedia: Boolean) {
        if (hasMedia && currentTimelineItem != null) {
            binding.playerView.visibility = View.VISIBLE
            binding.ivPreviewPlaceholder.visibility = View.GONE
        } else if (hasMedia) {
            binding.playerView.visibility = View.GONE
            binding.ivPreviewPlaceholder.visibility = View.VISIBLE
        } else {
            binding.playerView.visibility = View.GONE
            binding.ivPreviewPlaceholder.visibility = View.VISIBLE
        }
    }

    /**
     * Muestra solo los últimos dos mensajes de acción y los oculta después.
     */
    private fun showTemporaryAction(message: String) {
        if (actionMessages.size >= 2) {
            actionMessages.removeFirst()
        }
        actionMessages.add(message)
        renderActionTags()

        actionTagsJob?.cancel()
        actionTagsJob = lifecycleScope.launch {
            binding.actionTagsContainer.visibility = View.VISIBLE
            delay(ACTION_TAGS_VISIBLE_MS)
            binding.actionTagsContainer.visibility = View.GONE
        }
    }

    private fun renderActionTags() {
        val reversed = actionMessages.takeLast(2)
        binding.tvActionTagOne.text = reversed.getOrNull(reversed.size - 1) ?: ""
        binding.tvActionTagTwo.text = reversed.getOrNull(reversed.size - 2) ?: ""

        binding.tvActionTagOne.visibility =
            if (binding.tvActionTagOne.text.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.tvActionTagTwo.visibility =
            if (binding.tvActionTagTwo.text.isNullOrBlank()) View.GONE else View.VISIBLE

        binding.actionTagsContainer.visibility =
            if (reversed.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.pause_24px else R.drawable.ic_play
        )
    }

    private fun startAutoSaveLoop() {
        autoSaveJob?.cancel()

        autoSaveJob = lifecycleScope.launch {
            while (true) {
                delay(AUTO_SAVE_INTERVAL_MS)
                val currentProject = repository.getProjectById(projectId) ?: continue
                repository.updateProject(currentProject)
            }
        }
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    private fun getVideoDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

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

    private fun registerEdit(editType: String, message: String) {
        if (projectId == INVALID_PROJECT_ID) return

        lifecycleScope.launch {
            try {
                repository.addEdit(
                    projectId = projectId,
                    editType = editType
                )
                showTemporaryAction(message)
            } catch (_: Exception) {
            }
        }
    }

    private fun renderInvalidProject() {
        binding.tvProjectTitle.text = getString(R.string.project_unidentified)
        binding.tvTimeCurrent.text = getString(R.string.time_zero)
        binding.tvTimeTotal.text = getString(R.string.time_zero)
        updatePreviewVisibility(hasMedia = false)
        updatePlayPauseIcon(isPlaying = false)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onStop() {
        super.onStop()
        player.pause()
        updatePlayPauseIcon(isPlaying = false)
    }

    override fun onDestroy() {
        autoSaveJob?.cancel()
        playbackUiJob?.cancel()
        actionTagsJob?.cancel()
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "extra_project_id"
        private const val INVALID_PROJECT_ID = -1L
        private const val AUTO_SAVE_INTERVAL_MS = 3_000L
        private const val ACTION_TAGS_VISIBLE_MS = 2_500L
        private const val TIMELINE_ITEM_WIDTH_DP = 120

        fun createIntent(context: Context, projectId: Long): Intent {
            return Intent(context, VideoEditorActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
        }
    }
}

/**
 * Convierte dp a px.
 */
private fun Int.dp(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}