package com.example.laniptv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.laniptv.LanIPTVApplication
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
import android.util.Log

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository()
    private var serviceConnection: PlaylistServiceConnection? = null
    private val TAG = "MainViewModel"

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

    // Estado del modo de desarrollo
    private val _isDevelopmentMode = MutableStateFlow(false)
    val isDevelopmentMode: StateFlow<Boolean> = _isDevelopmentMode.asStateFlow()

    // Información de diagnóstico
    private val _diagnosisInfo = MutableStateFlow<String?>(null)
    val diagnosisInfo: StateFlow<String?> = _diagnosisInfo.asStateFlow()

    init {
        Log.d(TAG, "Inicializando MainViewModel")

        // Iniciar servicio de reproducción
        serviceConnection = PlaylistServiceConnection(application) { state ->
            _playerState.value = state
        }
        serviceConnection?.bindService()

        // Verificar modo de desarrollo
        viewModelScope.launch {
            val appConfig = (application as LanIPTVApplication).appConfig
            appConfig.isDevelopmentMode.collect { isDevMode ->
                _isDevelopmentMode.value = isDevMode
            }
        }

        // Cargar lista de reproducción (puedes personalizar la URL)
        loadPlaylist("https://opop.pro/XLE8sWYgsUXvNp")
    }

    /**
     * Carga la lista de reproducción desde una URL
     */
    fun loadPlaylist(url: String) {
        viewModelScope.launch {
            Log.d(TAG, "Iniciando carga de lista de reproducción desde: $url")
            _playlistState.value = PlaylistUiState.Loading

            try {
                val playlist = playlistRepository.getPlaylistFromUrl(url)
                _playlistState.value = PlaylistUiState.Success(playlist)

                Log.d(TAG, "Lista cargada con éxito: ${playlist.categories.size} categorías, ${playlist.allChannels.size} canales")

                // Seleccionar la categoría "Todos" por defecto
                _selectedCategory.value = playlist.categories.firstOrNull()

            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar la lista: ${e.message}", e)
                _playlistState.value = PlaylistUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    /**
     * Selecciona una categoría para mostrar sus canales
     */
    fun selectCategory(category: Category) {
        Log.d(TAG, "Categoría seleccionada: ${category.name} con ${category.channels.size} canales")
        _selectedCategory.value = category
        _isSearchActive.value = false
    }

    /**
     * Selecciona un canal para reproducir
     */
    fun selectChannel(channel: Channel) {
        Log.d(TAG, "Seleccionando nuevo canal: ${channel.name} - URL: ${channel.streamUrl}")

        if (_selectedChannel.value?.id == channel.id) {
            // Si es el mismo canal, cambiar modo fullscreen
            toggleFullscreen()
        } else {
            // Mantener el valor de fullscreen actual si estamos cambiando de canal
            // desde el control de navegación
            val wasFullscreen = _isFullscreen.value

            _selectedChannel.value = channel

            // Solo intentar reproducir si no estamos en modo de desarrollo
            if (!_isDevelopmentMode.value) {
                serviceConnection?.playChannel(channel)
            } else {
                // En modo desarrollo, simplemente cambiamos el estado
                _playerState.value = PlayerState.Playing
            }

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
            selectChannel(channelsToUse[nextIndex])
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
            selectChannel(channelsToUse[prevIndex])
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
            return
        }

        _isSearchActive.value = true

        val state = _playlistState.value
        if (state is PlaylistUiState.Success) {
            val allChannels = state.playlist.allChannels
            val filtered = allChannels.filter {
                it.name.contains(query, ignoreCase = true)
            }
            _filteredChannels.value = filtered
            Log.d(TAG, "Búsqueda activa: '$query' - ${filtered.size} resultados")
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
     * Muestra información de diagnóstico
     */
    fun showDiagnosis() {
        viewModelScope.launch {
            val info = serviceConnection?.getMediaPlayerInfo() ?: "No hay información disponible"
            _diagnosisInfo.value = info
        }
    }

    /**
     * Limpia la información de diagnóstico
     */
    fun clearDiagnosis() {
        _diagnosisInfo.value = null
    }

    /**
     * Obtiene información del reproductor para diagnóstico
     */
    fun getMediaPlayerInfo(): String {
        return serviceConnection?.getMediaPlayerInfo() ?: "No hay información disponible"
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MainViewModel siendo destruido")
        serviceConnection?.unbindService()
    }
}