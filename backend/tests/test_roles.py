import uuid
from datetime import datetime, timezone

from app.models.rol import Rol


async def _crear_rol(client, nombre: str, modulos: list[str] | None = None):
    return await client.post(
        "/api/v1/roles",
        json={
            "local_id": None,
            "nombre": nombre,
            "modulos_permitidos": modulos or ["venta", "caja"],
        },
    )


async def _crear_rol_sistema(session, nombre: str, modulos: list[str]) -> Rol:
    rol = Rol(
        id=uuid.uuid4(),
        local_id=None,
        nombre=nombre,
        modulos_permitidos=modulos,
        es_sistema=True,
        updated_at=datetime.now(timezone.utc),
        deleted_at=None,
    )
    session.add(rol)
    await session.commit()
    await session.refresh(rol)
    return rol


async def test_create_rol_happy_path(client):
    response = await _crear_rol(client, "rol_test_1", modulos=["venta", "inventario"])

    assert response.status_code == 201
    body = response.json()
    assert body["nombre"] == "rol_test_1"
    assert body["modulos_permitidos"] == ["venta", "inventario"]
    assert body["es_sistema"] is False
    assert body["is_synced"] is True

    listado = await client.get("/api/v1/roles?page_size=100")
    nombres = [item["nombre"] for item in listado.json()["items"]]
    assert "rol_test_1" in nombres


async def test_create_rol_sin_nombre_returns_422(client):
    response = await client.post(
        "/api/v1/roles",
        json={"modulos_permitidos": ["venta"]},
    )

    assert response.status_code == 422


async def test_create_rol_modulo_invalido_returns_422(client):
    response = await client.post(
        "/api/v1/roles",
        json={"nombre": "rol_invalido", "modulos_permitidos": ["marketing"]},
    )

    assert response.status_code == 422


async def test_create_rol_nombre_duplicado_returns_409(client):
    await _crear_rol(client, "rol_duplicado")

    response = await _crear_rol(client, "rol_duplicado")

    assert response.status_code == 409


async def test_update_rol_happy_path(client):
    creado = await _crear_rol(client, "rol_a_editar")
    rol_id = creado.json()["id"]

    response = await client.patch(
        f"/api/v1/roles/{rol_id}",
        json={"nombre": "rol_editado", "modulos_permitidos": ["caja", "devoluciones"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["nombre"] == "rol_editado"
    assert body["modulos_permitidos"] == ["caja", "devoluciones"]


async def test_update_rol_inexistente_returns_404(client):
    response = await client.patch(
        f"/api/v1/roles/{uuid.uuid4()}",
        json={"nombre": "x", "modulos_permitidos": ["venta"]},
    )

    assert response.status_code == 404


async def test_update_rol_de_sistema_returns_400(client, session):
    rol = await _crear_rol_sistema(session, "rol_sistema_editar", ["venta"])

    response = await client.patch(
        f"/api/v1/roles/{rol.id}",
        json={"nombre": "otro_nombre", "modulos_permitidos": ["venta", "caja"]},
    )

    assert response.status_code == 400


async def test_delete_rol_happy_path(client):
    creado = await _crear_rol(client, "rol_a_eliminar")
    rol_id = creado.json()["id"]

    response = await client.delete(f"/api/v1/roles/{rol_id}")

    assert response.status_code == 204
    listado = await client.get("/api/v1/roles?page_size=100")
    nombres = [item["nombre"] for item in listado.json()["items"]]
    assert "rol_a_eliminar" not in nombres


async def test_delete_rol_inexistente_returns_404(client):
    response = await client.delete(f"/api/v1/roles/{uuid.uuid4()}")

    assert response.status_code == 404


async def test_delete_rol_de_sistema_returns_400(client, session):
    rol = await _crear_rol_sistema(session, "rol_sistema_eliminar", ["venta"])

    response = await client.delete(f"/api/v1/roles/{rol.id}")

    assert response.status_code == 400
