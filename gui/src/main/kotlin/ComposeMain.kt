import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pesegato.AdbServer
import com.pesegato.MainJ
import com.pesegato.RSACrypt
import com.pesegato.data.QRCoder
import com.pesegato.data.Token
import com.pesegato.t9stoken.getHostname
import com.pesegato.theme.TriceratopsTheme
import com.pesegato.t9stoken.model.SecureToken
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlinx.coroutines.launch

// Data class to hold all information for display
data class DisplayableToken(
    val uuid: String, // The filename
    val label: String,
    val color: Token.Color,
    val image: ImageBitmap
)

@OptIn(ExperimentalMaterial3Api::class)
fun main() = application {
    var text by remember { mutableStateOf("Click 'Connect to Device' to start") }
    var showCreateTokenDialog by remember { mutableStateOf(false) }
    var isServerRunning by remember { mutableStateOf(false) }
    var tokens by remember { mutableStateOf<List<DisplayableToken>>(emptyList()) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    // --- Server Setup ---
    val adbServer = remember {
        AdbServer { receivedData ->
            // This callback runs on the main UI thread
            text = receivedData
            isServerRunning = false // Re-enable the button when the process is done
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    Window(onCloseRequest = {
        adbServer.stop()
        exitApplication()
    }, title = "Triceratops Composer") {
        TriceratopsTheme {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Column(modifier = Modifier.fillMaxHeight().width(250.dp).padding(16.dp)) {
                            Text("Actions", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(16.dp))

                            NavigationDrawerItem(
                                label = { Text("Read Clipboard") },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    val clipboardContent = readClipboard()
                                    text = if (clipboardContent != null) {
                                        "Clipboard: $clipboardContent"
                                    } else {
                                        "Clipboard is empty or does not contain text."
                                    }
                                    val publicKey = RSACrypt.generateOrGetKeyPair()
                                    val cert = MainJ.getCertificate(getHostname(), Base64.encode(publicKey.encoded))
                                    val bufferedImage = QRCoder.showQRCodeOnScreenBI(cert)
                                    imageBitmap = bufferedImage?.toComposeImageBitmap()
                                    tokens = emptyList()
                                    selectedTabIndex = 0 // Switch to the connection tab to show the QR code
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Create New Token") },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    showCreateTokenDialog = true
                                }
                            )
                        }
                    }
                },
                content = {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Triceratops") },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.apply { if (isClosed) open() else close() } } }) {
                                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                    }
                                }
                            )
                        },
                        content = { paddingValues ->
                            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                                val tabs = listOf("Device Connection", "Tokens")
                                TabRow(selectedTabIndex = selectedTabIndex) {
                                    tabs.forEachIndexed { index, title ->
                                        Tab(
                                            selected = selectedTabIndex == index,
                                            onClick = {
                                                selectedTabIndex = index
                                                if (index == 1) { // "Tokens" tab selected
                                                    val tokenFiles = File(MainJ.getPath()+"/POCO X6 5G").listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
                                                    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                                                    val jsonAdapter = moshi.adapter(SecureToken::class.java)

                                                    tokens = tokenFiles.mapNotNull { file ->
                                                        try {
                                                            val json = file.readText()
                                                            val secureToken = jsonAdapter.fromJson(json)
                                                            if (secureToken != null) {
                                                                val imageName = when (secureToken.color) {
                                                                    Token.Color.BLUE -> "t9stokenblue.png"
                                                                    Token.Color.GREEN -> "t9stokengreen.png"
                                                                    else -> "t9stokenred.png"
                                                                }
                                                                val loadedImage = loadImage(imageName)
                                                                val imageBitmap = loadedImage?.toComposeImageBitmap()

                                                                if (imageBitmap != null) {
                                                                    DisplayableToken(
                                                                        uuid = file.name.removeSuffix(".json"),
                                                                        label = secureToken.label,
                                                                        color = secureToken.color,
                                                                        image = imageBitmap
                                                                    )
                                                                } else null
                                                            } else null
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                            null
                                                        }
                                                    }
                                                    text = "Showing ${tokens.size} tokens."
                                                    imageBitmap = null // Clear the single QR code
                                                }
                                            },
                                            text = { Text(title) }
                                        )
                                    }
                                }

                                // Content for the selected tab
                                when (selectedTabIndex) {
                                    0 -> DeviceConnectionScreen(
                                        statusText = text,
                                        isServerRunning = isServerRunning,
                                        onConnectClick = {
                                            isServerRunning = true
                                            adbServer.receiveToken()
                                        },
                                        image = imageBitmap
                                    )
                                    1 -> TokenListScreen(tokens)
                                }
                            }
                        }
                    )
                }
            )

            // --- Conditionally display the dialog ---
            if (showCreateTokenDialog) {
                CreateTokenDialog(
                    onDismiss = {
                        showCreateTokenDialog = false
                        text = "Token creation cancelled."
                    },
                    onConfirm = { label, secret, color, number ->
                        showCreateTokenDialog = false
                        try {
                            MainJ.buildToken(label, color.name, secret, number - 2)
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

@Composable
fun TokenListScreen(tokens: List<DisplayableToken>) {
    if (tokens.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tokens found.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(tokens) { token ->
                TokenListItem(token)
                HorizontalDivider()
            }
        }
    }
}


@Composable
fun TokenListItem(token: DisplayableToken) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CreateTokenDialog(
    onConfirm: (label: String, secret: String, color: Token.Color, number: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var numberInput by remember { mutableStateOf("2") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Token.Color.GREEN) }
    val (labelFocus, secretFocus) = remember { FocusRequester.createRefs() }

    val isNumberValid = numberInput.toIntOrNull()?.let { it >= 2 } ?: false

    Dialog(onDismissRequest = onDismiss) {
        Surface(tonalElevation = 8.dp) {
            Column(
                modifier = Modifier.padding(16.dp).width(300.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Create New Token", style = MaterialTheme.typography.titleLarge)

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

                OutlinedTextField(
                    value = numberInput,
                    onValueChange = { numberInput = it },
                    label = { Text("Number of Parts") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isNumberValid
                )
                if (!isNumberValid) {
                    Text("Must be a number greater than or equal to 2", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

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
                    Button(
                        onClick = { onConfirm(label, secret, selectedColor, numberInput.toInt()) },
                        enabled = isNumberValid && label.isNotBlank() && secret.isNotBlank()
                    ) {
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
        Token.Color.entries.forEach { color ->
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
