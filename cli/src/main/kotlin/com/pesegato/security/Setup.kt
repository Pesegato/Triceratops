package com.pesegato.security

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.pesegato.MainJ
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlin.system.exitProcess

class Setup(private val terminal: Terminal = Terminal()) {

    private val realUserHome: String = System.getenv("SUDO_USER")?.let { sudoUser ->
        // Se siamo sotto sudo, puntiamo alla home dell'utente originale
        "/home/$sudoUser"
    } ?: System.getProperty("user.home")

    private val configFile = File(MainJ.getPath() + "t9s.properties")

    fun start(args: Array<String>) {
        val isBootstrap = System.getenv("TPM_BOOTSTRAP_MODE") == "true"
        val hostHostname = Config.hostHostname!!

        if (Config.isDocker) {
            terminal.println(brightBlue("🐳 Docker running on host: ${bold(hostHostname)}"))
        } else {
            terminal.println("AWT is available")
        }

        println("PATH: " + MainJ.getPath())
        if (isBootstrap) {
            runInformedSetup(configFile)
        }
        else {
            Config.loadFromProperties(Properties().apply { load(FileInputStream(configFile)) })
            showCurrentConfig()
            terminal.println(italic(gray("Per cambiare configurazione, lancia runTriceratops.sh --newconf")))
            terminal.println(green("✔ Proseguimento in corso...\n"))
            return
        }

    }

    private fun runInformedSetup(file: File) {
        terminal.println(
            Panel(
                content = white("Inizio procedura di configurazione guidata TPM"),
                title = bold(magenta("🛡️ TRICERATOPS SETUP"))
            )
        )
        terminal.println(Panel(
            content = Text(cyan("Analisi delle capacità del chip crittografico in corso...")),
            title = Text(bold("🛠️ BOOTSTRAP DIAGNOSTICO"))
        ))

        // 1. Mostriamo i dati reali del chip (usando i tools nel container)
        val tpm = ProvisionUtils.fetchTpmInfo()
        val isFtmp = tpm["vendor"] == "AMD" || tpm["vendor"] == "INTC"
        val typeLabel = if (isFtmp) "Firmware TPM (fTPM)" else "Discrete TPM (dTPM)"

        val infoTable = table {
            borderStyle = brightBlue
            header { row(bold("Caratteristica"), bold("Stato/Valore")) }
            body {
                row("Produttore", tpm["vendor"] ?: red("Non rilevato"))
                row("Versione Spec", tpm["spec"] ?: red("Non rilevato"))
                row("Firmware", tpm["fw"] ?: "N/D")
                row("Rev", tpm["rev"] ?: "N/D")
                row("Tipo Hardware", cyan(typeLabel))
            }
        }

        terminal.println(infoTable)

        val canOaep = ProvisionUtils.testOaepDeepScan()
        val can4096 = ProvisionUtils.testRsa4096()
        val canPkcs1 = ProvisionUtils.testPkcs1()

        terminal.println(Panel(
            content = table {
                borderStyle = brightBlue
                header { row(bold("Feature"), bold("Supporto Hardware"), bold("Note")) }
                body {
                    row("RSA-PKCS1",
                        if (canPkcs1) green("✔ SUPPORTATO") else yellow("⚠ LIMITATO"),
                        "Compatibilità legacy v1.5")
                    row("RSA-OAEP",
                        if (canOaep) green("✔ FUNZIONANTE") else red("⚠ ERRORE"),
                        if (canOaep) "Test crittografico superato" else "Possibile problema Driver/Middleware")
                    row("RSA 2048", green("✔ OK"), "Standard")
                    row("RSA 4096",
                        if (can4096) green("✔ SUPPORTATO") else yellow("✘ NON DISPONIBILE"),
                        if (can4096) "Sicurezza Ultra" else "Limite Hardware")
                }
            },
            title = Text(bold("🔍 Diagnostica Capacità Crittografiche"))
        ))

        val props = Properties()

        // Logica di scelta condizionale
        val choices = mutableListOf<String>()
        if (canOaep) choices.add("1")
        if (canPkcs1) choices.add("2")

        terminal.println(bold("\n1. Seleziona protocollo RSA:"))
        if (canOaep) terminal.println("  1) ${brightGreen("OAEP")} (SHA-256) - Consigliato")
        if (canPkcs1) terminal.println("  2) ${brightYellow("PKCS1")} v1.5 - Compatibilità")

        // Se uno dei due manca, impediamo la selezione
        var selection = ""
        while (!choices.contains(selection)) {
            terminal.print(magenta("Seleziona (o premi Invio per default): "))
            selection = readLine() ?: if (canOaep) "1" else "2"
            if (selection.isEmpty()) selection = if (canOaep) "1" else "2"
        }

        props.setProperty("tpm.algorithm", if (selection == "1") "OAEP" else "PKCS1")
        // 2. Selezione Identificativo
        terminal.println(bold("\n2. Seleziona metodo di identificazione:"))
        terminal.println("  1) ${brightBlue("Keyring")} (Label/Alias)")
        terminal.println("  2) ${brightMagenta("Hardware ID")} (Serial/UUID)")
        val idChoice = readlnOrNull() ?: "1"
        props.setProperty("tpm.id_mode", if (idChoice == "2") "HARDWARE_ID" else "KEYRING")

        try {
            FileOutputStream(configFile).use { out ->
                props.store(out, "Generato da Triceratops com.pesegato.security.Setup")
            }
            terminal.println(bold(green("\n✅ Configurazione salvata con successo!")))
            terminal.println(yellow("L'applicazione verrà ora chiusa. Rilanciala per applicare le modifiche.\n"))
            exitProcess(0)
        } catch (e: Exception) {
            terminal.println(red("❌ Errore durante il salvataggio: ${e.message}"))
        }
    }


    private fun showCurrentConfig() {

        val content = table {
            header { row(cyan("Parametro"), cyan("Valore")) }
            body {
                row("Algoritmo RSA", Config.algorithm)
                row("Metodo ID", Config.idMode)
                row("Stato Hardware", Config.tpmStatus)
            }
        }

        terminal.println(
            Panel(
                content = content,
                title = Text(bold(yellow("📦 Configurazione Attuale"))),
                borderStyle = blue
            )
        )
    }


    private fun getTpmSerialNumber(): String {
        return try {
            val process = ProcessBuilder("tpm2_getcap", "properties-fixed")
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            // Cerchiamo la riga che contiene il seriale (es. TPM2_PT_MANUFACTURER)
            output.lines()
                .find { it.contains("TPM2_PT_SERIAL_NUMBER") }
                ?.substringAfter(":")
                ?.trim() ?: "SN-UNKNOWN"
        } catch (e: Exception) {
            "NOT-AVAILABLE"
        }
    }
}