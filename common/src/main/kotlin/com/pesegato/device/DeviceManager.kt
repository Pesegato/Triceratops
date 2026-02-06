package com.pesegato.device

import com.pesegato.MainJ
import com.pesegato.model.Device
import java.io.File

/**
 * Gestisce la logica per trovare e leggere i dispositivi che hanno token salvati.
 */
class DeviceManager {

    /**
     * Scansiona la cartella dei token e restituisce una lista di dispositivi trovati.
     */
    fun getAvailableDevices(): List<Device> {
        val tokensDir = File(MainJ.getPath(), "tokens")
        if (!tokensDir.exists() || !tokensDir.isDirectory) return emptyList()

        return tokensDir.listFiles { file -> file.isDirectory }?.map { deviceDir ->
            val nameFile = File(deviceDir, "name")
            val displayName = if (nameFile.exists()) nameFile.readText().trim() else deviceDir.name
            Device(id = deviceDir.name, displayName = displayName)
        } ?: emptyList()
    }

    /**
     * Deletes a device's directory and all its contents.
     * @param deviceId The ID of the device to delete.
     * @return True if the directory was successfully deleted, false otherwise.
     */
    fun deleteDevice(deviceId: String): Boolean {
        val deviceDir = File(MainJ.getPath(), "tokens/$deviceId")
        if (!deviceDir.exists() || !deviceDir.isDirectory) {
            return false
        }
        return deviceDir.deleteRecursively()
    }
}