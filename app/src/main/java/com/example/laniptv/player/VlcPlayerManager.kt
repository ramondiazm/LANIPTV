package com.example.laniptv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.laniptv.data.model.Channel
import com.example.laniptv.data.model.PlayerState
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Gestiona la reproducción de streams con libVLC
 */
class VlcPlayerManager(
    private val context: Context,
    private val onPlayerStateChanged: (PlayerState) -> Unit
) {
    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentChannel: Channel? = null
    private var currentLayout: VLCVideoLayout? = null
    private val TAG = "VlcPlayerManager"

    /**
     * Inicializa la librería VLC con configuración básica
     */
    fun initialize() {
        if (libVlc == null) {
            try {
                // Configuración básica para VLC que debería funcionar en la mayoría de dispositivos
                val options = ArrayList<String>().apply {
                    // Opciones esenciales para la reproducción
                    add("--no-drop-late-frames")
                    add("--no-skip-frames")
                    add("--rtsp-tcp")
                    add("--network-caching=1500")
                    add("--http-reconnect")

                    // Deshabilitar funciones innecesarias para reducir problemas
                    add("--no-sub-autodetect-file")
                    add("--no-snapshot-preview")
                    add("--no-stats")

                    // Modo de audio seguro
                    add("--aout=opensles")
                    add("--audio-time-stretch")
                }

                Log.d(TAG, "Inicializando LibVLC con opciones básicas")
                libVlc = LibVLC(context, options)
                mediaPlayer = MediaPlayer(libVlc)

                // Configurar eventos del reproductor
                configureMediaPlayerEvents()

                Log.d(TAG, "LibVLC inicializado correctamente")
            } catch (e: Exception) {
                Log.e(TAG, "Error al inicializar LibVLC: ${e.message}", e)
            }
        }
    }

    /**
     * Configura el VideoLayout donde se reproducirá el video
     * Gestiona correctamente el cambio entre vistas
     */
    fun attachViews(videoLayout: VLCVideoLayout) {
        try {
            // Si hay un layout anterior y es diferente, desconectarlo primero
            if (currentLayout != null && currentLayout != videoLayout) {
                mediaPlayer?.detachViews()
                currentLayout = null
            }

            // Si no hay un layout actual o es diferente, adjuntar el nuevo
            if (currentLayout == null) {
                mediaPlayer?.attachViews(videoLayout, null, false, false)
                currentLayout = videoLayout
                Log.d(TAG, "Vistas adjuntadas al reproductor")
            }

            onPlayerStateChanged(PlayerState.Idle)
        } catch (e: Exception) {
            Log.e(TAG, "Error al adjuntar vistas: ${e.message}", e)
        }
    }

    /**
     * Desacopla las vistas
     */
    fun detachViews() {
        try {
            mediaPlayer?.detachViews()
            currentLayout = null
            Log.d(TAG, "Vistas desacopladas")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desacoplar vistas: ${e.message}", e)
        }
    }

    /**
     * Reproduce un canal
     */
    fun playChannel(channel: Channel) {
        if (mediaPlayer == null) {
            initialize()
        }

        currentChannel = channel
        onPlayerStateChanged(PlayerState.Loading)
        Log.d(TAG, "Iniciando reproducción de canal: ${channel.name}")

        try {
            // Detener reproducción anterior
            mediaPlayer?.stop()

            // Crear objeto Media con la URL del stream
            val uri = Uri.parse(channel.streamUrl)
            val media = Media(libVlc, uri)

            // Opciones básicas para el stream
            media.addOption(":network-caching=1500")

            if (channel.streamUrl.startsWith("http")) {
                media.addOption(":http-reconnect")
            }

            // Establecer el nuevo medio y reproducir
            mediaPlayer?.media = media
            media.release()
            mediaPlayer?.play()

            Log.d(TAG, "Reproducción iniciada: ${channel.streamUrl}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir canal: ${e.message}", e)
            onPlayerStateChanged(PlayerState.Error("Error al reproducir: ${e.message}"))
        }
    }

    /**
     * Configura listeners para eventos del reproductor
     */
    private fun configureMediaPlayerEvents() {
        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    Log.d(TAG, "Evento: Reproduciendo")
                    onPlayerStateChanged(PlayerState.Playing)
                }
                MediaPlayer.Event.Paused -> {
                    Log.d(TAG, "Evento: Pausado")
                    onPlayerStateChanged(PlayerState.Paused)
                }
                MediaPlayer.Event.EndReached -> {
                    Log.d(TAG, "Evento: Fin alcanzado")
                    // En caso de fin de stream, intentar reproducir de nuevo
                    currentChannel?.let {
                        Log.d(TAG, "Reintentando reproducción")
                        playChannel(it)
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "Evento: Error en reproducción")
                    onPlayerStateChanged(PlayerState.Error("Error en la reproducción"))
                }
                MediaPlayer.Event.Buffering -> {
                    Log.d(TAG, "Evento: Buffering ${event.buffering}%")
                    if (event.buffering >= 100 && mediaPlayer?.isPlaying == false) {
                        mediaPlayer?.play()
                    }
                }
            }
        }
    }
    /**
     * Pausa la reproducción
     */
    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                Log.d(TAG, "Reproducción pausada")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al pausar: ${e.message}", e)
        }
    }

    /**
     * Reanuda la reproducción
     */
    fun resume() {
        try {
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.play()
                Log.d(TAG, "Reproducción reanudada")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al reanudar: ${e.message}", e)
        }
    }

    /**
     * Libera recursos al finalizar
     */
    fun release() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.detachViews()
            mediaPlayer?.release()
            libVlc?.release()

            mediaPlayer = null
            libVlc = null
            currentLayout = null
            Log.d(TAG, "Recursos liberados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos: ${e.message}", e)
        }
    }
}