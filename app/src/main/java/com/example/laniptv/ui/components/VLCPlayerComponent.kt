package com.example.laniptv.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.laniptv.ui.viewmodel.MainViewModel
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun VlcPlayerComponent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isDevelopmentMode by viewModel.isDevelopmentMode.collectAsState()

    // Usar un key para evitar recreaciones constantes
    val playerKey = remember { "vlc_player_key" }

    Box(modifier = modifier) {
        // Componente de VLC real
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Crear una sola vez
                VLCVideoLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    viewModel.attachVideoView(this)
                }
            },
            // Actualización mínima - solo usa un id estable
            update = { /* No actualizar nada aquí para evitar recreaciones */ }
        )

        // Modo desarrollo - mostrar overlay
        if (isDevelopmentMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
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
                        text = "Reproduciendo streams de prueba en el emulador",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón para ver diagnóstico
                    Button(onClick = {
                        viewModel.showDiagnosis()
                    }) {
                        Text("Diagnóstico")
                    }
                }
            }
        }
    }
}