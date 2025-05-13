package com.example.laniptv.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Clase encargada de gestionar la configuración de la aplicación
 */
class AppConfig(private val context: Context) {

    companion object {
        // Extension property para dataStore
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

        // Keys para DataStore
        val PLAYLIST_URL_KEY = stringPreferencesKey("playlist_url")
        val EPG_URL_KEY = stringPreferencesKey("epg_url")
        val LAST_CHANNEL_ID_KEY = stringPreferencesKey("last_channel_id")
        val LAST_CATEGORY_NAME_KEY = stringPreferencesKey("last_category_name")

        // Valores por defecto
        const val DEFAULT_PLAYLIST_URL = "https://opop.pro/XLE8sWYgsUXvNp"
        const val DEFAULT_EPG_URL = "https://opop.pro/22AWtsbCszVyW"
    }

    /**
     * Obtiene la URL de la lista de reproducción
     */
    val playlistUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PLAYLIST_URL_KEY] ?: DEFAULT_PLAYLIST_URL
        }

    /**
     * Guarda la URL de la lista de reproducción
     */
    suspend fun savePlaylistUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PLAYLIST_URL_KEY] = url
        }
    }

    /**
     * Obtiene la URL de la EPG
     */
    val epgUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[EPG_URL_KEY] ?: DEFAULT_EPG_URL
        }

    /**
     * Guarda la URL de la EPG
     */
    suspend fun saveEpgUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[EPG_URL_KEY] = url
        }
    }

    /**
     * Obtiene el ID del último canal visualizado
     */
    val lastChannelId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_CHANNEL_ID_KEY]
        }

    /**
     * Guarda el ID del último canal visualizado
     */
    suspend fun saveLastChannelId(channelId: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CHANNEL_ID_KEY] = channelId
        }
    }

    /**
     * Obtiene el nombre de la última categoría seleccionada
     */
    val lastCategoryName: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_CATEGORY_NAME_KEY]
        }

    /**
     * Guarda el nombre de la última categoría seleccionada
     */
    suspend fun saveLastCategoryName(categoryName: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CATEGORY_NAME_KEY] = categoryName
        }
    }
}