package com.ulpro.ticovision_ai.ui.home

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