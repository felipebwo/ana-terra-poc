package br.com.ibra.controller

import br.com.ibra.service.Canal
import br.com.ibra.service.ChatAnaTerraService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/email")
class EmailChatController(
    private val chatAnaTerra: ChatAnaTerraService
) {

    data class EmailRequest(
        val from: String,
        val body: String
    )

    data class EmailResponse(
        val reply: String
    )

    @PostMapping("/chat")
    fun simular(@RequestBody req: EmailRequest): EmailResponse {
        val reply = chatAnaTerra.processarMensagem(Canal.EMAIL, req.from, req.body)
        return EmailResponse(reply = reply)
    }
}
