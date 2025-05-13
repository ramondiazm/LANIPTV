package com.example.laniptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.laniptv.data.model.Channel
import com.example.laniptv.data.model.EpgChannel
import com.example.laniptv.data.model.EpgProgram
import com.example.laniptv.data.model.EpgState
import com.example.laniptv.ui.components.LoadingScreenView
import com.example.laniptv.ui.viewmodel.EpgViewModel
import com.example.laniptv.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun EpgScreen(
    mainViewModel: MainViewModel,
    epgViewModel: EpgViewModel,
    onBack: () -> Unit
) {
    val epgState by epgViewModel.epgState.collectAsState()
    val selectedChannelId by epgViewModel.selectedChannelId.collectAsState()
    val selectedDate by epgViewModel.selectedDate.collectAsState()
    val programsForSelectedDay by epgViewModel.programsForSelectedDay.collectAsState()

    val playlistState by mainViewModel.playlistState.collectAsState()

    // Fechas para el selector de días
    val dates = remember {
        val result = mutableListOf<Date>()
        val calendar = Calendar.getInstance()

        // Añadir días desde hoy hasta 6 días adelante (1 semana)
        for (i in 0..6) {
            result.add(calendar.time)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        result
    }

    // Formateadores de fecha
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    LaunchedEffect(Unit) {
        // Cargar la EPG al entrar a la pantalla
        epgViewModel.loadEpg()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Cabecera
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Guía de Programación",
                style = MaterialTheme.typography.headlineMedium
            )

            Button(onClick = onBack) {
                Text("Volver")
            }
        }

        when (epgState) {
            is EpgState.Loading -> {
                LoadingScreenView()
            }

            is EpgState.Error -> {
                val errorMessage = (epgState as EpgState.Error).message
                ErrorScreen(errorMessage) {
                    epgViewModel.loadEpg(true)
                }
            }

            is EpgState.Success -> {
                val channels = (epgState as EpgState.Success).channels

                // Selector de días
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    items(dates) { date ->
                        val isSelected = isSameDay(date, selectedDate)

                        Column(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable { epgViewModel.selectDate(date) }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                                    MaterialTheme.shapes.small
                                )
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = dayFormat.format(date).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = dateFormat.format(date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Lista de canales y programas
                Row(modifier = Modifier.fillMaxSize()) {
                    // Lista de canales (lateral izquierda)
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .padding(end = 8.dp)
                    ) {
                        // Cabecera
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Canales",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        // Lista de canales
                        LazyColumn {
                            items(channels.values.toList()) { channel ->
                                ChannelItem(
                                    channel = channel,
                                    isSelected = channel.id == selectedChannelId,
                                    onClick = { epgViewModel.selectChannel(channel.id) }
                                )
                            }
                        }
                    }

                    // Lista de programas (derecha)
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Cabecera
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Nombre del canal seleccionado o indicación
                            Text(
                                text = selectedChannelId?.let { channels[it]?.name } ?: "Seleccione un canal",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        // Lista de programas
                        if (selectedChannelId != null) {
                            LazyColumn {
                                items(programsForSelectedDay) { program ->
                                    ProgramItem(program = program, timeFormat = timeFormat)
                                }

                                // Mensaje si no hay programas
                                if (programsForSelectedDay.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp)
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No hay información de programación disponible para este día",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Mensaje para seleccionar canal
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Seleccione un canal para ver su programación",
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

@Composable
fun ChannelItem(
    channel: EpgChannel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    }

    Divider()
}

@Composable
fun ProgramItem(
    program: EpgProgram,
    timeFormat: SimpleDateFormat
) {
    // Verificar si el programa está en curso
    val now = Date()
    val isCurrentProgram = now.after(program.startTime) && now.before(program.endTime)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(
                if (isCurrentProgram) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent,
                MaterialTheme.shapes.small
            )
            .border(
                width = if (isCurrentProgram) 1.dp else 0.dp,
                color = if (isCurrentProgram) MaterialTheme.colorScheme.primary
                else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp)
    ) {
        // Horario y título
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${timeFormat.format(program.startTime)} - ${timeFormat.format(program.endTime)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = program.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Descripción
        if (program.description.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = program.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/**
 * Comprueba si dos fechas son el mismo día
 */
private fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance()
    cal1.time = date1
    cal2.time = date2

    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}