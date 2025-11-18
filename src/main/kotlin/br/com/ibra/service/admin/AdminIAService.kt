package br.com.ibra.service.admin

import br.com.ibra.dto.AnaliseSoloRequest
import br.com.ibra.model.DocumentoAnalise
import br.com.ibra.repository.AdminIARepository
import br.com.ibra.client.EmbeddingClient
import org.springframework.stereotype.Service

@Service
class AdminIAService(
    private val adminIARepository: AdminIARepository,
    private val embeddingService: EmbeddingClient,
) {

    fun salvarAnalise(analise: AnaliseSoloRequest) {
        val txtEmbedding = "${analise.matriz} ${analise.laboratorio} ${analise.nome} ${analise.descricao}"
        val emb = embeddingService.gerarEmbedding(txtEmbedding)

        adminIARepository.inserirAnalise(analise, embedding = emb)
    }

    fun listarAnalises(): List<DocumentoAnalise> {
        return adminIARepository.listarAnalises()
    }

    fun deletarAnalise(id: Long) {
        adminIARepository.deletarAnalise(id)
    }

}