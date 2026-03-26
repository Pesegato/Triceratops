package com.pesegato.core

import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKeyFactory
import javax.crypto.interfaces.DHPublicKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

object CryptoEngine {
    val secureRandom: SecureRandom = SecureRandom()

    // --- 1. GESTIONE OTP E KDF ---

    // Espande l'OTP di 8 caratteri in una chiave lunga quanto serve (es. per XOR o AES)
    fun deriveKeyFromOTP(otp: String, salt: ByteArray, lengthBytes: Int): ByteArray {
        val spec = PBEKeySpec(otp.toCharArray(), salt, 10000, lengthBytes * 8)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    // --- 2. DIFFIE-HELLMAN ---

    @JvmStatic
    fun generateInitialDHKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("DiffieHellman")
        kpg.initialize(2048, secureRandom)
        return kpg.generateKeyPair()
    }

    fun generateSecondaryDHKeyPair(encodedPublicKeyA: ByteArray): KeyPair {
        val kf = KeyFactory.getInstance("DiffieHellman")
        val pubKeyA = kf.generatePublic(X509EncodedKeySpec(encodedPublicKeyA)) as DHPublicKey
        val kpg = KeyPairGenerator.getInstance("DiffieHellman")
        kpg.initialize(pubKeyA.params)
        return kpg.generateKeyPair()
    }

    // --- 3. OFFUSCAMENTO XOR ---

    fun applyXOR(data: ByteArray, mask: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor mask[i].toInt()).toByte()
        }
        return result
    }

    // --- 4. SEGRETO CONDIVISO E AES ---
    /**
     * @deprecated use deriveFinalKeyAndSAS
     */
@Deprecated("use deriveFinalKeyAndSAS")
    fun deriveAESKey(myPrivateKey: PrivateKey, theirPublicKeyBytes: ByteArray): SecretKeySpec {
        val kf = KeyFactory.getInstance("DiffieHellman")
        val theirPubKey = kf.generatePublic(X509EncodedKeySpec(theirPublicKeyBytes))
        val ka = KeyAgreement.getInstance("DiffieHellman")
        ka.init(myPrivateKey)
        ka.doPhase(theirPubKey, true)

        val sharedSecret = ka.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256")
        return SecretKeySpec(digest.digest(sharedSecret), "AES")
    }

    fun encryptB(data: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(data)
    }

    fun encrypt(plaintext: String, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plaintext.toByteArray())
    }

    fun decrypt(encryptedData: ByteArray, key: SecretKeySpec): String {
        val iv = encryptedData.sliceArray(0 until 12)
        val ciphertext = encryptedData.sliceArray(12 until encryptedData.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    // Hybrid KDF + SAS

    // 1. Deriva la maschera XOR usando sia il segreto salvato che l'OTP manuale
    fun deriveHybridMask(digitalSecret: ByteArray, manualOtp: String, salt: ByteArray, length: Int): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(digitalSecret)
        digest.update(manualOtp.toByteArray(Charsets.UTF_8))
        val combinedSeed = digest.digest()

        // Usiamo il seed combinato come password per PBKDF2
        val spec = PBEKeySpec(
            Base64.encode(combinedSeed).toCharArray(),
            salt,
            5000,
            length * 8
        )
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    // 2. Genera la chiave finale e il codice SAS
    fun deriveFinalKeyAndSAS(myPrivateKey: PrivateKey, theirPublicKeyBytes: ByteArray): Pair<SecretKeySpec, String> {
        val kf = KeyFactory.getInstance("DiffieHellman")
        val theirPubKey = kf.generatePublic(X509EncodedKeySpec(theirPublicKeyBytes))

        val ka = KeyAgreement.getInstance("DiffieHellman")
        ka.init(myPrivateKey)
        ka.doPhase(theirPubKey, true)
        val sharedSecret = ka.generateSecret()

        // Derivazione Chiave AES
        val keyDigest = MessageDigest.getInstance("SHA-256")
        val aesKey = SecretKeySpec(keyDigest.digest(sharedSecret), "AES")

        // Generazione SAS (5 cifre leggibili)
        val sasDigest = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        val sasValue = ((sasDigest[0].toInt() and 0xFF) shl 16) or
                ((sasDigest[1].toInt() and 0xFF) shl 8) or
                (sasDigest[2].toInt() and 0xFF)
        val sasCode = String.format("%05d", sasValue % 100000)

        return Pair(aesKey, sasCode)
    }

    // 1. Genera l'OTP per la prossima volta
    fun generateNextOTP(): ByteArray {
        val otp = ByteArray(32)
        SecureRandom().nextBytes(otp)
        return otp
    }

    // 2. Prepara il pacchetto per la sessione successiva
    // Viene inviato CIFRATO dentro la sessione attuale
    fun prepareNextSessionPacket(nextOtp: ByteArray): String {
        return Base64.encode(nextOtp)
    }
    /*
        // 3. Esegue il DH "Leggero" usando l'OTP precedente per offuscare
        fun performLightweightExchange(socket: Socket, savedOtp: ByteArray, isServer: Boolean): SecretKeySpec {
            val salt = "RatchetSaltV1".toByteArray()

            return if (isServer) {
                // Lato A
                val keyPairA = CryptoEngine.generateInitialKeyPair()
                NetworkUtils.sendPacket(socket, keyPairA.public.encoded)

                val pubBOffuscata = NetworkUtils.receivePacket(socket)
                val otpExpanded = CryptoEngine.deriveKeyFromOTP(Base64.getEncoder().encodeToString(savedOtp), salt, pubBOffuscata.size)
                val pubBReale = CryptoEngine.applyXOR(pubBOffuscata, otpExpanded)

                CryptoEngine.deriveAESKey(keyPairA.private, pubBReale)
            } else {
                // Lato B
                val pubA = NetworkUtils.receivePacket(socket)
                val keyPairB = CryptoEngine.generateSecondaryKeyPair(pubA)
                val pubB = keyPairB.public.encoded

                val otpExpanded = CryptoEngine.deriveKeyFromOTP(Base64.getEncoder().encodeToString(savedOtp), salt, pubB.size)
                val pubBOffuscata = CryptoEngine.applyXOR(pubB, otpExpanded)
                NetworkUtils.sendPacket(socket, pubBOffuscata)

                CryptoEngine.deriveAESKey(keyPairB.private, pubA)
            }
        }
    */

    fun generateSAS(sharedSecret: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        // Prendiamo i primi 3 byte e convertiamoli in un numero o in una stringa leggibile
        // Ad esempio, un numero a 5 cifre o 4 caratteri Base32
        val sasValue = ((hash[0].toInt() and 0xFF) shl 16) or
                ((hash[1].toInt() and 0xFF) shl 8) or
                (hash[2].toInt() and 0xFF)

        return String.format("%05d", sasValue % 100000) // Restituisce un codice tipo "48291"
    }

}