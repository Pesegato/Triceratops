package com.pesegato.t9stoken.protocol

import com.itextpdf.kernel.xmp.impl.Base64
import com.pesegato.AdbConnector
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

class KeyCerimonyProtocol(private val adbConnector: AdbConnector) {
    suspend fun step1() {
        println("Starting keycerimony")
        adbConnector.writeToServer("💍")
        val msg = adbConnector.readFromServer()
        println("PIN: $msg")

        val cmd = arrayOf("/provision_tpm.sh")
        val process = Runtime.getRuntime().exec(cmd)
        process.outputStream.bufferedWriter().use { writer ->
            writer.write(msg) // Scrive il PIN
            writer.newLine()  // Aggiunge il carattere "a capo" mancante
        }
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        println("stdout: $stdout")
        println("stderr: $stderr")
        println("exitCode: $exitCode")

        println("Lettura chiave del TPM:stdout: $stdout")
        // Carichiamo il file DER generato dal TPM durante il provisioning
        // Questo garantisce che la cifratura avvenga con la chiave CORRETTA
        val pubKeyBytes = Files.readAllBytes(Paths.get("tpm_public_key.der"))
        val kf = KeyFactory.getInstance("RSA")
        val publicKey = String(Base64.encode(kf.generatePublic(X509EncodedKeySpec(pubKeyBytes)).encoded))

        println("Public key: $publicKey")
        adbConnector.writeToServer("🦖$publicKey🦕")

        adbConnector.writeToServer("👋")
        adbConnector.disconnect()
    }
}