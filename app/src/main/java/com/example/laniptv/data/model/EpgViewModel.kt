package com.example.laniptv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.laniptv.LanIPTVApplication
import com.example.laniptv.data.model.Channel
import com.example.laniptv.data.model.EpgChannel
import com.example.laniptv.data.model.EpgProgram
import com.example.laniptv.data.model.EpgState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Date

/**
 * ViewModel para la gestión de la EPG
 */
class EpgViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<LanIPTVApplication>()
    private val epgRepository = app.epgRepository
    private val appConfig = app.appConfig

    // Estado de la EPG
    private val _epgState = MutableStateFlow<EpgState>(EpgState.Loading)
    val epgState: StateFlow<EpgState> = _epgState.asStateFlow()

    // Canal seleccionado actualmente
    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelId: StateFlow<String?> = _selectedChannelId.asStateFlow()

    // Fecha seleccionada para la programación
    private val _selectedDate = MutableStateFlow(Date())
    val selectedDate: StateFlow<Date> = _selectedDate.asStateFlow()

    // Programas para el día seleccionado
    private val _programsForSelectedDay = MutableStateFlow<List<EpgProgram>>(emptyList())
    val programsForSelectedDay: StateFlow<List<EpgProgram>> = _programsForSelectedDay.asStateFlow()

    // Programas actuales para todos los canales
    private val _currentPrograms = MutableStateFlow<Map<String, EpgProgram?>>(emptyMap())
    val currentPrograms: StateFlow<Map<String, EpgProgram?>> = _currentPrograms.asStateFlow()

    init {
        loadEpg()
    }

    /**
     * Carga los datos de la EPG
     * @param forceRefresh Si es true, fuerza la actualización de la caché
     */
    fun loadEpg(forceRefresh: Boolean = false) {
        _epgState.value = EpgState.Loading

        viewModelScope.launch {
            try {
                val epgUrl = appConfig.epgUrl.firstOrNull() ?: return@launch

                epgRepository.getEpgFromUrl(epgUrl, forceRefresh)
                    .onSuccess { (channels, programs) ->
                        _epgState.value = EpgState.Success(channels, programs)

                        // Actualizar programas actuales
                        updateCurrentPrograms(channels.keys.toList())
                    }
                    .onFailure { exception ->
                        _epgState.value = EpgState.Error(
                            exception.message ?: "Error desconocido al cargar la EPG"
                        )
                    }
            } catch (e: Exception) {
                _epgState.value = EpgState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Selecciona un canal para mostrar su programación
     * @param channelId ID del canal
     */
    fun selectChannel(channelId: String) {
        _selectedChannelId.value = channelId
        updateProgramsForSelectedDay()
    }

    /**
     * Cambia la fecha seleccionada para ver la programación
     * @param date Nueva fecha
     */
    fun selectDate(date: Date) {
        _selectedDate.value = date
        updateProgramsForSelectedDay()
    }

    /**
     * Actualiza los programas para el día y canal seleccionado
     */
    private fun updateProgramsForSelectedDay() {
        val channelId = _selectedChannelId.value ?: return
        val date = _selectedDate.value

        val programs = epgRepository.getProgramsForDay(channelId, date)
        _programsForSelectedDay.value = programs
    }

    /**
     * Actualiza los programas actuales para todos los canales
     * @param channelIds Lista de IDs de canales
     */
    fun updateCurrentPrograms(channelIds: List<String>) {
        val currentPrograms = epgRepository.getCurrentPrograms(channelIds)
        _currentPrograms.value = currentPrograms
    }

    /**
     * Obtiene el programa actual para un canal
     * @param channelId ID del canal
     * @return Programa actual o null si no hay datos
     */
    fun getCurrentProgram(channelId: String): EpgProgram? {
        return _currentPrograms.value[channelId]
    }

    /**
     * Obtiene el siguiente programa para un canal
     * @param channelId ID del canal
     * @return Programa siguiente o null si no hay datos
     */
    fun getNextProgram(channelId: String): EpgProgram? {
        val currentProgram = getCurrentProgram(channelId)
        return epgRepository.getNextProgram(channelId, currentProgram)
    }

    /**
     * Actualiza los datos cada minuto para mantener actualizada la información de programas actuales
     */
    fun startPeriodicUpdates() {
        // Aquí podríamos implementar una actualización periódica
        // Por ejemplo, usando un coroutine con delay para actualizar cada minuto
        // Pero para simplificar, lo dejamos como una función manual
    }

    /**
     * Busca canales EPG que coincidan con los canales de la playlist
     * @param playlistChannels Lista de canales de la playlist
     * @return Mapa de canal de playlist a canal de EPG si hay coincidencia
     */
    fun matchChannelsWithEpg(playlistChannels: List<Channel>): Map<Channel, EpgChannel?> {
        val result = mutableMapOf<Channel, EpgChannel?>()

        // Obtener los canales de la EPG
        val epgChannels = when (val state = _epgState.value) {
            is EpgState.Success -> state.channels
            else -> emptyMap()
        }

        playlistChannels.forEach { playlistChannel ->
            // Intentamos encontrar una coincidencia basada en el nombre del canal
            val matchedEpgChannel = epgChannels.values.find { epgChannel ->
                // Normalizar nombres para comparación
                val normalizedEpgName = epgChannel.name.trim().lowercase()
                val normalizedPlaylistName = playlistChannel.name.trim().lowercase()

                normalizedEpgName == normalizedPlaylistName ||
                        normalizedEpgName.contains(normalizedPlaylistName) ||
                        normalizedPlaylistName.contains(normalizedEpgName)
            }

            result[playlistChannel] = matchedEpgChannel
        }

        return result
    }
}