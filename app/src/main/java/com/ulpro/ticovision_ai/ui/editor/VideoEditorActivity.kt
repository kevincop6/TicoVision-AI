package com.ulpro.ticovision_ai.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ulpro.ticovision_ai.R
import com.ulpro.ticovision_ai.data.local.db.TicoVisionDatabase
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity
import com.ulpro.ticovision_ai.data.repository.ProjectRepository
import com.ulpro.ticovision_ai.databinding.ActivityVideoEditorBinding
import com.ulpro.ticovision_ai.ui.editor.controller.EditorActionTagController
import com.ulpro.ticovision_ai.ui.editor.controller.EditorAutoSaveController
import com.ulpro.ticovision_ai.ui.editor.controller.EditorMediaPickerManager
import com.ulpro.ticovision_ai.ui.editor.controller.EditorPermissionManager
import com.ulpro.ticovision_ai.ui.editor.controller.EditorPlayerController
import com.ulpro.ticovision_ai.ui.editor.logging.EditorErrorReportManager
import com.ulpro.ticovision_ai.ui.editor.timeline.TimelineRenderer
import com.ulpro.ticovision_ai.ui.editor.timeline.TimelineSeekHelper
import com.ulpro.ticovision_ai.ui.editor.timeline.TimelineThumbnailProvider
import com.ulpro.ticovision_ai.ui.editor.util.formatDuration
import com.ulpro.ticovision_ai.ui.editor.util.takeReadUriPermissionSafely
import com.ulpro.ticovision_ai.ui.editor.util.timelineKey
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
/**
 * Pantalla principal del editor de video.
 *
 * Adaptada al nuevo EditorPlayerController y al nuevo TimelineThumbnailProvider.
 */
class VideoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEditorBinding
    private lateinit var repository: ProjectRepository

    private lateinit var permissionManager: EditorPermissionManager
    private lateinit var actionTagController: EditorActionTagController
    private lateinit var autoSaveController: EditorAutoSaveController
    private lateinit var mediaPickerManager: EditorMediaPickerManager
    private lateinit var thumbnailProvider: TimelineThumbnailProvider
    private lateinit var timelineRenderer: TimelineRenderer
    private lateinit var playerController: EditorPlayerController
    private lateinit var errorReportManager: EditorErrorReportManager

    private var projectId: Long = VideoEditorConfig.INVALID_PROJECT_ID
    private var timelineItems: List<TimelineItemEntity> = emptyList()
    private var visualTimelineItems: List<TimelineItemEntity> = emptyList()
    private var currentTimelineItem: TimelineItemEntity? = null
    private var currentPlaylistIndex: Int = 0
    private var totalTimelineDurationMs: Long = 0L
    private var isDraggingPlayhead: Boolean = false
    private var isTimelineScrollSeeking: Boolean = false
    private var isProgrammaticTimelineScroll: Boolean = false
    private var pendingScrollSeekPositionMs: Long = 0L
    private var isUserScrollingTimeline: Boolean = false
    private var lastTimelineScrollX: Int = -1
    private var lastSyncedPreviewItemKey: String? = null
    private val requestMediaPermissionsLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { result ->
            val granted = result.entries.any { it.value }
            if (granted) {
                actionTagController.showTemporaryAction("Permisos de medios concedidos")
            } else {
                actionTagController.showTemporaryAction("Permisos de medios denegados")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeDependencies()

        projectId = intent.getLongExtra(
            VideoEditorConfig.EXTRA_PROJECT_ID,
            VideoEditorConfig.INVALID_PROJECT_ID
        )

        initializeControllers()
        setupToolbar()
        setupPreviewControls()
        setupBottomTools()
        setupTimelineInteractions()

        loadProjectInfo()
        restoreTimelineAndPreview()
        startAutoSaveIfPossible()
    }

    /**
     * Inicializa base de datos y repositorio.
     */
    private fun initializeDependencies() {
        val database = TicoVisionDatabase.getInstance(applicationContext)
        repository = ProjectRepository(
            projectDao = database.projectDao(),
            projectEditDao = database.projectEditDao(),
            timelineItemDao = database.timelineItemDao()
        )
        errorReportManager = EditorErrorReportManager(this)
    }

    /**
     * Inicializa controladores del editor.
     */
    private fun initializeControllers() {
        permissionManager = EditorPermissionManager(this)
        actionTagController = EditorActionTagController(binding, lifecycleScope)
        autoSaveController = EditorAutoSaveController(repository, lifecycleScope)

        mediaPickerManager = EditorMediaPickerManager(
            activity = this,
            onImageSelected = ::handleSelectedImage,
            onVideoSelected = ::handleSelectedVideo,
            onOperationCancelled = {
                actionTagController.showTemporaryAction("Operación cancelada")
            }
        )

        thumbnailProvider = TimelineThumbnailProvider(
            context = this
        )

        timelineRenderer = TimelineRenderer(
            context = this,
            binding = binding,
            lifecycleScope = lifecycleScope,
            thumbnailProvider = thumbnailProvider,
            onVideoItemSelected = { item, index ->
                currentTimelineItem = item
                currentPlaylistIndex = index.coerceAtLeast(0)

                playerController.prepareTimelinePlaylist(
                    timelineItems = visualTimelineItems,
                    startIndex = currentPlaylistIndex,
                    startPositionMs = 0L,
                    autoPlay = false,
                    isVideoMuted = playerController.isMuted(),
                    onTimelineItemChanged = { changedItem, playableIndex ->
                        currentTimelineItem = changedItem
                        currentPlaylistIndex = visualTimelineItems.indexOfFirst {
                            it.timelineKey() == changedItem.timelineKey()
                        }.coerceAtLeast(playableIndex.coerceAtLeast(0))
                        syncPreviewWithCurrentItem()
                        renderTimelineSelection()
                    },
                    onUiSyncRequested = {
                        syncPreviewWithCurrentItem()
                        updateTimeTextsFromPlayer()
                        updatePlayheadFromPlayer()
                        renderTimelineSelection()
                    }
                )

                syncPreviewWithCurrentItem()
                renderTimelineSelection()
                actionTagController.showTemporaryAction("Clip seleccionado")
            },
            onImageItemSelected = { item, index ->
                currentTimelineItem = item
                currentPlaylistIndex = index.coerceAtLeast(0)

                playerController.prepareTimelinePlaylist(
                    timelineItems = visualTimelineItems,
                    startIndex = currentPlaylistIndex,
                    startPositionMs = 0L,
                    autoPlay = false,
                    isVideoMuted = playerController.isMuted(),
                    onTimelineItemChanged = { changedItem, playableIndex ->
                        currentTimelineItem = changedItem
                        currentPlaylistIndex = visualTimelineItems.indexOfFirst {
                            it.timelineKey() == changedItem.timelineKey()
                        }.coerceAtLeast(playableIndex.coerceAtLeast(0))
                        syncPreviewWithCurrentItem()
                        renderTimelineSelection()
                    },
                    onUiSyncRequested = {
                        syncPreviewWithCurrentItem()
                        updateTimeTextsFromPlayer()
                        updatePlayheadFromPlayer()
                        renderTimelineSelection()
                    }
                )

                syncPreviewWithCurrentItem()
                renderTimelineSelection()
                actionTagController.showTemporaryAction("Imagen seleccionada")
            }
        )

        playerController = EditorPlayerController(
            context = this,
            binding = binding,
            lifecycleScope = lifecycleScope,
            onIsPlayingChanged = { isPlaying ->
                updatePlayPauseIcon(isPlaying)
            },
            onMediaItemTransition = { newIndex ->
                val playableItems = playerController.getCurrentPlayableTimelineItems()

                if (newIndex in playableItems.indices) {
                    val newItem = playableItems[newIndex]
                    currentTimelineItem = newItem
                    currentPlaylistIndex = visualTimelineItems.indexOfFirst {
                        it.timelineKey() == newItem.timelineKey()
                    }.coerceAtLeast(0)
                }

                syncPreviewWithCurrentItem()
                renderTimelineSelection()
                updateTimeTextsFromPlayer()
                updatePlayheadFromPlayer()
            },
            onPlaybackEnded = {
                updatePlayPauseIcon(false)
                syncPreviewWithCurrentItem()
                updateTimeTextsFromPlayer()
                updatePlayhead(
                    totalTimelineDurationMs,
                    totalTimelineDurationMs.coerceAtLeast(1L)
                )
            },
            onPositionRelevantEvent = {
                syncPreviewWithCurrentItem()
                updateTimeTextsFromPlayer()
                updatePlayheadFromPlayer()
                renderTimelineSelection()
            },
            onPlayerError = { throwable ->
                saveDebugReport(
                    tag = "player_error",
                    message = "Error durante reproducción de contenido",
                    throwable = throwable,
                    extraData = mapOf(
                        "projectId" to projectId.toString(),
                        "currentTimelineItemType" to (currentTimelineItem?.type ?: "null"),
                        "currentTimelineItemUri" to (currentTimelineItem?.sourceUri ?: "null"),
                        "visualTimelineItems" to visualTimelineItems.size.toString()
                    )
                )

                updatePlayPauseIcon(false)

                Toast.makeText(
                    this,
                    "Error reproduciendo contenido. Se generó un reporte.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    /**
     * Configura la barra superior.
     */
    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMore.setOnClickListener {
            requestMediaPermissionsIfNeeded()
        }

        binding.btnExport.setOnClickListener {
            actionTagController.showTemporaryAction(getString(R.string.msg_export_pending))
        }
    }

    /**
     * Configura controles principales de preview.
     */
    private fun setupPreviewControls() {
        binding.btnMute.setOnClickListener {
            val muted = playerController.toggleMute()
            updateMuteIcon(muted)

            val message = if (muted) {
                "Audio del video silenciado"
            } else {
                "Audio del video activado"
            }

            actionTagController.showTemporaryAction(message)
        }

        binding.btnPlayPause.setOnClickListener {
            if (visualTimelineItems.isEmpty()) {
                actionTagController.showTemporaryAction("No hay clips cargados para reproducir")
                updatePlayPauseIcon(false)
                return@setOnClickListener
            }

            if (playerController.mediaItemCount() == 0) {
                val safeIndex = currentPlaylistIndex.coerceIn(
                    0,
                    visualTimelineItems.lastIndex.coerceAtLeast(0)
                )

                playerController.prepareTimelinePlaylist(
                    timelineItems = visualTimelineItems,
                    startIndex = safeIndex,
                    startPositionMs = 0L,
                    autoPlay = true,
                    isVideoMuted = playerController.isMuted(),
                    onTimelineItemChanged = { changedItem, playableIndex ->
                        currentTimelineItem = changedItem
                        currentPlaylistIndex = visualTimelineItems.indexOfFirst {
                            it.timelineKey() == changedItem.timelineKey()
                        }.coerceAtLeast(playableIndex.coerceAtLeast(0))
                        syncPreviewWithCurrentItem()
                        renderTimelineSelection()
                    },
                    onUiSyncRequested = {
                        syncPreviewWithCurrentItem()
                        updateTimeTextsFromPlayer()
                        updatePlayheadFromPlayer()
                    }
                )
                return@setOnClickListener
            }

            playerController.togglePlayPause()
        }

        binding.btnUndo.setOnClickListener {
            registerEdit("undo", "Deshacer realizado")
        }

        binding.btnRedo.setOnClickListener {
            registerEdit("redo", "Rehacer realizado")
        }

        binding.btnAddTrack.setOnClickListener {
            if (projectId == VideoEditorConfig.INVALID_PROJECT_ID) {
                renderInvalidProject()
                return@setOnClickListener
            }
            showAddMediaOptionsDialog()
        }
    }

    /**
     * Configura herramientas inferiores.
     */
    private fun setupBottomTools() {
        binding.toolAudio.setOnClickListener {
            actionTagController.showTemporaryAction(
                "La carga de audio externo se conectará en la siguiente fase"
            )
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
            if (projectId == VideoEditorConfig.INVALID_PROJECT_ID) {
                renderInvalidProject()
                return@setOnClickListener
            }
            showAddMediaOptionsDialog()
        }

        binding.toolPip.setOnClickListener {
            registerEdit("pip", "Modo PIP agregado")
        }
    }
    /**
     * Muestra un diálogo simple para agregar medios visuales al editor.
     *
     * Opciones:
     * - Galería: permite escoger imagen o video desde el selector visual del sistema.
     * - Tomar foto: abre la cámara para capturar una imagen.
     * - Grabar video: abre la cámara para capturar un video.
     *
     * Se solicita permiso preventivo solo si todavía hace falta según la versión del sistema.
     */
    private fun showAddMediaOptionsDialog() {
        requestMediaPermissionsIfNeeded()

        val options = arrayOf(
            "Galería",
            "Tomar foto",
            "Grabar video"
        )

        AlertDialog.Builder(this)
            .setTitle("Agregar archivo")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        mediaPickerManager.openMediaPicker()
                        actionTagController.showTemporaryAction(
                            "Seleccioná una imagen o un video desde la galería"
                        )
                    }

                    1 -> {
                        mediaPickerManager.capturePhoto()
                        actionTagController.showTemporaryAction(
                            "Abriendo cámara para tomar foto"
                        )
                    }

                    2 -> {
                        mediaPickerManager.captureVideo()
                        actionTagController.showTemporaryAction(
                            "Abriendo cámara para grabar video"
                        )
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    /**
     * Configura interacción del timeline con scroll liviano y seek diferido.
     *
     * Reglas:
     * - Mientras se hace scroll, no se actualiza ExoPlayer.
     * - Al terminar el scroll, se hace seek real.
     * - Arrastrar la guía manualmente sigue permitiendo seek al soltar.
     */
    private fun setupTimelineInteractions() {
        binding.timelineScroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDraggingPlayhead = false
                    isUserScrollingTimeline = true
                    lastTimelineScrollX = -1
                }

                MotionEvent.ACTION_MOVE -> {
                    updateTimelineUiFromScrollPreview()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    updateTimelineUiFromScrollPreview()
                    commitTimelineScrollSeek()
                    isUserScrollingTimeline = false
                    isDraggingPlayhead = false
                    lastTimelineScrollX = -1
                }
            }
            false
        }

        val dragListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDraggingPlayhead = true
                    isUserScrollingTimeline = false
                    playerController.pause()
                    updatePlayPauseIcon(false)
                    binding.timelineScroll.parent?.requestDisallowInterceptTouchEvent(true)

                    val contentX = TimelineSeekHelper.getTimelineTouchXInsideContent(
                        timelineContent = binding.timelineContent,
                        timelineScrollX = binding.timelineScroll.scrollX,
                        rawX = event.rawX
                    )

                    movePlayheadFromTouch(contentX, false)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val contentX = TimelineSeekHelper.getTimelineTouchXInsideContent(
                        timelineContent = binding.timelineContent,
                        timelineScrollX = binding.timelineScroll.scrollX,
                        rawX = event.rawX
                    )

                    movePlayheadFromTouch(contentX, false)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val contentX = TimelineSeekHelper.getTimelineTouchXInsideContent(
                        timelineContent = binding.timelineContent,
                        timelineScrollX = binding.timelineScroll.scrollX,
                        rawX = event.rawX
                    )

                    movePlayheadFromTouch(contentX, true)
                    isDraggingPlayhead = false
                    isUserScrollingTimeline = false
                    binding.timelineScroll.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                else -> false
            }
        }

        binding.playhead.setOnTouchListener(dragListener)
        binding.playheadHandle.setOnTouchListener(dragListener)
    }
    /**
     * Sincroniza el seek del reproductor usando la posición visible del timeline.
     *
     * La referencia es la guía fija a la izquierda. Por eso la posición activa
     * se calcula a partir del scroll horizontal visible y no de una guía móvil.
     *
     * Importante:
     * - No dispara reproducción automática.
     * - Solo actualiza posición, selección y preview.
     */
    private fun syncSeekFromTimelineScroll() {
        if (visualTimelineItems.isEmpty()) return
        if (playerController.mediaItemCount() == 0) return

        val contentWidth = binding.timelineContent.width.coerceAtLeast(
            binding.timelineVideoTrack.width
        )
        if (contentWidth <= 0) return

        val totalDuration = totalTimelineDurationMs.coerceAtLeast(1L)

        val globalPositionMs = TimelineSeekHelper.calculateGlobalPositionFromScroll(
            timelineScrollX = binding.timelineScroll.scrollX,
            viewportWidth = binding.timelineScroll.width,
            contentWidth = contentWidth,
            totalDurationMs = totalDuration,
            fixedGuideX = 48f
        )

        binding.tvTimeCurrent.text = formatDuration(globalPositionMs)
        updatePlayhead(globalPositionMs, totalDuration)

        isTimelineScrollSeeking = true

        playerController.seekToGlobalPosition(
            globalPositionMs = globalPositionMs,
            onTimelineItemChanged = { changedItem, playableIndex ->
                currentTimelineItem = changedItem
                currentPlaylistIndex = visualTimelineItems.indexOfFirst {
                    it.timelineKey() == changedItem.timelineKey()
                }.coerceAtLeast(playableIndex.coerceAtLeast(0))

                syncPreviewWithCurrentItem()
            },
            onUiSyncRequested = {
                updateTimeTextsFromPlayer()
                updatePlayheadFromPlayer()
                renderTimelineSelection()
                isTimelineScrollSeeking = false
            }
        )
    }
    /**
     * Solicita permisos si todavía no han sido concedidos.
     */
    private fun requestMediaPermissionsIfNeeded() {
        val permissions = permissionManager.getRequiredMediaPermissions()
        if (permissions.isEmpty()) return

        if (permissionManager.areMediaPermissionsGranted()) {
            actionTagController.showTemporaryAction("Los permisos ya fueron concedidos")
            return
        }

        requestMediaPermissionsLauncher.launch(permissions)
    }

    /**
     * Procesa la selección de un video y lo agrega al timeline.
     */
    private fun handleSelectedVideo(uri: Uri) {
        if (projectId == VideoEditorConfig.INVALID_PROJECT_ID) {
            renderInvalidProject()
            return
        }

        contentResolver.takeReadUriPermissionSafely(uri)

        lifecycleScope.launch {
            try {
                val durationMs = thumbnailProvider.getVideoDuration(uri)

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
                actionTagController.showTemporaryAction("Video agregado al timeline")
            } catch (e: Exception) {
                saveDebugReport(
                    tag = "add_video_error",
                    message = "Error agregando video al timeline",
                    throwable = e,
                    extraData = mapOf(
                        "projectId" to projectId.toString(),
                        "uri" to uri.toString()
                    )
                )
                Toast.makeText(
                    this@VideoEditorActivity,
                    e.message ?: "No se pudo agregar el video al timeline.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Procesa la selección de una imagen y la agrega como clip real al timeline.
     */
    private fun handleSelectedImage(uri: Uri) {
        if (projectId == VideoEditorConfig.INVALID_PROJECT_ID) {
            renderInvalidProject()
            return
        }

        contentResolver.takeReadUriPermissionSafely(uri)

        lifecycleScope.launch {
            try {
                repository.addImageToTimeline(
                    projectId = projectId,
                    sourceUri = uri.toString(),
                    durationMs = VideoEditorConfig.DEFAULT_IMAGE_DURATION_MS,
                    thumbnailUri = uri.toString()
                )

                repository.addEdit(
                    projectId = projectId,
                    editType = "add_image",
                    startMs = 0L,
                    endMs = VideoEditorConfig.DEFAULT_IMAGE_DURATION_MS,
                    extraData = uri.toString()
                )

                loadProjectInfo()
                restoreTimelineAndPreview(playLastAdded = true)
                actionTagController.showTemporaryAction("Imagen agregada al timeline")
            } catch (e: Exception) {
                saveDebugReport(
                    tag = "add_image_error",
                    message = "Error agregando imagen al timeline",
                    throwable = e,
                    extraData = mapOf(
                        "projectId" to projectId.toString(),
                        "uri" to uri.toString()
                    )
                )
                Toast.makeText(
                    this@VideoEditorActivity,
                    e.message ?: "No se pudo agregar la imagen al timeline.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Restaura el timeline desde base de datos y sincroniza preview y player.
     */
    private fun restoreTimelineAndPreview(playLastAdded: Boolean = false) {
        if (projectId == VideoEditorConfig.INVALID_PROJECT_ID) return

        lifecycleScope.launch {
            try {
                timelineItems = repository.getTimelineItems(projectId)
                visualTimelineItems = timelineItems.filter { it.type == "video" || it.type == "image" }
                totalTimelineDurationMs = visualTimelineItems.sumOf { it.durationMs.coerceAtLeast(0L) }

                if (timelineItems.isEmpty()) {
                    currentTimelineItem = null
                    currentPlaylistIndex = 0
                    totalTimelineDurationMs = 0L

                    playerController.stopAndClear()
                    binding.ivPreviewPlaceholder.setImageDrawable(null)
                    updatePreviewVisibility(hasMedia = false)
                    updatePlayPauseIcon(false)
                    binding.tvTimeCurrent.text = formatDuration(0L)
                    binding.tvTimeTotal.text = formatDuration(0L)
                    updatePlayhead(0L, 1L)
                    timelineRenderer.renderTimelineItems(
                        items = timelineItems,
                        currentTimelineItem = null,
                        currentPlaylistIndex = 0
                    )
                    return@launch
                }

                if (visualTimelineItems.isEmpty()) {
                    currentTimelineItem = null
                    currentPlaylistIndex = 0
                    playerController.stopAndClear()
                    binding.ivPreviewPlaceholder.setImageDrawable(null)
                    updatePreviewVisibility(hasMedia = false)
                    updatePlayPauseIcon(false)
                    binding.tvTimeCurrent.text = formatDuration(0L)
                    binding.tvTimeTotal.text = formatDuration(0L)
                    updatePlayhead(0L, 1L)
                    timelineRenderer.renderTimelineItems(
                        items = timelineItems,
                        currentTimelineItem = null,
                        currentPlaylistIndex = 0
                    )
                    return@launch
                }

                val selectedIndex = if (playLastAdded) {
                    visualTimelineItems.lastIndex
                } else {
                    currentPlaylistIndex.coerceIn(0, visualTimelineItems.lastIndex)
                }

                currentPlaylistIndex = selectedIndex
                currentTimelineItem = visualTimelineItems[selectedIndex]

                playerController.prepareTimelinePlaylist(
                    timelineItems = visualTimelineItems,
                    startIndex = currentPlaylistIndex,
                    startPositionMs = 0L,
                    autoPlay = false,
                    isVideoMuted = playerController.isMuted(),
                    onTimelineItemChanged = { changedItem, playableIndex ->
                        currentTimelineItem = changedItem
                        currentPlaylistIndex = visualTimelineItems.indexOfFirst {
                            it.timelineKey() == changedItem.timelineKey()
                        }.coerceAtLeast(playableIndex.coerceAtLeast(0))
                        syncPreviewWithCurrentItem()
                        renderTimelineSelection()
                    },
                    onUiSyncRequested = {
                        syncPreviewWithCurrentItem()
                        updateTimeTextsFromPlayer()
                        updatePlayheadFromPlayer()
                        renderTimelineSelection()
                    }
                )
            } catch (e: Exception) {
                saveDebugReport(
                    tag = "restore_timeline_error",
                    message = "Error restaurando timeline y preview",
                    throwable = e,
                    extraData = mapOf("projectId" to projectId.toString())
                )
            }
        }
    }

    /**
     * Sincroniza el preview con el item actual.
     */
    private fun syncPreviewWithCurrentItem(force: Boolean = false) {
        val item = currentTimelineItem

        if (item == null) {
            lastSyncedPreviewItemKey = null
            binding.ivPreviewPlaceholder.setImageDrawable(null)
            updatePreviewVisibility(hasMedia = false)
            return
        }

        val itemKey = item.timelineKey()
        if (!force && lastSyncedPreviewItemKey == itemKey) {
            updatePreviewVisibility(hasMedia = true)
            return
        }

        lastSyncedPreviewItemKey = itemKey

        if (item.type == "image") {
            val imageUri = item.sourceUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            if (imageUri != null) {
                binding.ivPreviewPlaceholder.setImageURI(imageUri)
            } else {
                binding.ivPreviewPlaceholder.setImageDrawable(null)
            }
        }

        updatePreviewVisibility(hasMedia = true)
    }

    /**
     * Actualiza etiquetas de tiempo usando la lista reproducible real del player.
     *
     * Si el usuario está scrolleando manualmente el timeline,
     * se conserva el tiempo preview del gesto.
     */
    private fun updateTimeTextsFromPlayer() {
        if (isUserScrollingTimeline || isDraggingPlayhead) return

        if (visualTimelineItems.isEmpty()) {
            binding.tvTimeCurrent.text = formatDuration(0L)
            binding.tvTimeTotal.text = formatDuration(0L)
            return
        }

        val globalPosition = playerController.getGlobalPlaybackPosition()
        binding.tvTimeCurrent.text = formatDuration(globalPosition)
        binding.tvTimeTotal.text = formatDuration(totalTimelineDurationMs)
    }
    /**
     * Actualiza únicamente la UI liviana mientras el usuario scrollea el timeline.
     *
     * Optimización:
     * - Evita trabajo repetido si el scrollX no cambió.
     * - No toca ExoPlayer.
     * - No cambia preview.
     */
    private fun updateTimelineUiFromScrollPreview() {
        if (visualTimelineItems.isEmpty()) return

        val contentWidth = binding.timelineContent.width
            .coerceAtLeast(binding.timelineVideoTrack.width)
        if (contentWidth <= 0) return

        val currentScrollX = binding.timelineScroll.scrollX
        if (currentScrollX == lastTimelineScrollX) return
        lastTimelineScrollX = currentScrollX

        val totalDuration = totalTimelineDurationMs.coerceAtLeast(1L)

        val globalPositionMs = TimelineSeekHelper.calculateGlobalPositionFromScroll(
            timelineScrollX = currentScrollX,
            viewportWidth = binding.timelineScroll.width,
            contentWidth = contentWidth,
            totalDurationMs = totalDuration,
            fixedGuideX = 48f
        )

        pendingScrollSeekPositionMs = globalPositionMs
        binding.tvTimeCurrent.text = formatDuration(globalPositionMs)
        updatePlayhead(globalPositionMs, totalDuration)
    }
    /**
     * Aplica el seek real al reproductor cuando el usuario termina el scroll.
     *
     * Recién aquí se actualiza el item actual, la preview y la selección.
     */
    private fun commitTimelineScrollSeek() {
        if (visualTimelineItems.isEmpty()) return
        if (playerController.mediaItemCount() <= 0) return

        val targetPositionMs = pendingScrollSeekPositionMs
            .coerceIn(0L, totalTimelineDurationMs.coerceAtLeast(0L))

        playerController.seekToGlobalPosition(
            globalPositionMs = targetPositionMs,
            onTimelineItemChanged = { changedItem, playableIndex ->
                currentTimelineItem = changedItem
                currentPlaylistIndex = visualTimelineItems.indexOfFirst {
                    it.timelineKey() == changedItem.timelineKey()
                }.coerceAtLeast(playableIndex.coerceAtLeast(0))
                syncPreviewWithCurrentItem()
            },
            onUiSyncRequested = {
                if (!isUserScrollingTimeline && !isDraggingPlayhead) {
                    updateTimeTextsFromPlayer()
                    updatePlayheadFromPlayer()
                }
            }
        )
    }
    /**
     * Mueve el playhead desde el arrastre del usuario.
     */
    private fun movePlayheadFromTouch(rawLocalX: Float, seekPlayer: Boolean) {
        val totalDuration = totalTimelineDurationMs.coerceAtLeast(1L)
        val contentWidth = binding.timelineContent.width.coerceAtLeast(
            binding.timelineVideoTrack.width
        )

        if (contentWidth <= 0) return
        if (visualTimelineItems.isEmpty()) return

        val globalPositionMs = TimelineSeekHelper.calculateGlobalPositionFromTouch(
            rawLocalX = rawLocalX,
            contentWidth = contentWidth,
            totalDurationMs = totalDuration
        )

        updatePlayhead(globalPositionMs, totalDuration)
        binding.tvTimeCurrent.text = formatDuration(globalPositionMs)

        if (seekPlayer && playerController.mediaItemCount() > 0) {
            playerController.seekToGlobalPosition(
                globalPositionMs = globalPositionMs,
                onTimelineItemChanged = { changedItem, playableIndex ->
                    currentTimelineItem = changedItem
                    currentPlaylistIndex = visualTimelineItems.indexOfFirst {
                        it.timelineKey() == changedItem.timelineKey()
                    }.coerceAtLeast(playableIndex.coerceAtLeast(0))
                    syncPreviewWithCurrentItem()
                    renderTimelineSelection()
                },
                onUiSyncRequested = {
                    updateTimeTextsFromPlayer()
                    updatePlayheadFromPlayer()
                    renderTimelineSelection()
                }
            )
        }
    }

    /**
     * Actualiza la posición visual del playhead.
     */
    private fun updatePlayhead(currentMs: Long, totalMs: Long) {
        TimelineSeekHelper.updatePlayhead(
            timelineContainer = binding.timelineContainer,
            timelineScroll = binding.timelineScroll,
            timelineContent = binding.timelineContent,
            timelineVideoTrack = binding.timelineVideoTrack,
            playhead = binding.playhead,
            playheadHandle = binding.playheadHandle,
            currentMs = currentMs,
            totalMs = totalMs,
            isDraggingPlayhead = isDraggingPlayhead
        )
    }

    /**
     * Refresca playhead desde la posición real del player.
     *
     * Si el usuario está scrolleando manualmente el timeline,
     * no se actualiza para evitar pelear con la UI del gesto.
     */
    private fun updatePlayheadFromPlayer() {
        if (isUserScrollingTimeline || isDraggingPlayhead) return

        val globalPosition = playerController.getGlobalPlaybackPosition()
        updatePlayhead(globalPosition, totalTimelineDurationMs.coerceAtLeast(1L))
    }

    /**
     * Renderiza la selección actual del timeline.
     */
    private fun renderTimelineSelection() {
        timelineRenderer.renderTimelineItems(
            items = timelineItems,
            currentTimelineItem = currentTimelineItem,
            currentPlaylistIndex = currentPlaylistIndex
        )
    }

    /**
     * Cambia el ícono de mute.
     */
    private fun updateMuteIcon(isVideoMuted: Boolean) {
        binding.btnMute.setImageResource(
            if (isVideoMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        )
    }

    /**
     * Cambia el ícono de play o pausa.
     */
    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.pause_24px else R.drawable.ic_play
        )
    }

    /**
     * Muestra player o placeholder según el tipo actual.
     */
    private fun updatePreviewVisibility(hasMedia: Boolean) {
        val item = currentTimelineItem

        if (!hasMedia || item == null) {
            binding.playerView.visibility = View.GONE
            binding.ivPreviewPlaceholder.visibility = View.VISIBLE
            return
        }

        if (item.type == "video") {
            binding.playerView.visibility = View.VISIBLE
            binding.ivPreviewPlaceholder.visibility = View.GONE
        } else {
            binding.playerView.visibility = View.GONE
            binding.ivPreviewPlaceholder.visibility = View.VISIBLE
        }
    }

    /**
     * Carga título y duración base del proyecto.
     */
    private fun loadProjectInfo() {
        if (projectId == VideoEditorConfig.INVALID_PROJECT_ID) {
            renderInvalidProject()
            return
        }

        lifecycleScope.launch {
            try {
                val project = repository.getProjectById(projectId)

                if (project == null) {
                    renderInvalidProject()
                    return@launch
                }

                binding.tvProjectTitle.text = project.title
                binding.tvTimeCurrent.text = formatDuration(0L)

                val durationToShow = if (totalTimelineDurationMs > 0L) {
                    totalTimelineDurationMs
                } else {
                    project.durationMs
                }

                binding.tvTimeTotal.text = formatDuration(durationToShow)
            } catch (e: Exception) {
                saveDebugReport(
                    tag = "load_project_info_error",
                    message = "Error cargando información del proyecto",
                    throwable = e,
                    extraData = mapOf("projectId" to projectId.toString())
                )
            }
        }
    }

    /**
     * Registra una edición simple.
     */
    private fun registerEdit(editType: String, message: String) {
        if (projectId == VideoEditorConfig.INVALID_PROJECT_ID) return

        lifecycleScope.launch {
            try {
                repository.addEdit(
                    projectId = projectId,
                    editType = editType
                )
                actionTagController.showTemporaryAction(message)
            } catch (e: Exception) {
                saveDebugReport(
                    tag = "register_edit_error",
                    message = "Error registrando edición",
                    throwable = e,
                    extraData = mapOf(
                        "projectId" to projectId.toString(),
                        "editType" to editType
                    )
                )
            }
        }
    }

    /**
     * Renderiza estado inválido.
     */
    private fun renderInvalidProject() {
        currentTimelineItem = null
        currentPlaylistIndex = 0
        timelineItems = emptyList()
        visualTimelineItems = emptyList()
        totalTimelineDurationMs = 0L

        binding.tvProjectTitle.text = getString(R.string.project_unidentified)
        binding.tvTimeCurrent.text = getString(R.string.time_zero)
        binding.tvTimeTotal.text = getString(R.string.time_zero)

        binding.timelineVideoTrack.removeAllViews()
        binding.audioWaveTrack.removeAllViews()
        binding.externalAudioTrackContainer.visibility = View.GONE

        if (::playerController.isInitialized) {
            playerController.stopAndClear()
            updateMuteIcon(playerController.isMuted())
        }

        binding.ivPreviewPlaceholder.setImageDrawable(null)
        updatePreviewVisibility(false)
        updatePlayPauseIcon(false)
        actionTagController.clear()
    }

    /**
     * Inicia autosave si el proyecto es válido.
     */
    private fun startAutoSaveIfPossible() {
        if (projectId != VideoEditorConfig.INVALID_PROJECT_ID) {
            autoSaveController.start(projectId)
        }
    }

    override fun onStop() {
        super.onStop()
        if (::playerController.isInitialized) {
            playerController.pause()
        }
        updatePlayPauseIcon(false)
    }

    override fun onDestroy() {
        errorReportManager.trimReports(30)
        autoSaveController.stop()
        actionTagController.release()

        if (::playerController.isInitialized) {
            playerController.release()
        }

        super.onDestroy()
    }

    /**
     * Guarda un reporte y muestra al usuario si se logró crear.
     */
    private fun saveDebugReport(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        extraData: Map<String, String> = emptyMap()
    ) {
        val file = errorReportManager.saveErrorReport(
            tag = tag,
            message = message,
            throwable = throwable,
            extraData = extraData
        )

        if (file != null) {
            Toast.makeText(
                this,
                "Reporte guardado en: ${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "No se pudo guardar el reporte de error",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        /**
         * Crea el intent para abrir el editor.
         */
        fun createIntent(context: Context, projectId: Long): Intent {
            return Intent(context, VideoEditorActivity::class.java).apply {
                putExtra(VideoEditorConfig.EXTRA_PROJECT_ID, projectId)
            }
        }
    }
}