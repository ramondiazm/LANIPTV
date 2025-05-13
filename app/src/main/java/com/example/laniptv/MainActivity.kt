package com.example.laniptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.laniptv.ui.screens.EpgScreen
import com.example.laniptv.ui.screens.MainScreen
import com.example.laniptv.ui.screens.SettingsScreen
import com.example.laniptv.ui.theme.LanIPTVTheme
import com.example.laniptv.ui.viewmodel.EpgViewModel
import com.example.laniptv.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LanIPTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LanIPTVApp()
                }
            }
        }
    }

    // Aseguramos que el servicio se detenga al finalizar la actividad
    override fun onDestroy() {
        super.onDestroy()
        val intent = intent
        intent.setClass(this, com.example.laniptv.service.PlaylistService::class.java)
        stopService(intent)
    }
}

// Enum para representar las pantallas de la aplicación
enum class Screen {
    Main,
    Settings,
    Epg
}

@Composable
fun LanIPTVApp() {
    // Configuramos los ViewModels
    val mainViewModel: MainViewModel = viewModel()
    val epgViewModel: EpgViewModel = viewModel()

    // Estado para controlar la navegación
    var currentScreen by remember { mutableStateOf(Screen.Main) }

    when (currentScreen) {
        Screen.Main -> {
            MainScreen(
                viewModel = mainViewModel,
                epgViewModel = epgViewModel,
                onSettingsClick = { currentScreen = Screen.Settings },
                onEpgClick = { currentScreen = Screen.Epg }
            )
        }
        Screen.Settings -> {
            SettingsScreen(
                onBackPressed = { currentScreen = Screen.Main },
                onSaveSettings = { url ->
                    // Recargar la lista con la nueva URL
                    mainViewModel.loadPlaylist(url)
                    currentScreen = Screen.Main
                }
            )
        }
        Screen.Epg -> {
            EpgScreen(
                mainViewModel = mainViewModel,
                epgViewModel = epgViewModel,
                onBack = { currentScreen = Screen.Main }
            )
        }
    }
}