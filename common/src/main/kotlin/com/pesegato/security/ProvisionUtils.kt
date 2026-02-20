package com.pesegato.security

import java.io.File

class ProvisionUtils {
    companion object {
    fun fetchTpmInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        try {
            val process = ProcessBuilder("tpm2_getcap", "properties-fixed").start()
            val lines = process.inputStream.bufferedReader().readLines()

            var currentKey = ""

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                when {
                    // Se la riga finisce con ':', abbiamo trovato una nuova sezione (es. TPM2_PT_MANUFACTURER:)
                    line.endsWith(":") -> {
                        currentKey = trimmed.removeSuffix(":")
                    }

                    // Se siamo dentro una sezione e troviamo 'value:', estraiamo il valore tra virgolette
                    currentKey.isNotEmpty() && trimmed.startsWith("value:") -> {
                        val value = trimmed.substringAfter("value:")
                            .trim()
                            .removeSurrounding("\"")

                        if (value.isNotEmpty()) {
                            when (currentKey) {
                                "TPM2_PT_MANUFACTURER" -> info["vendor"] = value
                                "TPM2_PT_FAMILY_INDICATOR" -> info["spec"] = value
                                "TPM2_PT_REVISION" -> info["rev"] = value
                            }
                        }
                    }

                    // Per il firmware che spesso non ha 'value' ma solo 'raw'
                    currentKey == "TPM2_PT_FIRMWARE_VERSION_1" && trimmed.startsWith("raw:") -> {
                        info["fw"] = trimmed.substringAfter("raw:").trim()
                    }
                }
            }
        } catch (e: Exception) {
            info["error"] = "Errore durante il parsing: ${e.message}"
        }
        val isFtmp = info["vendor"] == "AMD" || info["vendor"] == "INTC"
        info["type"] = if (isFtmp) "Firmware TPM (fTPM)" else "Discrete TPM (dTPM)"

        info["RSA-OAEP"] = if (testOaepDeepScan()) "✔" else "✘"
        info["RSA 4096"] = if (testRsa4096()) "✔" else "✘"
        info["RSA-PKCS1"] = if (testPkcs1()) "✔" else "✘"


        return info
    }

        fun testAlgorithmSupport(algParams: String): Boolean {
            return try {
                // tpm2_testparms verifica se il TPM supporta la combinazione di algoritmo/padding
                val process = ProcessBuilder("tpm2_testparms", algParams)
                    .redirectErrorStream(true)
                    .start()

                val exitCode = process.waitFor()
                // Se l'exit code è 0, l'algoritmo è supportato dal chip
                exitCode == 0
            } catch (e: Exception) {
                false
            }
        }

        fun testRsa4096(): Boolean {
            // Verifica se il chip supporta chiavi a 4096 bit (molti dTPM industriali si fermano a 2048)
            return testAlgorithmSupport("rsa4096")
        }

        fun testOaepDeepScan(): Boolean {
            return try {
                // Invece di testparms, proviamo a creare una chiave RSA transitoria con OAEP.
                // Se il driver o il chip hanno problemi reali, questo fallirà.
                val process = ProcessBuilder(
                    "tpm2_createprimary", "-C", "o", "-g", "sha256", "-G", "rsa2048:oaep", "-c", "primary.ctx"
                ).start()

                val success = process.waitFor() == 0
                // Pulizia: rimuoviamo il contesto se creato
                File("primary.ctx").delete()
                success
            } catch (e: Exception) {
                false
            }
        }

        fun testPkcs1(): Boolean {
            // RSA Encryption Scheme - PKCS #1 v1.5
            // Spesso indicato come rsa:null o rsa:es nelle specifiche tpm2-tools
            return testAlgorithmSupport("rsa:rsaes")
        }


    }


}