package com.ulpro.ticovision_ai.ui.editor.timeline

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.ulpro.ticovision_ai.R
import com.ulpro.ticovision_ai.data.local.entity.TimelineItemEntity
import com.ulpro.ticovision_ai.databinding.ActivityVideoEditorBinding
import com.ulpro.ticovision_ai.ui.editor.VideoEditorConfig
import com.ulpro.ticovision_ai.ui.editor.util.dp
import com.ulpro.ticovision_ai.ui.editor.util.timelineKey
import com.ulpro.ticovisionai.ui.editor.timeline.TimelineThumbnailProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Renderizador optimizado del timeline.
 *
 * Objetivos:
 * - Evitar reconstrucciones completas del árbol de vistas cuando no es necesario.
 * - Reutilizar holders por clave estable del clip.
 * - Reducir carga de miniaturas por clip.
 * - Actualizar selección visual sin repintar todo el timeline.
 */
class TimelineRenderer(
    private val context: Context,
    private val binding: ActivityVideoEditorBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val thumbnailProvider: TimelineThumbnailProvider,
    private val onVideoItemSelected: (TimelineItemEntity, Int) -> Unit,
    private val onImageItemSelected: (TimelineItemEntity, Int) -> Unit
) {

    private val clipViewCache = linkedMapOf<String, ClipViewHolder>()
    private var lastRenderedSignature: String = ""
    private var lastSelectedKey: String? = null
    private var lastSelectedIndex: Int = -1
    private var audioTrackSignature: String = ""

    /**
     * Renderiza clips visuales y pista de audio externa.
     */
    fun renderTimelineItems(
        items: List<TimelineItemEntity>,
        currentTimelineItem: TimelineItemEntity?,
        currentPlaylistIndex: Int
    ) {
        val visualTimelineItems = items.filter { it.type == TYPE_VIDEO || it.type == TYPE_IMAGE }
        val selectedKey = currentTimelineItem?.timelineKey()
        val structuralSignature = buildStructuralSignature(visualTimelineItems)

        if (visualTimelineItems.isEmpty()) {
            clearVisualTrack()
            updateTimelineContentWidth(0)
            renderExternalAudioTrackIfNeeded(items)
            lastRenderedSignature = ""
            lastSelectedKey = selectedKey
            lastSelectedIndex = currentPlaylistIndex
            return
        }

        if (structuralSignature != lastRenderedSignature) {
            rebuildVisualTrack(visualTimelineItems, currentTimelineItem, currentPlaylistIndex)
            lastRenderedSignature = structuralSignature
        } else {
            updateSelectionOnly(visualTimelineItems, currentTimelineItem, currentPlaylistIndex)
        }

        binding.timelineVideoTrack.post {
            updateTimelineContentWidth(binding.timelineVideoTrack.width)
        }

        renderExternalAudioTrackIfNeeded(items)
        lastSelectedKey = selectedKey
        lastSelectedIndex = currentPlaylistIndex
    }

    /**
     * Actualiza solo selección sin reconstruir toda la pista.
     */
    fun updateSelectionState(
        items: List<TimelineItemEntity>,
        currentTimelineItem: TimelineItemEntity?,
        currentPlaylistIndex: Int
    ) {
        val visualTimelineItems = items.filter { it.type == TYPE_VIDEO || it.type == TYPE_IMAGE }
        if (visualTimelineItems.isEmpty()) return

        updateSelectionOnly(visualTimelineItems, currentTimelineItem, currentPlaylistIndex)
    }

    /**
     * Limpia recursos visuales y cancela cargas pendientes.
     */
    fun release() {
        clipViewCache.values.forEach { holder ->
            holder.thumbnailJobs.forEach { it.cancel() }
            holder.thumbnailJobs.clear()
            holder.container.setOnClickListener(null)
        }
        clipViewCache.clear()
        binding.timelineVideoTrack.removeAllViews()
        binding.audioWaveTrack.removeAllViews()
        binding.externalAudioTrackContainer.visibility = View.GONE
        lastRenderedSignature = ""
        lastSelectedKey = null
        lastSelectedIndex = -1
        audioTrackSignature = ""
    }

    private fun rebuildVisualTrack(
        items: List<TimelineItemEntity>,
        currentTimelineItem: TimelineItemEntity?,
        currentPlaylistIndex: Int
    ) {
        val newCache = linkedMapOf<String, ClipViewHolder>()

        binding.timelineVideoTrack.removeAllViews()

        items.forEachIndexed { index, item ->
            val key = item.timelineKey()
            val isSelected = isItemSelected(item, index, currentTimelineItem, currentPlaylistIndex)
            val existing = clipViewCache.remove(key)

            val holder = if (existing != null) {
                bindExistingHolder(existing, item, index, isSelected)
                existing
            } else {
                createHolder(item, index, isSelected)
            }

            binding.timelineVideoTrack.addView(holder.container)
            newCache[key] = holder
        }

        clipViewCache.values.forEach { orphan ->
            orphan.thumbnailJobs.forEach { it.cancel() }
            orphan.thumbnailJobs.clear()
            orphan.container.setOnClickListener(null)
        }

        clipViewCache.clear()
        clipViewCache.putAll(newCache)
    }

    private fun bindExistingHolder(
        holder: ClipViewHolder,
        item: TimelineItemEntity,
        index: Int,
        isSelected: Boolean
    ) {
        val itemWidth = calculateTimelineItemWidthPx(item)
        val shouldRebuildFrames =
            holder.itemWidthPx != itemWidth ||
                    holder.type != item.type ||
                    holder.sourceUri != item.sourceUri ||
                    holder.durationMs != item.durationMs

        holder.item = item
        holder.index = index
        holder.itemWidthPx = itemWidth
        holder.type = item.type.orEmpty()
        holder.sourceUri = item.sourceUri
        holder.durationMs = item.durationMs

        val params = (holder.container.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(itemWidth, ViewGroup.LayoutParams.MATCH_PARENT)

        params.width = itemWidth
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        params.marginEnd = 2.dp(context)
        holder.container.layoutParams = params

        holder.container.setOnClickListener {
            dispatchItemSelection(item, index)
        }

        updateHolderSelection(holder, isSelected)

        if (shouldRebuildFrames) {
            holder.framesStrip.removeAllViews()
            holder.thumbnailJobs.forEach { it.cancel() }
            holder.thumbnailJobs.clear()

            when (item.type) {
                TYPE_VIDEO -> populateVideoFrames(holder, item, itemWidth)
                TYPE_IMAGE -> populateImageFrames(holder, item, itemWidth)
            }
        }
    }

    private fun createHolder(
        item: TimelineItemEntity,
        index: Int,
        isSelected: Boolean
    ): ClipViewHolder {
        val itemWidth = calculateTimelineItemWidthPx(item)

        val container = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                itemWidth,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = 2.dp(context)
            }
            setBackgroundColor(
                if (item.type == TYPE_VIDEO) 0xFF2A2A2A.toInt() else 0xFF2E2E2E.toInt()
            )
            clipToOutline = true
        }

        val framesStrip = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        val overlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val borderView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        container.addView(framesStrip)
        container.addView(overlay)
        container.addView(borderView)

        val holder = ClipViewHolder(
            key = item.timelineKey(),
            item = item,
            index = index,
            type = item.type.orEmpty(),
            sourceUri = item.sourceUri,
            durationMs = item.durationMs,
            itemWidthPx = itemWidth,
            container = container,
            framesStrip = framesStrip,
            overlay = overlay,
            borderView = borderView
        )

        container.setOnClickListener {
            dispatchItemSelection(item, index)
        }

        updateHolderSelection(holder, isSelected)

        when (item.type) {
            TYPE_VIDEO -> populateVideoFrames(holder, item, itemWidth)
            TYPE_IMAGE -> populateImageFrames(holder, item, itemWidth)
        }

        return holder
    }

    private fun updateSelectionOnly(
        items: List<TimelineItemEntity>,
        currentTimelineItem: TimelineItemEntity?,
        currentPlaylistIndex: Int
    ) {
        val currentSelectedKey = currentTimelineItem?.timelineKey()

        if (currentSelectedKey == lastSelectedKey && currentPlaylistIndex == lastSelectedIndex) {
            return
        }

        items.forEachIndexed { index, item ->
            val key = item.timelineKey()
            val holder = clipViewCache[key] ?: return@forEachIndexed
            val isSelected = isItemSelected(item, index, currentTimelineItem, currentPlaylistIndex)
            updateHolderSelection(holder, isSelected)
            holder.index = index
            holder.item = item
            holder.container.setOnClickListener {
                dispatchItemSelection(item, index)
            }
        }
    }

    private fun updateHolderSelection(holder: ClipViewHolder, isSelected: Boolean) {
        holder.overlay.setBackgroundColor(
            if (isSelected) 0x22FFFFFF else {
                if (holder.type == TYPE_IMAGE) 0x14000000 else 0x08000000
            }
        )

        holder.borderView.background = ContextCompat.getDrawable(
            context,
            if (isSelected) R.drawable.bg_selected_timeline_item
            else R.drawable.bg_normal_timeline_item
        )
    }

    private fun populateImageFrames(
        holder: ClipViewHolder,
        item: TimelineItemEntity,
        itemWidth: Int
    ) {
        val frameCount = calculateImageFrameCount(itemWidth)
        val uri = item.sourceUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val imageTargets = ArrayList<ImageView>(frameCount)

        repeat(frameCount) { frameIndex ->
            val imageView = createFrameImageView(
                frameIndex = frameIndex,
                frameCount = frameCount,
                backgroundColor = 0xFF404040.toInt()
            )
            holder.framesStrip.addView(imageView)
            imageTargets.add(imageView)
        }

        if (uri == null || !thumbnailProvider.canReadUri(uri)) return

        val requestKey = "${item.timelineKey()}|image-strip|$frameCount"
        holder.framesStrip.tag = requestKey

        val job = lifecycleScope.launch {
            val bitmap = thumbnailProvider.getImageThumbnail(
                uri = uri,
                targetWidth = 96,
                targetHeight = 96
            )

            if (holder.framesStrip.tag != requestKey || bitmap == null) return@launch

            imageTargets.forEach { target ->
                target.setImageBitmap(bitmap)
            }
        }

        holder.thumbnailJobs.add(job)
    }

    private fun populateVideoFrames(
        holder: ClipViewHolder,
        item: TimelineItemEntity,
        itemWidth: Int
    ) {
        val frameCount = calculateVideoFrameCount(itemWidth)
        val uri = item.sourceUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val durationMs = item.durationMs.coerceAtLeast(1_000L)

        repeat(frameCount) { frameIndex ->
            val imageView = createFrameImageView(
                frameIndex = frameIndex,
                frameCount = frameCount,
                backgroundColor = 0xFF353535.toInt()
            )
            holder.framesStrip.addView(imageView)

            if (uri == null || !thumbnailProvider.canReadUri(uri)) return@repeat

            val progress = if (frameCount == 1) 0f else frameIndex.toFloat() / (frameCount - 1).toFloat()
            val frameTimeUs = (durationMs * progress * 1000f).toLong().coerceAtLeast(0L)
            val requestKey = "${item.timelineKey()}|video|$frameIndex|$frameCount"
            imageView.tag = requestKey

            val job = lifecycleScope.launch {
                val bitmap = thumbnailProvider.getVideoFrameAtTime(
                    uri = uri,
                    timeUs = frameTimeUs,
                    targetWidth = 96,
                    targetHeight = 96
                )

                if (imageView.tag == requestKey && bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                }
            }

            holder.thumbnailJobs.add(job)
        }
    }

    private fun createFrameImageView(
        frameIndex: Int,
        frameCount: Int,
        backgroundColor: Int
    ): ImageView {
        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginEnd = if (frameIndex == frameCount - 1) 0 else 1.dp(context)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(backgroundColor)
            setImageDrawable(null)
        }
    }

    private fun calculateTimelineItemWidthPx(item: TimelineItemEntity): Int {
        val minWidth = VideoEditorConfig.TIMELINE_MIN_ITEM_WIDTH_DP.dp(context)
        val pxPerSecond = VideoEditorConfig.TIMELINE_PX_PER_SECOND_DP.dp(context)
        val durationSeconds = item.durationMs.coerceAtLeast(1_000L) / 1000f
        val computedWidth = (durationSeconds * pxPerSecond).toInt()
        return computedWidth.coerceAtLeast(minWidth)
    }

    private fun calculateImageFrameCount(itemWidth: Int): Int {
        val frameWidth = VideoEditorConfig.TIMELINE_IMAGE_FRAME_WIDTH_DP.dp(context).coerceAtLeast(1)
        val rawCount = (itemWidth / frameWidth).coerceAtLeast(1)
        return rawCount.coerceIn(1, 4)
    }

    private fun calculateVideoFrameCount(itemWidth: Int): Int {
        val frameWidth = VideoEditorConfig.TIMELINE_VIDEO_FRAME_WIDTH_DP.dp(context).coerceAtLeast(1)
        val rawCount = (itemWidth / frameWidth).coerceAtLeast(1)
        return rawCount.coerceIn(1, 6)
    }

    private fun updateTimelineContentWidth(trackWidthPx: Int) {
        val minWidthPx = VideoEditorConfig.TIMELINE_MIN_CONTENT_WIDTH_DP.dp(context)
        val finalWidth = maxOf(trackWidthPx, minWidthPx)

        binding.timelineContent.layoutParams = binding.timelineContent.layoutParams.apply {
            width = finalWidth
        }

        binding.timelineRuler.layoutParams = binding.timelineRuler.layoutParams.apply {
            width = finalWidth
        }

        binding.audioWaveTrack.layoutParams = binding.audioWaveTrack.layoutParams.apply {
            width = finalWidth
        }

        binding.timelineContent.requestLayout()
        binding.timelineRuler.requestLayout()
        binding.audioWaveTrack.requestLayout()
    }

    private fun renderExternalAudioTrackIfNeeded(items: List<TimelineItemEntity>) {
        val externalAudioItems = items.filter { it.type == TYPE_AUDIO }
        val newSignature = externalAudioItems.joinToString(separator = "|") {
            "${it.timelineKey()}_${it.sourceUri.orEmpty()}"
        }

        if (externalAudioItems.isEmpty()) {
            if (audioTrackSignature.isNotEmpty()) {
                binding.externalAudioTrackContainer.visibility = View.GONE
                binding.audioWaveTrack.removeAllViews()
                binding.tvAudioTrack.text = ""
                audioTrackSignature = ""
            }
            return
        }

        if (newSignature == audioTrackSignature) return

        audioTrackSignature = newSignature
        binding.externalAudioTrackContainer.visibility = View.VISIBLE
        binding.audioWaveTrack.removeAllViews()

        val firstAudioItem = externalAudioItems.first()
        val label = firstAudioItem.sourceUri
            ?.substringAfterLast("/")
            ?.substringBefore("?")
            ?.ifBlank { "Audio externo" }
            ?: "Audio externo"

        binding.tvAudioTrack.text = "Audio: $label"

        val barCount = 32
        repeat(barCount) { barIndex ->
            binding.audioWaveTrack.addView(createAudioWaveBar(barIndex))
        }
    }

    private fun createAudioWaveBar(index: Int): View {
        val heightsDp = listOf(10, 16, 8, 20, 12, 24, 11, 18, 9, 22, 14, 19)
        val barHeight = heightsDp[index % heightsDp.size].dp(context)

        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                3.dp(context),
                barHeight
            ).apply {
                marginEnd = 3.dp(context)
            }
            setBackgroundColor(0xFFB7D9B0.toInt())
        }
    }

    private fun isItemSelected(
        item: TimelineItemEntity,
        index: Int,
        currentTimelineItem: TimelineItemEntity?,
        currentPlaylistIndex: Int
    ): Boolean {
        val sameByKey = currentTimelineItem?.timelineKey() == item.timelineKey()
        return sameByKey || currentPlaylistIndex == index
    }

    private fun buildStructuralSignature(items: List<TimelineItemEntity>): String {
        return items.joinToString(separator = "|") { item ->
            "${item.timelineKey()}_${item.type}_${item.durationMs}_${calculateTimelineItemWidthPx(item)}"
        }
    }

    private fun dispatchItemSelection(item: TimelineItemEntity, index: Int) {
        when (item.type) {
            TYPE_VIDEO -> onVideoItemSelected(item, index)
            TYPE_IMAGE -> onImageItemSelected(item, index)
        }
    }

    private fun clearVisualTrack() {
        clipViewCache.values.forEach { holder ->
            holder.thumbnailJobs.forEach { it.cancel() }
            holder.thumbnailJobs.clear()
            holder.container.setOnClickListener(null)
        }
        clipViewCache.clear()
        binding.timelineVideoTrack.removeAllViews()
    }

    private data class ClipViewHolder(
        val key: String,
        var item: TimelineItemEntity,
        var index: Int,
        var type: String,
        var sourceUri: String?,
        var durationMs: Long,
        var itemWidthPx: Int,
        val container: FrameLayout,
        val framesStrip: LinearLayout,
        val overlay: View,
        val borderView: View,
        val thumbnailJobs: MutableList<Job> = mutableListOf()
    )

    private companion object {
        const val TYPE_VIDEO = "video"
        const val TYPE_IMAGE = "image"
        const val TYPE_AUDIO = "audio"
    }
}