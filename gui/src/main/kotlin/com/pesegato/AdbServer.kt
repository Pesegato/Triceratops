package com.pesegato

import dadb.Dadb
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.BufferedReader
import java.net.Socket
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
// Defines the possible states of our ADB connection
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Decrypting : ConnectionState()
    data class Connected(val device: String, val deviceId: Dadb) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class AdbServer(private val onStatusUpdate: (String) -> Unit) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var dadb: Dadb? = null
    private var listenJob: Job? = null
    private var portForward: AutoCloseable? = null
    private var socket: Socket? = null
    private var socketWriter: PrintWriter? = null
    private var socketReader: BufferedReader? = null

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

                // 1. Establish port forward for the session
                val devicePort = 4242
                portForward = dadb!!.tcpForward(devicePort, devicePort)
                onStatusUpdate("Port forwarded. Connecting to device...")

                // 2. Create a single, persistent socket connection
                socket = Socket("localhost", devicePort)
                socketWriter = PrintWriter(socket!!.getOutputStream(), true)
                socketReader = socket!!.getInputStream().bufferedReader()

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
        if (socketWriter == null || _state.value !is ConnectionState.Connected) {
            onStatusUpdate("Cannot send: Not connected.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            _state.value = ConnectionState.Decrypting
            onStatusUpdate("Sending: $data")
            println("Sending: $data")
            socketWriter?.println(data)
        }
    }

    fun decryptToken(data: String){
        println("Received $data")
    }

    private fun listenForData() {
        val currentState = _state.value
        if (currentState !is ConnectionState.Connected) {
            onStatusUpdate("Error: Not connected to a device.")
            return
        }

        listenJob = Job()
        CoroutineScope(Dispatchers.IO + listenJob!!).launch {
            try {
                onStatusUpdate("Listening for data...")
                while (isActive) {
                    // readLine() is a blocking call, but it's in a coroutine.
                    // It will suspend until a line is received or the socket is closed.
                    val response = socketReader?.readLine()
                    if (response == null) {
                        onStatusUpdate("Device disconnected.")
                        // Trigger a full disconnect to clean up and reset state
                        withContext(Dispatchers.Main) { disconnect() }
                        break // Exit the listening loop
                    }
                    withContext(Dispatchers.Main) { onStatusUpdate("Received: $response") }

                    if (_state.value is ConnectionState.Decrypting){
                        decryptToken(response)
                    }
                    else
                                try {
                                    val userHome = System.getProperty("user.home")
                                    val appDir = File(userHome, ".Triceratops/tokens/${currentState.deviceId}")
                                    appDir.mkdirs() // Ensure the directory exists
                                    val nameFile = File(appDir, "name")
                                    if (!nameFile.exists() || nameFile.readText() != currentState.device) {
                                        nameFile.writeText(currentState.device)
                                    }
                                    val uuid = UUID.randomUUID().toString()
                                    val outputFile = File(appDir, uuid)
                                    outputFile.writeText(response)
                                    withContext(Dispatchers.Main) {
                                        onStatusUpdate("Data received and saved to ${outputFile.absolutePath}")
                                    }
                                    val output = PrintWriter(socket!!.getOutputStream(), true)
                                    output.println(uuid)
                                } catch (e: IOException) {
                                    withContext(Dispatchers.Main) {
                                        onStatusUpdate("Data received but failed to save to file: ${e.message}")
                                    }
                                }
                            }
                        } catch (e: IOException) {
                            onStatusUpdate("Client connection error: ${e.message}. Waiting for new connection.")
            } catch (e: CancellationException) {
                onStatusUpdate("Stopped listening for data.")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onStatusUpdate("Error during communication: ${e.message}") }
            } finally {
                // If the loop exits, ensure we are fully disconnected.
                withContext(Dispatchers.Main) { disconnect() }
            }
        }
    }

    fun disconnect() {
        // Check if already disconnected to prevent redundant calls
        if (_state.value is ConnectionState.Disconnected) return

        try {
            listenJob?.cancel() // Stop the listening coroutine
            // Close resources in reverse order of creation
            socketWriter?.close()
            socketReader?.close()
            socket?.close()
            portForward?.close() // Remove the ADB port forward rule
            dadb?.close()

            // Nullify all resources
            listenJob = null
            socketWriter = null
            socketReader = null
            socket = null
            portForward = null
            _state.value = ConnectionState.Disconnected
            onStatusUpdate("Disconnected.")
        } catch (e: IOException) {
            onStatusUpdate("Error during disconnect: ${e.message}")
        }
    }
}