# API Contract — v1

Fuente de verdad compartida entre `backend/` (FastAPI) y `android/` (cliente Kotlin).
Todo endpoint nuevo o modificado se documenta aquí ANTES de implementarse en
cualquiera de los dos lados. Base URL: `http://localhost:8000/api/v1` (dev).

---

## 1. Health Check

Verifica que el backend está vivo. Útil como primera prueba end-to-end del
flujo Kotlin → FastAPI.

**GET** `/health`

Response `200 OK`
```json
{
  "status": "ok",
  "version": "0.1.0"
}
```

No requiere autenticación.

---

## 2. Autenticación

### 2.1 Login

**POST** `/auth/login`

Request body
```json
{
  "email": "usuario@ejemplo.com",
  "password": "string, min 8 caracteres"
}
```

Response `200 OK`
```json
{
  "access_token": "jwt-string",
  "token_type": "bearer",
  "expires_in": 3600
}
```

Errores:
| Código | Caso |
|---|---|
| 401 | credenciales inválidas |
| 422 | body mal formado (validación Pydantic) |

### 2.2 Registro

**POST** `/auth/register`

Request body
```json
{
  "email": "usuario@ejemplo.com",
  "password": "string, min 8 caracteres",
  "name": "string"
}
```

Response `201 Created`
```json
{
  "id": "uuid",
  "email": "usuario@ejemplo.com",
  "name": "string"
}
```

Errores:
| Código | Caso |
|---|---|
| 409 | el email ya está registrado |
| 422 | body mal formado |

Todas las rutas fuera de `/auth/*` y `/health` requieren el header:
`Authorization: Bearer <access_token>`

---

## 3. Recurso de ejemplo: Items

Reemplaza "Item" por tu entidad real de dominio (ej. `Task`, `Product`, `Post`)
cuando definas el propósito específico de la app. La forma del contrato
(paginación, envoltorio de respuesta, errores) puedes mantenerla igual.

### 3.1 Listar items

**GET** `/items?page=1&page_size=20`

Response `200 OK`
```json
{
  "items": [
    { "id": "uuid", "title": "string", "created_at": "2026-08-08T12:00:00Z" }
  ],
  "page": 1,
  "page_size": 20,
  "total": 42
}
```

### 3.2 Crear item

**POST** `/items`

Request body
```json
{
  "title": "string, 1-120 caracteres"
}
```

Response `201 Created`
```json
{ "id": "uuid", "title": "string", "created_at": "2026-08-08T12:00:00Z" }
```

### 3.3 Obtener item por id

**GET** `/items/{id}`

Response `200 OK` → mismo shape que 3.2
Response `404 Not Found` → item no existe

### 3.4 Eliminar item

**DELETE** `/items/{id}`

Response `204 No Content`
Response `404 Not Found` → item no existe

---

## 4. Convenciones generales

- Todas las fechas en ISO 8601 UTC (`created_at`, `updated_at`).
- IDs como UUID v4 (string), nunca enteros autoincrementales expuestos en la API pública.
- Paginación siempre con `page` (1-indexed) y `page_size` (default 20, max 100).
- Errores de validación (422) devuelven el formato estándar de FastAPI/Pydantic;
  no se sobreescribe ese shape.
- Errores de negocio (400/401/403/404/409) devuelven:
  ```json
  { "detail": "mensaje legible para debug/log" }
  ```

## 5. Pendiente de definir

- [ ] Dominio real de "Item" (renombrar según el producto)
- [ ] Estrategia de refresh token (¿se agrega `refresh_token` en login?)
- [ ] ¿Roles/permisos, o autenticación simple de un solo tipo de usuario?
