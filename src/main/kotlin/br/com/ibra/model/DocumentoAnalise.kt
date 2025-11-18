package br.com.ibra.model

data class DocumentoAnalise(
    val id: Long?,
    val nome: String,
    val descricao: String,
    val preco: Double,
    val unidade: String,
    val tipo: String,
    val laboratorio: String,
    val distancia: Double? = null
)
