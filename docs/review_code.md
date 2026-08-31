# Revisión de código - Proyecto PDV

Fecha: 2026-08-30
Alcance: monorepo completo (`android/`, `backend/`, `docs/`, `scripts/`), rama `main` (commit `94654a8`).
Método: lectura estática de módulos clave de ambos lados, contrato de API, configuración de build,
suite de pruebas y tooling. No se ejecutaron builds ni pruebas como parte de esta revisión.

---

## 1. Resumen ejecutivo

El proyecto está en muy buen estado para su fase. La arquitectura offline-first se respeta de forma
consistente (separación `domain` / `data/local` / `data/remote`, patrón `ModeAware*` uniforme en las 11
entidades), la cobertura de pruebas es alta (321 métodos `@Test` en Android, 65 en backend) y el código
es limpio: sin `!!`, sin `GlobalScope`, sin `Thread.sleep`, un único `runBlocking` justificado, y
comentarios que explican el "por qué" incluyendo hallazgos de revisiones previas.

Los puntos a atender antes de exponer el backend fuera de la red local o de distribuir la app a
comercios reales son, en orden:

1. **Ningún endpoint del backend valida el JWT** (ya está en el checklist del contrato, sección 1).
2. **`.env` en la raíz contiene secretos reales** (PAT de GitHub y API key de DeepSeek).
3. **Los POST de sincronización no son idempotentes** (ventas, entradas, cortes, retiros): un reintento
   de red duplica datos.
4. **`fallbackToDestructiveMigration(dropAllTables = true)` + `exportSchema = false`** en una app cuya
   base local es la fuente de verdad.
5. **Condición de carrera en el decremento de inventario** del backend (read-modify-write sin bloqueo).

Ninguno es un defecto de diseño de fondo; son piezas pendientes coherentes con el estado "scaffolding".

---

## 2. Fortalezas (mantener)

- **Arquitectura**: la regla "`data/local` no depende de `data/remote`" se cumple; `EjecutorAccionesIa`
  llama a los mismos repositorios de dominio que las pantallas manuales, sin camino de escritura aparte.
- **Autorización de acciones de IA**: `EjecutorAccionesIa.moduloRequeridoPorTipo()` deriva el módulo a
  autorizar del `tipo` de acción (mapeo fijo en código), nunca del campo `modulo` que también viene del
  LLM. Correcto y bien comentado.
- **Login sin enumeración de usuarios**: `auth.login` devuelve un 401 genérico idéntico para usuario
  inexistente, inactivo, sin hash o password incorrecta.
- **Hashing**: bcrypt cost 12 en ambos lados, `suspend` + `Dispatchers.Default` en Android para no
  bloquear el hilo principal.
- **Token de IA cifrado** con clave AES-256 respaldada por Android Keystore; solo ciphertext+IV en
  DataStore.
- **`sync_conflicts`**: POST idempotente por id generado en dispositivo. Es el modelo correcto para el
  resto de los POST de sync (ver hallazgo M-1).
- **Pruebas**: happy path + casos de error por endpoint en backend; ViewModels con estados mockeados.

---

## 3. Hallazgos

Severidad: **A** = atender antes de exponer/distribuir · **M** = atender pronto · **B** = mejora / deuda menor.

### Backend

#### A-1. Ningún endpoint valida el `access_token`
`backend/app/security.py` genera el JWT en `create_access_token`, pero no existe ninguna dependencia
(`get_current_user` o similar) ni `jwt.decode` en todo `backend/app/`. Todos los routers salvo
`/auth/login` y `/health` están abiertos. Combinado con que `POST /usuarios` y `POST /roles` tampoco
están protegidos, cualquiera con acceso de red al puerto 8000 puede crear un usuario administrador.

Está registrado como pendiente en `docs/api-contract.md` (secciones 1 y "Pendientes"), lo cual es
correcto, pero conviene subir su prioridad.

Recomendación: agregar `HTTPBearer` + una dependencia `usuario_actual` que haga `jwt.decode` con
`JWT_SECRET_KEY`/`JWT_ALGORITHM` y cargue el `Usuario` por `sub`; aplicarla como
`dependencies=[Depends(...)]` a nivel de `APIRouter` para todo lo que no sea `/auth` ni `/health`.
Añadir en Android un `Interceptor` que agregue `Authorization: Bearer` (hoy no se envía en ninguna
request; `RemoteVentaRepository` y pares no lo incluyen).

#### A-2. Secretos reales versionados en el working tree
`.env` (raíz) contiene un Personal Access Token de GitHub (`github_pat_...`) y una API key de DeepSeek
(`sk-...`) en texto plano. El archivo está en `.gitignore` y **no aparece en el historial de git**
(verificado), pero:

- El PAT de GitHub debería **revocarse y regenerarse** de todos modos (quedó expuesto en el entorno de
  trabajo y no tiene relación evidente con lo que la app necesita en runtime).
- La API key de DeepSeek debería **rotarse** y moverse a un mecanismo de inyección por entorno.
- Ni `docker-compose.yml` ni el `Dockerfile` consumen ese `.env`; el backend usa
  `API_KEY_DEEPSEEK` sólo si algo lo lee (no se encontró uso en `backend/app/`). Aclarar si el archivo
  sigue siendo necesario; si no, eliminarlo.

#### A-3. POST de sincronización no idempotentes
`POST /ventas`, `POST /entradas`, `POST /cortes-caja`, `POST /retiros-efectivo` generan un `id` nuevo
en el servidor (`default=uuid.uuid4`) e ignoran `local_id` salvo para guardarlo. Un dispositivo que
envía una venta, el servidor la persiste, y la respuesta se pierde por corte de red -> el reintento
del motor de sync crea una **segunda** venta con el mismo `local_id`. `sync_conflicts` ya resuelve
esto bien (usa el id del dispositivo y devuelve la fila existente): conviene replicar ese patrón.

Recomendación: aceptar el `id`/`local_id` del dispositivo como clave, y en cada POST hacer
`db.get(...)` previo -> si existe, devolver 200 con la fila existente en lugar de insertar. Alternativa:
constraint `UNIQUE(local_id)` + captura de `IntegrityError` devolviendo la fila previa.

#### M-1. Condición de carrera en el decremento de inventario
`ventas.create_venta` y `entradas.create_entrada` hacen `SELECT` de `Inventario`, calculan
`cantidad - linea.cantidad` en Python y `UPDATE`. Dos requests concurrentes para el mismo
`(sucursal, articulo)` pueden leer el mismo valor y perder un decremento (lost update). En un backend
compartido entre sucursales con sync en background esto es plausible.

Recomendación: `select(Inventario).where(...).with_for_update()` dentro de la transacción, o un
`UPDATE inventario SET cantidad = cantidad - :n WHERE ...` atómico (mismo principio de "delta con
signo" que ya siguen, pero ejecutado en la base).

#### M-2. `create_venta`: N+1 en la validación de artículos
Líneas 50-52 de `ventas.py`: un `await db.get(Articulo, ...)` por línea dentro de un `for`. Para
tickets grandes son N roundtrips. Un único `select(Articulo.id).where(Articulo.id.in_(ids))` y
comparación de conjuntos resuelve la validación en una query.

#### M-3. Enums de dominio validados de forma inconsistente
`schemas/caja.py` usa `Literal["parcial", "final"]` para `tipo` (correcto), pero `schemas/venta.py`
deja `metodo_pago` y `estado` como `str` libres. `caja.get_totales_corte` filtra por
`metodo_pago == "efectivo"` / `"tarjeta"` y `estado == "completada"` con literales exactos: una venta
sincronizada con `metodo_pago = "Efectivo"` (u otra variante) quedaría **fuera del total de efectivo**
del corte, produciendo una diferencia de caja silenciosa.

Recomendación: `Literal[...]` (o `enum.StrEnum` compartido) para `metodo_pago` y `estado` en
`VentaCreateSchema`, alineado con las constantes que usan las queries de caja y con el lado Android
(`METODO_PAGO_EFECTIVO` / `METODO_PAGO_TARJETA` en `LocalCajaRepository`).

#### M-4. La imagen Docker no puede correr migraciones
`backend/Dockerfile` hace `COPY app ./app` únicamente: no incluye `alembic/`, `alembic.ini` ni
`requirements` de migración quedan sin `alembic` disponible en el contenedor. El despliegue del
contenedor no tiene forma de ejecutar `alembic upgrade head` (coincide con la nota de memoria
"alembic-must-run-from-host-venv"). Para cualquier destino que no sea la laptop de desarrollo esto hay
que resolverlo (COPY de `alembic/` + `alembic.ini`, y un entrypoint que corra las migraciones o un job
separado).

#### M-5. Sin exception handler global ni CORS
`CLAUDE.md` sección 4 pide "manejo de errores centralizado con `HTTPException` + un exception handler
global". No hay `@app.exception_handler` ni `add_middleware` en `app/main.py`. Consecuencias: una
excepción no prevista (p. ej. `IntegrityError` no capturado en `entradas.create_entrada` cuando
`articulo_nuevo` colisiona con el `UNIQUE` de `sku`) devuelve un 500 con traceback en vez de un cuerpo
de error consistente con la sección 12 del contrato. Falta también `CORSMiddleware` (necesario si
alguna vez hay un cliente web).

#### M-6. `entradas.create_entrada` no maneja colisión de `sku`/`codigo_barras`
Con `articulo_nuevo`, un `sku` duplicado dispara `IntegrityError` -> 500 (a diferencia de
`usuarios`/`roles` que sí capturan y devuelven 409). Añadir el mismo `try/except IntegrityError` +
`rollback` + 409.

#### B-1. Dependencias sin fijar
`backend/requirements.txt` usa `>=` en todo y no hay lockfile. Un build reproducible necesita versiones
fijas (`==`) o `uv`/`pip-tools` con lock. Relevante para Docker y para CI cuando se agregue.

#### B-2. Tests contra la base de desarrollo, esquema por `create_all`
`tests/conftest.py` corre contra `localhost:5432/pdv` (la misma base de dev, con rollback por test) y
crea el esquema con `Base.metadata.create_all`, **no** con las migraciones de Alembic. La suite nunca
verifica que las migraciones produzcan el esquema que los modelos esperan: una divergencia
modelo/migración (el gate `schema-parity` de `CLAUDE.md`) pasaría verde. Recomendación: fixture que
haga `alembic upgrade head` sobre una base de test dedicada (o al menos un test que compare
`Base.metadata` con el resultado de las migraciones).

#### B-3. `docker-compose.yml`: Postgres expuesto con credenciales triviales
`pdv/pdv` y `ports: 5432:5432`. Aceptable en local; no debe llegar así a ningún entorno compartido. El
backend en compose no define `JWT_SECRET_KEY`, así que usa el default hardcodeado de `security.py`.

#### B-4. Dockerfile corre como root
Añadir un usuario no privilegiado (`RUN adduser ... && USER app`).

### Android

#### A-4. Migración destructiva + `exportSchema = false`
`di/DatabaseModule.kt`: `.fallbackToDestructiveMigration(dropAllTables = true)`. `PdvDatabase` está en
`version = 7` con `exportSchema = false`. En una app offline-first donde Room **es** la fuente de
verdad, el primer cambio de esquema tras tener datos reales en un dispositivo borra todo el inventario,
ventas y cortes locales de ese equipo. Antes de la primera distribución a un comercio:

- `exportSchema = true` + versionar los JSON de esquema (habilita tests de migración de Room).
- Empezar a escribir `Migration` reales; quitar el `fallbackToDestructiveMigration`.

#### M-7. Navegación hecha a mano sin back stack
`MainActivity` usa `enum Pantalla` + `when`. No hay pila de navegación (cada pantalla vuelve a `HELLO`
con un callback `onBack` fijo), ni transiciones, ni deep links, ni `SavedStateHandle` de navegación. La
dependencia `androidx.hilt:hilt-navigation-compose` ya está; falta `androidx.navigation:navigation-compose`.
Para el tamaño actual (11 pantallas) es manejable, pero conviene migrar antes de que crezca: hoy
"volver" desde `ROLES` va siempre a `USUARIOS` aunque se haya entrado desde otro lado, y el botón
físico de atrás del sistema cierra la app en vez de navegar.

#### M-8. `HttpLoggingInterceptor` activo en release
`di/NetworkModule.kt` agrega `HttpLoggingInterceptor(Level.BASIC)` incondicionalmente. `BASIC` sólo
registra método/URL/estado (no cuerpos ni headers), así que no filtra el token de IA, pero igual no
debería estar en builds de release. Gatearlo con `BuildConfig.DEBUG`.

#### M-9. Agregaciones numéricas sólo en memoria
`Converters` guarda `BigDecimal` como TEXT (`toPlainString`). Correcto para precisión, pero implica:

- No se puede `SUM()`/`ORDER BY`/comparar cantidades en SQL de forma fiable (orden lexicográfico).
- `LocalCajaRepository.calcularTotales` trae todas las ventas del período y suma en Kotlin.
- `EjecutorAccionesIa.todosLosItems` pagina **todo** el inventario a memoria (bucle de 100 en 100)
  para responder `consultar_stock` / `exportar_inventario`.

Para catálogos e historiales grandes esto escala mal (memoria y latencia). Opciones: columna numérica
paralela para agregación/orden, o mover esas consultas al backend cuando el modo lo permita (ya existe
`GET /cortes-caja/totales` que agrega en Postgres con `Numeric`; falta el equivalente para stock).

#### M-10. Enforcement de permisos sólo en UI
`HelloScreen` oculta botones según `modulosPermitidos` y `HelloViewModel.onIntentoNavegar` valida, pero
las pantallas se montan por callback: no hay una compuerta única. Con el backend sin auth (A-1), la
única barrera real de un usuario sin permiso "usuarios" es que no vea el botón. Aceptable en modo
LOCAL; para REMOTO depende de A-1.

#### B-5. `DynamicHostInterceptor` lee DataStore en cada request
`runBlocking { preferences.deviceConfig.first() }` en el hilo de dispatch de OkHttp por request. El
comentario dice que DataStore cachea en memoria tras la primera lectura, lo cual es cierto, pero
`.first()` sobre el `Flow` reejecuta el pipeline. Un `StateFlow` cacheado en el interceptor (o
`data.first()` una vez + observación) evita el `runBlocking` repetido.

#### B-6. `Converters.toModulosPermitidos` parte por `,`
Si una clave de módulo llegara a contener una coma, el split la rompe. Está comentado ("sin comas, un
join simple alcanza"). Un separador improbable (``) o JSON serían más robustos; deuda menor.

#### B-7. Build de release sin R8/shrinking
`app/build.gradle.kts`: `isMinifyEnabled = false`. Para distribuir una app de POS conviene activar R8
(shrink + ofuscación) y validar con las reglas de Proguard de Room/Hilt/kotlinx-serialization.

#### B-8. `versionCode` / `versionName` estáticos
Siguen en `1` / `"0.1"` tras 20 Partes. Definir cómo se versiona cada release (parte del TODO de
"Play Console Internal Testing" en `CLAUDE.md` sección 7).

### Transversal

#### M-11. Sin CI/CD
Reconocido en `CLAUDE.md` sección 7. Con 386 pruebas entre ambos módulos, un workflow por módulo
(GitHub Actions: `./gradlew test lint` y `pytest` + `alembic upgrade head` contra un Postgres de
servicio) daría mucho valor y cerraría de paso B-2 y el gate `schema-parity`.

#### B-9. `docs/PLAN.md` en 3447 líneas
`CLAUDE.md` pide mantener los docs "enfocados". `PLAN.md` es bitácora histórica de 20 Partes; considerar
archivar las Partes ya migradas a `CLAUDE.md` en un `docs/PLAN-historico.md`.

---

## 4. Recomendaciones priorizadas

| # | Acción | Módulo | Severidad |
|---|--------|--------|-----------|
| 1 | Revocar el PAT de GitHub y rotar la API key de DeepSeek de `.env`; confirmar si el archivo sigue haciendo falta | raíz | A |
| 2 | Dependencia de auth (`HTTPBearer` + `jwt.decode`) en todos los routers salvo `/auth` y `/health`; interceptor `Authorization: Bearer` en Android | backend + android | A |
| 3 | Hacer idempotentes los POST de venta/entrada/corte/retiro (id del dispositivo como clave, igual que `sync_conflicts`) | backend | A |
| 4 | `exportSchema = true`, versionar esquemas, empezar migraciones de Room, quitar `fallbackToDestructiveMigration` | android | A |
| 5 | Bloqueo de fila / `UPDATE` atómico en el decremento de inventario | backend | M |
| 6 | `Literal`/enum para `metodo_pago` y `estado` de venta, alineado con las queries de caja y con Android | backend + android | M |
| 7 | Exception handler global + `CORSMiddleware`; capturar `IntegrityError` en `entradas` | backend | M |
| 8 | Incluir `alembic/` en la imagen Docker + entrypoint/job de migración | backend | M |
| 9 | Tests de backend contra base dedicada y esquema por `alembic upgrade head` | backend | M |
| 10 | Migrar a Navigation-Compose | android | M |
| 11 | Configurar CI (un workflow por módulo) | transversal | M |
| 12 | Gatear `HttpLoggingInterceptor` con `BuildConfig.DEBUG`; fijar versiones en `requirements.txt`; R8 en release | ambos | B |

---

## 5. Notas de verificación

- No se ejecutaron `./gradlew test` ni `pytest` en esta revisión; los conteos de pruebas provienen de
  `grep` sobre los fuentes de test (321 `@Test` en `android/app/src/test`, 65 `def test_` en
  `backend/tests`).
- El historial de git se revisó para confirmar que `.env` nunca fue commiteado: no aparece.
- Los hallazgos A-1 y M-11 ya figuran como pendientes en `docs/api-contract.md` y `CLAUDE.md`
  respectivamente; se incluyen aquí por su impacto, no como omisión del equipo.
