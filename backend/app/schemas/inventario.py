import uuid
from datetime import datetime
from decimal import Decimal

from pydantic import AwareDatetime, BaseModel, Field, field_validator

from app.schemas.comunes import UsuarioDispositivo
from app.schemas.entrada import ArticuloResponseSchema, InventarioResponseSchema, MovimientoResponseSchema


class InventarioItemResponseSchema(BaseModel):
    articulo_id: uuid.UUID
    codigo_barras: str | None
    sku: str
    nombre: str
    descripcion: str | None
    categoria: str | None
    unidad_medida: str
    precio_venta: Decimal
    costo: Decimal | None
    cantidad: Decimal
    ubicacion: str | None
    # Mayor entre el updated_at de `inventario` (cambia con cada movimiento
    # de stock) y el del articulo (cambia con una edicion de catalogo):
    # cursor y base del merge del pull diferido (PLAN.md Partes 32 y 35).
    updated_at: datetime


class InventarioListResponseSchema(BaseModel):
    items: list[InventarioItemResponseSchema]
    page: int
    page_size: int
    total: int


class ArticuloEdicionSchema(BaseModel):
    sucursal_id: uuid.UUID
    usuario_dispositivo: UsuarioDispositivo = None
    nombre: str = Field(min_length=1, max_length=160)
    descripcion: str | None = None
    categoria: str | None = None
    unidad_medida: str = Field(min_length=1, max_length=30)
    precio_venta: Decimal = Field(ge=0)
    costo: Decimal | None = Field(default=None, ge=0)
    cantidad: Decimal
    ubicacion: str | None = None


class AjusteInventarioResponseSchema(BaseModel):
    articulo: ArticuloResponseSchema
    inventario: InventarioResponseSchema
    movimiento: MovimientoResponseSchema | None


class AjusteDeltaCreateSchema(BaseModel):
    local_id: uuid.UUID
    sucursal_id: uuid.UUID
    articulo_id: uuid.UUID
    usuario_dispositivo: UsuarioDispositivo = None
    fecha: datetime
    delta: Decimal

    @field_validator("delta")
    @classmethod
    def _delta_no_cero(cls, value: Decimal) -> Decimal:
        if value == 0:
            raise ValueError("delta no puede ser 0")
        return value


class AjusteDeltaResponseSchema(BaseModel):
    movimiento: MovimientoResponseSchema
    inventario: InventarioResponseSchema


class ArticuloCatalogoEdicionSchema(BaseModel):
    sucursal_id: uuid.UUID
    nombre: str = Field(min_length=1, max_length=160)
    descripcion: str | None = None
    categoria: str | None = None
    unidad_medida: str = Field(min_length=1, max_length=30)
    precio_venta: Decimal = Field(ge=0)
    costo: Decimal | None = Field(default=None, ge=0)
    ubicacion: str | None = None
    editado_en: AwareDatetime


class ArticuloCatalogoEdicionResponseSchema(BaseModel):
    aplicado: bool
    articulo: ArticuloResponseSchema
    inventario: InventarioResponseSchema | None


class ValoresResponseSchema(BaseModel):
    valores: list[str]
