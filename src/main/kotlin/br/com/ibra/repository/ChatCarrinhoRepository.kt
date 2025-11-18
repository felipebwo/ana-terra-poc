package br.com.ibra.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.OffsetDateTime

data class CarrinhoItem(
    val id: Long,
    val sessionId: String,
    val analiseId: Long,
    val nome: String,
    val precoUnitario: BigDecimal,
    val quantidade: Int,
    val criadoEm: OffsetDateTime
)

@Service
class ChatCarrinhoRepository(
    private val jdbcTemplate: JdbcTemplate
) {

    fun addItem(
        sessionId: String,
        analiseId: Long?,
        nome: String,
        precoUnitario: BigDecimal,
        quantidade: Int
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO ia.chat_carrinho_item (session_id, analise_id, nome, preco_unitario, quantidade)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            sessionId,
            analiseId,
            nome,
            precoUnitario,
            quantidade
        )
    }

    fun listBySession(sessionId: String): List<CarrinhoItem> {
        return jdbcTemplate.query(
            """
            SELECT id, session_id, analise_id, nome, preco_unitario, quantidade, criado_em
            FROM ia.chat_carrinho_item
            WHERE session_id = ?
            ORDER BY id
            """.trimIndent(),
            { rs, _ ->
                CarrinhoItem(
                    id = rs.getLong("id"),
                    sessionId = rs.getString("session_id"),
                    analiseId = rs.getLong("analise_id"),
                    nome = rs.getString("nome"),
                    precoUnitario = rs.getBigDecimal("preco_unitario"),
                    quantidade = rs.getInt("quantidade"),
                    criadoEm = rs.getObject("criado_em", OffsetDateTime::class.java)
                )
            },
            sessionId
        )
    }

    fun clearSession(sessionId: String) {
        jdbcTemplate.update(
            "DELETE FROM ia.chat_carrinho_item WHERE session_id = ?",
            sessionId
        )
    }
}
