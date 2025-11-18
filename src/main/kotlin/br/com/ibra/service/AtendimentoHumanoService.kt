package br.com.ibra.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class AtendimentoHumanoService(
    private val jdbcTemplate: JdbcTemplate
) {

    data class AtendimentoPendente(
        val id: Long,
        val sessionKey: String,
        val cpf: String?,
        val canal: Canal,
        val motivo: String?,
        val ultimaMensagemCliente: String,
        val criadoEm: OffsetDateTime
    )

    fun abrirChamado(
        sessionKey: String,
        cpf: String?,
        canal: Canal,
        motivo: String?,
        ultimaMensagemCliente: String
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO ia.chat_atendimento_pendente
                (session_key, cpf, canal, motivo, ultima_mensagem_cliente)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            sessionKey,
            cpf,
            canal.name,
            motivo,
            ultimaMensagemCliente
        )
    }

    fun listarPendentes(): List<AtendimentoPendente> {
        return jdbcTemplate.query(
            """
            SELECT id,
                   session_key,
                   cpf,
                   canal,
                   motivo,
                   ultima_mensagem_cliente,
                   criado_em
              FROM ia.chat_atendimento_pendente
             WHERE resolvido = false
             ORDER BY criado_em DESC
            """.trimIndent()
        ) { rs, _ ->
            AtendimentoPendente(
                id = rs.getLong("id"),
                sessionKey = rs.getString("session_key"),
                cpf = rs.getString("cpf"),
                canal = Canal.valueOf(rs.getString("canal")),
                motivo = rs.getString("motivo"),
                ultimaMensagemCliente = rs.getString("ultima_mensagem_cliente"),
                criadoEm = rs.getObject("criado_em", OffsetDateTime::class.java)
            )
        }
    }

    fun marcarResolvido(id: Long) {
        jdbcTemplate.update(
            """
            UPDATE ia.chat_atendimento_pendente
               SET resolvido = true
             WHERE id = ?
            """.trimIndent(),
            id
        )
    }
}
