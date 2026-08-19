import uuid


async def _seeded_sucursal_id(client) -> str:
    response = await client.get("/api/v1/sucursales")
    return response.json()["items"][0]["id"]


async def test_create_entrada_articulo_nuevo_happy_path(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.post(
        "/api/v1/entradas",
        json={
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


async def test_create_entrada_articulo_existente_increments_inventario(client):
    sucursal_id = await _seeded_sucursal_id(client)

    primera = await client.post(
        "/api/v1/entradas",
        json={
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

    segunda = await client.post(
        "/api/v1/entradas",
        json={
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


async def test_create_entrada_sin_articulo_id_ni_articulo_nuevo_returns_422(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.post(
        "/api/v1/entradas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": "5",
        },
    )

    assert response.status_code == 422


async def test_create_entrada_articulo_id_inexistente_returns_404(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.post(
        "/api/v1/entradas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": "5",
            "articulo_id": str(uuid.uuid4()),
        },
    )

    assert response.status_code == 404
