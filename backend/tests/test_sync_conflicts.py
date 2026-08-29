import uuid


def _payload(**overrides) -> dict:
    base = {
        "id": str(uuid.uuid4()),
        "entidad": "inventario",
        "entidad_local_id": str(uuid.uuid4()),
        "sucursal_id": None,
        "valor_local": {"cantidad": 2},
        "valor_remoto": {"cantidad": 3},
        "valor_resuelto": {"cantidad": -1},
        "politica_aplicada": "evento_aditivo",
        "resuelto_automaticamente": False,
        "fecha_deteccion": "2026-08-28T14:03:11Z",
    }
    base.update(overrides)
    return base


async def test_create_sync_conflict_happy_path(client):
    payload = _payload()

    response = await client.post("/api/v1/sync-conflicts", json=payload)

    assert response.status_code == 201
    body = response.json()
    assert body["id"] == payload["id"]
    assert body["entidad"] == "inventario"
    assert body["valor_resuelto"] == {"cantidad": -1}
    assert body["politica_aplicada"] == "evento_aditivo"
    assert body["resuelto_automaticamente"] is False


async def test_list_sync_conflicts_returns_uploaded_conflicts_newest_first(client):
    viejo = _payload(fecha_deteccion="2026-08-27T09:00:00Z")
    nuevo = _payload(fecha_deteccion="2026-08-28T18:30:00Z")
    await client.post("/api/v1/sync-conflicts", json=viejo)
    await client.post("/api/v1/sync-conflicts", json=nuevo)

    response = await client.get("/api/v1/sync-conflicts?page_size=100")

    assert response.status_code == 200
    ids = [item["id"] for item in response.json()["items"]]
    assert nuevo["id"] in ids
    assert viejo["id"] in ids
    assert ids.index(nuevo["id"]) < ids.index(viejo["id"])


async def test_upload_sync_conflict_is_idempotent(client):
    payload = _payload()

    primera = await client.post("/api/v1/sync-conflicts", json=payload)
    segunda = await client.post(
        "/api/v1/sync-conflicts",
        json={**payload, "valor_resuelto": {"cantidad": 999}},
    )

    assert primera.status_code == 201
    assert segunda.status_code == 200
    # La segunda subida no sobreescribe: devuelve la fila original.
    assert segunda.json()["valor_resuelto"] == {"cantidad": -1}

    listado = await client.get("/api/v1/sync-conflicts?page_size=100")
    coincidencias = [item for item in listado.json()["items"] if item["id"] == payload["id"]]
    assert len(coincidencias) == 1


async def test_list_sync_conflicts_filters_by_resuelto_automaticamente(client):
    automatico = _payload(resuelto_automaticamente=True)
    manual = _payload(resuelto_automaticamente=False)
    await client.post("/api/v1/sync-conflicts", json=automatico)
    await client.post("/api/v1/sync-conflicts", json=manual)

    response = await client.get("/api/v1/sync-conflicts?resuelto_automaticamente=true&page_size=100")

    ids = [item["id"] for item in response.json()["items"]]
    assert automatico["id"] in ids
    assert manual["id"] not in ids


async def test_upload_sync_conflict_with_invalid_politica_returns_422(client):
    response = await client.post(
        "/api/v1/sync-conflicts",
        json=_payload(politica_aplicada="merge_magico"),
    )

    assert response.status_code == 422
