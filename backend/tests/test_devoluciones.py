import uuid

from sqlalchemy import select

from app.models.devolucion import Devolucion


async def _seeded_sucursal_id(client_autenticado) -> str:
    response = await client_autenticado.get("/api/v1/sucursales")
    return response.json()["items"][0]["id"]


async def _articulo_con_existencia(client_autenticado, sucursal_id: str, sku: str, cantidad: str) -> str:
    response = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-20T12:00:00Z",
            "cantidad": cantidad,
            "articulo_nuevo": {
                "sku": sku,
                "nombre": "Articulo de prueba para devolucion",
                "unidad_medida": "pieza",
                "precio_venta": "50.00",
            },
        },
    )
    return response.json()["articulo"]["id"]


async def _venta_registrada(client_autenticado, sucursal_id: str, articulo_id: str, folio: str) -> str:
    response = await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": folio,
            "fecha": "2026-08-20T12:00:00Z",
            "subtotal": "50.00",
            "total": "50.00",
            "metodo_pago": "efectivo",
            "lineas": [
                {
                    "articulo_id": articulo_id,
                    "cantidad": "1",
                    "precio_unitario": "50.00",
                    "subtotal": "50.00",
                }
            ],
        },
    )
    return response.json()["id"]


async def test_create_devolucion_happy_path(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="DEV-001", cantidad="10")
    venta_id = await _venta_registrada(client_autenticado, sucursal_id, articulo_id, folio="F-DEV-001")

    response = await client_autenticado.post(
        "/api/v1/devoluciones",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "venta_id": venta_id,
            "folio": "D-001",
            "fecha": "2026-08-20T13:00:00Z",
            "lineas": [
                {
                    "articulo_id": articulo_id,
                    "cantidad": "1",
                    "motivo": "Producto dañado",
                    "condicion": "defectuoso",
                }
            ],
        },
    )

    assert response.status_code == 201
    body = response.json()
    assert body["folio"] == "D-001"
    assert body["sucursal_id"] == sucursal_id
    assert body["venta_id"] == venta_id
    assert body["estado"] == "registrada"
    assert body["is_synced"] is True
    assert body["id"] is not None
    assert len(body["lineas"]) == 1
    assert body["lineas"][0]["cantidad"] == "1.000"
    assert body["lineas"][0]["condicion"] == "defectuoso"


async def test_create_devolucion_sin_venta_original_happy_path(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="DEV-002", cantidad="5")

    response = await client_autenticado.post(
        "/api/v1/devoluciones",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "D-002",
            "fecha": "2026-08-20T13:00:00Z",
            "lineas": [
                {
                    "articulo_id": articulo_id,
                    "cantidad": "2",
                }
            ],
        },
    )

    assert response.status_code == 201
    body = response.json()
    assert body["venta_id"] is None
    assert body["lineas"][0]["motivo"] is None
    assert body["lineas"][0]["condicion"] is None


async def test_create_devolucion_without_lineas_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/devoluciones",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "D-003",
            "fecha": "2026-08-20T13:00:00Z",
            "lineas": [],
        },
    )

    assert response.status_code == 422


async def test_create_devolucion_articulo_id_inexistente_returns_404(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/devoluciones",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "D-004",
            "fecha": "2026-08-20T13:00:00Z",
            "lineas": [
                {
                    "articulo_id": str(uuid.uuid4()),
                    "cantidad": "1",
                }
            ],
        },
    )

    assert response.status_code == 404


async def test_create_devolucion_venta_id_inexistente_returns_404(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="DEV-005", cantidad="5")

    response = await client_autenticado.post(
        "/api/v1/devoluciones",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "venta_id": str(uuid.uuid4()),
            "folio": "D-005",
            "fecha": "2026-08-20T13:00:00Z",
            "lineas": [
                {
                    "articulo_id": articulo_id,
                    "cantidad": "1",
                }
            ],
        },
    )

    assert response.status_code == 404


async def test_create_devolucion_sin_local_id_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="DEV-006", cantidad="5")

    response = await client_autenticado.post(
        "/api/v1/devoluciones",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "D-006",
            "fecha": "2026-08-20T13:00:00Z",
            "lineas": [{"articulo_id": articulo_id, "cantidad": "1"}],
        },
    )

    assert response.status_code == 422


async def test_create_devolucion_reintento_con_mismo_local_id_es_idempotente(client_autenticado, session):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="DEV-007", cantidad="10")
    local_id = str(uuid.uuid4())
    payload = {
        "local_id": local_id,
        "sucursal_id": sucursal_id,
        "usuario_id": "admin",
        "folio": "D-007",
        "fecha": "2026-08-20T13:00:00Z",
        "lineas": [{"articulo_id": articulo_id, "cantidad": "2", "condicion": "defectuoso"}],
    }

    primera = await client_autenticado.post("/api/v1/devoluciones", json=payload)
    segunda = await client_autenticado.post("/api/v1/devoluciones", json=payload)

    assert primera.status_code == 201
    assert segunda.status_code == 200
    assert segunda.json()["id"] == primera.json()["id"]
    assert len(segunda.json()["lineas"]) == 1

    devoluciones = (
        await session.execute(select(Devolucion).where(Devolucion.local_id == uuid.UUID(local_id)))
    ).scalars().all()
    assert len(devoluciones) == 1


async def test_create_devolucion_sucursal_id_inexistente_returns_404(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="DEV-404", cantidad="5")

    response = await client_autenticado.post(
        "/api/v1/devoluciones",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": str(uuid.uuid4()),
            "usuario_id": "admin",
            "folio": "D-404",
            "fecha": "2026-08-20T13:00:00Z",
            "lineas": [{"articulo_id": articulo_id, "cantidad": "1"}],
        },
    )

    assert response.status_code == 404
