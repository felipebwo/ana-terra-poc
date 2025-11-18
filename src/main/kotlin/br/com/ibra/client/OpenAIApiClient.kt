package br.com.ibra.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class OpenAIApiClient(
    @Value("\${openai.api.key}")
    private val apiKey: String,
    @Value("\${openai.api.base-url:https://api.openai.com}")
    private val apiUri: String
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val mapper = jacksonObjectMapper()

    /**
     * Gera uma resposta “natural” para o usuário (usado pela Ana Terra).
     *
     * @param prompt  Instruções + contexto (já montado no serviço)
     * @param fallback Texto de fallback caso dê erro na chamada
     */
    fun gerarRespostaNatural(
        prompt: String,
        fallback: String = ""
    ): String {
        val system = """
            Você é Ana Terra, assistente virtual de um laboratório de análises agrícolas.
            Responda SEMPRE em português do Brasil, de forma educada, acolhedora, direta e clara.
        """.trimIndent()

        return chamarChatCompletion(
            system = system,
            user = prompt,
            temperature = 0.5,
            fallback = fallback
        )
    }

    /**
     * Retorna a resposta “bruta” do modelo, usada para classificação (JSON etc.).
     *
     * @param system   Mensagem de sistema (instruções do modelo)
     * @param user     Conteúdo da mensagem do usuário
     * @param fallback Retorno padrão se algo der errado
     */
    fun completarBruto(
        system: String,
        user: String,
        fallback: String = ""
    ): String {
        return chamarChatCompletion(
            system = system,
            user = user,
            temperature = 0.0,
            fallback = fallback
        )
    }

    /**
     * Função interna que chama /v1/chat/completions já lidando com o formato do GPT-5.
     *
     * - Suporta tanto `content` como String
     * - Quanto `content` como lista de blocos com `text` (formato novo)
     */
    private fun chamarChatCompletion(
        system: String,
        user: String,
        temperature: Double,
        fallback: String
    ): String {
        val payload = mapOf(
            "model" to "gpt-5",  // pode trocar para gpt-5.1 / gpt-5.1-mini se quiser
            "messages" to listOf(
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to user)
            ),
           // "temperature" to temperature
        )

        return try {
            val body: RequestBody =
                mapper.writeValueAsString(payload)
                    .toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("$apiUri/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                //.addHeader("Content-Type", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string()
                if (text.isNullOrBlank()) {
                    println("OpenAIApiClient >> resposta vazia, usando fallback")
                    return fallback
                }

                // DEBUG opcional:
                // println("OpenAIApiClient >> raw response: $text")

                val parsed: Map<String, Any?> = mapper.readValue(text)
                val choices = parsed["choices"] as? List<*> ?: return fallback
                val first = choices.firstOrNull() as? Map<*, *> ?: return fallback
                val message = first["message"] as? Map<*, *> ?: return fallback

                val content = message["content"]

                val result: String? = when (content) {
                    is String -> {
                        // Formato antigo: content é uma string
                        content
                    }
                    is List<*> -> {
                        // Formato novo: lista de blocos, extraímos os "text"
                        content.joinToString(separator = "") { item ->
                            val m = item as? Map<*, *>
                            val textField = m?.get("text")
                            when (textField) {
                                is String -> textField
                                is Map<*, *> -> textField["text"] as? String ?: ""
                                else -> ""
                            }
                        }
                    }
                    else -> null
                }

                result?.trim().takeUnless { it.isNullOrBlank() } ?: fallback
            }
        } catch (e: Exception) {
            println("OpenAIApiClient >> erro chamando OpenAI: ${e.message}")
            fallback
        }
    }
}
