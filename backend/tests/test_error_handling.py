import logging

from httpx import ASGITransport, AsyncClient

from app.database import get_db
from app.main import app


async def _get_db_que_falla():
    raise RuntimeError("fallo inesperado de prueba")


async def test_excepcion_no_prevista_devuelve_detail_consistente(caplog):
    # M-5 (docs/review_code.md): antes de este handler, una excepcion no
    # prevista propagaba como 500 con traceback. Se fuerza forzando que
    # get_db falle en la resolucion de dependencias - dispara antes de
    # llegar a cualquier logica de negocio, sin depender de un endpoint
    # concreto.
    app.dependency_overrides[get_db] = _get_db_que_falla
    try:
        # raise_app_exceptions=False: Starlette's ServerErrorMiddleware
        # siempre relanza la excepcion original despues de enviar la
        # respuesta del handler (para que el servidor ASGI real la loguee) -
        # sin este flag, httpx la propagaria en el propio test en vez de
        # devolver la response ya enviada al cliente.
        transport = ASGITransport(app=app, raise_app_exceptions=False)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            with caplog.at_level(logging.ERROR):
                response = await client.get("/api/v1/sucursales")
    finally:
        app.dependency_overrides.pop(get_db, None)

    assert response.status_code == 500
    assert response.json() == {"detail": "error interno del servidor"}
    assert any(record.levelno == logging.ERROR for record in caplog.records)


async def test_cors_preflight_responde_con_headers(client):
    response = await client.options(
        "/api/v1/sucursales",
        headers={
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "GET",
        },
    )

    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "*"
