import uuid
from datetime import datetime
from decimal import Decimal
from typing import Self

from pydantic import BaseModel, Field, model_validator


class ArticuloNuevoSchema(BaseModel):
    local_id: uuid.UUID | None = None
    codigo_barras: str | None = None
    sku: str = Field(min_length=1, max_length=60)
    nombre: str = Field(min_length=1, max_length=160)
    descripcion: str | None = None
    categoria: str | None = None
    unidad_medida: str = Field(min_length=1, max_length=30)
    precio_venta: Decimal = Field(ge=0)
    costo: Decimal | None = Field(default=None, ge=0)


class ArticuloResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    codigo_barras: str | None
    sku: str
    nombre: str
    descripcion: str | None
    categoria: str | None
    unidad_medida: str
    precio_venta: Decimal
    costo: Decimal | None
    activo: bool
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True


class InventarioResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    articulo_id: uuid.UUID
    cantidad: Decimal
    ubicacion: str | None
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True


class MovimientoResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    sucursal_id: uuid.UUID
    articulo_id: uuid.UUID
    usuario_id: str
    tipo: str
    cantidad: Decimal
    ubicacion: str | None
    referencia_tipo: str | None
    referencia_id: uuid.UUID | None
    fecha: datetime
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True


class EntradaCreateSchema(BaseModel):
    local_id: uuid.UUID
    sucursal_id: uuid.UUID
    usuario_id: str = Field(min_length=1, max_length=120)
    fecha: datetime
    cantidad: Decimal = Field(gt=0)
    ubicacion: str | None = None
    articulo_id: uuid.UUID | None = None
    articulo_nuevo: ArticuloNuevoSchema | None = None

    @model_validator(mode="after")
    def _validar_articulo_exclusivo(self) -> Self:
        if (self.articulo_id is None) == (self.articulo_nuevo is None):
            raise ValueError("se requiere exactamente uno de articulo_id o articulo_nuevo")
        return self


class EntradaResponseSchema(BaseModel):
    movimiento: MovimientoResponseSchema
    inventario: InventarioResponseSchema
    articulo: ArticuloResponseSchema | None
