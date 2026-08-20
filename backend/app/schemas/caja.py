import uuid
from datetime import datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, Field


class CorteCajaCreateSchema(BaseModel):
    local_id: uuid.UUID | None = None
    sucursal_id: uuid.UUID
    usuario_id: str = Field(min_length=1, max_length=120)
    tipo: Literal["parcial", "final"]
    fecha_inicio: datetime
    fecha_fin: datetime
    total_ventas: Decimal = Field(ge=0)
    total_efectivo: Decimal = Field(ge=0)
    total_tarjeta: Decimal = Field(ge=0)
    total_retiros: Decimal = Field(default=Decimal("0"), ge=0)
    monto_esperado: Decimal
    monto_contado: Decimal | None = None
    diferencia: Decimal | None = None


class CorteCajaResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    usuario_id: str
    tipo: str
    fecha_inicio: datetime
    fecha_fin: datetime
    total_ventas: Decimal
    total_efectivo: Decimal
    total_tarjeta: Decimal
    total_retiros: Decimal
    monto_esperado: Decimal
    monto_contado: Decimal | None
    diferencia: Decimal | None
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True


class RetiroEfectivoCreateSchema(BaseModel):
    local_id: uuid.UUID | None = None
    sucursal_id: uuid.UUID
    usuario_id: str = Field(min_length=1, max_length=120)
    monto: Decimal = Field(gt=0)
    motivo: str | None = Field(default=None, max_length=255)
    fecha: datetime


class RetiroEfectivoResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    usuario_id: str
    monto: Decimal
    motivo: str | None
    fecha: datetime
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True


class TotalesCorteResponseSchema(BaseModel):
    total_ventas: Decimal
    total_efectivo: Decimal
    total_tarjeta: Decimal
    total_retiros: Decimal
    monto_esperado: Decimal
