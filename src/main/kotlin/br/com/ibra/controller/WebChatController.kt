package br.com.ibra.controller

import br.com.ibra.client.OpenAIApiClient
import br.com.ibra.service.Canal
import br.com.ibra.service.ChatAnaTerraService
import org.springframework.web.bind.annotation.*
import java.util.UUID
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths

@RestController
@RequestMapping("/web-chat")
class WebChatController(
    private val openAIApiClient: OpenAIApiClient,
    private val chatAnaTerra: ChatAnaTerraService
) {
    data class ChatRequest(
        val sessionId: String? = null,
        val message: String,
        val canal: String? = null,
        val userId: String? = null
    )

    data class ChatResponse(
        val sessionId: String,
        val reply: String
    )

    @PostMapping
    fun conversar(
        @RequestBody req: ChatRequest,
        @CookieValue(name = "ana_terra_user_id", required = false) cookieUserId: String?,
        response: HttpServletResponse
    ): ChatResponse {

        // userId efetivo: prioriza o que veio no body, depois o cookie
        val effectiveUserId = req.userId ?: cookieUserId ?: UUID.randomUUID().toString()

        // se não tinha cookie ainda, grava um
        if (cookieUserId == null) {
            val c = Cookie("ana_terra_user_id", effectiveUserId)
            c.path = "/"
            c.maxAge = 365 * 24 * 60 * 60  // 1 ano
            // c.isHttpOnly = true // se quiser proteger de JS, aí não dá pra ler no front
            response.addCookie(c)
        }

        // sessionId continua como antes (pode usar só o do front)
        val sid = req.sessionId ?: UUID.randomUUID().toString()

        val reply = chatAnaTerra.processarMensagem(
            Canal.WEB,
            sid,
            req.message
        )

        return ChatResponse(sessionId = sid, reply = reply)
    }

    @PostMapping("/audio", consumes = ["multipart/form-data"])
    fun conversarAudio(
        @RequestParam("audio") audio: MultipartFile,
        @RequestParam("sessionId", required = false) sessionIdParam: String?,
        @RequestParam("canal", required = false) canalParam: String?,
        @CookieValue(name = "ana_terra_user_id", required = false) cookieUserId: String?,
    ): ChatResponse {

        println(
            ">> /audio recebeu arquivo: " +
                    "size=${audio.size}, " +
                    "contentType=${audio.contentType}, " +
                    "originalFilename=${audio.originalFilename}"
        )

        // SALVAR TEMPORARIAMENTE PARA INSPECIONAR
        try {
            val tempPath = Paths.get("/tmp/ana-terra-audio-${System.currentTimeMillis()}.webm")
            Files.write(tempPath, audio.bytes)
            println(">> Arquivo salvo em: $tempPath")
        } catch (e: Exception) {
            println(">> Erro ao salvar arquivo temporário: ${e.message}")
        }

        val sid = sessionIdParam ?: UUID.randomUUID().toString()

        // 1) Transcrever áudio
        val textoTranscrito = openAIApiClient.transcreverAudio(audio)

        // (debug opcional)
        println(">> Áudio transcrito: $textoTranscrito")

        // 2) Reusar o mesmo fluxo do texto normal
        val reply = chatAnaTerra.processarMensagem(
            Canal.WEB,
            sid,
            textoTranscrito
        )

        return ChatResponse(
            sessionId = sid,
            reply = reply
        )
    }


}
