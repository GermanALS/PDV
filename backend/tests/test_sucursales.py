async def test_list_sucursales_returns_at_least_the_seeded_default(client):
    response = await client.get("/api/v1/sucursales")

    assert response.status_code == 200
    body = response.json()
    nombres = [item["nombre"] for item in body["items"]]
    assert "Sucursal principal" in nombres


async def test_list_sucursales_rejects_invalid_page(client):
    response = await client.get("/api/v1/sucursales?page=0")

    assert response.status_code == 422


async def test_create_sucursal_happy_path(client):
    response = await client.post(
        "/api/v1/sucursales",
        json={"local_id": None, "nombre": "Sucursal Test", "direccion": "Calle Falsa 123", "activa": True},
    )

    assert response.status_code == 201
    body = response.json()
    assert body["nombre"] == "Sucursal Test"
    assert body["direccion"] == "Calle Falsa 123"
    assert body["is_synced"] is True
    assert body["id"] is not None

    listado = await client.get("/api/v1/sucursales?page_size=100")
    nombres = [item["nombre"] for item in listado.json()["items"]]
    assert "Sucursal Test" in nombres


async def test_create_sucursal_without_nombre_returns_422(client):
    response = await client.post("/api/v1/sucursales", json={"direccion": "Calle Falsa 123"})

    assert response.status_code == 422
