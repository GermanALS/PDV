import uuid
from datetime import datetime
from decimal import Decimal
from typing import Literal, Self

from pydantic import BaseModel, Field, model_validator

from app.schemas.comunes import UsuarioDispositivo, a_centavos

# Valores validos de metodo_pago/estado (PLAN.md Parte 25, hallazgo M-3):
# caja.get_totales_corte filtra por estos mismos literales, asi que un
# valor libre (ej. "Efectivo") producia una diferencia de caja silenciosa.
# Se importan en caja.py en vez de repetir las strings alli, y coinciden
# con METODO_PAGO_EFECTIVO/METODO_PAGO_TARJETA de LocalCajaRepository.kt
# (Android).
METODO_PAGO_EFECTIVO = "efectivo"
METODO_PAGO_TARJETA = "tarjeta"
ESTADO_VENTA_COMPLETADA = "completada"


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
    usuario_dispositivo: UsuarioDispositivo = None
    folio: str = Field(min_length=1, max_length=60)
    fecha: datetime
    subtotal: Decimal = Field(ge=0)
    descuento: Decimal = Field(default=Decimal("0"), ge=0)
    impuestos: Decimal = Field(default=Decimal("0"), ge=0)
    total: Decimal = Field(ge=0)
    metodo_pago: Literal[METODO_PAGO_EFECTIVO, METODO_PAGO_TARJETA]
    estado: Literal[ESTADO_VENTA_COMPLETADA] = ESTADO_VENTA_COMPLETADA
    lineas: list[VentaDetalleCreateSchema] = Field(min_length=1)

    @model_validator(mode="after")
    def _validar_montos(self) -> Self:
        """Recalcula los montos con las formulas de VentaUiState (Android) y
        rechaza la venta si no cuadran (PLAN.md Parte 38, M-2)."""
        for linea in self.lineas:
            if a_centavos(linea.cantidad * linea.precio_unitario) != a_centavos(linea.subtotal):
                raise ValueError("el subtotal de una linea no es cantidad * precio_unitario")
        if a_centavos(sum((linea.subtotal for linea in self.lineas), Decimal("0"))) != a_centavos(self.subtotal):
            raise ValueError("el subtotal no es la suma de los subtotales de las lineas")
        if a_centavos(self.subtotal - self.descuento + self.impuestos) != a_centavos(self.total):
            raise ValueError("el total no es subtotal - descuento + impuestos")
        return self


class VentaResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    usuario_id: str
    usuario_dispositivo: str | None
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
