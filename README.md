# Ana Terra POC – Multicanal com Sessão em PostgreSQL

Esta POC implementa a assistente **Ana Terra** para laboratório de solo, reutilizando o seu código original
(`ChatIAService`, `VectorService`, etc.) e adicionando:

- Atendimento **multicanal**:
  - WhatsApp (Cloud API /webhook)
  - Chat via browser (`POST /api/chat`)
  - E-mail (simulado via `POST /api/email/chat`)
- Sessão de conversa **persistida no PostgreSQL** (`chat_session`)
- Carrinho de orçamento **persistido no PostgreSQL** (`chat_carrinho_item`)
- Orquestração de fluxo em `ChatAnaTerraService`, que:
  - Entende ações básicas (saudação, ver total, fechar)
  - Usa `VectorService` para encontrar a análise mais provável
  - Atualiza o carrinho
  - Chama `ChatIAService.gerarRespostaNatural(...)` para gerar uma resposta bem escrita no estilo da Ana Terra

## Estrutura principal

- `br.com.ibra.solo.chat.Canal` – enum de canais (WHATSAPP, WEB, EMAIL)
- `ChatSessionRepository` / `ChatCarrinhoRepository` – acesso ao PostgreSQL
- `ChatSessionService` – operações de alto nível com sessão + carrinho
- `ChatAnaTerraService` – fluxo de conversa da Ana Terra + integração com LLM
- `ChatIAService` – seu serviço original, com método extra `gerarRespostaNatural(...)`
- `WhatsappWebhookController` – integra com WhatsApp Cloud API
- `WebChatController` – endpoint genérico de chat web
- `EmailChatService` + `EmailChatController` – simulação de atendimento por e-mail

## Tabelas do chat

Arquivo: `src/main/resources/chat-schema.sql`

```sql
CREATE TABLE IF NOT EXISTS chat_session (
    session_id      TEXT PRIMARY KEY,
    canal           TEXT NOT NULL,
    cliente         TEXT NULL,
    contexto        JSONB NULL,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chat_carrinho_item (
    id              BIGSERIAL PRIMARY KEY,
    session_id      TEXT NOT NULL REFERENCES chat_session(session_id) ON DELETE CASCADE,
    analise_id      BIGINT NOT NULL,
    nome            TEXT NOT NULL,
    preco_unitario  NUMERIC(12,2) NOT NULL,
    quantidade      INT NOT NULL DEFAULT 1,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Rode esse script no seu banco `analises_solo` antes de iniciar a aplicação.

## Configuração

Em `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5435/analises_solo
spring.datasource.username=postgres
spring.datasource.password=postgres

openai.api.key=COLOQUE_SUA_CHAVE_AQUI
openai.api.uri=https://api.openai.com/v1/

whatsapp.verify-token=SEU_TOKEN_DE_VERIFICACAO
whatsapp.access-token=SEU_TOKEN_DE_ACESSO_META
```

## Endpoints

### 1. Chat Web (POC)

`POST /api/chat`

```json
{
  "sessionId": "opcional",
  "message": "Quero química macro e micro e rotina básica"
}
```

Resposta:

```json
{
  "sessionId": "uuid-gerado-ou-o-mesmo-enviado",
  "reply": "Texto da Ana Terra..."
}
```

### 2. WhatsApp Cloud API

- Verificação:
  - `GET /whatsapp/webhook?hub.verify_token=...&hub.challenge=...`
- Recebimento de mensagem:
  - `POST /whatsapp/webhook`

O controlador extrai `from` (telefone) e `text` (mensagem), chama `ChatAnaTerraService.processarMensagem`
e envia a resposta de volta usando a API da Meta.

### 3. E-mail (simulado)

`POST /api/email/chat`

```json
{
  "from": "cliente@fazenda.com.br",
  "body": "Quero orçamento de análise de Rotina - Básica + S"
}
```

Resposta:

```json
{
  "reply": "Texto da Ana Terra..."
}
```

Em produção, este serviço seria conectado a um listener real de e-mails (IMAP/POP/Webhook).

## Como rodar

```bash
mvn clean package
mvn spring-boot:run
```

Lembre de:

1. Criar as tabelas `chat_session` e `chat_carrinho_item` (arquivo `chat-schema.sql`).
2. Configurar a chave OpenAI e tokens do WhatsApp.
3. Usar `ngrok http 8087` (ajuste a porta se necessário) para expor o webhook do WhatsApp para testes.

## Próximos passos possíveis

- Persistir orçamentos fechados em uma tabela específica.
- Gerar PDF de orçamento ao fechar.
- Usar mais sinais do `ChatIAService` (classificação de intenção, extração de múltiplos itens).
- Personalizar saudação e tom por laboratório / matriz no banco.
