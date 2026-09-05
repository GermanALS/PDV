import uuid
from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, Field


class VentaDetalleCreateSchema(BaseModel):
    local_id: uuid.UUID | None = None
    articulo_id: uuid.UUID
    cantidad: Decimal = Field(gt=0)
    precio_unitario: Decimal = Field(ge=0)
    subtotal: Decimal = Field(ge=0)


class VentaDetalleResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    articulo_id: uuid.UUID
    cantidad: Decimal
    precio_unitario: Decimal
    subtotal: Decimal


class VentaCreateSchema(BaseModel):
    local_id: uuid.UUID
    sucursal_id: uuid.UUID
    usuario_id: str = Field(min_length=1, max_length=120)
    folio: str = Field(min_length=1, max_length=60)
    fecha: datetime
    subtotal: Decimal = Field(ge=0)
    descuento: Decimal = Field(default=Decimal("0"), ge=0)
    impuestos: Decimal = Field(default=Decimal("0"), ge=0)
    total: Decimal = Field(ge=0)
    metodo_pago: str = Field(min_length=1, max_length=30)
    estado: str = Field(default="completada", max_length=30)
    lineas: list[VentaDetalleCreateSchema] = Field(min_length=1)


class VentaResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    usuario_id: str
    folio: str
    fecha: datetime
    subtotal: Decimal
    descuento: Decimal
    impuestos: Decimal
    total: Decimal
    metodo_pago: str
    estado: str
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True
    lineas: list[VentaDetalleResponseSchema]
