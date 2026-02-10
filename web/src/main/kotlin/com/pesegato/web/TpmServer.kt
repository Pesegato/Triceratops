package com.pesegato.web

import com.pesegato.RSACrypt
import com.pesegato.device.DeviceManager
import com.pesegato.security.SecurityFactory
import com.pesegato.security.TotpUtils
import com.pesegato.token.TokenManager
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
import java.io.File

@Serializable
data class DeviceDto(val id: String, val displayName: String)

@Serializable
data class TokenDto(val uuid: String, val label: String, val color: String)

@Serializable
data class DecryptRequest(val data: String)

@Serializable
data class DecryptResponse(val result: String?, val error: String? = null)

fun startWebServer(deviceManager: DeviceManager, tokenManager: TokenManager) {

    // 1. Inizializzazione TPM
    val kp = SecurityFactory.getProtector()
    val rsa = RSACrypt(kp)
    println("✅ TPM inizializzato.")

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