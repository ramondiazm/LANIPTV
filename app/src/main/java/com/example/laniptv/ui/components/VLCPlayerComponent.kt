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

@Composable
fun VLCPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create LibVLC instance
    val libVlc = remember {
        // Setup LibVLC options
        val options = arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--rtsp-tcp",
            "--network-caching=1000",
            "-vvv" // Verbose logging for debugging
        )

        LibVLC(context, options)
    }

    // Create MediaPlayer
    val mediaPlayer = remember { MediaPlayer(libVlc) }

    // Reference to VLCVideoLayout
    val videoLayout = remember { VLCVideoLayoutRef() }

    // Set up media playback
    LaunchedEffect(streamUrl) {
        try {
            val media = Media(libVlc, Uri.parse(streamUrl))
            media.setHWDecoderEnabled(true, false)

            mediaPlayer.media = media
            mediaPlayer.play()

            media.release()
        } catch (e: Exception) {
            Log.e("VLCPlayer", "Error starting playback", e)
        }
    }

    // Handle lifecycle events to properly release resources
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        // Solo adjuntar vistas si no están ya adjuntas
                        videoLayout.get()?.let {
                            if (!mediaPlayer.hasMedia() || !mediaPlayer.isPlaying()) {
                                mediaPlayer.attachViews(it, null, false, false)
                                if (mediaPlayer.media == null) {
                                    val media = Media(libVlc, Uri.parse(streamUrl))
                                    mediaPlayer.media = media
                                    media.release()
                                }
                                mediaPlayer.play()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VLCPlayer", "Error resuming playback", e)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        mediaPlayer.stop()
                        mediaPlayer.detachViews()
                    } catch (e: Exception) {
                        Log.e("VLCPlayer", "Error pausing playback", e)
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        mediaPlayer.stop()
                        mediaPlayer.detachViews()
                    } catch (e: Exception) {
                        Log.e("VLCPlayer", "Error destroying playback", e)
                    }
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mediaPlayer.stop()
                mediaPlayer.detachViews()
                mediaPlayer.release()
                libVlc.release()
            } catch (e: Exception) {
                Log.e("VLCPlayer", "Error disposing resources", e)
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            factory = { ctx ->
                // Create a VLCVideoLayout
                VLCVideoLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    videoLayout.set(this)

                    // Attach the MediaPlayer to the view
                    // No adjuntar vistas aquí - lo haremos en el ciclo de vida ON_RESUME
                }
            },
            update = { /* No hacer nada en la actualización */ }
        )
    }
}

/**
 * Helper class to keep a reference to VLCVideoLayout
 */
class VLCVideoLayoutRef {
    private var layout: VLCVideoLayout? = null

    fun set(newLayout: VLCVideoLayout) {
        layout = newLayout
    }

    fun get(): VLCVideoLayout? = layout
}