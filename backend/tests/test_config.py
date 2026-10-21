import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.config import JWT_SECRET_KEY_DESARROLLO, cargar_jwt_secret_key, opciones_docs

# PLAN.md Parte 37, A-4 y M-4: entorno del backend.


def test_sin_jwt_secret_key_no_arranca():
    with pytest.raises(RuntimeError, match="JWT_SECRET_KEY es obligatoria"):
        cargar_jwt_secret_key({"PDV_ENV": "development"})


def test_clave_de_desarrollo_fuera_de_development_no_arranca():
    with pytest.raises(RuntimeError, match="valor de desarrollo"):
        cargar_jwt_secret_key({"JWT_SECRET_KEY": JWT_SECRET_KEY_DESARROLLO})


def test_clave_de_desarrollo_se_acepta_en_development():
    env = {"JWT_SECRET_KEY": JWT_SECRET_KEY_DESARROLLO, "PDV_ENV": "development"}
    assert cargar_jwt_secret_key(env) == JWT_SECRET_KEY_DESARROLLO


def test_clave_propia_se_acepta_en_produccion():
    assert cargar_jwt_secret_key({"JWT_SECRET_KEY": "clave-real"}) == "clave-real"


@pytest.mark.parametrize("ruta", ["/docs", "/redoc", "/openapi.json"])
async def test_docs_no_existen_en_produccion(ruta):
    app = FastAPI(**opciones_docs("production"))
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        assert (await client.get(ruta)).status_code == 404


async def test_docs_existen_en_development():
    app = FastAPI(**opciones_docs("development"))
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        assert (await client.get("/openapi.json")).status_code == 200
