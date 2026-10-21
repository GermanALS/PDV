import app.routers.auth as auth_router
from app.security import hash_password

# El login (/auth/login) es una ruta exenta: se ejerce con el `client`
# anonimo. El armado previo (crear rol/usuario) pega en rutas protegidas, asi
# que usa `client_autenticado`.


async def _crear_rol(client_autenticado, nombre: str = "rol_auth") -> str:
    response = await client_autenticado.post(
        "/api/v1/roles",
        json={"local_id": None, "nombre": nombre, "modulos_permitidos": ["venta"]},
    )
    return response.json()["id"]


async def _crear_usuario_con_password(
    client_autenticado,
    username: str,
    password: str | None,
    activo: bool = True,
    rol_id: str | None = None,
):
    return await client_autenticado.post(
        "/api/v1/usuarios",
        json={
            "local_id": None,
            "username": username,
            "nombre_completo": "Usuario de prueba",
            "rol_id": rol_id or await _crear_rol(client_autenticado, f"rol_{username}"),
            "activo": activo,
            "password_hash": hash_password(password) if password else None,
        },
    )


async def test_login_happy_path(client, client_autenticado):
    await _crear_usuario_con_password(client_autenticado, "usuario_login", "secret123")

    response = await client.post(
        "/api/v1/auth/login", json={"username": "usuario_login", "password": "secret123"}
    )

    assert response.status_code == 200
    body = response.json()
    assert body["access_token"]
    assert body["token_type"] == "bearer"
    assert body["usuario"]["username"] == "usuario_login"
    assert "password_hash" not in body["usuario"]


async def test_login_wrong_password_returns_401(client, client_autenticado):
    await _crear_usuario_con_password(client_autenticado, "usuario_login_2", "secret123")

    response = await client.post(
        "/api/v1/auth/login", json={"username": "usuario_login_2", "password": "incorrecta"}
    )

    assert response.status_code == 401


async def test_login_usuario_inexistente_returns_401(client):
    response = await client.post(
        "/api/v1/auth/login", json={"username": "no_existe", "password": "cualquiera"}
    )

    assert response.status_code == 401


async def test_login_usuario_inactivo_returns_401(client, client_autenticado):
    await _crear_usuario_con_password(
        client_autenticado, "usuario_inactivo", "secret123", activo=False
    )

    response = await client.post(
        "/api/v1/auth/login", json={"username": "usuario_inactivo", "password": "secret123"}
    )

    assert response.status_code == 401


async def test_login_usuario_sin_password_asignada_returns_401(client, client_autenticado):
    await _crear_usuario_con_password(client_autenticado, "usuario_sin_password", None)

    response = await client.post(
        "/api/v1/auth/login",
        json={"username": "usuario_sin_password", "password": "cualquiera"},
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


# Admin bootstrap de las migraciones 0009 + 0014 (PLAN.md Parte 37, A-3).
async def _login_admin_bootstrap(client) -> dict:
    response = await client.post(
        "/api/v1/auth/login", json={"username": "admin", "password": "admin123"}
    )
    assert response.status_code == 200
    return response.json()


async def test_admin_bootstrap_logs_in_flagged_and_gets_403_on_protected_routes(client):
    body = await _login_admin_bootstrap(client)
    headers = {"Authorization": f"Bearer {body['access_token']}"}

    assert body["usuario"]["debe_cambiar_password"] is True
    lectura = await client.get("/api/v1/usuarios", headers=headers)
    escritura = await client.post(
        "/api/v1/roles",
        headers=headers,
        json={"local_id": None, "nombre": "rol_bloqueado", "modulos_permitidos": ["venta"]},
    )
    assert lectura.status_code == 403
    assert lectura.json() == {"detail": "debe cambiar la contrasena"}
    assert escritura.status_code == 403


async def test_cambiar_password_clears_the_flag_and_replaces_the_password(client):
    body = await _login_admin_bootstrap(client)
    headers = {"Authorization": f"Bearer {body['access_token']}"}

    cambio = await client.post(
        "/api/v1/auth/cambiar-password",
        headers=headers,
        json={"password_hash": hash_password("nueva-segura")},
    )

    assert cambio.status_code == 204
    assert (await client.get("/api/v1/usuarios", headers=headers)).status_code == 200
    vieja = await client.post(
        "/api/v1/auth/login", json={"username": "admin", "password": "admin123"}
    )
    nueva = await client.post(
        "/api/v1/auth/login", json={"username": "admin", "password": "nueva-segura"}
    )
    assert vieja.status_code == 401
    assert nueva.json()["usuario"]["debe_cambiar_password"] is False


async def test_cambiar_password_rejects_a_non_bcrypt_hash(client):
    body = await _login_admin_bootstrap(client)

    response = await client.post(
        "/api/v1/auth/cambiar-password",
        headers={"Authorization": f"Bearer {body['access_token']}"},
        json={"password_hash": "texto-plano"},
    )

    assert response.status_code == 422


async def test_cambiar_password_without_token_returns_401(client):
    response = await client.post(
        "/api/v1/auth/cambiar-password", json={"password_hash": hash_password("x")}
    )

    assert response.status_code == 401


# PLAN.md Parte 37, M-4: limite de intentos y tiempo constante.


async def _login(client, username: str, password: str):
    return await client.post("/api/v1/auth/login", json={"username": username, "password": password})


async def test_cinco_fallos_bloquean_el_username_aunque_la_siguiente_sea_correcta(client, client_autenticado):
    await _crear_usuario_con_password(client_autenticado, "usuario_bloqueo", "secret123")
    fallos = [await _login(client, "usuario_bloqueo", "incorrecta") for _ in range(5)]

    response = await _login(client, "usuario_bloqueo", "secret123")

    assert [r.status_code for r in fallos] == [401] * 5
    assert response.status_code == 429


async def test_un_login_exitoso_reinicia_el_contador(client, client_autenticado):
    await _crear_usuario_con_password(client_autenticado, "usuario_reinicio", "secret123")
    for _ in range(4):
        await _login(client, "usuario_reinicio", "incorrecta")
    await _login(client, "usuario_reinicio", "secret123")

    for _ in range(4):
        response = await _login(client, "usuario_reinicio", "incorrecta")

    assert response.status_code == 401


async def test_usuario_inexistente_tambien_verifica_un_hash(client, monkeypatch):
    llamadas = []
    original = auth_router.verify_password
    monkeypatch.setattr(
        auth_router, "verify_password", lambda plain, hashed: llamadas.append(hashed) or original(plain, hashed)
    )

    response = await _login(client, "no_existe_nunca", "lo-que-sea")

    assert response.status_code == 401
    assert len(llamadas) == 1
