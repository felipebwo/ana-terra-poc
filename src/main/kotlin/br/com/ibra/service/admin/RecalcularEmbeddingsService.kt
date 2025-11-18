package br.com.ibra.service.admin

import br.com.ibra.client.EmbeddingClient
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class RecalcularEmbeddingsService(
    private val embeddingService: EmbeddingClient,
    private val jdbcTemplate: JdbcTemplate
) {

    fun atualizarEmbeddings() {
        val lista = jdbcTemplate.query("SELECT id, contexto FROM ia.analises") { rs, _ ->
            rs.getLong("id") to rs.getString("contexto")
        }

        lista.forEach { (id, contexto) ->
            val emb = embeddingService.gerarEmbedding(contexto)
            val embStr = emb.joinToString(",")

            jdbcTemplate.update(
                "UPDATE ia.analises SET embedding = ARRAY[$embStr]::vector WHERE id = ?",
                id
            )
        }
    }
}