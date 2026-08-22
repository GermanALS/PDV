import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class UsuarioCreateSchema(BaseModel):
    local_id: uuid.UUID | None = None
    username: str = Field(min_length=1, max_length=60)
    nombre_completo: str = Field(min_length=1, max_length=120)
    rol_id: uuid.UUID
    activo: bool = True
    # Ya hasheado del lado que recibio el texto plano (PLAN.md Parte 13,
    # Decision 2) - este endpoint nunca recibe ni calcula a partir de una
    # contrasena en texto plano. None = usuario creado sin contrasena
    # todavia (no puede iniciar sesion hasta que se le asigne una).
    password_hash: str | None = None


class UsuarioUpdateSchema(BaseModel):
    username: str = Field(min_length=1, max_length=60)
    nombre_completo: str = Field(min_length=1, max_length=120)
    rol_id: uuid.UUID
    activo: bool
    # None = no cambiar la contrasena existente (semantica PATCH).
    password_hash: str | None = None


class UsuarioResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    username: str
    nombre_completo: str
    rol_id: uuid.UUID
    activo: bool
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True


class UsuarioListResponseSchema(BaseModel):
    items: list[UsuarioResponseSchema]
    page: int
    page_size: int
    total: int
