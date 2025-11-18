package br.com.ibra.dto

data class AnaliseSoloRequest(
    val nome: String,
    val descricao: String,
    val preco: Double,
    val unidade: String = "amostra",
    val tipo: String = "fixo",
    val matriz: String,
    val laboratorio: String
)