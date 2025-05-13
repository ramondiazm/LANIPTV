package com.example.laniptv.data.repository

import com.example.laniptv.data.model.EpgChannel
import com.example.laniptv.data.model.EpgProgram
import com.example.laniptv.data.parser.EpgParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * Repositorio para obtener y gestionar los datos de la EPG
 */
class EpgRepository {
    private val epgParser = EpgParser()

    // Caché para los canales de la EPG
    private var cachedChannels: Map<String, EpgChannel> = emptyMap()

    // Caché para los programas por canal
    private var cachedPrograms: Map<String, List<EpgProgram>> = emptyMap()

    // Timestamp de la última actualización
    private var lastUpdateTimestamp: Long = 0

    // Tiempo máximo de caché en milisegundos (1 hora)
    private val MAX_CACHE_TIME = 60 * 60 * 1000

    /**
     * Obtiene los datos de la EPG desde una URL
     * @param url URL del archivo XMLTV
     * @param forceRefresh Si es true, fuerza la actualización de la caché
     * @return Map con los canales y sus programas
     */
    suspend fun getEpgFromUrl(url: String, forceRefresh: Boolean = false): Result<Pair<Map<String, EpgChannel>, Map<String, List<EpgProgram>>>> {
        return withContext(Dispatchers.IO) {
            try {
                // Si tenemos datos en caché y no han expirado, devolverlos
                val currentTime = System.currentTimeMillis()
                if (!forceRefresh &&
                    cachedChannels.isNotEmpty() &&
                    cachedPrograms.isNotEmpty() &&
                    (currentTime - lastUpdateTimestamp) < MAX_CACHE_TIME) {
                    return@withContext Result.success(Pair(cachedChannels, cachedPrograms))
                }

                // Descargar y parsear la EPG
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val programs = epgParser.parse(inputStream)

                    // Guardar en caché
                    cachedPrograms = programs
                    cachedChannels = epgParser.getChannels()
                    lastUpdateTimestamp = currentTime

                    inputStream.close()
                    connection.disconnect()

                    return@withContext Result.success(Pair(cachedChannels, cachedPrograms))
                } else {
                    return@withContext Result.failure(Exception("Error al obtener la EPG: Código $responseCode"))
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Obtiene los programas actuales para un conjunto de canales
     * @param channelIds Lista de IDs de canales
     * @return Map de ID de canal a programa actual
     */
    fun getCurrentPrograms(channelIds: List<String>): Map<String, EpgProgram?> {
        val result = mutableMapOf<String, EpgProgram?>()
        val currentTime = Date()

        channelIds.forEach { channelId ->
            val channelPrograms = cachedPrograms[channelId] ?: emptyList()

            // Buscar el programa actual (que esté en emisión en este momento)
            val currentProgram = channelPrograms.find { program ->
                currentTime.after(program.startTime) && currentTime.before(program.endTime)
            }

            result[channelId] = currentProgram
        }

        return result
    }

    /**
     * Obtiene el programa siguiente para un canal específico
     * @param channelId ID del canal
     * @param currentProgram Programa actual (opcional)
     * @return Programa siguiente o null si no hay más programas
     */
    fun getNextProgram(channelId: String, currentProgram: EpgProgram? = null): EpgProgram? {
        val channelPrograms = cachedPrograms[channelId] ?: return null

        // Si no tenemos programa actual, usamos la hora actual
        if (currentProgram == null) {
            val currentTime = Date()
            return channelPrograms.find { it.startTime.after(currentTime) }
        }

        // Buscar el programa que empiece después del actual
        return channelPrograms.find { it.startTime >= currentProgram.endTime }
    }

    /**
     * Obtiene los programas para un día específico
     * @param channelId ID del canal
     * @param date Fecha para la que queremos los programas
     * @return Lista de programas para ese día
     */
    fun getProgramsForDay(channelId: String, date: Date): List<EpgProgram> {
        val channelPrograms = cachedPrograms[channelId] ?: return emptyList()

        // Establecer la fecha a las 00:00:00
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.time

        // Establecer la fecha a las 23:59:59
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.time

        // Filtrar programas que se emiten durante ese día
        return channelPrograms.filter { program ->
            (program.startTime.after(startOfDay) && program.startTime.before(endOfDay)) ||
                    (program.endTime.after(startOfDay) && program.endTime.before(endOfDay)) ||
                    (program.startTime.before(startOfDay) && program.endTime.after(endOfDay))
        }
    }
}