import uuid
from datetime import datetime, timezone
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.articulo import Articulo
from app.models.inventario import Inventario
from app.models.movimiento import Movimiento
from app.models.sucursal import Sucursal
from app.schemas.entrada import ArticuloResponseSchema, InventarioResponseSchema, MovimientoResponseSchema
from app.schemas.inventario import (
    AjusteInventarioResponseSchema,
    ArticuloEdicionSchema,
    InventarioItemResponseSchema,
    InventarioListResponseSchema,
    ValoresResponseSchema,
)

router = APIRouter(tags=["inventario"])

TIPO_MOVIMIENTO_AJUSTE = "ajuste"
REFERENCIA_AJUSTE_MANUAL = "ajuste_manual"


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


@router.get("/inventario", response_model=InventarioListResponseSchema)
async def list_inventario(
    sucursal_id: uuid.UUID,
    q: str | None = None,
    updated_since: datetime | None = None,
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
) -> InventarioListResponseSchema:
    filtros = [Inventario.sucursal_id == sucursal_id, Inventario.deleted_at.is_(None), Articulo.deleted_at.is_(None)]
    if q:
        termino = f"%{q}%"
        filtros.append(
            (Articulo.nombre.ilike(termino)) | (Articulo.sku.ilike(termino)) | (Articulo.codigo_barras.ilike(termino))
        )
    # Pull diferido (PLAN.md Parte 32, Grupo 3): filtra por updated_at de la
    # fila `inventario`, no del articulo.
    if updated_since is not None:
        filtros.append(Inventario.updated_at >= updated_since)

    total = await db.scalar(
        select(func.count()).select_from(Inventario).join(Articulo, Articulo.id == Inventario.articulo_id).where(*filtros)
    )
    resultado = await db.execute(
        select(Inventario, Articulo)
        .join(Articulo, Articulo.id == Inventario.articulo_id)
        .where(*filtros)
        .order_by(Articulo.nombre)
        .offset((page - 1) * page_size)
        .limit(page_size)
    )
    items = [
        InventarioItemResponseSchema(
            articulo_id=articulo.id,
            codigo_barras=articulo.codigo_barras,
            sku=articulo.sku,
            nombre=articulo.nombre,
            descripcion=articulo.descripcion,
            categoria=articulo.categoria,
            unidad_medida=articulo.unidad_medida,
            precio_venta=articulo.precio_venta,
            costo=articulo.costo,
            cantidad=inventario.cantidad,
            ubicacion=inventario.ubicacion,
            updated_at=inventario.updated_at,
        )
        for inventario, articulo in resultado.all()
    ]
    return InventarioListResponseSchema(items=items, page=page, page_size=page_size, total=total or 0)


@router.get("/inventario/ubicaciones", response_model=ValoresResponseSchema)
async def list_ubicaciones(sucursal_id: uuid.UUID, db: AsyncSession = Depends(get_db)) -> ValoresResponseSchema:
    resultado = await db.execute(
        select(Inventario.ubicacion)
        .where(Inventario.sucursal_id == sucursal_id, Inventario.ubicacion.is_not(None), Inventario.deleted_at.is_(None))
        .distinct()
        .order_by(Inventario.ubicacion)
    )
    return ValoresResponseSchema(valores=[fila[0] for fila in resultado.all()])


@router.patch("/inventario/{articulo_id}", response_model=AjusteInventarioResponseSchema)
async def ajustar_articulo(
    articulo_id: uuid.UUID,
    payload: ArticuloEdicionSchema,
    db: AsyncSession = Depends(get_db),
) -> AjusteInventarioResponseSchema:
    articulo = await db.get(Articulo, articulo_id)
    if articulo is None:
        raise HTTPException(status_code=404, detail="articulo no encontrado")
    # Valida sucursal_id antes de escribir (PLAN.md Parte 32): ver
    # create_venta en ventas.py.
    if await db.get(Sucursal, payload.sucursal_id) is None:
        raise HTTPException(status_code=404, detail=f"sucursal no encontrada: {payload.sucursal_id}")

    ahora = datetime.now(timezone.utc)
    articulo.nombre = payload.nombre
    articulo.descripcion = payload.descripcion
    articulo.categoria = payload.categoria
    articulo.unidad_medida = payload.unidad_medida
    articulo.precio_venta = payload.precio_venta
    articulo.costo = payload.costo
    articulo.updated_at = ahora

    resultado_inventario = await db.execute(
        select(Inventario).where(Inventario.sucursal_id == payload.sucursal_id, Inventario.articulo_id == articulo_id)
    )
    inventario = resultado_inventario.scalar_one_or_none()
    cantidad_conocida: Decimal = inventario.cantidad if inventario is not None else Decimal(0)
    delta = payload.cantidad - cantidad_conocida

    if inventario is None:
        inventario = Inventario(
            sucursal_id=payload.sucursal_id,
            articulo_id=articulo_id,
            cantidad=payload.cantidad,
            ubicacion=payload.ubicacion,
        )
        db.add(inventario)
    else:
        inventario.cantidad = payload.cantidad
        inventario.ubicacion = payload.ubicacion
        inventario.updated_at = ahora

    movimiento: Movimiento | None = None
    if delta != 0:
        movimiento = Movimiento(
            sucursal_id=payload.sucursal_id,
            articulo_id=articulo_id,
            usuario_id=payload.usuario_id,
            tipo=TIPO_MOVIMIENTO_AJUSTE,
            cantidad=delta,
            ubicacion=payload.ubicacion,
            referencia_tipo=REFERENCIA_AJUSTE_MANUAL,
            referencia_id=None,
            fecha=ahora,
        )
        db.add(movimiento)

    await db.commit()
    await db.refresh(articulo)
    await db.refresh(inventario)
    if movimiento is not None:
        await db.refresh(movimiento)

    return AjusteInventarioResponseSchema(
        articulo=_articulo_to_response(articulo),
        inventario=_inventario_to_response(inventario),
        movimiento=_movimiento_to_response(movimiento) if movimiento is not None else None,
    )


@router.get("/articulos/categorias", response_model=ValoresResponseSchema)
async def list_categorias(db: AsyncSession = Depends(get_db)) -> ValoresResponseSchema:
    resultado = await db.execute(
        select(Articulo.categoria)
        .where(Articulo.categoria.is_not(None), Articulo.deleted_at.is_(None))
        .distinct()
        .order_by(Articulo.categoria)
    )
    return ValoresResponseSchema(valores=[fila[0] for fila in resultado.all()])


@router.get("/articulos/unidades-medida", response_model=ValoresResponseSchema)
async def list_unidades_medida(db: AsyncSession = Depends(get_db)) -> ValoresResponseSchema:
    resultado = await db.execute(
        select(Articulo.unidad_medida).where(Articulo.deleted_at.is_(None)).distinct().order_by(Articulo.unidad_medida)
    )
    return ValoresResponseSchema(valores=[fila[0] for fila in resultado.all()])
