import uuid


async def _crear_rol(client_autenticado, nombre: str = "rol_test") -> str:
    response = await client_autenticado.post(
        "/api/v1/roles",
        json={"local_id": None, "nombre": nombre, "modulos_permitidos": ["venta"]},
    )
    return response.json()["id"]


async def _crear_usuario(client_autenticado, username: str, rol_id: str):
    return await client_autenticado.post(
        "/api/v1/usuarios",
        json={
            "local_id": None,
            "username": username,
            "nombre_completo": "Usuario de prueba",
            "rol_id": rol_id,
            "activo": True,
        },
    )


async def test_create_usuario_happy_path(client_autenticado):
    rol_id = await _crear_rol(client_autenticado, "rol_usuario_1")
    response = await _crear_usuario(client_autenticado, "usuario_test_1", rol_id)

    assert response.status_code == 201
    body = response.json()
    assert body["username"] == "usuario_test_1"
    assert body["rol_id"] == rol_id
    assert body["activo"] is True
    assert body["is_synced"] is True
    assert "password_hash" not in body

    listado = await client_autenticado.get("/api/v1/usuarios?page_size=100")
    usernames = [item["username"] for item in listado.json()["items"]]
    assert "usuario_test_1" in usernames


async def test_create_usuario_without_username_returns_422(client_autenticado):
    rol_id = await _crear_rol(client_autenticado, "rol_sin_username")
    response = await client_autenticado.post(
        "/api/v1/usuarios",
        json={"nombre_completo": "Sin usuario", "rol_id": rol_id, "activo": True},
    )

    assert response.status_code == 422


async def test_create_usuario_rol_inexistente_returns_404(client_autenticado):
    response = await client_autenticado.post(
        "/api/v1/usuarios",
        json={
            "username": "rol_invalido",
            "nombre_completo": "Rol invalido",
            "rol_id": str(uuid.uuid4()),
            "activo": True,
        },
    )

    assert response.status_code == 404


async def test_create_usuario_username_duplicado_returns_409(client_autenticado):
    rol_id = await _crear_rol(client_autenticado, "rol_duplicado_usuario")
    await _crear_usuario(client_autenticado, "usuario_duplicado", rol_id)

    response = await _crear_usuario(client_autenticado, "usuario_duplicado", rol_id)

    assert response.status_code == 409


async def test_update_usuario_happy_path(client_autenticado):
    rol_id = await _crear_rol(client_autenticado, "rol_editar_usuario")
    creado = await _crear_usuario(client_autenticado, "usuario_a_editar", rol_id)
    usuario_id = creado.json()["id"]

    response = await client_autenticado.patch(
        f"/api/v1/usuarios/{usuario_id}",
        json={
            "username": "usuario_editado",
            "nombre_completo": "Nombre editado",
            "rol_id": rol_id,
            "activo": False,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["username"] == "usuario_editado"
    assert body["nombre_completo"] == "Nombre editado"
    assert body["rol_id"] == rol_id
    assert body["activo"] is False


async def test_update_usuario_inexistente_returns_404(client_autenticado):
    rol_id = await _crear_rol(client_autenticado, "rol_usuario_inexistente")
    response = await client_autenticado.patch(
        f"/api/v1/usuarios/{uuid.uuid4()}",
        json={"username": "x", "nombre_completo": "x", "rol_id": rol_id, "activo": True},
    )

    assert response.status_code == 404


async def test_delete_usuario_happy_path(client_autenticado):
    rol_id = await _crear_rol(client_autenticado, "rol_eliminar_usuario")
    creado = await _crear_usuario(client_autenticado, "usuario_a_eliminar", rol_id)
    usuario_id = creado.json()["id"]

    response = await client_autenticado.delete(f"/api/v1/usuarios/{usuario_id}")

    assert response.status_code == 204
    listado = await client_autenticado.get("/api/v1/usuarios?page_size=100")
    usernames = [item["username"] for item in listado.json()["items"]]
    assert "usuario_a_eliminar" not in usernames


async def test_delete_usuario_inexistente_returns_404(client_autenticado):
    response = await client_autenticado.delete(f"/api/v1/usuarios/{uuid.uuid4()}")

    assert response.status_code == 404
