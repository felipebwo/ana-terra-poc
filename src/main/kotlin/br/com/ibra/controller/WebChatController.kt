package br.com.ibra.controller

import br.com.ibra.service.Canal
import br.com.ibra.service.ChatAnaTerraService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/web-chat")
class WebChatController(
    private val chatAnaTerra: ChatAnaTerraService
) {
    data class ChatRequest(
        val sessionId: String? = null,
        val message: String
    )

    data class ChatResponse(
        val sessionId: String,
        val reply: String
    )

    @PostMapping
    fun conversar(@RequestBody req: ChatRequest): ChatResponse {
        val sid = req.sessionId ?: UUID.randomUUID().toString()
        val reply = chatAnaTerra.processarMensagem(Canal.WEB, sid, req.message)
        return ChatResponse(sessionId = sid, reply = reply)
    }
}
