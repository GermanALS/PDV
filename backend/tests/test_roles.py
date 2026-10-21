import uuid
from datetime import datetime, timezone

from app.models.rol import Rol
from app.models.usuario import Usuario
from app.security import create_access_token


async def _crear_rol(client_autenticado, nombre: str, modulos: list[str] | None = None):
    return await client_autenticado.post(
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


async def test_create_rol_happy_path(client_autenticado):
    response = await _crear_rol(client_autenticado, "rol_test_1", modulos=["venta", "inventario"])

    assert response.status_code == 201
    body = response.json()
    assert body["nombre"] == "rol_test_1"
    assert body["modulos_permitidos"] == ["venta", "inventario"]
    assert body["es_sistema"] is False
    assert body["is_synced"] is True

    listado = await client_autenticado.get("/api/v1/roles?page_size=100")
    nombres = [item["nombre"] for item in listado.json()["items"]]
    assert "rol_test_1" in nombres


async def test_create_rol_sin_nombre_returns_422(client_autenticado):
    response = await client_autenticado.post(
        "/api/v1/roles",
        json={"modulos_permitidos": ["venta"]},
    )

    assert response.status_code == 422


async def test_create_rol_modulo_invalido_returns_422(client_autenticado):
    response = await client_autenticado.post(
        "/api/v1/roles",
        json={"nombre": "rol_invalido", "modulos_permitidos": ["marketing"]},
    )

    assert response.status_code == 422


async def test_create_rol_nombre_duplicado_returns_409(client_autenticado):
    await _crear_rol(client_autenticado, "rol_duplicado")

    response = await _crear_rol(client_autenticado, "rol_duplicado")

    assert response.status_code == 409


async def test_update_rol_happy_path(client_autenticado):
    creado = await _crear_rol(client_autenticado, "rol_a_editar")
    rol_id = creado.json()["id"]

    response = await client_autenticado.patch(
        f"/api/v1/roles/{rol_id}",
        json={"nombre": "rol_editado", "modulos_permitidos": ["caja", "devoluciones"]},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["nombre"] == "rol_editado"
    assert body["modulos_permitidos"] == ["caja", "devoluciones"]


async def test_update_rol_inexistente_returns_404(client_autenticado):
    response = await client_autenticado.patch(
        f"/api/v1/roles/{uuid.uuid4()}",
        json={"nombre": "x", "modulos_permitidos": ["venta"]},
    )

    assert response.status_code == 404


async def test_update_rol_de_sistema_returns_400(client_autenticado, session):
    rol = await _crear_rol_sistema(session, "rol_sistema_editar", ["venta"])

    response = await client_autenticado.patch(
        f"/api/v1/roles/{rol.id}",
        json={"nombre": "otro_nombre", "modulos_permitidos": ["venta", "caja"]},
    )

    assert response.status_code == 400


async def test_delete_rol_happy_path(client_autenticado):
    creado = await _crear_rol(client_autenticado, "rol_a_eliminar")
    rol_id = creado.json()["id"]

    response = await client_autenticado.delete(f"/api/v1/roles/{rol_id}")

    assert response.status_code == 204
    listado = await client_autenticado.get("/api/v1/roles?page_size=100")
    nombres = [item["nombre"] for item in listado.json()["items"]]
    assert "rol_a_eliminar" not in nombres


async def test_delete_rol_inexistente_returns_404(client_autenticado):
    response = await client_autenticado.delete(f"/api/v1/roles/{uuid.uuid4()}")

    assert response.status_code == 404


async def test_delete_rol_de_sistema_returns_400(client_autenticado, session):
    rol = await _crear_rol_sistema(session, "rol_sistema_eliminar", ["venta"])

    response = await client_autenticado.delete(f"/api/v1/roles/{rol.id}")

    assert response.status_code == 400


# PLAN.md Parte 37, M-6.


async def test_delete_rol_con_usuarios_asignados_returns_409(client_autenticado):
    rol = await _crear_rol(client_autenticado, "rol_con_usuarios")
    rol_id = rol.json()["id"]
    await client_autenticado.post(
        "/api/v1/usuarios",
        json={
            "local_id": None,
            "username": "usuario_del_rol",
            "nombre_completo": "Usuario del rol",
            "rol_id": rol_id,
            "activo": True,
        },
    )

    response = await client_autenticado.delete(f"/api/v1/roles/{rol_id}")

    assert response.status_code == 409


async def test_rol_borrado_no_otorga_modulos(client, session):
    rol = Rol(nombre="rol_borrado_con_permisos", modulos_permitidos=["usuarios"])
    session.add(rol)
    await session.flush()
    usuario = Usuario(username="usuario_rol_borrado", nombre_completo="Rol borrado", rol_id=rol.id)
    session.add(usuario)
    await session.flush()
    rol.deleted_at = datetime.now(timezone.utc)
    await session.flush()

    response = await client.post(
        "/api/v1/roles",
        headers={"Authorization": f"Bearer {create_access_token(str(usuario.id))}"},
        json={"local_id": None, "nombre": "rol_no_permitido", "modulos_permitidos": ["venta"]},
    )

    assert response.status_code == 403
