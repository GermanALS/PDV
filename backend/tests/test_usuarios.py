import uuid


async def _crear_usuario(client, username: str, rol: str = "encargado_turno"):
    return await client.post(
        "/api/v1/usuarios",
        json={
            "local_id": None,
            "username": username,
            "nombre_completo": "Usuario de prueba",
            "rol": rol,
            "activo": True,
        },
    )


async def test_create_usuario_happy_path(client):
    response = await _crear_usuario(client, "usuario_test_1", rol="administrador")

    assert response.status_code == 201
    body = response.json()
    assert body["username"] == "usuario_test_1"
    assert body["rol"] == "administrador"
    assert body["activo"] is True
    assert body["is_synced"] is True
    assert "password_hash" not in body

    listado = await client.get("/api/v1/usuarios?page_size=100")
    usernames = [item["username"] for item in listado.json()["items"]]
    assert "usuario_test_1" in usernames


async def test_create_usuario_without_username_returns_422(client):
    response = await client.post(
        "/api/v1/usuarios",
        json={"nombre_completo": "Sin usuario", "rol": "administrador", "activo": True},
    )

    assert response.status_code == 422


async def test_create_usuario_rol_invalido_returns_422(client):
    response = await client.post(
        "/api/v1/usuarios",
        json={"username": "rol_invalido", "nombre_completo": "Rol invalido", "rol": "supervisor", "activo": True},
    )

    assert response.status_code == 422


async def test_create_usuario_username_duplicado_returns_409(client):
    await _crear_usuario(client, "usuario_duplicado")

    response = await _crear_usuario(client, "usuario_duplicado")

    assert response.status_code == 409


async def test_update_usuario_happy_path(client):
    creado = await _crear_usuario(client, "usuario_a_editar")
    usuario_id = creado.json()["id"]

    response = await client.patch(
        f"/api/v1/usuarios/{usuario_id}",
        json={
            "username": "usuario_editado",
            "nombre_completo": "Nombre editado",
            "rol": "administrador",
            "activo": False,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["username"] == "usuario_editado"
    assert body["nombre_completo"] == "Nombre editado"
    assert body["rol"] == "administrador"
    assert body["activo"] is False


async def test_update_usuario_inexistente_returns_404(client):
    response = await client.patch(
        f"/api/v1/usuarios/{uuid.uuid4()}",
        json={"username": "x", "nombre_completo": "x", "rol": "administrador", "activo": True},
    )

    assert response.status_code == 404


async def test_delete_usuario_happy_path(client):
    creado = await _crear_usuario(client, "usuario_a_eliminar")
    usuario_id = creado.json()["id"]

    response = await client.delete(f"/api/v1/usuarios/{usuario_id}")

    assert response.status_code == 204
    listado = await client.get("/api/v1/usuarios?page_size=100")
    usernames = [item["username"] for item in listado.json()["items"]]
    assert "usuario_a_eliminar" not in usernames


async def test_delete_usuario_inexistente_returns_404(client):
    response = await client.delete(f"/api/v1/usuarios/{uuid.uuid4()}")

    assert response.status_code == 404
