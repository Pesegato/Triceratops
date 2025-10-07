import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.prompt
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.pesegato.data.Token
import java.util.Locale.getDefault
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

    val onDocker = args.isNotEmpty() && args[0] == "-docker"
    MainJ.setDockerEnvironment(onDocker)
    t.println("running in docker: $onDocker")
    /*
    t.println(red("Hello, world!"), whitespace = Whitespace.NORMAL)

    val style = (bold + black + strikethrough)
    t.println(
        cyan("You ${(green on white)("can ${style("nest")} styles")} arbitrarily")
    )

    t.println(rgb("#b4eeb4")("You can also use true color and color spaces like HSL"))
*/
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
    val secret = t.prompt("Now enter the token secret for ${(cText) ("$label")}", hideInput = true)
    val number = t.prompt("At least 2 token parts will be generated. Enter 0 if ok or any number to add more")?.toIntOrNull()

    if ( number == null){
        t.println("Bad input, quit.")
        exitProcess(1);
    }

    MainJ.buildToken(label,color,secret, number);

    val name = t.prompt("Name of the Device")

    val publicKey = RSACrypt.generateOrGetKeyPair(label)

    val cert = MainJ.getCertificate(name, Base64.encode(publicKey.encoded))
    //val c64 = Base64.encode(cert)
    try {
        MainJ.showQRCodeOnScreenSwing(name, cert)
    } catch (e : Exception) {
        println("Headless environment, cannot show QR code on screen, falling back to text")
        MainJ.showQRCodeOnScreen(cert)
    }
    println("Certificate: $cert")

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