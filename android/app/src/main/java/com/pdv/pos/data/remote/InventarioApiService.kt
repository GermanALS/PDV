package com.pdv.pos.data.remote

import com.pdv.pos.data.remote.dto.AjusteDeltaDto
import com.pdv.pos.data.remote.dto.AjusteDeltaRequestDto
import com.pdv.pos.data.remote.dto.AjusteInventarioDto
import com.pdv.pos.data.remote.dto.ArticuloCatalogoDto
import com.pdv.pos.data.remote.dto.ArticuloCatalogoRequestDto
import com.pdv.pos.data.remote.dto.ArticuloEdicionRequestDto
import com.pdv.pos.data.remote.dto.InventarioListResponseDto
import com.pdv.pos.data.remote.dto.ValoresDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface InventarioApiService {
    @GET("inventario")
    suspend fun getInventario(
        @Query("sucursal_id") sucursalId: String,
        @Query("q") q: String?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("updated_since") updatedSince: String? = null,
    ): InventarioListResponseDto

    @PATCH("inventario/{articuloId}")
    suspend fun ajustarArticulo(
        @Path("articuloId") articuloId: String,
        @Body request: ArticuloEdicionRequestDto,
    ): AjusteInventarioDto

    @POST("ajustes-inventario")
    suspend fun ajustarPorDelta(@Body request: AjusteDeltaRequestDto): AjusteDeltaDto

    @PATCH("articulos/{articuloId}")
    suspend fun editarCatalogo(
        @Path("articuloId") articuloId: String,
        @Body request: ArticuloCatalogoRequestDto,
    ): ArticuloCatalogoDto

    @GET("inventario/ubicaciones")
    suspend fun getUbicaciones(@Query("sucursal_id") sucursalId: String): ValoresDto

    @GET("articulos/categorias")
    suspend fun getCategorias(): ValoresDto

    @GET("articulos/unidades-medida")
    suspend fun getUnidadesMedida(): ValoresDto
}
