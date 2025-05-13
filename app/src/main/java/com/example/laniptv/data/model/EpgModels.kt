package com.example.laniptv.data.model

import java.util.Date

/**
 * Modelo para representar un canal en el EPG
 */
data class EpgChannel(
    val id: String,           // ID único del canal (usado en XMLTV)
    val name: String,         // Nombre del canal
    val iconUrl: String? = null // URL del icono del canal
)

/**
 * Modelo para representar el estado actual del EPG
 */
sealed class EpgState {
    object Loading : EpgState()
    data class Success(val channels: Map<String, EpgChannel>, val programs: Map<String, List<EpgProgram>>) : EpgState()
    data class Error(val message: String) : EpgState()
}