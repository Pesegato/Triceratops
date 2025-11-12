package com.pesegato.gui

import androidx.compose.ui.graphics.ImageBitmap
import com.pesegato.data.Token

data class DisplayableToken(
    val uuid: String, // The filename
    val label: String,
    val color: Token.Color,
    val image: ImageBitmap
)
