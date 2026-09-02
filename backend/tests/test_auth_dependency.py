import uuid
from datetime import datetime, timedelta, timezone

import jwt
import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.dependencies import usuario_actual
from app.models.rol import Rol
from app.models.usuario import Usuario
from app.security import JWT_ALGORITHM, JWT_SECRET_KEY, create_access_token


def _cred(token: str) -> HTTPAuthorizationCredentials:
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)


async def _crear_usuario(session, *, activo: bool = True, borrado: bool = False) -> Usuario:
    rol = Rol(nombre=f"rol_{uuid.uuid4().hex[:8]}", modulos_permitidos=["venta"])
    session.add(rol)
    await session.flush()
    usuario = Usuario(
        username=f"user_{uuid.uuid4().hex[:8]}",
        nombre_completo="Dep Test",
        rol_id=rol.id,
        activo=activo,
        deleted_at=datetime.now(timezone.utc) if borrado else None,
    )
    session.add(usuario)
    await session.flush()
    return usuario


async def test_token_valido_devuelve_usuario(session):
    usuario = await _crear_usuario(session)
    resultado = await usuario_actual(_cred(create_access_token(str(usuario.id))), session)
    assert resultado.id == usuario.id


async def test_sin_credenciales_lanza_401(session):
    with pytest.raises(HTTPException) as exc:
        await usuario_actual(None, session)
    assert exc.value.status_code == 401


async def test_token_expirado_lanza_401(session):
    usuario = await _crear_usuario(session)
    token = jwt.encode(
        {"sub": str(usuario.id), "exp": datetime.now(timezone.utc) - timedelta(minutes=1)},
        JWT_SECRET_KEY,
        algorithm=JWT_ALGORITHM,
    )
    with pytest.raises(HTTPException) as exc:
        await usuario_actual(_cred(token), session)
    assert exc.value.status_code == 401


async def test_firma_invalida_lanza_401(session):
    usuario = await _crear_usuario(session)
    token = jwt.encode(
        {"sub": str(usuario.id), "exp": datetime.now(timezone.utc) + timedelta(minutes=5)},
        "otra-clave-distinta-suficientemente-larga-para-hs256",
        algorithm=JWT_ALGORITHM,
    )
    with pytest.raises(HTTPException) as exc:
        await usuario_actual(_cred(token), session)
    assert exc.value.status_code == 401


async def test_usuario_borrado_lanza_401(session):
    usuario = await _crear_usuario(session, borrado=True)
    with pytest.raises(HTTPException) as exc:
        await usuario_actual(_cred(create_access_token(str(usuario.id))), session)
    assert exc.value.status_code == 401


async def test_usuario_inactivo_lanza_401(session):
    usuario = await _crear_usuario(session, activo=False)
    with pytest.raises(HTTPException) as exc:
        await usuario_actual(_cred(create_access_token(str(usuario.id))), session)
    assert exc.value.status_code == 401


async def test_sub_inexistente_lanza_401(session):
    with pytest.raises(HTTPException) as exc:
        await usuario_actual(_cred(create_access_token(str(uuid.uuid4()))), session)
    assert exc.value.status_code == 401
