from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.usuario import Usuario
from app.routers.usuarios import usuario_to_response
from app.schemas.auth import AuthLoginRequestSchema, AuthLoginResponseSchema
from app.security import create_access_token, verify_password

router = APIRouter(tags=["auth"])

_CREDENCIALES_INVALIDAS = HTTPException(status_code=401, detail="credenciales invalidas")


@router.post("/auth/login", response_model=AuthLoginResponseSchema)
async def login(payload: AuthLoginRequestSchema, db: AsyncSession = Depends(get_db)) -> AuthLoginResponseSchema:
    result = await db.execute(
        select(Usuario).where(Usuario.username == payload.username, Usuario.deleted_at.is_(None))
    )
    usuario = result.scalar_one_or_none()

    # Mismo detalle 401 generico sin importar la causa (usuario inexistente,
    # inactivo, sin password_hash asignado, o password incorrecta) - evita
    # que la respuesta permita enumerar usernames validos.
    if usuario is None or not usuario.activo or usuario.password_hash is None:
        raise _CREDENCIALES_INVALIDAS
    if not verify_password(payload.password, usuario.password_hash):
        raise _CREDENCIALES_INVALIDAS

    access_token = create_access_token(str(usuario.id))
    return AuthLoginResponseSchema(access_token=access_token, usuario=usuario_to_response(usuario))
