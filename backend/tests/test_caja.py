import uuid

from sqlalchemy import select

from app.models.caja import CorteCaja, RetiroEfectivo


async def _seeded_sucursal_id(client_autenticado) -> str:
    response = await client_autenticado.get("/api/v1/sucursales")
    return response.json()["items"][0]["id"]


async def test_create_corte_caja_happy_path(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/cortes-caja",
        json={
            "local_id": str(uuid.uuid4()),
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


async def test_create_corte_caja_tipo_invalido_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/cortes-caja",
        json={
            "local_id": str(uuid.uuid4()),
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


async def test_create_retiro_efectivo_happy_path(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/retiros-efectivo",
        json={
            "local_id": str(uuid.uuid4()),
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


async def test_create_retiro_efectivo_monto_invalido_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/retiros-efectivo",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "monto": "0.00",
            "fecha": "2026-08-20T11:00:00Z",
        },
    )

    assert response.status_code == 422


async def test_get_totales_corte_happy_path(client_autenticado):
    # Periodo en una fecha lejos de cualquier dato real/de verificacion
    # manual (evita colisionar con filas de otras corridas contra la misma
    # base de desarrollo, ya que este endpoint agrega por rango de fecha
    # sin filtrar por ningun id propio del test).
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    articulo_response = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "local_id": str(uuid.uuid4()),
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

    await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "local_id": str(uuid.uuid4()),
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
    await client_autenticado.post(
        "/api/v1/retiros-efectivo",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "monto": "50.00",
            "fecha": "2099-01-01T11:00:00Z",
        },
    )

    response = await client_autenticado.get(
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


async def test_get_totales_corte_falta_sucursal_id_returns_422(client_autenticado):
    response = await client_autenticado.get(
        "/api/v1/cortes-caja/totales",
        params={
            "fecha_inicio": "2026-08-20T08:00:00Z",
            "fecha_fin": "2026-08-20T14:00:00Z",
        },
    )

    assert response.status_code == 422


async def test_list_cortes_caja_happy_path(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    await client_autenticado.post(
        "/api/v1/cortes-caja",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "tipo": "parcial",
            "fecha_inicio": "2026-08-21T08:00:00Z",
            "fecha_fin": "2026-08-21T14:00:00Z",
            "total_ventas": "500.00",
            "total_efectivo": "500.00",
            "total_tarjeta": "0",
            "total_retiros": "0",
            "monto_esperado": "500.00",
        },
    )

    response = await client_autenticado.get("/api/v1/cortes-caja", params={"sucursal_id": sucursal_id, "page_size": 100})

    assert response.status_code == 200
    body = response.json()
    assert body["total"] >= 1
    assert any(item["sucursal_id"] == sucursal_id and item["tipo"] == "parcial" for item in body["items"])


async def test_list_cortes_caja_falta_sucursal_id_returns_422(client_autenticado):
    response = await client_autenticado.get("/api/v1/cortes-caja")

    assert response.status_code == 422


async def test_list_retiros_efectivo_happy_path(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    await client_autenticado.post(
        "/api/v1/retiros-efectivo",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "monto": "25.00",
            "motivo": "Prueba de listado",
            "fecha": "2026-08-21T11:00:00Z",
        },
    )

    response = await client_autenticado.get("/api/v1/retiros-efectivo", params={"sucursal_id": sucursal_id, "page_size": 100})

    assert response.status_code == 200
    body = response.json()
    assert body["total"] >= 1
    assert any(item["sucursal_id"] == sucursal_id and item["motivo"] == "Prueba de listado" for item in body["items"])


async def test_list_retiros_efectivo_falta_sucursal_id_returns_422(client_autenticado):
    response = await client_autenticado.get("/api/v1/retiros-efectivo")

    assert response.status_code == 422


async def test_list_cortes_caja_filtra_por_rango_de_fechas(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    for fecha_fin in ("2026-07-10T14:00:00Z", "2026-08-15T14:00:00Z"):
        await client_autenticado.post(
            "/api/v1/cortes-caja",
            json={
                "local_id": str(uuid.uuid4()),
                "sucursal_id": sucursal_id,
                "usuario_id": "admin",
                "tipo": "parcial",
                "fecha_inicio": "2026-01-01T08:00:00Z",
                "fecha_fin": fecha_fin,
                "total_ventas": "100.00",
                "total_efectivo": "100.00",
                "total_tarjeta": "0",
                "total_retiros": "0",
                "monto_esperado": "100.00",
            },
        )

    response = await client_autenticado.get(
        "/api/v1/cortes-caja",
        params={
            "sucursal_id": sucursal_id,
            "desde": "2026-08-01T00:00:00Z",
            "hasta": "2026-08-31T23:59:59Z",
            "page_size": 100,
        },
    )

    assert response.status_code == 200
    fechas_fin = [item["fecha_fin"] for item in response.json()["items"]]
    assert all(f.startswith("2026-08") for f in fechas_fin)
    assert any(f.startswith("2026-08-15") for f in fechas_fin)


async def test_list_retiros_efectivo_filtra_por_rango_de_fechas(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    for fecha, motivo in (("2026-07-10T11:00:00Z", "fuera de rango"), ("2026-08-15T11:00:00Z", "dentro de rango")):
        await client_autenticado.post(
            "/api/v1/retiros-efectivo",
            json={
                "local_id": str(uuid.uuid4()),
                "sucursal_id": sucursal_id,
                "usuario_id": "admin",
                "monto": "25.00",
                "motivo": motivo,
                "fecha": fecha,
            },
        )

    response = await client_autenticado.get(
        "/api/v1/retiros-efectivo",
        params={
            "sucursal_id": sucursal_id,
            "desde": "2026-08-01T00:00:00Z",
            "hasta": "2026-08-31T23:59:59Z",
            "page_size": 100,
        },
    )

    assert response.status_code == 200
    motivos = [item["motivo"] for item in response.json()["items"]]
    assert "dentro de rango" in motivos
    assert "fuera de rango" not in motivos


async def test_create_corte_caja_reintento_con_mismo_local_id_es_idempotente(client_autenticado, session):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    payload = {
        "local_id": str(uuid.uuid4()),
        "sucursal_id": sucursal_id,
        "usuario_id": "admin",
        "tipo": "parcial",
        "fecha_inicio": "2026-08-22T08:00:00Z",
        "fecha_fin": "2026-08-22T14:00:00Z",
        "total_ventas": "300.00",
        "total_efectivo": "300.00",
        "total_tarjeta": "0",
        "total_retiros": "0",
        "monto_esperado": "300.00",
    }

    primera = await client_autenticado.post("/api/v1/cortes-caja", json=payload)
    segunda = await client_autenticado.post("/api/v1/cortes-caja", json=payload)

    assert primera.status_code == 201
    assert segunda.status_code == 200
    assert segunda.json()["id"] == primera.json()["id"]

    cortes = (
        await session.execute(
            select(CorteCaja).where(CorteCaja.local_id == uuid.UUID(payload["local_id"]))
        )
    ).scalars().all()
    assert len(cortes) == 1


async def test_create_retiro_efectivo_reintento_con_mismo_local_id_es_idempotente(client_autenticado, session):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    payload = {
        "local_id": str(uuid.uuid4()),
        "sucursal_id": sucursal_id,
        "usuario_id": "admin",
        "monto": "40.00",
        "motivo": "Reintento de sync",
        "fecha": "2026-08-22T11:00:00Z",
    }

    primera = await client_autenticado.post("/api/v1/retiros-efectivo", json=payload)
    segunda = await client_autenticado.post("/api/v1/retiros-efectivo", json=payload)

    assert primera.status_code == 201
    assert segunda.status_code == 200
    assert segunda.json()["id"] == primera.json()["id"]

    retiros = (
        await session.execute(
            select(RetiroEfectivo).where(RetiroEfectivo.local_id == uuid.UUID(payload["local_id"]))
        )
    ).scalars().all()
    assert len(retiros) == 1
