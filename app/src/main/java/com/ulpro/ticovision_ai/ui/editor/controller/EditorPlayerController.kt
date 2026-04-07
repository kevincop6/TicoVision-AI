package com.ulpro.ticovision_ai.ui.editor.controller

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity
import com.ulpro.ticovision_ai.databinding.ActivityVideoEditorBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controlador central de reproducción del editor.
 *
 * Encapsula la configuración de ExoPlayer, la preparación segura de playlist,
 * el control de mute, el seek global y la sincronización periódica con la UI.
 */
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

    init {
        binding.playerView.player = player
        binding.playerView.useController = false
        player.volume = 1f

        player.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onIsPlayingChanged(isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val safeIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                onMediaItemTransition(safeIndex)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onPlayerError(error)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                    events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                ) {
                    onPositionRelevantEvent()
                }
            }
        })
    }

    /**
     * Devuelve si el video está silenciado.
     */
    fun isMuted(): Boolean = isVideoMuted

    /**
     * Devuelve la cantidad actual de MediaItems cargados en el player.
     */
    fun mediaItemCount(): Int = player.mediaItemCount

    /**
     * Alterna entre mute y unmute.
     */
    fun toggleMute(): Boolean {
        isVideoMuted = !isVideoMuted
        player.volume = if (isVideoMuted) 0f else 1f
        return isVideoMuted
    }

    /**
     * Alterna entre play y pause con protección ante errores.
     */
    fun togglePlayPause() {
        try {
            if (player.mediaItemCount == 0) {
                onIsPlayingChanged(false)
                return
            }

            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        } catch (e: Exception) {
            onPlayerError(e)
        }
    }

    /**
     * Pausa la reproducción actual.
     */
    fun pause() {
        try {
            player.pause()
        } catch (e: Exception) {
            onPlayerError(e)
        }
    }

    /**
     * Detiene y limpia completamente la playlist del player.
     */
    fun stopAndClear() {
        try {
            player.pause()
            player.clearMediaItems()
            player.seekTo(0L)
        } catch (e: Exception) {
            onPlayerError(e)
        } finally {
            playbackUiJob?.cancel()
        }
    }

    /**
     * Libera todos los recursos del player.
     */
    fun release() {
        try {
            playbackUiJob?.cancel()
            player.release()
        } catch (e: Exception) {
            onPlayerError(e)
        }
    }

    /**
     * Genera una clave estable del item para comparaciones internas.
     */
    private fun buildItemKey(item: TimelineItemEntity): String {
        val source = item.sourceUri ?: "no_source"
        val type = item.type
        val duration = item.durationMs
        return "$type|$source|$duration"
    }

    /**
     * Valida si una URI es razonablemente reproducible.
     */
    private fun canReadUri(uri: Uri): Boolean {
        return try {
            when (uri.scheme?.lowercase()) {
                ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                }

                ContentResolver.SCHEME_FILE,
                "http",
                "https" -> true

                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Convierte la lista de TimelineItemEntity en elementos reproducibles válidos.
     */
    private fun buildPlayableEntries(
        timelineVideoItems: List<TimelineItemEntity>
    ): List<PlayableEntry> {
        return timelineVideoItems.mapNotNull { item ->
            val uriString = item.sourceUri ?: return@mapNotNull null
            val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@mapNotNull null

            if (!canReadUri(uri)) {
                return@mapNotNull null
            }

            PlayableEntry(
                sourceItem = item,
                sourceKey = buildItemKey(item),
                mediaItem = MediaItem.fromUri(uri)
            )
        }
    }

    /**
     * Prepara la playlist del timeline usando solamente clips de video válidos.
     */
    fun prepareTimelinePlaylist(
        timelineVideoItems: List<TimelineItemEntity>,
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean,
        isVideoMuted: Boolean,
        onVideoItemChanged: (TimelineItemEntity, Int) -> Unit,
        onUiSyncRequested: () -> Unit
    ) {
        try {
            if (timelineVideoItems.isEmpty()) {
                player.pause()
                player.clearMediaItems()
                onIsPlayingChanged(false)
                return
            }

            val playableEntries = buildPlayableEntries(timelineVideoItems)

            if (playableEntries.isEmpty()) {
                player.pause()
                player.clearMediaItems()
                onIsPlayingChanged(false)
                onPlayerError(
                    IllegalStateException("No hay elementos de video válidos para reproducir.")
                )
                return
            }

            val originalSafeIndex = startIndex.coerceIn(0, timelineVideoItems.lastIndex)
            val requestedItem = timelineVideoItems[originalSafeIndex]
            val requestedKey = buildItemKey(requestedItem)

            val mappedPlayableIndex = playableEntries.indexOfFirst {
                it.sourceKey == requestedKey
            }.takeIf { it >= 0 } ?: 0

            val mediaItems = playableEntries.map { it.mediaItem }
            val safePosition = startPositionMs.coerceAtLeast(0L)

            onVideoItemChanged(
                playableEntries[mappedPlayableIndex].sourceItem,
                mappedPlayableIndex
            )

            playbackUiJob?.cancel()

            player.pause()
            player.clearMediaItems()
            player.setMediaItems(mediaItems, mappedPlayableIndex, safePosition)
            player.prepare()
            player.playWhenReady = autoPlay

            this.isVideoMuted = isVideoMuted
            player.volume = if (isVideoMuted) 0f else 1f

            onUiSyncRequested()
            startPlaybackUiSync(onUiSyncRequested)
        } catch (e: Exception) {
            onPlayerError(e)
        }
    }

    /**
     * Calcula la posición global de reproducción sumando las duraciones de clips anteriores.
     */
    fun getGlobalPlaybackPosition(
        timelineVideoItems: List<TimelineItemEntity>,
        totalVideoDurationMs: Long
    ): Long {
        return try {
            if (timelineVideoItems.isEmpty()) return 0L
            if (player.mediaItemCount == 0) return 0L

            val safeCurrentIndex = player.currentMediaItemIndex
                .coerceIn(0, timelineVideoItems.lastIndex)

            var accumulated = 0L
            for (index in 0 until safeCurrentIndex) {
                accumulated += timelineVideoItems[index].durationMs.coerceAtLeast(0L)
            }

            val currentPosition = player.currentPosition.coerceAtLeast(0L)
            (accumulated + currentPosition).coerceAtMost(totalVideoDurationMs.coerceAtLeast(0L))
        } catch (e: Exception) {
            onPlayerError(e)
            0L
        }
    }

    /**
     * Busca la posición global dentro de la lista de clips de video y devuelve
     * el índice de clip y su posición local.
     */
    private fun findClipIndexAndLocalPosition(
        globalPositionMs: Long,
        timelineVideoItems: List<TimelineItemEntity>
    ): Pair<Int, Long> {
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
        return lastIndex to lastDuration
    }

    /**
     * Hace seek usando una posición global del timeline.
     */
    fun seekToGlobalPosition(
        globalPositionMs: Long,
        timelineVideoItems: List<TimelineItemEntity>,
        totalVideoDurationMs: Long,
        onVideoItemChanged: (TimelineItemEntity, Int) -> Unit,
        onUiSyncRequested: () -> Unit
    ) {
        try {
            if (timelineVideoItems.isEmpty()) return

            val safeGlobal = globalPositionMs.coerceIn(
                0L,
                totalVideoDurationMs.coerceAtLeast(0L)
            )

            val (targetIndex, localPositionMs) = findClipIndexAndLocalPosition(
                globalPositionMs = safeGlobal,
                timelineVideoItems = timelineVideoItems
            )

            val safeTargetIndex = targetIndex.coerceIn(0, timelineVideoItems.lastIndex)
            onVideoItemChanged(timelineVideoItems[safeTargetIndex], safeTargetIndex)

            if (player.mediaItemCount == 0) {
                prepareTimelinePlaylist(
                    timelineVideoItems = timelineVideoItems,
                    startIndex = safeTargetIndex,
                    startPositionMs = localPositionMs,
                    autoPlay = false,
                    isVideoMuted = isVideoMuted,
                    onVideoItemChanged = onVideoItemChanged,
                    onUiSyncRequested = onUiSyncRequested
                )
                return
            }

            if (safeTargetIndex >= player.mediaItemCount) {
                onPlayerError(
                    IndexOutOfBoundsException(
                        "Índice objetivo fuera de rango en player. " +
                                "targetIndex=$safeTargetIndex mediaItemCount=${player.mediaItemCount}"
                    )
                )
                return
            }

            if (player.currentMediaItemIndex != safeTargetIndex) {
                player.seekTo(safeTargetIndex, localPositionMs)
            } else {
                player.seekTo(localPositionMs)
            }

            onUiSyncRequested()
        } catch (e: Exception) {
            onPlayerError(e)
        }
    }

    /**
     * Inicia un loop de sincronización ligera para refrescar la UI del timeline.
     */
    private fun startPlaybackUiSync(onUiSyncRequested: () -> Unit) {
        playbackUiJob?.cancel()

        playbackUiJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    if (player.mediaItemCount > 0) {
                        onUiSyncRequested()
                    }
                } catch (e: Exception) {
                    onPlayerError(e)
                }
                delay(50L)
            }
        }
    }

    /**
     * Estructura interna que mantiene el vínculo entre MediaItem y TimelineItemEntity.
     */
    private data class PlayableEntry(
        val sourceItem: TimelineItemEntity,
        val sourceKey: String,
        val mediaItem: MediaItem
    )
}