from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.venta import Venta, VentaDetalle
from app.schemas.venta import VentaCreateSchema, VentaDetalleResponseSchema, VentaResponseSchema

router = APIRouter(tags=["ventas"])


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
    await db.commit()
    await db.refresh(venta, attribute_names=["lineas"])
    return _to_response(venta)
