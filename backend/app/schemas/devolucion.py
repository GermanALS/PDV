import uuid
from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, Field


class DevolucionDetalleCreateSchema(BaseModel):
    local_id: uuid.UUID | None = None
    articulo_id: uuid.UUID
    cantidad: Decimal = Field(gt=0)
    motivo: str | None = Field(default=None, max_length=255)
    condicion: str | None = Field(default=None, max_length=30)


class DevolucionDetalleResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    articulo_id: uuid.UUID
    cantidad: Decimal
    motivo: str | None
    condicion: str | None


class DevolucionCreateSchema(BaseModel):
    local_id: uuid.UUID | None = None
    sucursal_id: uuid.UUID
    usuario_id: str = Field(min_length=1, max_length=120)
    venta_id: uuid.UUID | None = None
    folio: str = Field(min_length=1, max_length=60)
    fecha: datetime
    estado: str = Field(default="registrada", max_length=30)
    lineas: list[DevolucionDetalleCreateSchema] = Field(min_length=1)


class DevolucionResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    usuario_id: str
    venta_id: uuid.UUID | None
    folio: str
    fecha: datetime
    estado: str
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True
    lineas: list[DevolucionDetalleResponseSchema]
