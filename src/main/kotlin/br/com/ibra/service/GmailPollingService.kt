package br.com.ibra.service

import jakarta.mail.*
import jakarta.mail.Flags
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.search.FlagTerm
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.Properties

@Service
class GmailPollingService(

    @Value("\${gmail.username}")
    private val username: String,

    @Value("\${gmail.app-password}")
    private val appPassword: String,

    private val chatAnaTerraService: ChatAnaTerraService
) {

    /**
     * Roda a cada 10s (ajuste o intervalo como quiser).
     * Lê e-mails não lidos, gera resposta via IA e responde automaticamente.
     */
    @Scheduled(fixedDelay = 10_000)
    fun checkInbox() {
        println(">> [GmailPollingService] Verificando inbox do Gmail...")

        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", "imap.gmail.com")
            put("mail.imaps.port", "993")
            put("mail.imaps.ssl.enable", "true")

            put("mail.transport.protocol", "smtp")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, appPassword)
            }
        })

        var store: Store? = null
        var inbox: Folder? = null

        try {
            store = session.getStore("imaps")
            store.connect("imap.gmail.com", username, appPassword)

            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)

            // Busca apenas não lidos
            val unread = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))

            if (unread.isEmpty()) {
                println(">> [GmailPollingService] Nenhum e-mail não lido.")
                return
            }

            println(">> [GmailPollingService] Encontrados ${unread.size} e-mails não lidos.")

            for (msg in unread) {
                processMessage(session, msg)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                inbox?.close(true)
                store?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Processa um único e-mail: lê, manda pra IA e responde.
     */
    private fun processMessage(session: Session, msg: Message) {
        val from = msg.from?.firstOrNull()?.toString() ?: "desconhecido"
        val subject = msg.subject ?: "(sem assunto)"
        val body = getText(msg)

        println(">> [GmailPollingService] E-mail recebido de $from | Assunto: $subject")
        println("----- BODY -----")
        println(body)
        println("----------------")

        // 1) Chama a IA (Ana Terra)
        val resposta = chatAnaTerraService.processarMensagem(Canal.EMAIL, subject, body)

        // 2) Envia resposta automática
        enviarResposta(session, to = from, originalSubject = subject, resposta = resposta)

        // 3) Marca como lido
        msg.setFlag(Flags.Flag.SEEN, true)
    }

    /**
     * Envia um e-mail simples de resposta usando o mesmo Session.
     */
    private fun enviarResposta(session: Session, to: String, originalSubject: String, resposta: String) {
        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username, "Ana Terra"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false))
                subject = if (originalSubject.lowercase().startsWith("re:")) {
                    originalSubject
                } else {
                    "Re: $originalSubject"
                }
                setText(resposta, Charsets.UTF_8.name())
            }

            Transport.send(message)
            println(">> [GmailPollingService] Resposta enviada para $to")

        } catch (e: Exception) {
            println(">> [GmailPollingService] Erro ao enviar resposta: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Extrai o texto do e-mail (inclusive multipart).
     */
    private fun getText(p: Part): String {
        if (p.isMimeType("text/*")) {
            return p.content.toString()
        }

        if (p.isMimeType("multipart/*")) {
            val mp = p.content as MimeMultipart
            val sb = StringBuilder()
            for (i in 0 until mp.count) {
                val bodyPart = mp.getBodyPart(i)
                sb.append(getText(bodyPart))
            }
            return sb.toString()
        }

        return ""
    }
}
