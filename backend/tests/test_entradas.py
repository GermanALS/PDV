import uuid

from sqlalchemy import select

from app.models.articulo import Articulo
from app.models.movimiento import Movimiento


async def _seeded_sucursal_id(client_autenticado) -> str:
    response = await client_autenticado.get("/api/v1/sucursales")
    return response.json()["items"][0]["id"]


async def test_create_entrada_articulo_nuevo_happy_path(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": "25",
            "ubicacion": "Estante B2",
            "articulo_nuevo": {
                "sku": "NEW-001",
                "nombre": "Articulo nuevo de prueba",
                "unidad_medida": "pieza",
                "precio_venta": "15.00",
                "costo": "9.00",
            },
        },
    )

    assert response.status_code == 201
    body = response.json()
    assert body["articulo"]["sku"] == "NEW-001"
    assert body["articulo"]["is_synced"] is True
    assert body["inventario"]["cantidad"] == "25.000"
    assert body["inventario"]["sucursal_id"] == sucursal_id
    assert body["movimiento"]["tipo"] == "entrada"
    assert body["movimiento"]["referencia_tipo"] == "entrada_manual"


async def test_create_entrada_articulo_existente_increments_inventario(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    primera = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": "10",
            "articulo_nuevo": {
                "sku": "NEW-002",
                "nombre": "Articulo existente de prueba",
                "unidad_medida": "pieza",
                "precio_venta": "20.00",
            },
        },
    )
    articulo_id = primera.json()["articulo"]["id"]

    segunda = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "user1",
            "fecha": "2026-08-19T13:00:00Z",
            "cantidad": "5",
            "articulo_id": articulo_id,
        },
    )

    assert segunda.status_code == 201
    body = segunda.json()
    assert body["articulo"] is None
    assert body["inventario"]["cantidad"] == "15.000"
    assert body["inventario"]["articulo_id"] == articulo_id


async def test_create_entrada_sin_articulo_id_ni_articulo_nuevo_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": "5",
        },
    )

    assert response.status_code == 422


async def test_create_entrada_articulo_id_inexistente_returns_404(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": "5",
            "articulo_id": str(uuid.uuid4()),
        },
    )

    assert response.status_code == 404


async def test_create_entrada_sin_local_id_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": "5",
            "articulo_id": str(uuid.uuid4()),
        },
    )

    assert response.status_code == 422


async def test_create_entrada_reintento_con_mismo_local_id_es_idempotente(client_autenticado, session):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    payload = {
        "local_id": str(uuid.uuid4()),
        "sucursal_id": sucursal_id,
        "usuario_id": "admin",
        "fecha": "2026-08-19T12:00:00Z",
        "cantidad": "25",
        "articulo_nuevo": {
            "sku": "NEW-003",
            "nombre": "Articulo de prueba de idempotencia",
            "unidad_medida": "pieza",
            "precio_venta": "15.00",
        },
    }

    primera = await client_autenticado.post("/api/v1/entradas", json=payload)
    segunda = await client_autenticado.post("/api/v1/entradas", json=payload)

    assert primera.status_code == 201
    assert segunda.status_code == 200
    assert segunda.json()["movimiento"]["id"] == primera.json()["movimiento"]["id"]
    assert segunda.json()["articulo"]["id"] == primera.json()["articulo"]["id"]
    assert segunda.json()["inventario"]["cantidad"] == "25.000"

    movimientos = (
        await session.execute(
            select(Movimiento).where(Movimiento.local_id == uuid.UUID(payload["local_id"]))
        )
    ).scalars().all()
    assert len(movimientos) == 1

    articulos = (
        await session.execute(select(Articulo).where(Articulo.sku == "NEW-003"))
    ).scalars().all()
    assert len(articulos) == 1


async def test_create_entrada_articulo_existente_reintento_con_mismo_local_id_es_idempotente(
    client_autenticado, session
):
    # Rama distinta de la del test anterior: articulo_id existente + fila de
    # inventario ya existente (incremento, no alta) - entradas.py linea 136.
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    primera_entrada = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": "10",
            "articulo_nuevo": {
                "sku": "NEW-004",
                "nombre": "Articulo existente para idempotencia",
                "unidad_medida": "pieza",
                "precio_venta": "20.00",
            },
        },
    )
    articulo_id = primera_entrada.json()["articulo"]["id"]

    payload = {
        "local_id": str(uuid.uuid4()),
        "sucursal_id": sucursal_id,
        "usuario_id": "admin",
        "fecha": "2026-08-19T13:00:00Z",
        "cantidad": "5",
        "articulo_id": articulo_id,
    }

    primera = await client_autenticado.post("/api/v1/entradas", json=payload)
    segunda = await client_autenticado.post("/api/v1/entradas", json=payload)

    assert primera.status_code == 201
    assert segunda.status_code == 200
    assert segunda.json()["movimiento"]["id"] == primera.json()["movimiento"]["id"]
    assert segunda.json()["articulo"] is None
    assert segunda.json()["inventario"]["cantidad"] == "15.000"

    movimientos = (
        await session.execute(
            select(Movimiento).where(Movimiento.local_id == uuid.UUID(payload["local_id"]))
        )
    ).scalars().all()
    assert len(movimientos) == 1
