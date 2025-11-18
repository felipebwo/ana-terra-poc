-- =========================================================
--  SCRIPT DE CRIAÇÃO DO ESQUEMA IA + EXTENSÕES + TABELAS
-- =========================================================

BEGIN;

-- 1) Schema
CREATE SCHEMA IF NOT EXISTS ia;

-- 2) Extensão para o tipo public.vector (pgvector)
--    Instale a extensão pgvector no servidor, se ainda não estiver instalada.
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

-- =========================================================
-- 3) TABELAS
--    Ordem: bases -> referenciadas -> dependentes
-- =========================================================

-- 3.1) Sessões de chat (base para carrinho / orçamentos)
CREATE TABLE IF NOT EXISTS ia.chat_session (
    session_id      text          NOT NULL,
    cpf             VARCHAR(11)   NULL,
    canal           text          NOT NULL,
    cliente         text          NULL,
    contexto        jsonb         NULL,
    criado_em       timestamptz   NOT NULL DEFAULT now(),
    atualizado_em   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT chat_session_pkey PRIMARY KEY (session_id)
);

-- 3.2) Laboratórios
CREATE TABLE IF NOT EXISTS ia.laboratorios (
    id          serial4       NOT NULL,
    nome        varchar(150)  NOT NULL,
    descricao   text          NULL,
    CONSTRAINT laboratorios_pkey      PRIMARY KEY (id),
    CONSTRAINT laboratorios_nome_key  UNIQUE (nome)
);

-- 3.5) Análises
CREATE TABLE IF NOT EXISTS ia.analises (
    id              serial4        NOT NULL,
    nome            varchar(255)   NOT NULL,
    descricao       text           NULL,
    preco           numeric(10, 2) NULL,
    unidade         varchar(100)   NULL DEFAULT 'amostra'::character varying,
    tipo            varchar(50)    NULL DEFAULT 'fixo'::character varying,
    laboratorio_id  int4           NULL,
    contexto        text           NULL,
    embedding       public.vector  NULL,
    CONSTRAINT analises_pkey PRIMARY KEY (id),
    CONSTRAINT analises_laboratorio_id_fkey
        FOREIGN KEY (laboratorio_id)
        REFERENCES ia.laboratorios(id),
    CONSTRAINT analises_matriz_id_fkey
        FOREIGN KEY (matriz_id)
        REFERENCES ia.matrizes(id)
);

-- Índices auxiliares para analises
CREATE INDEX IF NOT EXISTS analises_lab_idx
    ON ia.analises USING btree (laboratorio_id);

CREATE INDEX IF NOT EXISTS analises_matriz_idx
    ON ia.analises USING btree (matriz_id);

-- 3.6) Itens de carrinho por sessão de chat
CREATE TABLE IF NOT EXISTS ia.chat_carrinho_item (
    id              bigserial      NOT NULL,
    session_id      text           NOT NULL,
    analise_id      int8           NOT NULL,
    nome            text           NOT NULL,
    preco_unitario  numeric(12, 2) NOT NULL,
    quantidade      int4           NOT NULL DEFAULT 1,
    criado_em       timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT chat_carrinho_item_pkey PRIMARY KEY (id),
    CONSTRAINT chat_carrinho_item_session_id_fkey
        FOREIGN KEY (session_id)
        REFERENCES ia.chat_session(session_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_carrinho_session
    ON ia.chat_carrinho_item USING btree (session_id);

CREATE TABLE ia.chat_log (
    id           BIGSERIAL PRIMARY KEY,
    session_key  TEXT        NOT NULL,
    cpf          VARCHAR(11),
    canal        TEXT        NOT NULL,
    papel        TEXT        NOT NULL, -- 'USER' ou 'BOT'
    mensagem     TEXT        NOT NULL,
    criado_em    TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chat_log_session
    ON ia.chat_log (session_key, criado_em DESC);


CREATE TABLE IF NOT EXISTS ia.chat_atendimento_pendente (
    id                       bigserial   PRIMARY KEY,
    session_key              text        NOT NULL,
    cpf                      text        NULL,
    canal                    text        NOT NULL,
    motivo                   text        NULL,
    ultima_mensagem_cliente  text        NOT NULL,
    criado_em                timestamptz NOT NULL DEFAULT now(),
    resolvido                boolean     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_chat_atend_pendente_abertos
    ON ia.chat_atendimento_pendente (resolvido, criado_em DESC);