package com.ulpro.ticovision_ai.ui.editor.controller

import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import com.ulpro.ticovision_ai.databinding.ActivityVideoEditorBinding
import com.ulpro.ticovision_ai.ui.editor.VideoEditorConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedList

/**
 * Controla los mensajes temporales de acciones recientes del editor.
 */
class EditorActionTagController(
    private val binding: ActivityVideoEditorBinding,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private val actionMessages = LinkedList<String>()
    private var actionTagsJob: Job? = null

    /**
     * Muestra un mensaje temporal en la zona de tags de acciones.
     */
    fun showTemporaryAction(message: String) {
        if (actionMessages.size >= 2) {
            actionMessages.removeFirst()
        }

        actionMessages.add(message)
        renderActionTags()

        actionTagsJob?.cancel()
        actionTagsJob = lifecycleScope.launch {
            binding.actionTagsContainer.visibility = View.VISIBLE
            delay(VideoEditorConfig.ACTION_TAGS_VISIBLE_MS)
            binding.actionTagsContainer.visibility = View.GONE
        }
    }

    /**
     * Limpia todos los mensajes visibles.
     */
    fun clear() {
        actionMessages.clear()
        renderActionTags()
        actionTagsJob?.cancel()
    }

    /**
     * Cancela corrutinas asociadas al controlador.
     */
    fun release() {
        actionTagsJob?.cancel()
    }

    /**
     * Renderiza hasta dos mensajes recientes.
     */
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
}