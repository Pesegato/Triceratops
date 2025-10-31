import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pesegato.data.QRCoder
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.pesegato.MainJ
import com.pesegato.RSACrypt
import kotlin.io.encoding.Base64

var imageBitmap: ImageBitmap? = null

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Triceratops Composer") {
        var text by remember { mutableStateOf("Click the button to read the clipboard") }

        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text)
                // Display the image if it exists
                imageBitmap?.let {
                    Image(bitmap = it, contentDescription = "QR Code")
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Button(onClick = {
                    val clipboardContent = readClipboard()
                    text = if (clipboardContent != null) {
                        "Clipboard: $clipboardContent"
                    } else {
                        "Clipboard is empty or does not contain text."
                    }
                    val publicKey = RSACrypt.generateOrGetKeyPair()

                    val cert = MainJ.getCertificate("name", Base64.encode(publicKey.encoded))
                    //val c64 = Base64.encode(cert)
                    val bufferedImage = QRCoder.showQRCodeOnScreenBI(cert)

                    imageBitmap = bufferedImage?.toComposeImageBitmap()
                }) {
                    Text("Read Clipboard")
                }
            }
        }
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
