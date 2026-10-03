# Revisión de código - Proyecto PDV (pre-release)

Fecha: 2026-09-28 (pruebas de verificación: 2026-09-28/29)
Alcance: monorepo completo (`android/`, `backend/`, `docs/`, `scripts/`, `.github/`), rama `main`
(commit `a97b2c3`). Enfoque: última ronda antes del primer release en Play Store.
Método, en dos fases:
1. Lectura estática de los módulos críticos (auth, sync push/pull, inventario, caja, IA,
   persistencia, build y despliegue).
2. Verificación dinámica (2026-09-28/29): suites completas de ambos módulos, reproducción de los
   hallazgos de backend contra el contenedor Docker reconstruido con el código actual, y pruebas en
   el dispositivo físico Xiaomi M2102J20SG por Wi-Fi LAN (`192.168.0.132:8000`) con el APK de
   debug del 2026-09-11 (mismo código Android que `a97b2c3`).

Cada hallazgo indica su estado de verificación: **Reproducido** (con la evidencia observada),
**Confirmado por prueba existente**, o **Sin reproducir** (solo evidencia estática).

Revisión anterior: archivada como `docs/review_code_2026-09-28_1816.md` (revisión del 2026-08-30,
fuente de las Partes 21-30). Los identificadores de este documento (A-n / M-n / B-n) son nuevos y
no corresponden a los de la revisión anterior.

---

## 1. Estado de la revisión anterior

Todos los hallazgos de la revisión del 2026-08-30 se verificaron contra el código actual.

| Hallazgo previo | Estado | Evidencia |
|---|---|---|
| A-1 JWT sin validar | Resuelto | `backend/app/dependencies.py` (`usuario_actual`), router `protected` en `app/main.py` |
| A-2 Secretos en `.env` | Resuelto | Sin `.env` en el árbol |
| A-3 POST de sync no idempotentes | Resuelto | `UNIQUE(local_id)` + `IntegrityError` en ventas/entradas/cortes/retiros/devoluciones |
| A-4 Migración destructiva Room | Resuelto | `exportSchema`, `MIGRATION_7_8`, sin `fallbackToDestructiveMigration` |
| M-1 Carrera en decremento de inventario | Resuelto (parcial) | `with_for_update()` en ventas/entradas. Queda la nota de alcance, reproducida: ver B-1 de este documento. **No** se aplicó a `PATCH /inventario` (ver A-2) |
| M-2 .. M-11 | Resueltos | Verificados por lectura |
| B-1 .. B-6, B-8, B-9 | Resueltos | Verificados por lectura |
| **B-7 R8/shrinking en release** | **Pendiente** | `isMinifyEnabled = false`; el `assembleRelease` del 2026-09-29 produce un APK sin ofuscar (ver A-7). Absorbido por la Parte 34, sin iniciar |
| Nota M-5 (500 sin headers CORS) | Pendiente, sin impacto | Sin cliente web todavía. Ver M-4 |
| Test `MigrationTestHelper` (diferido desde Parte 24/28) | **Pendiente** | No existe `android/app/src/androidTest`. Ver M-7 |

Conclusión: de la ronda anterior solo queda abierto B-7 (ya planificado en la Parte 34) y el test
de migración de Room diferido.

---

## 2. Resumen ejecutivo

La base es sólida: arquitectura offline-first coherente, suites en verde (backend 170 passed;
Android 437 tests, 0 fallos), CI por módulo, idempotencia de sync, auth JWT extremo a extremo y
código limpio (sin `GlobalScope`, sin catches genéricos, `BigDecimal` para dinero).

Aun así, **no recomiendo publicar todavía**. Las pruebas confirmaron los bloqueantes de la lectura
estática y agregaron dos hallazgos nuevos. Hay cuatro grupos:

1. **El sync entre terminales no converge en inventario** (A-1, A-2), reproducido en el backend y
   en el teléfono: el pull no ve las ventas de otras terminales, las filas locales quedan "sucias"
   para siempre, y el push de ajustes sobrescribe el stock remoto. En el Xiaomi ya había
   divergencias reales antes de las pruebas (12 vs 20, 42 vs 48 unidades).
2. **La sucursal seleccionada cambia sola** (A-8, nuevo), reproducido dos veces en el teléfono: al
   pasar a `LOCAL_CON_SINCRONIZACION` la app termina apuntando a otra sucursal sin avisar, y las
   ventas se registrarían ahí.
3. **Seguridad de un despliegue real** (A-3, A-4, A-5, A-6): credenciales `admin/admin123`
   (visibles en claro en el APK de release), JWT falsificable con el secreto por defecto, crash en
   cada arranque tras una restauración de respaldo (reproducido en el teléfono), y ventas
   descartadas del backend sin aviso.
4. **La Parte 34 entera sigue sin empezar** (A-7): el `assembleRelease` actual produce un APK sin
   firmar y sin ofuscar, con texto de prueba en la pantalla principal.

Si el primer release va a correr **solo en modo LOCAL** (una terminal, sin backend), A-1, A-2,
A-4, A-6 y A-8 dejan de ser bloqueantes; A-3, A-5 y A-7 siguen siéndolo.

---

## 3. Hallazgos

Severidad: **A** = bloquea el release · **M** = atender antes de habilitar sync/REMOTO en comercios
reales, o en el primer patch · **B** = mejora / deuda menor.

### A. Bloqueantes

#### A-1. El pull de inventario no converge entre terminales

Dos defectos que se suman:

**(a) Backend: `inventario.updated_at` no cambia con ventas ni entradas.**
`backend/app/models/inventario.py:21` define `updated_at` solo con `default=` (sin `onupdate`), y
`ventas.py:139` / `entradas.py:154` modifican `inventario.cantidad` sin tocar `updated_at`. Solo
`PATCH /inventario` lo actualiza (`inventario.py:181`). El filtro `?updated_since=` del pull
(Parte 32) solo ve filas cambiadas por ajustes manuales: una venta de la terminal B **nunca llega**
a la terminal A. El comentario de `schemas/inventario.py:22` ("cambia con cada movimiento de
stock") describe lo que debería pasar, no lo que pasa. El test existente
(`test_list_inventario_incluye_updated_at_y_filtra_por_updated_since`) no lo detecta porque solo
crea la fila y filtra, nunca la modifica después.

**(b) Android: las filas de inventario quedan "sucias" para siempre.**
`VentaDao.kt:106` y `EntradaDao.kt:85` usan `conCantidad(...)`, que pone `isSynced = false`. El
único que vuelve a marcar el inventario como sincronizado es `InventarioAjustePusher.kt:75`;
`VentaPusher`/`EntradaPusher` no lo hacen. En `InventarioPuller.mergeItem`, una fila con
`isSynced = false` nunca se sobrescribe: solo registra un conflicto "gana lo local". Resultado: tras
la primera venta local de un artículo, esa fila deja de recibir cambios remotos.

**Reproducido (2026-09-28):**
- (a) contra el contenedor: entrada de 10 unidades (`updated_at` = 00:25:08.927), venta de 3 →
  `cantidad` 7.000 con el **mismo** `updated_at`; `GET /inventario?updated_since=<entre ambas>` no
  devuelve la fila.
- (a) y (b) en el Xiaomi, `LOCAL_CON_SINCRONIZACION`, sucursal "Sucursal principal":
  - Estado previo a la prueba: los 3 artículos del teléfono con `remoteId` tenían inventario con
    `isSynced=0` desde el 2026-09-11 y ya divergían del backend: SKU-1234 local 12 / backend 20,
    SKU-123456 local 42 / backend 48.
  - "Terminal B" (curl) ajustó SKU-98765 a 30 con `PATCH` y vendió 1 SKU-1234 con `POST /ventas`.
    Tras "Sincronizar ahora": SKU-98765 sigue en 25 en el teléfono (conflicto
    `last_write_wins_local_gana` registrado, log `SYNC_CONFLICT ... gana lo local hasta el proximo
    push`), pero no hay nada que subir ("Pendientes por subir: 1" es solo un ajuste huérfano), así
    que "el próximo push" no llega nunca. SKU-1234 queda en 12 contra 19 en el backend **sin
    conflicto registrado**: el pull ni siquiera vio la venta.

Recomendación:
- Backend: `onupdate=lambda: datetime.now(timezone.utc)` en `Inventario.updated_at` (o asignarlo en
  los dos routers), con un test: crear inventario, registrar una venta, y comprobar que
  `GET /inventario?updated_since=<antes de la venta>` la devuelve.
- Android: tratar el inventario como dato derivado de los movimientos. Tras subir la venta/entrada,
  marcar la fila como sincronizada (o sobrescribir con la cantidad devuelta por el backend). La
  regla "gana lo local" solo tiene sentido mientras existan movimientos pendientes de ese
  `(sucursal, artículo)`.
- Test JVM del puller: venta local sincronizada + cambio remoto → la fila local toma el valor
  remoto.
- Plan de reconciliación para los dispositivos que ya divergen (como el Xiaomi): una sola vez,
  tomar el valor del backend para las filas sin movimientos pendientes.

#### A-2. `PATCH /inventario` sobrescribe el stock remoto con un valor absoluto

`InventarioAjustePusher` manda la cantidad **local actual** (`request(...)`, campo `cantidad`), y
`inventario.py:179` hace `inventario.cantidad = payload.cantidad`. Además `ajustar_articulo` no usa
`with_for_update()` (`inventario.py:164`), así que tiene la misma carrera read-modify-write que se
corrigió en ventas/entradas (M-1 anterior), y no es idempotente por `local_id`.

**Reproducido (2026-09-28)** contra el contenedor: stock 10 → venta de 3 de la "terminal B" (7) →
`PATCH` de la "terminal A" con su valor offline 15 (10 + ajuste de 5). Stock final **15**; con
deltas habría quedado en 12. El movimiento de ajuste registrado fue de +8, no de +5: la venta de B
se perdió y el historial de movimientos tampoco lo refleja. Mismo efecto observado en el teléfono
(prueba de A-1: el `PATCH` a 30 generó un movimiento de +5 sobre 25).

Recomendación: que el push de ajustes mande un **delta** (suma de los movimientos `ajuste`
pendientes) con su `local_id`, igual que ventas/entradas: un endpoint `POST /ajustes-inventario`
(o un campo `delta` + `local_id` en el PATCH), aplicado con `with_for_update()`. El PATCH absoluto
puede quedarse para el modo REMOTO interactivo, donde el usuario ve el valor actual. Cambio de
contrato: primero `docs/api-contract.md`.

#### A-3. Credenciales por defecto `admin` / `admin123` sin cambio forzado

`backend/alembic/versions/0009_seed_admin_usuario.py:28` y
`android/.../data/local/LocalAuthRepository.kt:16-17`. Todo backend nuevo y todo dispositivo recién
instalado arranca con un administrador de contraseña pública. El comentario dice "se espera que se
cambie tras el primer login", pero nada lo obliga.

**Reproducido (2026-09-28/29):** `POST /auth/login` con `admin/admin123` → 200 contra el backend
de desarrollo, y el login local del Xiaomi también. En el APK de release generado el 2026-09-29
(`app-release-unsigned.apk`, sin R8) la cadena `admin123` aparece en claro dentro de `classes*.dex`
(`unzip -p ... | grep -a admin123`): cualquiera que descargue la app de Play Store la puede leer.

Recomendación: marcar el usuario bootstrap con "debe cambiar contraseña" y forzar el cambio en el
primer login (Android y backend), o pedir la contraseña del administrador en un asistente inicial.
En backend, alternativa más simple: tomar la contraseña inicial de una variable de entorno
obligatoria en el despliegue productivo. R8 no resuelve esto (ofusca nombres, no literales).

#### A-4. `JWT_SECRET_KEY` con valor por defecto conocido

`backend/app/security.py:11` y `docker-compose.yml` (`${JWT_SECRET_KEY:-dev-secret-key-...}`). Si
el override productivo omite la variable, el backend arranca igual y cualquiera puede firmar un JWT
válido para el `sub` del admin (el UUID sale en cualquier respuesta de `/usuarios` o del login).
Falla en silencio, no de forma visible.

**Reproducido (2026-09-28):** un JWT firmado fuera del backend con
`dev-secret-key-cambiar-en-produccion` y `sub` = id del admin → `GET /usuarios` responde 200.

Recomendación: sin default en código. Que el proceso no arranque si falta `JWT_SECRET_KEY` (con un
valor de desarrollo solo en `docker-compose.yml` local), o que rechace el valor de desarrollo
cuando hay una variable `ENV=production`.

#### A-5. `allowBackup="true"` + claves de Android Keystore: crash en cada arranque tras restaurar

`AndroidManifest.xml:13`. Auto Backup copia los DataStore (sesión y token de IA cifrados) y la base
Room, pero **las claves de Android Keystore no se respaldan**. Tras restaurar en un teléfono nuevo
(o reinstalar con restauración), `DataStoreSessionStore.cargar()` intenta descifrar con una clave
distinta y lanza excepción. Esa llamada corre en `PdvApplication.kt:62`
(`applicationScope.launch { sessionManager.restaurarSesion() }`), un scope sin
`CoroutineExceptionHandler`: la excepción llega al handler del hilo y cierra la app. Además el
respaldo saca de la terminal los hashes bcrypt de los usuarios y todo el historial de ventas.

**Reproducido (2026-09-28) en el Xiaomi:** `dumpsys package` confirma el flag `ALLOW_BACKUP` y
Google Backup activo. Se simuló la restauración colocando en una instalación limpia (desinstalar +
instalar el mismo APK) los mismos archivos que incluye Auto Backup (`databases/`, `files/`), tomados
con una sesión `LOCAL_CON_SINCRONIZACION` que tenía JWT cifrado. Resultado: la app se cierra en
**cada** arranque (3 de 3 intentos):

```
FATAL EXCEPTION: DefaultDispatcher-worker-8
javax.crypto.AEADBadTagException
  at com.pdv.pos.config.AndroidKeystoreTokenCipher.descifrar(AndroidKeystoreTokenCipher.kt:39)
  at com.pdv.pos.auth.DataStoreSessionStore.cargar(SessionStore.kt:55)
Caused by: android.security.KeyStoreException: Signature/MAC verification failed
```

La única salida para el usuario es borrar los datos de la app (pierde todo lo no sincronizado). Si
la sesión persistida no trae token (modo LOCAL), la app arranca, pero el token de IA queda ilegible
y `IaPreferences.getToken()` falla igual en el primer uso del chat.

Recomendación: `android:allowBackup="false"` (o `dataExtractionRules`/`fullBackupContent` que
excluyan `datastore/` y la base), y en `cargar()`/`getToken()` tratar un fallo de descifrado como
"sin sesión / sin token" y limpiar las claves. Es uno de los pocos puntos donde capturar la
excepción está justificado: es un fallo esperable de I/O con estado externo.

#### A-6. El push descarta ventas del backend sin dejar rastro visible

`EntityPusher.kt` (`empujarFila`) + `VentaPusher.kt:44`: cualquier 4xx distinto de 401 (403 porque
el rol del backend no tiene el módulo `venta`, 422 por un payload inválido, 409) marca la venta como
`isSynced = true` sin `remoteId`. El único rastro es una línea en el log del dispositivo. Para dinero
es grave: el corte de caja del backend no cuadra con el del dispositivo, y nadie lo sabe. El caso
del 403 es realista: los roles se editan por separado en local y en backend (y en la prueba de A-8
se vio que el catálogo de roles no se sincroniza).

**Confirmado por prueba existente:** `VentaPusherTest` "a 403 on one venta discards it (no infinite
retry) and keeps going with the rest" fija exactamente este comportamiento, y la suite pasa. **Sin
reproducir en el dispositivo:** el escenario quedó preparado (usuario `revcaja` con rol local
`caja` = `venta`, y el mismo usuario en el backend con el rol `REV-sin-venta` = `entrada`), pero la
prueba se detuvo antes de registrar la venta por decisión del usuario.

Recomendación: no reutilizar `isSynced` para "rechazada". Un estado propio (ej. columna
`syncRechazo` con el código HTTP) + registro en `sync_conflicts` (ya existe el panel de la
Parte 19) + contador visible en la sección Sincronización de Configuración. Cambio de esquema:
aplica la compuerta `schema-parity`.

#### A-7. Release sin preparar (Parte 34 sin iniciar)

**Verificado (2026-09-29):** `./gradlew assembleRelease` termina en verde (14m 54s) y produce
`app-release-unsigned.apk` de 36.5 MB; `apksigner verify` → `DOES NOT VERIFY` (sin firma), sin R8.

Estado actual:
- Sin `signingConfigs.release`; `isMinifyEnabled = false` (B-7 anterior); sin `isShrinkResources`.
- `versionCode = 1` / `versionName = "0.1"`; `outputFileName` solo para debug
  (`pos-hello-debug-v0.1.apk`, nombre heredado de la Parte 1).
- La pantalla principal todavía muestra el texto de la Parte 1: "Hola desde una funcion local de
  Kotlin" y "Backend: ok (v0.1.0)" (observado en el Xiaomi). Tiene que reemplazarse antes de
  publicar.
- `BASE_URL = "http://localhost:8000/api/v1/"` (`di/NetworkModule.kt:36`). En release el cleartext
  está bloqueado (correcto), así que el modo REMOTO **solo** funciona contra un backend HTTPS que
  todavía no existe.
- Permisos `CAMERA` y `RECORD_AUDIO` (dictado del chat de IA): ambos requieren justificación en
  Play Console.
- El asistente IA envía al proveedor (DeepSeek/OpenAI/OpenRouter) nombres, precios, **costos** y
  totales de caja (`ia/EstadoPuntoVenta.kt`). Tiene que declararse en el formulario Data Safety y
  en la política de privacidad, e idealmente pedir consentimiento explícito al activar la IA.
- La app usa `Theme.Material.Light.NoActionBar` de plataforma como tema de la Activity (sin tema
  propio ni splash `core-splashscreen`): en el arranque en frío se ve un fondo claro antes de
  Compose, aunque la app corre en modo oscuro.

Recomendación: ejecutar la Parte 34 tal como está en `docs/PLAN.md`, resolviendo primero sus 4
decisiones abiertas (canal, destino, Play App Signing, URL base). Sumar al checklist la pantalla
principal, la declaración de datos enviados al proveedor de IA y `assembleRelease` en CI (B-9).

#### A-8. La sucursal seleccionada cambia sola al pasar a `LOCAL_CON_SINCRONIZACION` (nuevo)

`config/ConfiguracionViewModel.kt:86-101` combina `deviceConfig` con `observeSucursales()` y, si la
sucursal persistida no aparece en el catálogo emitido en ese momento, **persiste**
`sucursales.firstOrNull()`. En `LOCAL_CON_SINCRONIZACION` el catálogo cambia de origen según haya
o no JWT (`ModeAwareSucursalRepository`: remoto, o local si el remoto falla), y los ids local y
remoto de la misma sucursal son distintos. El backend ordena por nombre
(`routers/sucursales.py:36`), así que "la primera" es la alfabéticamente menor, no la anterior.

**Reproducido dos veces en el Xiaomi (2026-09-28 y 2026-09-29).** Seleccionada "Sucursal
principal" (id remoto `2fea5a2f…`), modo LOCAL:
1. Cambio a `LOCAL_CON_SINCRONIZACION` con la sesión local sin JWT → el catálogo remoto responde
   401 → cae al local → se persiste `8447cd57…`, el id **local** de "Sucursal principal". El
   desplegable muestra el mismo nombre, así que el usuario no nota nada.
2. Cerrar sesión y entrar de nuevo (ahora con JWT) → llega el catálogo remoto, `8447cd57…` no está
   → se persiste `0e7ab7e6…` = `REV-932402-suc2`, una sucursal de prueba creada en el backend
   durante esta revisión. El pull siguiente corrió contra esa sucursal (log:
   `Pull inventario ... [0e7ab7e6-...]`).

En un comercio real con varias sucursales, el paso 2 deja la terminal operando en otra sucursal:
ventas, cortes e inventario se registran ahí. Un usuario sin el módulo `configuracion` (ej. rol
`caja`) ni siquiera puede corregirlo. Además, en el paso 1 las ventas se escriben con el id local,
que el backend rechaza con 404 al sincronizar → se descartan por A-6.

Recomendación: no persistir un fallback automático cuando la sucursal no aparece; conservar la
selección y pedir al usuario que elija (o bloquear la operación hasta que lo haga). Mapear
sucursales local↔remota por `remoteId` en vez de comparar ids crudos. Test JVM: catálogo que emite
primero la lista local y luego la remota → la selección no cambia.

### M. Atender antes de habilitar sync/REMOTO en comercios reales

#### M-1. `usuario_id` lo decide el cliente en todas las escrituras

`VentaCreateSchema`, `EntradaCreateSchema`, `CorteCajaCreateSchema`, `ArticuloEdicionSchema` y
`DevolucionCreateSchema` reciben `usuario_id: str` libre, y el backend lo guarda tal cual. Un
usuario autenticado puede registrar operaciones a nombre de otro, y en
`LOCAL_CON_SINCRONIZACION` el valor es el UUID local de Room, que no corresponde a ningún usuario
del backend. La auditoría por usuario del backend no es confiable.

**Reproducido (2026-09-28):** venta con `usuario_id = "otro-usuario-inventado"` → 201 y guardada
con ese valor.

Recomendación: tomar el usuario de `usuario_actual` (el JWT) y conservar el valor del dispositivo
solo como dato informativo, si hace falta.

#### M-2. El backend no valida la consistencia de los montos

`create_venta` acepta `subtotal`, `total` y el `subtotal` de cada línea sin comprobar que
`Σ lineas.subtotal == subtotal`, `cantidad * precio_unitario == linea.subtotal` ni
`total == subtotal - descuento + impuestos`. `get_totales_corte` suma `Venta.total`. Lo mismo con
`CorteCajaCreateSchema` (`monto_esperado`, `diferencia`, y sin validar
`fecha_fin >= fecha_inicio`).

**Reproducido (2026-09-28):** venta con subtotal 10 y `total = 0.01` → 201, `total` guardado
0.01. Corte con `fecha_fin` en el año 2000 (anterior a `fecha_inicio`), `total_efectivo` 999 >
`total_ventas` 1 y `monto_esperado` −5 → 201.

Recomendación: un `model_validator` en los schemas que recalcule y compare (con `Decimal`, a 2
decimales).

#### M-3. El cursor del pull puede saltarse filas

- `updated_at` se asigna en Python antes del commit (`default=lambda: datetime.now(...)`). Una
  transacción que empieza antes pero hace commit después de que otra terminal ya avanzó su cursor
  queda con un `updated_at` menor que el cursor y **no se descarga nunca**.
- El cursor se guarda por entidad (`syncStateStore.pullCursor(entidad)`), no por
  `(entidad, sucursal)`. Si se cambia la sucursal seleccionada, el pull de la nueva arranca con el
  cursor de la anterior.

**Reproducido (2026-09-28):** con dos sesiones reales de SQLAlchemy contra Postgres (modelos del
backend, `RetiroEfectivo`): la sesión "lenta" hace `flush` (fija `updated_at`), la "rápida" hace
commit un segundo después; el pull ve solo la rápida y avanza el cursor; al hacer commit la lenta,
el pull con el cursor nuevo tampoco la ve. Resultado: `lenta row never pulled: True`. El segundo
punto se dio de hecho en el Xiaomi: por A-8, el pull de inventario corrió contra otra sucursal y
avanzó el cursor compartido.

Recomendación: restar una ventana de solape al cursor (ej. 2 minutos; el merge ya es idempotente),
o usar una secuencia monotónica asignada por el servidor. Guardar el cursor por sucursal.

#### M-4. Endurecimiento del backend expuesto a internet

**Reproducido (2026-09-28)**, contra el contenedor:
- `/auth/login` no tiene límite de intentos ni bloqueo: 20 intentos fallidos seguidos contra
  `admin` → los 20 responden 401, sin 429 ni bloqueo.
- Enumeración por tiempo: mediana de 5 intentos, usuario inexistente **115 ms** vs usuario
  existente con contraseña incorrecta **1056 ms**. La diferencia revela qué usernames existen.
  Calcular un hash ficticio en la rama "no existe" (`auth.py`) lo iguala.
- `/docs` y `/openapi.json` → 200 sin autenticación (FastAPI por defecto).
- Un `password_hash` con formato inválido se acepta en `POST /usuarios` (201), y el login de ese
  usuario responde **500** (`ValueError: Invalid salt` en `bcrypt.checkpw`, visto en el log del
  contenedor). Validar el prefijo `$2` en el schema.
- CORS `allow_origins=["*"]` (lectura): sin impacto con clientes Android, pero conviene restringirlo
  o quitarlo en producción.

#### M-5. Ciclo de vida de la sesión

- En modo LOCAL la sesión persistida (`SessionStore`) no expira nunca. **Reproducido (2026-09-28)
  en el Xiaomi:** tras `am force-stop` y arranque en frío, la app abre directo en la pantalla
  principal con "Sesión: admin", aunque el último login era del 2026-09-11 (17 días antes). En una
  terminal compartida entre turnos, eso rompe la atribución de ventas y cortes. Considerar
  expiración por inactividad o al cerrar turno.
- En `LOCAL_CON_SINCRONIZACION`, cuando el JWT expira (24 h) el primer 401 del `SyncWorker`
  dispara `AuthInterceptor.kt:44` → `logout()`, y el cajero vuelve al login a mitad de turno por un
  proceso en background. Es consistente con la decisión D3 de la Parte 32, pero conviene que el
  worker no cierre la sesión interactiva: que marque "sync requiere re-login" y lo muestre en
  Configuración. **Sin reproducir** (prueba detenida). Observado de paso: con una sesión sin JWT,
  Configuración muestra "Último error: sesion expirada", un mensaje engañoso cuando la sesión nunca
  tuvo token.

#### M-6. Roles y usuarios: estados inconsistentes

- `DELETE /roles/{id}` hace soft-delete aunque haya usuarios activos con ese rol, y
  `usuario_actual` carga el rol sin filtrar `deleted_at` (`dependencies.py:41`): los permisos de un
  rol borrado **siguen vigentes**.
- Nada impide desactivar o borrar al último usuario con módulo `usuarios`, ni borrarse a uno mismo,
  lo que puede dejar el backend sin administrador.

**Reproducido (2026-09-28):** rol con `venta`/`usuarios` + usuario asignado → `DELETE /roles` →
204; ese usuario hace `POST /ventas` → 201; el mismo usuario hace `DELETE /usuarios/<su id>` → 204.

Recomendación: rechazar el borrado de un rol con 409 si tiene usuarios asignados (o tratar un rol
borrado como sin módulos), y proteger al último administrador.

#### M-7. Sin pruebas instrumentadas

No existe `android/app/src/androidTest`. Quedan sin cubrir: el test `MigrationTestHelper` de
`MIGRATION_7_8` (diferido desde las Partes 24 y 28), las queries `WHERE isSynced = 0` de los
pushers, y la semántica numérica de `SUM(cantidadNum)`/`ORDER BY cantidadNum`. Tras el release,
cualquier migración nueva se ejecutará sobre bases con datos reales de comercios. Antes de publicar
conviene tener al menos el test de migración 7→8 como plantilla. Las pruebas de esta revisión en el
Xiaomi (lectura de la base Room con `run-as` + `sqlite3`) muestran que el estado real de Room es
donde aparecen los defectos de sync; automatizarlo tiene valor.

#### M-8. Venta: cantidades enteras e impuestos fijos en cero (decisión de producto)

`venta/VentaUiState.kt:14` (`cantidad: Int`) impide vender por peso o por fracción, aunque el
backend y el inventario usan `Numeric(12,3)`. `descuento` e `impuestos` están fijos en
`BigDecimal.ZERO` (líneas 35-36), así que el ticket no desglosa IVA. No es un bug; hay que decidir
si el primer release a comercios reales lo necesita, y dejarlo explícito en la ficha de la app.

#### M-9. Operación del backend productivo

`Dockerfile` levanta un único proceso `uvicorn` (`CLAUDE.md` §4 menciona Gunicorn con workers para
producción), sin proxy TLS delante, con `8000` publicado en todas las interfaces y sin estrategia de
respaldo de Postgres. Todo esto cae dentro de las decisiones abiertas de la Parte 34 (destino de
despliegue). Lo anoto para que el checklist incluya los respaldos, que hoy no menciona.

#### M-10. API key del proveedor de IA en texto plano en el dispositivo de pruebas (nuevo)

En el DataStore del Xiaomi (`files/datastore/configuracion.preferences_pb`) existe una clave
`ia_token` con la API key del proveedor **en texto plano**, junto a la versión cifrada
(`ia_token_ciphertext`/`ia_token_iv`). Ninguna versión commiteada del código escribe esa clave
(`git log -p` de `IaPreferences.kt` arranca ya con la versión cifrada, commit `daca1aa`): es un
residuo de un build de desarrollo previo al cifrado de la Parte 14, y nada lo limpia.

Impacto: una instalación nueva desde Play Store no lo tendría, pero la key de este dispositivo
está legible para cualquiera con acceso `run-as`/root o al respaldo, y con `allowBackup` (A-5) está
también en el respaldo de Google del teléfono. El valor no se reproduce en este documento.

Recomendación: **rotar esa API key** en el panel del proveedor; borrar la clave `ia_token` del
DataStore del dispositivo (por ejemplo, una limpieza única de claves obsoletas al arrancar la app,
que también sirve si otro teléfono de desarrollo la tiene).

### B. Mejoras / deuda menor

#### B-1. Primer `INSERT` concurrente de inventario → 500 (arrastrado de M-1 anterior)
Dos ventas o entradas simultáneas que crean la primera fila `(sucursal, artículo)` chocan con
`uq_inventario_sucursal_articulo`. **Reproducido (2026-09-28):** 6 ventas concurrentes del mismo
artículo en una sucursal sin fila de inventario → `[500, 201, 201, 201, 201, 201]`; log del
contenedor: `UniqueViolationError ... "uq_inventario_sucursal_articulo"`. La venta fallida se
revierte completa y el reintento del motor de sync (5xx → `Reintentar`) la resuelve, así que el
impacto es bajo. `INSERT ... ON CONFLICT DO NOTHING` + `SELECT ... FOR UPDATE` lo cerraría.

#### B-2. `devoluciones`: mismas deudas que ya se corrigieron en ventas
Validación de artículos con un `db.get` por línea (N+1, igual que el M-2 anterior) y `estado: str`
libre en lugar de `Literal` (igual que el M-3 anterior).

#### B-3. `AppLogger`: `SimpleDateFormat` compartido entre corrutinas
`logging/AppLogger.kt`: `SimpleDateFormat` no es thread-safe y `log()` corre en `Dispatchers.IO`
de forma concurrente (worker de sync + UI). Usar `DateTimeFormatter` (inmutable). Además
`purgeExpiredFiles()` lista el directorio en cada línea de log; basta con hacerlo al rotar el
archivo. Observado en el Xiaomi: el log de la app (`app-log-20260818-163132.txt`) sigue siendo un
único archivo de 800 KB desde el 2026-08-18 y registra una línea de prueba
"Verificacion Parte 5" en cada arranque (`PdvApplication.logSampleLinePerCategory`), que debería
quitarse antes del release.

#### B-4. Helper duplicado `toBigDecimalOrNull`
Existen 6 copias privadas de `runCatching { BigDecimal(this) }.getOrNull()` (Caja, Devolución,
Entrada, Inventario, Venta, CSV importer). Kotlin ya trae `String.toBigDecimalOrNull()`.

#### B-5. `runCatching` alrededor de una llamada suspendida
`data/ModeAwareAuthRepository.kt:44`: `runCatching { remote.login(...) }` también captura
`CancellationException`, lo que rompe la cancelación estructurada si el login se cancela. Capturar
`IOException`/`HttpException` explícitamente.

#### B-6. `DynamicHostInterceptor`: lectura bloqueante al construir
`runBlocking { preferences.deviceConfig.first() }` en la inicialización del singleton. Si Hilt lo
construye por primera vez desde el hilo principal, bloquea la UI con I/O de DataStore. Es un costo
de una sola vez; conviene medirlo con StrictMode en el build de debug. **Sin reproducir.**

#### B-7. Documentación desactualizada
- `CLAUDE.md` §1 dice "Partes 32-33 pendientes" (ambas están mergeadas); §3 dice que WorkManager
  "aún no está en el build"; §4 marca el exception handler global como "pendiente, Parte 25"; §7
  dice "Aún no hay CI/CD configurado".
- `CLAUDE.md` §2 y `docs/PLAN.md` (encabezado e historial) citan `docs/review_code.md` como fuente
  de las Partes 21-30; ese documento ahora es `docs/review_code_2026-09-28_1816.md`.
- `android/app/src/debug/res/xml/network_security_config.xml`: el comentario remite el
  endurecimiento a la "Parte 32" (ahora es la 34).
- `backend/app/schemas/inventario.py:22`: el comentario es incorrecto (ver A-1).

#### B-8. Textos de UI hardcodeados
Los composables usan literales en español en lugar de `strings.xml`. Para un solo mercado es
aceptable, pero bloquea la traducción y dispararía el lint `HardcodedText` si se activa.

#### B-9. CI no compila la variante de release
`android-ci.yml` corre `test lint`, pero no `assembleRelease` ni `testReleaseUnitTest`. Al activar
R8 (A-7), un fallo de reglas keep solo aparecería en el teléfono. Agregar `assembleRelease` al
workflow (hoy compila en verde, ver A-7).

#### B-10. Árbol de trabajo con cambios sin commitear
`docs/manual-tecnico.md` y `docs/ALCANCE-DOCS.md` están modificados, y
`docs/manual-tecnico.html`/`.pdf` están sin trackear (salida de `/docs-sync`). Decidir si los
artefactos generados se versionan o se agregan a `.gitignore` antes de etiquetar el release.

#### B-11. Advertencias de lint (nuevo)
`./gradlew lint` (2026-09-28): 0 errores, 30 advertencias. 25 son de versiones
(`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`), revisar antes del
release. Las demás:
- `InsecureBaseConfiguration`: el `base-config` con cleartext del build de debug (esperado; debe
  seguir fuera del release).
- `ConstantLocale`: `ConfiguracionScreen.kt:263` guarda `Locale.getDefault()` en un campo estático.
- `UnusedResources` + `ObsoleteSdkInt`: `mipmap-anydpi-v26/ic_launcher_round.xml` no se usa (el
  manifest no declara `android:roundIcon`) y la carpeta `-v26` sobra con `minSdk = 26`.
- `AutoboxingStateCreation` (hint): `CajaScreen.kt:340`, `mutableLongStateOf`.

#### B-12. Textos cortados en botones segmentados (nuevo)
Observado en el Xiaomi (1080x2400): "Local con sincronización" se corta como "sincronizació / n"
en Configuración, y en Usuarios los roles se ven como "administrad / or" y "encargado_t / urno".
Los `SegmentedButton` reparten el ancho en partes iguales. Etiquetas más cortas, `maxLines = 1`
con elipsis, o un selector vertical en pantallas angostas.

#### B-13. Advertencia de configuración de Alembic (nuevo)
`uv run pytest` emite `DeprecationWarning: No path_separator found in configuration` (Alembic
1.19). Agregar `path_separator = os` a `backend/alembic.ini`.

---

## 4. Recomendaciones priorizadas

| # | Acción | Módulo | Sev. | Verificación |
|---|--------|--------|------|------|
| 1 | `onupdate` de `inventario.updated_at` + marcar inventario sincronizado tras el push de venta/entrada + reconciliar dispositivos ya divergentes (A-1) | backend + android | A | Reproducido |
| 2 | Push de ajustes como delta idempotente con `with_for_update()` (A-2) | contrato + backend + android | A | Reproducido |
| 3 | No persistir un fallback de sucursal; mapear local↔remota por `remoteId` (A-8) | android | A | Reproducido |
| 4 | Cambio forzado de la contraseña bootstrap `admin123` (A-3) | backend + android | A | Reproducido |
| 5 | Sin default para `JWT_SECRET_KEY` fuera de dev (A-4) | backend | A | Reproducido |
| 6 | `allowBackup=false` / reglas de extracción + fallo de descifrado = sin sesión (A-5) | android | A | Reproducido |
| 7 | Estado "rechazada" visible para filas de sync con 4xx (A-6) | android (+ esquema) | A | Prueba existente |
| 8 | Parte 34 completa + pantalla principal + declaración de datos enviados a la IA (A-7) | ambos | A | Verificado |
| 9 | Rotar la API key de IA del dispositivo de pruebas y limpiar la clave `ia_token` (M-10) | operación + android | M | Observado |
| 10 | `usuario_id` desde el JWT; validación de montos (M-1, M-2) | backend | M | Reproducido |
| 11 | Cursor de pull con solape y por sucursal (M-3) | android | M | Reproducido |
| 12 | Rate limit de login, tiempo constante, docs cerrados, validación de `password_hash`, roles borrados (M-4, M-6) | backend | M | Reproducido |
| 13 | Test de migración de Room instrumentado (M-7) | android | M | — |
| 14 | Decisiones de producto: cantidades fraccionarias, IVA, expiración de sesión (M-5, M-8) | producto | M | M-5 reproducido |
| 15 | Deuda B-1..B-13 | ambos | B | — |

Sugerencia de planificación: agrupar 1, 2, 3, 7 y 11 en una Parte "Convergencia del sync" (toca
contrato y esquema, candidata a delegación `feature-dev`, `CLAUDE.md` §11); 4, 5, 6 y 12 en una
Parte "Endurecimiento de seguridad pre-release"; y 8 es la Parte 34 existente. Si el primer release
es solo LOCAL, basta con la segunda Parte y la 34. El punto 9 es operativo y conviene hacerlo ya.

---

## 5. Notas de verificación

### Suites y build (2026-09-28/29)
- Backend: `uv run pytest` → **170 passed**, 1 warning (B-13), en 12.96 s. `uv run alembic check`
  → "No new upgrade operations detected"; `alembic current` → `0013 (head)`. (La primera versión de
  este documento decía 109 tests, contados con `grep`; el número real es 170.)
- Android: `./gradlew testDebugUnitTest lint --max-workers=1` → `BUILD SUCCESSFUL`; 437 tests,
  0 fallos, 0 errores, 90 clases (resultados de `app/build/test-results`). Gradle marcó
  `testDebugUnitTest` como `UP-TO-DATE`: el código Android no cambió desde la corrida del
  2026-09-11, así que los resultados son de esa ejecución. Lint: 0 errores, 30 advertencias (B-11).
- `./gradlew assembleRelease` → `BUILD SUCCESSFUL`, APK sin firmar (A-7).

### Entorno de las pruebas
- Backend: `scripts/start-windows.ps1` (reconstruye la imagen con el código actual), Postgres con el
  volumen de desarrollo existente.
- Scripts de reproducción del backend (httpx + SQLAlchemy) ejecutados desde el scratchpad de la
  sesión, no versionados.
- Dispositivo: Xiaomi M2102J20SG (`adb` por USB para inspección; la app contra el backend por Wi-Fi
  LAN). Lectura de Room copiando `pdv.db` + `-wal` + `-shm` con `run-as` (técnica de la Parte 32).
  UI manejada con `uiautomator dump` + `input tap`.

### Efectos de las pruebas que siguen presentes
- **Base de desarrollo del backend**: datos de prueba con prefijo `REV-`: artículos
  (`REV-932402-*`), ventas/cortes de prueba, la sucursal `REV-932402-suc2` (no hay endpoint para
  borrar sucursales), el rol `REV-932402-rol` (borrado), `REV-sin-venta`, y los usuarios
  `rev-932402-badhash` (con hash inválido), `rev-932402-cajero` (borrado) y `revcaja`. Además un
  `PATCH` sobre SKU-98765 (cantidad 30, nombre "Prueba 1", precio 10) y una venta de 1 SKU-1234 de
  la "terminal B".
- **Xiaomi**, estado al detener las pruebas (2026-09-29):
  - La app se reinstaló durante A-5 y se restauraron los datos originales (copia del 2026-09-28
    19:00). Por la reinstalación la clave de Keystore es nueva: el token de IA guardado ya no se
    puede leer (hay que volver a capturarlo en Configuración) y hay que volver a conceder el
    permiso de cámara.
  - Después de la restauración se retomó la prueba de A-6: la app quedó en modo
    `LOCAL_CON_SINCRONIZACION`, sesión `admin` con JWT, y **sucursal seleccionada
    `REV-932402-suc2`** (efecto de A-8). Hay que volver a seleccionar "Sucursal principal" (o
    volver a modo LOCAL) antes de usar el teléfono.
  - Existe un usuario local nuevo `revcaja` (rol `caja`).
  - "Mantener pantalla encendida mientras carga" quedó activado (`svc power stayon usb`); el valor
    original era 0 (`adb shell svc power stayon false` lo revierte).
  - Dos copias completas de los datos de la app (incluida la key de M-10) quedaron en el
    scratchpad de la sesión; deben borrarse al terminar.
- Ningún hallazgo se corrigió en esta revisión; no se modificó código del repositorio.
