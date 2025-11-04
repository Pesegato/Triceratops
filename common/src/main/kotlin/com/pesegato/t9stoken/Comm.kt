package com.pesegato.t9stoken

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Retrieves the hostname of the local machine.
 *
 * @return The hostname as a String, or a fallback value like "unknown" if it cannot be determined.
 */
fun getHostname(): String {
    return try {
        InetAddress.getLocalHost().hostName
    } catch (e: UnknownHostException) {
        println("Could not determine hostname: ${e.message}")
        "unknown" // Fallback value
    }
}