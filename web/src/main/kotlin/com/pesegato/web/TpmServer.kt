package com.pesegato.web

import com.pesegato.AdbConnector
import com.pesegato.MainJ
import com.pesegato.RSACrypt
import com.pesegato.data.QRCoder
import com.pesegato.device.DeviceManager
import com.pesegato.security.Config
import com.pesegato.security.SecurityFactory
import com.pesegato.security.TotpUtils.encryptMessage
import com.pesegato.token.TokenManager
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.spec.MGF1ParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64

@Serializable
data class StatusDto(val hostname: String, val tpmInitialized: Boolean)

@Serializable
data class DeviceDto(val id: String, val displayName: String)

@Serializable
data class TokenDto(val uuid: String, val label: String, val color: String)

@Serializable
data class WebDecryptRequest(val totp: String, val clientPublicKey: String)
@Serializable
data class DecryptRequest(val data: String)

@Serializable
data class DecryptResponse(val result: String?, val error: String? = null)

fun startWebServer(deviceManager: DeviceManager, tokenManager: TokenManager, connector: AdbConnector) {

    // 1. Inizializzazione TPM
    var tpmInitialized = false
    lateinit var rsa: RSACrypt
    try {
        val kp = SecurityFactory.getProtector()
        rsa = RSACrypt(kp)
        tpmInitialized = true
        println("✅ TPM inizializzato.")
    } catch (e: Exception) {
        println("❌ ERRORE: Impossibile inizializzare il TPM. ${e.message}")
    }

    embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        install(ContentNegotiation) {
            json()
        }
        routing {
            staticResources("/", "static") {
                default("index.html")
            }
            get("/status") {
                call.respond(StatusDto(hostname = Config.hostHostname!!, tpmInitialized = tpmInitialized))
            }
            get("/certificate") {
                val hostname = Config.hostHostname!!

                val name = hostname

                val publicKey = rsa.publicKey

                println("--- VERIFICA CHIAVE ---");
                println("Provider: " + publicKey.javaClass.getName());
// Se vedi "sun.security.pkcs11.P11Key$P11PublicKey", è fatta!
                println("Algoritmo: " + publicKey.algorithm);
                println("Format: " + publicKey.format);

                val cert = MainJ.getCertificate(name, Base64.encode(publicKey.encoded))
                //val c64 = Base64.encode(cert)
                println("Certificate: $cert")

                // 2. Usa il metodo esistente per generare l'immagine in memoria
                val bufferedImage = QRCoder.showQRCodeOnScreenBI(cert)

                if (bufferedImage != null) {
                    // 3. Converte il BufferedImage in byte PNG
                    val outputStream = ByteArrayOutputStream()
                    ImageIO.write(bufferedImage, "png", outputStream)
                    val imageBytes = outputStream.toByteArray()

                    // 4. Invia i byte al browser con l'header corretto (image/png)
                    call.respondBytes(imageBytes, ContentType.Image.PNG)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Errore nella generazione del QR")
                }
            }
            get("/devices") {
                val devices = deviceManager.getAvailableDevices()
                // Mappiamo il modello di dominio (Device) nel DTO serializzabile
                call.respond(devices.map { DeviceDto(it.id, it.displayName) })
            }

            get("/devices/{deviceId}/tokens") {
                val deviceId = call.parameters["deviceId"]
                if (deviceId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing deviceId")
                    return@get
                }
                val tokens = tokenManager.getTokensForDevice(deviceId)
                call.respond(tokens.map { TokenDto(it.uuid, it.label, it.color.name) })
            }

            post("/devices/{deviceId}/connect") {
                val deviceId = call.parameters["deviceId"]
                if (deviceId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing deviceId")
                    return@post
                }
                connector.connect()
                call.respond(HttpStatusCode.OK, "Device connected")
            }

            post("/devices/{deviceId}/tokens/{tokenUuid}/decrypt") {
                val deviceId = call.parameters["deviceId"]
                val tokenUuid = call.parameters["tokenUuid"]
                if (deviceId == null || tokenUuid == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing deviceId or tokenUuid")
                    return@post
                }

                try {
                    val request = call.receive<WebDecryptRequest>()
                    println("Request received)//: $request")

                    val tokenFile = File(MainJ.getPath(), "tokens/$deviceId/$tokenUuid")
                    if (!tokenFile.exists()) {
                        call.respond(HttpStatusCode.NotFound, "Token not found")
                        return@post
                    }

                    val tkData = tokenFile.readText()

                    val encryptedMessage = encryptMessage(tkData, request.totp)

                    val responseBox = CompletableDeferred<String>()

                    connector.waitForResponse(responseBox)
                    connector.sendToken(encryptedMessage)
                    val result = responseBox.await()

                    //println("RESULT: $result")
                    //il risultato viene letto in AdbConnector.decryptToken()

                    if (result != null) {
                        // 2. Ricostruisci la chiave pubblica del client
                        val clientKeyBytes = java.util.Base64.getDecoder().decode(request.clientPublicKey)
                        val keySpec = java.security.spec.X509EncodedKeySpec(clientKeyBytes)
                        val keyFactory = java.security.KeyFactory.getInstance("RSA")
                        val clientKey = keyFactory.generatePublic(keySpec)

                        // 3. Cifra il risultato con la chiave pubblica del client (RSA-OAEP)
                        // Nota: Deve corrispondere ai parametri usati nel browser (RSA-OAEP con SHA-256)
                        val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding")
                        val oaepParams = OAEPParameterSpec(
                            "SHA-256",
                            "MGF1",
                            MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT
                        )
                        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, clientKey, oaepParams)
                        val encryptedBytes = cipher.doFinal(result.toByteArray(Charsets.UTF_8))
                        val encryptedBase64 = java.util.Base64.getEncoder().encodeToString(encryptedBytes)

                        // 4. Rispondi con il campo 'encryptedResult' che il JS si aspetta
                        call.respond(mapOf("encryptedResult" to encryptedBase64))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Decryption failed (Invalid TOTP?)"))
                    }
                } catch (e: Exception) {
                    println("❌ Errore durante la decifrazione: ${e.message}")
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error during decryption: ${e.message}"))
                }
            }

            post("/decrypt") {
                val pin="123456"
                if (pin.isEmpty()) {
                    call.respond(HttpStatusCode.Unauthorized, "Server not initialized with API Key")
                    return@post
                }

                val request = call.receive<DecryptRequest>()

                // Calcoliamo il TOTP corrente come AAD per validare la richiesta
                val decrypted = rsa.decrypt(request.data)

                if (decrypted != null) {
                    call.respond(DecryptResponse(result = decrypted))
                } else {
                    call.respond(DecryptResponse(result = null, error = "Decryption failed"))
                }
            }
        }
    }.start(wait = true)
}