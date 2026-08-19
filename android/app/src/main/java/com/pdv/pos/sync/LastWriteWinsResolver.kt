package com.pdv.pos.sync

// Politica para entidades sin ambiguedad de cantidad (PLAN.md Parte 6,
// "Decisiones abiertas"; docs/api-contract.md sec. 4).
object LastWriteWinsResolver {
    fun <T> resolve(local: Versioned<T>, remote: Versioned<T>): Versioned<T> =
        if (remote.updatedAt >= local.updatedAt) remote else local
}
