package com.pdv.pos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CajaDao {
    @Insert
    suspend fun insertCorte(entity: CorteCajaEntity)

    // Historial reactivo (PLAN.md Parte 18, sub-parte F): Room invalida este
    // Flow ante cualquier insertCorte, incluido el de EjecutorAccionesIa (via
    // el mismo CajaRepository), sin necesitar wiring aparte entre las dos
    // clases. Orden mas reciente primero, igual que el resto de historiales
    // de la app.
    @Query("SELECT * FROM cortes_caja WHERE sucursalId = :sucursalId AND deletedAt IS NULL ORDER BY fechaFin DESC")
    fun observarCortes(sucursalId: String): Flow<List<CorteCajaEntity>>

    // Rango por fechaFin para la exportacion por periodo (PLAN.md Parte 18,
    // sub-parte I), mismo patron que RetiroDao.getRetirosDelPeriodo. Orden
    // ascendente: el CSV se lee como una bitacora cronologica.
    @Query(
        "SELECT * FROM cortes_caja WHERE sucursalId = :sucursalId AND deletedAt IS NULL " +
            "AND fechaFin BETWEEN :desde AND :hasta ORDER BY fechaFin ASC",
    )
    suspend fun getCortesDelPeriodo(sucursalId: String, desde: Long, hasta: Long): List<CorteCajaEntity>
}
