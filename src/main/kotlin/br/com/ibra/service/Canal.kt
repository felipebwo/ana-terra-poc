package br.com.ibra.service

enum class Canal {
    WHATSAPP,
    WEB,
    EMAIL
}

fun buildSessionKey(canal: Canal, rawId: String): String =
    canal.name.lowercase() + ":" + rawId
