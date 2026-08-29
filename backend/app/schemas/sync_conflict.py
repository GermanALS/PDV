import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

PoliticaAplicada = Literal["last_write_wins", "evento_aditivo"]


class SyncConflictCreateSchema(BaseModel):
    id: uuid.UUID
    entidad: str = Field(min_length=1, max_length=120)
    entidad_local_id: uuid.UUID
    sucursal_id: uuid.UUID | None = None
    valor_local: dict
    valor_remoto: dict
    valor_resuelto: dict
    politica_aplicada: PoliticaAplicada
    resuelto_automaticamente: bool
    fecha_deteccion: datetime


class SyncConflictResponseSchema(BaseModel):
    id: uuid.UUID
    entidad: str
    entidad_local_id: uuid.UUID
    sucursal_id: uuid.UUID | None
    valor_local: dict
    valor_remoto: dict
    valor_resuelto: dict
    politica_aplicada: str
    resuelto_automaticamente: bool
    fecha_deteccion: datetime


class SyncConflictListResponseSchema(BaseModel):
    items: list[SyncConflictResponseSchema]
    page: int
    page_size: int
    total: int
