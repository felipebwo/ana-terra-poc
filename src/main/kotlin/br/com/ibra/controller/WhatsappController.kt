package br.com.ibra.controller

import br.com.ibra.service.Canal
import br.com.ibra.service.ChatAnaTerraService
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/whatsapp")
class WhatsappController(
    private val chatAnaTerra: ChatAnaTerraService,
    private val mapper: ObjectMapper,
    @Value("\${whatsapp.verify-token:changeme}") private val verifyToken: String,
    @Value("\${whatsapp.access-token:changeme}") private val accessToken: String
) {

    private val client = OkHttpClient()

    @GetMapping("/webhook")
    fun verify(
        @RequestParam("hub.verify_token", required = false) token: String?,
        @RequestParam("hub.challenge", required = false) challenge: String?
    ): String =
        if (token == verifyToken) challenge ?: "" else "invalid token"

    @PostMapping("/webhook")
    fun receive(@RequestBody body: Map<String, Any>) {
        val entry = (body["entry"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return
        val change = (entry["changes"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return
        val value = change["value"] as? Map<*, *> ?: return
        val msg = (value["messages"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return

        val from = msg["from"]?.toString() ?: return
        val text = ((msg["text"] as? Map<*, *>)?.get("body"))?.toString() ?: ""

        val resposta = chatAnaTerra.processarMensagem(Canal.WHATSAPP, from, text)

        val metadata = value["metadata"] as? Map<*, *> ?: return
        val phoneId = metadata["phone_number_id"].toString()

        val payload = mapOf(
            "messaging_product" to "whatsapp",
            "to" to from,
            "type" to "text",
            "text" to mapOf("body" to resposta)
        )
        val json = mapper.writeValueAsString(payload)

        val req = Request.Builder()
            .url("https://graph.facebook.com/v18.0/$phoneId/messages")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { }
    }
}
