import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pesegato.AdbConnector
import com.pesegato.MainJ
import com.pesegato.RSACrypt
import com.pesegato.device.DeviceManager
import com.pesegato.data.QRCoder
import com.pesegato.gui.*
import com.pesegato.model.Device
import com.pesegato.security.SecurityFactory
import com.pesegato.t9stoken.getHostname
import com.pesegato.theme.TriceratopsTheme
import com.pesegato.t9stoken.model.SecureToken
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import kotlin.io.encoding.Base64
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
fun main() = application {
    var text by remember { mutableStateOf("Click 'Connect to Device' to start") }
    var showCreateTokenDialog by remember { mutableStateOf(false) }
    var isServerRunning by remember { mutableStateOf(false) }
    var tokens by remember { mutableStateOf<List<DisplayableToken>>(emptyList()) }
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    val kp = SecurityFactory.getProtector()
    val rsa = RSACrypt(kp);

    // --- Server Setup ---
    val adbServer = remember {
        AdbConnector(rsa) { receivedData ->
            text = receivedData
            isServerRunning = false
        }
    }

    // Istanziamo il nuovo manager
    val deviceManager = remember { DeviceManager() }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    Window(onCloseRequest = {
        adbServer.disconnect()
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
                                    text = if (clipboardContent != null) "Clipboard: $clipboardContent" else "Clipboard is empty or does not contain text."
                                    val publicKey = kp.publicKey
                                    val cert = MainJ.getCertificate(getHostname(), Base64.encode(publicKey.encoded))
                                    imageBitmap = QRCoder.showQRCodeOnScreenBI(cert)?.toComposeImageBitmap()
                                    tokens = emptyList()
                                    selectedTabIndex = 0
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
                                                    devices = deviceManager.getAvailableDevices()
                                                    selectedDevice = null
                                                    tokens = emptyList()
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
                                            adbServer.connect()
                                        },
                                        image = imageBitmap
                                    )
                                    1 -> {
                                        if (selectedDevice == null) {
                                            DeviceListScreen(devices) { device ->
                                                selectedDevice = device
                                                val deviceDir = File(MainJ.getPath(), "tokens/${device.id}")
                                                val tokenFiles = deviceDir.listFiles { _, name -> name.length == 36 } ?: emptyArray()
                                                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                                                val jsonAdapter = moshi.adapter(SecureToken::class.java)

                                                tokens = tokenFiles.mapNotNull { file ->
                                                    try {
                                                        val json = file.readText()
                                                        println("JSON: $json")
                                                        val secureToken = jsonAdapter.fromJson(json)
                                                        if (secureToken != null) {
                                                            val imageName = when (secureToken.color) {
                                                                com.pesegato.data.Token.Color.BLUE -> "t9stokenblue.png"
                                                                com.pesegato.data.Token.Color.GREEN -> "t9stokengreen.png"
                                                                else -> "t9stokenred.png"
                                                            }
                                                            val loadedImage = loadImage(imageName)
                                                            if (loadedImage != null) {
                                                                DisplayableToken(
                                                                    uuid = file.name.removeSuffix(".json"),
                                                                    label = secureToken.label,
                                                                    color = secureToken.color,
                                                                    image = loadedImage.toComposeImageBitmap()
                                                                )
                                                            } else null
                                                        } else null
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        null
                                                    }
                                                }
                                            }
                                        } else {
                                            TokenListScreen(
                                                tokens = tokens,
                                                deviceDisplayName = selectedDevice!!.displayName,
                                                onBackClick = {
                                                    selectedDevice = null
                                                    tokens = emptyList()
                                                },
                                                onTokenClick = { token ->
                                                    text = "Clicked on token: ${token.label}"
                                                    adbServer.sendToken(File(MainJ.getPath(), "tokens/${selectedDevice!!.id}/${token.uuid}").readText())
                                                }
                                            )
                                        }
                                    }
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
