package br.com.ibra.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.collections.get

@Service
class EmbeddingClient(
    @Value("\${openai.api.key:}")
    private val apiKey: String,
    @Value("\${openai.api.uri:}")
    private val apiUri: String
) {

    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()

    fun gerarEmbedding(texto: String): List<Double> {
        val url = "${apiUri}/embeddings"
        val payload = mapOf(
            "model" to "text-embedding-3-small",
            "input" to texto
        )
        val body = mapper.writeValueAsString(payload).toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val t = resp.body?.string() ?: throw IllegalStateException("Resposta vazia OpenAI")
            val parsed: Map<String,Any> = mapper.readValue(t)
            val data = parsed["data"] as List<*>
            val emb = (data[0] as Map<*,*>) ["embedding"] as List<Double>
            return emb
        }
    }
}