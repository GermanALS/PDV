import re

import pytest
from fastapi.routing import APIRoute

from app.main import app

_UUID_DUMMY = "00000000-0000-0000-0000-000000000000"

# Toda ruta montada bajo el router `protected` de app.main: sin header
# Authorization debe responder 401 antes de llegar al handler. Se listan
# todos los endpoints existentes (api-contract.md sec. 12).
RUTAS_PROTEGIDAS = [
    ("GET", "/api/v1/sucursales"),
    ("POST", "/api/v1/sucursales"),
    ("GET", "/api/v1/sync-conflicts"),
    ("POST", "/api/v1/sync-conflicts"),
    ("POST", "/api/v1/ventas"),
    ("POST", "/api/v1/entradas"),
    ("GET", "/api/v1/inventario"),
    ("GET", "/api/v1/inventario/ubicaciones"),
    ("PATCH", "/api/v1/inventario/00000000-0000-0000-0000-000000000000"),
    ("GET", "/api/v1/articulos/categorias"),
    ("GET", "/api/v1/articulos/unidades-medida"),
    ("POST", "/api/v1/cortes-caja"),
    ("GET", "/api/v1/cortes-caja"),
    ("GET", "/api/v1/cortes-caja/totales"),
    ("POST", "/api/v1/retiros-efectivo"),
    ("GET", "/api/v1/retiros-efectivo"),
    ("POST", "/api/v1/devoluciones"),
    ("GET", "/api/v1/roles"),
    ("POST", "/api/v1/roles"),
    ("PATCH", "/api/v1/roles/00000000-0000-0000-0000-000000000000"),
    ("DELETE", "/api/v1/roles/00000000-0000-0000-0000-000000000000"),
    ("GET", "/api/v1/usuarios"),
    ("POST", "/api/v1/usuarios"),
    ("PATCH", "/api/v1/usuarios/00000000-0000-0000-0000-000000000000"),
    ("DELETE", "/api/v1/usuarios/00000000-0000-0000-0000-000000000000"),
]


@pytest.mark.parametrize("metodo,ruta", RUTAS_PROTEGIDAS)
async def test_sin_token_devuelve_401(client, metodo, ruta):
    response = await client.request(metodo, ruta)
    assert response.status_code == 401
    assert response.json() == {"detail": "token invalido o expirado"}


@pytest.mark.parametrize("metodo,ruta", RUTAS_PROTEGIDAS)
async def test_token_invalido_devuelve_401(client, metodo, ruta):
    response = await client.request(
        metodo, ruta, headers={"Authorization": "Bearer no-es-un-jwt"}
    )
    assert response.status_code == 401


@pytest.mark.parametrize("ruta", ["/api/v1/health", "/api/v1/auth/login"])
async def test_rutas_exentas_no_exigen_token(client, ruta):
    # No 401: /health responde 200; /auth/login sin body responde 422
    # (validacion), nunca 401 por falta de header.
    response = await client.request("POST" if "login" in ruta else "GET", ruta)
    assert response.status_code != 401


def _rutas_hoja(routes):
    """Recorre las rutas incluyendo las de routers anidados via
    include_router (Starlette 1.x las envuelve en _IncludedRouter en vez de
    aplanarlas en app.routes). `route.path` es el path local del router,
    sin el prefijo /api/v1."""
    for route in routes:
        if isinstance(route, APIRoute):
            yield route
        elif hasattr(route, "original_router"):
            yield from _rutas_hoja(route.original_router.routes)


async def test_ninguna_ruta_no_exenta_responde_sin_token(client):
    """Guarda de regresion auto-descubierta: recorre todas las rutas
    montadas (no una lista a mano) y verifica que cada una fuera de
    /health y /auth responde 401 sin header."""
    hojas = list(_rutas_hoja(app.routes))
    assert len(hojas) >= len(RUTAS_PROTEGIDAS)

    fallos = []
    for route in hojas:
        if route.path == "/health" or route.path.startswith("/auth"):
            continue
        ruta = "/api/v1" + re.sub(r"\{[^}]+\}", _UUID_DUMMY, route.path)
        for metodo in route.methods - {"HEAD", "OPTIONS"}:
            response = await client.request(metodo, ruta)
            if response.status_code != 401:
                fallos.append(f"{metodo} {ruta} -> {response.status_code}")
    assert not fallos, f"rutas sin 401 sin token: {fallos}"
