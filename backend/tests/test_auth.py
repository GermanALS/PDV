from app.security import hash_password


async def _crear_rol(client, nombre: str = "rol_auth") -> str:
    response = await client.post(
        "/api/v1/roles",
        json={"local_id": None, "nombre": nombre, "modulos_permitidos": ["venta"]},
    )
    return response.json()["id"]


async def _crear_usuario_con_password(
    client, username: str, password: str | None, activo: bool = True, rol_id: str | None = None
):
    return await client.post(
        "/api/v1/usuarios",
        json={
            "local_id": None,
            "username": username,
            "nombre_completo": "Usuario de prueba",
            "rol_id": rol_id or await _crear_rol(client, f"rol_{username}"),
            "activo": activo,
            "password_hash": hash_password(password) if password else None,
        },
    )


async def test_login_happy_path(client):
    await _crear_usuario_con_password(client, "usuario_login", "secret123")

    response = await client.post(
        "/api/v1/auth/login", json={"username": "usuario_login", "password": "secret123"}
    )

    assert response.status_code == 200
    body = response.json()
    assert body["access_token"]
    assert body["token_type"] == "bearer"
    assert body["usuario"]["username"] == "usuario_login"
    assert "password_hash" not in body["usuario"]


async def test_login_wrong_password_returns_401(client):
    await _crear_usuario_con_password(client, "usuario_login_2", "secret123")

    response = await client.post(
        "/api/v1/auth/login", json={"username": "usuario_login_2", "password": "incorrecta"}
    )

    assert response.status_code == 401


async def test_login_usuario_inexistente_returns_401(client):
    response = await client.post(
        "/api/v1/auth/login", json={"username": "no_existe", "password": "cualquiera"}
    )

    assert response.status_code == 401


async def test_login_usuario_inactivo_returns_401(client):
    await _crear_usuario_con_password(client, "usuario_inactivo", "secret123", activo=False)

    response = await client.post(
        "/api/v1/auth/login", json={"username": "usuario_inactivo", "password": "secret123"}
    )

    assert response.status_code == 401


async def test_login_usuario_sin_password_asignada_returns_401(client):
    await _crear_usuario_con_password(client, "usuario_sin_password", None)

    response = await client.post(
        "/api/v1/auth/login", json={"username": "usuario_sin_password", "password": "cualquiera"}
    )

    assert response.status_code == 401


# Cierra "credenciales ficticias (admin/password, user1/password) dejan de
# aceptarse" (PLAN.md Parte 13, checklist "Reemplazo del login ficticio"):
# sin un usuario real de nombre "admin" en la base (la suite de tests no
# corre la migracion 0009 que lo seedea), el login ficticio de la Parte 4
# no tiene ningun camino que lo siga aceptando.
async def test_login_credenciales_ficticias_de_la_parte_4_returns_401(client):
    response_admin = await client.post(
        "/api/v1/auth/login", json={"username": "admin", "password": "password"}
    )
    response_user1 = await client.post(
        "/api/v1/auth/login", json={"username": "user1", "password": "password"}
    )

    assert response_admin.status_code == 401
    assert response_user1.status_code == 401
