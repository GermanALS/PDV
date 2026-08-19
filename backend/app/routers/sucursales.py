from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.sucursal import Sucursal
from app.schemas.sucursal import SucursalCreateSchema, SucursalListResponseSchema, SucursalResponseSchema

router = APIRouter(tags=["sucursales"])


def _to_response(sucursal: Sucursal) -> SucursalResponseSchema:
    return SucursalResponseSchema(
        id=sucursal.id,
        local_id=sucursal.local_id,
        nombre=sucursal.nombre,
        direccion=sucursal.direccion,
        activa=sucursal.activa,
        updated_at=sucursal.updated_at,
        deleted_at=sucursal.deleted_at,
    )


@router.get("/sucursales", response_model=SucursalListResponseSchema)
async def list_sucursales(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
) -> SucursalListResponseSchema:
    total = await db.scalar(
        select(func.count()).select_from(Sucursal).where(Sucursal.deleted_at.is_(None))
    )
    result = await db.execute(
        select(Sucursal)
        .where(Sucursal.deleted_at.is_(None))
        .order_by(Sucursal.nombre)
        .offset((page - 1) * page_size)
        .limit(page_size)
    )
    items = [_to_response(sucursal) for sucursal in result.scalars().all()]
    return SucursalListResponseSchema(items=items, page=page, page_size=page_size, total=total or 0)


@router.post("/sucursales", response_model=SucursalResponseSchema, status_code=201)
async def create_sucursal(
    payload: SucursalCreateSchema,
    db: AsyncSession = Depends(get_db),
) -> SucursalResponseSchema:
    sucursal = Sucursal(**payload.model_dump())
    db.add(sucursal)
    await db.commit()
    await db.refresh(sucursal)
    return _to_response(sucursal)
