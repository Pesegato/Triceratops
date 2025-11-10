package com.pesegato

import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class AdbServer(private val onDataReceived: (String) -> Unit) {

    private var dadb: Dadb? = null
    private var serverSocket: ServerSocket? = null

    fun receiveToken() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Dadb.discover() finds the ADB server automatically
                dadb = Dadb.discover()
                if (dadb == null) {
                    withContext(Dispatchers.Main) { onDataReceived("Error: ADB server not found.") }
                    return@launch
                }

                // 2. Get the first connected device
                val devices = Dadb.list()
                if (devices.isEmpty()) {
                    withContext(Dispatchers.Main) { onDataReceived("Error: No Android device connected.") }
                    return@launch
                }

                //serverSocket = ServerSocket(0)
                //val localPort = serverSocket!!.localPort
                val devicePort = 7001 // The port the Android app will connect to

                val device = devices.first()

                // 3. Open a shell stream to the device and execute a command
                //    This is a simple example. For real data, you'd use `device.open() as in previous attempts`
                onDataReceived("Device connected: ${device}. Waiting for data...")

                dadb!!.tcpForward(
                    hostPort = devicePort,
                    targetPort = devicePort
                ).use {


                    onDataReceived("Connected with $device!")

                    val socket =Socket("localhost", devicePort)

                    val input = socket.inputStream.bufferedReader()
                    val response = input.readLine() // Reads one line of text
                    onDataReceived("Received: $response")
                    try {
                        val userHome = System.getProperty("user.home")
                        val appDir = File(userHome, ".Triceratops/$device")
                        appDir.mkdirs() // Ensure the directory exists
                        val outputFile = File(appDir, UUID.randomUUID().toString())
                        outputFile.writeText(response)
                        withContext(Dispatchers.Main) {
                            onDataReceived("Data received and saved to ${outputFile.absolutePath}")
                        }
                    } catch (e: IOException){
                        withContext(Dispatchers.Main){
                            onDataReceived("Data received but failed to save to file: ${e.message}")
                        }
                    }
                    val output = PrintWriter(socket.getOutputStream(), true)
                    val message = "SuperJSON"
                    output.println(message)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onDataReceived("Error: ${e.message}") }
            } finally {
                stop()
            }
        }
    }

    fun sendToken(data: String) {
        println(data)
    }

    fun stop() {
        try {
            serverSocket?.close()
            dadb?.close()
        } catch (_: IOException) {
            // Log or handle error
        }
    }
}
