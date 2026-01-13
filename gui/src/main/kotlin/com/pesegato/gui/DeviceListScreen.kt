package com.pesegato.gui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeviceListScreen(devices: List<Pair<String, String>>, onDeviceClick: (Pair<String, String>) -> Unit) {
    if (devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No device folders found in '.Triceratops/tokens/'.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(devices) { devicePair ->
                ListItem(
                    headlineContent = { Text(devicePair.second) }, // Show the display name
                    modifier = Modifier.clickable { onDeviceClick(devicePair) } // Pass the whole pair on click
                )
                HorizontalDivider()
            }
        }
    }
}
