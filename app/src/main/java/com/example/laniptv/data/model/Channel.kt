package com.example.laniptv.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Modelo para representar un canal IPTV
 */
@Parcelize
data class Channel(
    val id: String,             // ID único del canal
    val name: String,           // Nombre del canal
    val streamUrl: String,      // URL del stream (udp://@... o http://...)
    val logoUrl: String?,       // URL del logo
    val categoryName: String,   // Nombre de la categoría
    val number: Int = 0,        // Número del canal (opcional)
    val epgId: String? = null,  // ID para relacionar con EPG (para futura versión)
    val isFavorite: Boolean = false // Marcador de favorito (opcional)
) : Parcelable

/**
 * Modelo para representar una categoría de canales
 */
data class Category(
    val name: String,           // Nombre de la categoría
    val channels: List<Channel> = emptyList() // Lista de canales en esta categoría
)

/**
 * Modelo para representar la lista de reproducción M3U completa
 */
data class Playlist(
    val categories: List<Category> = emptyList(), // Lista de categorías
    val allChannels: List<Channel> = emptyList()  // Lista de todos los canales
)

/**
 * Estados de UI para la pantalla principal
 */
sealed class PlaylistUiState {
    object Loading : PlaylistUiState()
    data class Success(val playlist: Playlist) : PlaylistUiState()
    data class Error(val message: String) : PlaylistUiState()
}

/**
 * Estados para el reproductor
 */
sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    object Playing : PlayerState()
    object Paused : PlayerState()
    data class Error(val message: String) : PlayerState()
}