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
import java.lang.ref.WeakReference

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
    private var currentLayout: WeakReference<VLCVideoLayout>? = null
    private val TAG = "VlcPlayerManager"

    // Indica si estamos en un emulador (para usar configuración optimizada)
    private val isEmulator: Boolean by lazy {
        (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk_google")
                || android.os.Build.PRODUCT.contains("google_sdk")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("sdk_x86")
                || android.os.Build.PRODUCT.contains("sdk_gphone64_arm64")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator")
                || android.os.Build.PRODUCT.contains("simulator")
    }

    // Handler para reintentos con delay
    private val handler = Handler(Looper.getMainLooper())

    // Evitar múltiples intentos de recreación
    private var isInitializing = false

    /**
     * Inicializa la librería VLC con configuración optimizada
     */
    @Synchronized
    fun initialize() {
        if (libVlc != null || isInitializing) {
            return
        }

        isInitializing = true

        try {
            Log.d(TAG, "Inicializando LibVLC con opciones optimizadas")

            // Opciones para emulador o dispositivo real
            val options = ArrayList<String>()

            if (isEmulator) {
                Log.d(TAG, "Configurando VLC para emulador")
                // Configuración minimalista para emulador
                options.apply {
                    add("--no-video-title-show")
                    add("--no-stats")
                    add("--no-snapshot-preview")
                    add("--aout=android_audiotrack")
                    add("--vout=android_display")
                    add("--network-caching=10000") // Caché grande para emulador
                    add("--file-caching=10000")
                    add("--live-caching=10000")
                    add("--intf=dummy") // Interface dummy para emulador
                    add("--extraintf=")
                    add("-v") // Verbose logs pero no tanto como -vv
                }
            } else {
                Log.d(TAG, "Configurando VLC para dispositivo real")
                // Configuración optimizada para dispositivos reales
                options.apply {
                    add("--network-caching=5000")
                    add("--file-caching=5000")
                    add("--live-caching=5000")
                    add("--sout-mux-caching=5000")
                    add("--http-reconnect")
                    add("--adaptive-maxbuffer=30000")
                    add("--clock-jitter=0")
                    add("--clock-synchro=0")
                    add("--no-drop-late-frames")
                    add("--no-skip-frames")
                    add("--rtsp-tcp")
                    add("--aout=opensles")
                    add("--audio-time-stretch")
                    add("--avcodec-fast")
                    add("--no-sub-autodetect-file")
                    add("--no-snapshot-preview")
                    add("--no-stats")
                    add("--android-display-chroma")
                    add("--codec=mediacodec,all")
                }
            }

            libVlc = LibVLC(context, options)

            if (libVlc != null) {
                mediaPlayer = MediaPlayer(libVlc)

                // Configurar eventos del reproductor con mejor manejo de errores
                configureMediaPlayerEvents()
                Log.d(TAG, "LibVLC inicializado correctamente")

                // Notificar estado inicial
                onPlayerStateChanged(PlayerState.Idle)
            } else {
                Log.e(TAG, "Error: LibVLC es nulo después de la inicialización")
                onPlayerStateChanged(PlayerState.Error("No se pudo inicializar el reproductor"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar LibVLC: ${e.message}", e)
            libVlc = null
            mediaPlayer = null
            onPlayerStateChanged(PlayerState.Error("Error al inicializar: ${e.message}"))
        } finally {
            isInitializing = false
        }
    }

    /**
     * Configura el VideoLayout donde se reproducirá el video
     * Gestiona correctamente el cambio entre vistas
     */
    @Synchronized
    fun attachViews(videoLayout: VLCVideoLayout) {
        try {
            if (mediaPlayer == null || libVlc == null) {
                initialize()
            }

            // Si hay un layout anterior, obtenerlo y comprobar si es distinto
            val previousLayout = currentLayout?.get()
            if (previousLayout != null && previousLayout !== videoLayout) {
                mediaPlayer?.detachViews()
                currentLayout = null
            }

            // Si no hay un layout actual o es diferente, adjuntar el nuevo
            if (currentLayout == null || currentLayout?.get() == null) {
                mediaPlayer?.attachViews(videoLayout, null, false, false)
                currentLayout = WeakReference(videoLayout)
                Log.d(TAG, "Vistas adjuntadas al reproductor")
            }

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
    @Synchronized
    fun playChannel(channel: Channel) {
        // Cancelar cualquier reintento pendiente
        handler.removeCallbacksAndMessages(null)

        // Guardar canal actual
        currentChannel = channel

        // Notificar que estamos cargando
        onPlayerStateChanged(PlayerState.Loading)
        Log.d(TAG, "Iniciando reproducción de canal: ${channel.name} - URL: ${channel.streamUrl}")

        // Inicializar si es necesario
        if (mediaPlayer == null || libVlc == null) {
            initialize()
        }

        // Si después de inicializar sigue siendo nulo, reportar error
        if (libVlc == null || mediaPlayer == null) {
            onPlayerStateChanged(PlayerState.Error("No se pudo inicializar el reproductor"))
            return
        }

        try {
            // Detener reproducción anterior
            mediaPlayer?.stop()

            // Para emuladores, usar una URL de prueba que funcione bien
            val streamUrl = if (isEmulator) {
                // URL de test que funciona bien en emuladores
                Log.d(TAG, "Usando URL de prueba para emulador")
                "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
            } else {
                channel.streamUrl
            }

            // Crear objeto Media con la URL del stream
            val uri = Uri.parse(streamUrl)
            val media = Media(libVlc, uri)

            // Configurar opciones específicas para cada tipo de stream
            val isHls = streamUrl.contains(".m3u8")
            val isUdp = streamUrl.startsWith("udp") || streamUrl.startsWith("rtp")
            val isHttp = streamUrl.startsWith("http")

            // Opciones comunes para todos los streams
            if (isEmulator) {
                media.addOption(":network-caching=10000")
            } else {
                media.addOption(":network-caching=5000")
            }

            // Opciones específicas por tipo de stream
            when {
                isHls -> {
                    // Configuración específica para HLS
                    media.addOption(":http-reconnect")
                    media.addOption(":hls-timeout=60")
                    Log.d(TAG, "Configurando para stream HLS")
                }
                isUdp -> {
                    // Optimizaciones para streams UDP/RTP
                    media.addOption(":udp-timeout=5000")
                    media.addOption(":udp-caching=5000")
                    media.addOption(":clock-jitter=0")
                    Log.d(TAG, "Configurando para stream UDP/RTP")
                }
                isHttp -> {
                    // Optimizaciones para streams HTTP
                    media.addOption(":http-reconnect")
                    media.addOption(":http-continuous")
                    media.addOption(":adaptive-maxbuffer=30000")
                    Log.d(TAG, "Configurando para stream HTTP")
                }
            }

            // Establecer el nuevo medio y reproducir
            mediaPlayer?.media = media
            media.release()
            mediaPlayer?.play()

            Log.d(TAG, "Reproducción iniciada: $streamUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir canal: ${e.message}", e)
            onPlayerStateChanged(PlayerState.Error("Error al reproducir: ${e.message}"))

            // Reintentar con delay en caso de error
            retryPlayback(channel, 3000)
        }
    }

    /**
     * Reintenta la reproducción después de un error
     */
    private fun retryPlayback(channel: Channel, delayMs: Long) {
        handler.postDelayed({
            Log.d(TAG, "Reintentando reproducción de canal: ${channel.name}")

            try {
                // Si el media player existe, intentar reproducir directamente
                if (mediaPlayer != null && libVlc != null) {
                    playChannel(channel)
                } else {
                    // Si no existe, reinicializar todo
                    release()
                    initialize()

                    // Reconectar vistas si hay un layout disponible
                    currentLayout?.get()?.let { layout ->
                        attachViews(layout)
                    }

                    // Intentar reproducir de nuevo
                    playChannel(channel)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en reintento: ${e.message}", e)
                onPlayerStateChanged(PlayerState.Error("Error en reintento: ${e.message}"))
            }
        }, delayMs)
    }

    /**
     * Configura listeners para eventos del reproductor con manejo mejorado
     */
    private fun configureMediaPlayerEvents() {
        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    Log.d(TAG, "Evento: Abriendo medio")
                }
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
                    currentChannel?.let { channel ->
                        retryPlayback(channel, 1500)
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "Evento: Error en reproducción")
                    // Implementación mejorada de manejo de errores
                    val errorMessage = "Error en la reproducción"
                    onPlayerStateChanged(PlayerState.Error(errorMessage))

                    // Reintentar después de un error con delay progresivo
                    currentChannel?.let { channel ->
                        retryPlayback(channel, 3000)
                    }
                }
                MediaPlayer.Event.Buffering -> {
                    val buffering = event.buffering
                    Log.d(TAG, "Evento: Buffering ${buffering}%")

                    if (buffering >= 100) {
                        if (mediaPlayer?.isPlaying == false) {
                            mediaPlayer?.play()
                        }
                    } else if (buffering > 0) {
                        // Informar al usuario sobre el estado del buffering
                        onPlayerStateChanged(PlayerState.Loading)
                    }

                    // Mostrar más detalles sobre el buffer
                    if (isEmulator && buffering == 0.0f) {
                        Log.d(TAG, "Buffering estancado en 0% en emulador - Usando stream de prueba")
                    }
                }
                MediaPlayer.Event.TimeChanged, MediaPlayer.Event.PositionChanged -> {
                    // Evento silencioso, no loggear para evitar spam
                }
                else -> {
                    // Loggear otros eventos con descripción clara
                    Log.d(TAG, "Evento VLC: ${event.type} - ${getEventDescription(event.type)}")
                }
            }
        }
    }

    /**
     * Método auxiliar para describir los tipos de eventos
     */
    private fun getEventDescription(eventType: Int): String {
        return when (eventType) {
            MediaPlayer.Event.MediaChanged -> "Media cambiado"
            MediaPlayer.Event.Opening -> "Abriendo"
            MediaPlayer.Event.Buffering -> "Buffering"
            MediaPlayer.Event.Playing -> "Reproduciendo"
            MediaPlayer.Event.Paused -> "Pausado"
            MediaPlayer.Event.Stopped -> "Detenido"
            MediaPlayer.Event.EndReached -> "Fin alcanzado"
            MediaPlayer.Event.EncounteredError -> "Error encontrado"
            MediaPlayer.Event.TimeChanged -> "Tiempo cambiado"
            MediaPlayer.Event.PositionChanged -> "Posición cambiada"
            MediaPlayer.Event.SeekableChanged -> "Navegable cambiado"
            MediaPlayer.Event.PausableChanged -> "Pausable cambiado"
            MediaPlayer.Event.LengthChanged -> "Duración cambiada"
            MediaPlayer.Event.Vout -> "Salida de video"
            MediaPlayer.Event.ESAdded -> "ES añadido"
            MediaPlayer.Event.ESDeleted -> "ES eliminado"
            MediaPlayer.Event.ESSelected -> "ES seleccionado"
            else -> "Evento desconocido: $eventType"
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
     * Obtiene información del reproductor para diagnóstico
     */
    fun getMediaPlayerInfo(): String {
        val mpInfo = mediaPlayer?.let {
            "Estado: ${if (it.isPlaying) "Reproduciendo" else "Pausa/Detenido"}\n" +
                    "Tiempo: ${it.time / 1000} segundos\n" +
                    "Emulador: $isEmulator\n" +
                    "Canal actual: ${currentChannel?.name ?: "Ninguno"}\n"
        } ?: "MediaPlayer nulo"

        return "=== VLC DIAGNÓSTICO ===\n$mpInfo"
    }

    /**
     * Libera recursos al finalizar, asegurando que todo se libere en el orden correcto
     */
    @Synchronized
    fun release() {
        try {
            // Eliminar cualquier tarea pendiente de reintento
            handler.removeCallbacksAndMessages(null)

            // Detener la reproducción primero
            try {
                mediaPlayer?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener el reproductor: ${e.message}", e)
            }

            // Desacoplar vistas
            try {
                mediaPlayer?.detachViews()
            } catch (e: Exception) {
                Log.e(TAG, "Error al desacoplar vistas: ${e.message}", e)
            }

            // Liberar MediaPlayer primero
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error al liberar MediaPlayer: ${e.message}", e)
            }

            // Luego liberar LibVLC
            try {
                libVlc?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error al liberar LibVLC: ${e.message}", e)
            }

            // Limpiar referencias
            mediaPlayer = null
            libVlc = null
            currentLayout = null
            currentChannel = null

            Log.d(TAG, "Recursos liberados correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos: ${e.message}", e)
        }
    }
}