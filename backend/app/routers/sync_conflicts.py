import uuid

from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.sync_conflict import SyncConflict
from app.schemas.sync_conflict import (
    SyncConflictCreateSchema,
    SyncConflictListResponseSchema,
    SyncConflictResponseSchema,
)

router = APIRouter(tags=["sync-conflicts"])


def _to_response(conflict: SyncConflict) -> SyncConflictResponseSchema:
    return SyncConflictResponseSchema(
        id=conflict.id,
        entidad=conflict.entidad,
        entidad_local_id=conflict.entidad_local_id,
        sucursal_id=conflict.sucursal_id,
        valor_local=conflict.valor_local,
        valor_remoto=conflict.valor_remoto,
        valor_resuelto=conflict.valor_resuelto,
        politica_aplicada=conflict.politica_aplicada,
        resuelto_automaticamente=conflict.resuelto_automaticamente,
        fecha_deteccion=conflict.fecha_deteccion,
    )


@router.get("/sync-conflicts", response_model=SyncConflictListResponseSchema)
async def list_sync_conflicts(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    sucursal_id: uuid.UUID | None = Query(default=None),
    resuelto_automaticamente: bool | None = Query(default=None),
    db: AsyncSession = Depends(get_db),
) -> SyncConflictListResponseSchema:
    filters = []
    if sucursal_id is not None:
        filters.append(SyncConflict.sucursal_id == sucursal_id)
    if resuelto_automaticamente is not None:
        filters.append(SyncConflict.resuelto_automaticamente == resuelto_automaticamente)

    total = await db.scalar(select(func.count()).select_from(SyncConflict).where(*filters))
    result = await db.execute(
        select(SyncConflict)
        .where(*filters)
        .order_by(SyncConflict.fecha_deteccion.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
    )
    items = [_to_response(conflict) for conflict in result.scalars().all()]
    return SyncConflictListResponseSchema(items=items, page=page, page_size=page_size, total=total or 0)


@router.post("/sync-conflicts", response_model=SyncConflictResponseSchema, status_code=201)
async def create_sync_conflict(
    payload: SyncConflictCreateSchema,
    response: Response,
    db: AsyncSession = Depends(get_db),
) -> SyncConflictResponseSchema:
    # Idempotente: el id lo genera el dispositivo, asi que un reintento de
    # sync que reenvia el mismo conflicto devuelve la fila existente en vez
    # de duplicarla o sobreescribirla (docs/api-contract.md 3.2).
    existing = await db.get(SyncConflict, payload.id)
    if existing is not None:
        response.status_code = 200
        return _to_response(existing)

    conflict = SyncConflict(**payload.model_dump())
    db.add(conflict)
    await db.commit()
    await db.refresh(conflict)
    return _to_response(conflict)
