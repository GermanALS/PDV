import uuid
from datetime import datetime

from fastapi import APIRouter, Depends, Query
from sqlalchemy import case, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.caja import CorteCaja, RetiroEfectivo
from app.models.venta import Venta
from app.schemas.caja import (
    CorteCajaCreateSchema,
    CorteCajaListResponseSchema,
    CorteCajaResponseSchema,
    RetiroEfectivoCreateSchema,
    RetiroEfectivoListResponseSchema,
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


# Contraparte remota de CajaRepository.observeCortes (Android, PLAN.md
# Parte 18, sub-parte F) - mismo estilo de paginacion que GET /inventario
# (sucursal_id requerido, page/page_size), orden por fecha_fin descendente
# (mas reciente primero) para que el historial de Caja muestre lo ultimo
# arriba. desde/hasta (opcionales, PLAN.md sub-parte I) filtran por
# fecha_fin dentro del rango inclusivo, para la exportacion por periodo.
@router.get("/cortes-caja", response_model=CorteCajaListResponseSchema)
async def list_cortes_caja(
    sucursal_id: uuid.UUID,
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    desde: datetime | None = None,
    hasta: datetime | None = None,
    db: AsyncSession = Depends(get_db),
) -> CorteCajaListResponseSchema:
    filtros = [CorteCaja.sucursal_id == sucursal_id, CorteCaja.deleted_at.is_(None)]
    if desde is not None:
        filtros.append(CorteCaja.fecha_fin >= desde)
    if hasta is not None:
        filtros.append(CorteCaja.fecha_fin <= hasta)
    total = await db.scalar(select(func.count()).select_from(CorteCaja).where(*filtros))
    resultado = await db.execute(
        select(CorteCaja)
        .where(*filtros)
        .order_by(CorteCaja.fecha_fin.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
    )
    items = [_corte_to_response(corte) for corte in resultado.scalars().all()]
    return CorteCajaListResponseSchema(items=items, page=page, page_size=page_size, total=total or 0)


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


# Contraparte remota de RetiroEfectivoRepository.observeRetiros (Android,
# PLAN.md Parte 18, sub-parte F) - mismo criterio de paginacion que
# GET /cortes-caja, orden por fecha descendente. desde/hasta (opcionales,
# PLAN.md sub-parte I) filtran por fecha dentro del rango inclusivo.
@router.get("/retiros-efectivo", response_model=RetiroEfectivoListResponseSchema)
async def list_retiros_efectivo(
    sucursal_id: uuid.UUID,
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    desde: datetime | None = None,
    hasta: datetime | None = None,
    db: AsyncSession = Depends(get_db),
) -> RetiroEfectivoListResponseSchema:
    filtros = [RetiroEfectivo.sucursal_id == sucursal_id, RetiroEfectivo.deleted_at.is_(None)]
    if desde is not None:
        filtros.append(RetiroEfectivo.fecha >= desde)
    if hasta is not None:
        filtros.append(RetiroEfectivo.fecha <= hasta)
    total = await db.scalar(select(func.count()).select_from(RetiroEfectivo).where(*filtros))
    resultado = await db.execute(
        select(RetiroEfectivo)
        .where(*filtros)
        .order_by(RetiroEfectivo.fecha.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
    )
    items = [_retiro_to_response(retiro) for retiro in resultado.scalars().all()]
    return RetiroEfectivoListResponseSchema(items=items, page=page, page_size=page_size, total=total or 0)
