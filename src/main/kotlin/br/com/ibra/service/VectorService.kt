package br.com.ibra.service

import br.com.ibra.client.EmbeddingClient
import br.com.ibra.model.DocumentoAnalise
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class VectorService(
    private val jdbcTemplate: JdbcTemplate,
    private val embeddingService: EmbeddingClient
) {

    /**
     * Busca N análises mais semelhantes à pergunta do cliente (pelo vetor/embedding).
     */
    fun buscarAnalisesOrdenadas(pergunta: String, limite: Int = 1): List<DocumentoAnalise> {
        val emb = embeddingService.gerarEmbedding(pergunta)
        val embStr = emb.joinToString(",")

        val sql = """
            SELECT a.id,
                   a.nome,
                   a.descricao,
                   a.preco,
                   a.unidade,
                   a.tipo,
                   l.nome AS laboratorio,
                   (a.embedding <-> ARRAY[$embStr]::vector) AS distancia
            FROM ia.analises a
            JOIN ia.laboratorios l ON l.id = a.laboratorio_id
            ORDER BY distancia ASC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, arrayOf(limite)) { rs, _ ->
            DocumentoAnalise(
                id = rs.getLong("id"),
                nome = rs.getString("nome"),
                descricao = rs.getString("descricao"),
                preco = rs.getDouble("preco"),
                unidade = rs.getString("unidade"),
                tipo = rs.getString("tipo"),
                laboratorio = rs.getString("laboratorio"),
                distancia = rs.getDouble("distancia")
            )
        }
    }

    /**
     * Atalho: melhor análise (top-1) para um texto dado.
     */
    fun buscarMelhorAnalisePara(textoItem: String, maxDist: Double = 0.98): DocumentoAnalise? {
        val lista = buscarAnalisesOrdenadas(textoItem, limite = 1)
        val encontrado = lista.firstOrNull() ?: return null

        val dist = encontrado.distancia
        println(">> candidato: id=${encontrado.id}, nome='${encontrado.nome}', preço=${encontrado.preco}, dist=$dist")

        // se não veio distância por algum motivo, devolve mesmo assim
        if (dist == null) return encontrado

        // aplica o corte de similaridade
        return if (dist <= maxDist) {
            encontrado
        } else {
            println(">> nenhum candidato bom o suficiente (dist=$dist, max=$maxDist)")
            null
        }
    }


    /**
     * Lista análises de for forma semantica por distancia.
     */
    fun listarAnalisesSemantico(query: String, limite: Int = 100): List<DocumentoAnalise> {
        val emb = embeddingService.gerarEmbedding(query)
        val embStr = emb.joinToString(",")

        val sql = """
            SELECT a.id,
                   a.nome,
                   a.descricao,
                   a.preco,
                   a.unidade,
                   a.tipo,
                   l.nome AS laboratorio,
                  (a.embedding <-> ARRAY[$embStr]::vector) AS distancia
            FROM ia.analises a
            JOIN ia.laboratorios l ON l.id = a.laboratorio_id
            ORDER BY distancia ASC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, arrayOf(limite)) { rs, _ ->
            DocumentoAnalise(
                id = rs.getLong("id"),
                nome = rs.getString("nome"),
                descricao = rs.getString("descricao"),
                preco = rs.getDouble("preco"),
                unidade = rs.getString("unidade"),
                tipo = rs.getString("tipo"),
                laboratorio = rs.getString("laboratorio"),
                distancia = rs.getDouble("distancia")
            )
        }
    }
}
