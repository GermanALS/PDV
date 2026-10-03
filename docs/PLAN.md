# PLAN.md — Plan de desarrollo del Punto de Venta

Este documento vive en `docs/PLAN.md` y contiene el diseño en desarrollo activo
del proyecto. A diferencia de `CLAUDE.md` (contexto estable: arquitectura ya
decidida, convenciones, comandos), aquí se documentan features en fase de
diseño, decisiones que aún pueden iterar, y el checklist de avance. Cuando una
Parte se estabiliza, su resumen final se migra a `CLAUDE.md`.

**Estado del documento**: las Partes 1-33 están implementadas y mergeadas; su
detalle (checklists cerrados, decisiones resueltas, criterios de verificación)
se archivó en `docs/PLAN-historico.md` para mantener este archivo enfocado.
Aquí quedan únicamente las Partes pendientes (34-39), el Backlog de trabajo
futuro y el historial de decisiones estructurales.

Origen de las Partes que salen de revisiones de código:
- Partes 21-30: revisión del 2026-08-30, hoy archivada como
  `docs/review_code_2026-09-28_1816.md`.
- Partes 34-39: revisión pre-release del 2026-09-28/29, `docs/review_code.md`
  (hallazgos A-n / M-n / B-n de ese documento; sus identificadores no
  coinciden con los de la revisión anterior).

Cada Parte tiene su sección `### Checklist` con
ítems verificables y criterios de éxito concretos (comando, archivo, o
comportamiento observable). Las Partes con decisiones sin cerrar llevan además
una subsección `### Decisiones abiertas` con checkboxes: son preguntas que el
agente debe hacer antes de implementar, nunca resolver por cuenta propia
(`CLAUDE.md` §9).

Cómo leen estos checklists las herramientas de sincronización (`/jira-sync`,
convención de ramas/commits): ver `CLAUDE.md` sección 10.

---

## Parte 34: Convergencia del inventario en el sync

*(Sale de los hallazgos A-1, A-2 y M-3 de `docs/review_code.md` (revisión
pre-release del 2026-09-28/29), los tres reproducidos. Con más de una
terminal por sucursal el inventario diverge sin avisar: (a) el backend no
actualiza `inventario.updated_at` en ventas/entradas, así que el pull
`?updated_since=` nunca ve esos cambios; (b) en Android, `VentaDao`/
`EntradaDao` dejan la fila de inventario con `isSynced = false` y nadie la
limpia, así que el pull nunca la sobrescribe; (c) `InventarioAjustePusher`
manda la cantidad absoluta y `PATCH /inventario` la impone, borrando ventas
de otras terminales; (d) el cursor del pull salta filas confirmadas tarde y
se comparte entre sucursales. En el Xiaomi ya había divergencias reales
(12 vs 20, 42 vs 48). Toca contrato, backend y motor de sync: delegación
`feature-dev` (`CLAUDE.md` §11).)*

Claves de Jira: **sin asignar todavía** (las asigna `/jira-sync`).

Objetivo: que dos terminales de la misma sucursal en
`LOCAL_CON_SINCRONIZACION` converjan al mismo stock tras un ciclo de sync,
sin perder ventas, ajustes ni entradas de ninguna de las dos.

### Checklist

**1. Contrato del inventario incremental**
- [ ] `docs/api-contract.md` §7: ajuste de stock por **delta** idempotente
  por `local_id` (endpoint o campo según Decisiones abiertas), aplicado con
  bloqueo de fila; §7.1 documenta que `updated_at` cambia con **todo**
  movimiento de stock y que el cliente consulta con una ventana de solape.
  Contrato primero (skill `sync-api-contract`). `needs-approval`.

**2. Backend**
- [ ] A-1(a): `Inventario.updated_at` se actualiza en cada cambio de
  `cantidad` (`onupdate` o asignación en `ventas.py`/`entradas.py`).
  Criterio: test nuevo — crear inventario, registrar una venta, y
  `GET /inventario?updated_since=<antes de la venta>` devuelve la fila;
  `uv run pytest` en verde.
- [ ] A-2: ajuste por delta con `with_for_update()` e idempotente por
  `local_id`. Criterio: test que reproduce el escenario de la revisión
  (stock 10, venta de 3 de la terminal B, ajuste +5 de la terminal A) y
  termina en 12; reintento con el mismo `local_id` no duplica.
  `uv run alembic check` sin drift si cambia el esquema (`schema-parity`).

**3. Android: push y pull**
- [ ] A-1(b): tras subir una venta/entrada, la fila de inventario afectada
  deja de quedar "sucia" (marcada sincronizada o sobrescrita con la cantidad
  del backend); la regla "gana lo local" del `InventarioPuller` aplica solo
  si hay movimientos pendientes de ese `(sucursal, artículo)`. `jvm-tests`:
  venta local sincronizada + cambio remoto → la fila local toma el valor
  remoto.
- [ ] A-2: `InventarioAjustePusher` envía la suma de los movimientos
  `ajuste` pendientes como delta con su `local_id`. `jvm-tests`.
- [ ] M-3: cursor de pull por `(entidad, sucursal)` y consulta con ventana
  de solape. `jvm-tests`: cambio de sucursal no hereda el cursor; una fila
  con `updated_at` anterior al cursor pero dentro de la ventana se baja.

**4. Reconciliación y verificación**
- [ ] Reconciliación única de dispositivos que ya divergen (política en
  Decisiones abiertas), ejecutada al primer ciclo de sync tras actualizar.
  `jvm-tests`.
- [ ] Verificación en el Xiaomi M2102J20SG con una segunda "terminal"
  simulada contra el backend (curl), repitiendo las pruebas de A-1/A-2 de
  `review_code.md`: tras un ciclo, el stock del teléfono y el del backend
  coinciden y no se registran conflictos espurios. `needs-device`.

### Decisiones abiertas

- [ ] ¿Ajuste por delta como endpoint nuevo (`POST /ajustes-inventario`) o
  como campos `delta` + `local_id` en el `PATCH /inventario/{id}` actual?
  El PATCH absoluto puede quedarse para el modo REMOTO interactivo.
- [ ] Política de reconciliación de dispositivos ya divergentes: ¿gana el
  backend en las filas sin movimientos pendientes, o se registra cada
  diferencia en `sync_conflicts` para que el admin decida?
- [ ] Tamaño de la ventana de solape del cursor (propuesta: 2 minutos; el
  merge ya es idempotente), o secuencia monotónica asignada por el servidor.

---

## Parte 35: Sucursal estable y rechazos visibles del sync

*(Sale de los hallazgos A-8, A-6 y M-5 (2º punto) de `docs/review_code.md`.
A-8, reproducido dos veces en el Xiaomi: al pasar a
`LOCAL_CON_SINCRONIZACION`, `ConfiguracionViewModel` persiste
`sucursales.firstOrNull()` cuando la sucursal seleccionada no aparece en el
catálogo emitido en ese momento; el catálogo alterna entre local y remoto
(ids distintos para la misma sucursal) y la terminal termina operando en
otra sucursal sin avisar. A-6: una fila rechazada con 4xx (ej. 403 por rol)
se marca `isSynced = true` sin `remoteId` y solo deja una línea de log —
ventas que nunca llegan al backend. M-5: un 401 del `SyncWorker` cierra la
sesión interactiva del cajero.)*

Claves de Jira: **sin asignar todavía** (las asigna `/jira-sync`).

Objetivo: que la sucursal de trabajo solo cambie por decisión del usuario, y
que toda fila que el backend rechace quede visible y recuperable.

### Checklist

**1. Selección de sucursal estable (A-8)**
- [ ] `ConfiguracionViewModel` no persiste un fallback automático: si la
  sucursal guardada no está en el catálogo emitido, conserva la selección y
  pide al usuario que elija (o bloquea las operaciones que la requieren).
  Sucursal local y remota se relacionan por `remoteId`, no por id crudo.
  `jvm-tests`: catálogo que emite primero la lista local y luego la remota
  → la selección persistida no cambia.
- [ ] Verificación en el Xiaomi: repetir la secuencia de A-8 de
  `review_code.md` (LOCAL → `LOCAL_CON_SINCRONIZACION` sin JWT, re-login
  con JWT, con una sucursal remota alfabéticamente anterior) y comprobar que
  la sucursal no cambia. `needs-device`.

**2. Rechazos de sync visibles (A-6)**
- [ ] Estado de rechazo propio (no `isSynced`) con el código HTTP, en la
  forma que se decida; migración de Room + `docs/schema-pos.json` +
  (si aplica) backend alineados. `schema-parity`.
- [ ] Cada rechazo se registra en `sync_conflicts` (visible en el panel de
  la Parte 19) y la sección Sincronización de Configuración muestra el
  número de filas rechazadas. `jvm-tests`: 403 en una venta → estado
  rechazado + conflicto registrado + contador > 0.
- [ ] Verificación en el Xiaomi con el escenario preparado en la revisión
  (usuario con rol local `caja` y rol backend sin `venta`): la venta
  rechazada aparece en el panel y en el contador. `needs-device`.

**3. Sesión del worker (M-5, 2º punto)**
- [ ] Un 401 recibido por el `SyncWorker` no cierra la sesión interactiva:
  se registra como "sync requiere re-login" y se muestra en Configuración.
  Con una sesión que nunca tuvo JWT, el mensaje lo dice así (hoy muestra
  "sesion expirada"). `jvm-tests` sobre `AuthInterceptor`/orquestador.

### Decisiones abiertas

- [ ] Forma del estado de rechazo: ¿columna en cada entidad transaccional
  o tabla aparte de rechazos?
- [ ] Filas ya descartadas en dispositivos existentes (marcadas
  `isSynced = true` sin `remoteId`): ¿se reintentan una vez tras la
  migración, se reportan como rechazadas, o se dejan como están?
- [ ] Sucursal no encontrada en el catálogo: ¿bloquear operaciones hasta
  que el usuario elija, o solo avisar?

---

## Parte 36: Endurecimiento de seguridad pre-release

*(Sale de los hallazgos A-3, A-4, A-5, M-4, M-6 y M-10 de
`docs/review_code.md`, reproducidos contra el backend y en el Xiaomi:
`admin/admin123` sin cambio forzado (visible en claro en el APK de release);
JWT falsificable con el `JWT_SECRET_KEY` por defecto; crash en cada arranque
tras restaurar un respaldo (`allowBackup` + clave de Keystore no
respaldada); login sin límite de intentos y con enumeración por tiempo
(115 ms vs 1056 ms); `/docs` públicos; `password_hash` inválido → 500; rol
borrado que sigue otorgando permisos; API key de IA residual en texto plano
en el dispositivo de pruebas.)*

Claves de Jira: **sin asignar todavía** (las asigna `/jira-sync`).

Objetivo: que un backend expuesto a internet y una app distribuida en Play
Store no tengan credenciales ni secretos por defecto, no se caigan tras una
restauración y resistan los ataques básicos de login.

### Checklist

**1. Credenciales de arranque (A-3)**
- [ ] El administrador bootstrap (backend y dispositivo) debe cambiar su
  contraseña en el primer login, según la decisión abierta. Criterio:
  login con `admin/admin123` no da acceso a ningún módulo hasta cambiarla;
  `jvm-tests` + test de backend.

**2. Backend (A-4, M-4, M-6)**
- [ ] A-4: el backend no arranca si falta `JWT_SECRET_KEY` (o si tiene el
  valor de desarrollo fuera de dev). Criterio: test + arranque del
  contenedor sin la variable falla con un mensaje claro.
- [ ] M-4: límite de intentos de login, tiempo de respuesta constante para
  usuario inexistente, `/docs`/`/openapi.json` deshabilitados en producción,
  `password_hash` validado (prefijo `$2`) → `422`. Criterio: tests por
  cada punto; repetir las mediciones de `review_code.md` (20 intentos,
  tiempos, 500 por hash inválido) con resultado corregido.
- [ ] M-6: `DELETE /roles/{id}` con usuarios asignados → `409`; un rol
  borrado no otorga módulos; no se puede desactivar/borrar al último
  usuario con módulo `usuarios` ni a uno mismo. Contrato §10/§11
  actualizado primero. Tests happy path + error.

**3. Android (A-5, M-10)**
- [ ] A-5: `allowBackup="false"` o `dataExtractionRules` que excluyan
  DataStore y la base; un fallo de descifrado en `SessionStore`/
  `IaPreferences` se trata como "sin sesión / sin token" y limpia las
  claves. `jvm-tests` con un `TokenCipher` que lanza.
- [ ] M-10: limpieza única de claves obsoletas del DataStore (incluida
  `ia_token` en claro) al arrancar. `jvm-tests`. Tarea operativa aparte:
  rotar la API key del proveedor de IA del dispositivo de pruebas.
- [ ] Verificación en el Xiaomi: repetir la reproducción de A-5 de
  `review_code.md` (instalación limpia + archivos de una sesión con JWT) →
  la app abre en el login sin cerrarse. `needs-device`.

### Decisiones abiertas

- [ ] Contraseña inicial del admin: ¿cambio forzado en el primer login, o
  contraseña provista por variable de entorno en el despliegue (backend) y
  asistente inicial (dispositivo)?
- [ ] Límite de intentos de login: ¿en la app (contador en base/memoria) o
  delegado al proxy/infraestructura del destino de despliegue (Parte 39)?
- [ ] `allowBackup`: ¿desactivar por completo o mantener respaldo
  excluyendo solo DataStore y la base?

---

## Parte 37: Integridad de datos del backend

*(Sale de los hallazgos M-1, M-2, B-1 y B-2 de `docs/review_code.md`,
reproducidos contra el backend: `usuario_id` libre en todas las escrituras
(se aceptó "otro-usuario-inventado"); montos sin validar (venta con
subtotal 10 y total 0.01 → 201; corte con fechas invertidas → 201); primer
insert concurrente de inventario → 500 (1 de 6); devoluciones con N+1 y
`estado` libre.)*

Claves de Jira: **sin asignar todavía** (las asigna `/jira-sync`).

Objetivo: que el backend sea la fuente confiable de atribución y de montos,
sin depender de que el cliente mande datos consistentes.

### Checklist

**1. Contrato**
- [ ] `docs/api-contract.md` §5/§6/§7/§8/§9: el usuario de cada escritura
  sale del JWT; nuevos `422` por montos inconsistentes y por
  `fecha_fin < fecha_inicio`; `estado` de devolución con valores cerrados.
  Contrato primero (skill `sync-api-contract`). `needs-approval`.

**2. Backend**
- [ ] M-1: `usuario_id` derivado de `usuario_actual` en ventas, entradas,
  cortes, retiros, devoluciones y ajustes. Tests happy path + error.
- [ ] M-2: validadores de montos con `Decimal` a 2 decimales
  (`Σ lineas.subtotal == subtotal`, `cantidad * precio_unitario ==
  linea.subtotal`, `total == subtotal - descuento + impuestos`) y de cortes
  (fechas, totales). Criterio: los payloads de la reproducción de
  `review_code.md` dan `422`.
- [ ] B-1: el primer insert concurrente de inventario no da `500`
  (`INSERT ... ON CONFLICT` + `FOR UPDATE`). Criterio: test con ventas
  concurrentes sin fila previa → todas `201`.
- [ ] B-2: `create_devolucion` valida artículos con una sola query y
  `estado` como `Literal`. `uv run pytest` en verde.

**3. Cliente Android**
- [ ] Ajustes del cliente y de los pushers si el contrato cambia (ej. dejar
  de enviar `usuario_id`). `jvm-tests`.

### Decisiones abiertas

- [ ] `usuario_id` del dispositivo: ¿se deja de enviar, o se guarda aparte
  como dato informativo (ej. "usuario local") junto al usuario del JWT?
- [ ] Ventas históricas ya guardadas con montos inconsistentes: ¿se
  auditan con una consulta única o se ignoran?

---

## Parte 38: Calidad y deuda menor pre-release

*(Sale de los hallazgos M-7, B-3, B-4, B-5, B-6, B-8, B-11, B-12 y B-13 de
`docs/review_code.md`. Ninguno bloquea por sí solo, pero varios son
visibles para el usuario (botones con texto cortado, línea de prueba en el
log) o protegen las migraciones futuras sobre datos reales (M-7).)*

Claves de Jira: **sin asignar todavía** (las asigna `/jira-sync`).

Objetivo: dejar el código y la app sin deuda visible ni riesgos conocidos
de mantenimiento antes del primer release.

### Checklist

**1. Pruebas instrumentadas (M-7)**
- [ ] `android/app/src/androidTest` con un test `MigrationTestHelper` de
  `MIGRATION_7_8` (diferido desde las Partes 24 y 28) como plantilla para
  las migraciones futuras, y una prueba de las queries `isSynced = 0`.
  Criterio: `./gradlew connectedAndroidTest` en verde en el Xiaomi.
  `needs-device`.

**2. Android menor**
- [ ] B-3: `AppLogger` con `DateTimeFormatter` (sin `SimpleDateFormat`
  compartido), purga solo al rotar, y sin la línea "Verificacion Parte 5"
  de cada arranque (`PdvApplication.logSampleLinePerCategory`). `jvm-tests`.
- [ ] B-4/B-5: `String.toBigDecimalOrNull()` de Kotlin en lugar de las 6
  copias privadas; `ModeAwareAuthRepository` captura `IOException`/
  `HttpException` en vez de `runCatching`. `jvm-tests`.
- [ ] B-6: medir con StrictMode en debug si `DynamicHostInterceptor` lee
  DataStore en el hilo principal; corregir solo si se confirma.
  `needs-device`.
- [ ] B-11: advertencias de lint no relacionadas con versiones resueltas
  (`ConstantLocale` en `ConfiguracionScreen.kt:263`, `ic_launcher_round` +
  carpeta `mipmap-anydpi-v26`, `mutableLongStateOf` en `CajaScreen.kt`).
  Criterio: `./gradlew lint` sin esas advertencias.
- [ ] B-12: los botones segmentados de Configuración y Usuarios no cortan
  palabras en el Xiaomi (1080x2400). `needs-device`.

**3. Backend menor**
- [ ] B-13: `path_separator = os` en `backend/alembic.ini`. Criterio:
  `uv run pytest` sin el `DeprecationWarning`.

### Decisiones abiertas

- [ ] B-8: mover los textos de UI a `strings.xml`, ¿dentro del primer
  release o después?
- [ ] B-11: ¿se actualizan las dependencias marcadas por lint
  (`GradleDependency`, `NewerVersionAvailable`) en esta Parte o en una
  Parte de mantenimiento aparte?

---

## Parte 39: Primer release productivo (APK firmado + backend desplegado)

*(Sale del análisis "qué falta para el primer release productivo" del
2026-09-08. Absorbe el hallazgo B-7 —R8/shrinking, diferido en la Parte 29
"hasta que haya distribución real"— y cierra el `[TODO]` de destino de
despliegue de `CLAUDE.md` §7. Era la Parte 34; se renumeró a 39 el
2026-10-02 para ir después de las Partes 34-38 que salen de la revisión
pre-release (`docs/review_code.md`), y se amplió con sus hallazgos A-7,
M-9, B-9 y B-10 y con las decisiones de producto M-5 y M-8. Verificado el
2026-09-29: `./gradlew assembleRelease` produce `app-release-unsigned.apk`
(36.5 MB) sin firmar y sin ofuscar. Desde la Parte 31 el modo REMOTO ya no
depende de `adb reverse` (Wi-Fi LAN), pero sigue sin un backend HTTPS
productivo.)*

Claves de Jira: **sin asignar todavía**. Las asigna `/jira-sync` cuando se
sincronice esta Parte; no se pre-escriben en este archivo (evita el problema
de claves fantasma que tuvo la Parte 30).

Objetivo: un APK (o AAB) de release firmado, ofuscado y verificado en
dispositivo, apuntando a un backend FastAPI desplegado y accesible por HTTPS
desde fuera de la LAN.

### Checklist

**1. Firma de release**
- [ ] `signingConfigs.release` en `android/app/build.gradle.kts` que lee de un
  `keystore.properties` gitignoreado (o variables de entorno) y se aplica al
  `buildType release`. Criterio: `./gradlew assembleRelease` produce un APK y
  `apksigner verify --print-certs` lo reporta firmado con el certificado de
  release, distinto del de debug.
- [ ] Keystore de release generado con `keytool`, respaldado fuera del repo;
  `keystore.properties` y el `.jks`/`.keystore` en `.gitignore`. Criterio:
  `git status` sobre un árbol limpio no muestra el keystore ni credenciales;
  `assembleDebug` compila sin el archivo y `assembleRelease` falla con un
  mensaje claro si falta.
- [ ] `CLAUDE.md` §7 documenta dónde vive el keystore, cómo se pasan las
  contraseñas al build, y el procedimiento de rotación — sin incluir el
  keystore ni los secretos en el repo.

**2. Despliegue del backend productivo**
- [ ] Backend desplegado en el destino elegido (ver Decisiones abiertas) con
  `docker-compose.prod.yml` real: `JWT_SECRET_KEY` fuerte, credenciales de
  Postgres propias, sin publicar `5432` al exterior. Criterio:
  `GET https://<dominio>/api/v1/health` responde `{"status":"ok"}` sobre TLS
  válido desde una red distinta a la LAN de desarrollo.
- [ ] Migraciones Alembic aplicadas en el entorno productivo. Criterio:
  `alembic current` en prod devuelve el mismo head que el repo.
- [ ] `CLAUDE.md` §7: destino de despliegue definido (ya no `[TODO]`), pasos
  de arranque/actualización y rotación de `JWT_SECRET_KEY`.
- [ ] M-9 (`docs/review_code.md`): proceso de producción definido (workers
  de Gunicorn/Uvicorn según `CLAUDE.md` §4, proxy TLS delante, `8000` no
  publicado directamente) y respaldo automático de Postgres con una
  restauración probada. Criterio: un respaldo restaurado en una base vacía
  levanta el backend con `alembic current` en el head y los datos íntegros.

**3. URL base productiva en la app**
- [ ] El módulo Configuración permite fijar y persistir la URL base del
  backend remoto (`https`, host, puerto), o se hornea un valor productivo por
  defecto. Criterio: en el Xiaomi, modo REMOTO contra `https://<dominio>`
  completa login + una operación de sync sin `adb reverse`. `needs-device`.
  Nota (2026-10-02): la Parte 31 ya agregó esquema http/https, host y
  puerto editables en Configuración y en el login; queda decidir el valor
  por defecto (ver Decisiones abiertas) y verificar contra HTTPS real.
- [ ] Cleartext HTTP deshabilitado en release (network security config /
  `usesCleartextTraffic=false`), con allowlist explícito solo si se decide
  mantener pruebas locales. Criterio: una petición `http://` a un host
  productivo falla; `https://` funciona.

**4. Endurecimiento del build de release (hallazgo B-7 de `docs/review_code_2026-09-28_1816.md`)**
- [ ] `isMinifyEnabled = true` e `isShrinkResources = true` en el `buildType
  release`. Criterio: `./gradlew assembleRelease` en verde; el APK de release
  pesa menos que el equivalente sin shrink.
- [ ] Reglas keep en `android/app/proguard-rules.pro` para Room, Hilt,
  kotlinx-serialization, Retrofit y ML Kit barcode. Criterio: con el APK de
  release firmado instalado en el Xiaomi, los 6 módulos abren y una venta +
  un sync end-to-end completan sin `ClassNotFoundException` ni errores de
  (de)serialización.
- [ ] Verificación en el dispositivo físico Xiaomi M2102J20SG con el APK de
  release (firmado y ofuscado), no solo debug. `needs-device`.

**5. Preparación del release**
- [ ] `versionCode` / `versionName` subidos desde `1` / `"0.1"` al primer
  valor de release según el esquema de `CLAUDE.md` §7 (ej. `1.0.0` ->
  `versionCode 10000`), en el commit que prepara el release.
- [ ] Regla de `outputFileName` para el variant `release` siguiendo la
  convención `{app}-{proposito}-{tipo-build}-v{version}` (hoy solo existe
  para `debug`). Criterio: `assembleRelease` emite un archivo con ese nombre.
- [ ] Suite unitaria en verde sobre el variant de release:
  `./gradlew testReleaseUnitTest` (mostrar salida completa). `jvm-tests`.
- [ ] B-9 (`docs/review_code.md`): `.github/workflows/android-ci.yml`
  compila la variante de release (`assembleRelease`) para que un fallo de
  reglas keep de R8 aparezca en CI y no en el teléfono. Criterio: el
  workflow corre en verde en el PR de esta Parte.
- [ ] B-10 (`docs/review_code.md`): decidido y aplicado si
  `docs/manual-tecnico.html`/`.pdf` (salida de `/docs-sync`) se versionan o
  se agregan a `.gitignore`. Criterio: `git status` limpio antes de
  etiquetar el release.

**6. Distribución**
- [ ] Artefacto generado según el canal elegido (ver Decisiones abiertas):
  APK firmado para sideload, o AAB (`./gradlew bundleRelease`) para Play
  Console. Criterio: el artefacto instala/valida en un dispositivo limpio
  distinto del de desarrollo.
- [ ] Si el canal es Play Console: cuenta de desarrollador, ficha (icono,
  capturas, descripción), política de privacidad publicada (URL), formulario
  Data Safety, cuestionario de content rating y justificación del permiso
  `CAMERA`. Criterio: la app pasa la revisión de un track interno de Play.
- [ ] Flujo de permiso `CAMERA` en runtime (escaneo de código de barras)
  probado en el dispositivo con el build de release: concesión y denegación.
  `needs-device`.
- [ ] A-7 (`docs/review_code.md`): la política de privacidad y el formulario
  Data Safety declaran los datos que el asistente IA envía al proveedor
  (nombres, precios, costos y totales de caja, `ia/EstadoPuntoVenta.kt`) y
  el uso de `RECORD_AUDIO` (dictado); la app pide consentimiento explícito
  al activar la IA. Criterio: el consentimiento aparece en el Xiaomi al
  activar la IA y sin él no se envía nada al proveedor. `needs-device`.

**7. Pantalla principal y tema (hallazgo A-7)**
- [ ] La pantalla principal deja de mostrar el texto de prueba de la
  Parte 1 ("Hola desde una funcion local de Kotlin", "Backend: ok
  (v0.1.0)") y muestra contenido propio del punto de venta (sucursal,
  usuario en turno, estado de conexión). `jvm-tests` del ViewModel +
  verificación visual en el Xiaomi. `needs-device`.
- [ ] Tema propio de la Activity (paleta "Recibo" de `CLAUDE.md` §3) y
  pantalla de arranque con `core-splashscreen` (librería nueva, señalar
  según `CLAUDE.md` §9), sin el destello claro del tema de plataforma en el
  arranque en frío con modo oscuro. `needs-device`.

### Decisiones abiertas

- [ ] **Canal de distribución**: Play Console (AAB; requiere cuenta, fichas,
  Data Safety y revisiones, pero gestiona updates y firma) vs sideload de APK
  firmado (sin store, distribución manual). Afecta los grupos 1, 5 y 6.
- [ ] **Destino de despliegue del backend** (`CLAUDE.md` §7 lo tiene como
  `[TODO]` desde el inicio): Render / Fly.io / VPS propio / AWS. A sopesar:
  costo, TLS y dominio incluidos, correr el `docker-compose` tal cual,
  backups de Postgres.
- [ ] **Play App Signing** (solo si el canal es Play): ¿se delega la clave de
  firma de la app a Google, o se mantiene la clave localmente?
- [ ] **URL base**: ¿siempre configurable por el usuario en Configuración, o
  valor productivo por defecto con override oculto para soporte?
- [ ] **Expiración de la sesión en modo LOCAL** (M-5 de `docs/review_code.md`,
  reproducido: la app abrió con la sesión de `admin` de 17 días antes): ¿la
  sesión persistida expira por inactividad, al cerrar turno/corte final, o
  se mantiene como hoy? Si entra al release, se agregan sus ítems a esta
  Parte.
- [ ] **Venta por fracción/peso e IVA en el ticket** (M-8 de
  `docs/review_code.md`): hoy `cantidad` es entera en el carrito y
  `descuento`/`impuestos` están fijos en cero. ¿Lo necesita el primer
  release a comercios reales, o se declara la limitación en la ficha de la
  app y se planifica después?

---

## Backlog (trabajo futuro, fuera del alcance actual)

- **Robustez ante `content` vacío del proveedor de IA** (hallazgo Parte
  20, 2026-08-30): DeepSeek `deepseek-chat` en modo `json_object` devuelve
  `content` vacío de forma intermitente, lo que hoy corta la conversación
  con "El proveedor no devolvió ninguna respuesta". Evaluar un reintento
  único de la misma llamada ante `content` vacío, y/o fijar `max_tokens`
  explícito en `ChatCompletionRequestDto`. Toca el camino compartido de IA
  (Parte 15/16), no solo el FAQ. El diagnóstico (`finish_reason` + `usage`
  en el log de `LlmClient`) ya está para juntar más muestras.
- **Multi-tenancy compartido (alternativa no elegida)**: en vez de un
  backend por franquicia, un solo backend/Postgres podría servir a varias
  franquicias separadas lógicamente por un `tenant_id` en
  `sucursales`/`articulos`/`ventas`/etc., con autenticación consciente de
  tenant y filtrado por tenant en cada query. Evaluado el 2026-08-25 junto
  con la Parte 18 sub-parte G y descartado por ahora en favor de un
  deployment Docker separado por franquicia (más simple, aislamiento
  total, sin migración de esquema). Reconsiderar solo si administrar N
  deployments se vuelve operativamente costoso.

---

## Historial de decisiones clave

- El orden original numeraba: Plan → Estructura → Frontend → Login → Modelo
  de BD → Backend local/remoto → UI+Local+Servidor → IA (3 partes) → Gestión
  de usuarios → Logs. Se reordenó a: Plan → Estructura → **Modelo de BD** →
  Frontend → Login → Backend local/remoto → UI+Local+Servidor → **Gestión de
  usuarios** → IA (3 partes) → Logs, para que el esquema de datos exista
  antes de construir la UI sobre datos estáticos, y para que el sistema de
  permisos exista antes de dar autonomía de escritura a la IA.
- Se eliminó la referencia a un directorio `frontend/` y a un archivo
  `AGENTS.md` de la Parte 1 original: la app es nativa (Kotlin + Jetpack
  Compose), no un frontend web separado. `CLAUDE.md` en la raíz del
  monorepo cumple la función de contexto que originalmente se planteó para
  `AGENTS.md`.
- **2026-08-17**: se fusionaron las Partes 4 (frontend), 6a (repositorio
  local), 6b (repositorio remoto y sync) y 7 (wiring) —organizadas por capa,
  es decir, todas las UIs de los 7 módulos, luego todos los repos locales,
  etc.— en Partes independientes por módulo (Parte 5: Configuración, Partes
  6-11: los 6 módulos restantes), cada una con los mismos 4 sub-pasos (UI →
  repositorio local → repositorio remoto/contrato → wiring), para evitar
  revisitar cada módulo en fases no consecutivas y validar cada módulo de
  punta a punta antes de pasar al siguiente. El módulo Configuración se
  adelantó a ser el primero del bloque (antes iba último, como módulo 7)
  porque los demás módulos dependen de que `BackendMode` ya funcione de
  verdad para probar sus modos remoto y local-con-sincronización. La Parte
  de login ficticio (antes Parte 5) se reubicó como Parte 4, justo antes de
  este bloque, porque varios módulos requieren atribuir acciones al usuario
  en turno desde su primera implementación. Esto recorrió la numeración de
  las Partes 8-12 originales a 12-16.
- **2026-08-17 (continuación)**: se detectó que la Parte 5 (Configuración,
  motor de sync) y la entonces Parte 14 (Refinamiento de IA) ya
  referenciaban "el log de la Parte 16" antes de que Logs se hubiera
  construido — Logs estaba al final del documento pese a ser infraestructura
  compartida que otras partes necesitan invocar desde mucho antes, igual que
  el motor de sync. Se movió Logs a una nueva Parte 5, justo después del
  login (de donde toma el campo `usuario`) y antes de Configuración, y se
  agregó una nota de integración explícita en cada módulo (Partes 6-12) y en
  Gestión de usuarios/Refinamiento de IA indicando qué categoría de log usa
  cada uno. Esto recorrió Configuración y los módulos de la Parte 5-11
  anterior a la Parte 6-12 actual, y el resto de las partes (antes 12-16) a
  13-16.
- **2026-08-31**: se incorporaron los hallazgos de la revisión de código
  `docs/review_code.md` (2026-08-30) como Partes 21-29, siguiendo el esquema:
  cada hallazgo tipo A es una Parte propia (21 auth JWT, 22 rotación de
  secretos, 23 idempotencia de POST de sync, 24 migraciones de Room); los
  tipo M se agruparon por relación (25 robustez del backend, 26 despliegue y
  CI, 27 Navigation-Compose, 28 logging y agregaciones Android; M-10 se
  absorbió en la Parte 21 porque sin auth no tiene sentido); los tipo B van
  juntos en la Parte 29, salvo B-2 (Parte 26) y B-3 (Parte 22) por afinidad
  temática. `docs/review_code.md` queda como documento fuente; ninguna Parte
  nueva se implementó en esa sesión. (Ese documento se archivó el 2026-09-28
  como `docs/review_code_2026-09-28_1816.md`.)
- **2026-09-07** (Parte 29, B-9): las Partes 1-28 (todas mergeadas) se
  movieron a `docs/PLAN-historico.md` con sus checklists finales y decisiones
  resueltas; `docs/PLAN.md` quedó solo con lo activo (Parte 29, Parte 30,
  Backlog, este historial). El encabezado de `PLAN.md` — que seguía diciendo
  "16 Partes" / "Partes 2-16" / "Partes 1-20 implementadas" — se reescribió.
  En la misma Parte se decidió pinear `backend/requirements.txt` con `==`
  (B-1) y planificar la migración a `uv` como Parte 30 aparte, y diferir R8
  en el build de release (B-7) hasta que haya distribución real.
- **2026-09-09**: se insertó la Parte 32 (motor de sincronización diferida de
  `LOCAL_CON_SINCRONIZACION`) a partir del análisis "qué falta para terminar
  el modo local + la sync remota"; la Parte 32 anterior (primer release
  productivo) pasó a Parte 33, al final del listado. El análisis encontró que
  `CLAUDE.md` describía el motor de sync como implementado cuando solo existen
  las primitivas puras (`LastWriteWinsSyncEngine`, `EventoAditivoCombiner`,
  `MigrationPlanner`) sin orquestación, sin WorkManager y sin pull —
  `LOCAL_CON_SINCRONIZACION` no sube ni baja cambios todavía. Alcance de la
  Parte 32: push + pull de entidades transaccionales
  (`ventas`/`entradas`/`cortes_caja`/`retiros_efectivo`/`devoluciones`/
  `inventario`). Se agregó a la lista de delegación a subagentes feature-dev
  de `CLAUDE.md` §11 y `.claude/commands/parte.md`.
- **2026-09-11**: se insertó la Parte 33 (actualización de
  `docs/manual-tecnico.md`) a partir de un hallazgo del cierre de la Parte 32:
  el manual técnico, creado el 2026-09-09, quedó desactualizado desde su
  creación (numeración de Partes vieja, sin el motor de sync real) y apareció
  como archivo sin trackear durante ese cierre, excluido del commit por
  estar stale. La Parte 33 anterior (primer release productivo) pasó a Parte
  34, al final del listado.
- **2026-10-02**: se incorporaron los hallazgos de la revisión pre-release
  `docs/review_code.md` (2026-09-28/29: lectura estática + reproducción
  contra el backend dockerizado y en el Xiaomi) como Partes 34-38, agrupados
  por tema a decisión del usuario: 34 convergencia del inventario en el sync
  (A-1, A-2, M-3), 35 sucursal estable y rechazos visibles (A-8, A-6, M-5
  2º punto), 36 seguridad pre-release (A-3, A-4, A-5, M-4, M-6, M-10),
  37 integridad de datos del backend (M-1, M-2, B-1, B-2), 38 calidad y
  deuda menor (M-7, B-3..B-6, B-8, B-11..B-13). La Parte 34 anterior
  (primer release productivo, sin claves de Jira) pasó a Parte 39, al final
  del listado, ampliada con A-7, M-9, B-9 y B-10, y con las decisiones de
  producto M-5 (1er punto, expiración de sesión) y M-8 (venta por fracción,
  IVA) como Decisiones abiertas. B-7 (documentación desactualizada) se
  corrigió en la misma sesión, fuera de cualquier Parte. Las Partes 29-33,
  ya cerradas, se archivaron en `docs/PLAN-historico.md` (el ítem B-7 de la
  Parte 29, R8, quedó absorbido por la Parte 39). La Parte 34 se agrega a la
  lista de delegación a subagentes feature-dev de `CLAUDE.md` §11 y
  `.claude/commands/parte.md`. Ninguna Parte nueva se implementó en esa
  sesión.
