package br.com.ibra.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class ChatLogService(
    private val jdbcTemplate: JdbcTemplate
) {
    fun registrar(
        sessionKey: String,
        cpf: String?,
        canal: Canal,
        papel: String, // "USER" / "BOT"
        mensagem: String
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO ia.chat_log (session_key, cpf, canal, papel, mensagem)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            sessionKey,
            cpf,
            canal.name,
            papel,
            mensagem
        )
    }
}
