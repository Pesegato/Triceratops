package com.pesegato.model

/**
 * Rappresenta un dispositivo che ha salvato dei token.
 * @param id L'identificativo univoco del dispositivo (corrisponde al nome della cartella).
 * @param displayName Il nome leggibile del dispositivo, recuperato dal file 'name'.
 */
data class Device(val id: String, val displayName: String)