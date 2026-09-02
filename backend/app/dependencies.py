import uuid

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.database import get_db
from app.models.usuario import Usuario
from app.security import JWT_ALGORITHM, JWT_SECRET_KEY

_bearer = HTTPBearer(auto_error=False)

# Detalle generico unico para toda falla de enforcement (api-contract.md
# sec. 12): no se distingue token ausente, firma invalida, expirado o
# usuario inactivo, igual que el 401 de /auth/login no enumera usernames.
_TOKEN_INVALIDO = HTTPException(status_code=401, detail="token invalido o expirado")


async def usuario_actual(
    credenciales: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: AsyncSession = Depends(get_db),
) -> Usuario:
    """Valida el JWT del header Authorization: Bearer y devuelve el Usuario.

    Lanza 401 ante token ausente, firma invalida, expirado, o cuyo `sub` no
    corresponde a un usuario existente, activo y no borrado.
    """
    if credenciales is None:
        raise _TOKEN_INVALIDO
    try:
        payload = jwt.decode(credenciales.credentials, JWT_SECRET_KEY, algorithms=[JWT_ALGORITHM])
        usuario_id = uuid.UUID(payload["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise _TOKEN_INVALIDO

    usuario = await db.scalar(
        select(Usuario)
        .options(selectinload(Usuario.rol))
        .where(Usuario.id == usuario_id, Usuario.deleted_at.is_(None))
    )
    if usuario is None or not usuario.activo:
        raise _TOKEN_INVALIDO
    return usuario
