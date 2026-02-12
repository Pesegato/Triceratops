package com.pesegato

import dadb.AdbStream
import dadb.Dadb
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
// Defines the possible states of our ADB connection
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val device: String, val deviceId: Dadb, val isDecrypting: Boolean = false) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class AdbConnector(private val rsa: RSACrypt, private val onStatusUpdate: (String) -> Unit) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var dadb: Dadb? = null
    private var listenJob: Job? = null
    private var stream: AdbStream? = null
    private var socketOutputStream: OutputStream? = null
    private var socketInputStream: InputStream? = null

    fun connect() {
        // Prevent trying to connect if we are already connected or connecting
        if (_state.value !is ConnectionState.Disconnected && _state.value !is ConnectionState.Error) return

        CoroutineScope(Dispatchers.IO).launch {
            _state.value = ConnectionState.Connecting
            onStatusUpdate("Discovering ADB server...")
            try {
                dadb = Dadb.discover()
                if (dadb == null) {
                    _state.value = ConnectionState.Error("ADB server not found.")
                    onStatusUpdate("Error: ADB server not found.")
                    return@launch
                }

                val devices = Dadb.list()
                if (devices.isEmpty()) {
                    _state.value = ConnectionState.Error("No Android device connected.")
                    onStatusUpdate("Error: No Android device connected.")
                    return@launch
                }

                val deviceId = devices.first()

                val device = dadb!!.shell("settings get global device_name").output.trim()

                println("Connected to device: $device")

                // 1. Open a direct stream to the device (Tunneling)
                // Questo evita i problemi di port forwarding e socket locali in Docker
                onStatusUpdate("Opening direct stream to tcp:4242...")
                stream = dadb!!.open("tcp:4242")
                socketOutputStream = stream!!.sink.outputStream()
                socketInputStream = stream!!.source.inputStream()

                _state.value = ConnectionState.Connected(device, deviceId)
                onStatusUpdate("Connected to device: $device")

                // 3. Start listening for incoming data on the persistent connection
                listenForData()

            } catch (e: Exception) {
                // Clean up on partial failure
                disconnect()
                _state.value = ConnectionState.Error(e.message ?: "An unknown error occurred")
                onStatusUpdate("Error: ${e.message}")
            }
        }
    }

    fun sendToken(data: String){
        val currentState = _state.value
        if (socketOutputStream == null || currentState !is ConnectionState.Connected) {
            onStatusUpdate("Cannot send: Not connected.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            _state.value = currentState.copy(isDecrypting = true)
            onStatusUpdate("Sending: $data")
            try {
                // Invio manuale dei byte con newline e flush forzato
                socketOutputStream?.write((data + "\n").toByteArray(Charsets.UTF_8))
                socketOutputStream?.flush()
                onStatusUpdate("Data flushed to socket.")
            } catch (e: IOException) {
                onStatusUpdate("Error: Failed to send data.")
                e.printStackTrace()
            }
        }
    }

    var waitBox: CompletableDeferred<String>? = null

    fun waitForResponse(responseBox: CompletableDeferred<String>) {
        waitBox=responseBox
    }

    fun decryptToken(data: String){
        onStatusUpdate("Received encrypted data")
        val secret=rsa.decrypt(data)
        onStatusUpdate("Decrypted: $secret")
        waitBox?.complete(secret)
    }

    private fun listenForData() {
        val initialState = _state.value
        if (initialState !is ConnectionState.Connected) {
            onStatusUpdate("Error: Not connected to a device.")
            return
        }

        listenJob = Job()
        CoroutineScope(Dispatchers.IO + listenJob!!).launch {
            try {
                onStatusUpdate("Listening for data...")
                val buffer = ByteArray(4096)
                while (isActive) {
                    // Lettura bloccante sui byte grezzi
                    val readCount = socketInputStream?.read(buffer)
                    if (readCount == null || readCount == -1) {
                        onStatusUpdate("Device disconnected (EOF). Socket closed by remote peer.")
                        break // Exit the listening loop
                    }
                    val rawData = String(buffer, 0, readCount, Charsets.UTF_8)
                    // Gestiamo il caso in cui arrivino più messaggi o manchi il newline
                    val responses = rawData.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

                    for (response in responses) {
                    onStatusUpdate("Received: $response")
                    val currentState = _state.value
                    if (currentState is ConnectionState.Connected && currentState.isDecrypting){
                        decryptToken(response)
                        _state.value = currentState.copy(isDecrypting = false)
                    }
                    else
                        try {
                            val userHome = System.getProperty("user.home")
                            val appDir = File(userHome, ".Triceratops/tokens/${initialState.deviceId}")
                            appDir.mkdirs() // Ensure the directory exists
                            val nameFile = File(appDir, "name")
                            if (!nameFile.exists() || nameFile.readText() != initialState.device) {
                                nameFile.writeText(initialState.device)
                            }
                            val uuid = UUID.randomUUID().toString()
                            val outputFile = File(appDir, uuid)
                            outputFile.writeText(response)
                            onStatusUpdate("Data received and saved to ${outputFile.absolutePath}")
                            socketOutputStream?.write((uuid + "\n").toByteArray(Charsets.UTF_8))
                            socketOutputStream?.flush()
                        } catch (e: IOException) {
                            onStatusUpdate("Data received but failed to save to file: ${e.message}")
                        }
                    }
                }
            } catch (e: IOException) {
                if (isActive) onStatusUpdate("Client connection error: ${e.message}. Waiting for new connection.")
            } catch (e: CancellationException) {
                onStatusUpdate("Stopped listening for data.")
            } catch (e: Exception) {
                onStatusUpdate("Error during communication: ${e.message}")
            } finally {
                // If the loop exits, ensure we are fully disconnected.
                disconnect()
            }
        }
    }

    fun disconnect() {
        // Check if already disconnected to prevent redundant calls
        if (_state.value is ConnectionState.Disconnected) return

        try {
            listenJob?.cancel() // Stop the listening coroutine
            // Close resources in reverse order of creation
            socketOutputStream?.close()
            socketInputStream?.close()
            stream?.close()
            dadb?.close()

            // Nullify all resources
            listenJob = null
            socketOutputStream = null
            socketInputStream = null
            stream = null
            _state.value = ConnectionState.Disconnected
            onStatusUpdate("Disconnected.")
        } catch (e: IOException) {
            onStatusUpdate("Error during disconnect: ${e.message}")
        }
    }
}