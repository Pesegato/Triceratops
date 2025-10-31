import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.prompt
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.pesegato.data.Token
import com.pesegato.t9stoken.model.SecureToken
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.file.Files
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Locale.getDefault
import javax.crypto.Cipher
import kotlin.io.encoding.Base64
import kotlin.jvm.java
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

    val onDocker = args.isNotEmpty() && args[0] == "-docker"
    MainJ.setDockerEnvironment(onDocker)
    val hostHostname = getHostHostname()
    if (onDocker){
        t.println("Docker running on host: $hostHostname")
    } else {
        t.println("AWT is available")
    }
    /*
    t.println(red("Hello, world!"), whitespace = Whitespace.NORMAL)

    val style = (bold + black + strikethrough)
    t.println(
        cyan("You ${(green on white)("can ${style("nest")} styles")} arbitrarily")
    )

    t.println(rgb("#b4eeb4")("You can also use true color and color spaces like HSL"))
*/

    val choice=t.prompt("Please input command", choices = listOf("decrypt", "create", "show"))
    if (choice=="decrypt"){
        val str=readClipboard()
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val jsonAdapterST = moshi.adapter(SecureToken::class.java)
        val import = jsonAdapterST.fromJson(str!!)
        val secret=decr(import!!.secret)

        println("SECRET: $secret")
    }
    else if (choice=="create") {
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
            t.prompt("At least 2 token parts will be generated. Enter 0 if ok or any number to add more")?.toIntOrNull()

        if (number == null) {
            t.println("Bad input, quit.")
            exitProcess(1);
        }

        MainJ.buildToken(label, color, secret, number);

    }
    else if (choice=="show") {
        val hostname = if (onDocker) hostHostname!! else getHostname()
        var name = t.prompt("Name of the Device, enter for "+yellow(hostname))

        if (name.isNullOrEmpty()){
            name = hostname
        }

        val publicKey = RSACrypt.generateOrGetKeyPair()

        val cert = MainJ.getCertificate(name, Base64.encode(publicKey.encoded))
        //val c64 = Base64.encode(cert)
        try {
            MainJ.showQRCodeOnScreenSwing(name, cert)
        } catch (e: Exception) {
            println("Headless environment, cannot show QR code on screen, falling back to text")
            MainJ.showQRCodeOnScreen(cert)
        }
        println("Certificate: $cert")
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

/**
 * Retrieves the hostname of the local machine.
 *
 * @return The hostname as a String, or a fallback value like "unknown" if it cannot be determined.
 */
fun getHostname(): String {
    return try {
        InetAddress.getLocalHost().hostName
    } catch (e: UnknownHostException) {
        println("Could not determine hostname: ${e.message}")
        "unknown" // Fallback value
    }
}

fun getHostHostname(): String? {
    return System.getenv("HOST_HOSTNAME")
}

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