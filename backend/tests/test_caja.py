async def _seeded_sucursal_id(client) -> str:
    response = await client.get("/api/v1/sucursales")
    return response.json()["items"][0]["id"]


async def test_create_corte_caja_happy_path(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.post(
        "/api/v1/cortes-caja",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "tipo": "parcial",
            "fecha_inicio": "2026-08-20T08:00:00Z",
            "fecha_fin": "2026-08-20T14:00:00Z",
            "total_ventas": "1500.00",
            "total_efectivo": "900.00",
            "total_tarjeta": "600.00",
            "total_retiros": "100.00",
            "monto_esperado": "800.00",
            "monto_contado": "795.00",
            "diferencia": "-5.00",
        },
    )

    assert response.status_code == 201
    body = response.json()
    assert body["tipo"] == "parcial"
    assert body["sucursal_id"] == sucursal_id
    assert body["monto_esperado"] == "800.00"
    assert body["is_synced"] is True
    assert body["id"] is not None


async def test_create_corte_caja_tipo_invalido_returns_422(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.post(
        "/api/v1/cortes-caja",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "tipo": "semanal",
            "fecha_inicio": "2026-08-20T08:00:00Z",
            "fecha_fin": "2026-08-20T14:00:00Z",
            "total_ventas": "1500.00",
            "total_efectivo": "900.00",
            "total_tarjeta": "600.00",
            "monto_esperado": "900.00",
        },
    )

    assert response.status_code == 422


async def test_create_retiro_efectivo_happy_path(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.post(
        "/api/v1/retiros-efectivo",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "monto": "100.00",
            "motivo": "Pago a proveedor",
            "fecha": "2026-08-20T11:00:00Z",
        },
    )

    assert response.status_code == 201
    body = response.json()
    assert body["monto"] == "100.00"
    assert body["motivo"] == "Pago a proveedor"
    assert body["is_synced"] is True
    assert body["id"] is not None


async def test_create_retiro_efectivo_monto_invalido_returns_422(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.post(
        "/api/v1/retiros-efectivo",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "monto": "0.00",
            "fecha": "2026-08-20T11:00:00Z",
        },
    )

    assert response.status_code == 422


async def test_get_totales_corte_happy_path(client):
    # Periodo en una fecha lejos de cualquier dato real/de verificacion
    # manual (evita colisionar con filas de otras corridas contra la misma
    # base de desarrollo, ya que este endpoint agrega por rango de fecha
    # sin filtrar por ningun id propio del test).
    sucursal_id = await _seeded_sucursal_id(client)

    articulo_response = await client.post(
        "/api/v1/entradas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2099-01-01T07:00:00Z",
            "cantidad": "10",
            "articulo_nuevo": {
                "sku": "TOT-001",
                "nombre": "Articulo de prueba para totales de corte",
                "unidad_medida": "pieza",
                "precio_venta": "100.00",
            },
        },
    )
    articulo_id = articulo_response.json()["articulo"]["id"]

    await client.post(
        "/api/v1/ventas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "F-TOT-1",
            "fecha": "2099-01-01T09:00:00Z",
            "subtotal": "100.00",
            "total": "100.00",
            "metodo_pago": "efectivo",
            "lineas": [
                {
                    "articulo_id": articulo_id,
                    "cantidad": "1",
                    "precio_unitario": "100.00",
                    "subtotal": "100.00",
                }
            ],
        },
    )
    await client.post(
        "/api/v1/retiros-efectivo",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "monto": "50.00",
            "fecha": "2099-01-01T11:00:00Z",
        },
    )

    response = await client.get(
        "/api/v1/cortes-caja/totales",
        params={
            "sucursal_id": sucursal_id,
            "fecha_inicio": "2099-01-01T08:00:00Z",
            "fecha_fin": "2099-01-01T14:00:00Z",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["total_ventas"] == "100.00"
    assert body["total_efectivo"] == "100.00"
    assert body["total_tarjeta"] == "0"
    assert body["total_retiros"] == "50.00"
    assert body["monto_esperado"] == "50.00"


async def test_get_totales_corte_falta_sucursal_id_returns_422(client):
    response = await client.get(
        "/api/v1/cortes-caja/totales",
        params={
            "fecha_inicio": "2026-08-20T08:00:00Z",
            "fecha_fin": "2026-08-20T14:00:00Z",
        },
    )

    assert response.status_code == 422
