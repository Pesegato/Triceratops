package com.pesegato.model

import com.pesegato.data.Token

data class LoadedToken(
    val uuid: String,
    val label: String,
    val color: Token.Color
)