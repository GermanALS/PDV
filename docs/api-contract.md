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

**Estado**: sin implementar. El modelo real no es login/registro por email
— cambia en dos fases y debe documentarse aquí recién al llegar a cada una,
no antes:

- **Fase demo (PLAN.md Parte 4)**: login con credenciales ficticias fijas
  (`admin`/`password`, `user1`/`password`), sin endpoint de registro. Solo
  habilita/deshabilita el acceso a la UI de demostración.
- **Fase real (PLAN.md Parte 13)**: gestión completa de usuarios con roles
  (Administrador, encargado de turno, roles personalizados) y permisos por
  módulo. Reemplaza por completo cualquier login de la fase demo.

Todas las rutas fuera de `/auth/*` y `/health` requerirán el header
`Authorization: Bearer <access_token>` una vez exista autenticación real;
no aplica todavía.

---

## 3. Recurso de ejemplo: Items (placeholder — pendiente de reemplazo)

**Este bloque es el template genérico de scaffolding inicial y no refleja el
dominio del punto de venta.** Se reemplaza por los endpoints reales
(`articulos`, `inventario`, `ventas`, `cortes_caja`, `devoluciones`,
`movimientos`, `usuarios`, `sucursales`) cuando:

- PLAN.md Parte 3 (modelado de BD) defina el esquema campo-por-campo de los
  7 módulos y quede aprobado por el usuario (todavía no ejecutada).
- Cada Parte de módulo (PLAN.md Partes 6-12, una por módulo) traduzca su
  porción del esquema a rutas reales de FastAPI.

Se conserva la forma (paginación, envoltorio de respuesta, errores) de este
placeholder como referencia de estilo para cuando se escriban los endpoints
reales.

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
- **`sucursal_id`** (uuid): obligatorio en el body y la response de toda
  entidad transaccional (`inventario`, `ventas`, `cortes_caja`,
  `devoluciones`, `movimientos`) — arquitectura multi-sucursal, ver
  PLAN.md Parte 3.
- **Campos de tracking de sincronización**: toda entidad sincronizable
  expone `local_id`, `remote_id` (nullable hasta sincronizar), `updated_at`,
  `is_synced`, y `deleted_at` (soft-delete; nunca DELETE físico) — ver
  PLAN.md Parte 3.
- **Resolución de conflictos**: entidades sin ambigüedad de cantidad
  (precios, usuarios, permisos) usan last-write-wins por `updated_at`;
  entidades de cantidad/movimiento (inventario, ventas, cortes de caja,
  devoluciones) se tratan como eventos que se suman/aplican, nunca como
  estado final sobreescrito — ver PLAN.md Parte 6 (módulo Configuración,
  motor de sync genérico).

## 5. Pendiente de definir

Bloqueado por trabajo previo no ejecutado (no es falta de definición en
este contrato, sino prerequisitos pendientes):

- [ ] Esquema de datos campo-por-campo de los 7 módulos (PLAN.md Parte 3,
  sin ejecutar ni aprobar todavía) — bloquea la sección 3 y cualquier
  endpoint real por módulo.
- [ ] Rutas de autenticación real y gestión de usuarios/roles (PLAN.md
  Partes 4 y 13, sin implementar).
- [ ] Rutas de IA — passthrough a DeepSeek, entrada/salida estructurada,
  validación de permisos (PLAN.md Partes 14-16), sin diseñar a nivel de
  contrato.

Genuinamente abierto (no depende de trabajo previo):

- [ ] Estrategia de refresh token (¿se agrega `refresh_token` en login, y
  con qué expiración?).
- [ ] ¿Los logs de la app (PLAN.md Parte 5, archivos `.txt` locales al
  dispositivo) alguna vez viajan por API, o son puramente locales? Si son
  puramente locales, no requieren entrada en este contrato.
