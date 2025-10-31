package com.pesegato.t9stoken.model

import com.pesegato.data.Token

data class SecureToken(
    val label: String,
    val color: Token.Color,
    val secret: String
) {
/*
    constructor(token: Token) : this(
        // The following block calls the primary constructor after parsing the JSON.
        label = token.label,
        color = token.color,
        secret = token.secret!!
    )*/
}