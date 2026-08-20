import uuid
from decimal import Decimal

from pydantic import BaseModel, Field

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


class InventarioListResponseSchema(BaseModel):
    items: list[InventarioItemResponseSchema]
    page: int
    page_size: int
    total: int


class ArticuloEdicionSchema(BaseModel):
    sucursal_id: uuid.UUID
    usuario_id: str = Field(min_length=1, max_length=120)
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


class ValoresResponseSchema(BaseModel):
    valores: list[str]
