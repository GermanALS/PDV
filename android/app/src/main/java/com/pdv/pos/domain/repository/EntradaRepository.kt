package com.pdv.pos.domain.repository

import com.pdv.pos.domain.model.Entrada

interface EntradaRepository {
    suspend fun registrarEntrada(entrada: Entrada)
}
