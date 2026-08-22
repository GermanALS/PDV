import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

Modulo = Literal["venta", "entrada", "inventario", "caja", "devoluciones", "usuarios", "configuracion"]


class RolCreateSchema(BaseModel):
    local_id: uuid.UUID | None = None
    nombre: str = Field(min_length=1, max_length=60)
    modulos_permitidos: list[Modulo] = Field(min_length=1)


class RolUpdateSchema(BaseModel):
    nombre: str = Field(min_length=1, max_length=60)
    modulos_permitidos: list[Modulo] = Field(min_length=1)


class RolResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    nombre: str
    modulos_permitidos: list[str]
    es_sistema: bool
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True


class RolListResponseSchema(BaseModel):
    items: list[RolResponseSchema]
    page: int
    page_size: int
    total: int
