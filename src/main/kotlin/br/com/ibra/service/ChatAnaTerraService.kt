package br.com.ibra.service

import br.com.ibra.client.OpenAIApiClient
import br.com.ibra.enum.ClassificacaoAcao
import br.com.ibra.enum.TipoAcaoOrcamento
import br.com.ibra.model.DocumentoAnalise
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.Locale
import java.util.regex.Pattern

interface ChatAnaTerraService {
    fun processarMensagem(canal: Canal, rawSessionId: String, texto: String): String
}

@Service
class ChatAnaTerraServiceImpl(
    private val vectorService: VectorService,
    private val sessionService: ChatSessionService,
    private val openAIApiClient: OpenAIApiClient,
    private val chatLogService: ChatLogService,
    private val atendimentoHumanoService: AtendimentoHumanoService
) : ChatAnaTerraService {

    private val mapper = jacksonObjectMapper()

    private data class ClassificacaoPergunta(
        val intencao: String,
        val categoria: String?
    )

    override fun processarMensagem(canal: Canal, rawSessionId: String, texto: String): String {
        val t = texto.trim()
        if (t.isBlank()) return "Não consegui entender a mensagem 😅. Pode repetir com outras palavras?"

        // monta / garante sessão
        val sessionKey = buildSessionKey(canal, rawSessionId)
        sessionService.ensureSession(
            sessionKey,
            canal,
            cliente = when (canal) {
                Canal.WHATSAPP -> rawSessionId
                Canal.EMAIL -> rawSessionId
                Canal.WEB -> null
            }
        )

        val lower = t.lowercase(Locale.getDefault())
        val sessionInfo = sessionService.getInfo(sessionKey)
        val cpfAtual = sessionInfo?.cpf

        // loga mensagem do cliente
        chatLogService.registrar(sessionKey, cpfAtual, canal, "USER", t)

        // ================================
        // 1) SAUDAÇÃO PRIMEIRO
        // ================================
        if (isSaudacao(lower)) {
            val draft = when (canal) {
                Canal.EMAIL -> """
                Olá! 😊  
                Sou a Ana Terra, assistente do laboratório de análises agrícolas.
                Me diga quais análises você deseja orçar ou o tipo de amostra (solo, folha, água, semente).
            """.trimIndent()

                else -> """
                Oi! Tudo certo por aí? 😊🌾  
                Sou a Ana Terra, posso te ajudar com orçamento de análises de solo, folha, semente ou água.
                Como posso te ajudar hoje?
            """.trimIndent()
            }

            val prompt = """
            Você é Ana Terra. Reescreva a mensagem abaixo de forma acolhedora e natural.
            $draft
        """.trimIndent()

            var resposta = openAIApiClient.gerarRespostaNatural(prompt, draft)

            // Após a saudação, verificar CPF
            if (cpfAtual.isNullOrBlank()) {
                resposta += "\n\n" +
                        "Antes da gente continuar, pode me informar **seu CPF** (só os números)? " +
                        "Assim consigo identificar seu cadastro por aqui. 🙂"
            }

            chatLogService.registrar(sessionKey, cpfAtual, canal, "BOT", resposta)
            return resposta
        }

        // ================================
        // 2) SOLICITAR CPF CASO AINDA NÃO TENHA
        // ================================
        if (cpfAtual.isNullOrBlank()) {
            val cpfEncontrado = extrairCpf(t)
            val resposta = if (cpfEncontrado == null) {
                "Antes de continuar, preciso do seu CPF para identificar seu cadastro. " +
                        "Pode me enviar apenas os números, por favor? 🙂"
            } else {
                sessionService.setCpf(sessionKey, cpfEncontrado)
                "Perfeito, já registrei seu CPF aqui! 😊\nComo posso te ajudar agora?"
            }

            chatLogService.registrar(sessionKey, cpfEncontrado ?: cpfAtual, canal, "BOT", resposta)
            return resposta
        }

        // ======================
        // 3) FLUXO NORMAL (já tem CPF)
        // ======================
        val classificacao = classificarPergunta(t)
        val intencao = classificacao.intencao
        val categoria = classificacao.categoria

        val resposta: String = when (intencao) {
            "listar_analises" ->
                responderListaAnalises(sessionKey, canal, categoria)

            "duvida_analise" ->
                responderDuvidaAnalise(sessionKey, canal, t, categoria)

            // dúvida geral → papo geral com modelo
            "duvida_geral" ->
                mensagemEncaminharHumano(sessionKey, canal, t, motivo = "classificador_intencao_humano")
                //responderChatGeral(t)

            // fora de escopo / sensível → humano
            "humano" ->
                mensagemEncaminharHumano(sessionKey, canal, t, motivo = "classificador_intencao_humano")

            // "outro" → trata como papo geral (pode ser trocado para humano se quiser)
            "outro" ->
                mensagemEncaminharHumano(sessionKey, canal, t, motivo = "classificador_intencao_humano")
                //responderChatGeral(t)

            // "orcamento_analise" ou qualquer outra coisa cai no fluxo de orçamento/carrinho
            else ->
                processarFluxoOrcamento(sessionKey, lower, t)
        }

        val cpfFinal = sessionService.getInfo(sessionKey)?.cpf
        chatLogService.registrar(sessionKey, cpfFinal, canal, "BOT", resposta)
        return resposta
    }

    // (mantido, se você quiser usar em outro lugar no futuro)
    private fun processarMensagemComCpf(canal: Canal, sessionKey: String, texto: String): String {
        val t = texto.trim()
        val lower = t.lowercase(Locale.getDefault())

        // Saudações
        if (isSaudacao(lower)) {
            val draft = when (canal) {
                Canal.EMAIL -> """
                    Olá! 😊
                    Sou a Ana Terra, assistente do laboratório de análises de solo.
                    Me diga quais análises você deseja orçar ou o tipo de amostra (solo, folha, água, semente).
                """.trimIndent()
                else -> """
                    Oi! 😊
                    Sou a Ana Terra. Posso te ajudar a montar o orçamento das análises de solo, folha, água ou semente.
                    Pode me dizer o nome da análise ou o que você precisa avaliar.
                """.trimIndent()
            }
            val prompt = """
                Você é **Ana Terra**, assistente virtual de um laboratório de análises agrícolas.
                Reescreva a mensagem abaixo de forma natural, curta e acolhedora, mantendo o mesmo sentido.

                Mensagem base:
                $draft
            """.trimIndent()
            return openAIApiClient.gerarRespostaNatural(prompt, draft)
        }

        // Fechar orçamento
        if (lower.contains("fechar") || lower.contains("finalizar") || lower.contains("concluir")) {
            val resumo = sessionService.resumo(sessionKey)
            if (resumo.total == BigDecimal.ZERO) {
                return "Seu orçamento ainda está vazio 🌱. Me diga quais análises você quer incluir."
            }
            sessionService.limpar(sessionKey)
            val corpo = resumo.linhas.joinToString("\n")
            val draft = """
                Fechando o orçamento:

                $corpo
                Total: R$ %.2f

                Se quiser, posso te orientar sobre coleta das amostras ou prazos de análise.
            """.trimIndent().format(resumo.total)

            val prompt = """
                Você é **Ana Terra**, técnica de laboratório agrícola.
                Gere uma mensagem simpática de encerramento de orçamento, usando o resumo de itens abaixo.

                Resumo do orçamento:
                $corpo
                Total: R$ ${"%.2f".format(resumo.total)}

                Mensagem do cliente:
                "$texto"
            """.trimIndent()

            return openAIApiClient.gerarRespostaNatural(prompt, draft)
        }

        // Ver total parcial
        if (lower.contains("total") || lower.contains("parcial") || lower.contains("quanto está")) {
            val resumo = sessionService.resumo(sessionKey)
            if (resumo.total == BigDecimal.ZERO) {
                return "Ainda não adicionei nenhuma análise ao seu orçamento 🌱. Me diga pelo menos uma para começarmos."
            }
            val corpo = resumo.linhas.joinToString("\n")
            val draft = """
                Até agora seu orçamento está assim:

                $corpo
                Total parcial: R$ %.2f

                Você pode incluir mais análises ou pedir para fechar o orçamento.
            """.trimIndent().format(resumo.total)

            val prompt = """
                Você é **Ana Terra**, assistente do laboratório.
                Explique o total parcial do orçamento de forma clara e acolhedora, usando o resumo abaixo.

                Resumo:
                $corpo
                Total parcial: R$ ${"%.2f".format(resumo.total)}

                Mensagem do cliente:
                "$texto"
            """.trimIndent()

            return openAIApiClient.gerarRespostaNatural(prompt, draft)
        }

        // Quantidade de amostras
        val qtd = Regex("""(\d+)""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

        // Regra: se quantidade > 1000 → encaminha humano
        if (qtd > 1000) {
            return mensagemEncaminharHumano(sessionKey, canal, texto, motivo = "quantidade_amostras_maior_1000 ($qtd)")
        }

        // Buscar análise mais provável
        val analise = vectorService.buscarMelhorAnalisePara(t)
            ?: return mensagemEncaminharHumano(sessionKey, canal, texto, motivo = "analise_nao_encontrada")

        sessionService.addAnalise(sessionKey, analise, qtd)
        val resumo = sessionService.resumo(sessionKey)
        val corpo = resumo.linhas.joinToString("\n")

        val draft = """
            Adicionei $qtd× "${analise.nome}" ao seu orçamento.

            Total parcial: R$ %.2f

            Você pode pedir outra análise, perguntar o total ou pedir para fechar o orçamento.
        """.trimIndent().format(resumo.total)

        val prompt = """
            Você é **Ana Terra**, assistente virtual de laboratório agrícola.
            Gere uma resposta curta e simpática para o cliente, explicando que a análise abaixo foi adicionada ao carrinho
            e mostrando o total parcial do orçamento.

            Análise adicionada:
            - Nome: ${analise.nome}
            - Preço unitário: R$ ${"%.2f".format(analise.preco)}
            - Quantidade: $qtd

            Resumo atual do carrinho:
            $corpo
            Total parcial: R$ ${"%.2f".format(resumo.total)}

            Mensagem original do cliente:
            "$texto"
        """.trimIndent()

        return openAIApiClient.gerarRespostaNatural(prompt, draft)
    }

    // ----------------------------------------------------
    // CLASSIFICAÇÃO
    // ----------------------------------------------------

    private fun classificarPergunta(textoOriginal: String): ClassificacaoPergunta {
        val user = """
        Você é um classificador de intenção para o chatbot Ana Terra, assistente de laboratório agrícola.
        Sua tarefa é receber a mensagem de um cliente e responder SOMENTE um JSON válido, sem nenhum texto antes ou depois,
        sem markdown, sem crases, sem comentários, exatamente neste formato:

        {
          "intencao": "orcamento_analise" | "listar_analises" | "duvida_analise" | "duvida_geral" | "humano" | "outro",
          "categoria": "solo" | "vegetal" | "ambiental" | "semente" | "desconhecida"
        }

        Regras:
        - "orcamento_analise": quando o cliente pede preço, orçamento, tabela de valores ou quer FECHAR uma análise específica.
        - "listar_analises": quando o cliente pede lista/catálogo/tabela de análises (ex.: "me manda as análises de solo que vocês fazem").
        - "duvida_analise": quando pergunta o que significa um exame, pra que serve, quando fazer, mas não pede preço.
        - "duvida_geral": perguntas gerais (clima, agricultura, manejo, curiosidades) que não envolvam diretamente o catálogo de análises.
        - "humano": reclamações, problemas com laudo, questões financeiras complexas, dúvidas muito fora do escopo do laboratório
                    ou qualquer assunto sensível que exija atendimento humano.
        - "outro": se não se encaixar em nada disso.

        Categoria:
        - "solo": tudo que envolva análise de solo, textura, física, química, carbono, macro/micronutrientes no solo.
        - "vegetal": folhas, tecido vegetal, planta.
        - "ambiental": água, efluentes, resíduos, análises ambientais.
        - "semente": análises de semente, vigor, germinação, pureza.
        - "desconhecida": se não der para inferir.

        Mensagem do cliente:
        "$textoOriginal"
    """.trimIndent()

        val fallbackJson = """{"intencao":"outro","categoria":"desconhecida"}"""

        val respostaBruta = openAIApiClient.completarBruto(
            system = """
            Você é um classificador de intenção.
            Responda SEMPRE apenas um JSON válido, sem markdown, sem crases, sem explicações.
        """.trimIndent(),
            user = user,
            fallback = fallbackJson
        )

        println(">> classificarPergunta - respostaBruta = $respostaBruta")

        val jsonApenas = try {
            val start = respostaBruta.indexOf('{')
            val end = respostaBruta.lastIndexOf('}')
            if (start != -1 && end != -1 && end > start) {
                respostaBruta.substring(start, end + 1)
            } else {
                respostaBruta
            }
        } catch (e: Exception) {
            fallbackJson
        }

        return try {
            val node = mapper.readTree(jsonApenas)
            val intencao = node.get("intencao")?.asText() ?: "outro"
            val categoria = node.get("categoria")?.asText()

            ClassificacaoPergunta(
                intencao = intencao,
                categoria = categoria?.takeIf { it != "desconhecida" }
            )
        } catch (e: Exception) {
            println(">> classificarPergunta - erro parseando JSON: ${e.message}")
            println(">> classificarPergunta - jsonApenas = $jsonApenas")
            ClassificacaoPergunta("outro", null)
        }
    }

    // ----------------------------------------------------
    // FLUXO ORÇAMENTO / CARRINHO
    // ----------------------------------------------------

    private fun processarFluxoOrcamento(
        sessionKey: String,
        lower: String,
        textoOriginal: String
    ): String {

        // ==========================
        // 0) Limpar orçamento
        // ==========================
        if (lower.contains("limpar") || lower.contains("zerar") || lower.contains("cancelar orçamento")) {
            sessionService.limpar(sessionKey)
            return "Zerei o seu orçamento por aqui 👍. Se quiser, me manda de novo o que precisa analisar que a gente remonta."
        }

        // ==========================
        // 1) Resumo do carrinho
        // ==========================
        if (lower.contains("resumo") || lower.contains("carrinho") || lower.contains("ver orçamento")) {
            val resumo = sessionService.resumo(sessionKey)
            if (resumo.total == BigDecimal.ZERO) {
                return "Por enquanto o seu orçamento está vazio 🌱. Me diz o que você quer analisar que eu te ajudo a montar."
            }

            val corpo = resumo.linhas.joinToString("\n")

            val prompt = """
            Você é Ana Terra.
            Explique o orçamento abaixo de forma curta, simpática e acolhedora.
        """.trimIndent()

            val draft = """
            Até agora seu orçamento está assim:
            
            $corpo
            Total parcial: R$ ${"%.2f".format(resumo.total)}
        """.trimIndent()

            return openAIApiClient.gerarRespostaNatural(prompt, draft)
        }

        // ==========================
        // 2) Fechar orçamento
        // ==========================
        if (lower.contains("fechar") || lower.contains("finalizar") || lower.contains("concluir")) {
            val resumo = sessionService.resumo(sessionKey)
            if (resumo.total == BigDecimal.ZERO) {
                return "Seu orçamento ainda está vazio 🌱. Me conta quais análises você precisa pra eu montar tudo direitinho."
            }

            val corpo = resumo.linhas.joinToString("\n")

            val prompt = """
            Você é Ana Terra.
            Gere uma resposta curta explicando que o orçamento foi finalizado.
        """.trimIndent()

            val draft = """
            Fechando seu orçamento:
            
            $corpo
            Total final: R$ ${"%.2f".format(resumo.total)}

            Agora só preciso dos seus dados (nome, CPF/CNPJ, cidade/UF)
            para finalizar o cadastro e combinar o envio das amostras. 💚
        """.trimIndent()

            return openAIApiClient.gerarRespostaNatural(prompt, draft)
        }

        // ==========================
        // 3) Identificação da análise (semântica)
        // ==========================
        val analise = vectorService.buscarMelhorAnalisePara(textoOriginal, maxDist = 0.98)
            ?: return mensagemEncaminharHumano(
                sessionKey,
                Canal.WEB, // se quiser, pode trocar para o canal da sessão via outro parâmetro
                textoOriginal,
                motivo = "analise_nao_encontrada_fluxo_orcamento"
            )

        val quantidade = extrairQuantidadeAmostras(lower)

        // 🚨 Quantidade muito alta → manda para humano (comercial / logística)
        if (quantidade > 1000) {
            return mensagemEncaminharHumano(
                sessionKey,
                Canal.WEB,
                textoOriginal,
                motivo = "quantidade_amostras_maior_1000 ($quantidade)"
            )
        }

        // 4) CLASSIFICA AÇÃO DE ORÇAMENTO (preço / adicionar / remover / finalizar / outro)
        val acao = classificarAcaoDeOrcamento(textoOriginal, analise.nome).acao

        return when (acao) {

            // --------- SÓ QUER O PREÇO ---------
            TipoAcaoOrcamento.SO_PRECO -> {
                val total = analise.preco * quantidade.toDouble()

                val draft = """
                    A análise ${analise.nome} custa R$ ${"%.2f".format(analise.preco)} por amostra.
            
                    Para $quantidade amostra(s), o valor seria R$ ${"%.2f".format(total)}.
            
                    Se quiser, posso incluir no seu orçamento — é só me pedir. 🙂
                """.trimIndent()

                draft
            }

            // --------- QUER INCLUIR ---------
            TipoAcaoOrcamento.ADICIONAR -> {
                sessionService.addAnalise(sessionKey, analise, quantidade)
                val resumo = sessionService.resumo(sessionKey)
                val corpo = resumo.linhas.joinToString("\n")

                val draft = """
            Prontinho, incluí esta análise no seu orçamento:

            • ${analise.nome}
              Quantidade: $quantidade
              Preço unitário: R$ ${"%.2f".format(analise.preco)}

            Resumo atual:
            $corpo
            Total parcial: R$ ${"%.2f".format(resumo.total)}

            Se quiser, podemos adicionar mais análises ou já partir para o fechamento. 💚
            """.trimIndent()

                draft
            }

            // --------- QUER REMOVER ---------
            TipoAcaoOrcamento.REMOVER -> {
                """
            Claro! Me diga qual análise você quer remover do orçamento 😊
            """.trimIndent()
            }

            // --------- QUER FINALIZAR ---------
            TipoAcaoOrcamento.FINALIZAR -> {
                val resumo = sessionService.resumo(sessionKey)
                val corpo = resumo.linhas.joinToString("\n")

                """
            Perfeito! Vou finalizar seu orçamento agora mesmo 💚

            Aqui está o resumo:
            $corpo
            Total: R$ ${"%.2f".format(resumo.total)}

            Me envie, por favor:
            - Nome ou razão social
            - CPF/CNPJ
            - Cidade/UF

            Assim eu concluo tudo certinho pra você. 🙂
            """.trimIndent()
            }

            // --------- OUTROS CASOS ---------
            TipoAcaoOrcamento.OUTRO -> {
                """
            Posso te ajudar com valores, incluir análises ou explicar qualquer exame 🌱  
            Como posso te ajudar agora?
            """.trimIndent()
            }
        }
    }

    private fun classificarAcaoDeOrcamento(
        textoOriginal: String,
        nomeAnaliseProvavel: String
    ): ClassificacaoAcao {

        val user = """
        Você é um classificador de AÇÃO sobre orçamento de análises de laboratório agrícola.
        Retorne APENAS um JSON válido, sem explicações, sem markdown.

        Formato:
        {
          "acao": "SO_PRECO" | "ADICIONAR" | "REMOVER" | "FINALIZAR" | "OUTRO"
        }

        Definições:
        - SO_PRECO → cliente só quer saber o valor, não pediu para incluir.
        - ADICIONAR → cliente pediu para incluir/colocar essa análise no orçamento.
        - REMOVER → cliente pediu para remover algo do orçamento.
        - FINALIZAR → cliente quer concluir/fechar o orçamento.
        - OUTRO → qualquer outra intenção.

        A análise identificada é: "$nomeAnaliseProvavel"

        Mensagem do cliente:
        "$textoOriginal"
    """.trimIndent()

        val fallback = """{"acao":"OUTRO"}"""

        val resp = openAIApiClient.completarBruto(
            system = "Você é um classificador de AÇÃO. Retorne só JSON válido.",
            user = user,
            fallback = fallback
        )

        val json = try {
            val start = resp.indexOf('{')
            val end = resp.lastIndexOf('}')
            resp.substring(start, end + 1)
        } catch (e: Exception) {
            fallback
        }

        return try {
            val node = mapper.readTree(json)
            val str = node.get("acao")?.asText() ?: "OUTRO"
            val acao = TipoAcaoOrcamento.valueOf(str)
            ClassificacaoAcao(acao)
        } catch (_: Exception) {
            ClassificacaoAcao(TipoAcaoOrcamento.OUTRO)
        }
    }

    // Heurística simples para extrair quantidade de amostras da frase
    private fun extrairQuantidadeAmostras(lower: String): Int {
        val pattern = Pattern.compile("(\\d+)")
        val m = pattern.matcher(lower)
        if (m.find()) {
            val q = m.group(1)?.toIntOrNull()
            if (q != null && q > 0) {
                return q
            }
        }
        return 1
    }

    // ----------------------------------------------------
    // LISTAR ANÁLISES (usando busca semântica)
    // ----------------------------------------------------
    private fun responderListaAnalises(sessionKey: String, canal: Canal, categoria: String?): String {

        val termo = when (categoria) {
            "solo" -> "análises de solo"
            "vegetal" -> "análises foliares"
            "ambiental" -> "análises de água"
            "semente" -> "análises de semente"
            else -> "análises laboratoriais"
        }

        val analises = vectorService.listarAnalisesSemantico(termo, limite = 40)

        if (analises.isEmpty()) {
            return mensagemEncaminharHumano(
                sessionKey,
                canal,
                "Cliente pediu uma lista de análises (categoria=$categoria), mas não encontrei resultados na base.",
                motivo = "lista_analises_vazia"
            )
        }

        val bloco = analises.take(20).joinToString("\n") { a ->
            "• ${a.nome} (${a.laboratorio}) – R$ ${"%.2f".format(a.preco)} por ${a.unidade}"
        }

        return """
        Claro! Aqui estão algumas análises relacionadas a **$termo**:

        $bloco
        
        Se quiser, posso detalhar alguma delas ou já montar o orçamento certinho pra você 🌱
    """.trimIndent()
    }

    // ----------------------------------------------------
    // DÚVIDA SOBRE ANÁLISE
    // ----------------------------------------------------
    private fun responderDuvidaAnalise(
        sessionKey: String,
        canal: Canal,
        textoOriginal: String,
        categoria: String?
    ): String {
        val analises = vectorService.buscarAnalisesOrdenadas(textoOriginal, limite = 3)

        // 1) Nada encontrado → encaminha humano
        if (analises.isEmpty()) {
            return mensagemEncaminharHumano(
                sessionKey,
                canal,
                "Dúvida sobre análise que não consegui ligar a nenhum item cadastrado:\n\"$textoOriginal\"",
                motivo = "duvida_analise_sem_base"
            )
        }

        // 2) Se a melhor análise estiver com distância muito alta, consideramos que não há base suficiente
        val melhor = analises.first()
        val dist = melhor.distancia

        if (dist != null && dist > 0.8) {
            return mensagemEncaminharHumano(
                sessionKey,
                canal,
                "Dúvida sobre análise com baixa similaridade na base (distância=$dist):\n\"$textoOriginal\"",
                motivo = "duvida_analise_baixa_similaridade"
            )
        }

        val contexto = analises.joinToString("\n\n") { a: DocumentoAnalise ->
            """
        [${a.nome} - ${a.laboratorio}]
        Descrição: ${a.descricao}
        """.trimIndent()
        }

        val prompt = """
        Você é **Ana Terra**, técnica de laboratório agrícola.
        O cliente fez uma pergunta sobre análises laboratoriais.
        Use APENAS as informações abaixo para explicar de forma simples e prática,
        sem inventar análises novas nem resultados:

        CONTEXTO:
        $contexto

        Pergunta do cliente:
        "$textoOriginal"

        Responda em tom informal, acolhedor e objetivo,
        explicando pra que serve a(s) análise(s), quando é indicada e como ajuda na tomada de decisão.
    """.trimIndent()

        val fallback = """
        As análises abaixo podem ter relação com a sua dúvida:
        
        $contexto
    """.trimIndent()

        return openAIApiClient.gerarRespostaNatural(prompt, fallback)
    }

    // ----------------------------------------------------
    // PAPO GERAL
    // ----------------------------------------------------
    private fun responderChatGeral(textoOriginal: String): String {
        val prompt = """
            O cliente enviou a mensagem abaixo. 
            Não é exatamente um pedido de orçamento e pode envolver dúvidas gerais sobre agricultura, solo, manejo ou análises.
            Responda como **Ana Terra**, de forma simpática, objetiva e útil.

            Mensagem:
            "$textoOriginal"
        """.trimIndent()

        val fallback =
            "Vou tentar te ajudar, mas se eu não conseguir, posso pedir para um atendente humano entrar em contato, combinado? 🙂"

        return openAIApiClient.gerarRespostaNatural(prompt, fallback)
    }

    // ----------------------------------------------------
    // HUMANO (sem abrir chamado – versão simples, ainda disponível)
    // ----------------------------------------------------
    private fun mensagemEncaminharHumano(textoOriginal: String): String {
        return """
            Te entendo, esse tipo de situação é melhor a gente ver com calma. 💬
            Vou encaminhar sua mensagem para um atendente humano aqui do laboratório, tá bem?
            
            Se puder, me confirma:
            - Seu nome completo;
            - Cidade/UF;
            - Melhor telefone ou WhatsApp para contato.
            
            Assim o pessoal já te retorna direitinho. 🙂
        """.trimIndent()
    }

    // ----------------------------------------------------
    // HUMANO (abrindo ticket/pendência)
    // ----------------------------------------------------
    private fun mensagemEncaminharHumano(
        sessionKey: String,
        canal: Canal,
        textoOriginal: String,
        motivo: String? = null
    ): String {
        val info = sessionService.getInfo(sessionKey)
        val cpf = info?.cpf

        atendimentoHumanoService.abrirChamado(
            sessionKey = sessionKey,
            cpf = cpf,
            canal = canal,
            motivo = motivo,
            ultimaMensagemCliente = textoOriginal
        )

        return """
            Te entendo, esse tipo de situação é melhor a gente ver com calma. 💬
            Vou pedir para um atendente aqui do laboratório entrar em contato com você, combinado?

            Se puder, me confirma:
            - Seu nome completo;
            - Cidade/UF;
            - Melhor telefone ou WhatsApp para contato.

            Assim o pessoal já te retorna direitinho. 🙂
        """.trimIndent()
    }

    // ----------------------------------------------------
    // UTILS
    // ----------------------------------------------------
    private fun extrairCpf(texto: String): String? {
        val digits = texto.filter { it.isDigit() }
        return if (digits.length == 11) digits else null
    }

    private fun isSaudacao(lower: String): Boolean {
        val s = listOf("oi", "olá", "ola", "bom dia", "boa tarde", "boa noite", "e aí", "eai")
        return s.any { lower.startsWith(it) }
    }
}
