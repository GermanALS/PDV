from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.articulo import Articulo
from app.models.inventario import Inventario
from app.models.movimiento import Movimiento
from app.models.venta import Venta, VentaDetalle
from app.schemas.venta import VentaCreateSchema, VentaDetalleResponseSchema, VentaResponseSchema

router = APIRouter(tags=["ventas"])

TIPO_MOVIMIENTO_SALIDA = "salida"
REFERENCIA_VENTA = "venta"


def _to_response(venta: Venta) -> VentaResponseSchema:
    return VentaResponseSchema(
        id=venta.id,
        local_id=venta.local_id,
        sucursal_id=venta.sucursal_id,
        usuario_id=venta.usuario_id,
        folio=venta.folio,
        fecha=venta.fecha,
        subtotal=venta.subtotal,
        descuento=venta.descuento,
        impuestos=venta.impuestos,
        total=venta.total,
        metodo_pago=venta.metodo_pago,
        estado=venta.estado,
        updated_at=venta.updated_at,
        deleted_at=venta.deleted_at,
        lineas=[
            VentaDetalleResponseSchema(
                id=linea.id,
                local_id=linea.local_id,
                articulo_id=linea.articulo_id,
                cantidad=linea.cantidad,
                precio_unitario=linea.precio_unitario,
                subtotal=linea.subtotal,
            )
            for linea in venta.lineas
        ],
    )


@router.post("/ventas", response_model=VentaResponseSchema, status_code=201)
async def create_venta(payload: VentaCreateSchema, db: AsyncSession = Depends(get_db)) -> VentaResponseSchema:
    for linea in payload.lineas:
        if await db.get(Articulo, linea.articulo_id) is None:
            raise HTTPException(status_code=404, detail=f"articulo no encontrado: {linea.articulo_id}")

    venta = Venta(
        local_id=payload.local_id,
        sucursal_id=payload.sucursal_id,
        usuario_id=payload.usuario_id,
        folio=payload.folio,
        fecha=payload.fecha,
        subtotal=payload.subtotal,
        descuento=payload.descuento,
        impuestos=payload.impuestos,
        total=payload.total,
        metodo_pago=payload.metodo_pago,
        estado=payload.estado,
        lineas=[
            VentaDetalle(
                local_id=linea.local_id,
                articulo_id=linea.articulo_id,
                cantidad=linea.cantidad,
                precio_unitario=linea.precio_unitario,
                subtotal=linea.subtotal,
            )
            for linea in payload.lineas
        ],
    )
    db.add(venta)
    await db.flush()

    # Decremento real de inventario.cantidad + movimiento de salida por
    # linea, en la misma transaccion que la venta (PLAN.md Parte 9, gap
    # corregido - antes esta ruta solo persistia la venta y sus lineas,
    # docs/api-contract.md aun decia que el movimiento se generaba solo del
    # lado del dispositivo). Delta con signo, nunca un UPDATE cantidad = X
    # directo (mismo principio que POST /entradas).
    for linea in payload.lineas:
        resultado_inventario = await db.execute(
            select(Inventario).where(
                Inventario.sucursal_id == payload.sucursal_id,
                Inventario.articulo_id == linea.articulo_id,
            )
        )
        inventario = resultado_inventario.scalar_one_or_none()
        if inventario is None:
            db.add(
                Inventario(
                    sucursal_id=payload.sucursal_id,
                    articulo_id=linea.articulo_id,
                    cantidad=-linea.cantidad,
                )
            )
        else:
            inventario.cantidad = inventario.cantidad - linea.cantidad

        db.add(
            Movimiento(
                sucursal_id=payload.sucursal_id,
                articulo_id=linea.articulo_id,
                usuario_id=payload.usuario_id,
                tipo=TIPO_MOVIMIENTO_SALIDA,
                cantidad=linea.cantidad,
                referencia_tipo=REFERENCIA_VENTA,
                referencia_id=venta.id,
                fecha=payload.fecha,
            )
        )

    await db.commit()
    await db.refresh(venta, attribute_names=["lineas"])
    return _to_response(venta)
