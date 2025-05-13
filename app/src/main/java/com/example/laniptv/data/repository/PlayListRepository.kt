package com.example.laniptv.data.repository

import com.example.laniptv.data.model.Category
import com.example.laniptv.data.model.Playlist
import com.example.laniptv.data.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Repositorio para obtener y gestionar la lista de reproducción
 */
class PlaylistRepository {
    private val m3uParser = M3uParser()

    /**
     * Obtiene la lista de reproducción desde una URL
     * @param url URL donde se encuentra el archivo M3U8
     * @return Playlist con los canales organizados
     */
    suspend fun getPlaylistFromUrl(url: String): Playlist {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    var playlist = parsePlaylist(inputStream)

                    // Agregar categoría "Todos" con todos los canales
                    playlist = addAllChannelsCategory(playlist)

                    inputStream.close()
                    connection.disconnect()
                    return@withContext playlist
                } else {
                    throw Exception("Error al obtener la lista: Código $responseCode")
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    /**
     * Parsea la lista de reproducción desde un InputStream
     */
    private fun parsePlaylist(inputStream: InputStream): Playlist {
        return m3uParser.parse(inputStream)
    }

    /**
     * Agrega la categoría "Todos" con todos los canales
     */
    private fun addAllChannelsCategory(playlist: Playlist): Playlist {
        val allCategory = Category(
            name = "Todos",
            channels = playlist.allChannels
        )

        // Agregar al principio de la lista de categorías
        val updatedCategories = listOf(allCategory) + playlist.categories

        return Playlist(
            categories = updatedCategories,
            allChannels = playlist.allChannels
        )
    }

    /**
     * Guarda un canal como favorito (para futura implementación)
     */
    suspend fun toggleFavorite(channelId: String, isFavorite: Boolean): Result<Boolean> {
        // Implementación para futura versión
        return Result.success(isFavorite)
    }
}