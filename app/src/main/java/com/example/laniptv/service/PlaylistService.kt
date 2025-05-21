package com.example.laniptv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.laniptv.MainActivity
import com.example.laniptv.R
import com.example.laniptv.data.model.Channel
import com.example.laniptv.data.model.PlayerState
import com.example.laniptv.player.VlcPlayerManager
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Servicio para reproducir canales IPTV en segundo plano
 */
class PlaylistService : Service() {

    private val binder = LocalBinder()
    private var vlcPlayerManager: VlcPlayerManager? = null
    private var currentChannel: Channel? = null
    private var onPlayerStateChanged: ((PlayerState) -> Unit)? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // ID del canal de notificaciones
    private val CHANNEL_ID = "LanIPTVServiceChannel"
    // ID de la notificación
    private val NOTIFICATION_ID = 1

    private val TAG = "PlaylistService"

    /**
     * Binder para la conexión con el servicio
     */
    inner class LocalBinder : Binder() {
        fun getService(): PlaylistService = this@PlaylistService
    }

    override fun onCreate() {
        super.onCreate()
        // Crear el canal de notificaciones
        createNotificationChannel()

        // Adquirir un wakelock parcial para mantener la CPU encendida durante la reproducción
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LanIPTV::PlaybackWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutos
        } catch (e: Exception) {
            Log.e(TAG, "Error al adquirir wakelock: ${e.message}")
        }

        // Inicializar el reproductor VLC
        vlcPlayerManager = VlcPlayerManager(this) { state ->
            // Propagar estado al listener en la actividad
            onPlayerStateChanged?.invoke(state)

            // Actualizar notificación según el estado
            updateNotificationForPlayerState(state)
        }

        vlcPlayerManager?.initialize()

        Log.d(TAG, "Servicio PlaylistService creado")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Mostrar notificación para servicio en primer plano
        val notification = createNotification("Reproduciendo IPTV")
        startForeground(NOTIFICATION_ID, notification)

        // Manejar la acción de intent
        intent?.let { handleIntent(it) }

        return START_STICKY
    }

    /**
     * Maneja las acciones enviadas mediante intents
     */
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            "play" -> {
                // Obtener datos del canal desde el intent
                val channelId = intent.getStringExtra("channelId")
                val channelName = intent.getStringExtra("channelName")
                val streamUrl = intent.getStringExtra("streamUrl")

                if (channelId != null && channelName != null && streamUrl != null) {
                    val channel = Channel(
                        id = channelId,
                        name = channelName,
                        streamUrl = streamUrl,
                        logoUrl = intent.getStringExtra("logoUrl"),
                        categoryName = intent.getStringExtra("categoryName") ?: "Sin categoría"
                    )

                    playChannel(channel)

                    // Actualizar notificación con el nombre del canal
                    val notification = createNotification("Reproduciendo: ${channel.name}")
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
            "pause" -> {
                vlcPlayerManager?.pause()
            }
            "resume" -> {
                vlcPlayerManager?.resume()
            }
            "stop" -> {
                stopSelf()
            }
        }
    }

    /**
     * Actualiza la notificación según el estado del reproductor
     */
    private fun updateNotificationForPlayerState(state: PlayerState) {
        val message = when (state) {
            is PlayerState.Playing -> "Reproduciendo: ${currentChannel?.name ?: "Canal"}"
            is PlayerState.Paused -> "Pausado: ${currentChannel?.name ?: "Canal"}"
            is PlayerState.Loading -> "Cargando: ${currentChannel?.name ?: "Canal"}"
            is PlayerState.Error -> "Error: ${state.message}"
            else -> "IPTV Player"
        }

        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Crea el canal de notificaciones (requerido para Android O y superior)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "LAN IPTV Service"
            val descriptionText = "Canal para el servicio de reproducción IPTV"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Crea una notificación
     */
    private fun createNotification(contentText: String): Notification {
        // Intent para abrir la actividad principal al hacer clic en la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Crear notificación
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LAN IPTV")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Reproduce un canal
     */
    fun playChannel(channel: Channel) {
        currentChannel = channel
        vlcPlayerManager?.playChannel(channel)
    }

    /**
     * Asocia la vista de video para la reproducción
     */
    fun attachViews(videoLayout: VLCVideoLayout) {
        vlcPlayerManager?.attachViews(videoLayout)
    }

    /**
     * Desasocia la vista de video
     */
    fun detachViews() {
        vlcPlayerManager?.detachViews()
    }

    /**
     * Establece un listener para los cambios de estado del reproductor
     */
    fun setOnPlayerStateChangedListener(listener: (PlayerState) -> Unit) {
        onPlayerStateChanged = listener
    }

    /**
     * Pausa la reproducción
     */
    fun pause() {
        vlcPlayerManager?.pause()
    }

    /**
     * Reanuda la reproducción
     */
    fun resume() {
        vlcPlayerManager?.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar recursos
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar wakelock: ${e.message}")
        }

        vlcPlayerManager?.release()
        vlcPlayerManager = null

        Log.d(TAG, "Servicio PlaylistService destruido")
    }
}