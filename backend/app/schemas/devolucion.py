import uuid
from datetime import datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, Field

from app.schemas.comunes import UsuarioDispositivo


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
    # Obligatorio: clave de idempotencia de POST /devoluciones (PLAN.md Parte
    # 32, gap 1), mismo criterio que VentaCreateSchema.local_id (Parte 23).
    local_id: uuid.UUID
    sucursal_id: uuid.UUID
    usuario_dispositivo: UsuarioDispositivo = None
    venta_id: uuid.UUID | None = None
    folio: str = Field(min_length=1, max_length=60)
    fecha: datetime
    # Valores de docs/schema-pos.json (PLAN.md Parte 38, B-2); la transicion
    # entre ellos no se valida.
    estado: Literal["registrada", "en_gestion_proveedor", "cerrada"] = "registrada"
    lineas: list[DevolucionDetalleCreateSchema] = Field(min_length=1)


class DevolucionResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    usuario_id: str
    usuario_dispositivo: str | None
    venta_id: uuid.UUID | None
    folio: str
    fecha: datetime
    estado: str
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True
    lineas: list[DevolucionDetalleResponseSchema]
