package com.pesegato.web

import com.pesegato.MainJ
import com.pesegato.RSACrypt
import com.pesegato.data.QRCoder
import com.pesegato.device.DeviceManager
import com.pesegato.security.Config
import com.pesegato.security.SecurityFactory
import com.pesegato.security.TotpUtils
import com.pesegato.t9stoken.getHostname
import com.pesegato.token.TokenManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.default
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64

@Serializable
data class StatusDto(val hostname: String, val tpmInitialized: Boolean)

@Serializable
data class DeviceDto(val id: String, val displayName: String)

@Serializable
data class TokenDto(val uuid: String, val label: String, val color: String)

@Serializable
data class WebDecryptRequest(val totp: String)

@Serializable
data class DecryptRequest(val data: String)

@Serializable
data class DecryptResponse(val result: String?, val error: String? = null)

fun startWebServer(deviceManager: DeviceManager, tokenManager: TokenManager) {

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

    // 2. Lettura API Key condivisa
    val apiKeyFile = File("/app/output/api.key")
    var apiKey = ""

    // Semplice meccanismo di attesa se la GUI è lenta a scrivere il file
    var attempts = 0
    while (!apiKeyFile.exists() && attempts < 10) {
        println("⏳ In attesa del file api.key...")
        Thread.sleep(1000)
        attempts++
    }

    if (apiKeyFile.exists()) {
        apiKey = apiKeyFile.readText().trim()
        println("🔒 API Key caricata.")
    } else {
        println("⚠️  API Key non trovata! Il server rifiuterà tutte le richieste.")
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

                rsa.test()

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
            post("/devices/{deviceId}/tokens/{tokenUuid}/decrypt") {
                val deviceId = call.parameters["deviceId"]
                val tokenUuid = call.parameters["tokenUuid"]
                if (deviceId == null || tokenUuid == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing deviceId or tokenUuid")
                    return@post
                }

                try {
                    val request = call.receive<WebDecryptRequest>()
                    val tokenFile = File(MainJ.getPath(), "tokens/$deviceId/$tokenUuid")
                    if (!tokenFile.exists()) {
                        call.respond(HttpStatusCode.NotFound, "Token not found")
                        return@post
                    }
                    val encryptedContent = tokenFile.readText()
                    val decrypted = rsa.decrypt(encryptedContent, request.totp)

                    call.respond(DecryptResponse(result = decrypted, error = if (decrypted == null) "Decryption failed (Invalid TOTP?)" else null))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Error during decryption: ${e.message}")
                }
            }



            post("/decrypt") {
                if (apiKey.isEmpty()) {
                    call.respond(HttpStatusCode.Unauthorized, "Server not initialized with API Key")
                    return@post
                }

                val authHeader = call.request.header("Authorization")
                if (authHeader != "Bearer $apiKey") {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid API Key")
                    return@post
                }

                val request = call.receive<DecryptRequest>()

                // Calcoliamo il TOTP corrente come AAD per validare la richiesta
                val totp = TotpUtils.generateCurrentCode(apiKey)
                val decrypted = rsa.decrypt(request.data, totp)

                if (decrypted != null) {
                    call.respond(DecryptResponse(result = decrypted))
                } else {
                    call.respond(DecryptResponse(result = null, error = "Decryption failed"))
                }
            }
        }
    }.start(wait = true)
}