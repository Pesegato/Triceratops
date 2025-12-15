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
import java.net.Socket
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
// Defines the possible states of our ADB connection
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val device: String, val deviceId: Dadb) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class AdbServer(private val onStatusUpdate: (String) -> Unit) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var dadb: Dadb? = null
    private var listenJob: Job? = null

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

                _state.value = ConnectionState.Connected(device, deviceId)
                onStatusUpdate("Connected to device: $device")
                // Start listening for data now that we are connected
                listenForData()

            } catch (e: Exception) {
                _state.value = ConnectionState.Error(e.message ?: "An unknown error occurred")
                onStatusUpdate("Error: ${e.message}")
            }
        }
    }

    fun sendToken(data: String){
        println("Sending data: $data")
    }

    private fun listenForData() {
        val currentState = _state.value
        if (currentState !is ConnectionState.Connected) {
            onStatusUpdate("Error: Not connected to a device.")
            return
        }

        listenJob = Job()
        CoroutineScope(Dispatchers.IO + listenJob!!).launch {
            val devicePort = 4242 // The port the Android app is listening on
            try {
                dadb!!.tcpForward(
                    hostPort = devicePort,
                    targetPort = devicePort
                ).use {
                    onStatusUpdate("Port forwarded. Ready to receive data.")
                    while (isActive) {
                        try {
                            // This will block until a client connects from the device
                            val socket = Socket("localhost", devicePort)
                            onStatusUpdate("Device client connected.")

                            socket.use { clientSocket ->
                                val input = clientSocket.inputStream.bufferedReader()
                                val response = input.readLine() // Reads one line of text
                                if (response == null) {
                                    onStatusUpdate("Client disconnected.")
                                    return@use // continue to next iteration of while(isActive)
                                }
                                withContext(Dispatchers.Main) { onStatusUpdate("Received: $response") }

                                try {
                                    val userHome = System.getProperty("user.home")
                                    val appDir = File(userHome, ".Triceratops/tokens/${currentState.deviceId}")
                                    appDir.mkdirs() // Ensure the directory exists
                                    val nameFile = File(appDir, "name")
                                    if (!nameFile.exists() || nameFile.readText() != currentState.device) {
                                        nameFile.writeText(currentState.device)
                                    }
                                    val outputFile = File(appDir, UUID.randomUUID().toString())
                                    outputFile.writeText(response)
                                    withContext(Dispatchers.Main) {
                                        onStatusUpdate("Data received and saved to ${outputFile.absolutePath}")
                                    }
                                } catch (e: IOException) {
                                    withContext(Dispatchers.Main) {
                                        onStatusUpdate("Data received but failed to save to file: ${e.message}")
                                    }
                                }
                                val output = PrintWriter(clientSocket.getOutputStream(), true)
                                val message = "SuperJSON"
                                output.println(message)
                            }
                        } catch (e: IOException) {
                            onStatusUpdate("Client connection error: ${e.message}. Waiting for new connection.")
                        }
                    }
                }
            } catch (e: CancellationException) {
                onStatusUpdate("Stopped listening for data.")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onStatusUpdate("Error during communication: ${e.message}") }
            } finally {
                onStatusUpdate("Communication channel closed.")

            }
        }
    }

    fun disconnect() {
        try {
            dadb?.close()
            listenJob?.cancel()
            listenJob = null
            _state.value = ConnectionState.Disconnected
            onStatusUpdate("Disconnected.")
        } catch (e: IOException) {
            onStatusUpdate("Error during disconnect: ${e.message}")
        }
    }
}