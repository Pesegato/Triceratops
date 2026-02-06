package com.pesegato.token

import com.pesegato.MainJ
import com.pesegato.model.LoadedToken
import com.pesegato.t9stoken.model.SecureToken
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

class TokenManager {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(SecureToken::class.java)

    fun getTokensForDevice(deviceId: String): List<LoadedToken> {
        val deviceDir = File(MainJ.getPath(), "tokens/$deviceId")
        if (!deviceDir.exists() || !deviceDir.isDirectory) return emptyList()

        // Filtriamo i file che hanno lunghezza 36 (UUID standard)
        val tokenFiles = deviceDir.listFiles { _, name -> name.length == 36 } ?: emptyArray()

        return tokenFiles.mapNotNull { file ->
            try {
                val json = file.readText()
                val secureToken = jsonAdapter.fromJson(json)
                if (secureToken != null) {
                    LoadedToken(uuid = file.name.removeSuffix(".json"), label = secureToken.label, color = secureToken.color)
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Deletes a specific token file for a given device.
     * @param deviceId The ID of the device.
     * @param tokenUuid The UUID of the token to delete.
     * @return True if the file was successfully deleted, false otherwise.
     */
    fun deleteToken(deviceId: String, tokenUuid: String): Boolean {
        val tokenFile = File(MainJ.getPath(), "tokens/$deviceId/$tokenUuid")
        if (!tokenFile.exists() || tokenFile.isDirectory) {
            return false
        }
        return tokenFile.delete()
    }
}