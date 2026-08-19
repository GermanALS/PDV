import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class SucursalCreateSchema(BaseModel):
    local_id: uuid.UUID | None = None
    nombre: str = Field(min_length=1, max_length=120)
    direccion: str | None = None
    activa: bool = True


class SucursalResponseSchema(BaseModel):
    id: uuid.UUID
    local_id: uuid.UUID | None
    nombre: str
    direccion: str | None
    activa: bool
    updated_at: datetime
    deleted_at: datetime | None
    is_synced: bool = True


class SucursalListResponseSchema(BaseModel):
    items: list[SucursalResponseSchema]
    page: int
    page_size: int
    total: int
