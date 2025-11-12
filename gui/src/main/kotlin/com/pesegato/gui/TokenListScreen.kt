package com.pesegato.gui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenListScreen(tokens: List<DisplayableToken>, deviceName: String, onBackClick: () -> Unit, onTokenClick: (DisplayableToken) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tokens for $deviceName") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to device list")
                }
            }
        )
        if (tokens.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tokens found for this device.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(tokens) { token ->
                    TokenListItem(token, onTokenClick)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun TokenListItem(token: DisplayableToken, onTokenClick: (DisplayableToken) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTokenClick(token) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(bitmap = token.image, contentDescription = "Token Image", modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(token.label, style = MaterialTheme.typography.titleLarge)
            Text(token.uuid, style = MaterialTheme.typography.labelSmall)
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tokenColorToComposeColor(token.color))
        )
    }
}
