package com.example.laniptv.data.model

import java.util.Date

/**
 * Modelo de datos para programas de la EPG en la aplicación IPTV
 */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Date,
    val endTime: Date
)