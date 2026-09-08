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

1. ~~**Ningún endpoint del backend valida el JWT**~~ — RESUELTO en la Parte 21 (PR #30).
2. ~~**`.env` en la raíz contiene secretos reales** (PAT de GitHub y API key de DeepSeek).~~ —
   RESUELTO en la Parte 22.
3. **Los POST de sincronización no son idempotentes** (ventas, entradas, cortes, retiros): un reintento
   de red duplica datos.
4. ~~**`fallbackToDestructiveMigration(dropAllTables = true)` + `exportSchema = false`** en una app cuya
   base local es la fuente de verdad.~~ — RESUELTO en la Parte 24.
5. ~~**Condición de carrera en el decremento de inventario** del backend (read-modify-write sin bloqueo).~~ —
   RESUELTO en la Parte 25.

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

#### ~~A-1. Ningún endpoint valida el `access_token`~~ — RESUELTO

**RESUELTO** en la Parte 21 (`docs/PLAN.md`), PR #30 (merge `3cbde60`, 2026-09-02).
Dependencia `usuario_actual` (`backend/app/dependencies.py`) aplicada al router
`protected` de `app/main.py` (todos los routers salvo `auth` y `health`); el
cliente Android adjunta `Authorization: Bearer` vía `AuthInterceptor` en modo
REMOTO. Texto original del hallazgo abajo.

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

#### ~~A-2. Secretos reales versionados en el working tree~~ — RESUELTO

**RESUELTO** en la Parte 22 (`docs/PLAN.md`): PAT de GitHub y API key de DeepSeek rotados, `.env`
eliminado del working tree (nunca estuvo en el historial de git). Texto original del hallazgo abajo.

`.env` (raíz) contiene un Personal Access Token de GitHub (`github_pat_...`) y una API key de DeepSeek
(`sk-...`) en texto plano. El archivo está en `.gitignore` y **no aparece en el historial de git**
(verificado), pero:

- El PAT de GitHub debería **revocarse y regenerarse** de todos modos (quedó expuesto en el entorno de
  trabajo y no tiene relación evidente con lo que la app necesita en runtime).
- La API key de DeepSeek debería **rotarse** y moverse a un mecanismo de inyección por entorno.
- Ni `docker-compose.yml` ni el `Dockerfile` consumen ese `.env`; el backend usa
  `API_KEY_DEEPSEEK` sólo si algo lo lee (no se encontró uso en `backend/app/`). Aclarar si el archivo
  sigue siendo necesario; si no, eliminarlo.

#### ~~A-3. POST de sincronización no idempotentes~~ — RESUELTO

**RESUELTO** en la Parte 23 (`docs/PLAN.md`, POS-107): `UNIQUE(local_id)` (migración Alembic 0012) +
captura de `IntegrityError` en los 4 routers, devolviendo `200` con la fila existente en reintentos.
Verificado con `pytest` (149/149) y manualmente contra Docker (2 POST consecutivos por ruta, sin
duplicados). Texto original del hallazgo abajo.

`POST /ventas`, `POST /entradas`, `POST /cortes-caja`, `POST /retiros-efectivo` generan un `id` nuevo
en el servidor (`default=uuid.uuid4`) e ignoran `local_id` salvo para guardarlo. Un dispositivo que
envía una venta, el servidor la persiste, y la respuesta se pierde por corte de red -> el reintento
del motor de sync crea una **segunda** venta con el mismo `local_id`. `sync_conflicts` ya resuelve
esto bien (usa el id del dispositivo y devuelve la fila existente): conviene replicar ese patrón.

Recomendación: aceptar el `id`/`local_id` del dispositivo como clave, y en cada POST hacer
`db.get(...)` previo -> si existe, devolver 200 con la fila existente en lugar de insertar. Alternativa:
constraint `UNIQUE(local_id)` + captura de `IntegrityError` devolviendo la fila previa.

#### ~~M-1. Condición de carrera en el decremento de inventario~~ — RESUELTO

**RESUELTO** en la Parte 25 (`docs/PLAN.md`, POS-116). `select(Inventario)...with_for_update()`
en `ventas.create_venta` y `entradas.create_entrada` bloquea la fila hasta el commit de la
transacción. Verificado con un test de concurrencia real (dos conexiones/transacciones
independientes vía `asyncio.gather`) que se confirmó manualmente falla sin el fix (lost update:
`7.000` en vez de `4.000`) y pasa con él. Texto original del hallazgo abajo.

`ventas.create_venta` y `entradas.create_entrada` hacen `SELECT` de `Inventario`, calculan
`cantidad - linea.cantidad` en Python y `UPDATE`. Dos requests concurrentes para el mismo
`(sucursal, articulo)` pueden leer el mismo valor y perder un decremento (lost update). En un backend
compartido entre sucursales con sync en background esto es plausible.

Recomendación: `select(Inventario).where(...).with_for_update()` dentro de la transacción, o un
`UPDATE inventario SET cantidad = cantidad - :n WHERE ...` atómico (mismo principio de "delta con
signo" que ya siguen, pero ejecutado en la base).

Nota de alcance (no resuelta): el lock solo protege la fila cuando ya existe. Dos requests
concurrentes que sean la *primera* entrada/venta jamás registrada para un `(sucursal, articulo)`
compiten por el mismo `INSERT` y la segunda choca con `uq_inventario_sucursal_articulo`, sin manejo
especial (se propaga como `500`, igual que antes de este fix) — fuera del criterio explícito de M-1,
que es sobre no perder un decremento en inventario ya existente.

#### ~~M-2. `create_venta`: N+1 en la validación de artículos~~ — RESUELTO

**RESUELTO** en la Parte 25 (`docs/PLAN.md`, POS-117). `create_venta` valida los `articulo_id` de
las líneas con un único `select(Articulo.id).where(Articulo.id.in_(ids))` + diferencia de conjuntos.
Texto original del hallazgo abajo.

Líneas 50-52 de `ventas.py`: un `await db.get(Articulo, ...)` por línea dentro de un `for`. Para
tickets grandes son N roundtrips. Un único `select(Articulo.id).where(Articulo.id.in_(ids))` y
comparación de conjuntos resuelve la validación en una query.

#### ~~M-3. Enums de dominio validados de forma inconsistente~~ — RESUELTO

**RESUELTO** en la Parte 25 (`docs/PLAN.md`, POS-117). `metodo_pago`/`estado` de
`VentaCreateSchema` pasaron a `Literal[...]` con constantes de módulo en `schemas/venta.py`
(`METODO_PAGO_EFECTIVO`/`METODO_PAGO_TARJETA`/`ESTADO_VENTA_COMPLETADA`), reutilizadas por
`caja.get_totales_corte` en vez de las strings sueltas — cierra el riesgo de divergencia en la
raíz, no solo con validación en el borde. `estado` quedó restringido a solo `"completada"` (se
confirmó que ninguna capa del proyecto implementa cancelación de venta); `docs/schema-pos.json` se
actualizó para dejar de documentar `"cancelada"` como valor vigente. Texto original del hallazgo
abajo.

`schemas/caja.py` usa `Literal["parcial", "final"]` para `tipo` (correcto), pero `schemas/venta.py`
deja `metodo_pago` y `estado` como `str` libres. `caja.get_totales_corte` filtra por
`metodo_pago == "efectivo"` / `"tarjeta"` y `estado == "completada"` con literales exactos: una venta
sincronizada con `metodo_pago = "Efectivo"` (u otra variante) quedaría **fuera del total de efectivo**
del corte, produciendo una diferencia de caja silenciosa.

Recomendación: `Literal[...]` (o `enum.StrEnum` compartido) para `metodo_pago` y `estado` en
`VentaCreateSchema`, alineado con las constantes que usan las queries de caja y con el lado Android
(`METODO_PAGO_EFECTIVO` / `METODO_PAGO_TARJETA` en `LocalCajaRepository`).

#### ~~M-4. La imagen Docker no puede correr migraciones~~ — RESUELTO

**RESUELTO** en la Parte 26 (`docs/PLAN.md`, POS-120). `backend/Dockerfile` copia `alembic/` +
`alembic.ini` a la imagen. `docker-compose.yml` agrega un servicio `migrate` (`alembic upgrade
head`, `restart: on-failure`) del que `backend` depende con `condition:
service_completed_successfully` — job separado, sin condición de carrera entre workers. Verificado
con `docker compose up -d --build` sobre un volumen limpio: las 12 migraciones corren y el backend
queda sano sin intervención del host. Texto original del hallazgo abajo.

`backend/Dockerfile` hace `COPY app ./app` únicamente: no incluye `alembic/`, `alembic.ini` ni
`requirements` de migración quedan sin `alembic` disponible en el contenedor. El despliegue del
contenedor no tiene forma de ejecutar `alembic upgrade head` (coincide con la nota de memoria
"alembic-must-run-from-host-venv"). Para cualquier destino que no sea la laptop de desarrollo esto hay
que resolverlo (COPY de `alembic/` + `alembic.ini`, y un entrypoint que corra las migraciones o un job
separado).

#### ~~M-5. Sin exception handler global ni CORS~~ — RESUELTO

**RESUELTO** en la Parte 25 (`docs/PLAN.md`, POS-118). `@app.exception_handler(Exception)` en
`app/main.py` devuelve `{"detail": "error interno del servidor"}` con `500` y loguea a nivel
`ERROR`, sin interferir con el manejo ya existente de `HTTPException`/`422`. `CORSMiddleware`
agregado con orígenes `*` (sin cliente web propio todavía). Documentado en `docs/api-contract.md`
§12, incluyendo la nota de que los `500` del handler global no llevan headers CORS por cómo
Starlette posiciona `ServerErrorMiddleware` fuera de `CORSMiddleware` en el stack — sin impacto hoy,
a revisar el día que exista un cliente web real. Texto original del hallazgo abajo.

`CLAUDE.md` sección 4 pide "manejo de errores centralizado con `HTTPException` + un exception handler
global". No hay `@app.exception_handler` ni `add_middleware` en `app/main.py`. Consecuencias: una
excepción no prevista (p. ej. `IntegrityError` no capturado en `entradas.create_entrada` cuando
`articulo_nuevo` colisiona con el `UNIQUE` de `sku`) devuelve un 500 con traceback en vez de un cuerpo
de error consistente con la sección 12 del contrato. Falta también `CORSMiddleware` (necesario si
alguna vez hay un cliente web).

#### ~~M-6. `entradas.create_entrada` no maneja colisión de `sku`/`codigo_barras`~~ — RESUELTO

**RESUELTO** en la Parte 25 (`docs/PLAN.md`, POS-116). `entradas.create_entrada` re-verifica el
`sku` contra la tabla antes de atribuir el `409` (evita que un `IntegrityError` no relacionado, ej.
un `sucursal_id` inexistente, se malinterprete como colisión de sku), con `rollback` igual que
`usuarios`/`roles`. `codigo_barras` no tiene `UNIQUE` en el modelo (`Articulo.sku` es la única
columna con esa restricción), así que el hallazgo aplicaba solo a `sku` en la práctica. Texto
original del hallazgo abajo.

Con `articulo_nuevo`, un `sku` duplicado dispara `IntegrityError` -> 500 (a diferencia de
`usuarios`/`roles` que sí capturan y devuelven 409). Añadir el mismo `try/except IntegrityError` +
`rollback` + 409.

#### B-1. Dependencias sin fijar
`backend/requirements.txt` usa `>=` en todo y no hay lockfile. Un build reproducible necesita versiones
fijas (`==`) o `uv`/`pip-tools` con lock. Relevante para Docker y para CI cuando se agregue.

#### ~~B-2. Tests contra la base de desarrollo, esquema por `create_all`~~ — RESUELTO

**RESUELTO** en la Parte 26 (`docs/PLAN.md`, POS-121). `tests/conftest.py` corre contra `pdv_test`
(base dedicada, distinta de la de desarrollo) y construye el esquema aplicando `alembic upgrade
head` in-process, no `Base.metadata.create_all`. `tests/test_schema_parity.py` (nuevo) compara el
esquema real post-migraciones contra `Base.metadata` vía `alembic.autogenerate.compare_metadata` y
falla si divergen — verificado agregando temporalmente una columna sin migración a un modelo y
confirmando que el test la detecta. De paso se corrigió que `alembic/env.py` deshabilitaba el logger
del exception handler global al correr Alembic in-process (`fileConfig(...,
disable_existing_loggers=False)`). `pytest`: 156 passed. Texto original del hallazgo abajo.

`tests/conftest.py` corre contra `localhost:5432/pdv` (la misma base de dev, con rollback por test) y
crea el esquema con `Base.metadata.create_all`, **no** con las migraciones de Alembic. La suite nunca
verifica que las migraciones produzcan el esquema que los modelos esperan: una divergencia
modelo/migración (el gate `schema-parity` de `CLAUDE.md`) pasaría verde. Recomendación: fixture que
haga `alembic upgrade head` sobre una base de test dedicada (o al menos un test que compare
`Base.metadata` con el resultado de las migraciones).

#### ~~B-3. `docker-compose.yml`: Postgres expuesto con credenciales triviales~~ — RESUELTO

**RESUELTO** en la Parte 22 (`docs/PLAN.md`): `docker-compose.yml` parametriza credenciales de Postgres
y `JWT_SECRET_KEY` con `${VAR:-default-local}`; `docker-compose.prod.yml.example` documenta el override
no-local (gitignoreado) que fija valores reales y quita la publicación de `5432` al host. Texto original
del hallazgo abajo.

`pdv/pdv` y `ports: 5432:5432`. Aceptable en local; no debe llegar así a ningún entorno compartido. El
backend en compose no define `JWT_SECRET_KEY`, así que usa el default hardcodeado de `security.py`.

#### B-4. Dockerfile corre como root
Añadir un usuario no privilegiado (`RUN adduser ... && USER app`).

### Android

#### ~~A-4. Migración destructiva + `exportSchema = false`~~ — RESUELTO

**RESUELTO** en la Parte 24 (`docs/PLAN.md`, POS-111): `exportSchema = true` +
`room.schemaLocation` en `app/build.gradle.kts` (JSON de esquema v7 versionado en git);
`fallbackToDestructiveMigration` eliminado de `DatabaseModule.kt`. Línea base v7 (sin
datos de producción que preservar); la primera migración real (7 -> 8, con su test
`MigrationTestHelper` instrumentado) queda para la próxima Parte que toque el esquema.
Verificado en dispositivo: instalación sobre datos previos (ventas/inventario/cortes)
sin pérdida. Texto original del hallazgo abajo.

`di/DatabaseModule.kt`: `.fallbackToDestructiveMigration(dropAllTables = true)`. `PdvDatabase` está en
`version = 7` con `exportSchema = false`. En una app offline-first donde Room **es** la fuente de
verdad, el primer cambio de esquema tras tener datos reales en un dispositivo borra todo el inventario,
ventas y cortes locales de ese equipo. Antes de la primera distribución a un comercio:

- `exportSchema = true` + versionar los JSON de esquema (habilita tests de migración de Room).
- Empezar a escribir `Migration` reales; quitar el `fallbackToDestructiveMigration`.

#### ~~M-7. Navegación hecha a mano sin back stack~~ — RESUELTO

**RESUELTO** en la Parte 27 (`docs/PLAN.md`, POS-125/126/127). `MainActivity` usa `NavHost` +
`NavController` de `androidx.navigation:navigation-compose` con una ruta por pantalla, en vez del
`enum Pantalla` + `when`. Los `onBack` fijos se reemplazaron por `navController.popBackStack()`
(corrige que "volver" desde `ROLES` iba siempre a `USUARIOS`), y el botón físico de atrás del sistema
ahora navega el stack real en vez de cerrar la app. El gate de permiso por pantalla
(`HelloViewModel.onIntentoNavegar` / `ConfiguracionViewModel.onIntentoAbrirConflictos`) y
`AsistenteIaWidget` montado una sola vez se conservaron sin cambios de comportamiento. Verificado con
`./gradlew build`/`testDebugUnitTest` en verde y recorrido manual de las 11 pantallas en el Xiaomi.
Texto original del hallazgo abajo.

`MainActivity` usa `enum Pantalla` + `when`. No hay pila de navegación (cada pantalla vuelve a `HELLO`
con un callback `onBack` fijo), ni transiciones, ni deep links, ni `SavedStateHandle` de navegación. La
dependencia `androidx.hilt:hilt-navigation-compose` ya está; falta `androidx.navigation:navigation-compose`.
Para el tamaño actual (11 pantallas) es manejable, pero conviene migrar antes de que crezca: hoy
"volver" desde `ROLES` va siempre a `USUARIOS` aunque se haya entrado desde otro lado, y el botón
físico de atrás del sistema cierra la app en vez de navegar.

#### ~~M-8. `HttpLoggingInterceptor` activo en release~~ — RESUELTO

**RESUELTO** en la Parte 28 (`docs/PLAN.md`, POS-129). Se habilitó `buildConfig = true` (opt-in
desde AGP 8) y `provideOkHttpClient` delega en `buildOkHttpClient(..., includeNetworkLogging =
BuildConfig.DEBUG)`: el `HttpLoggingInterceptor(Level.BASIC)` solo se agrega en builds debug.
`LlmNetworkModule` no lo tenía y no se tocó. `NetworkModuleTest` (release sin interceptor / debug con
uno en nivel `BASIC`) y `./gradlew testDebugUnitTest` en verde. Texto original del hallazgo abajo.

`di/NetworkModule.kt` agrega `HttpLoggingInterceptor(Level.BASIC)` incondicionalmente. `BASIC` sólo
registra método/URL/estado (no cuerpos ni headers), así que no filtra el token de IA, pero igual no
debería estar en builds de release. Gatearlo con `BuildConfig.DEBUG`.

#### ~~M-9. Agregaciones numéricas sólo en memoria~~ — RESUELTO

**RESUELTO** en la Parte 28 (`docs/PLAN.md`, POS-130). Columna espejo `InventarioEntity.cantidadNum`
(REAL) junto a `cantidad` (TEXT, sigue siendo la fuente de verdad exacta), con migración de Room
`MIGRATION_7_8` (`ALTER TABLE` + backfill `CAST`) y `PdvDatabase` v7→v8. `InventarioDao.sumarCantidad`
(`SUM(cantidadNum)`) y `observarPaginaExport` (`ORDER BY i.cantidadNum`) nuevas, expuestas como
`InventarioRepository.sumarStock` / `observarInventarioParaExport`; `EjecutorAccionesIa.ejecutarConsultarStock`
resuelve el total con un escalar (no enumera) y el camino de exportación usa la lectura ordenada por
cantidad. `LocalCajaRepository.calcularTotales` se deja sumando en Kotlin a propósito (acotado por el
período de un turno, no por el catálogo), documentado en el código. Hueco conocido: la semántica
numérica del SQL (`SUM`/`ORDER BY` no lexicográfico) no se cubre con `jvm-tests` porque el módulo
Android no tiene infra para probar Room en la JVM; queda para el test de migración de Room diferido de
la Parte 24. `./gradlew testDebugUnitTest` en verde. Texto original del hallazgo abajo.

`Converters` guarda `BigDecimal` como TEXT (`toPlainString`). Correcto para precisión, pero implica:

- No se puede `SUM()`/`ORDER BY`/comparar cantidades en SQL de forma fiable (orden lexicográfico).
- `LocalCajaRepository.calcularTotales` trae todas las ventas del período y suma en Kotlin.
- `EjecutorAccionesIa.todosLosItems` pagina **todo** el inventario a memoria (bucle de 100 en 100)
  para responder `consultar_stock` / `exportar_inventario`.

Para catálogos e historiales grandes esto escala mal (memoria y latencia). Opciones: columna numérica
paralela para agregación/orden, o mover esas consultas al backend cuando el modo lo permita (ya existe
`GET /cortes-caja/totales` que agrega en Postgres con `Numeric`; falta el equivalente para stock).

#### ~~M-10. Enforcement de permisos sólo en UI~~ — RESUELTO

**RESUELTO** en la Parte 21 (`docs/PLAN.md`), PR #30 (merge `3cbde60`, 2026-09-02).
`verificar_modulo` + dict central `_ESCRITURA_MODULO` (`backend/app/permissions.py`)
chequean el módulo del rol del usuario autenticado en las escrituras server-side
(`403` si falta). Texto original del hallazgo abajo.

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

#### ~~M-11. Sin CI/CD~~ — RESUELTO

**RESUELTO** en la Parte 26 (`docs/PLAN.md`, POS-122, POS-123). Un workflow por módulo:
`.github/workflows/backend-ci.yml` (Postgres 16 como service container, `alembic upgrade head` +
`alembic check` + `pytest`) y `.github/workflows/android-ci.yml` (`./gradlew test lint
--max-workers=1` + verificación de que el JSON de esquema de Room commiteado sigue vigente;
`--max-workers=1` evita la carrera transitoria de KSP debug/release entre notada en la Parte 24).
Ambos pasos replicados localmente en verde (backend: 156 tests, `alembic check` sin diferencias;
Android: 670 tests, lint sin errores, diff de schemas limpio), y confirmados en verde también en
GitHub Actions en el PR #35 (tras corregir el bit ejecutable de `android/gradlew`, perdido por un
commit original desde Windows, y actualizar las Actions a versiones sin warnings de Node
deprecado). Texto original del hallazgo abajo.

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
| 1 | ~~Revocar el PAT de GitHub y rotar la API key de DeepSeek de `.env`; confirmar si el archivo sigue haciendo falta~~ **RESUELTO (Parte 22)** | raíz | A |
| 2 | ~~Dependencia de auth (`HTTPBearer` + `jwt.decode`) en todos los routers salvo `/auth` y `/health`; interceptor `Authorization: Bearer` en Android~~ **RESUELTO (Parte 21, PR #30)** — incluye el enforcement de permisos por módulo server-side (M-10) | backend + android | A |
| 3 | ~~Hacer idempotentes los POST de venta/entrada/corte/retiro (id del dispositivo como clave, igual que `sync_conflicts`)~~ **RESUELTO (Parte 23)** | backend | A |
| 4 | ~~`exportSchema = true`, versionar esquemas, empezar migraciones de Room, quitar `fallbackToDestructiveMigration`~~ **RESUELTO (Parte 24)** | android | A |
| 5 | ~~Bloqueo de fila / `UPDATE` atómico en el decremento de inventario~~ **RESUELTO (Parte 25)** | backend | M |
| 6 | ~~`Literal`/enum para `metodo_pago` y `estado` de venta, alineado con las queries de caja y con Android~~ **RESUELTO (Parte 25)** | backend + android | M |
| 7 | ~~Exception handler global + `CORSMiddleware`; capturar `IntegrityError` en `entradas`~~ **RESUELTO (Parte 25)** | backend | M |
| 8 | ~~Incluir `alembic/` en la imagen Docker + entrypoint/job de migración~~ **RESUELTO (Parte 26, PR #35)** | backend | M |
| 9 | ~~Tests de backend contra base dedicada y esquema por `alembic upgrade head`~~ **RESUELTO (Parte 26, PR #35)** | backend | M |
| 10 | ~~Migrar a Navigation-Compose~~ **RESUELTO (Parte 27)** | android | M |
| 11 | ~~Configurar CI (un workflow por módulo)~~ **RESUELTO (Parte 26, PR #35)** | transversal | M |
| 12 | ~~Gatear `HttpLoggingInterceptor` con `BuildConfig.DEBUG`~~ **RESUELTO (Parte 28)**; fijar versiones en `requirements.txt` (B-1); R8 en release (B-7) | ambos | B |

---

## 5. Notas de verificación

- No se ejecutaron `./gradlew test` ni `pytest` en esta revisión; los conteos de pruebas provienen de
  `grep` sobre los fuentes de test (321 `@Test` en `android/app/src/test`, 65 `def test_` en
  `backend/tests`).
- El historial de git se revisó para confirmar que `.env` nunca fue commiteado: no aparece.
- Los hallazgos A-1 y M-11 ya figuran como pendientes en `docs/api-contract.md` y `CLAUDE.md`
  respectivamente; se incluyen aquí por su impacto, no como omisión del equipo.
- **Actualización 2026-09-02**: A-1 y M-10 resueltos en la Parte 21 (`docs/PLAN.md`),
  PR #30 (merge `3cbde60`).
- **Actualización 2026-09-04**: A-2 y B-3 resueltos en la Parte 22 (`docs/PLAN.md`).
- **Actualización 2026-09-04**: A-4 resuelto en la Parte 24 (`docs/PLAN.md`, POS-111).
- **Actualización 2026-09-05**: A-3 resuelto en la Parte 23 (`docs/PLAN.md`, POS-107), PR #32.
- **Actualización 2026-09-06**: M-1, M-2, M-3, M-5 y M-6 resueltos en la Parte 25 (`docs/PLAN.md`,
  POS-115/116/117/118).
- **Actualización 2026-09-07**: M-4, B-2 y M-11 resueltos en la Parte 26 (`docs/PLAN.md`,
  POS-119/120/121/122/123), PR #35.
- **Actualización 2026-09-07**: M-7 resuelto en la Parte 27 (`docs/PLAN.md`, POS-124/125/126/127).
- **Actualización 2026-09-07**: M-8 y M-9 resueltos en la Parte 28 (`docs/PLAN.md`, POS-128/129/130).
