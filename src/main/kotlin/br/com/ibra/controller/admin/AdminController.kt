package br.com.ibra.controller.admin

import br.com.ibra.dto.AnaliseSoloRequest
import br.com.ibra.dto.AtendimentoPendenteDto
import br.com.ibra.model.DocumentoAnalise
import br.com.ibra.service.AtendimentoHumanoService
import br.com.ibra.service.admin.AdminIAService
import br.com.ibra.service.admin.GerarContextoAnalisesService
import br.com.ibra.service.admin.RecalcularEmbeddingsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ia/admin")
class AdminController(
    private val adminIAService: AdminIAService,
    private val atendimentoHumanoService: AtendimentoHumanoService,
    private val gerarContextoAnalisesService: GerarContextoAnalisesService,
    private val recalcularEmbeddingsService: RecalcularEmbeddingsService
) {

    @GetMapping("/atendimentos/pendentes")
    fun listarPendentes(): List<AtendimentoPendenteDto> {
        val pendentes = atendimentoHumanoService.listarPendentes()
        return pendentes.map { p ->
            AtendimentoPendenteDto(
                id = p.id,
                sessionKey = p.sessionKey,
                cpf = p.cpf,
                canal = p.canal.name,
                motivo = p.motivo,
                ultimaMensagemCliente = p.ultimaMensagemCliente,
                criadoEm = p.criadoEm.toString() // ISO 8601, fácil de usar no front
            )
        }
    }

    @PostMapping("/{id}/resolver")
    fun marcarResolvido(@PathVariable id: Long): ResponseEntity<Void> {
        atendimentoHumanoService.marcarResolvido(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/gerar-contextos")
    fun gerarContextos(): String {
        gerarContextoAnalisesService.gerarContextos()
        return "Contextos gerados com sucesso ✅"
    }

    @PostMapping("/recalcular-embeddings")
    fun recalcularEmbeddings(): String {
        recalcularEmbeddingsService.atualizarEmbeddings()
        return "Embeddings recalculados com sucesso ✅"
    }

    @PostMapping("/analise")
    fun criarAnalise(@RequestBody request: AnaliseSoloRequest): ResponseEntity<String> {
        return try {
            adminIAService.salvarAnalise(request)
            ResponseEntity.ok("Análise salva com sucesso.")
        } catch (ex: Exception) {
            ResponseEntity.status(500).body("Erro ao inserir análise: ${ex.message}")
        }
    }

    @PostMapping("/analise/lote")
    fun criarAnalisesEmLote(@RequestBody requests: List<AnaliseSoloRequest>): ResponseEntity<Map<String, Any>> {
        val resultados = mutableListOf<String>()
        var sucesso = 0
        var falha = 0

        requests.forEach { analise ->
            try {
                adminIAService.salvarAnalise(analise)
                resultados.add("✅ ${analise.nome}")
                sucesso++
            } catch (ex: Exception) {
                resultados.add("❌ ${analise.nome}: ${ex.message}")
                falha++
            }
        }

        val resumo = mapOf(
            "total" to requests.size,
            "sucesso" to sucesso,
            "falha" to falha,
            "detalhes" to resultados
        )

        return ResponseEntity.ok(resumo)
    }

    @GetMapping("/analise")
    fun listarAnalises(): ResponseEntity<List<DocumentoAnalise>> {
        val lista = adminIAService.listarAnalises()
        return ResponseEntity.ok(lista)
    }

    @DeleteMapping("/analise/{id}")
    fun deletarAnalise(@PathVariable id: Long): ResponseEntity<String> {
        return try {
            adminIAService.deletarAnalise(id)
            ResponseEntity.ok("Análise deletada com sucesso.")
        } catch (ex: Exception) {
            ResponseEntity.badRequest().body("Erro ao deletar análise: ${ex.message}")
        }
    }


}