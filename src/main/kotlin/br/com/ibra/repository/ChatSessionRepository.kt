package br.com.ibra.repository

import br.com.ibra.dto.ChatSession
import br.com.ibra.service.Canal
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ChatSessionRepository(
    private val jdbcTemplate: JdbcTemplate
) {

    fun upsert(sessionId: String, canal: Canal, cliente: String? = null): ChatSession {
        jdbcTemplate.update(
            """
            INSERT INTO ia.chat_session (session_id, canal, cliente)
            VALUES (?, ?, ?)
            ON CONFLICT (session_id)
            DO UPDATE SET
              canal = EXCLUDED.canal,
              cliente = COALESCE(EXCLUDED.cliente, chat_session.cliente),
              atualizado_em = now()
            """.trimIndent(),
            sessionId,
            canal.name.lowercase(),
            cliente
        )
        return findById(sessionId)!!
    }

    fun findById(sessionId: String): ChatSession? {
        return jdbcTemplate.query(
            """
        SELECT session_id, canal, cliente, cpf, contexto, criado_em, atualizado_em
        FROM ia.chat_session
        WHERE session_id = ?
        """.trimIndent(),
            { rs, _ ->
                ChatSession(
                    sessionId = rs.getString("session_id"),
                    canal = Canal.valueOf(rs.getString("canal").uppercase()),
                    cliente = rs.getString("cliente"),
                    cpf = rs.getString("cpf"),
                    contexto = rs.getString("contexto"),
                    criadoEm = rs.getObject("criado_em", OffsetDateTime::class.java),
                    atualizadoEm = rs.getObject("atualizado_em", OffsetDateTime::class.java)
                )
            },
            sessionId
        ).firstOrNull()
    }

    fun updateCpf(sessionId: String, cpf: String) {
        jdbcTemplate.update(
            """
        UPDATE ia.chat_session
           SET cpf = ?, atualizado_em = now()
         WHERE session_id = ?
        """.trimIndent(),
            cpf,
            sessionId
        )
    }

    fun updateContext(sessionId: String, contextoJson: String?) {
        jdbcTemplate.update(
            """
            UPDATE ia.chat_session
               SET contexto = ?::jsonb,
                   atualizado_em = now()
             WHERE session_id = ?
            """.trimIndent(),
            contextoJson,
            sessionId
        )
    }

    fun delete(sessionId: String) {
        jdbcTemplate.update("DELETE FROM ia.chat_session WHERE session_id = ?", sessionId)
    }
}
