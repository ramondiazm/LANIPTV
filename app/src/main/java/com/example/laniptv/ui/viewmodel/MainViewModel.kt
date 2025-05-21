package com.example.laniptv.ui.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.laniptv.data.model.Category
import com.example.laniptv.data.model.Channel
import com.example.laniptv.data.model.PlayerState
import com.example.laniptv.data.model.PlaylistUiState
import com.example.laniptv.data.repository.PlaylistRepository
import com.example.laniptv.service.PlaylistServiceConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout

private const val TAG = "MainViewModel"

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository()
    private var serviceConnection: PlaylistServiceConnection? = null

    // Estado de la lista de reproducción
    private val _playlistState = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Loading)
    val playlistState: StateFlow<PlaylistUiState> = _playlistState.asStateFlow()

    // Categoría seleccionada
    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    // Canal seleccionado
    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel.asStateFlow()

    // Estado de búsqueda
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    // Canales filtrados por búsqueda
    private val _filteredChannels = MutableStateFlow<List<Channel>>(emptyList())
    val filteredChannels: StateFlow<List<Channel>> = _filteredChannels.asStateFlow()

    // Estado del reproductor
    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Estado de pantalla completa
    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    // Control para reintentos de reproducción
    private var retryCount = 0
    private val MAX_RETRIES = 3
    private var retryHandler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    init {
        Log.d(TAG, "Inicializando MainViewModel")

        // Iniciar servicio de reproducción
        serviceConnection = PlaylistServiceConnection(application) { state ->
            _playerState.value = state

            // Manejar reintentos automáticos para errores
            if (state is PlayerState.Error) {
                Log.e(TAG, "Error en reproducción: ${state.message}")
                retryPlayback()
            }
        }
        serviceConnection?.bindService()

        // Cargar lista de reproducción
        loadPlaylist("https://opop.pro/XLE8sWYgsUXvNp")
    }

    /**
     * Carga la lista de reproducción desde una URL
     */
    fun loadPlaylist(url: String) {
        viewModelScope.launch {
            _playlistState.value = PlaylistUiState.Loading
            Log.d(TAG, "Iniciando carga de lista de reproducción desde: $url")

            try {
                val playlist = playlistRepository.getPlaylistFromUrl(url)
                _playlistState.value = PlaylistUiState.Success(playlist)
                Log.d(TAG, "Lista cargada con éxito: ${playlist.categories.size} categorías, ${playlist.allChannels.size} canales")

                // Seleccionar la categoría "Todos" por defecto
                _selectedCategory.value = playlist.categories.firstOrNull()

            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar lista: ${e.message}", e)
                _playlistState.value = PlaylistUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Selecciona una categoría para mostrar sus canales
     */
    fun selectCategory(category: Category) {
        _selectedCategory.value = category
        _isSearchActive.value = false
        Log.d(TAG, "Categoría seleccionada: ${category.name} con ${category.channels.size} canales")
    }

    /**
     * Selecciona un canal para reproducir
     */
    fun selectChannel(channel: Channel) {
        if (_selectedChannel.value?.id == channel.id) {
            // Si es el mismo canal, cambiar modo fullscreen
            toggleFullscreen()
            Log.d(TAG, "Cambiando modo fullscreen para canal actual: ${channel.name}")
        } else {
            // Mantener el valor de fullscreen actual si estamos cambiando de canal
            // desde el control de navegación
            val wasFullscreen = _isFullscreen.value

            // Cancelar cualquier reintento programado
            cancelRetry()
            retryCount = 0

            _selectedChannel.value = channel
            _playerState.value = PlayerState.Loading
            Log.d(TAG, "Seleccionando nuevo canal: ${channel.name} - URL: ${channel.streamUrl}")
            serviceConnection?.playChannel(channel)

            // Solo reiniciar el fullscreen si no estamos en navegación
            // (cuando se selecciona desde la lista de canales)
            if (!wasFullscreen) {
                _isFullscreen.value = false
            }
        }
    }

    /**
     * Cambia al siguiente canal manteniendo el modo fullscreen
     */
    fun nextChannel() {
        val currentChannel = _selectedChannel.value ?: return
        val channelsToUse = if (_isSearchActive.value) {
            _filteredChannels.value
        } else {
            _selectedCategory.value?.channels ?: emptyList()
        }

        if (channelsToUse.isEmpty()) return

        val currentIndex = channelsToUse.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex != -1) {
            val nextIndex = (currentIndex + 1) % channelsToUse.size
            // Mantener el valor de fullscreen actual
            Log.d(TAG, "Cambiando al siguiente canal: ${channelsToUse[nextIndex].name}")
            _selectedChannel.value = channelsToUse[nextIndex]
            serviceConnection?.playChannel(channelsToUse[nextIndex])
            // No reiniciar el modo fullscreen
        }
    }

    /**
     * Cambia al canal anterior manteniendo el modo fullscreen
     */
    fun previousChannel() {
        val currentChannel = _selectedChannel.value ?: return
        val channelsToUse = if (_isSearchActive.value) {
            _filteredChannels.value
        } else {
            _selectedCategory.value?.channels ?: emptyList()
        }

        if (channelsToUse.isEmpty()) return

        val currentIndex = channelsToUse.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex != -1) {
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else channelsToUse.size - 1
            // Mantener el valor de fullscreen actual
            Log.d(TAG, "Cambiando al canal anterior: ${channelsToUse[prevIndex].name}")
            _selectedChannel.value = channelsToUse[prevIndex]
            serviceConnection?.playChannel(channelsToUse[prevIndex])
            // No reiniciar el modo fullscreen
        }
    }

    /**
     * Activa/desactiva el modo fullscreen
     */
    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
        Log.d(TAG, "Modo fullscreen: ${_isFullscreen.value}")
    }

    /**
     * Busca canales que coincidan con el texto
     */
    fun searchChannels(query: String) {
        if (query.isEmpty()) {
            _isSearchActive.value = false
            Log.d(TAG, "Búsqueda desactivada")
            return
        }

        _isSearchActive.value = true
        Log.d(TAG, "Iniciando búsqueda: '$query'")

        val state = _playlistState.value
        if (state is PlaylistUiState.Success) {
            val allChannels = state.playlist.allChannels
            val filtered = allChannels.filter {
                it.name.contains(query, ignoreCase = true)
            }
            _filteredChannels.value = filtered
            Log.d(TAG, "Resultado de búsqueda: ${filtered.size} canales encontrados")
        }
    }

    /**
     * Conecta el layout de video al reproductor
     */
    fun attachVideoView(videoLayout: VLCVideoLayout) {
        Log.d(TAG, "Adjuntando videoLayout al reproductor")
        serviceConnection?.attachVideoView(videoLayout)
    }

    /**
     * Reintenta la reproducción después de un error
     */
    private fun retryPlayback() {
        val channel = _selectedChannel.value ?: return

        // Cancelar cualquier reintento anterior
        cancelRetry()

        if (retryCount < MAX_RETRIES) {
            retryCount++
            val delayMs = 1500L * retryCount  // Aumentar retraso con cada intento

            Log.d(TAG, "Programando reintento #$retryCount para ${channel.name} en ${delayMs}ms")

            retryRunnable = Runnable {
                Log.d(TAG, "Ejecutando reintento #$retryCount")
                // Cambiar estado a Loading para indicar que estamos reintentando
                _playerState.value = PlayerState.Loading
                serviceConnection?.playChannel(channel)
            }

            retryHandler.postDelayed(retryRunnable!!, delayMs)
        } else {
            Log.e(TAG, "Número máximo de reintentos alcanzado para ${channel.name}")
            // Ya no intentamos más reintentos automáticos
        }
    }

    /**
     * Cancela cualquier reintento programado
     */
    private fun cancelRetry() {
        retryRunnable?.let {
            retryHandler.removeCallbacks(it)
            retryRunnable = null
            Log.d(TAG, "Reintento automático cancelado")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared - Liberando recursos del ViewModel")
        cancelRetry()
        serviceConnection?.unbindService()
        serviceConnection = null
    }
}