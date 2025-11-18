package br.com.ibra.repository

import br.com.ibra.dto.AnaliseSoloRequest
import br.com.ibra.model.DocumentoAnalise
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class AdminIARepository(
    private val jdbcTemplate: JdbcTemplate
) {

    fun findLaboratorioIdByNome(nome: String): Int? =
        jdbcTemplate.queryForObject(
            "SELECT id FROM ia.laboratorios WHERE nome ILIKE ?",
            Int::class.java,
            nome
        )

    fun inserirAnalise(analise: AnaliseSoloRequest, embedding: List<Double>) {
        val embStr = embedding.joinToString(",")

        val labId = findLaboratorioIdByNome(analise.laboratorio)
            ?: throw IllegalArgumentException("Laboratório '${analise.laboratorio}' não encontrado")

        val sql = """
            INSERT INTO ia.analises 
                (nome, descricao, preco, unidade, tipo, matriz_id, laboratorio_id, embedding)
            VALUES 
                (?, ?, ?, ?, ?, ?, ?, ?::vector)
        """.trimIndent()

        jdbcTemplate.update(
            sql,
            analise.nome,
            analise.descricao,
            analise.preco,
            analise.unidade,
            analise.tipo,
            labId,
            "ARRAY[$embStr]"
        )
    }

    fun listarAnalises(): List<DocumentoAnalise> {
        val sql = """
            SELECT a.id, a.nome, a.descricao, a.preco, a.unidade, a.tipo,
                  l.nome AS laboratorio
              FROM ia.analises a
              JOIN ia.laboratorios l ON l.id = a.laboratorio_id
             ORDER BY a.id
        """.trimIndent()

        return jdbcTemplate.query(sql) { rs, _ ->
            DocumentoAnalise(
                id = rs.getLong("id"),
                nome = rs.getString("nome"),
                descricao = rs.getString("descricao"),
                preco = rs.getDouble("preco"),
                unidade = rs.getString("unidade"),
                tipo = rs.getString("tipo"),
                laboratorio = rs.getString("laboratorio")
            )
        }
    }

    fun deletarAnalise(id: Long) {
        jdbcTemplate.update("DELETE FROM ia.analises WHERE id = ?", id)
    }

}