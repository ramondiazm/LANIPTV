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

    // Estado del modo de desarrollo
    private val _isDevelopmentMode = MutableStateFlow(false)
    val isDevelopmentMode: StateFlow<Boolean> = _isDevelopmentMode.asStateFlow()

    init {
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
            _playlistState.value = PlaylistUiState.Loading

            try {
                val playlist = playlistRepository.getPlaylistFromUrl(url)
                _playlistState.value = PlaylistUiState.Success(playlist)

                // Seleccionar la categoría "Todos" por defecto
                _selectedCategory.value = playlist.categories.firstOrNull()

            } catch (e: Exception) {
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
    }

    /**
     * Selecciona un canal para reproducir
     */
    fun selectChannel(channel: Channel) {
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
        }
    }

    /**
     * Conecta el layout de video al reproductor
     */
    fun attachVideoView(videoLayout: VLCVideoLayout) {
        serviceConnection?.attachVideoView(videoLayout)
    }

    override fun onCleared() {
        super.onCleared()
        serviceConnection?.unbindService()
    }
}