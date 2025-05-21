package com.example.laniptv.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import android.util.Log
import kotlinx.coroutines.delay

@Composable
fun VLCPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Configuración mejorada para LibVLC
    val libVlc = remember {
        // Configuración optimizada para streams
        val options = arrayListOf(
            // Opciones críticas para streaming
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--rtsp-tcp",
            "--http-reconnect",

            // Configuración de caché para streams
            "--network-caching=2000",
            "--file-caching=1500",
            "--live-caching=1800",

            // Opciones de decodificación de video
            "--codec=avcodec",
            "--hw-dec=automatic", // Habilita decodificación por hardware
            "--video-filter=deinterlace",
            "--deinterlace-mode=blend",

            // Opciones de audio
            "--aout=opensles",
            "--audio-time-stretch",

            // Opciones para mejorar la resiliencia
            "--clock-jitter=0",
            "--clock-synchro=0",

            // Logging para debug
            "-vvv"
        )

        LibVLC(context, options)
    }

    // Crear MediaPlayer
    val mediaPlayer = remember { MediaPlayer(libVlc) }

    // Referencia a VLCVideoLayout
    val videoLayout = remember { VLCVideoLayoutRef() }

    // Configurar reproducción del stream
    LaunchedEffect(streamUrl) {
        try {
            Log.d("VLCPlayer", "Iniciando reproducción de: $streamUrl")

            // Pequeño retraso para asegurarse que el layout está listo
            delay(300)

            val media = Media(libVlc, Uri.parse(streamUrl))

            // Configuración específica para streams HLS/HTTP
            media.setHWDecoderEnabled(true, false)

            if (streamUrl.contains(".m3u8")) {
                Log.d("VLCPlayer", "Aplicando configuración para HLS")
                media.addOption(":adaptive-use-access")
                media.addOption(":adaptive-maxwidth=1920")
                media.addOption(":adaptive-maxheight=1080")
                media.addOption(":adaptive-bw=2000") // Ancho de banda adaptativo
            }

            // Opciones adicionales para mejorar la reproducción
            media.addOption(":network-caching=2000")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")

            // Configurar y reproducir
            mediaPlayer.media = media
            mediaPlayer.play()
            media.release()

            // Monitorear estado inicial
            delay(500)
            if (!mediaPlayer.isPlaying) {
                Log.d("VLCPlayer", "Reproducción no iniciada, reintentando...")
                mediaPlayer.stop()
                mediaPlayer.play()
            }
        } catch (e: Exception) {
            Log.e("VLCPlayer", "Error iniciando reproducción: ${e.message}", e)
        }
    }

    // Manejar eventos del ciclo de vida
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        Log.d("VLCPlayer", "Lifecycle: ON_RESUME")
                        videoLayout.get()?.let {
                            mediaPlayer.attachViews(it, null, true, false)

                            if (mediaPlayer.media == null || !mediaPlayer.isPlaying) {
                                Log.d("VLCPlayer", "Reiniciando reproducción en ON_RESUME")
                                val media = Media(libVlc, Uri.parse(streamUrl))
                                mediaPlayer.media = media
                                mediaPlayer.play()
                                media.release()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VLCPlayer", "Error en ON_RESUME: ${e.message}", e)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        Log.d("VLCPlayer", "Lifecycle: ON_PAUSE")
                        mediaPlayer.pause()
                    } catch (e: Exception) {
                        Log.e("VLCPlayer", "Error en ON_PAUSE: ${e.message}", e)
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        Log.d("VLCPlayer", "Lifecycle: ON_DESTROY")
                        mediaPlayer.stop()
                        mediaPlayer.detachViews()
                    } catch (e: Exception) {
                        Log.e("VLCPlayer", "Error en ON_DESTROY: ${e.message}", e)
                    }
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                Log.d("VLCPlayer", "Liberando recursos")
                mediaPlayer.stop()
                mediaPlayer.detachViews()
                mediaPlayer.release()
                libVlc.release()
            } catch (e: Exception) {
                Log.e("VLCPlayer", "Error liberando recursos: ${e.message}", e)
            }
        }
    }

    // Vista de Android
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            factory = { ctx ->
                // Crear VLCVideoLayout
                VLCVideoLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    videoLayout.set(this)

                    // Configuración importante: intenta adjuntar vistas inmediatamente
                    try {
                        mediaPlayer.attachViews(this, null, true, false)
                        Log.d("VLCPlayer", "Vistas adjuntadas en factory")
                    } catch (e: Exception) {
                        Log.e("VLCPlayer", "Error adjuntando vistas en factory: ${e.message}", e)
                    }
                }
            }
        )
    }
}

/**
 * Clase auxiliar para mantener referencia a VLCVideoLayout
 */
class VLCVideoLayoutRef {
    private var layout: VLCVideoLayout? = null

    fun set(newLayout: VLCVideoLayout) {
        layout = newLayout
    }

    fun get(): VLCVideoLayout? = layout
}