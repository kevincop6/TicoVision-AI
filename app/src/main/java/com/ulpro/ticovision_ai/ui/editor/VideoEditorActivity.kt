package com.ulpro.ticovision_ai.ui.editor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.view.MotionEvent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private var timelineVideoItems: List<TimelineItemEntity> = emptyList()
    private var timelineItems: List<TimelineItemEntity> = emptyList()
    private var currentPlaylistIndex: Int = 0
    private var totalVideoDurationMs: Long = 0L
    private var isDraggingPlayhead: Boolean = false
    private var isVideoMuted: Boolean = false
    private var timelineTrackWidthPx: Int = 0

    private val thumbnailMemoryCache by lazy {
        object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024L / 16L).toInt()) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val newIndex = player.currentMediaItemIndex
            if (newIndex in timelineVideoItems.indices) {
                currentPlaylistIndex = newIndex
                currentTimelineItem = timelineVideoItems[newIndex]
            }
            updateTimeTextsFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                updatePlayPauseIcon(false)
                updateTimeTextsFromPlayer()
                updatePlayhead(totalVideoDurationMs, totalVideoDurationMs.coerceAtLeast(1L))
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
            ) {
                updateTimeTextsFromPlayer()
            }
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
        setupTimelineInteractions()
        loadProjectInfo()
        restoreTimelineAndPreview()
        startAutoSaveLoop()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)

        binding.playerView.player = player
        binding.playerView.useController = false

        isVideoMuted = false
        player.volume = 1f

        binding.externalAudioTrackContainer.visibility = View.GONE

        updateMuteIcon()
        updatePreviewVisibility(hasMedia = false)
        updatePlayPauseIcon(isPlaying = false)
        updatePlayhead(0L, 1L)
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
            isVideoMuted = !isVideoMuted
            player.volume = if (isVideoMuted) 0f else 1f
            updateMuteIcon()

            val message = if (isVideoMuted) {
                "Audio del video silenciado"
            } else {
                "Audio del video activado"
            }
            showTemporaryAction(message)
        }

        binding.btnPlayPause.setOnClickListener {
            if (timelineVideoItems.isEmpty()) {
                showTemporaryAction("No hay videos cargados para reproducir")
                updatePlayPauseIcon(isPlaying = false)
                return@setOnClickListener
            }

            if (player.mediaItemCount == 0) {
                prepareTimelinePlaylist(
                    startIndex = currentPlaylistIndex.coerceIn(0, timelineVideoItems.lastIndex),
                    startPositionMs = 0L,
                    autoPlay = true
                )
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
            showTemporaryAction("La carga de audio externo se conectará en la siguiente fase")
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

    private fun setupTimelineInteractions() {
        binding.timelineScroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDraggingPlayhead = false
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isDraggingPlayhead = false
                }
            }
            false
        }

        val dragListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDraggingPlayhead = true
                    player.pause()
                    updatePlayPauseIcon(false)
                    binding.timelineScroll.parent?.requestDisallowInterceptTouchEvent(true)

                    val contentX = getTimelineTouchXInsideContent(event.rawX)
                    movePlayheadFromTouch(contentX, false)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val contentX = getTimelineTouchXInsideContent(event.rawX)
                    movePlayheadFromTouch(contentX, false)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val contentX = getTimelineTouchXInsideContent(event.rawX)
                    movePlayheadFromTouch(contentX, true)

                    isDraggingPlayhead = false
                    binding.timelineScroll.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                else -> false
            }
        }

        binding.playhead.setOnTouchListener(dragListener)
        binding.playheadHandle.setOnTouchListener(dragListener)
    }

    private fun getTimelineTouchXInsideContent(rawX: Float): Float {
        val location = IntArray(2)
        binding.timelineContent.getLocationOnScreen(location)

        val contentLeftOnScreen = location[0].toFloat()
        val scrollOffset = binding.timelineScroll.scrollX.toFloat()

        return (rawX - contentLeftOnScreen) + scrollOffset
    }

    private fun movePlayheadFromTouch(rawLocalX: Float, seekPlayer: Boolean) {
        val totalDuration = totalVideoDurationMs.coerceAtLeast(1L)
        val contentWidth = binding.timelineContent.width.coerceAtLeast(binding.timelineVideoTrack.width)

        if (contentWidth <= 0) return
        if (timelineVideoItems.isEmpty()) return

        val clampedX = rawLocalX.coerceIn(0f, contentWidth.toFloat())
        val progress = clampedX / contentWidth.toFloat()
        val globalPositionMs = (progress * totalDuration).toLong()

        updatePlayhead(globalPositionMs, totalDuration)
        binding.tvTimeCurrent.text = formatDuration(globalPositionMs)

        if (seekPlayer) {
            seekToGlobalPosition(globalPositionMs)
        }
    }

    private fun updateMuteIcon() {
        binding.btnMute.setImageResource(
            if (isVideoMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        )
    }

    private fun updateTimeTextsFromPlayer() {
        if (timelineVideoItems.isEmpty()) {
            binding.tvTimeCurrent.text = formatDuration(0L)
            binding.tvTimeTotal.text = formatDuration(0L)
            return
        }

        val globalPosition = getGlobalPlaybackPosition()
        binding.tvTimeCurrent.text = formatDuration(globalPosition)
        binding.tvTimeTotal.text = formatDuration(totalVideoDurationMs)
    }

    private fun getGlobalPlaybackPosition(): Long {
        if (timelineVideoItems.isEmpty()) return 0L
        if (player.mediaItemCount == 0) return 0L

        val currentIndex = player.currentMediaItemIndex
            .coerceIn(0, timelineVideoItems.lastIndex)

        var accumulated = 0L
        for (index in 0 until currentIndex) {
            accumulated += timelineVideoItems[index].durationMs.coerceAtLeast(0L)
        }

        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        return (accumulated + currentPosition).coerceAtMost(totalVideoDurationMs)
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

                player.pause()
                player.clearMediaItems()
                currentTimelineItem = null

                binding.ivPreviewPlaceholder.setImageURI(uri)
                binding.tvTimeCurrent.text = formatDuration(0L)
                binding.tvTimeTotal.text = formatDuration(totalVideoDurationMs)

                updatePreviewVisibility(hasMedia = true)
                updatePlayPauseIcon(isPlaying = false)

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

    private fun restoreTimelineAndPreview(playLastAdded: Boolean = false) {
        if (projectId == INVALID_PROJECT_ID) return

        lifecycleScope.launch {
            timelineItems = repository.getTimelineItems(projectId)
            timelineVideoItems = timelineItems.filter { it.type == "video" }
            totalVideoDurationMs = timelineVideoItems.sumOf { it.durationMs.coerceAtLeast(0L) }

            renderTimelineItems(timelineItems)

            if (timelineItems.isEmpty()) {
                currentTimelineItem = null
                currentPlaylistIndex = 0
                totalVideoDurationMs = 0L

                player.pause()
                player.clearMediaItems()

                updatePreviewVisibility(hasMedia = false)
                updatePlayPauseIcon(isPlaying = false)
                binding.tvTimeCurrent.text = formatDuration(0L)
                binding.tvTimeTotal.text = formatDuration(0L)
                updatePlayhead(0L, 1L)
                return@launch
            }

            updatePreviewVisibility(hasMedia = true)

            if (timelineVideoItems.isEmpty()) {
                currentTimelineItem = null
                currentPlaylistIndex = 0

                player.pause()
                player.clearMediaItems()

                binding.tvTimeCurrent.text = formatDuration(0L)
                binding.tvTimeTotal.text = formatDuration(0L)
                updatePlayPauseIcon(isPlaying = false)
                updatePlayhead(0L, 1L)
                return@launch
            }

            val selectedIndex = if (playLastAdded) {
                timelineVideoItems.lastIndex
            } else {
                currentPlaylistIndex.coerceIn(0, timelineVideoItems.lastIndex)
            }

            currentPlaylistIndex = selectedIndex
            currentTimelineItem = timelineVideoItems[selectedIndex]

            prepareTimelinePlaylist(
                startIndex = selectedIndex,
                startPositionMs = 0L,
                autoPlay = false
            )
        }
    }

    private fun prepareTimelinePlaylist(
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean
    ) {
        if (timelineVideoItems.isEmpty()) {
            player.pause()
            player.clearMediaItems()
            updatePlayPauseIcon(false)
            return
        }

        val mediaItems = timelineVideoItems.mapNotNull { item ->
            val uriString = item.sourceUri ?: return@mapNotNull null
            MediaItem.fromUri(Uri.parse(uriString))
        }

        if (mediaItems.isEmpty()) {
            player.pause()
            player.clearMediaItems()
            updatePlayPauseIcon(false)
            return
        }

        val safeIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        val safePosition = startPositionMs.coerceAtLeast(0L)

        currentPlaylistIndex = safeIndex
        currentTimelineItem = timelineVideoItems[safeIndex]

        player.setMediaItems(mediaItems, safeIndex, safePosition)
        player.prepare()
        player.playWhenReady = autoPlay
        player.volume = if (isVideoMuted) 0f else 1f

        updatePreviewVisibility(hasMedia = true)
        updatePlayPauseIcon(autoPlay)
        updateTimeTextsFromPlayer()
        startPlaybackUiSync()

        binding.timelineContainer.post {
            updatePlayhead(getGlobalPlaybackPosition(), totalVideoDurationMs.coerceAtLeast(1L))
        }
    }

    private fun findClipIndexAndLocalPosition(globalPositionMs: Long): Pair<Int, Long> {
        if (timelineVideoItems.isEmpty()) return 0 to 0L

        var accumulated = 0L

        timelineVideoItems.forEachIndexed { index, item ->
            val duration = item.durationMs.coerceAtLeast(0L)
            val clipEnd = accumulated + duration

            if (globalPositionMs < clipEnd) {
                val localPosition = (globalPositionMs - accumulated).coerceAtLeast(0L)
                return index to localPosition
            }

            accumulated = clipEnd
        }

        val lastIndex = timelineVideoItems.lastIndex
        val lastDuration = timelineVideoItems[lastIndex].durationMs.coerceAtLeast(0L)
        return lastIndex to lastDuration.coerceAtLeast(0L)
    }

    private fun seekToGlobalPosition(globalPositionMs: Long) {
        if (timelineVideoItems.isEmpty()) return

        val safeGlobal = globalPositionMs.coerceIn(0L, totalVideoDurationMs.coerceAtLeast(0L))
        val (targetIndex, localPositionMs) = findClipIndexAndLocalPosition(safeGlobal)

        currentPlaylistIndex = targetIndex
        currentTimelineItem = timelineVideoItems[targetIndex]

        if (player.mediaItemCount == 0) {
            prepareTimelinePlaylist(
                startIndex = targetIndex,
                startPositionMs = localPositionMs,
                autoPlay = false
            )
            return
        }

        if (player.currentMediaItemIndex != targetIndex) {
            player.seekTo(targetIndex, localPositionMs)
        } else {
            player.seekTo(localPositionMs)
        }

        updateTimeTextsFromPlayer()
        updatePlayhead(safeGlobal, totalVideoDurationMs.coerceAtLeast(1L))
    }

    private fun startPlaybackUiSync() {
        playbackUiJob?.cancel()

        playbackUiJob = lifecycleScope.launch {
            while (isActive) {
                if (timelineVideoItems.isNotEmpty() && player.mediaItemCount > 0) {
                    val globalPosition = getGlobalPlaybackPosition()
                    val totalDuration = totalVideoDurationMs.coerceAtLeast(1L)

                    binding.tvTimeCurrent.text = formatDuration(globalPosition)
                    binding.tvTimeTotal.text = formatDuration(totalVideoDurationMs)

                    if (!isDraggingPlayhead) {
                        updatePlayhead(globalPosition, totalDuration)
                    }
                }

                delay(50L)
            }
        }
    }

    private fun updatePlayhead(currentMs: Long, totalMs: Long) {
        if (totalMs <= 0L) return

        val contentWidth = binding.timelineContent.width.coerceAtLeast(binding.timelineVideoTrack.width)
        if (contentWidth <= 0) return

        timelineTrackWidthPx = contentWidth

        val progress = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

        binding.timelineContainer.post {
            val playheadWidth = binding.playhead.width.coerceAtLeast(1)
            val handleWidth = binding.playheadHandle.width.coerceAtLeast(playheadWidth)
            val usableWidth = (contentWidth - playheadWidth).coerceAtLeast(0)

            val targetX = usableWidth * progress

            binding.playhead.translationX = targetX
            binding.playheadHandle.translationX = targetX - ((handleWidth - playheadWidth) / 2f)

            if (!isDraggingPlayhead) {
                val centerOffset = binding.timelineScroll.width / 2f
                val desiredScroll = (targetX - centerOffset + (playheadWidth / 2f)).toInt()
                val maxScroll = (contentWidth - binding.timelineScroll.width).coerceAtLeast(0)

                binding.timelineScroll.scrollTo(
                    desiredScroll.coerceIn(0, maxScroll),
                    0
                )
            }
        }
    }

    private fun renderTimelineItems(items: List<TimelineItemEntity>) {
        binding.timelineVideoTrack.removeAllViews()

        timelineTrackWidthPx = 0

        if (items.isEmpty()) {
            updateTimelineContentWidth(0)
            renderExternalAudioTrackIfNeeded(items)
            return
        }

        val visualTimelineItems = items.filter { it.type == "video" || it.type == "image" }

        visualTimelineItems.forEachIndexed { index, item ->
            val itemView = when (item.type) {
                "video" -> createVideoThumbnailView(item, index)
                "image" -> createImageThumbnailView(item, index)
                else -> null
            }

            if (itemView != null) {
                binding.timelineVideoTrack.addView(itemView)
            }
        }

        binding.timelineVideoTrack.post {
            timelineTrackWidthPx = binding.timelineVideoTrack.width
            updateTimelineContentWidth(timelineTrackWidthPx)
            updatePlayhead(getGlobalPlaybackPosition(), totalVideoDurationMs.coerceAtLeast(1L))
        }

        renderExternalAudioTrackIfNeeded(items)
    }

    private fun updateTimelineContentWidth(trackWidthPx: Int) {
        val minWidthPx = 900.dp(this)
        val finalWidth = maxOf(trackWidthPx, minWidthPx)

        val params = binding.timelineContent.layoutParams
        params.width = finalWidth
        binding.timelineContent.layoutParams = params

        binding.timelineRuler.layoutParams = binding.timelineRuler.layoutParams.apply {
            width = finalWidth
        }

        binding.audioWaveTrack.layoutParams = binding.audioWaveTrack.layoutParams.apply {
            width = finalWidth
        }
    }

    private fun createVideoThumbnailView(
        item: TimelineItemEntity,
        index: Int
    ): View {
        val itemWidth = calculateTimelineItemWidthPx(item)
        val frameLayout = FrameLayout(this)

        val params = LinearLayout.LayoutParams(
            itemWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            marginEnd = 2.dp(this@VideoEditorActivity)
        }

        frameLayout.layoutParams = params
        frameLayout.setBackgroundColor(0xFF2A2A2A.toInt())

        val framesStrip = createVideoFramesStripOptimized(item, itemWidth)
        frameLayout.addView(framesStrip)

        val isSelected = currentPlaylistIndex == index &&
                currentTimelineItem?.sourceUri == item.sourceUri

        val overlay = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(if (isSelected) 0x22FFFFFF else 0x08000000)
        }

        val borderView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = ContextCompat.getDrawable(
                this@VideoEditorActivity,
                if (isSelected) {
                    R.drawable.bg_selected_timeline_item
                } else {
                    R.drawable.bg_normal_timeline_item
                }
            )
        }

        frameLayout.addView(overlay)
        frameLayout.addView(borderView)

        frameLayout.setOnClickListener {
            currentPlaylistIndex = index
            currentTimelineItem = item

            prepareTimelinePlaylist(
                startIndex = index,
                startPositionMs = 0L,
                autoPlay = false
            )

            renderTimelineItems(timelineItems)
            showTemporaryAction("Clip seleccionado")
        }

        return frameLayout
    }

    private fun createImageThumbnailView(
        item: TimelineItemEntity,
        index: Int
    ): View {
        val itemWidth = calculateTimelineItemWidthPx(item)
        val frameLayout = FrameLayout(this)

        val params = LinearLayout.LayoutParams(
            itemWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            marginEnd = 2.dp(this@VideoEditorActivity)
        }

        frameLayout.layoutParams = params
        frameLayout.setBackgroundColor(0xFF2E2E2E.toInt())

        val framesStrip = createImageFramesStrip(item, itemWidth)
        frameLayout.addView(framesStrip)

        val overlay = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x14000000)
        }

        frameLayout.addView(overlay)

        frameLayout.setOnClickListener {
            player.pause()
            player.clearMediaItems()

            currentTimelineItem = null
            currentPlaylistIndex = index.coerceAtLeast(0)

            item.sourceUri?.let {
                binding.ivPreviewPlaceholder.setImageURI(Uri.parse(it))
            }

            binding.tvTimeCurrent.text = formatDuration(0L)
            binding.tvTimeTotal.text = formatDuration(totalVideoDurationMs)
            updatePreviewVisibility(hasMedia = true)
            updatePlayPauseIcon(isPlaying = false)

            showTemporaryAction("Imagen cargada en preview")
        }

        return frameLayout
    }

    private fun renderExternalAudioTrackIfNeeded(items: List<TimelineItemEntity>) {
        val externalAudioItems = items.filter { it.type == "audio" }

        if (externalAudioItems.isEmpty()) {
            binding.externalAudioTrackContainer.visibility = View.GONE
            binding.audioWaveTrack.removeAllViews()
            binding.tvAudioTrack.text = ""
            return
        }

        binding.externalAudioTrackContainer.visibility = View.VISIBLE
        binding.audioWaveTrack.removeAllViews()

        val firstAudioItem = externalAudioItems.first()
        val label = firstAudioItem.sourceUri
            ?.substringAfterLast("/")
            ?.substringBefore("?")
            ?.ifBlank { "Audio externo" }
            ?: "Audio externo"

        binding.tvAudioTrack.text = "Audio: $label"

        val barCount = 48
        repeat(barCount) { barIndex ->
            binding.audioWaveTrack.addView(createAudioWaveBar(barIndex))
        }
    }

    private fun createAudioWaveBar(index: Int): View {
        val heightsDp = listOf(10, 16, 8, 20, 12, 24, 11, 18, 9, 22, 14, 19)
        val barHeight = heightsDp[index % heightsDp.size].dp(this)

        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                3.dp(this@VideoEditorActivity),
                barHeight
            ).apply {
                marginEnd = 3.dp(this@VideoEditorActivity)
            }
            setBackgroundColor(0xFFB7D9B0.toInt())
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

    private fun updatePreviewVisibility(hasMedia: Boolean) {
        if (hasMedia && currentTimelineItem != null && currentTimelineItem?.type == "video") {
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
        val recentMessages = actionMessages.takeLast(2).reversed()

        val firstMessage = recentMessages.getOrNull(0).orEmpty()
        val secondMessage = recentMessages.getOrNull(1).orEmpty()

        binding.tvActionTagOne.text = firstMessage
        binding.tvActionTagTwo.text = secondMessage

        binding.tvActionTagOne.visibility =
            if (firstMessage.isBlank()) View.GONE else View.VISIBLE

        binding.tvActionTagTwo.visibility =
            if (secondMessage.isBlank()) View.GONE else View.VISIBLE

        binding.actionTagsContainer.visibility =
            if (firstMessage.isBlank() && secondMessage.isBlank()) View.GONE else View.VISIBLE
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
            binding.tvTimeCurrent.text = formatDuration(0L)

            val durationToShow = if (totalVideoDurationMs > 0L) {
                totalVideoDurationMs
            } else {
                project.durationMs
            }

            binding.tvTimeTotal.text = formatDuration(durationToShow)
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
        currentTimelineItem = null
        currentPlaylistIndex = 0
        timelineItems = emptyList()
        timelineVideoItems = emptyList()
        totalVideoDurationMs = 0L
        timelineTrackWidthPx = 0

        binding.tvProjectTitle.text = getString(R.string.project_unidentified)
        binding.tvTimeCurrent.text = getString(R.string.time_zero)
        binding.tvTimeTotal.text = getString(R.string.time_zero)

        binding.timelineVideoTrack.removeAllViews()
        binding.audioWaveTrack.removeAllViews()
        binding.externalAudioTrackContainer.visibility = View.GONE

        player.pause()
        player.clearMediaItems()

        updatePreviewVisibility(hasMedia = false)
        updatePlayPauseIcon(isPlaying = false)
        updateMuteIcon()
        renderActionTags()
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
        private const val TIMELINE_ITEM_WIDTH_DP = 148

        fun createIntent(context: Context, projectId: Long): Intent {
            return Intent(context, VideoEditorActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
        }
    }

    private fun calculateTimelineItemWidthPx(item: TimelineItemEntity): Int {
        val minWidth = 96.dp(this)
        val pxPerSecond = 36.dp(this)
        val durationSeconds = (item.durationMs.coerceAtLeast(1000L) / 1000f)
        val computedWidth = (durationSeconds * pxPerSecond).toInt()
        return computedWidth.coerceAtLeast(minWidth)
    }

    private fun createImageFramesStrip(
        item: TimelineItemEntity,
        itemWidth: Int
    ): LinearLayout {
        val strip = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        val frameWidth = 48.dp(this)
        val frameCount = (itemWidth / frameWidth).coerceAtLeast(2)
        val uri = item.sourceUri?.let { Uri.parse(it) }

        repeat(frameCount) {
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF202020.toInt())
            }

            if (uri != null) {
                imageView.setImageURI(uri)
            }

            strip.addView(imageView)
        }

        return strip
    }

    private fun createVideoFramesStripOptimized(
        item: TimelineItemEntity,
        itemWidth: Int
    ): LinearLayout {
        val strip = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        val frameWidth = 56.dp(this)
        val frameCount = (itemWidth / frameWidth).coerceIn(2, 8)
        val durationMs = item.durationMs.coerceAtLeast(1L)
        val uri = item.sourceUri?.let { Uri.parse(it) }

        repeat(frameCount) { frameIndex ->
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF202020.toInt())
            }

            strip.addView(imageView)

            if (uri != null) {
                val timeUs = ((durationMs * frameIndex.toLong()) / frameCount) * 1000L
                val cacheKey = "${uri}_$timeUs"

                val cachedBitmap = thumbnailMemoryCache.get(cacheKey)
                if (cachedBitmap != null) {
                    imageView.setImageBitmap(cachedBitmap)
                } else {
                    lifecycleScope.launch {
                        val bitmap = withContext(Dispatchers.IO) {
                            getVideoThumbnailAtTime(uri, timeUs)
                        }

                        if (bitmap != null) {
                            thumbnailMemoryCache.put(cacheKey, bitmap)
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }

        return strip
    }

    private fun getVideoThumbnailAtTime(uri: Uri, timeUs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs.coerceAtLeast(0L),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    160,
                    160
                )
            } else {
                retriever.getFrameAtTime(
                    timeUs.coerceAtLeast(0L),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}

private fun Int.dp(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}