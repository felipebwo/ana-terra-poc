# Usa imagem oficial do PostgreSQL com suporte a extensões
FROM postgres:16

# Instala pgvector
RUN apt-get update && apt-get install -y postgresql-16-pgvector && rm -rf /var/lib/apt/lists/*

# Copia script de inicialização
COPY init.sql /docker-entrypoint-initdb.d/

# Variáveis de ambiente padrão
ENV POSTGRES_DB=ana_terra_ia
ENV POSTGRES_USER=postgres
ENV POSTGRES_PASSWORD=postgres

EXPOSE 5432
