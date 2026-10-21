import uuid
from datetime import datetime, timezone
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy import func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import usuario_actual
from app.inventario_bloqueo import bloquear_inventario
from app.models.articulo import Articulo
from app.models.inventario import Inventario
from app.models.movimiento import Movimiento
from app.models.sucursal import Sucursal
from app.models.usuario import Usuario
from app.schemas.entrada import ArticuloResponseSchema, InventarioResponseSchema, MovimientoResponseSchema
from app.schemas.inventario import (
    AjusteDeltaCreateSchema,
    AjusteDeltaResponseSchema,
    AjusteInventarioResponseSchema,
    ArticuloCatalogoEdicionResponseSchema,
    ArticuloCatalogoEdicionSchema,
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
        usuario_dispositivo=movimiento.usuario_dispositivo,
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
    # Pull diferido (PLAN.md Partes 32 y 35): una fila cambia si cambio su
    # inventario (stock, ubicacion) o su articulo (catalogo global).
    if updated_since is not None:
        filtros.append(or_(Inventario.updated_at >= updated_since, Articulo.updated_at >= updated_since))

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
            updated_at=max(inventario.updated_at, articulo.updated_at),
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
    usuario: Usuario = Depends(usuario_actual),
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
            # El usuario sale del JWT, nunca del body (PLAN.md Parte 38, M-1).
            usuario_id=str(usuario.id),
            usuario_dispositivo=payload.usuario_dispositivo,
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


@router.post("/ajustes-inventario", response_model=AjusteDeltaResponseSchema, status_code=201)
async def create_ajuste_delta(
    payload: AjusteDeltaCreateSchema,
    response: Response,
    db: AsyncSession = Depends(get_db),
    usuario: Usuario = Depends(usuario_actual),
) -> AjusteDeltaResponseSchema:
    """Suma un delta a la existencia; idempotente por `local_id` del movimiento (PLAN.md Parte 34)."""
    if await db.get(Sucursal, payload.sucursal_id) is None:
        raise HTTPException(status_code=404, detail=f"sucursal no encontrada: {payload.sucursal_id}")
    if await db.get(Articulo, payload.articulo_id) is None:
        raise HTTPException(status_code=404, detail="articulo no encontrado")

    try:
        # Bloqueada hasta el commit; la primera fila concurrente no choca
        # (PLAN.md Parte 38, B-1).
        inventario = await bloquear_inventario(db, payload.sucursal_id, payload.articulo_id)
        inventario.cantidad = inventario.cantidad + payload.delta

        movimiento = Movimiento(
            local_id=payload.local_id,
            sucursal_id=payload.sucursal_id,
            articulo_id=payload.articulo_id,
            # El usuario sale del JWT, nunca del body (PLAN.md Parte 38, M-1).
            usuario_id=str(usuario.id),
            usuario_dispositivo=payload.usuario_dispositivo,
            tipo=TIPO_MOVIMIENTO_AJUSTE,
            cantidad=payload.delta,
            referencia_tipo=REFERENCIA_AJUSTE_MANUAL,
            fecha=payload.fecha,
        )
        db.add(movimiento)
        await db.commit()
    except IntegrityError:
        await db.rollback()
        movimiento = (
            await db.execute(select(Movimiento).where(Movimiento.local_id == payload.local_id))
        ).scalar_one_or_none()
        if movimiento is None:
            raise
        inventario = (
            await db.execute(
                select(Inventario).where(
                    Inventario.sucursal_id == movimiento.sucursal_id, Inventario.articulo_id == movimiento.articulo_id
                )
            )
        ).scalar_one()
        response.status_code = 200
        return AjusteDeltaResponseSchema(
            movimiento=_movimiento_to_response(movimiento), inventario=_inventario_to_response(inventario)
        )

    await db.refresh(movimiento)
    await db.refresh(inventario)
    return AjusteDeltaResponseSchema(
        movimiento=_movimiento_to_response(movimiento), inventario=_inventario_to_response(inventario)
    )


@router.patch("/articulos/{articulo_id}", response_model=ArticuloCatalogoEdicionResponseSchema)
async def editar_catalogo_articulo(
    articulo_id: uuid.UUID,
    payload: ArticuloCatalogoEdicionSchema,
    db: AsyncSession = Depends(get_db),
) -> ArticuloCatalogoEdicionResponseSchema:
    """Edita catalogo y ubicacion sin tocar la cantidad; last-write-wins por `editado_en` (PLAN.md Parte 35)."""
    articulo = (
        await db.execute(select(Articulo).where(Articulo.id == articulo_id).with_for_update(key_share=True))
    ).scalar_one_or_none()
    if articulo is None:
        raise HTTPException(status_code=404, detail="articulo no encontrado")
    if await db.get(Sucursal, payload.sucursal_id) is None:
        raise HTTPException(status_code=404, detail=f"sucursal no encontrada: {payload.sucursal_id}")

    inventario = (
        await db.execute(
            select(Inventario).where(Inventario.sucursal_id == payload.sucursal_id, Inventario.articulo_id == articulo_id)
        )
    ).scalar_one_or_none()

    aplicado = payload.editado_en >= articulo.updated_at
    if aplicado:
        if inventario is None:
            inventario = Inventario(sucursal_id=payload.sucursal_id, articulo_id=articulo_id, cantidad=Decimal(0))
            db.add(inventario)
        articulo.nombre = payload.nombre
        articulo.descripcion = payload.descripcion
        articulo.categoria = payload.categoria
        articulo.unidad_medida = payload.unidad_medida
        articulo.precio_venta = payload.precio_venta
        articulo.costo = payload.costo
        articulo.updated_at = datetime.now(timezone.utc)
        inventario.ubicacion = payload.ubicacion

    await db.commit()
    await db.refresh(articulo)
    if inventario is not None:
        await db.refresh(inventario)
    return ArticuloCatalogoEdicionResponseSchema(
        aplicado=aplicado,
        articulo=_articulo_to_response(articulo),
        inventario=_inventario_to_response(inventario) if inventario is not None else None,
    )


@router.get("/articulos/categorias",response_model=ValoresResponseSchema)
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
