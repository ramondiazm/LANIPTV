package com.example.laniptv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
     * Inicializa la librería VLC con configuración optimizada
     */
    fun initialize() {
        if (libVlc == null) {
            try {
                // Configuración mejorada para mayor estabilidad en reproducciones IPTV
                val options = ArrayList<String>().apply {
                    // Opciones de buffering mejoradas
                    add("--network-caching=3000")       // Incrementado para mejor estabilidad
                    add("--file-caching=1500")
                    add("--live-caching=2000")

                    // Opciones críticas para streaming
                    add("--sout-mux-caching=2000")
                    add("--http-reconnect")
                    add("--adaptive-maxbuffer=30000")   // Búfer más grande para adaptación

                    // Opciones para mejorar la continuidad del stream
                    add("--clock-jitter=0")
                    add("--clock-synchro=0")
                    add("--no-drop-late-frames")        // No descartar frames tardíos
                    add("--no-skip-frames")             // No saltarse frames

                    // Opciones para manejo de UDP y RTP
                    add("--udp-timeout=5000")
                    add("--rtsp-tcp")                   // Preferir TCP para RTSP

                    // Opciones para audio
                    add("--aout=opensles")
                    add("--audio-time-stretch")         // Permite estirar audio para sincronización
                    add("--avcodec-fast")               // Decodificación más rápida

                    // Deshabilitar funciones innecesarias
                    add("--no-sub-autodetect-file")
                    add("--no-snapshot-preview")
                    add("--no-stats")
                }

                Log.d(TAG, "Inicializando LibVLC con opciones mejoradas")
                libVlc = LibVLC(context, options)
                mediaPlayer = MediaPlayer(libVlc)

                // Configurar eventos del reproductor con mejor manejo de errores
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
     * Reproduce un canal con configuración optimizada
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

            // Configurar opciones según el tipo de stream
            val isUdp = channel.streamUrl.startsWith("udp") || channel.streamUrl.startsWith("rtp")
            val isHttp = channel.streamUrl.startsWith("http")

            // Opciones comunes
            media.addOption(":network-caching=3000")

            if (isUdp) {
                // Optimizaciones para streams UDP/RTP
                media.addOption(":udp-timeout=5000")
                media.addOption(":udp-caching=3000")
                media.addOption(":clock-jitter=0")
            } else if (isHttp) {
                // Optimizaciones para streams HTTP
                media.addOption(":http-reconnect")
                media.addOption(":http-continuous")
                media.addOption(":adaptive-maxbuffer=30000")
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
     * Configura listeners para eventos del reproductor con manejo mejorado
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
                    // Implementación mejorada de reintento con delay
                    currentChannel?.let {
                        Log.d(TAG, "Reintentando reproducción con delay")
                        // Pequeña pausa antes de reintentar para evitar bucle de reintentos rápidos
                        Handler(Looper.getMainLooper()).postDelayed({
                            playChannel(it)
                        }, 1500) // 1.5 segundos de pausa
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "Evento: Error en reproducción")
                    // Implementación mejorada de manejo de errores
                    val errorMessage = "Error en la reproducción"
                    onPlayerStateChanged(PlayerState.Error(errorMessage))

                    // Reintentar después de un error con delay progresivo
                    currentChannel?.let { channel ->
                        // Reintentar con un delay mayor
                        Handler(Looper.getMainLooper()).postDelayed({
                            playChannel(channel)
                        }, 3000) // 3 segundos
                    }
                }
                MediaPlayer.Event.Buffering -> {
                    val buffering = event.buffering
                    Log.d(TAG, "Evento: Buffering ${buffering}%")

                    if (buffering >= 100) {
                        if (mediaPlayer?.isPlaying == false) {
                            mediaPlayer?.play()
                        }
                    } else if (buffering > 10) {
                        // Informar al usuario sobre el estado del buffering
                        onPlayerStateChanged(PlayerState.Loading)
                    }
                }
                MediaPlayer.Event.TimeChanged, MediaPlayer.Event.PositionChanged -> {
                    // Evento silencioso, no loggear para evitar spam
                }
                else -> {
                    // Loggear otros eventos
                    Log.d(TAG, "Evento VLC: ${event.type}")
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