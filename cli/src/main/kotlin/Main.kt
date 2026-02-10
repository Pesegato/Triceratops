import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.prompt
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.pesegato.AdbConnector
import com.pesegato.MainJ
import com.pesegato.RSACrypt
import com.pesegato.data.QRCoder
import com.pesegato.data.Token
import com.pesegato.device.DeviceManager
import com.pesegato.security.Config
import com.pesegato.security.SecurityFactory
import com.pesegato.security.Setup
import com.pesegato.t9stoken.getHostname
import com.pesegato.t9stoken.model.SecureToken
import com.pesegato.token.TokenManager
import com.pesegato.web.startWebServer
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Locale.getDefault
import javax.crypto.Cipher
import kotlin.io.encoding.Base64
import kotlin.system.exitProcess

    fun main(args: Array<String>) {
    /*
    val name = "Kotlin"
    println("Hello, " + name + "!")

    for (i in 1..5) {
        println("i = $i")
    }

     */
    val title = green(
        """
    Triceratops    
"""
    )

    val version = "0.1"

    val t = Terminal()
    //t.println(brightRed("You can use any of the standard ANSI colors"))
    t.println(
        Panel(
            content = Text(title),
            title = Text("Welcome"),
            bottomTitle = Text(yellow("v.0.1"), align = TextAlign.RIGHT, width = 20)
        )
    )

        val setup= Setup()
        setup.start(args)

        val deviceManager = DeviceManager()
        val tokenManager = TokenManager()
        if ("--web" in args) {
            println("Modalità Web attivata. Avvio del server Ktor in background...")
            // Avviamo il server in un thread separato per non bloccare la CLI.
            // startWebSer
            Thread { startWebServer(deviceManager, tokenManager) }.start()
        }


    val kp = SecurityFactory.getProtector()
    val rsa = RSACrypt(kp);
    rsa.test()

        val connector = AdbConnector(rsa) { status ->
            println(Date().toString() + " [ADB STATUS]: $status")
        }
        var running = true

        for (device in deviceManager.getAvailableDevices()){
            println("- ${device.displayName} (ID: ${device.id})")
        }
    /*
    t.println(red("Hello, world!"), whitespace = Whitespace.NORMAL)

    val style = (bold + black + strikethrough)
    t.println(
        cyan("You ${(green on white)("can ${style("nest")} styles")} arbitrarily")
    )

    t.println(rgb("#b4eeb4")("You can also use true color and color spaces like HSL"))
*/
        while (running) {

            val choice =
                t.prompt("Please input command", choices = listOf("connect", "disconnect", "devices", "tokens", "delete-token", "delete-device", "send", "decrypt", "create", "show", "reset"))
            when (choice) {
                "connect" -> connector.connect()
                "disconnect" -> connector.disconnect()
                "send" -> {
                    val deviceId = "89f8d2a8"
                    val loadedTokens = tokenManager.getTokensForDevice(deviceId)
                    val tokenUuid = loadedTokens.first().uuid
                    //if (parts.size > 1) {
                    connector.sendToken(File(MainJ.getPath(), "tokens/$deviceId/$tokenUuid").readText())
                    //} else {
                    //    println("Errore: specifica il testo o token da inviare.")
                    //}
                }
                "devices" -> {
                    val deviceList = deviceManager.getAvailableDevices()
                    if (deviceList.isEmpty()) {
                        println("Nessun dispositivo con token trovato.")
                    } else {
                        println("Dispositivi trovati:")
                        deviceList.forEach { println("- ${it.displayName} (ID: ${it.id})") }
                    }
                }
                "tokens" -> {
                    //if (parts.size < 2) {
                    //    println("Errore: specifica l'ID del dispositivo. Es: tokens <device_id>")
                    //} else {
                        val deviceId = "89f8d2a8"
                        val tokenList = tokenManager.getTokensForDevice(deviceId)
                        if (tokenList.isEmpty()) {
                            println("Nessun token trovato per il dispositivo con ID: $deviceId")
                        } else {
                            println("Token per il dispositivo $deviceId:")
                            tokenList.forEach { println("- ${it.label} (UUID: ${it.uuid})") }
                        }
                    }
                //}
                "delete-token" -> {
                    //if (parts.size < 3) {
                    //    println("Errore: specifica ID dispositivo e UUID token. Es: delete-token <device_id> <token_uuid>")
                    //} else {
                    val deviceId = "89f8d2a8"
                        val tokenUuid = "89f8d2a8"//token id
                        if (tokenManager.deleteToken(deviceId, tokenUuid)) {
                            println("Token $tokenUuid eliminato con successo.")
                        } else {
                            println("Errore: impossibile eliminare il token. Controlla gli ID.")
                        }
                    }
                //}
                "delete-device" -> {
                    //if (parts.size < 2) {
                    //    println("Errore: specifica l'ID del dispositivo. Es: delete-device <device_id>")
                    //} else {
                        val deviceId = "89f8d2a8"
                        if (deviceManager.deleteDevice(deviceId)) {
                            println("Dispositivo $deviceId e tutti i suoi token sono stati eliminati.")
                        } else {
                            println("Errore: impossibile eliminare il dispositivo. Controlla l'ID.")
                        }
                    }
                //}

                "exit" -> running = false


                "decrypt" -> {
                    val inputJson: String? // Declare a variable to hold the input

                    if (Config.isDocker) {
                        t.println(yellow("Please paste the content to decrypt below."))
                        t.println(gray("(Press Ctrl+D on a new line when you are finished)"))
                        // Read all text from standard input until the user signals EOF (Ctrl+D)
                        inputJson = System.`in`.bufferedReader().readText()
                    } else {
                        t.println("Reading from system clipboard...")
                        inputJson = readClipboard()
                    }

                    if (inputJson.isNullOrBlank()) {
                        t.println(red("Error: No input data was provided."))
                        exitProcess(1)
                    }

                    val moshi = Moshi.Builder()
                        .add(KotlinJsonAdapterFactory())
                        .build()
                    val jsonAdapterST = moshi.adapter(SecureToken::class.java)
                    val importData = jsonAdapterST.fromJson(inputJson)

                    if (importData == null) {
                        t.println(red("Error: Could not parse the provided JSON data."))
                        exitProcess(1)
                    }

                    //val secret = decr(importData.secret)

                    //println("SECRET: $secret")
                }

                "create" -> {
                    t.println("Now creating a new token");
                    val color = t.prompt("Choose a color", choices = Token.Color::class.java.enumConstants.map {
                        it.name.lowercase(
                            getDefault()
                        )
                    })
                    //val color = t.prompt("Choose a color", choices = listOf("red", "green", "blue", "white", "gray", "black"))
                    val cText = when (color) {
                        "red" -> red on black
                        "green" -> green on black
                        "blue" -> blue on black
                        "white" -> white on gray
                        "gray" -> gray on black
                        else -> black on white
                    }
                    val label = t.prompt("Choose a label")
                    val secret = t.prompt("Now enter the token secret for ${(cText)("$label")}", hideInput = true)
                    val number =
                        t.prompt("At least 2 token parts will be generated. Enter 0 if ok or any number to add more")
                            ?.toIntOrNull()

                    if (number == null) {
                        t.println("Bad input, quit.")
                        exitProcess(1);
                    }

                    MainJ.buildToken(label, color, secret, number);

                }

                "show" -> {
                    val hostname = if (Config.isDocker) Config.hostHostname!! else getHostname()
                    var name = t.prompt("Name of the Device, enter for " + yellow(hostname))

                    if (name.isNullOrEmpty()) {
                        name = hostname
                    }

                    val publicKey = rsa.publicKey

                    println("--- VERIFICA CHIAVE ---");
                    println("Provider: " + publicKey.javaClass.getName());
// Se vedi "sun.security.pkcs11.P11Key$P11PublicKey", è fatta!
                    println("Algoritmo: " + publicKey.algorithm);
                    println("Format: " + publicKey.format);

                    rsa.test()

                    val cert = MainJ.getCertificate(name, Base64.encode(publicKey.encoded))
                    //val c64 = Base64.encode(cert)
                    try {
                        QRCoder.showQRCodeOnScreenSwing(name, cert)
                    } catch (e: Exception) {
                        println("Headless environment, cannot show QR code on screen, falling back to text")
                        QRCoder.showQRCodeOnScreen(cert)
                    }
                    println("Certificate: $cert")
                }

                "reset" -> {
                    println("Launch runTriceratops.sh --resetTPM")
                }
            }
            //connector.disconnect()
            //println("Disconnected.")
        }
    /**
    val terminal = Terminal()
    val a = terminal.textAnimation<Int> { frame ->
        (1..50).joinToString("") {
            val hue = (frame + it) * 3 % 360
            TextColors.hsv(hue, 1, 1)("━")
        }
    }

    terminal.cursor.hide(showOnExit = true)
    repeat(120) {
        a.update(it)
        Thread.sleep(25)
    }

    val progress = progressBarLayout {
        marquee(terminal.theme.warning("my-file-download.bin"), width = 15)
        percentage()
        progressBar()
        completed(style = terminal.theme.success)
        speed("B/s", style = terminal.theme.info)
        timeRemaining(style = magenta)
    }.animateOnThread(terminal)

    val future = progress.execute()

// Update the progress as the download progresses
    progress.update { total = 3_000_000_000 }
    while (!progress.finished) {
        progress.advance(15_000_000)
        Thread.sleep(100)
    }

// Optional: wait for the future to complete so that the final frame of the
// animation is rendered before the program exits.
    future.get()
*/
}

fun readClipboard(): String? {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val contents = clipboard.getContents(null)

    return if (contents?.isDataFlavorSupported(DataFlavor.stringFlavor) == true) {
        try {
            val json = contents.getTransferData(DataFlavor.stringFlavor) as? String

            json
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


/*
fun decr(encrypted: String): String{
    //val encrypted = "BqCe++3LpUUp0kUr8U8W1oC6bcTR6eAMDZkXlhFJLrV55Tol2hx+RoXNGlhPOZDejrikOZN8bWOvYZ33E8jJZaWKUVp/EFfOiWXK2TA4/7QXYo03esyEX08P13LiI1Sw67H4vD7vKXbbYxYzdxTmJG4l3xMsSx/ozEoepH8REOxK8d4P2EGCECVpbejIugW0/SJVRWlowZtKF7qMlycz1Prux340yGcDx9/K6dTxDTuwdKYgIsfkuoaIjquqnh7g8ouUBr1diDQDkhHX0zn3mYO41Lh6m4ojQ/X71bUq8JnQlJE7ifoI7tYA/mZ2rVGNuTNgGoCH07dFRKyj8cmuUg=="

    val privateKey = RSACrypt.getPrivateKeyPath()

    //read from file
    val privateKeyFile = File(RSACrypt.getPrivateKeyPath())

    val privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath())
    val keyFactory = KeyFactory.getInstance("RSA")
    val privateKeySpec: EncodedKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
    val privateKeys = keyFactory.generatePrivate(privateKeySpec)
    println("LOADED THE KEY")


    val decr=decryptDataK(encrypted, privateKeys)

    println("DECRYPTED: $decr"  )
    return decr
}
*/

fun decryptDataK(encryptedText: String, privateKey: PrivateKey): String {

    // Initialize the cipher for decryption
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.DECRYPT_MODE, privateKey)

    // Decrypt the encrypted text
    val decryptedBytes = cipher.doFinal(Base64.decode(encryptedText))
    return String(decryptedBytes)
}

fun decryptData(encryptedText: String, privateKeyString: String): String {
    // Decode the Base64 encoded private key
    val keyBytes = Base64.decode(privateKeyString)
    val keySpec = PKCS8EncodedKeySpec(keyBytes)

    // Generate the private key
    val keyFactory = KeyFactory.getInstance("RSA")
    val privateKey: PrivateKey = keyFactory.generatePrivate(keySpec)

    // Initialize the cipher for decryption
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.DECRYPT_MODE, privateKey)

    // Decrypt the encrypted text
    val decryptedBytes = cipher.doFinal(Base64.decode(encryptedText))
    return String(decryptedBytes)
}