package br.com.ibra.service.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import kotlin.collections.get

@Service
class GerarContextoAnalisesService(
    @Value("\${openai.api.key}")
    private val apiKey: String,
    @Value("\${openai.api.uri:}")
    private val apiUri: String,
    private val jdbcTemplate: JdbcTemplate,
    private val mapper: ObjectMapper
) {
    private val client = OkHttpClient()

    fun gerarContextos() {
        val analises = jdbcTemplate.query("SELECT id, nome, descricao FROM ia.analises") { rs, _ ->
            Triple(rs.getLong("id"), rs.getString("nome"), rs.getString("descricao"))
        }

        analises.forEach { (id, nome, descricao) ->
            val prompt = """
                Você é especialista em análises laboratoriais agrícolas.
                Gere um texto curto (máx 120 palavras) explicando:
                - Para que serve a análise "$nome"
                - Quando ela deve ser solicitada
                - O que o resultado ajuda o produtor a decidir
                - De forma simples, sem linguagem acadêmica.

                Descrição original: $descricao
            """.trimIndent()

            val payload = mapOf(
                "model" to "gpt-4o-mini",
                "messages" to listOf(mapOf("role" to "user", "content" to prompt))
            )

            val body = mapper.writeValueAsString(payload)
                .toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("${apiUri}/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: return@use
                val parsed: Map<String, Any> = mapper.readValue(text)
                val msg = ((parsed["choices"] as List<*>)[0] as Map<*, *>)["message"] as Map<*, *>
                val contexto = msg["content"] as String

                jdbcTemplate.update(
                    "UPDATE ia.analises SET contexto = ? WHERE id = ?",
                    contexto, id
                )
            }
        }
    }
}