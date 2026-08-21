import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.usuario import Usuario
from app.schemas.usuario import (
    UsuarioCreateSchema,
    UsuarioListResponseSchema,
    UsuarioResponseSchema,
    UsuarioUpdateSchema,
)

router = APIRouter(tags=["usuarios"])


def _to_response(usuario: Usuario) -> UsuarioResponseSchema:
    return UsuarioResponseSchema(
        id=usuario.id,
        local_id=usuario.local_id,
        username=usuario.username,
        nombre_completo=usuario.nombre_completo,
        rol=usuario.rol,
        activo=usuario.activo,
        updated_at=usuario.updated_at,
        deleted_at=usuario.deleted_at,
    )


@router.get("/usuarios", response_model=UsuarioListResponseSchema)
async def list_usuarios(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
) -> UsuarioListResponseSchema:
    total = await db.scalar(select(func.count()).select_from(Usuario).where(Usuario.deleted_at.is_(None)))
    result = await db.execute(
        select(Usuario)
        .where(Usuario.deleted_at.is_(None))
        .order_by(Usuario.nombre_completo)
        .offset((page - 1) * page_size)
        .limit(page_size)
    )
    items = [_to_response(usuario) for usuario in result.scalars().all()]
    return UsuarioListResponseSchema(items=items, page=page, page_size=page_size, total=total or 0)


@router.post("/usuarios", response_model=UsuarioResponseSchema, status_code=201)
async def create_usuario(payload: UsuarioCreateSchema, db: AsyncSession = Depends(get_db)) -> UsuarioResponseSchema:
    usuario = Usuario(**payload.model_dump())
    db.add(usuario)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=409, detail=f"ya existe un usuario con username={payload.username}")
    await db.refresh(usuario)
    return _to_response(usuario)


@router.patch("/usuarios/{usuario_id}", response_model=UsuarioResponseSchema)
async def update_usuario(
    usuario_id: uuid.UUID,
    payload: UsuarioUpdateSchema,
    db: AsyncSession = Depends(get_db),
) -> UsuarioResponseSchema:
    usuario = await db.get(Usuario, usuario_id)
    if usuario is None or usuario.deleted_at is not None:
        raise HTTPException(status_code=404, detail="usuario no encontrado")

    usuario.username = payload.username
    usuario.nombre_completo = payload.nombre_completo
    usuario.rol = payload.rol
    usuario.activo = payload.activo
    usuario.updated_at = datetime.now(timezone.utc)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=409, detail=f"ya existe un usuario con username={payload.username}")
    await db.refresh(usuario)
    return _to_response(usuario)


@router.delete("/usuarios/{usuario_id}", status_code=204)
async def delete_usuario(usuario_id: uuid.UUID, db: AsyncSession = Depends(get_db)) -> None:
    usuario = await db.get(Usuario, usuario_id)
    if usuario is None or usuario.deleted_at is not None:
        raise HTTPException(status_code=404, detail="usuario no encontrado")
    usuario.deleted_at = datetime.now(timezone.utc)
    await db.commit()
