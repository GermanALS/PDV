import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

RolUsuario = Literal["administrador", "encargado_turno"]


class UsuarioCreateSchema(BaseModel):
    local_id: uuid.UUID | None = None
    username: str = Field(min_length=1, max_length=60)
    nombre_completo: str = Field(min_length=1, max_length=120)
    rol: RolUsuario
    activo: bool = True


class UsuarioUpdateSchema(BaseModel):
    username: str = Field(min_length=1, max_length=60)
    nombre_completo: str = Field(min_length=1, max_length=120)
    rol: RolUsuario
    activo: bool


class UsuarioResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    username: str
    nombre_completo: str
    rol: str
    activo: bool
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True


class UsuarioListResponseSchema(BaseModel):
    items: list[UsuarioResponseSchema]
    page: int
    page_size: int
    total: int
