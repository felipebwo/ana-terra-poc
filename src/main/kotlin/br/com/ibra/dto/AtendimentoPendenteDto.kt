package br.com.ibra.dto

data class AtendimentoPendenteDto(
    val id: Long,
    val sessionKey: String,
    val cpf: String?,
    val canal: String,
    val motivo: String?,
    val ultimaMensagemCliente: String,
    val criadoEm: String
)
