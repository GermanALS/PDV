import uuid
from contextlib import asynccontextmanager

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app
from app.models.rol import Rol
from app.models.usuario import Usuario
from app.security import create_access_token


@asynccontextmanager
async def _cliente_con_modulos(session, modulos: list[str]):
    rol = Rol(nombre=f"rol_{uuid.uuid4().hex[:8]}", modulos_permitidos=modulos)
    session.add(rol)
    await session.flush()
    usuario = Usuario(
        username=f"u_{uuid.uuid4().hex[:8]}", nombre_completo="Permisos Test", rol_id=rol.id
    )
    session.add(usuario)
    await session.flush()
    token = create_access_token(str(usuario.id))
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
        headers={"Authorization": f"Bearer {token}"},
    ) as ac:
        yield ac


# Escrituras y el modulo que exige cada una (api-contract.md sec. 12).
ESCRITURAS = [
    ("POST", "/api/v1/ventas", "venta"),
    ("POST", "/api/v1/entradas", "entrada"),
    ("PATCH", f"/api/v1/inventario/{uuid.uuid4()}", "inventario"),
    ("POST", "/api/v1/cortes-caja", "caja"),
    ("POST", "/api/v1/retiros-efectivo", "caja"),
    ("POST", "/api/v1/devoluciones", "devoluciones"),
    ("POST", "/api/v1/usuarios", "usuarios"),
    ("PATCH", f"/api/v1/usuarios/{uuid.uuid4()}", "usuarios"),
    ("DELETE", f"/api/v1/usuarios/{uuid.uuid4()}", "usuarios"),
    ("POST", "/api/v1/roles", "usuarios"),
    ("PATCH", f"/api/v1/roles/{uuid.uuid4()}", "usuarios"),
    ("DELETE", f"/api/v1/roles/{uuid.uuid4()}", "usuarios"),
    ("POST", "/api/v1/sucursales", "configuracion"),
]


@pytest.mark.parametrize("metodo,ruta,modulo", ESCRITURAS)
async def test_escritura_sin_el_modulo_devuelve_403(session, metodo, ruta, modulo):
    # Rol con todos los modulos salvo el que la ruta exige.
    otros = [m for m in ("venta", "entrada", "inventario", "caja", "devoluciones", "usuarios", "configuracion", "ia") if m != modulo]
    async with _cliente_con_modulos(session, otros) as client:
        response = await client.request(metodo, ruta, json={})
    assert response.status_code == 403
    assert response.json() == {"detail": f"el rol no tiene permiso para el modulo: {modulo}"}


async def test_post_usuarios_sin_modulo_usuarios_devuelve_403(session):
    # Criterio explicito del checklist.
    async with _cliente_con_modulos(session, ["venta"]) as client:
        response = await client.post(
            "/api/v1/usuarios",
            json={
                "local_id": None,
                "username": "nuevo",
                "nombre_completo": "Nuevo",
                "rol_id": str(uuid.uuid4()),
                "activo": True,
            },
        )
    assert response.status_code == 403
    assert response.json() == {"detail": "el rol no tiene permiso para el modulo: usuarios"}


async def test_escritura_con_el_modulo_no_devuelve_403(session):
    # Con el modulo, el 403 no aparece: la request llega al handler y falla
    # por validacion de negocio (422), no por permiso.
    async with _cliente_con_modulos(session, ["usuarios"]) as client:
        response = await client.post("/api/v1/usuarios", json={})
    assert response.status_code != 403
    assert response.status_code == 422


async def test_get_no_exige_modulo(session):
    # Un rol que solo tiene "configuracion" puede leer usuarios y roles:
    # las lecturas solo exigen token.
    async with _cliente_con_modulos(session, ["configuracion"]) as client:
        assert (await client.get("/api/v1/usuarios")).status_code == 200
        assert (await client.get("/api/v1/roles")).status_code == 200


async def test_post_sync_conflicts_no_exige_modulo(session):
    # Infraestructura del motor de sync: cualquier rol autenticado.
    async with _cliente_con_modulos(session, ["venta"]) as client:
        payload = {
            "local_id": str(uuid.uuid4()),
            "entidad": "inventario",
            "entidad_local_id": str(uuid.uuid4()),
            "sucursal_id": str(uuid.uuid4()),
            "politica": "last_write_wins",
            "payload_local": {},
            "payload_remoto": {},
            "detectado_en": "2026-09-02T12:00:00Z",
            "resuelto_automaticamente": True,
        }
        response = await client.post("/api/v1/sync-conflicts", json=payload)
    assert response.status_code != 403
