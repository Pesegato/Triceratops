package com.pesegato

import dadb.AdbStream
import dadb.Dadb
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.SocketException

// Defines the possible states of our ADB connection
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val device: String, val deviceId: String, val isDecrypting: Boolean = false) :
        ConnectionState()

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
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    fun connect(onConnected: () -> Unit) {
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
                writer = PrintWriter(socketOutputStream!!, true)
                reader = BufferedReader(InputStreamReader(socketInputStream!!))

                _state.value = ConnectionState.Connected(device, deviceId.toString())
                onStatusUpdate("Connected to device: $device")

                withContext(Dispatchers.Main) {
                    onConnected()
                }
                // 3. Start listening for incoming data on the persistent connection

            } catch (e: Exception) {
                // Clean up on partial failure
                disconnect()
                _state.value = ConnectionState.Error(e.message ?: "An unknown error occurred")
                onStatusUpdate("Error: ${e.message}")
            }
        }
    }

    var unlock: String = "\uD83D\uDD13"
    var poliziotta: String = "\uD83D\uDC6E\u200D\u2640\uFE0F"
    var etx: ByteArray = "🦕".toByteArray(Charsets.UTF_8);

    fun sendToken(data: String) {
        val currentState = _state.value
        if (socketOutputStream == null || currentState !is ConnectionState.Connected) {
            onStatusUpdate("Cannot send: Not connected.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            _state.value = currentState.copy(isDecrypting = true)
            onStatusUpdate("Sending: $poliziotta")
            onStatusUpdate("Sending: $data")
            try {
                socketOutputStream?.write((poliziotta + "\n").toByteArray(Charsets.UTF_8))
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
        waitBox = responseBox
    }

    /*
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
        */

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


// Inserisci questo codice all'interno della classe ADBConnector

    /**
     * Scrive un singolo messaggio di testo al client connesso.
     * Questa è una funzione di sospensione (suspending function) che deve essere chiamata da una coroutine.
     *
     * @param message La stringa da inviare.
     * @throws SocketException se si verifica un errore durante la scrittura (es. client disconnesso).
     * @throws IllegalStateException se nessun client è connesso.
     */
    suspend fun writeToServer(message: String) {
        // Esegui sul dispatcher IO, ideale per le operazioni di rete
        withContext(Dispatchers.IO) {
            // Controlla lo stato della connessione prima di procedere
            if (writer == null || _state.value !is ConnectionState.Connected) {
                throw IllegalStateException("Nessun client connesso o writer non inizializzato.")
            }

            try {
                println("ADBConnector: write to client: $message")
                writer?.println(message)
                // writer?.flush() // println con autoFlush=true lo fa già, ma esplicitarlo è sicuro.
            } catch (e: Exception) {
                // Se la scrittura fallisce, è probabile che il client si sia disconnesso.
                println("ADBConnector, Errore durante la scrittura sul client: ${e.message}")
                disconnect() // Gestisci la disconnessione
                throw SocketException("Scrittura fallita. Il client potrebbe essersi disconnesso.")
            }
        }
    }

    /**
     * Legge una singola riga di testo dal client connesso.
     * Questa è una funzione di sospensione (suspending function) che attende un messaggio.
     *
     * @return La stringa letta dal client.
     * @throws SocketException se la connessione viene chiusa durante la lettura.
     * @throws IllegalStateException se nessun client è connesso.
     */
    suspend fun readFromServer(): String {
        // Esegui sul dispatcher IO
        return withContext(Dispatchers.IO) {
            // Controlla lo stato della connessione
            if (reader == null || _state.value !is ConnectionState.Connected) {
                throw IllegalStateException("Nessun client connesso o reader non inizializzato.")
            }

            try {
                println("ADBConnector, In attesa di leggere dal client...")
                val message = reader?.readLine()

                if (message != null) {
                    println("ADBConnector, Letto dal client: $message")
                    message // Ritorna il messaggio letto
                } else {
                    // readLine() restituisce null se lo stream è terminato (client disconnesso).
                    println("ADBConnector Stream terminato. Il client si è disconnesso.")
                    disconnect()
                    throw SocketException("Client disconnesso durante la lettura.")
                }
            } catch (e: Exception) {
                println("ADBConnector, Errore durante la lettura dal client: ${e.message}")
                disconnect() // Gestisci la disconnessione in caso di errore
                throw SocketException("Lettura fallita: ${e.message}")
            }
        }
    }
}