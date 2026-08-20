import uuid
from datetime import datetime

from fastapi import APIRouter, Depends
from sqlalchemy import case, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.caja import CorteCaja, RetiroEfectivo
from app.models.venta import Venta
from app.schemas.caja import (
    CorteCajaCreateSchema,
    CorteCajaResponseSchema,
    RetiroEfectivoCreateSchema,
    RetiroEfectivoResponseSchema,
    TotalesCorteResponseSchema,
)

router = APIRouter(tags=["caja"])


def _corte_to_response(corte: CorteCaja) -> CorteCajaResponseSchema:
    return CorteCajaResponseSchema(
        id=corte.id,
        local_id=corte.local_id,
        sucursal_id=corte.sucursal_id,
        usuario_id=corte.usuario_id,
        tipo=corte.tipo,
        fecha_inicio=corte.fecha_inicio,
        fecha_fin=corte.fecha_fin,
        total_ventas=corte.total_ventas,
        total_efectivo=corte.total_efectivo,
        total_tarjeta=corte.total_tarjeta,
        total_retiros=corte.total_retiros,
        monto_esperado=corte.monto_esperado,
        monto_contado=corte.monto_contado,
        diferencia=corte.diferencia,
        updated_at=corte.updated_at,
        deleted_at=corte.deleted_at,
    )


def _retiro_to_response(retiro: RetiroEfectivo) -> RetiroEfectivoResponseSchema:
    return RetiroEfectivoResponseSchema(
        id=retiro.id,
        local_id=retiro.local_id,
        sucursal_id=retiro.sucursal_id,
        usuario_id=retiro.usuario_id,
        monto=retiro.monto,
        motivo=retiro.motivo,
        fecha=retiro.fecha,
        updated_at=retiro.updated_at,
        deleted_at=retiro.deleted_at,
    )


# El dispositivo ya agrego ventas/retiros de su propio periodo
# (LocalCajaRepository.calcularTotales, PLAN.md Parte 10); esta ruta solo
# persiste el corte ya calculado, sin recalcular nada del lado del
# servidor (mismo criterio que POST /sucursales).
@router.post("/cortes-caja", response_model=CorteCajaResponseSchema, status_code=201)
async def create_corte_caja(payload: CorteCajaCreateSchema, db: AsyncSession = Depends(get_db)) -> CorteCajaResponseSchema:
    corte = CorteCaja(
        local_id=payload.local_id,
        sucursal_id=payload.sucursal_id,
        usuario_id=payload.usuario_id,
        tipo=payload.tipo,
        fecha_inicio=payload.fecha_inicio,
        fecha_fin=payload.fecha_fin,
        total_ventas=payload.total_ventas,
        total_efectivo=payload.total_efectivo,
        total_tarjeta=payload.total_tarjeta,
        total_retiros=payload.total_retiros,
        monto_esperado=payload.monto_esperado,
        monto_contado=payload.monto_contado,
        diferencia=payload.diferencia,
    )
    db.add(corte)
    await db.commit()
    await db.refresh(corte)
    return _corte_to_response(corte)


# Contraparte remota de LocalCajaRepository.calcularTotales (Android,
# PLAN.md Parte 10): usada por RemoteCajaRepository cuando BackendMode es
# remoto/local-con-sincronizacion. A diferencia de las rutas POST de este
# archivo, aqui si se agrega en SQL (Numeric de Postgres, sin la perdida de
# precision de SUM() sobre texto que se evito del lado de Room).
@router.get("/cortes-caja/totales", response_model=TotalesCorteResponseSchema)
async def get_totales_corte(
    sucursal_id: uuid.UUID,
    fecha_inicio: datetime,
    fecha_fin: datetime,
    db: AsyncSession = Depends(get_db),
) -> TotalesCorteResponseSchema:
    ventas_result = await db.execute(
        select(
            func.coalesce(func.sum(Venta.total), 0),
            func.coalesce(func.sum(case((Venta.metodo_pago == "efectivo", Venta.total), else_=0)), 0),
            func.coalesce(func.sum(case((Venta.metodo_pago == "tarjeta", Venta.total), else_=0)), 0),
        ).where(
            Venta.sucursal_id == sucursal_id,
            Venta.estado == "completada",
            Venta.deleted_at.is_(None),
            Venta.fecha >= fecha_inicio,
            Venta.fecha <= fecha_fin,
        )
    )
    total_ventas, total_efectivo, total_tarjeta = ventas_result.one()

    retiros_result = await db.execute(
        select(func.coalesce(func.sum(RetiroEfectivo.monto), 0)).where(
            RetiroEfectivo.sucursal_id == sucursal_id,
            RetiroEfectivo.deleted_at.is_(None),
            RetiroEfectivo.fecha >= fecha_inicio,
            RetiroEfectivo.fecha <= fecha_fin,
        )
    )
    total_retiros = retiros_result.scalar_one()

    return TotalesCorteResponseSchema(
        total_ventas=total_ventas,
        total_efectivo=total_efectivo,
        total_tarjeta=total_tarjeta,
        total_retiros=total_retiros,
        monto_esperado=total_efectivo - total_retiros,
    )


@router.post("/retiros-efectivo", response_model=RetiroEfectivoResponseSchema, status_code=201)
async def create_retiro_efectivo(
    payload: RetiroEfectivoCreateSchema, db: AsyncSession = Depends(get_db)
) -> RetiroEfectivoResponseSchema:
    retiro = RetiroEfectivo(
        local_id=payload.local_id,
        sucursal_id=payload.sucursal_id,
        usuario_id=payload.usuario_id,
        monto=payload.monto,
        motivo=payload.motivo,
        fecha=payload.fecha,
    )
    db.add(retiro)
    await db.commit()
    await db.refresh(retiro)
    return _retiro_to_response(retiro)
