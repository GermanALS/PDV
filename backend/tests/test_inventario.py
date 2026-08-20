import uuid


async def _seeded_sucursal_id(client) -> str:
    response = await client.get("/api/v1/sucursales")
    return response.json()["items"][0]["id"]


async def _articulo_con_existencia(client, sucursal_id: str, sku: str, cantidad: str, categoria: str = "Bebidas") -> str:
    response = await client.post(
        "/api/v1/entradas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": cantidad,
            "articulo_nuevo": {
                "sku": sku,
                "nombre": f"Articulo {sku}",
                "categoria": categoria,
                "unidad_medida": "pieza",
                "precio_venta": "20.00",
            },
        },
    )
    return response.json()["articulo"]["id"]


async def test_list_inventario_happy_path(client):
    sucursal_id = await _seeded_sucursal_id(client)
    articulo_id = await _articulo_con_existencia(client, sucursal_id, sku="INV-001", cantidad="15")

    response = await client.get(f"/api/v1/inventario?sucursal_id={sucursal_id}")

    assert response.status_code == 200
    body = response.json()
    fila = next(item for item in body["items"] if item["articulo_id"] == articulo_id)
    assert fila["sku"] == "INV-001"
    assert fila["cantidad"] == "15.000"
    assert fila["categoria"] == "Bebidas"


async def test_list_inventario_filters_by_q(client):
    sucursal_id = await _seeded_sucursal_id(client)
    await _articulo_con_existencia(client, sucursal_id, sku="INV-BUSCAME", cantidad="1")
    await _articulo_con_existencia(client, sucursal_id, sku="INV-OTRO", cantidad="1")

    response = await client.get(f"/api/v1/inventario?sucursal_id={sucursal_id}&q=BUSCAME")

    assert response.status_code == 200
    body = response.json()
    assert all("BUSCAME" in item["sku"] for item in body["items"])
    assert len(body["items"]) >= 1


async def test_list_inventario_rejects_invalid_page(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.get(f"/api/v1/inventario?sucursal_id={sucursal_id}&page=0")

    assert response.status_code == 422


async def test_ajustar_articulo_updates_attributes_and_applies_delta(client):
    sucursal_id = await _seeded_sucursal_id(client)
    articulo_id = await _articulo_con_existencia(client, sucursal_id, sku="INV-002", cantidad="10")

    response = await client.patch(
        f"/api/v1/inventario/{articulo_id}",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "nombre": "Nombre editado",
            "categoria": "Abarrotes",
            "unidad_medida": "pieza",
            "precio_venta": "25.00",
            "cantidad": "30",
            "ubicacion": "Estante Z1",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["articulo"]["nombre"] == "Nombre editado"
    assert body["articulo"]["categoria"] == "Abarrotes"
    assert body["inventario"]["cantidad"] == "30.000"
    assert body["inventario"]["ubicacion"] == "Estante Z1"
    assert body["movimiento"]["tipo"] == "ajuste"
    assert body["movimiento"]["cantidad"] == "20.000"


async def test_ajustar_articulo_without_quantity_change_creates_no_movimiento(client):
    sucursal_id = await _seeded_sucursal_id(client)
    articulo_id = await _articulo_con_existencia(client, sucursal_id, sku="INV-003", cantidad="8")

    response = await client.patch(
        f"/api/v1/inventario/{articulo_id}",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "nombre": "Articulo INV-003",
            "unidad_medida": "pieza",
            "precio_venta": "20.00",
            "cantidad": "8",
        },
    )

    assert response.status_code == 200
    assert response.json()["movimiento"] is None


async def test_ajustar_articulo_inexistente_returns_404(client):
    sucursal_id = await _seeded_sucursal_id(client)

    response = await client.patch(
        f"/api/v1/inventario/{uuid.uuid4()}",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "nombre": "No existe",
            "unidad_medida": "pieza",
            "precio_venta": "10.00",
            "cantidad": "1",
        },
    )

    assert response.status_code == 404


async def test_valores_endpoints_include_seeded_data(client):
    sucursal_id = await _seeded_sucursal_id(client)
    await _articulo_con_existencia(client, sucursal_id, sku="INV-004", cantidad="1", categoria="Categoria-Unica-Test")

    categorias = await client.get("/api/v1/articulos/categorias")
    unidades = await client.get("/api/v1/articulos/unidades-medida")
    ubicaciones = await client.get(f"/api/v1/inventario/ubicaciones?sucursal_id={sucursal_id}")

    assert "Categoria-Unica-Test" in categorias.json()["valores"]
    assert "pieza" in unidades.json()["valores"]
    assert ubicaciones.status_code == 200
