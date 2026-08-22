package com.pdv.pos.data.local

import java.util.UUID

// Mismas claves de modulo y mismo reparto que el seed de la migracion
// backend 0007_create_roles (PLAN.md Parte 13): los dos roles de sistema
// deben quedar identicos en ambos lados para que un dispositivo LOCAL que
// nunca sincronizo tenga el mismo catalogo que el backend. Compartido entre
// LocalRolRepository (bootstrap al abrir Roles) y LocalAuthRepository
// (bootstrap al intentar iniciar sesion) para no duplicar la lista.
const val NOMBRE_ROL_ADMINISTRADOR = "administrador"
const val NOMBRE_ROL_ENCARGADO_TURNO = "encargado_turno"

private val MODULOS_ADMINISTRADOR =
    listOf("venta", "entrada", "inventario", "caja", "devoluciones", "usuarios", "configuracion")
private val MODULOS_ENCARGADO_TURNO = listOf("venta", "entrada", "inventario", "caja", "devoluciones")

fun rolesDeSistema(now: Long): List<RolEntity> = listOf(
    RolEntity(
        localId = UUID.randomUUID().toString(),
        remoteId = null,
        nombre = NOMBRE_ROL_ADMINISTRADOR,
        modulosPermitidos = MODULOS_ADMINISTRADOR,
        esSistema = true,
        updatedAt = now,
        isSynced = false,
        deletedAt = null,
    ),
    RolEntity(
        localId = UUID.randomUUID().toString(),
        remoteId = null,
        nombre = NOMBRE_ROL_ENCARGADO_TURNO,
        modulosPermitidos = MODULOS_ENCARGADO_TURNO,
        esSistema = true,
        updatedAt = now,
        isSynced = false,
        deletedAt = null,
    ),
)
