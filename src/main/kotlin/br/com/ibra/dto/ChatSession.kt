package br.com.ibra.dto

import br.com.ibra.service.Canal
import java.time.OffsetDateTime

data class ChatSession(
    val sessionId: String,
    val canal: Canal,
    val cliente: String? = null,
    val cpf: String? = null,
    val contexto: String? = null,
    val criadoEm: OffsetDateTime,
    val atualizadoEm: OffsetDateTime
)