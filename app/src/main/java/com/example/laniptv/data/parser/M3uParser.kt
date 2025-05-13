package com.example.laniptv.data.parser

import com.example.laniptv.data.model.Channel
import com.example.laniptv.data.model.Category
import com.example.laniptv.data.model.Playlist
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

/**
 * Parser para archivos M3U8
 */
class M3uParser {

    /**
     * Parsea un archivo M3U8 desde un InputStream
     * @param inputStream InputStream del archivo M3U8
     * @return Playlist con categorías y canales
     */
    fun parse(inputStream: InputStream): Playlist {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val channels = mutableListOf<Channel>()

        var line: String?
        var currentChannelName: String? = null
        var currentLogoUrl: String? = null
        var currentGroupTitle: String? = null
        var currentChannelNumber: Int = 0
        var currentTvgId: String? = null

        // Leemos línea a línea
        while (reader.readLine().also { line = it } != null) {
            line = line?.trim()

            // Ignoramos líneas vacías y la primera línea (#EXTM3U)
            if (line.isNullOrEmpty() || line == "#EXTM3U") {
                continue
            }

            // Si es una línea de información de extinf
            if (line!!.startsWith("#EXTINF:")) {
                val extinf = line!!

                // Extraemos el nombre del canal
                currentChannelName = extractChannelName(extinf)

                // Extraemos atributos como logo, categoría, etc.
                currentLogoUrl = extractAttribute(extinf, "tvg-logo")
                currentGroupTitle = extractAttribute(extinf, "group-title") ?: "Sin categoría"
                currentChannelNumber = extractAttribute(extinf, "tvg-chno")?.toIntOrNull() ?: 0
                currentTvgId = extractAttribute(extinf, "tvg-id")
            }
            // Si es una URL de stream
            else if (!line!!.startsWith("#") && currentChannelName != null) {
                val streamUrl = line!!.trim()

                // Creamos el canal
                val channel = Channel(
                    id = UUID.randomUUID().toString(),
                    name = currentChannelName!!,
                    streamUrl = streamUrl,
                    logoUrl = currentLogoUrl,
                    categoryName = currentGroupTitle ?: "Sin categoría",
                    number = currentChannelNumber,
                    epgId = currentTvgId
                )

                channels.add(channel)

                // Reiniciamos variables para el siguiente canal
                currentChannelName = null
                currentLogoUrl = null
                currentGroupTitle = null
                currentChannelNumber = 0
                currentTvgId = null
            }
        }

        // Organizamos los canales por categorías
        val categoriesMap = channels.groupBy { it.categoryName }
        val categories = categoriesMap.map { (name, channelList) ->
            Category(name = name, channels = channelList)
        }.sortedBy { it.name }

        return Playlist(
            categories = categories,
            allChannels = channels.sortedBy { it.number }
        )
    }

    /**
     * Extrae el nombre del canal de la línea EXTINF
     */
    private fun extractChannelName(extinf: String): String {
        // Primero buscamos si hay un nombre después de una coma
        val commaIndex = extinf.lastIndexOf(",")
        if (commaIndex != -1 && commaIndex < extinf.length - 1) {
            return extinf.substring(commaIndex + 1).trim()
        }

        // Si no hay coma, usamos un ID genérico
        return "Canal sin nombre"
    }

    /**
     * Extrae un atributo específico de la línea EXTINF
     */
    private fun extractAttribute(extinf: String, attributeName: String): String? {
        val pattern = "$attributeName=\"([^\"]*)\""
        val regex = Regex(pattern)
        val matchResult = regex.find(extinf)

        return matchResult?.groupValues?.getOrNull(1)
    }
}