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

**Fase real implementada (PLAN.md Parte 13)**: reemplaza por completo el
login ficticio de la fase demo (Parte 4) — las credenciales
`admin`/`password` y `user1`/`password` ya no se aceptan. Valida contra los
`usuarios`/`roles` que las secciones 10-11 persisten y usa bcrypt (cost
factor 12, mismo algoritmo y parámetros en backend y Android — PLAN.md
Parte 13, Decisión 2) para el hash de contraseña; el hash se calcula del
lado que recibe el texto plano (Android, siempre — ver sección 11.2/11.3),
nunca en esta ruta. El `access_token` es un JWT (`PyJWT`, `HS256`, 24
horas de expiración, firmado con `JWT_SECRET_KEY`) — **enforcement** del
header `Authorization: Bearer <access_token>` sobre el resto de las rutas
todavía no está implementado (no es parte del checklist de esta Parte); el
token se emite pero ningún otro endpoint lo valida todavía.

**Usuario de bootstrap**: el backend recién levantado no tiene forma de
autenticarse sin al menos un usuario existente. La migración
`0009_seed_admin_usuario` crea `admin` / `admin123` (rol Administrador) —
contraseña de desarrollo, se espera cambiarla desde la pantalla de Usuarios
tras el primer login. En modo LOCAL (Android sin backend), el mismo
usuario se siembra de forma perezosa en Room la primera vez que se abre el
login, con la misma contraseña, por el mismo motivo.

### 2.1 Login

**POST** `/auth/login`

Request body
```json
{
  "username": "string",
  "password": "string"
}
```

Response `200 OK`
```json
{
  "access_token": "string (JWT)",
  "token_type": "bearer",
  "usuario": {
    "id": "uuid",
    "local_id": "uuid o null",
    "username": "admin",
    "nombre_completo": "string",
    "rol_id": "uuid",
    "activo": true,
    "updated_at": "2026-08-21T12:00:00Z",
    "is_synced": true,
    "deleted_at": null
  }
}
```

Response `401 Unauthorized` — credenciales inválidas. Un único detalle
genérico (`{"detail": "credenciales invalidas"}`) sin distinguir causa
(usuario inexistente, inactivo, sin contraseña asignada, o contraseña
incorrecta) — evita que la respuesta permita enumerar usernames válidos.

Response `422 Unprocessable Entity` — falta `username`/`password`.

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
(`venta_detalle`) en una sola llamada. `id` es la PK del backend =
`remote_id` del dispositivo una vez sincronizada; cada línea tiene su
propio `id`/`local_id` con el mismo patrón de correlación que sucursales
(sección 4). `usuario_id` es el identificador de sesión ficticio de la
Parte 4 (`admin`/`user1`), no un UUID todavía — se endurece a FK real
contra `usuarios` en la Parte 13. No hay endpoint de listado (`GET
/ventas`) todavía: no lo necesita ningún sub-paso de la Parte 7; se agrega
si una Parte futura lo requiere.

El `movimiento` de salida que decrementa inventario se genera también aquí
(no solo del lado del dispositivo como se documentó originalmente en la
Parte 7): esta ruta hacía únicamente la venta+líneas hasta que la Parte 9
("Módulo Inventario", sub-paso 2) corrigió el hueco — sin este decremento,
la existencia consultada por esa Parte nunca reflejaba ventas ya
realizadas. Cada línea aplica su decremento con delta con signo
(`cantidad_actual - cantidad_vendida`), nunca un `UPDATE cantidad = X`
directo, mismo principio que `POST /entradas` (sección 6). Por eso cada
`articulo_id` de las líneas debe existir en `articulos` — a diferencia de
`venta_detalle`, que no tiene FK real (PLAN.md Parte 8, hallazgo de
verificación), `inventario.articulo_id`/`movimientos.articulo_id` sí la
tienen.

### 5.1 Registrar venta

**POST** `/ventas`

Los campos de monto/cantidad (`subtotal`, `descuento`, `impuestos`, `total`,
`cantidad`, `precio_unitario`) viajan como **string JSON**, no como número
crudo — evita perder precisión decimal en el viaje de ida y vuelta;
`Decimal` de Pydantic los acepta igual que un número.

Request body
```json
{
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "folio": "string",
  "fecha": "2026-08-19T12:00:00Z",
  "subtotal": "100.00",
  "descuento": "0.00",
  "impuestos": "0.00",
  "total": "100.00",
  "metodo_pago": "efectivo",
  "estado": "completada",
  "lineas": [
    {
      "local_id": "uuid o null",
      "articulo_id": "uuid",
      "cantidad": "2",
      "precio_unitario": "50.00",
      "subtotal": "100.00"
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
  "subtotal": "100.00",
  "descuento": "0.00",
  "impuestos": "0.00",
  "total": "100.00",
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
      "cantidad": "2.000",
      "precio_unitario": "50.00",
      "subtotal": "100.00"
    }
  ]
}
```

Response `422 Unprocessable Entity` — `lineas` vacío, o falta
`sucursal_id`/`usuario_id`/`folio`/`metodo_pago`.

Response `404 Not Found` — algún `articulo_id` de `lineas` no corresponde a
ningún artículo existente.

---

## 6. Entradas de mercancía

Tercer recurso real del dominio (PLAN.md Parte 8, módulo Entrada de
mercancía). No existe una tabla `entradas` propia en `docs/schema-pos.json`
— una entrada es, en sí misma, un `movimiento` (`tipo="entrada"`) más el
efecto que produce sobre `articulos` (alta, si el artículo es nuevo) e
`inventario` (alta o incremento de `cantidad`). El endpoint hace las tres
escrituras en una sola llamada atómica, análogo a como `POST /ventas`
persiste la venta y sus líneas juntas.

`cantidad` viaja como **string JSON** (igual convención que Ventas, sección
5) para no perder precisión decimal. El incremento de `inventario.cantidad`
se aplica sumando el delta (`cantidad_actual + cantidad_entrada`), nunca
sobrescribiendo el valor — mismo principio de "eventos aditivos" que el
motor de sync del dispositivo (`EventoAditivoCombiner`, PLAN.md Parte 6),
aunque el backend no reutiliza ese código Kotlin.

Exactamente uno de `articulo_id` (artículo existente) o `articulo_nuevo`
(artículo a dar de alta) debe venir en el body — nunca ambos, nunca
ninguno.

### 6.1 Registrar entrada

**POST** `/entradas`

Request body — artículo existente:
```json
{
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "fecha": "2026-08-19T12:00:00Z",
  "cantidad": "10",
  "ubicacion": "Estante A1",
  "articulo_id": "uuid"
}
```

Request body — artículo nuevo:
```json
{
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "fecha": "2026-08-19T12:00:00Z",
  "cantidad": "25",
  "ubicacion": "Estante B2",
  "articulo_nuevo": {
    "local_id": "uuid o null",
    "codigo_barras": "string o null",
    "sku": "string, 1-60 caracteres",
    "nombre": "string, 1-160 caracteres",
    "descripcion": "string o null",
    "categoria": "string o null",
    "unidad_medida": "string, 1-30 caracteres",
    "precio_venta": "15.00",
    "costo": "9.00 o null"
  }
}
```

Response `201 Created`
```json
{
  "movimiento": {
    "id": "uuid",
    "local_id": "uuid o null",
    "sucursal_id": "uuid",
    "articulo_id": "uuid",
    "usuario_id": "admin",
    "tipo": "entrada",
    "cantidad": "25.000",
    "ubicacion": "Estante B2",
    "referencia_tipo": "entrada_manual",
    "referencia_id": null,
    "fecha": "2026-08-19T12:00:00Z",
    "updated_at": "2026-08-19T12:00:00Z",
    "is_synced": true,
    "deleted_at": null
  },
  "inventario": {
    "id": "uuid",
    "local_id": "uuid o null",
    "sucursal_id": "uuid",
    "articulo_id": "uuid",
    "cantidad": "25.000",
    "ubicacion": "Estante B2",
    "updated_at": "2026-08-19T12:00:00Z",
    "is_synced": true,
    "deleted_at": null
  },
  "articulo": {
    "id": "uuid",
    "local_id": "uuid o null",
    "codigo_barras": "string o null",
    "sku": "string",
    "nombre": "string",
    "descripcion": "string o null",
    "categoria": "string o null",
    "unidad_medida": "string",
    "precio_venta": "15.00",
    "costo": "9.00",
    "activo": true,
    "updated_at": "2026-08-19T12:00:00Z",
    "is_synced": true,
    "deleted_at": null
  }
}
```
`articulo` es `null` cuando la entrada fue de un artículo existente
(`articulo_id`), ya que no hubo alta de catálogo.

Response `422 Unprocessable Entity` — falta `sucursal_id`/`usuario_id`/
`cantidad`, o el body trae tanto `articulo_id` como `articulo_nuevo` (o
ninguno de los dos).

Response `404 Not Found` — `articulo_id` no corresponde a ningún artículo
existente.

---

## 7. Inventario

Cuarto recurso real del dominio (PLAN.md Parte 9, módulo Inventario).
Consulta el inventario de una sucursal (artículo + existencia juntos, mismo
join que hace `InventarioDao` del lado Android) y permite ajustar los
atributos de catálogo de un artículo junto con su cantidad en existencia,
en una sola llamada atómica — análogo a como `POST /entradas` persiste el
artículo, el inventario y el movimiento juntos (sección 6).

### 7.1 Listar inventario

**GET** `/inventario?sucursal_id=uuid&page=1&page_size=20&q=texto`

`q` (opcional): filtra por nombre, SKU o código de barras (coincidencia
parcial, sin distinguir mayúsculas/minúsculas) — mismo criterio de
búsqueda que `InventarioDao.observarPagina` del lado Android.

Response `200 OK`
```json
{
  "items": [
    {
      "articulo_id": "uuid",
      "codigo_barras": "string o null",
      "sku": "string",
      "nombre": "string",
      "descripcion": "string o null",
      "categoria": "string o null",
      "unidad_medida": "string",
      "precio_venta": "18.50",
      "costo": "12.00 o null",
      "cantidad": "24.000",
      "ubicacion": "string o null"
    }
  ],
  "page": 1,
  "page_size": 20,
  "total": 12
}
```

### 7.2 Ajustar artículo (atributos de catálogo + cantidad en existencia)

**PATCH** `/inventario/{articulo_id}`

`cantidad` es el **nuevo valor** de existencia, no un delta — el servidor
calcula `delta = cantidad - cantidad_actual` y lo aplica sumando (nunca un
`UPDATE cantidad = X` directo), igual que
`LocalInventarioRepository.actualizarArticuloCompleto` del lado Android
(PLAN.md Parte 6, "Decisiones abiertas"). Si el delta resulta `0`, se
actualiza el artículo (y la ubicación, si cambió) pero no se inserta
`movimiento`.

Request body
```json
{
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "nombre": "string, 1-160 caracteres",
  "descripcion": "string o null",
  "categoria": "string o null",
  "unidad_medida": "string, 1-30 caracteres",
  "precio_venta": "19.00",
  "costo": "12.00 o null",
  "cantidad": "30",
  "ubicacion": "string o null"
}
```

Response `200 OK`
```json
{
  "articulo": {
    "id": "uuid",
    "local_id": "uuid o null",
    "codigo_barras": "string o null",
    "sku": "string",
    "nombre": "string",
    "descripcion": "string o null",
    "categoria": "string o null",
    "unidad_medida": "string",
    "precio_venta": "19.00",
    "costo": "12.00",
    "activo": true,
    "updated_at": "2026-08-19T12:00:00Z",
    "is_synced": true,
    "deleted_at": null
  },
  "inventario": {
    "id": "uuid",
    "local_id": "uuid o null",
    "sucursal_id": "uuid",
    "articulo_id": "uuid",
    "cantidad": "30.000",
    "ubicacion": "string o null",
    "updated_at": "2026-08-19T12:00:00Z",
    "is_synced": true,
    "deleted_at": null
  },
  "movimiento": {
    "id": "uuid",
    "local_id": "uuid o null",
    "sucursal_id": "uuid",
    "articulo_id": "uuid",
    "usuario_id": "admin",
    "tipo": "ajuste",
    "cantidad": "5.000",
    "ubicacion": "string o null",
    "referencia_tipo": "ajuste_manual",
    "referencia_id": null,
    "fecha": "2026-08-19T12:00:00Z",
    "updated_at": "2026-08-19T12:00:00Z",
    "is_synced": true,
    "deleted_at": null
  }
}
```
`movimiento.cantidad` es el delta con signo aplicado (puede ser negativo);
`movimiento` es `null` cuando el delta fue `0`.

Response `404 Not Found` — `articulo_id` no corresponde a ningún artículo
existente.

Response `422 Unprocessable Entity` — falta `sucursal_id`/`usuario_id`/
`nombre`/`unidad_medida`/`precio_venta`/`cantidad`.

### 7.3 Valores usados de categoría, unidad de medida y ubicación

Alimentan el campo de lista editable del cliente (`EditableDropdownField`,
PLAN.md Parte 9) — de solo lectura, derivados de los valores ya usados en
`articulos`/`inventario`, sin tabla de catálogo propia (PLAN.md Parte 9,
"Enfoque: lista derivada de valores ya usados").

**GET** `/articulos/categorias`

Response `200 OK`
```json
{ "valores": ["Abarrotes", "Bebidas"] }
```

**GET** `/articulos/unidades-medida`

Response `200 OK`
```json
{ "valores": ["kg", "pieza"] }
```

**GET** `/inventario/ubicaciones?sucursal_id=uuid`

Response `200 OK`
```json
{ "valores": ["Estante A1", "Estante B2"] }
```

---

## 8. Cortes de caja y retiros de efectivo

Quinto recurso real del dominio (PLAN.md Parte 10, módulo Caja, ampliado
2026-08-20 con cortes parciales/finales y retiros de efectivo). A
diferencia de `POST /ventas` (sección 5), el backend **no recalcula**
ningún total: el dispositivo ya agregó `ventas`/`retiros_efectivo` de su
propio periodo (`LocalCajaRepository.calcularTotales`) y estas rutas solo
persisten lo que llega, igual que `POST /sucursales` (sección 4). No hay
FK real entre `retiros_efectivo` y `cortes_caja` — se relacionan por rango
de fecha del lado del dispositivo, nunca por referencia (PLAN.md Parte 10,
"Decisiones abiertas").

### 8.1 Registrar corte de caja

**POST** `/cortes-caja`

Los montos viajan como **string JSON** (misma convención que Ventas,
sección 5).

Request body
```json
{
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "tipo": "parcial",
  "fecha_inicio": "2026-08-20T08:00:00Z",
  "fecha_fin": "2026-08-20T14:00:00Z",
  "total_ventas": "1500.00",
  "total_efectivo": "900.00",
  "total_tarjeta": "600.00",
  "total_retiros": "100.00",
  "monto_esperado": "800.00",
  "monto_contado": "795.00",
  "diferencia": "-5.00"
}
```
`tipo` es `"parcial"` o `"final"`. `monto_contado`/`diferencia` son `null`
si el corte se guarda sin contar el efectivo físico todavía.

Response `201 Created`
```json
{
  "id": "uuid",
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "tipo": "parcial",
  "fecha_inicio": "2026-08-20T08:00:00Z",
  "fecha_fin": "2026-08-20T14:00:00Z",
  "total_ventas": "1500.00",
  "total_efectivo": "900.00",
  "total_tarjeta": "600.00",
  "total_retiros": "100.00",
  "monto_esperado": "800.00",
  "monto_contado": "795.00",
  "diferencia": "-5.00",
  "updated_at": "2026-08-20T14:00:00Z",
  "is_synced": true,
  "deleted_at": null
}
```

Response `422 Unprocessable Entity` — falta `sucursal_id`/`usuario_id`/
`tipo`/`fecha_inicio`/`fecha_fin`/`total_ventas`/`total_efectivo`/
`total_tarjeta`/`monto_esperado`, o `tipo` no es `"parcial"`/`"final"`.

### 8.2 Registrar retiro de efectivo

**POST** `/retiros-efectivo`

Request body
```json
{
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "monto": "100.00",
  "motivo": "string o null",
  "fecha": "2026-08-20T11:00:00Z"
}
```

Response `201 Created`
```json
{
  "id": "uuid",
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "monto": "100.00",
  "motivo": "string o null",
  "fecha": "2026-08-20T11:00:00Z",
  "updated_at": "2026-08-20T11:00:00Z",
  "is_synced": true,
  "deleted_at": null
}
```

Response `422 Unprocessable Entity` — falta `sucursal_id`/`usuario_id`/
`monto`/`fecha`, o `monto` no es mayor que `0`.

### 8.3 Totales de un periodo (para calcular un corte)

Contraparte remota de `LocalCajaRepository.calcularTotales` (Android): la
usa `RemoteCajaRepository` cuando `BackendMode` es remoto o
local-con-sincronización, para poder mostrar los mismos totales que el
modo local calcula desde Room. A diferencia de 8.1/8.2, aquí el backend sí
agrega (`SUM` sobre `Numeric` de Postgres — sin la conversión a texto que
tiene `BigDecimal` en Room, que por eso se evita agregar en SQL del lado
del dispositivo).

**GET** `/cortes-caja/totales?sucursal_id=uuid&fecha_inicio=2026-08-20T08:00:00Z&fecha_fin=2026-08-20T14:00:00Z`

Response `200 OK`
```json
{
  "total_ventas": "1500.00",
  "total_efectivo": "900.00",
  "total_tarjeta": "600.00",
  "total_retiros": "100.00",
  "monto_esperado": "800.00"
}
```
`monto_esperado = total_efectivo - total_retiros`. Si no hay ventas ni
retiros en el periodo, todos los campos son `"0"`.

Response `422 Unprocessable Entity` — falta `sucursal_id`/`fecha_inicio`/
`fecha_fin`.

---

## 9. Devoluciones

Sexto recurso real del dominio (PLAN.md Parte 11, módulo Devoluciones).
Registra la devolución de un cliente junto con sus líneas
(`devolucion_detalle`) en una sola llamada, mismo patrón que `POST /ventas`
(sección 5). A diferencia de Ventas/Entradas, **esta ruta no toca
`inventario` ni escribe `movimiento`** — el propósito de esta Parte es
"gestionar la devolución con el proveedor", no un restock inmediato del
catálogo vendible; si una Parte futura necesita ese efecto, se documenta
ahí, no aquí. `venta_id` es opcional (`"venta original, si se conoce"`,
`docs/schema-pos.json`); si se envía, debe corresponder a una venta
existente. A diferencia de `venta_detalle` (sección 5, sin FK real a
`articulos`), `devolucion_detalle.articulo_id` sí tiene FK real — mismo
criterio que `inventario`/`movimientos` (`docs/schema-pos.json`).

`cantidad` viaja como **string JSON** (misma convención que Ventas/Entradas)
para no perder precisión decimal.

### 9.1 Registrar devolución

**POST** `/devoluciones`

Request body
```json
{
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "venta_id": "uuid o null",
  "folio": "string",
  "fecha": "2026-08-20T12:00:00Z",
  "estado": "registrada",
  "lineas": [
    {
      "local_id": "uuid o null",
      "articulo_id": "uuid",
      "cantidad": "1",
      "motivo": "string o null",
      "condicion": "defectuoso"
    }
  ]
}
```
`lineas` requiere al menos un elemento. `estado` es opcional, por defecto
`"registrada"` (`docs/schema-pos.json`: `"registrada"` /
`"en_gestion_proveedor"` / `"cerrada"`, sin validación server-side de la
transición todavía — esta Parte solo crea devoluciones en `"registrada"`,
las transiciones de estado no están en su checklist). `condicion` es libre
(ej. `"defectuoso"` / `"no_defectuoso"`, `docs/schema-pos.json` lo describe
como ejemplo, no como enum cerrado).

Response `201 Created`
```json
{
  "id": "uuid",
  "local_id": "uuid o null",
  "sucursal_id": "uuid",
  "usuario_id": "admin",
  "venta_id": "uuid o null",
  "folio": "string",
  "fecha": "2026-08-20T12:00:00Z",
  "estado": "registrada",
  "updated_at": "2026-08-20T12:00:00Z",
  "is_synced": true,
  "deleted_at": null,
  "lineas": [
    {
      "id": "uuid",
      "local_id": "uuid o null",
      "articulo_id": "uuid",
      "cantidad": "1.000",
      "motivo": "string o null",
      "condicion": "defectuoso"
    }
  ]
}
```

Response `422 Unprocessable Entity` — `lineas` vacío, o falta
`sucursal_id`/`usuario_id`/`folio`/`fecha`.

Response `404 Not Found` — `venta_id` no corresponde a ninguna venta
existente, o algún `articulo_id` de `lineas` no corresponde a ningún
artículo existente.

---

## 10. Roles

Octavo recurso real del dominio (PLAN.md Parte 13, gestión de usuarios
real). Catálogo de roles personalizados: reemplaza el `Literal`
`"administrador"`/`"encargado_turno"` que `usuarios.rol` usaba hasta esta
Parte por una tabla propia — `usuarios.rol_id` (sección 11) ahora es FK a
`roles.id`. No lleva `sucursal_id` (no es `sucursal_scoped`, mismo criterio
que `usuarios`). `modulos_permitidos` es la lista de claves de módulo que
ese rol puede ver/usar — activa o desactiva pantallas completas, no
acciones dentro de un módulo (PLAN.md Parte 13, esquema aprobado). Claves
de módulo válidas: `"venta"`, `"entrada"`, `"inventario"`, `"caja"`,
`"devoluciones"`, `"usuarios"`, `"configuracion"`.

Dos roles de sistema (`es_sistema: true`) vienen seedeados por la migración
0007: `"administrador"` (los 7 módulos) y `"encargado_turno"` (todos menos
`"usuarios"` y `"configuracion"`). Un rol de sistema no se puede editar ni
eliminar (`400`) — solo los roles personalizados creados después
(`es_sistema: false`) admiten `PATCH`/`DELETE`.

### 10.1 Listar roles

**GET** `/roles?page=1&page_size=20`

Response `200 OK`
```json
{
  "items": [
    {
      "id": "uuid",
      "local_id": "uuid o null",
      "nombre": "administrador",
      "modulos_permitidos": ["venta", "entrada", "inventario", "caja", "devoluciones", "usuarios", "configuracion"],
      "es_sistema": true,
      "updated_at": "2026-08-21T12:00:00Z",
      "is_synced": true,
      "deleted_at": null
    }
  ],
  "page": 1,
  "page_size": 20,
  "total": 2
}
```

### 10.2 Crear rol

**POST** `/roles`

Request body
```json
{
  "local_id": "uuid o null",
  "nombre": "string, 1-60 caracteres, unico",
  "modulos_permitidos": ["venta", "caja"]
}
```
`es_sistema` siempre es `false` en los roles creados por esta ruta — los
roles de sistema solo existen por el seed de la migración 0007.

Response `201 Created` → mismo shape que un ítem de 10.1.

Response `409 Conflict` — ya existe un rol con ese `nombre`.

Response `422 Unprocessable Entity` — falta `nombre`/`modulos_permitidos`,
`modulos_permitidos` vacío, o contiene una clave de módulo no reconocida.

### 10.3 Editar rol

**PATCH** `/roles/{id}`

Request body
```json
{
  "nombre": "string, 1-60 caracteres, unico",
  "modulos_permitidos": ["venta", "caja", "devoluciones"]
}
```

Response `200 OK` → mismo shape que un ítem de 10.1.

Response `400 Bad Request` — el rol es de sistema (`es_sistema: true`).

Response `404 Not Found` — no existe un rol con ese `id`.

Response `409 Conflict` — el `nombre` nuevo ya lo usa otro rol.

### 10.4 Eliminar rol

**DELETE** `/roles/{id}`

Soft-delete: fija `deleted_at`, no borra la fila (PLAN.md Parte 3).

Response `204 No Content`

Response `400 Bad Request` — el rol es de sistema (`es_sistema: true`).

Response `404 Not Found` — no existe un rol con ese `id`.

---

## 11. Usuarios

Séptimo recurso real del dominio (PLAN.md Parte 12, módulo Administración de
usuarios — demo; endurecido en la Parte 13). CRUD completo de `usuarios`; a
diferencia de las demás entidades transaccionales, **no lleva
`sucursal_id`** (`usuarios` no es `sucursal_scoped` en
`docs/schema-pos.json` — un usuario puede operar en cualquier sucursal).
`rol_id` es FK a `roles.id` (sección 10) — reemplaza al `Literal` fijo que
usaba esta sección antes de la Parte 13. La respuesta **nunca** incluye
`password_hash` — esta ruta nunca recibe ni calcula un hash a partir de una
contraseña en texto plano; el `password_hash` opcional que aceptan 11.2 y
11.3 ya llega calculado del lado que tuvo el texto plano (Android, siempre
— PLAN.md Parte 13, Decisión 2), nunca en este endpoint.

### 11.1 Listar usuarios

**GET** `/usuarios?page=1&page_size=20`

Response `200 OK`
```json
{
  "items": [
    {
      "id": "uuid",
      "local_id": "uuid o null",
      "username": "admin",
      "nombre_completo": "string",
      "rol_id": "uuid",
      "activo": true,
      "updated_at": "2026-08-21T12:00:00Z",
      "is_synced": true,
      "deleted_at": null
    }
  ],
  "page": 1,
  "page_size": 20,
  "total": 1
}
```

### 11.2 Crear usuario

**POST** `/usuarios`

Request body
```json
{
  "local_id": "uuid o null",
  "username": "string, 1-60 caracteres, unico",
  "nombre_completo": "string, 1-120 caracteres",
  "rol_id": "uuid",
  "activo": true,
  "password_hash": "string (bcrypt) o null"
}
```
`password_hash` es opcional; `null` (u omitido) crea el usuario sin
contraseña asignada — no puede iniciar sesión hasta que se le asigne una
(mismo estado que los usuarios creados en la Parte 12, antes de esta
Parte).

Response `201 Created` → mismo shape que un ítem de 11.1.

Response `404 Not Found` — `rol_id` no corresponde a ningún rol existente.

Response `409 Conflict` — ya existe un usuario con ese `username`.

### 11.3 Editar usuario

**PATCH** `/usuarios/{id}`

Request body
```json
{
  "username": "string, 1-60 caracteres, unico",
  "nombre_completo": "string, 1-120 caracteres",
  "rol_id": "uuid",
  "activo": true,
  "password_hash": "string (bcrypt) o null"
}
```
`password_hash` es opcional; `null` (u omitido) **no cambia** la contraseña
existente (semántica PATCH) — para dejar a un usuario sin contraseña
explícitamente no hay endpoint dedicado en esta Parte.

Response `200 OK` → mismo shape que un ítem de 11.1.

Response `404 Not Found` — no existe un usuario con ese `id`, o `rol_id` no
corresponde a ningún rol existente.

Response `409 Conflict` — el `username` nuevo ya lo usa otro usuario.

### 11.4 Eliminar usuario

**DELETE** `/usuarios/{id}`

Soft-delete: fija `deleted_at`, no borra la fila (PLAN.md Parte 3).

Response `204 No Content`

Response `404 Not Found` — no existe un usuario con ese `id`.

---

## 12. Convenciones generales

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

## 13. Pendiente de definir

Bloqueado por trabajo previo no ejecutado (no es falta de definición en
este contrato, sino prerequisitos pendientes):

- [ ] `sucursales` (sección 4), `ventas` (sección 5), la alta de
  `articulos`/`inventario`/`movimientos` vía `POST /entradas` (sección 6),
  la lectura/ajuste de `articulos`/`inventario` (sección 7),
  `cortes_caja`/`retiros_efectivo` (sección 8), `devoluciones` (sección 9),
  `roles` (sección 10), `usuarios` (sección 11) y `auth/login` (sección 2)
  ya están implementadas; el placeholder de la sección 3 se reemplaza
  módulo por módulo a medida que cada Parte llega a su sub-paso de
  repositorio remoto.
- [ ] Enforcement del header `Authorization: Bearer <access_token>` sobre
  el resto de las rutas (fuera de `/auth/*` y `/health`) — el token ya se
  emite (sección 2) pero ningún endpoint lo valida todavía; no es parte del
  checklist de la Parte 13, queda abierto para cuando el proyecto lo
  priorice.
- [ ] Rutas de IA — passthrough a DeepSeek, entrada/salida estructurada,
  validación de permisos (PLAN.md Partes 14-16), sin diseñar a nivel de
  contrato.

Genuinamente abierto (no depende de trabajo previo):

- [ ] Estrategia de refresh token (¿se agrega `refresh_token` en login, y
  con qué expiración?).
- [ ] ¿Los logs de la app (PLAN.md Parte 5, archivos `.txt` locales al
  dispositivo) alguna vez viajan por API, o son puramente locales? Si son
  puramente locales, no requieren entrada en este contrato.
