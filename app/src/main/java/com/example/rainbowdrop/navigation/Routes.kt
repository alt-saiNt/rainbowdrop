package com.example.rainbowdrop.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data class FilterSelection(val imageUri: String) : Route

    @Serializable
    data class MethodSelection(
        val imageUri: String,
        val filterType: String
    ) : Route

    @Serializable
    data class Editor(
        val imageUri: String,
        val filterType: String,
        val tool: String,
        val isMysteryMode: Boolean
    ) : Route
}
