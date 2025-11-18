package br.com.ibra.service;

import br.com.ibra.dto.ChatSession
import br.com.ibra.model.DocumentoAnalise
import br.com.ibra.repository.ChatCarrinhoRepository
import br.com.ibra.repository.ChatSessionRepository
import br.com.ibra.service.Canal
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ChatSessionService(
    private val sessionRepo: ChatSessionRepository,
    private val carrinhoRepo: ChatCarrinhoRepository
) {

    fun ensureSession(sessionKey: String, canal: Canal, cliente: String? = null): ChatSession {
        return sessionRepo.upsert(sessionKey, canal, cliente)
    }

    fun getInfo(sessionKey: String): ChatSession? =
        sessionRepo.findById(sessionKey)

    fun setCpf(sessionKey: String, cpf: String) {
        sessionRepo.updateCpf(sessionKey, cpf)
    }

    fun addAnalise(sessionKey: String, analise: DocumentoAnalise, quantidade: Int) {
        carrinhoRepo.addItem(
            sessionId = sessionKey,
            analiseId = analise.id,
            nome = analise.nome,
            precoUnitario = BigDecimal.valueOf(analise.preco),
            quantidade = quantidade
        )
    }

    data class ResumoCarrinho(
        val linhas: List<String>,
        val total: BigDecimal
    )

    fun resumo(sessionKey: String): ResumoCarrinho {
        val itens = carrinhoRepo.listBySession(sessionKey)
        val linhas = itens.map {
            val subtotal = it.precoUnitario.multiply(BigDecimal.valueOf(it.quantidade.toLong()))
            "- ${it.quantidade}× ${it.nome} → R$ %.2f".format(subtotal)
        }

        val total = itens.fold(BigDecimal.ZERO) { acc, it ->
            acc + it.precoUnitario.multiply(BigDecimal.valueOf(it.quantidade.toLong()))
        }

        return ResumoCarrinho(linhas, total)
    }

    fun limpar(sessionKey: String) {
        carrinhoRepo.clearSession(sessionKey)
    }
}
