package com.pesegato.gui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun DeviceConnectionScreen(statusText: String, isServerRunning: Boolean, onConnectClick: () -> Unit, image: ImageBitmap?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        image?.let {
            Image(bitmap = it, contentDescription = "QR Code")
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(statusText)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onConnectClick, enabled = !isServerRunning) {
            Text("Connect to Device")
        }
    }
}
