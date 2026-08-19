import uuid


async def _seeded_sucursal_id(client) -> str:
    response = await client.get("/api/v1/sucursales")
    return response.json()["items"][0]["id"]


async def test_create_venta_happy_path(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.post(
        "/api/v1/ventas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "F-001",
            "fecha": "2026-08-19T12:00:00Z",
            "subtotal": "100.00",
            "descuento": "0.00",
            "impuestos": "0.00",
            "total": "100.00",
            "metodo_pago": "efectivo",
            "estado": "completada",
            "lineas": [
                {
                    "articulo_id": str(uuid.uuid4()),
                    "cantidad": "2",
                    "precio_unitario": "50.00",
                    "subtotal": "100.00",
                }
            ],
        },
    )

    assert response.status_code == 201
    body = response.json()
    assert body["folio"] == "F-001"
    assert body["sucursal_id"] == sucursal_id
    assert body["is_synced"] is True
    assert body["id"] is not None
    assert len(body["lineas"]) == 1
    assert body["lineas"][0]["cantidad"] == "2.000"


async def test_create_venta_without_lineas_returns_422(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.post(
        "/api/v1/ventas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "F-002",
            "fecha": "2026-08-19T12:00:00Z",
            "subtotal": "0.00",
            "total": "0.00",
            "metodo_pago": "efectivo",
            "lineas": [],
        },
    )

    assert response.status_code == 422
