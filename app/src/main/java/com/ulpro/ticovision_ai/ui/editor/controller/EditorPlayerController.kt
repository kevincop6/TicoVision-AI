package com.ulpro.ticovision_ai.ui.editor.controller

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity
import com.ulpro.ticovision_ai.databinding.ActivityVideoEditorBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EditorPlayerController(
    private val context: Context,
    private val binding: ActivityVideoEditorBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onIsPlayingChanged: (Boolean) -> Unit,
    private val onMediaItemTransition: (Int) -> Unit,
    private val onPlaybackEnded: () -> Unit,
    private val onPositionRelevantEvent: () -> Unit,
    private val onPlayerError: (Throwable) -> Unit
) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var playbackUiJob: Job? = null
    private var isVideoMuted: Boolean = false

    // Handler del Main Thread para garantizar que todos los callbacks
    // de la UI se ejecuten en el hilo correcto, incluyendo los errores
    // que ExoPlayer dispara desde su hilo interno.
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentPlayableEntries: List<PlayableEntry> = emptyList()

    init {
        binding.playerView.player = player
        binding.playerView.useController = false
        player.volume = 1f

        player.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // ExoPlayer puede llamar esto desde su hilo interno
                mainHandler.post { onIsPlayingChanged(isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val safeIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                // Garantizamos ejecución en el Main Thread
                mainHandler.post { onMediaItemTransition(safeIndex) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    mainHandler.post { onPlaybackEnded() }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // BUG CRÍTICO CORREGIDO: onPlayerError venía del hilo de ExoPlayer.
                // Sin mainHandler.post{}, cualquier Toast o acceso a View causaba
                // CalledFromWrongThreadException -> crash de la Activity.
                mainHandler.post { onPlayerError(error) }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                    events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                ) {
                    mainHandler.post { onPositionRelevantEvent() }
                }
            }
        })
    }

    fun isMuted(): Boolean = isVideoMuted

    fun mediaItemCount(): Int = player.mediaItemCount

    fun getCurrentPlayableTimelineItems(): List<TimelineItemEntity> {
        return currentPlayableEntries.map { it.sourceItem }
    }

    fun getCurrentPlayableItem(): TimelineItemEntity? {
        if (currentPlayableEntries.isEmpty()) return null
        val index = player.currentMediaItemIndex.coerceIn(0, currentPlayableEntries.lastIndex)
        return currentPlayableEntries.getOrNull(index)?.sourceItem
    }

    fun toggleMute(): Boolean {
        isVideoMuted = !isVideoMuted
        player.volume = if (isVideoMuted) 0f else 1f
        return isVideoMuted
    }

    fun togglePlayPause() {
        try {
            if (player.mediaItemCount == 0) {
                onIsPlayingChanged(false)
                return
            }
            if (player.isPlaying) player.pause() else player.play()
        } catch (e: Exception) {
            mainHandler.post { onPlayerError(e) }
        }
    }

    fun pause() {
        try {
            player.pause()
        } catch (e: Exception) {
            mainHandler.post { onPlayerError(e) }
        }
    }

    fun stopAndClear() {
        try {
            player.pause()
            player.clearMediaItems()
            player.seekTo(0L)
            currentPlayableEntries = emptyList()
        } catch (e: Exception) {
            mainHandler.post { onPlayerError(e) }
        } finally {
            playbackUiJob?.cancel()
        }
    }

    fun release() {
        try {
            playbackUiJob?.cancel()
            mainHandler.removeCallbacksAndMessages(null) // Limpia handlers pendientes
            currentPlayableEntries = emptyList()
            player.release()
        } catch (e: Exception) {
            // En release no reenviamos al handler porque la Activity puede estar destruida
        }
    }

    private fun buildItemKey(item: TimelineItemEntity): String {
        val source = item.sourceUri ?: "no_source"
        val type = item.type ?: "unknown"
        val duration = item.durationMs
        return "$type|$source|$duration"
    }

    private fun canReadUri(uri: Uri): Boolean {
        return try {
            when (uri.scheme?.lowercase()) {
                ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                }
                ContentResolver.SCHEME_FILE, "http", "https" -> true
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayableEntries(
        timelineItems: List<TimelineItemEntity>
    ): List<PlayableEntry> {
        return timelineItems.mapNotNull { item ->
            val sourceUri = item.sourceUri ?: return@mapNotNull null
            val parsedUri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return@mapNotNull null

            if (!canReadUri(parsedUri)) return@mapNotNull null

            val normalizedType = item.type?.lowercase() ?: return@mapNotNull null

            val mediaItem = when (normalizedType) {
                "video" -> MediaItem.fromUri(parsedUri)
                "image" -> MediaItem.Builder()
                    .setUri(parsedUri)
                    .setImageDurationMs(item.durationMs.coerceAtLeast(1L))
                    .build()
                else -> return@mapNotNull null
            }

            PlayableEntry(
                sourceItem = item,
                sourceKey = buildItemKey(item),
                mediaItem = mediaItem
            )
        }
    }

    fun prepareTimelinePlaylist(
        timelineItems: List<TimelineItemEntity>,
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean,
        isVideoMuted: Boolean,
        onTimelineItemChanged: (TimelineItemEntity, Int) -> Unit,
        onUiSyncRequested: () -> Unit
    ) {
        try {
            if (timelineItems.isEmpty()) {
                player.pause()
                player.clearMediaItems()
                currentPlayableEntries = emptyList()
                onIsPlayingChanged(false)
                return
            }

            val playableEntries = buildPlayableEntries(timelineItems)

            if (playableEntries.isEmpty()) {
                player.pause()
                player.clearMediaItems()
                currentPlayableEntries = emptyList()
                onIsPlayingChanged(false)
                // BUG SECUNDARIO CORREGIDO: antes faltaba return después de onPlayerError,
                // lo que causaba que se ejecutara setMediaItems() con lista vacía.
                mainHandler.post {
                    onPlayerError(
                        IllegalStateException("No hay clips válidos del timeline para reproducir.")
                    )
                }
                return // ← Este return faltaba
            }

            val safeStartIndex = startIndex.coerceIn(0, timelineItems.lastIndex)
            val requestedKey = buildItemKey(timelineItems[safeStartIndex])
            val mappedPlayableIndex = playableEntries
                .indexOfFirst { it.sourceKey == requestedKey }
                .takeIf { it >= 0 } ?: 0

            val mediaItems = playableEntries.map { it.mediaItem }
            playbackUiJob?.cancel()

            player.pause()
            player.clearMediaItems()
            currentPlayableEntries = playableEntries

            player.setMediaItems(mediaItems, mappedPlayableIndex, startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = autoPlay

            this.isVideoMuted = isVideoMuted
            player.volume = if (isVideoMuted) 0f else 1f

            onTimelineItemChanged(
                currentPlayableEntries[mappedPlayableIndex].sourceItem,
                mappedPlayableIndex
            )

            onUiSyncRequested()
            startPlaybackUiSync(onUiSyncRequested)

        } catch (e: Exception) {
            mainHandler.post { onPlayerError(e) }
        }
    }

    fun getGlobalPlaybackPosition(): Long {
        return try {
            if (currentPlayableEntries.isEmpty()) return 0L
            if (player.mediaItemCount == 0) return 0L

            val safeCurrentIndex = player.currentMediaItemIndex
                .coerceIn(0, currentPlayableEntries.lastIndex)

            var accumulated = 0L
            for (index in 0 until safeCurrentIndex) {
                accumulated += currentPlayableEntries[index].sourceItem.durationMs.coerceAtLeast(0L)
            }
            accumulated + player.currentPosition.coerceAtLeast(0L)
        } catch (e: Exception) {
            mainHandler.post { onPlayerError(e) }
            0L
        }
    }

    private fun findClipIndexAndLocalPosition(globalPositionMs: Long): Pair<Int, Long> {
        if (currentPlayableEntries.isEmpty()) return 0 to 0L
        var accumulated = 0L
        currentPlayableEntries.forEachIndexed { index, entry ->
            val duration = entry.sourceItem.durationMs.coerceAtLeast(0L)
            val clipEnd = accumulated + duration
            if (globalPositionMs < clipEnd) {
                return index to (globalPositionMs - accumulated).coerceAtLeast(0L)
            }
            accumulated = clipEnd
        }
        val lastIndex = currentPlayableEntries.lastIndex
        return lastIndex to currentPlayableEntries[lastIndex].sourceItem.durationMs.coerceAtLeast(0L)
    }

    fun seekToGlobalPosition(
        globalPositionMs: Long,
        onTimelineItemChanged: (TimelineItemEntity, Int) -> Unit,
        onUiSyncRequested: () -> Unit
    ) {
        try {
            if (currentPlayableEntries.isEmpty()) return
            if (player.mediaItemCount == 0) return

            val totalDuration = currentPlayableEntries.sumOf {
                it.sourceItem.durationMs.coerceAtLeast(0L)
            }
            val safeGlobal = globalPositionMs.coerceIn(0L, totalDuration.coerceAtLeast(0L))
            val (targetIndex, localPositionMs) = findClipIndexAndLocalPosition(safeGlobal)
            val safeTargetIndex = targetIndex.coerceIn(0, currentPlayableEntries.lastIndex)

            if (safeTargetIndex >= player.mediaItemCount) {
                mainHandler.post {
                    onPlayerError(
                        IndexOutOfBoundsException(
                            "Índice fuera de rango. targetIndex=$safeTargetIndex " +
                                    "mediaItemCount=${player.mediaItemCount}"
                        )
                    )
                }
                return
            }

            onTimelineItemChanged(currentPlayableEntries[safeTargetIndex].sourceItem, safeTargetIndex)

            if (player.currentMediaItemIndex != safeTargetIndex) {
                player.seekTo(safeTargetIndex, localPositionMs)
            } else {
                player.seekTo(localPositionMs)
            }

            onUiSyncRequested()
        } catch (e: Exception) {
            mainHandler.post { onPlayerError(e) }
        }
    }

    fun getCurrentPlayableDurationMs(): Long {
        return currentPlayableEntries.sumOf { it.sourceItem.durationMs.coerceAtLeast(0L) }
    }

    private fun startPlaybackUiSync(onUiSyncRequested: () -> Unit) {
        playbackUiJob?.cancel()

        playbackUiJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    if (player.mediaItemCount > 0 && player.isPlaying) {
                        onUiSyncRequested()
                    }
                } catch (e: Exception) {
                    onPlayerError(e)
                }
                delay(100L)
            }
        }
    }

    private data class PlayableEntry(
        val sourceItem: TimelineItemEntity,
        val sourceKey: String,
        val mediaItem: MediaItem
    )

    /**
     * Inicia reproducción explícitamente si hay contenido cargado.
     */
    fun play() {
        try {
            if (player.mediaItemCount == 0) {
                onIsPlayingChanged(false)
                return
            }
            player.play()
        } catch (e: Exception) {
            onPlayerError(e)
        }
    }
}