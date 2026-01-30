package com.pesegato.security

import java.util.Properties

object Config {
    @JvmStatic
    var algorithm: String = "PKCS1"
    @JvmStatic
    var idMode: String = "KEYRING"
    @JvmStatic
    var tpmStatus: String = "Unknown"
    @JvmStatic
    var isDocker: Boolean = System.getenv("RUNNING_IN_DOCKER") == "true"
    @JvmStatic
    var hostHostname: String? = System.getenv("HOST_HOSTNAME")
    @JvmStatic
    var configLoaded: Boolean = false

    fun loadFromProperties(props: Properties) {
        algorithm = props.getProperty("tpm.algorithm", "PKCS1")
        idMode = props.getProperty("tpm.id_mode", "KEYRING")
        tpmStatus = props.getProperty("tpm.hw_verified", "Unknown")
        configLoaded = true
    }
}