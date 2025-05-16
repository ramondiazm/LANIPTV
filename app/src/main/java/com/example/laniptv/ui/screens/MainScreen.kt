package com.example.laniptv.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.laniptv.data.model.Channel
import com.example.laniptv.data.model.EpgState
import com.example.laniptv.data.model.PlaylistUiState
import com.example.laniptv.ui.components.CategoryList
import com.example.laniptv.ui.components.ChannelCard
import com.example.laniptv.ui.components.LoadingScreenView
import com.example.laniptv.ui.components.SearchBar
import com.example.laniptv.ui.viewmodel.EpgViewModel
import com.example.laniptv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding


@Composable
fun MainScreen(
    viewModel: MainViewModel,
    epgViewModel: EpgViewModel,
    onSettingsClick: () -> Unit,
    onEpgClick: () -> Unit
) {
    val context = LocalContext.current
    val playlistState by viewModel.playlistState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        when (playlistState) {
            is PlaylistUiState.Loading -> {
                LoadingScreenView()
            }

            is PlaylistUiState.Error -> {
                val errorMessage = (playlistState as PlaylistUiState.Error).message
                ErrorScreen(errorMessage = errorMessage) {
                    // Botón para reintentar cargar la lista
                    coroutineScope.launch {
                        viewModel.loadPlaylist("http://tu-servidor-local/playlist.m3u8")
                    }
                }
            }

            is PlaylistUiState.Success -> {
                val playlist = (playlistState as PlaylistUiState.Success).playlist

                if (isFullscreen) {
                    // Pantalla completa con el canal seleccionado
                    FullscreenPlayerView(
                        viewModel = viewModel,
                        selectedChannel = selectedChannel,
                        epgViewModel = epgViewModel
                    )
                } else {
                    // Vista normal con categorías y lista de canales
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Panel lateral izquierdo con categorías
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column {
                                // Botones de navegación
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Row {
                                        // Botón de EPG
                                        IconButton(onClick = onEpgClick) {
                                            // Ícono de EPG
                                            Text("📺")
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Botón de configuración
                                        IconButton(onClick = onSettingsClick) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Configuración"
                                            )
                                        }
                                    }
                                }

                                // Barra de búsqueda
                                SearchBar(
                                    onSearch = viewModel::searchChannels,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Lista de categorías
                                CategoryList(
                                    categories = playlist.categories,
                                    selectedCategory = selectedCategory,
                                    onCategorySelected = viewModel::selectCategory
                                )
                            }
                        }

                        // Área principal dividida en canales y reproductor
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Área de reproducción (2/3 de la pantalla)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(2f)
                                    .background(Color.Black)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (selectedChannel != null) {
                                                    viewModel.toggleFullscreen()
                                                }
                                            }
                                        )
                                    }
                            ) {
                                // Componente de reproductor VLC
                                VlcPlayerComponent(
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Información básica del canal actual (siempre visible)
                                selectedChannel?.let { channel ->
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(16.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.7f),
                                                shape = MaterialTheme.shapes.small
                                            )
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = channel.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Lista de canales (1/3 de la pantalla)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                val isSearchActive by viewModel.isSearchActive.collectAsState()
                                val filteredChannels by viewModel.filteredChannels.collectAsState()
                                val channelsToShow = if (isSearchActive) {
                                    filteredChannels
                                } else {
                                    selectedCategory?.channels ?: emptyList()
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    item {
                                        Text(
                                            text = if (isSearchActive) {
                                                "Resultados de búsqueda (${filteredChannels.size})"
                                            } else {
                                                selectedCategory?.name ?: "Todos los canales"
                                            },
                                            style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    items(channelsToShow) { channel ->
                                        ChannelCard(
                                            channel = channel,
                                            isSelected = channel.id == selectedChannel?.id,
                                            onClick = { viewModel.selectChannel(channel) },
                                            currentProgram = null
                                        )
                                    }

                                    // Mensaje cuando no hay canales
                                    if (channelsToShow.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (isSearchActive) "No se encontraron canales" else "Esta categoría no tiene canales",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Componente separado para VLC que evita la duplicación de código
 */
@Composable
fun VlcPlayerComponent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isDevelopmentMode by viewModel.isDevelopmentMode.collectAsState()

    Box(modifier = modifier) {
        // Componente de VLC real
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    viewModel.attachVideoView(this)
                }
            }
        )

        // Modo desarrollo - mostrar overlay
        if (isDevelopmentMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "MODO DESARROLLO",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "La reproducción de video está desactivada en el emulador.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FullscreenPlayerView(
    viewModel: MainViewModel,
    selectedChannel: Channel?,
    epgViewModel: EpgViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        viewModel.toggleFullscreen()
                    }
                )
            }
    ) {
        // Componente de reproductor VLC
        VlcPlayerComponent(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // Barra flotante con controles e información del canal
        ChannelControlBar(
            channel = selectedChannel,
            onPreviousChannel = { viewModel.previousChannel() },
            onNextChannel = { viewModel.nextChannel() },
            onExitFullscreen = { viewModel.toggleFullscreen() },
            epgViewModel = epgViewModel
        )
    }
}

@Composable
fun ChannelControlBar(
    channel: Channel?,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onExitFullscreen: () -> Unit,
    epgViewModel: EpgViewModel
) {
    var isVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Estado del EPG
    val epgState by epgViewModel.epgState.collectAsState()
    val currentPrograms by epgViewModel.currentPrograms.collectAsState()

    // Definir el formato de tiempo aquí para que esté disponible en todo el componente
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Auto-hide timer
    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(5000)  // 5 segundos
            if (System.currentTimeMillis() - lastInteractionTime > 4800) {
                isVisible = false
            }
        }
    }

    // Hacer visible la barra con un tap
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        isVisible = !isVisible
                        lastInteractionTime = System.currentTimeMillis()
                    }
                )
            }
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),  // Aumento el tamaño para acomodar más información
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Barra principal con controles e información del canal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Botón anterior
                        IconButton(onClick = {
                            onPreviousChannel()
                            lastInteractionTime = System.currentTimeMillis()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Canal anterior",
                                tint = Color.White
                            )
                        }

                        // Información del canal con logo
                        channel?.let {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Logo del canal
                                if (!it.logoUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(it.logoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Logo de ${it.name}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .padding(end = 8.dp)
                                    )
                                }

                                // Nombre del canal
                                Text(
                                    text = it.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Controles a la derecha
                        Row {
                            // Botón de salir de fullscreen
                            IconButton(onClick = {
                                onExitFullscreen()
                                lastInteractionTime = System.currentTimeMillis()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Salir de pantalla completa",
                                    tint = Color.White
                                )
                            }

                            // Botón siguiente
                            IconButton(onClick = {
                                onNextChannel()
                                lastInteractionTime = System.currentTimeMillis()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Canal siguiente",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Información del programa actual
                    if (channel != null && epgState is EpgState.Success) {
                        // Intentar encontrar información de EPG para este canal
                        val epgChannelId = findEpgChannelId(channel, epgState)

                        epgChannelId?.let { channelId ->
                            val currentProgram = currentPrograms[channelId]
                            val nextProgram = if (currentProgram != null) {
                                epgViewModel.getNextProgram(channelId)
                            } else null

                            currentProgram?.let { program ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    // Título y horario del programa
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Título del programa
                                        Text(
                                            text = program.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Horario
                                        Text(
                                            text = "${timeFormat.format(program.startTime)} - ${timeFormat.format(program.endTime)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }

                                    // Barra de progreso
                                    val progress by remember(program) {
                                        derivedStateOf {
                                            val now = System.currentTimeMillis()
                                            val programStart = program.startTime.time
                                            val programEnd = program.endTime.time
                                            val duration = programEnd - programStart
                                            if (duration <= 0) 0f else (now - programStart).toFloat() / duration
                                        }
                                    }

                                    LinearProgressIndicator(
                                        progress = progress.coerceIn(0f, 1f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )

                                    // Descripción del programa
                                    if (program.description.isNotEmpty()) {
                                        Text(
                                            text = program.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // A continuación
                                    nextProgram?.let { next ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "A continuación:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.padding(end = 4.dp)
                                            )

                                            Text(
                                                text = "${timeFormat.format(next.startTime)}: ${next.title}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Función auxiliar para encontrar el ID del canal en la EPG
 */
private fun findEpgChannelId(channel: Channel, epgState: EpgState): String? {
    return if (epgState is EpgState.Success) {
        val epgChannels = epgState.channels
        epgChannels.entries.find { (_, epgChannel) ->
            val normalizedEpgName = epgChannel.name.trim().lowercase()
            val normalizedChannelName = channel.name.trim().lowercase()

            normalizedEpgName == normalizedChannelName ||
                    normalizedEpgName.contains(normalizedChannelName) ||
                    normalizedChannelName.contains(normalizedEpgName)
        }?.key
    } else null
}

@Composable
fun ErrorScreen(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error al cargar la lista",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 16.dp),
            textAlign = TextAlign.Center
        )
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}