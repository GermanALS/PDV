from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.dependencies import usuario_del_token
from app.limite_login import limite_login
from app.models.usuario import Usuario
from app.routers.usuarios import usuario_to_response
from app.schemas.auth import (
    AuthCambiarPasswordRequestSchema,
    AuthLoginRequestSchema,
    AuthLoginResponseSchema,
)
from app.security import create_access_token, hash_password, verify_password

router = APIRouter(tags=["auth"])

_CREDENCIALES_INVALIDAS = HTTPException(status_code=401, detail="credenciales invalidas")
_DEMASIADOS_INTENTOS = HTTPException(status_code=429, detail="demasiados intentos fallidos, intenta mas tarde")

# Se verifica contra este hash cuando no hay uno real: sin el, un username
# inexistente responde ~10 veces mas rapido y la diferencia los enumera
# (PLAN.md Parte 37, M-4).
_HASH_FICTICIO = hash_password("hash-ficticio-de-tiempo-constante")


@router.post("/auth/login", response_model=AuthLoginResponseSchema)
async def login(payload: AuthLoginRequestSchema, db: AsyncSession = Depends(get_db)) -> AuthLoginResponseSchema:
    if limite_login.bloqueado(payload.username):
        raise _DEMASIADOS_INTENTOS
    result = await db.execute(
        select(Usuario).where(Usuario.username == payload.username, Usuario.deleted_at.is_(None))
    )
    usuario = result.scalar_one_or_none()

    # Mismo detalle 401 generico, y el mismo costo bcrypt, sin importar la
    # causa (usuario inexistente, inactivo, sin password_hash asignado, o
    # password incorrecta) - evita enumerar usernames validos.
    habilitado = usuario is not None and usuario.activo and usuario.password_hash is not None
    password_hash = usuario.password_hash if habilitado else _HASH_FICTICIO
    if not verify_password(payload.password, password_hash) or not habilitado:
        limite_login.registrar_fallo(payload.username)
        raise _CREDENCIALES_INVALIDAS
    limite_login.reiniciar(payload.username)

    access_token = create_access_token(str(usuario.id))
    return AuthLoginResponseSchema(access_token=access_token, usuario=usuario_to_response(usuario))


@router.post("/auth/cambiar-password", status_code=204)
async def cambiar_password(
    payload: AuthCambiarPasswordRequestSchema,
    usuario: Usuario = Depends(usuario_del_token),
    db: AsyncSession = Depends(get_db),
) -> Response:
    """Cambia la contrasena del usuario del token y apaga
    `debe_cambiar_password`; unica ruta que acepta el token de un usuario con
    la marca encendida (api-contract.md sec. 2.2)."""
    usuario.password_hash = payload.password_hash
    usuario.debe_cambiar_password = False
    usuario.updated_at = datetime.now(timezone.utc)
    await db.commit()
    return Response(status_code=204)
