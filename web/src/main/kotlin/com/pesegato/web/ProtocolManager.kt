package com.pesegato.web

import com.pesegato.AdbConnector
import com.pesegato.ConnectionState
import com.pesegato.MainJ
import com.pesegato.RSACrypt
import com.pesegato.device.DeviceManager
import com.pesegato.security.TotpUtils.encryptMessage
import com.pesegato.t9stoken.protocol.KeyCerimonyProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ProtocolManager(private val connector: AdbConnector) {

    var protocol: KeyCerimonyProtocol? = null

    fun startKeyCeremony() {
        println("Starting keycerimony")
        CoroutineScope(Dispatchers.IO).launch {
            connector.writeToServer("💍")
            protocol = KeyCerimonyProtocol()
        }
    }

    fun startHandshake() {
        println("Starting handshake")
        CoroutineScope(Dispatchers.IO).launch {
            connector.writeToServer("❤")
            connector.writeToServer(DeviceManager.hwId)
            val msg = connector.readFromServer()
            println("Received: $msg")
            if (msg.startsWith("🦎") && msg.endsWith("🦕")) {
                val pin = msg.substring(2, msg.length - 2)
                println("Ho il pin: $pin")
                RSACrypt.setPIN(pin)
            }
            /*
            non qui
            val msg2=connector.readFromServer()
            if ((msg2.startsWith("🚓"))&&msg2.endsWith("🦕")) {
                val tokenJ= msg2.substring(2, msg2.length - 2)
                println("Ho il token: $tokenJ")
            }
             */
        }
    }

    fun addToken() {
        println("Adding token")
        CoroutineScope(Dispatchers.IO).launch {
            connector.writeToServer("🔗")
            //connector.writeToServer(DeviceManager.hwId)
            val msg = connector.readFromServer()
            println("Received: $msg")
            if (msg.startsWith("👋"))
                return@launch
            /*
            non qui
            if (msg.startsWith("🦎")&&msg.endsWith("🦕")) {
                val pin=msg.substring(2, msg.length - 2)
                println("Ho il pin: $pin")
            }

             */
            if ((msg.startsWith("🚓")) && msg.endsWith("🦕")) {
                val tokenJ = msg.substring(2, msg.length - 2)
                println("Ho il token: $tokenJ")

                val currentState = connector.state.value
                if (currentState !is ConnectionState.Connected) {
                    println("Errore: Impossibile salvare il token, nessuna connessione attiva.")
                    return@launch
                }
                // Recuperiamo device e deviceId direttamente dallo stato del connettore
                val deviceId = currentState.deviceId.toString()
                val device = currentState.device

                val appDir = File(MainJ.getPath(), "tokens/${deviceId}")
                appDir.mkdirs() // Ensure the directory exists

                val nameFile = File(appDir, "name")
                if (!nameFile.exists() || nameFile.readText() != device) {
                    nameFile.writeText(device)
                }
                val uuid = UUID.randomUUID().toString()
                val outputFile = File(appDir, uuid)
                outputFile.writeText(tokenJ)
                println("Data received and saved to ${outputFile.absolutePath}")
                connector.writeToServer("🪙$uuid🦕")

            }
        }
    }

    fun startProvisioning(otp: String, onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            println("-->start provisioning, waiting for PIN")
            // 1. Invia l'OTP iniziale
            //connector.sendToken(otp)

            // 2. Avvia la cerimonia delle chiavi
            val msg = connector.readFromServer()
            val kMgs = protocol!!.step1(msg)
            connector.writeToServer(kMgs)

            connector.writeToServer("👋")
            connector.disconnect()

            onComplete()
        }
    }

    suspend fun decrypt(message: String, otpSecret: String, responseBox: CompletableDeferred<String>) {
        println("-->decrypt")
        val encryptedMessage = encryptMessage(message, otpSecret)
        connector.waitForResponse(responseBox)
        connector.sendToken(encryptedMessage)
        responseBox.complete(connector.readFromServer())
    }
}