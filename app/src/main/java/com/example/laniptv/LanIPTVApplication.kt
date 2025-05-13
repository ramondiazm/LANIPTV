package com.example.laniptv

import android.app.Application
import com.example.laniptv.data.config.AppConfig
import com.example.laniptv.data.repository.EpgRepository
import com.example.laniptv.data.repository.PlaylistRepository

/**
 * Clase principal de la aplicación que inicializa los componentes
 */
class LanIPTVApplication : Application() {

    // Instancias de los componentes principales
    lateinit var appConfig: AppConfig
    lateinit var playlistRepository: PlaylistRepository
    lateinit var epgRepository: EpgRepository

    override fun onCreate() {
        super.onCreate()

        // Inicializar componentes
        appConfig = AppConfig(this)
        playlistRepository = PlaylistRepository()
        epgRepository = EpgRepository()

        // Configuraciones adicionales aquí
    }

    companion object {
        // Acceso fácil a la instancia de aplicación desde cualquier contexto
        @JvmStatic
        fun from(context: android.content.Context): LanIPTVApplication {
            return context.applicationContext as LanIPTVApplication
        }
    }
}