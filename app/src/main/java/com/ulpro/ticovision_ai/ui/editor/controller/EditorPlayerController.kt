package com.ulpro.ticovision_ai.ui.editor.controller

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
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

/**
 * Controlador central de reproducción del editor.
 *
 * Esta versión corrige los problemas de desalineación entre el timeline visual
 * y la playlist real del reproductor.
 *
 * Soporta:
 * - Clips de video.
 * - Clips de imagen convertidos a MediaItem con duración fija.
 * - Mapeo estable entre items del timeline y items reproducibles.
 * - Manejo explícito de errores de Media3.
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

    /**
     * Lista real y reproducible que está cargada actualmente en el player.
     *
     * Debe ser la única fuente de verdad para posiciones, índices y seeks.
     */
    private var currentPlayableEntries: List<PlayableEntry> = emptyList()

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
     * Devuelve la lista reproducible actual.
     */
    fun getCurrentPlayableTimelineItems(): List<TimelineItemEntity> {
        return currentPlayableEntries.map { it.sourceItem }
    }

    /**
     * Devuelve el item reproducible actual o null.
     */
    fun getCurrentPlayableItem(): TimelineItemEntity? {
        if (currentPlayableEntries.isEmpty()) return null
        val index = player.currentMediaItemIndex.coerceIn(0, currentPlayableEntries.lastIndex)
        return currentPlayableEntries.getOrNull(index)?.sourceItem
    }

    /**
     * Alterna entre mute y unmute.
     */
    fun toggleMute(): Boolean {
        isVideoMuted = !isVideoMuted
        player.volume = if (isVideoMuted) 0f else 1f
        return isVideoMuted
    }

    /**
     * Alterna entre play y pause.
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
            currentPlayableEntries = emptyList()
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
            currentPlayableEntries = emptyList()
            player.release()
        } catch (e: Exception) {
            onPlayerError(e)
        }
    }

    /**
     * Genera una clave estable para identificar un clip dentro del timeline.
     */
    private fun buildItemKey(item: TimelineItemEntity): String {
        val source = item.sourceUri ?: "no_source"
        val type = item.type ?: "unknown"
        val duration = item.durationMs
        return "$type|$source|$duration"
    }

    /**
     * Verifica si una URI puede abrirse razonablemente.
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
     * Construye la lista reproducible real a partir del timeline completo.
     *
     * Reglas:
     * - video -> MediaItem.fromUri
     * - image -> MediaItem.Builder().setImageDurationMs(...)
     * - otros tipos -> se ignoran por ahora
     */
    @OptIn(UnstableApi::class)
    private fun buildPlayableEntries(
        timelineItems: List<TimelineItemEntity>
    ): List<PlayableEntry> {
        return timelineItems.mapNotNull { item ->
            val sourceUri = item.sourceUri ?: return@mapNotNull null
            val parsedUri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return@mapNotNull null

            if (!canReadUri(parsedUri)) {
                return@mapNotNull null
            }

            val normalizedType = item.type?.lowercase() ?: return@mapNotNull null

            val mediaItem = when (normalizedType) {
                "video" -> {
                    MediaItem.fromUri(parsedUri)
                }

                "image" -> {
                    MediaItem.Builder()
                        .setUri(parsedUri)
                        .setImageDurationMs(item.durationMs.coerceAtLeast(1L))
                        .build()
                }

                else -> return@mapNotNull null
            }

            PlayableEntry(
                sourceItem = item,
                sourceKey = buildItemKey(item),
                mediaItem = mediaItem
            )
        }
    }

    /**
     * Prepara la playlist completa del timeline visual.
     *
     * IMPORTANTE:
     * Esta función debe recibir la lista visual completa del timeline
     * (videos + imágenes), no solo videos.
     */
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
                onPlayerError(
                    IllegalStateException("No hay clips válidos del timeline para reproducir.")
                )
                return
            }

            val safeStartIndex = startIndex.coerceIn(0, timelineItems.lastIndex)
            val requestedItem = timelineItems[safeStartIndex]
            val requestedKey = buildItemKey(requestedItem)

            val mappedPlayableIndex = playableEntries.indexOfFirst {
                it.sourceKey == requestedKey
            }.takeIf { it >= 0 } ?: 0

            val safePosition = startPositionMs.coerceAtLeast(0L)
            val mediaItems = playableEntries.map { it.mediaItem }

            playbackUiJob?.cancel()

            player.pause()
            player.clearMediaItems()

            currentPlayableEntries = playableEntries

            player.setMediaItems(mediaItems, mappedPlayableIndex, safePosition)
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
            onPlayerError(e)
        }
    }

    /**
     * Calcula la posición global actual usando la lista realmente cargada en el player.
     */
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

            val currentPosition = player.currentPosition.coerceAtLeast(0L)
            accumulated + currentPosition
        } catch (e: Exception) {
            onPlayerError(e)
            0L
        }
    }

    /**
     * Busca el índice de clip y la posición local usando la lista reproducible real.
     */
    private fun findClipIndexAndLocalPosition(
        globalPositionMs: Long
    ): Pair<Int, Long> {
        if (currentPlayableEntries.isEmpty()) return 0 to 0L

        var accumulated = 0L

        currentPlayableEntries.forEachIndexed { index, entry ->
            val duration = entry.sourceItem.durationMs.coerceAtLeast(0L)
            val clipEnd = accumulated + duration

            if (globalPositionMs < clipEnd) {
                val localPosition = (globalPositionMs - accumulated).coerceAtLeast(0L)
                return index to localPosition
            }

            accumulated = clipEnd
        }

        val lastIndex = currentPlayableEntries.lastIndex
        val lastDuration = currentPlayableEntries[lastIndex].sourceItem.durationMs.coerceAtLeast(0L)
        return lastIndex to lastDuration
    }

    /**
     * Hace seek usando la lista reproducible real del player.
     */
    fun seekToGlobalPosition(
        globalPositionMs: Long,
        onTimelineItemChanged: (TimelineItemEntity, Int) -> Unit,
        onUiSyncRequested: () -> Unit
    ) {
        try {
            if (currentPlayableEntries.isEmpty()) return
            if (player.mediaItemCount == 0) return

            val totalTimelineDuration = currentPlayableEntries.sumOf {
                it.sourceItem.durationMs.coerceAtLeast(0L)
            }

            val safeGlobal = globalPositionMs.coerceIn(
                0L,
                totalTimelineDuration.coerceAtLeast(0L)
            )

            val (targetIndex, localPositionMs) = findClipIndexAndLocalPosition(safeGlobal)
            val safeTargetIndex = targetIndex.coerceIn(0, currentPlayableEntries.lastIndex)

            if (safeTargetIndex >= player.mediaItemCount) {
                onPlayerError(
                    IndexOutOfBoundsException(
                        "Índice fuera de rango. targetIndex=$safeTargetIndex mediaItemCount=${player.mediaItemCount}"
                    )
                )
                return
            }

            onTimelineItemChanged(
                currentPlayableEntries[safeTargetIndex].sourceItem,
                safeTargetIndex
            )

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
     * Devuelve la duración total reproducible actual.
     */
    fun getCurrentPlayableDurationMs(): Long {
        return currentPlayableEntries.sumOf { it.sourceItem.durationMs.coerceAtLeast(0L) }
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
     * Estructura interna que mantiene el vínculo entre el timeline y el MediaItem real.
     */
    private data class PlayableEntry(
        val sourceItem: TimelineItemEntity,
        val sourceKey: String,
        val mediaItem: MediaItem
    )
}