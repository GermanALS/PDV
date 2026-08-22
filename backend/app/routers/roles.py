import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.rol import Rol
from app.schemas.rol import (
    RolCreateSchema,
    RolListResponseSchema,
    RolResponseSchema,
    RolUpdateSchema,
)

router = APIRouter(tags=["roles"])


def _to_response(rol: Rol) -> RolResponseSchema:
    return RolResponseSchema(
        id=rol.id,
        local_id=rol.local_id,
        nombre=rol.nombre,
        modulos_permitidos=rol.modulos_permitidos,
        es_sistema=rol.es_sistema,
        updated_at=rol.updated_at,
        deleted_at=rol.deleted_at,
    )


@router.get("/roles", response_model=RolListResponseSchema)
async def list_roles(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
) -> RolListResponseSchema:
    total = await db.scalar(select(func.count()).select_from(Rol).where(Rol.deleted_at.is_(None)))
    result = await db.execute(
        select(Rol)
        .where(Rol.deleted_at.is_(None))
        .order_by(Rol.nombre)
        .offset((page - 1) * page_size)
        .limit(page_size)
    )
    items = [_to_response(rol) for rol in result.scalars().all()]
    return RolListResponseSchema(items=items, page=page, page_size=page_size, total=total or 0)


@router.post("/roles", response_model=RolResponseSchema, status_code=201)
async def create_rol(payload: RolCreateSchema, db: AsyncSession = Depends(get_db)) -> RolResponseSchema:
    rol = Rol(**payload.model_dump(), es_sistema=False)
    db.add(rol)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=409, detail=f"ya existe un rol con nombre={payload.nombre}")
    await db.refresh(rol)
    return _to_response(rol)


@router.patch("/roles/{rol_id}", response_model=RolResponseSchema)
async def update_rol(
    rol_id: uuid.UUID,
    payload: RolUpdateSchema,
    db: AsyncSession = Depends(get_db),
) -> RolResponseSchema:
    rol = await db.get(Rol, rol_id)
    if rol is None or rol.deleted_at is not None:
        raise HTTPException(status_code=404, detail="rol no encontrado")
    if rol.es_sistema:
        raise HTTPException(status_code=400, detail="no se puede modificar un rol de sistema")

    rol.nombre = payload.nombre
    rol.modulos_permitidos = payload.modulos_permitidos
    rol.updated_at = datetime.now(timezone.utc)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=409, detail=f"ya existe un rol con nombre={payload.nombre}")
    await db.refresh(rol)
    return _to_response(rol)


@router.delete("/roles/{rol_id}", status_code=204)
async def delete_rol(rol_id: uuid.UUID, db: AsyncSession = Depends(get_db)) -> None:
    rol = await db.get(Rol, rol_id)
    if rol is None or rol.deleted_at is not None:
        raise HTTPException(status_code=404, detail="rol no encontrado")
    if rol.es_sistema:
        raise HTTPException(status_code=400, detail="no se puede eliminar un rol de sistema")
    rol.deleted_at = datetime.now(timezone.utc)
    await db.commit()
