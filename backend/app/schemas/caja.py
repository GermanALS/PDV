import uuid
from datetime import datetime
from decimal import Decimal
from typing import Literal, Self

from pydantic import BaseModel, Field, model_validator

from app.schemas.comunes import UsuarioDispositivo, a_centavos


class CorteCajaCreateSchema(BaseModel):
    local_id: uuid.UUID
    sucursal_id: uuid.UUID
    usuario_dispositivo: UsuarioDispositivo = None
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

    @model_validator(mode="after")
    def _validar_consistencia(self) -> Self:
        """Coherencia interna del corte con las formulas de
        LocalCajaRepository.calcularTotales y CajaUiState (PLAN.md Parte 38,
        M-2). No compara contra las ventas guardadas: con el sync diferido el
        corte puede llegar antes que ellas."""
        if self.fecha_fin < self.fecha_inicio:
            raise ValueError("fecha_fin es anterior a fecha_inicio")
        if a_centavos(self.total_efectivo + self.total_tarjeta) != a_centavos(self.total_ventas):
            raise ValueError("total_ventas no es total_efectivo + total_tarjeta")
        if a_centavos(self.total_efectivo - self.total_retiros) != a_centavos(self.monto_esperado):
            raise ValueError("monto_esperado no es total_efectivo - total_retiros")
        if (self.monto_contado is None) != (self.diferencia is None):
            raise ValueError("monto_contado y diferencia van juntos o ninguno")
        if self.monto_contado is not None and a_centavos(self.monto_contado - self.monto_esperado) != a_centavos(
            self.diferencia
        ):
            raise ValueError("diferencia no es monto_contado - monto_esperado")
        return self


class CorteCajaResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    usuario_id: str
    usuario_dispositivo: str | None
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
    local_id: uuid.UUID
    sucursal_id: uuid.UUID
    usuario_dispositivo: UsuarioDispositivo = None
    monto: Decimal = Field(gt=0)
    motivo: str | None = Field(default=None, max_length=255)
    fecha: datetime


class RetiroEfectivoResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    usuario_id: str
    usuario_dispositivo: str | None
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


class CorteCajaListResponseSchema(BaseModel):
    items: list[CorteCajaResponseSchema]
    page: int
    page_size: int
    total: int


class RetiroEfectivoListResponseSchema(BaseModel):
    items: list[RetiroEfectivoResponseSchema]
    page: int
    page_size: int
    total: int
