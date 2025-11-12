package com.pesegato.gui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.pesegato.data.Token
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO

fun tokenColorToComposeColor(color: Token.Color): Color {
    return when (color) {
        Token.Color.GREEN -> Color(0xFF008000)
        Token.Color.RED -> Color.Red
        Token.Color.BLUE -> Color.Blue
        Token.Color.WHITE -> Color.White
        Token.Color.GRAY -> Color.Gray
        Token.Color.BLACK -> Color.Black
    }
}

fun readClipboard(): String? {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val contents = clipboard.getContents(null)

    return if (contents?.isDataFlavorSupported(DataFlavor.stringFlavor) == true) {
        try {
            contents.getTransferData(DataFlavor.stringFlavor) as? String
        } catch (e: UnsupportedFlavorException) {
            println("Clipboard content is not a string: ${e.message}")
            null
        } catch (e: IOException) {
            println("Could not access clipboard: ${e.message}")
            null
        }
    } else {
        null
    }
}

fun loadImage(resourcePath: String): BufferedImage? {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
    return if (stream != null) {
        try {
            ImageIO.read(stream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    } else {
        println("Image not found: $resourcePath")
        null
    }
}

// Helper extension function to determine if a color is light or dark
fun Color.luminance(): Float {
    return (0.2126f * red + 0.7152f * green + 0.0722f * blue)
}
