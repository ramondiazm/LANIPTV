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
    private val TAG = "PlaylistService"

    // ID del canal de notificaciones
    private val CHANNEL_ID = "LanIPTVServiceChannel"
    // ID de la notificación
    private val NOTIFICATION_ID = 1

    /**
     * Binder para la conexión con el servicio
     */
    inner class LocalBinder : Binder() {
        fun getService(): PlaylistService = this@PlaylistService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio PlaylistService creado")

        // Crear el canal de notificaciones
        createNotificationChannel()

        try {
            // Inicializar el reproductor VLC
            vlcPlayerManager = VlcPlayerManager(this) { state ->
                onPlayerStateChanged?.invoke(state)
            }

            vlcPlayerManager?.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear VlcPlayerManager: ${e.message}", e)
        }
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
        try {
            currentChannel = channel
            vlcPlayerManager?.playChannel(channel)
            Log.d(TAG, "Reproduciendo canal: ${channel.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir canal: ${e.message}", e)
            onPlayerStateChanged?.invoke(PlayerState.Error("Error al reproducir: ${e.message}"))
        }
    }

    /**
     * Asocia la vista de video para la reproducción
     */
    fun attachViews(videoLayout: VLCVideoLayout) {
        try {
            vlcPlayerManager?.attachViews(videoLayout)
            Log.d(TAG, "Vistas adjuntadas al reproductor")
        } catch (e: Exception) {
            Log.e(TAG, "Error al adjuntar vistas: ${e.message}", e)
        }
    }

    /**
     * Desasocia la vista de video
     */
    fun detachViews() {
        try {
            vlcPlayerManager?.detachViews()
            Log.d(TAG, "Vistas desacopladas del reproductor")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desacoplar vistas: ${e.message}", e)
        }
    }

    /**
     * Establece un listener para los cambios de estado del reproductor
     */
    fun setOnPlayerStateChangedListener(listener: (PlayerState) -> Unit) {
        onPlayerStateChanged = listener
        Log.d(TAG, "Listener de estado del reproductor configurado")
    }

    /**
     * Pausa la reproducción
     */
    fun pause() {
        try {
            vlcPlayerManager?.pause()
            Log.d(TAG, "Reproducción pausada")
        } catch (e: Exception) {
            Log.e(TAG, "Error al pausar: ${e.message}", e)
        }
    }

    /**
     * Reanuda la reproducción
     */
    fun resume() {
        try {
            vlcPlayerManager?.resume()
            Log.d(TAG, "Reproducción reanudada")
        } catch (e: Exception) {
            Log.e(TAG, "Error al reanudar: ${e.message}", e)
        }
    }

    /**
     * Obtiene información del reproductor para diagnóstico
     */
    fun getMediaPlayerInfo(): String {
        return try {
            val vlcInfo = vlcPlayerManager?.getMediaPlayerInfo() ?: "Reproductor VLC no disponible"
            val currentChannelInfo = currentChannel?.let {
                "Canal actual: ${it.name}\nURL: ${it.streamUrl}\n"
            } ?: "Sin canal seleccionado\n"

            "=== DIAGNÓSTICO DEL SERVICIO ===\n" +
                    "$currentChannelInfo" +
                    "$vlcInfo\n" +
                    "Servicio enlazado: Sí\n" +
                    "Servicio en primer plano: Sí"
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener información de diagnóstico: ${e.message}", e)
            "Error al obtener información: ${e.message}"
        }
    }

    /**
     * Obtiene el canal actual
     */
    fun getCurrentChannel(): Channel? {
        return currentChannel
    }

    /**
     * Verifica si el reproductor está reproduciéndose
     */
    fun isPlaying(): Boolean {
        // Esta función podría implementarse en VlcPlayerManager si es necesaria
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destruyendo PlaylistService")

        try {
            // Liberar recursos
            vlcPlayerManager?.release()
            vlcPlayerManager = null
            currentChannel = null
            onPlayerStateChanged = null
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar recursos en onDestroy: ${e.message}", e)
        }
    }
}