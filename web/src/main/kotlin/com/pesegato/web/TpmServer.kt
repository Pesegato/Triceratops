package com.pesegato.web

import com.pesegato.AdbConnector
import com.pesegato.ConnectionState
import com.pesegato.MainJ
import com.pesegato.RSACrypt
import com.pesegato.data.QRCoder
import com.pesegato.device.DeviceManager
import com.pesegato.security.Config
import com.pesegato.security.ProvisionUtils
import com.pesegato.security.SecurityFactory
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
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.system.exitProcess

@Serializable
data class ProvisionRequest(val otp: String)

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

@Serializable
data class HybridEncryptedResponse(
    val encryptedKey: String, // Base64 encoded AES key
    val iv: String,           // Base64 encoded IV
    val data: String          // Base64 encoded encrypted data
)

@Volatile
var provisioningCompleted = false
fun startWebServer(deviceManager: DeviceManager, tokenManager: TokenManager, rsa: RSACrypt, tpmInitialized: Boolean) {

    val connector = AdbConnector(rsa) { status ->
        println(Date().toString() + " [ADB STATUS]: $status")
    }

    val protocolManager = ProtocolManager(connector)

    //protocolManager.startProvisioning("otp"){
    //    provisioningCompleted = true
    //}

    connector.connect(
        onConnected = {
            println("connected")

            if (!tpmInitialized) {
                protocolManager.startKeyCeremony()
            } else {
                protocolManager.startHandshake()
            }
        }
    )

    // 1. Inizializzazione TPM
    lateinit var rsa: RSACrypt
    try {
        //val kp = SecurityFactory.getProvisionalProtector()
        val kp = SecurityFactory.getProtector()
        rsa = RSACrypt(kp)
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
                call.respond(StatusDto(Config.hostHostname!!, tpmInitialized))
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

            get("/connectionStatus") {
                when (connector.state.value) {
                    ConnectionState.Disconnected -> call.respond("Not connected")
                    ConnectionState.Connecting -> call.respond("Connecting")
                    is ConnectionState.Connected -> call.respond("Connected with ${(connector.state.value as ConnectionState.Connected).device}")
                    else -> {
                        call.respond("Error: ${(connector.state.value as ConnectionState.Error).message}")
                    }
                }
            }

            get("/capabilities") {
                val capabilities = ProvisionUtils.fetchTpmInfo()
                call.respond(capabilities)
            }

            post("/cleartpmdata") {
                val cmd = arrayOf("/clean_tpm.sh", "--factory")
                val process = Runtime.getRuntime().exec(cmd)
                process.waitFor()
                call.respond(HttpStatusCode.OK, "TPM cleared. Shutting down.")
                Thread {
                    Thread.sleep(1000)
                    exitProcess(0)
                }.start()
            }

            post("/provision") {
                val body = call.receive<ProvisionRequest>()
                val otp = body.otp
                println("Ricevuto OTP: $otp")
                protocolManager.startProvisioning(otp) {
                    provisioningCompleted = true
                }
                //
                /*
                connector.connect {
                    CoroutineScope(Dispatchers.IO).launch {
                        val p = KeyCerimonyProtocol(connector)
                        p.step1()
                        provisioningCompleted = true
                    }
                }
                */
                call.respond("TBD")
            }

            get("/provisionEvents") {
                call.response.header("Cache-Control", "no-cache")
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    try {
                        var lastKeepaliveTime = System.currentTimeMillis()
                        while (!provisioningCompleted) {
                            // Controlla ogni 100 ms
                            delay(100)

                            // Se è passato almeno 15 secondi dall'ultimo keepalive, invialo
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastKeepaliveTime >= 15_000) {
                                write(": keepalive\n\n")
                                flush()
                                lastKeepaliveTime = currentTime
                            }
                        }

                        write("event: completed\ndata: ok\n\n")
                        flush()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // This is expected when the client disconnects.
                        println("Client disconnected from /provisionEvents stream (job cancelled).")
                    } catch (e: java.io.IOException) {
                        // This is expected when the client disconnects, so we just log it calmly.
                        println("Client disconnected from /provisionEvents stream (IO error).")
                    } catch (e: Exception) {
                        println("An unexpected error occurred in /provisionEvents stream: ${e.message}")
                    }
                }
            }

            post("/devices/{deviceId}/connect") {
                //Obsoleta??
                val deviceId = call.parameters["deviceId"]
                if (deviceId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing deviceId")
                    return@post
                }
                /*
                già connesso all'avvio
                connector.connect(
                    onConnected = { println("connected") }
                )

                 */
                call.respond(HttpStatusCode.OK, "Device connected")
            }

            post("/addtoken") {
                protocolManager.addToken()
                call.respond(HttpStatusCode.OK, "Token added")
            }

            post("/devices/{deviceId}/tokens/{tokenUuid}/delete") {
                call.respond(HttpStatusCode.OK, "Token (not really) deleted")
            }

            post("/unlock") {
                connector.writeToServer(connector.unlock)
                call.respond(HttpStatusCode.OK, "Authorize your device.")
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

                    val responseBox = CompletableDeferred<String>()
                    protocolManager.decrypt(tkData, request.totp, responseBox)

                    val result = responseBox.await()

                    println("RESULT: $result")
                    val secret = rsa.decrypt(result)
                    println("secret :$secret")
                    //il risultato viene letto in AdbConnector.decryptToken()

                    if (secret.isNotEmpty()) {
                        // 2. Ricostruisci la chiave pubblica del client
                        val clientKeyBytes = java.util.Base64.getDecoder().decode(request.clientPublicKey)
                        val keySpec = X509EncodedKeySpec(clientKeyBytes)
                        val keyFactory = KeyFactory.getInstance("RSA")
                        val clientKey = keyFactory.generatePublic(keySpec)

                        // 3. Cifra il risultato con la chiave pubblica del client (RSA-OAEP)
                        // --- HYBRID ENCRYPTION FOR RESPONSE ---

                        // 3a. Generate a temporary AES key and IV
                        val aesKeyGenerator = javax.crypto.KeyGenerator.getInstance("AES")
                        aesKeyGenerator.init(256) // AES-256 for strong security
                        val aesKey = aesKeyGenerator.generateKey()
                        val iv = ByteArray(12) // GCM standard IV size
                        java.security.SecureRandom().nextBytes(iv)

                        // 3b. Encrypt the actual result data with AES/GCM
                        val dataCipher = Cipher.getInstance("AES/GCM/NoPadding")
                        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, iv) // 128-bit auth tag
                        dataCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
                        val encryptedData = dataCipher.doFinal(secret.toByteArray(Charsets.UTF_8))

                        // 3c. Encrypt the temporary AES key with the client's public RSA key
                        val keyCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
                        val oaepParams = OAEPParameterSpec(
                            "SHA-256",
                            "MGF1",
                            MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT
                        )
                        keyCipher.init(Cipher.ENCRYPT_MODE, clientKey, oaepParams)
                        val encryptedAesKey = keyCipher.doFinal(aesKey.getEncoded())

                        // 4. Package and send the hybrid payload
                        val responsePayload = HybridEncryptedResponse(
                            encryptedKey = java.util.Base64.getEncoder().encodeToString(encryptedAesKey),
                            iv = java.util.Base64.getEncoder().encodeToString(iv),
                            data = java.util.Base64.getEncoder().encodeToString(encryptedData)
                        )
                        call.respond(responsePayload)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Decryption failed (Invalid TOTP?)"))
                    }
                } catch (e: Exception) {
                    println("❌ Errore durante la decifrazione: ${e.message}")
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error during decryption: ${e.message}")
                    )
                }
            }

            /*
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
            */
        }
    }.start(wait = true)
}