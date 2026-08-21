from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.articulo import Articulo
from app.models.devolucion import Devolucion, DevolucionDetalle
from app.models.venta import Venta
from app.schemas.devolucion import DevolucionCreateSchema, DevolucionDetalleResponseSchema, DevolucionResponseSchema

router = APIRouter(tags=["devoluciones"])


def _to_response(devolucion: Devolucion) -> DevolucionResponseSchema:
    return DevolucionResponseSchema(
        id=devolucion.id,
        local_id=devolucion.local_id,
        sucursal_id=devolucion.sucursal_id,
        usuario_id=devolucion.usuario_id,
        venta_id=devolucion.venta_id,
        folio=devolucion.folio,
        fecha=devolucion.fecha,
        estado=devolucion.estado,
        updated_at=devolucion.updated_at,
        deleted_at=devolucion.deleted_at,
        lineas=[
            DevolucionDetalleResponseSchema(
                id=linea.id,
                local_id=linea.local_id,
                articulo_id=linea.articulo_id,
                cantidad=linea.cantidad,
                motivo=linea.motivo,
                condicion=linea.condicion,
            )
            for linea in devolucion.lineas
        ],
    )


# Esta ruta no toca `inventario` ni escribe `movimiento` (a diferencia de
# POST /ventas y POST /entradas) - el proposito de esta Parte es gestionar
# la devolucion con el proveedor, no un restock inmediato del catalogo
# vendible (docs/api-contract.md seccion 9).
@router.post("/devoluciones", response_model=DevolucionResponseSchema, status_code=201)
async def create_devolucion(
    payload: DevolucionCreateSchema, db: AsyncSession = Depends(get_db)
) -> DevolucionResponseSchema:
    if payload.venta_id is not None and await db.get(Venta, payload.venta_id) is None:
        raise HTTPException(status_code=404, detail=f"venta no encontrada: {payload.venta_id}")

    for linea in payload.lineas:
        if await db.get(Articulo, linea.articulo_id) is None:
            raise HTTPException(status_code=404, detail=f"articulo no encontrado: {linea.articulo_id}")

    devolucion = Devolucion(
        local_id=payload.local_id,
        sucursal_id=payload.sucursal_id,
        usuario_id=payload.usuario_id,
        venta_id=payload.venta_id,
        folio=payload.folio,
        fecha=payload.fecha,
        estado=payload.estado,
        lineas=[
            DevolucionDetalle(
                local_id=linea.local_id,
                articulo_id=linea.articulo_id,
                cantidad=linea.cantidad,
                motivo=linea.motivo,
                condicion=linea.condicion,
            )
            for linea in payload.lineas
        ],
    )
    db.add(devolucion)
    await db.commit()
    await db.refresh(devolucion, attribute_names=["lineas"])
    return _to_response(devolucion)
