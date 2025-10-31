import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pesegato.MainJ
import com.pesegato.RSACrypt
import com.pesegato.data.QRCoder
import com.pesegato.data.Token
import com.pesegato.theme.TriceratopsTheme
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException
import kotlin.io.encoding.Base64

var imageBitmap: ImageBitmap? = null

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Triceratops Composer") {
        var text by remember { mutableStateOf("Click a button to start") }
        var showCreateTokenDialog by remember { mutableStateOf(false) }

        TriceratopsTheme {
            Surface(modifier = Modifier.fillMaxSize()) { // This Surface will draw the background
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Display the image if it exists
                    imageBitmap?.let {
                        Image(bitmap = it, contentDescription = "QR Code")
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // --- Row of Buttons ---
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            val clipboardContent = readClipboard()
                            text = if (clipboardContent != null) {
                                "Clipboard: $clipboardContent"
                            } else {
                                "Clipboard is empty or does not contain text."
                            }
                            val publicKey = RSACrypt.generateOrGetKeyPair()
                            val cert = MainJ.getCertificate("name", Base64.encode(publicKey.encoded))
                            val bufferedImage = QRCoder.showQRCodeOnScreenBI(cert)
                            imageBitmap = bufferedImage?.toComposeImageBitmap()
                        }) {
                            Text("Read Clipboard")
                        }

                        Button(onClick = {
                            showCreateTokenDialog = true
                        }) {
                            Text("Create New Token")
                        }
                    }
                }
            }

            // --- Conditionally display the dialog ---
            if (showCreateTokenDialog) {
                CreateTokenDialog(
                    onDismiss = {
                        showCreateTokenDialog = false
                        text = "Token creation cancelled."
                    },
                    onConfirm = { label, secret, color ->
                        showCreateTokenDialog = false
                        try {
                            MainJ.buildToken(label, color.name, secret, 0)
                            text = "Successfully created token '$label'! Check your user home folder."
                        } catch (e: Exception) {
                            text = "Error creating token: ${e.message}"
                            e.printStackTrace()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CreateTokenDialog(
    onConfirm: (label: String, secret: String, color: Token.Color) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Token.Color.GREEN) }
    val (labelFocus, secretFocus) = remember { FocusRequester.createRefs() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(elevation = 8.dp) {
            Column(
                modifier = Modifier.padding(16.dp).width(300.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Create New Token", style = MaterialTheme.typography.h6)

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Token Label") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { secretFocus.requestFocus() }
                    ),
                    modifier = Modifier.focusRequester(labelFocus)
                )

                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Token Secret") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff

                        val description = if (passwordVisible) "Hide password" else "Show password"

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, description)
                        }
                    },
                    modifier = Modifier.focusRequester(secretFocus)
                )

                // --- Visual Color Picker ---
                ColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

                // --- Action Buttons ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onConfirm(label, secret, selectedColor)
                    }) {
                        Text("Confirm")
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        labelFocus.requestFocus()
    }
}

@Composable
fun ColorPicker(selectedColor: Token.Color, onColorSelected: (Token.Color) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Token.Color.values().forEach { color ->
            val composeColor = tokenColorToComposeColor(color)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(composeColor)
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (color == selectedColor) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = if (composeColor.luminance() > 0.5) Color.Black else Color.White
                    )
                }
            }
        }
    }
}

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

// Helper extension function to determine if a color is light or dark
fun Color.luminance(): Float {
    return (0.2126f * red + 0.7152f * green + 0.0722f * blue)
}
