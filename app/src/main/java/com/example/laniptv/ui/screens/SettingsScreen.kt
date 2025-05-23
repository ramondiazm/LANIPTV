package com.example.laniptv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.laniptv.LanIPTVApplication
import com.example.laniptv.data.config.AppConfig
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPressed: () -> Unit,
    onSaveSettings: (String) -> Unit
) {
    val context = LocalContext.current
    val appConfig = LanIPTVApplication.from(context).appConfig
    val coroutineScope = rememberCoroutineScope()

    // Estado para la URL de la lista
    var playlistUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Estado para el modo de desarrollo
    var developmentMode by remember { mutableStateOf(false) }

    // Cargar la URL actual
    LaunchedEffect(Unit) {
        playlistUrl = appConfig.playlistUrl.firstOrNull() ?: AppConfig.DEFAULT_PLAYLIST_URL
        appConfig.isDevelopmentMode.firstOrNull()?.let {
            developmentMode = it
        }
        isLoading = false
    }

    // Mostrar pantalla de carga mientras se obtienen los datos
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        // Ícono de retroceso
                        Text("←")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sección de configuración de la lista
            Text(
                text = "Lista de reproducción",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = playlistUrl,
                onValueChange = { playlistUrl = it },
                label = { Text("URL de la lista M3U8") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        appConfig.savePlaylistUrl(playlistUrl)
                        onSaveSettings(playlistUrl)
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Guardar cambios")
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Sección de modo de desarrollo
            Text(
                text = "Modo de desarrollo",
                style = MaterialTheme.typography.titleLarge
            )

            // Switch para activar/desactivar el modo de desarrollo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Activar modo de desarrollo",
                    style = MaterialTheme.typography.bodyLarge
                )

                Switch(
                    checked = developmentMode,
                    onCheckedChange = { newValue ->
                        developmentMode = newValue
                        coroutineScope.launch {
                            appConfig.saveDevelopmentMode(newValue)
                        }
                    }
                )
            }

            Text(
                text = "El modo de desarrollo muestra contenido de muestra en lugar de intentar reproducir streams reales. Útil para pruebas en emuladores.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Acerca de la aplicación
            Text(
                text = "Acerca de",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "LAN IPTV v1.0",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Aplicación para reproducir canales IPTV a través de la red local.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}