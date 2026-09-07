from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
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


async def _entrada_existente_response(
    db: AsyncSession, payload: EntradaCreateSchema
) -> EntradaResponseSchema:
    movimiento = (
        await db.execute(select(Movimiento).where(Movimiento.local_id == payload.local_id))
    ).scalar_one()
    inventario = (
        await db.execute(
            select(Inventario).where(
                Inventario.sucursal_id == movimiento.sucursal_id,
                Inventario.articulo_id == movimiento.articulo_id,
            )
        )
    ).scalar_one()
    articulo = await db.get(Articulo, movimiento.articulo_id) if payload.articulo_nuevo is not None else None
    return EntradaResponseSchema(
        movimiento=_movimiento_to_response(movimiento),
        inventario=_inventario_to_response(inventario),
        articulo=_articulo_to_response(articulo) if articulo is not None else None,
    )


@router.post("/entradas", response_model=EntradaResponseSchema, status_code=201)
async def create_entrada(
    payload: EntradaCreateSchema, response: Response, db: AsyncSession = Depends(get_db)
) -> EntradaResponseSchema:
    articulo: Articulo | None = None
    # Idempotente por local_id (PLAN.md Parte 23): UNIQUE(local_id) en
    # movimientos. Todo el flujo de escritura (alta de articulo, upsert de
    # inventario, insert de movimiento) va en un solo try - en un reintento
    # con articulo_nuevo, el primer choque de UNIQUE puede darse antes en el
    # flush intermedio del articulo (UNIQUE en sku), no en el commit final.
    # El rollback deshace toda la transaccion (incluido el alta de
    # articulo/inventario), y se devuelve el estado ya persistido en vez de
    # duplicar - salvo que el conflicto no corresponda a un reintento (ej.
    # sku duplicado real), en cuyo caso se relanza el error original.
    try:
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

        # with_for_update(): mismo principio que POST /ventas (PLAN.md Parte
        # 25, hallazgo M-1) - bloquea la fila hasta el commit para que dos
        # entradas concurrentes del mismo articulo no pierdan un incremento.
        resultado_inventario = await db.execute(
            select(Inventario)
            .where(
                Inventario.sucursal_id == payload.sucursal_id,
                Inventario.articulo_id == articulo_id,
            )
            .with_for_update()
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
    except IntegrityError:
        await db.rollback()
        existente_movimiento = (
            await db.execute(select(Movimiento).where(Movimiento.local_id == payload.local_id))
        ).scalar_one_or_none()
        if existente_movimiento is None:
            sku_colisiona = payload.articulo_nuevo is not None and (
                await db.execute(select(Articulo.id).where(Articulo.sku == payload.articulo_nuevo.sku))
            ).scalar_one_or_none() is not None
            if sku_colisiona:
                # No es un reintento (PLAN.md Parte 25, hallazgo M-6): el
                # articulo_nuevo choco con el UNIQUE de sku -> 409, mismo
                # patron que usuarios/roles, en vez de propagar el
                # IntegrityError. Se confirma releyendo el sku (en vez de
                # asumir que cualquier IntegrityError en este bloque es la
                # colision) porque el try tambien cubre los FK de
                # inventario/movimiento hacia sucursal_id.
                raise HTTPException(
                    status_code=409, detail=f"ya existe un articulo con sku={payload.articulo_nuevo.sku}"
                )
            raise
        response.status_code = 200
        return await _entrada_existente_response(db, payload)

    await db.refresh(movimiento)
    await db.refresh(inventario)
    if articulo is not None:
        await db.refresh(articulo)

    return EntradaResponseSchema(
        movimiento=_movimiento_to_response(movimiento),
        inventario=_inventario_to_response(inventario),
        articulo=_articulo_to_response(articulo) if articulo is not None else None,
    )
