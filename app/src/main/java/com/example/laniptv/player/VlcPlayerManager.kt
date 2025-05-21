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

    // Contador de reintentos para manejo de errores
    private var retryCount = 0
    private val MAX_RETRIES = 3

    /**
     * Inicializa la librería VLC con configuración optimizada
     */
    fun initialize() {
        if (libVlc == null) {
            try {
                // Configuración mejorada para mayor estabilidad en reproducciones IPTV
                val options = ArrayList<String>().apply {
                    // Opciones de buffering mejoradas
                    add("--network-caching=2000")
                    add("--file-caching=1500")
                    add("--live-caching=1800")

                    // Opciones críticas para streaming
                    add("--sout-mux-caching=2000")
                    add("--http-reconnect")
                    add("--adaptive-maxbuffer=30000")   // Búfer más grande para adaptación

                    // Opciones para mejorar la calidad del video
                    add("--codec=avcodec")
                    add("--hw-dec=automatic")         // Decodificación por hardware automática
                    add("--video-filter=deinterlace")  // Mejora video entrelazado
                    add("--deinterlace-mode=blend")   // Modo de desentrelazado

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

                    // Verbose para debugging
                    add("-vvv")
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
            // Si hay un layout anterior, desconectarlo primero
            if (currentLayout != null) {
                mediaPlayer?.detachViews()
                currentLayout = null
                Log.d(TAG, "Vistas anteriores desacopladas")
            }

            // Pequeño retraso para garantizar que el surface se actualice
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // Adjuntar nuevo layout - importante: el tercer parámetro es enableVideoFilter
                    // que debe ser true para habilitar filtros de video
                    mediaPlayer?.attachViews(videoLayout, null, true, false)
                    currentLayout = videoLayout
                    Log.d(TAG, "Vistas adjuntadas al reproductor")

                    // Si hay un canal actual, reintentar la reproducción
                    currentChannel?.let {
                        if (mediaPlayer?.isPlaying == false) {
                            Log.d(TAG, "Reintentando reproducción al adjuntar vistas")
                            playChannel(it)
                        }
                    }

                    onPlayerStateChanged(PlayerState.Idle)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al adjuntar vistas (delayed): ${e.message}", e)
                }
            }, 200)  // 200ms de retraso
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
        retryCount = 0  // Reiniciar contador de reintentos
        onPlayerStateChanged(PlayerState.Loading)
        Log.d(TAG, "Iniciando reproducción de canal: ${channel.name} - URL: ${channel.streamUrl}")

        try {
            // Detener reproducción anterior
            mediaPlayer?.stop()

            // Crear objeto Media con la URL del stream
            val uri = Uri.parse(channel.streamUrl)
            val media = Media(libVlc, uri)

            // Configurar opciones según el tipo de stream
            val isHls = channel.streamUrl.contains(".m3u8")
            val isUdp = channel.streamUrl.startsWith("udp") || channel.streamUrl.startsWith("rtp")
            val isHttp = channel.streamUrl.startsWith("http")

            // Opciones comunes
            media.addOption(":network-caching=2000")

            // Habilitar decodificación por hardware
            media.setHWDecoderEnabled(true, false)

            // Configuración específica por tipo de stream
            if (isHls) {
                Log.d(TAG, "Aplicando configuración para HLS")
                media.addOption(":adaptive-use-access")
                media.addOption(":adaptive-maxwidth=1920")
                media.addOption(":adaptive-maxheight=1080")
                media.addOption(":adaptive-bw=2000") // Ancho de banda adaptativo
            } else if (isUdp) {
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

            // Reintentar automáticamente si hay errores
            retryPlayback()
        }
    }

    /**
     * Reintenta la reproducción después de un error
     */
    private fun retryPlayback() {
        val channel = currentChannel ?: return

        if (retryCount < MAX_RETRIES) {
            retryCount++
            Log.d(TAG, "Reintento #$retryCount de reproducción para ${channel.name}")

            // Aumentar el retraso con cada reintento
            val delayMs = 1000L * retryCount

            Handler(Looper.getMainLooper()).postDelayed({
                playChannel(channel)
            }, delayMs)
        } else {
            Log.e(TAG, "Número máximo de reintentos alcanzado para ${channel.name}")
            onPlayerStateChanged(PlayerState.Error("No se pudo reproducir después de $MAX_RETRIES intentos"))
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
                    retryCount = 0  // Reiniciar contador si la reproducción es exitosa
                    onPlayerStateChanged(PlayerState.Playing)
                }
                MediaPlayer.Event.Paused -> {
                    Log.d(TAG, "Evento: Pausado")
                    onPlayerStateChanged(PlayerState.Paused)
                }
                MediaPlayer.Event.EndReached -> {
                    Log.d(TAG, "Evento: Fin alcanzado")
                    // Implementación mejorada de reintento con delay
                    retryPlayback()
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "Evento: Error en reproducción")
                    val errorMessage = "Error en la reproducción"
                    onPlayerStateChanged(PlayerState.Error(errorMessage))

                    // Reintentar con la estrategia de reintento
                    retryPlayback()
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
                MediaPlayer.Event.Vout -> {
                    val countVout = event.voutCount
                    Log.d(TAG, "Evento: Vout - Conteo: $countVout")
                    if (countVout > 0) {
                        Log.d(TAG, "¡Video habilitado! Superficie de vídeo creada.")
                    } else {
                        Log.d(TAG, "Superficie de vídeo destruída.")
                    }
                }
                MediaPlayer.Event.ESAdded -> {
                    Log.d(TAG, "Evento: ES Añadido - Tipo: ${event.esChangedType}")
                }
                MediaPlayer.Event.MediaChanged -> {
                    Log.d(TAG, "Evento: Media Cambiado")
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