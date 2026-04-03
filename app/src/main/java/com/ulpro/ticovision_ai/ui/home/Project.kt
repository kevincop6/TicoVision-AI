package com.ulpro.ticovision_ai.ui.home

/**
 * Modelo de apoyo para UI.
 * Actualmente, no es obligatorio porque la pantalla trabaja
 * directamente con ProjectEntity, pero se mantiene por compatibilidad
 * futura si deseas mapear entidades a modelos visuales.
 */
data class Project(
    val id: Long,
    val title: String,
    val subtitle: String,
    val date: String,
    val duration: String,
    val size: String,
    val imageRes: Int,
    val projectDir: String
)