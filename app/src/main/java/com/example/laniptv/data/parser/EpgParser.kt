package com.example.laniptv.data.parser

import android.util.Log
import com.example.laniptv.data.model.EpgChannel
import com.example.laniptv.data.model.EpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parser para archivos XMLTV (guía de programación electrónica)
 */
class EpgParser {
    private val TAG = "EpgParser"

    // Canales disponibles en la EPG
    private val channels = mutableMapOf<String, EpgChannel>()

    // Programas por canal
    private val programsByChannel = mutableMapOf<String, MutableList<EpgProgram>>()

    // Formato de fecha usado en XMLTV
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Parsea un archivo XMLTV desde un InputStream
     * @param inputStream InputStream del archivo XMLTV
     * @return Map de canales con sus programas
     */
    suspend fun parse(inputStream: InputStream): Map<String, List<EpgProgram>> {
        return withContext(Dispatchers.IO) {
            try {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                val parser = factory.newPullParser()
                parser.setInput(inputStream, null)

                var eventType = parser.eventType
                var currentTag = ""

                // Variables temporales para almacenar datos durante el parsing
                var currentChannelId = ""
                var currentChannelName = ""
                var currentChannelIcon = ""

                var currentProgramChannelId = ""
                var currentProgramStart: Date? = null
                var currentProgramStop: Date? = null
                var currentProgramTitle = ""
                var currentProgramDescription = ""

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name

                            // Parsing de los nodos de canal
                            when (currentTag) {
                                "channel" -> {
                                    currentChannelId = parser.getAttributeValue(null, "id") ?: ""
                                    currentChannelName = ""
                                    currentChannelIcon = ""
                                }
                                "programme" -> {
                                    currentProgramChannelId = parser.getAttributeValue(null, "channel") ?: ""

                                    // Parsear fechas de inicio y fin
                                    val startStr = parser.getAttributeValue(null, "start")
                                    val stopStr = parser.getAttributeValue(null, "stop")

                                    currentProgramStart = if (startStr != null) parseXmltvDate(startStr) else null
                                    currentProgramStop = if (stopStr != null) parseXmltvDate(stopStr) else null

                                    currentProgramTitle = ""
                                    currentProgramDescription = ""
                                }
                            }
                        }

                        XmlPullParser.TEXT -> {
                            val text = parser.text

                            // Dependiendo de la etiqueta actual, guardamos el texto
                            when (currentTag) {
                                "display-name" -> currentChannelName = text
                                "title" -> currentProgramTitle = text
                                "desc" -> currentProgramDescription = text
                                "icon" -> {
                                    if (parser.getAttributeValue(null, "src") != null) {
                                        currentChannelIcon = parser.getAttributeValue(null, "src")
                                    }
                                }
                            }
                        }

                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "channel" -> {
                                    // Guardar canal al terminar su nodo
                                    if (currentChannelId.isNotEmpty()) {
                                        val channel = EpgChannel(
                                            id = currentChannelId,
                                            name = currentChannelName,
                                            iconUrl = currentChannelIcon
                                        )
                                        channels[currentChannelId] = channel
                                    }
                                }
                                "programme" -> {
                                    // Guardar programa al terminar su nodo
                                    if (currentProgramChannelId.isNotEmpty() &&
                                        currentProgramStart != null &&
                                        currentProgramStop != null) {

                                        // Ya que hemos verificado que no son nulos
                                        val startTime = currentProgramStart
                                        val endTime = currentProgramStop

                                        val program = EpgProgram(
                                            channelId = currentProgramChannelId,
                                            title = currentProgramTitle,
                                            description = currentProgramDescription,
                                            startTime = startTime,
                                            endTime = endTime
                                        )

                                        // Añadir programa a la lista de ese canal
                                        if (!programsByChannel.containsKey(currentProgramChannelId)) {
                                            programsByChannel[currentProgramChannelId] = mutableListOf()
                                        }

                                        programsByChannel[currentProgramChannelId]?.add(program)
                                    }
                                }
                            }

                            currentTag = ""
                        }
                    }

                    eventType = parser.next()
                }

                // SOLUCIÓN AL ERROR: Ordenar programas de manera segura
                val result = mutableMapOf<String, List<EpgProgram>>()

                // Hacer una copia de las claves para evitar modificación concurrente
                val channelIds = programsByChannel.keys.toList()

                // Procesar cada canal por separado
                for (channelId in channelIds) {
                    val programs = programsByChannel[channelId]
                    if (programs != null) {
                        // Crear una copia de la lista y ordenarla
                        val sortedPrograms = ArrayList(programs)
                        sortedPrograms.sortWith { a, b -> a.startTime.compareTo(b.startTime) }

                        // Guardar la lista ordenada en el resultado final
                        result[channelId] = sortedPrograms
                    }
                }

                return@withContext result

            } catch (e: Exception) {
                Log.e(TAG, "Error parseando EPG: ${e.message}", e)
                return@withContext emptyMap<String, List<EpgProgram>>()
            }
        }
    }

    /**
     * Obtiene todos los canales disponibles en la EPG
     */
    fun getChannels(): Map<String, EpgChannel> {
        return channels
    }

    /**
     * Convierte una fecha en formato XMLTV a objeto Date
     */
    private fun parseXmltvDate(dateStr: String): Date? {
        return try {
            // Formato típico XMLTV: 20210725123000 +0000
            // Añadimos espacio antes de la zona horaria si no lo tiene
            val formattedDateStr = if (dateStr.contains(" ")) {
                dateStr
            } else {
                val timezone = dateStr.substring(dateStr.length - 5)
                val date = dateStr.substring(0, dateStr.length - 5)
                "$date $timezone"
            }

            dateFormat.parse(formattedDateStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando fecha XMLTV: $dateStr", e)
            null
        }
    }
}