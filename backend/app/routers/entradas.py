from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.articulo import Articulo
from app.models.inventario import Inventario
from app.models.movimiento import Movimiento
from app.schemas.entrada import (
    ArticuloResponseSchema,
    EntradaCreateSchema,
    EntradaResponseSchema,
    InventarioResponseSchema,
    MovimientoResponseSchema,
)

router = APIRouter(tags=["entradas"])

TIPO_MOVIMIENTO_ENTRADA = "entrada"
REFERENCIA_ENTRADA_MANUAL = "entrada_manual"


def _articulo_to_response(articulo: Articulo) -> ArticuloResponseSchema:
    return ArticuloResponseSchema(
        id=articulo.id,
        local_id=articulo.local_id,
        codigo_barras=articulo.codigo_barras,
        sku=articulo.sku,
        nombre=articulo.nombre,
        descripcion=articulo.descripcion,
        categoria=articulo.categoria,
        unidad_medida=articulo.unidad_medida,
        precio_venta=articulo.precio_venta,
        costo=articulo.costo,
        activo=articulo.activo,
        updated_at=articulo.updated_at,
        deleted_at=articulo.deleted_at,
    )


def _inventario_to_response(inventario: Inventario) -> InventarioResponseSchema:
    return InventarioResponseSchema(
        id=inventario.id,
        local_id=inventario.local_id,
        sucursal_id=inventario.sucursal_id,
        articulo_id=inventario.articulo_id,
        cantidad=inventario.cantidad,
        ubicacion=inventario.ubicacion,
        updated_at=inventario.updated_at,
        deleted_at=inventario.deleted_at,
    )


def _movimiento_to_response(movimiento: Movimiento) -> MovimientoResponseSchema:
    return MovimientoResponseSchema(
        id=movimiento.id,
        local_id=movimiento.local_id,
        sucursal_id=movimiento.sucursal_id,
        articulo_id=movimiento.articulo_id,
        usuario_id=movimiento.usuario_id,
        tipo=movimiento.tipo,
        cantidad=movimiento.cantidad,
        ubicacion=movimiento.ubicacion,
        referencia_tipo=movimiento.referencia_tipo,
        referencia_id=movimiento.referencia_id,
        fecha=movimiento.fecha,
        updated_at=movimiento.updated_at,
        deleted_at=movimiento.deleted_at,
    )


@router.post("/entradas", response_model=EntradaResponseSchema, status_code=201)
async def create_entrada(payload: EntradaCreateSchema, db: AsyncSession = Depends(get_db)) -> EntradaResponseSchema:
    articulo: Articulo | None = None
    if payload.articulo_nuevo is not None:
        articulo = Articulo(**payload.articulo_nuevo.model_dump())
        db.add(articulo)
        await db.flush()
        articulo_id = articulo.id
    else:
        articulo_id = payload.articulo_id
        existente = await db.get(Articulo, articulo_id)
        if existente is None:
            raise HTTPException(status_code=404, detail="articulo no encontrado")

    resultado_inventario = await db.execute(
        select(Inventario).where(
            Inventario.sucursal_id == payload.sucursal_id,
            Inventario.articulo_id == articulo_id,
        )
    )
    inventario = resultado_inventario.scalar_one_or_none()
    if inventario is None:
        inventario = Inventario(
            sucursal_id=payload.sucursal_id,
            articulo_id=articulo_id,
            cantidad=payload.cantidad,
            ubicacion=payload.ubicacion,
        )
        db.add(inventario)
    else:
        # Delta con signo, nunca un UPDATE cantidad = X directo (mismo
        # principio que EventoAditivoCombiner del lado Android, PLAN.md
        # Parte 6).
        inventario.cantidad = inventario.cantidad + payload.cantidad
        if payload.ubicacion is not None:
            inventario.ubicacion = payload.ubicacion

    movimiento = Movimiento(
        local_id=payload.local_id,
        sucursal_id=payload.sucursal_id,
        articulo_id=articulo_id,
        usuario_id=payload.usuario_id,
        tipo=TIPO_MOVIMIENTO_ENTRADA,
        cantidad=payload.cantidad,
        ubicacion=payload.ubicacion,
        referencia_tipo=REFERENCIA_ENTRADA_MANUAL,
        referencia_id=None,
        fecha=payload.fecha,
    )
    db.add(movimiento)

    await db.commit()
    await db.refresh(movimiento)
    await db.refresh(inventario)
    if articulo is not None:
        await db.refresh(articulo)

    return EntradaResponseSchema(
        movimiento=_movimiento_to_response(movimiento),
        inventario=_inventario_to_response(inventario),
        articulo=_articulo_to_response(articulo) if articulo is not None else None,
    )
