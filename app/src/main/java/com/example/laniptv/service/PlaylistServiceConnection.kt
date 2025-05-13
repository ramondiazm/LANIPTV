package com.example.laniptv.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.example.laniptv.data.model.Channel
import com.example.laniptv.data.model.PlayerState
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Conexión con el servicio de reproducción
 */
class PlaylistServiceConnection(
    private val context: Context,
    private val onPlayerStateChanged: (PlayerState) -> Unit
) : ServiceConnection {

    private var playlistService: PlaylistService? = null
    private var isBound = false
    private val TAG = "ServiceConnection"

    /**
     * Enlaza el servicio
     */
    fun bindService() {
        if (!isBound) {
            val intent = Intent(context, PlaylistService::class.java)
            context.startService(intent)
            context.bindService(intent, this, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Desenlaza el servicio
     */
    fun unbindService() {
        if (isBound) {
            context.unbindService(this)
            isBound = false
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(TAG, "Servicio conectado")
        val binder = service as PlaylistService.LocalBinder
        playlistService = binder.getService()
        isBound = true

        // Configurar el listener para eventos del reproductor
        playlistService?.setOnPlayerStateChangedListener { state ->
            onPlayerStateChanged(state)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(TAG, "Servicio desconectado")
        playlistService = null
        isBound = false
    }

    /**
     * Reproduce un canal
     */
    fun playChannel(channel: Channel) {
        playlistService?.playChannel(channel)
    }

    /**
     * Adjunta la vista de video
     */
    fun attachVideoView(videoLayout: VLCVideoLayout) {
        playlistService?.attachViews(videoLayout)
    }

    /**
     * Pausa la reproducción
     */
    fun pause() {
        playlistService?.pause()
    }

    /**
     * Reanuda la reproducción
     */
    fun resume() {
        playlistService?.resume()
    }
}