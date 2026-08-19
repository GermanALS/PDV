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

## 4. Sucursales

Primer recurso real del dominio (PLAN.md Parte 6, módulo Configuración).
`id` es la PK del backend — es el valor que el dispositivo Android guarda
como `remote_id` una vez sincronizado; el backend no tiene un campo propio
llamado `remote_id` (ese nombre solo tiene sentido del lado del
dispositivo). `local_id` viaja en el `POST` para que el dispositivo
correlacione la fila creada con su registro local (ej. la sucursal por
defecto seedeada que nunca se sincronizó, PLAN.md Parte 3 "Al migrar de
local a remoto"); es opcional. `is_synced` siempre es `true` en las
respuestas del backend — una fila que existe en el servidor es por
definición la versión sincronizada.

### 4.1 Listar sucursales

**GET** `/sucursales?page=1&page_size=20`

Response `200 OK`
```json
{
  "items": [
    {
      "id": "uuid",
      "local_id": "uuid o null",
      "nombre": "string",
      "direccion": "string o null",
      "activa": true,
      "updated_at": "2026-08-18T12:00:00Z",
      "is_synced": true,
      "deleted_at": null
    }
  ],
  "page": 1,
  "page_size": 20,
  "total": 1
}
```

### 4.2 Crear sucursal

**POST** `/sucursales`

Request body
```json
{
  "local_id": "uuid o null",
  "nombre": "string, 1-120 caracteres",
  "direccion": "string o null",
  "activa": true
}
```

Response `201 Created` → mismo shape que un ítem de 4.1.

---

## 5. Ventas

Segundo recurso real del dominio (PLAN.md Parte 7, módulo Venta de
mostrador). Registra una venta de mostrador junto con sus líneas
(`venta_detalle`) en una sola llamada. El `movimiento` de salida que
decrementa inventario (PLAN.md Parte 7, "Decisiones abiertas": venta como
evento aditivo) se genera del lado del dispositivo, no aquí — este
endpoint solo persiste la venta y sus líneas tal cual las manda el cliente.
`id` es la PK del backend = `remote_id` del dispositivo una vez
sincronizada; cada línea tiene su propio `id`/`local_id` con el mismo
patrón de correlación que sucursales (sección 4). `usuario_id` es el
identificador de sesión ficticio de la Parte 4 (`admin`/`user1`), no un
UUID todavía — se endurece a FK real contra `usuarios` en la Parte 13. No
hay endpoint de listado (`GET /ventas`) todavía: no lo necesita ningún
sub-paso de la Parte 7; se agrega si una Parte futura lo requiere.

### 5.1 Registrar venta

**POST** `/ventas`

Request body
```json
{
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "folio": "string",
  "fecha": "2026-08-19T12:00:00Z",
  "subtotal": 100.00,
  "descuento": 0.00,
  "impuestos": 0.00,
  "total": 100.00,
  "metodo_pago": "efectivo",
  "estado": "completada",
  "lineas": [
    {
      "local_id": "uuid o null",
      "articulo_id": "uuid",
      "cantidad": 2,
      "precio_unitario": 50.00,
      "subtotal": 100.00
    }
  ]
}
```
`lineas` requiere al menos un elemento.

Response `201 Created`
```json
{
  "id": "uuid",
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "folio": "string",
  "fecha": "2026-08-19T12:00:00Z",
  "subtotal": 100.00,
  "descuento": 0.00,
  "impuestos": 0.00,
  "total": 100.00,
  "metodo_pago": "efectivo",
  "estado": "completada",
  "updated_at": "2026-08-19T12:00:00Z",
  "is_synced": true,
  "deleted_at": null,
  "lineas": [
    {
      "id": "uuid",
      "local_id": "uuid o null",
      "articulo_id": "uuid",
      "cantidad": 2,
      "precio_unitario": 50.00,
      "subtotal": 100.00
    }
  ]
}
```

Response `422 Unprocessable Entity` — `lineas` vacío, o falta
`sucursal_id`/`usuario_id`/`folio`/`metodo_pago`.

---

## 6. Convenciones generales

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

## 7. Pendiente de definir

Bloqueado por trabajo previo no ejecutado (no es falta de definición en
este contrato, sino prerequisitos pendientes):

- [ ] Rutas reales de `articulos`, `inventario`, `cortes_caja`,
  `devoluciones`, `movimientos`, `usuarios` (PLAN.md Partes 8-12, una por
  módulo) — `sucursales` (sección 4) y `ventas` (sección 5) ya están
  implementadas; el placeholder de la sección 3 se reemplaza módulo por
  módulo a medida que cada Parte llega a su sub-paso de repositorio
  remoto.
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
