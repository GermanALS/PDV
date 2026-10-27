# PLAN.md — Plan de desarrollo del Punto de Venta

Este documento vive en `docs/PLAN.md` y contiene el diseño en desarrollo activo
del proyecto. A diferencia de `CLAUDE.md` (contexto estable: arquitectura ya
decidida, convenciones, comandos), aquí se documentan features en fase de
diseño, decisiones que aún pueden iterar, y el checklist de avance. Cuando una
Parte se estabiliza, su resumen final se migra a `CLAUDE.md`.

**Estado del documento**: las Partes 1-33 están implementadas y mergeadas; su
detalle (checklists cerrados, decisiones resueltas, criterios de verificación)
se archivó en `docs/PLAN-historico.md` para mantener este archivo enfocado.
Aquí quedan únicamente las Partes pendientes (34-40), el Backlog de trabajo
futuro y el historial de decisiones estructurales.

Origen de las Partes que salen de revisiones de código:
- Partes 21-30: revisión del 2026-08-30, hoy archivada como
  `docs/review_code_2026-09-28_1816.md`.
- Partes 34 y 36-39: revisión pre-release del 2026-09-28/29,
  `docs/review_code.md` (hallazgos A-n / M-n / B-n de ese documento; sus
  identificadores no coinciden con los de la revisión anterior). La Parte 40
  es el primer release; la 35 salió de la ejecución de la 34 (2026-10-12).

Cada Parte tiene su sección `### Checklist` con
ítems verificables y criterios de éxito concretos (comando, archivo, o
comportamiento observable). Las Partes con decisiones sin cerrar llevan además
una subsección `### Decisiones abiertas` con checkboxes: son preguntas que el
agente debe hacer antes de implementar, nunca resolver por cuenta propia
(`CLAUDE.md` §9).

Cómo leen estos checklists las herramientas de sincronización (`/jira-sync`,
convención de ramas/commits): ver `CLAUDE.md` sección 10.

---

## Parte 34: Convergencia del inventario en el sync  <!-- POS-157 -->

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

Claves de Jira: épica `POS-157`; historias `POS-158`..`POS-161` (asignadas
por `/jira-sync` el 2026-10-12).

Objetivo: que dos terminales de la misma sucursal en
`LOCAL_CON_SINCRONIZACION` converjan al mismo stock tras un ciclo de sync,
sin perder ventas, ajustes ni entradas de ninguna de las dos.

### Checklist

**1. Contrato del inventario incremental** (POS-158)
- [x] `docs/api-contract.md` §7: ajuste de stock por **delta** idempotente
  por `local_id` (endpoint o campo según Decisiones abiertas), aplicado con
  bloqueo de fila; §7.1 documenta que `updated_at` cambia con **todo**
  movimiento de stock y que el cliente consulta con una ventana de solape.
  Contrato primero (skill `sync-api-contract`). `needs-approval`.

**2. Backend** (POS-159)
- [x] A-1(a): `Inventario.updated_at` se actualiza en cada cambio de
  `cantidad` (`onupdate` o asignación en `ventas.py`/`entradas.py`).
  Criterio: test nuevo — crear inventario, registrar una venta, y
  `GET /inventario?updated_since=<antes de la venta>` devuelve la fila;
  `uv run pytest` en verde.
- [x] A-2: ajuste por delta con `with_for_update()` e idempotente por
  `local_id`. Criterio: test que reproduce el escenario de la revisión
  (stock 10, venta de 3 de la terminal B, ajuste +5 de la terminal A) y
  termina en 12; reintento con el mismo `local_id` no duplica.
  `uv run alembic check` sin drift si cambia el esquema (`schema-parity`).

**3. Android: push y pull** (POS-160)
- [x] A-1(b): tras subir una venta/entrada, la fila de inventario afectada
  deja de quedar "sucia" (marcada sincronizada o sobrescrita con la cantidad
  del backend); la regla "gana lo local" del `InventarioPuller` aplica solo
  si hay movimientos pendientes de ese `(sucursal, artículo)`. `jvm-tests`:
  venta local sincronizada + cambio remoto → la fila local toma el valor
  remoto.
- [x] A-2: `InventarioAjustePusher` envía la suma de los movimientos
  `ajuste` pendientes como delta con su `local_id`. `jvm-tests`.
- [x] M-3: cursor de pull por `(entidad, sucursal)` y consulta con ventana
  de solape. `jvm-tests`: cambio de sucursal no hereda el cursor; una fila
  con `updated_at` anterior al cursor pero dentro de la ventana se baja.

**4. Reconciliación y verificación** (POS-161)
- [x] Reconciliación única de dispositivos que ya divergen (política en
  Decisiones abiertas), ejecutada al primer ciclo de sync tras actualizar.
  `jvm-tests`.
- [x] Verificación en el Xiaomi M2102J20SG con una segunda "terminal"
  simulada contra el backend (curl), repitiendo las pruebas de A-1/A-2 de
  `review_code.md`: tras un ciclo, el stock del teléfono y el del backend
  coinciden y no se registran conflictos espurios. `needs-device`.

### Decisiones abiertas

- [x] ¿Ajuste por delta como endpoint nuevo (`POST /ajustes-inventario`) o
  como campos `delta` + `local_id` en el `PATCH /inventario/{id}` actual?
  El PATCH absoluto puede quedarse para el modo REMOTO interactivo.
  **Resuelta (2026-10-12)**: endpoint nuevo, un request por movimiento
  `ajuste`, idempotente por `movimientos.local_id` (UNIQUE ya existente).
- [x] Política de reconciliación de dispositivos ya divergentes: ¿gana el
  backend en las filas sin movimientos pendientes, o se registra cada
  diferencia en `sync_conflicts` para que el admin decida?
  **Resuelta (2026-10-12)**: híbrida; el backend gana en filas sin
  pendientes, `remoto + efecto de pendientes` en las demás, y un único
  registro resumen en `sync_conflicts`. Bandera por sucursal
  (`inventario_reconciliado_v2`).
- [x] Tamaño de la ventana de solape del cursor (propuesta: 2 minutos; el
  merge ya es idempotente), o secuencia monotónica asignada por el servidor.
  **Resuelta (2026-10-12)**: ventana de 2 minutos en el cliente y cursor por
  `(entidad, sucursal)`; la secuencia del servidor no se implementa.

---

## Parte 35: Convergencia de catálogo y ubicación en el sync  <!-- POS-162 -->

*(Sale de un hallazgo del `code-reviewer` en el sub-paso 3 de la Parte 34
(2026-10-12), confirmado leyendo el código; no está en `docs/review_code.md`.
Hasta la Parte 34, el `PATCH /inventario/{id}` del ajuste llevaba junto con la
cantidad los atributos de catálogo (nombre, precio, categoría, etc.) y la
`ubicacion`. Al pasar el ajuste a delta (`POST /ajustes-inventario`, que solo
suma stock), esos atributos dejaron de subirse; el `POST` tampoco transporta
`ubicacion`. En sentido inverso, `InventarioPuller.mergeItem` solo actualiza
la fila de `inventario`, nunca `articulos`: las ediciones de catálogo hechas
en otra terminal no llegan. Además, una edición de `ubicacion` sin cambio de
stock queda como fila local sucia sin movimiento, y el siguiente pull la pisa
con el valor remoto. Toca contrato, backend y motor de sync: igual que la 34,
delegación `feature-dev` (`CLAUDE.md` §11).)*

Claves de Jira: épica POS-162; historias POS-163 (grupo 1), POS-164 (grupo 2),
POS-165 (grupo 3), POS-166 (grupo 4) y POS-167 (grupo 5), creadas por
`/jira-sync` el 2026-10-16.

Objetivo: que nombre, precio, categoría, unidad de medida y ubicación de un
artículo, editados en una terminal, lleguen a las demás de la sucursal sin
pisar el stock ni perderse en el siguiente pull.

### Checklist

**1. Contrato de edición de catálogo y ubicación (POS-163)**
- [x] `docs/api-contract.md` §7: operación que actualiza atributos de
  catálogo y `ubicacion` sin tocar `cantidad`, con resolución
  last-write-wins por `updated_at` (forma según Decisiones abiertas).
  Contrato primero (skill `sync-api-contract`). `needs-approval`.
  Resultado: §7.5 `PATCH /articulos/{articulo_id}`, §7.1 (filtro y
  `updated_at` incluyen el del artículo) y fila en la tabla de permisos de
  §12. Aprobado por el usuario.

**2. Backend (POS-164)**
- [x] La operación del contrato: actualiza `articulos` (y `ubicacion` de
  `inventario`) con `updated_at` nuevo, sin movimiento ni cambio de
  `cantidad`. Criterio: tests happy path + error (artículo inexistente);
  una edición no altera el stock de una venta concurrente.
  `uv run pytest` en verde; `uv run alembic check` sin drift si cambia el
  esquema (`schema-parity`).
- [x] `GET /inventario?updated_since=` filtra por `inventario.updated_at` o
  `articulos.updated_at` y devuelve el mayor de los dos como `updated_at`
  (contrato §7.1); `PATCH /articulos/{articulo_id}` registrado en
  `backend/app/permissions.py` (módulo `inventario`) y en
  `test_permisos.py`. Tests: una edición de catálogo aparece en el pull de
  otra sucursal; 403 sin el módulo.

**3. Android: push (POS-165)**
- [x] Una edición de catálogo o de `ubicacion` sin cambio de stock queda
  registrada como pendiente de subir con una señal propia (no
  `inventario.isSynced`, que toda escritura local deja en `false`); un
  pusher la sube y la limpia. Si cambia el esquema de Room: migración +
  `docs/schema-pos.json` alineados (`schema-parity`). `jvm-tests`.
- [x] El ajuste de stock con cambio de `ubicacion` también la sube (campo del
  contrato o llamada de edición, según la decisión). `jvm-tests`.
- [x] La migración de Room 8 a 9 rescata las ediciones locales que la Parte
  34 dejó sin subir (`UPDATE inventario SET edicionPendiente = 1` para los
  artículos con `articulos.isSynced = 0` y `remoteId` no nulo); el LWW del
  servidor decide si ganan. Si el servidor responde `aplicado = false`, el
  cliente adopta lo vigente y registra el conflicto en `sync_conflicts`
  (`last_write_wins_remoto_gana`). `jvm-tests`.

**4. Android: pull (POS-166)**
- [x] `InventarioPuller` mergea también los atributos de `articulos` y la
  `ubicacion` del backend: sin edición local pendiente gana el remoto; con
  una pendiente se conserva lo local. `jvm-tests`: catálogo editado en otra
  terminal → el artículo local lo refleja; edición local pendiente no se
  pisa.
- [x] Un artículo creado en otra terminal (sin fila local con ese
  `remoteId`) se crea localmente desde el ítem del pull; si ya existe un
  artículo local con el mismo SKU y `remoteId` nulo, se enlaza en vez de
  insertar. `jvm-tests`. *(Agregado a pedido del usuario el 2026-10-13, a
  partir del hallazgo del `code-architect`.)*
- [x] Reconciliación única de catálogo por sucursal (bandera nueva en
  `SyncStateStore`, pull completo una vez) para que los dispositivos con
  cursor y reconciliación de inventario ya marcados bajen las ediciones y
  artículos anteriores. `jvm-tests`. *(Agregado a pedido del usuario el
  2026-10-13.)*

**5. Verificación (POS-167)**
- [x] Verificación en el Xiaomi M2102J20SG con una segunda "terminal"
  simulada contra el backend (curl): editar nombre y ubicación en el teléfono
  y en el backend, tras un ciclo ambos coinciden y el stock no cambia.
  `needs-device`. *(Verificado el 2026-10-16 en el Xiaomi 24030PN60G, porque
  el M2102J20SG dejó de estar disponible: edición en el teléfono llegó al
  backend y la edición por curl llegó al teléfono, con stock 30 sin cambio y
  `edicionPendiente` en 0.)*

### Decisiones abiertas

- [x] Operación de edición: ¿endpoint nuevo `PATCH /articulos/{id}` (con
  `ubicacion` de la sucursal en el body) o reutilizar `PATCH
  /inventario/{id}` enviando `cantidad` igual al valor del servidor? Lo
  segundo reabre la carrera de la Parte 34. **Resuelta (2026-10-13):**
  endpoint nuevo `PATCH /articulos/{id}`.
- [x] `ubicacion` en el ajuste por delta: ¿campo opcional de `POST
  /ajustes-inventario` o solo vía la operación de edición? **Resuelta:**
  solo vía la operación de edición.
- [x] Señal de pendiente de una edición sin movimiento: ¿columna en
  `articulos`/`inventario`, o tabla de pendientes? **Resuelta:** columna
  `edicionPendiente` en `inventario` (migración de Room 8 a 9).
- [x] Conflicto de edición simultánea del mismo artículo en dos terminales:
  ¿last-write-wins silencioso por `updated_at` o registro en
  `sync_conflicts`? **Resuelta:** LWW en el servidor; solo se registra en
  `sync_conflicts` cuando la edición local pierde.

---

## Parte 36: Sucursal estable y rechazos visibles del sync  <!-- POS-168 -->

*(Sale de los hallazgos A-8, A-6 y M-5 (2º punto) de `docs/review_code.md`.
A-8, reproducido dos veces en el Xiaomi: al pasar a
`LOCAL_CON_SINCRONIZACION`, `ConfiguracionViewModel` persiste
`sucursales.firstOrNull()` cuando la sucursal seleccionada no aparece en el
catálogo emitido en ese momento; el catálogo alterna entre local y remoto
(ids distintos para la misma sucursal) y la terminal termina operando en
otra sucursal sin avisar. A-6: una fila rechazada con 4xx (ej. 403 por rol)
se marca `isSynced = true` sin `remoteId` y solo deja una línea de log —
ventas que nunca llegan al backend. M-5: un 401 del `SyncWorker` cierra la
sesión interactiva del cajero. Caso real visto en el Xiaomi durante la
Parte 34 (2026-10-12): un ajuste de stock sobre un artículo con `sku` vacío,
cuyas entradas el backend rechazó con 4xx, se queda "pendiente" para siempre
(contador "Pendientes por subir: 1") sin que nada lo vuelva visible ni
recuperable.)*

Claves de Jira: épica POS-168; historias POS-169 (grupo 1), POS-170 (grupo 2)
y POS-171 (grupo 3), creadas por `/jira-sync` el 2026-10-20.

Objetivo: que la sucursal de trabajo solo cambie por decisión del usuario, y
que toda fila que el backend rechace quede visible y recuperable.

### Checklist

**1. Selección de sucursal estable (A-8) (POS-169)**
- [x] `ConfiguracionViewModel` no persiste un fallback automático: si la
  sucursal guardada no está en el catálogo emitido, conserva la selección y
  pide al usuario que elija (o bloquea las operaciones que la requieren).
  Sucursal local y remota se relacionan por `remoteId`, no por id crudo.
  `jvm-tests`: catálogo que emite primero la lista local y luego la remota
  → la selección persistida no cambia.
- [x] Verificación en el Xiaomi: repetir la secuencia de A-8 de
  `review_code.md` (LOCAL → `LOCAL_CON_SINCRONIZACION` sin JWT, re-login
  con JWT, con una sucursal remota alfabéticamente anterior) y comprobar que
  la sucursal no cambia. `needs-device`.

**2. Rechazos de sync visibles (A-6) (POS-170)**
- [x] Estado de rechazo propio (no `isSynced`) con el código HTTP, en la
  forma que se decida; migración de Room + `docs/schema-pos.json` +
  (si aplica) backend alineados. `schema-parity`.
- [x] Cada rechazo se registra en `sync_conflicts` (visible en el panel de
  la Parte 19) y la sección Sincronización de Configuración muestra el
  número de filas rechazadas. `jvm-tests`: 403 en una venta → estado
  rechazado + conflicto registrado + contador > 0.
- [x] Verificación en el Xiaomi con el escenario preparado en la revisión
  (usuario con rol local `caja` y rol backend sin `venta`): la venta
  rechazada aparece en el panel y en el contador. `needs-device`.

**3. Sesión del worker (M-5, 2º punto) (POS-171)**
- [x] Un 401 recibido por el `SyncWorker` no cierra la sesión interactiva:
  se registra como "sync requiere re-login" y se muestra en Configuración.
  Con una sesión que nunca tuvo JWT, el mensaje lo dice así (hoy muestra
  "sesion expirada"). `jvm-tests` sobre `AuthInterceptor`/orquestador.

### Decisiones abiertas

- [x] Forma del estado de rechazo: ¿columna en cada entidad transaccional
  o tabla aparte de rechazos?
  **Resuelta (2026-10-18)**: columna `syncRechazo` (código HTTP, nullable)
  en cada entidad que se sube; pendiente = `isSynced = 0 AND syncRechazo IS NULL`.
- [x] Filas ya descartadas en dispositivos existentes (marcadas
  `isSynced = true` sin `remoteId`): ¿se reintentan una vez tras la
  migración, se reportan como rechazadas, o se dejan como están?
  **Resuelta (2026-10-18)**: se reportan como rechazadas (código
  desconocido) y se registran en `sync_conflicts`, solo en las entidades
  donde el sync siempre asigna `remoteId`.
- [x] Sucursal no encontrada en el catálogo: ¿bloquear operaciones hasta
  que el usuario elija, o solo avisar?
  **Resuelta (2026-10-18)**: solo avisar (aviso visible), sin bloquear: un
  rol `caja` no tiene el módulo `configuracion` y quedaría atascado.
- [x] Registro del rechazo en `sync_conflicts`: ¿valor nuevo en
  `politica_aplicada`? **Resuelta (2026-10-18)**: sí, `rechazo_backend`.
  **Revisada (2026-10-19)**: solo como valor local de Android, documentado
  en `schema-pos.json`; ni el contrato ni el backend cambian, porque el
  dispositivo nunca sube conflictos (ver Backlog).

---

## Parte 37: Endurecimiento de seguridad pre-release  <!-- POS-172 -->

*(Sale de los hallazgos A-3, A-4, A-5, M-4, M-6 y M-10 de
`docs/review_code.md`, reproducidos contra el backend y en el Xiaomi:
`admin/admin123` sin cambio forzado (visible en claro en el APK de release);
JWT falsificable con el `JWT_SECRET_KEY` por defecto; crash en cada arranque
tras restaurar un respaldo (`allowBackup` + clave de Keystore no
respaldada); login sin límite de intentos y con enumeración por tiempo
(115 ms vs 1056 ms); `/docs` públicos; `password_hash` inválido → 500; rol
borrado que sigue otorgando permisos; API key de IA residual en texto plano
en el dispositivo de pruebas.)*

Claves de Jira: épica POS-172; historias POS-173 (grupo 1), POS-174 (grupo 2) y POS-175 (grupo 3).

Objetivo: que un backend expuesto a internet y una app distribuida en Play
Store no tengan credenciales ni secretos por defecto, no se caigan tras una
restauración y resistan los ataques básicos de login.

### Checklist

**1. Credenciales de arranque (A-3) (POS-173)**
- [x] El administrador bootstrap (backend y dispositivo) debe cambiar su
  contraseña en el primer login, según la decisión abierta. Criterio:
  login con `admin/admin123` no da acceso a ningún módulo hasta cambiarla;
  `jvm-tests` + test de backend. *(2026-10-20: marca
  `debe_cambiar_password` en Alembic 0014, Room v11 (`MIGRATION_10_11`) y
  `schema-pos.json`; `POST /auth/cambiar-password` (contrato §2.2) y `403`
  en toda ruta protegida mientras siga encendida; pantalla obligatoria en
  la app y el `SyncWorker` no sube nada en ese estado. pytest 190/0,
  Android 476/0/0.)*

**2. Backend (A-4, M-4, M-6) (POS-174)**
- [x] A-4: el backend no arranca si falta `JWT_SECRET_KEY` (o si tiene el
  valor de desarrollo fuera de dev). Criterio: test + arranque del
  contenedor sin la variable falla con un mensaje claro. *(2026-10-20:
  `app/config.py` con `PDV_ENV`, que sin valor se asume productivo;
  `development` solo en el `docker-compose.yml` local y en los tests.
  `docker run` de la imagen sin la variable termina con
  `RuntimeError: JWT_SECRET_KEY es obligatoria`, y con la clave de
  desarrollo sin `PDV_ENV` con `... PDV_ENV no es development`.)*
- [x] M-4: límite de intentos de login, tiempo de respuesta constante para
  usuario inexistente, `/docs`/`/openapi.json` deshabilitados en producción,
  `password_hash` validado (prefijo `$2`) → `422`. Criterio: tests por
  cada punto; repetir las mediciones de `review_code.md` (20 intentos,
  tiempos, 500 por hash inválido) con resultado corregido. *(2026-10-20,
  contra el contenedor reconstruido: 20 intentos contra `admin` → 5 × 401 y
  15 × 429, y la contraseña correcta también recibe 429 mientras dura el
  bloqueo; mediana de 5 intentos, usuario inexistente 568 ms frente a
  existente con contraseña incorrecta 542 ms (antes 115 frente a 1056);
  `POST /usuarios` con hash inválido → 422 (antes 201 y login 500);
  `/docs`, `/redoc` y `/openapi.json` → 404 con `PDV_ENV` productivo. El
  contador es por `username` y por worker.)*
- [x] M-6: `DELETE /roles/{id}` con usuarios asignados → `409`; un rol
  borrado no otorga módulos; no se puede desactivar/borrar al último
  usuario con módulo `usuarios` ni a uno mismo. Contrato §10/§11
  actualizado primero. Tests happy path + error. *(2026-10-20: la
  reproducción de `review_code.md` contra el contenedor da 409 en
  `DELETE /roles` con un usuario asignado y 409 al borrarse a uno mismo.
  pytest 210/0.)*

**3. Android (A-5, M-10) (POS-175)**
- [x] A-5: `allowBackup="false"` o `dataExtractionRules` que excluyan
  DataStore y la base; un fallo de descifrado en `SessionStore`/
  `IaPreferences` se trata como "sin sesión / sin token" y limpia las
  claves. `jvm-tests` con un `TokenCipher` que lanza. *(2026-10-20:
  `allowBackup="false"` más `dataExtractionRules` que excluyen todo, porque
  desde Android 12 `allowBackup` ya no corta la transferencia entre
  dispositivos; `GeneralSecurityException` al descifrar → sin sesión o sin
  token, con las claves borradas; `ClaveAjenaTokenCipher` en los tests.
  Android 480/0/0, lint sin avisos nuevos.)*
- [x] M-10: limpieza única de claves obsoletas del DataStore (incluida
  `ia_token` en claro) al arrancar. `jvm-tests`. Tarea operativa aparte:
  rotar la API key del proveedor de IA del dispositivo de pruebas.
  *(2026-10-20: `LimpiezaDataStore` borra `ia_token`, `nombre_base_datos`
  y los cursores de pull sin sucursal antes de restaurar la sesión, y solo
  escribe si alguna sigue presente. El Xiaomi 24030PN60G actual no tiene
  `ia_token`, que estaba en el M2102J20SG anterior. La rotación de la key
  queda a cargo del usuario.)*
- [x] Verificación en el Xiaomi: repetir la reproducción de A-5 de
  `review_code.md` (instalación limpia + archivos de una sesión con JWT) →
  la app abre en el login sin cerrarse. `needs-device`. *(2026-10-21,
  Xiaomi 24030PN60G: respaldo de `databases/` y `files/` con la sesión
  `revcaja` y su JWT cifrado, desinstalación, instalación y reposición de
  los archivos. Tres arranques en frío sin `FATAL EXCEPTION` (antes 3 de
  3); el DataStore quedó sin claves `session_*` y con el resto intacto.
  El usuario confirmó que la app abre en el login y que los datos siguen
  ahí.)*

### Decisiones abiertas

- [x] Contraseña inicial del admin: ¿cambio forzado en el primer login, o
  contraseña provista por variable de entorno en el despliegue (backend) y
  asistente inicial (dispositivo)? **Resuelta (2026-10-20): cambio forzado
  en el primer login**, con la marca `debe_cambiar_password` en `usuarios`
  (backend, Room y `schema-pos.json`) y una pantalla obligatoria de cambio.
- [x] Límite de intentos de login: ¿en la app (contador en base/memoria) o
  delegado al proxy/infraestructura del destino de despliegue (Parte 40)?
  **Resuelta (2026-10-20): en la app, contador en memoria del proceso**
  (por worker; sin librerías nuevas).
- [x] `allowBackup`: ¿desactivar por completo o mantener respaldo
  excluyendo solo DataStore y la base? **Resuelta (2026-10-20): desactivar
  por completo** (`android:allowBackup="false"`).

---

## Parte 38: Integridad de datos del backend  <!-- POS-177 -->

*(Sale de los hallazgos M-1, M-2, B-1 y B-2 de `docs/review_code.md`,
reproducidos contra el backend: `usuario_id` libre en todas las escrituras
(se aceptó "otro-usuario-inventado"); montos sin validar (venta con
subtotal 10 y total 0.01 → 201; corte con fechas invertidas → 201); primer
insert concurrente de inventario → 500 (1 de 6); devoluciones con N+1 y
`estado` libre.)*

Claves de Jira: épica POS-177; historias POS-178 (grupo 1), POS-179 (grupo 2) y POS-180 (grupo 3).

Objetivo: que el backend sea la fuente confiable de atribución y de montos,
sin depender de que el cliente mande datos consistentes.

### Checklist

**1. Contrato (POS-178)**
- [x] `docs/api-contract.md` §5/§6/§7/§8/§9: el usuario de cada escritura
  sale del JWT; nuevos `422` por montos inconsistentes y por
  `fecha_fin < fecha_inicio`; `estado` de devolución con valores cerrados.
  Contrato primero (skill `sync-api-contract`). `needs-approval`.
  *(Aprobado por el usuario el 2026-10-21. `usuario_id` sale del request y
  lo fija el servidor con el `id` del JWT; el cliente manda
  `usuario_dispositivo` (informativo), que vuelve en las responses de
  §5.1, §6.1, §7.2, §7.4, §8.1-8.5 y §9.1. §7.5 deja de recibir usuario.
  Consistencia de montos de venta en §5.1 y de corte en §8.1 (incluye
  `fecha_fin >= fecha_inicio`); `estado` de devolución cerrado a tres
  valores en §9.1; regla general en §12. La columna `usuario_dispositivo`
  (Alembic + `schema-pos.json`) va con el grupo 2.)*

**2. Backend (POS-179)**
- [x] M-1: `usuario_id` derivado de `usuario_actual` en ventas, entradas,
  cortes, retiros, devoluciones y ajustes. Tests happy path + error.
  *(2026-10-21: también en `PATCH /inventario` (7.2). Columna
  `usuario_dispositivo` en `ventas`/`movimientos`/`cortes_caja`/
  `retiros_efectivo`/`devoluciones` (Alembic 0015 + `schema-pos.json`;
  Room sin cambio, su `usuarioId` ya es el del dispositivo;
  `test_schema_parity` en verde). Un test por ruta manda
  `usuario_id = "otro-usuario-inventado"` y comprueba que se guarda el `id`
  del JWT; `usuario_dispositivo` de 121 caracteres → `422`. Contra el
  contenedor reconstruido: venta con ese `usuario_id` → `201` con el `id`
  de `admin`.)*
- [x] M-2: validadores de montos con `Decimal` a 2 decimales
  (`Σ lineas.subtotal == subtotal`, `cantidad * precio_unitario ==
  linea.subtotal`, `total == subtotal - descuento + impuestos`) y de cortes
  (fechas, totales). Criterio: los payloads de la reproducción de
  `review_code.md` dan `422`.
  *(2026-10-21: `model_validator` en `VentaCreateSchema` y
  `CorteCajaCreateSchema`, helper `a_centavos` en `schemas/comunes.py`. El
  corte también exige `total_ventas == efectivo + tarjeta`,
  `monto_esperado == efectivo - retiros` y `diferencia == contado -
  esperado`. Contra el contenedor: venta con total 0.01 → `422`; corte con
  `fecha_fin` en el 2000, efectivo 999 y esperado −5 → `422`. Auditoría
  única de solo lectura (decisión 2) sobre las 20 ventas del backend de
  desarrollo: 1 inconsistente, `REV-V-d521bc`, la propia reproducción de
  `review_code.md` del 2026-09-29.)*
- [x] B-1: el primer insert concurrente de inventario no da `500`
  (`INSERT ... ON CONFLICT` + `FOR UPDATE`). Criterio: test con ventas
  concurrentes sin fila previa → todas `201`.
  *(2026-10-21: `app/inventario_bloqueo.py`, usado por ventas, entradas y
  `POST /ajustes-inventario`. El test con 6 ventas concurrentes da
  `[201] * 6` y existencia −6; con la lógica anterior el mismo test dio
  `[500, 201, 500, 500, 500, 201]`. Contra el contenedor: 6 ventas
  concurrentes → seis `201`, existencia −7 tras una venta más, sin
  `UniqueViolation` en el log.)*
- [x] B-2: `create_devolucion` valida artículos con una sola query y
  `estado` como `Literal`. `uv run pytest` en verde.
  *(2026-10-21: query `IN` como en ventas; `estado` fuera de los tres
  valores → `422` (test y contenedor). `uv run pytest`: 226 passed.)*

**3. Cliente Android (POS-180)**
- [x] Ajustes del cliente y de los pushers si el contrato cambia (ej. dejar
  de enviar `usuario_id`). `jvm-tests`.
  *(2026-10-21: los DTOs de request de venta, entrada, corte, retiro,
  devolución, `PATCH /inventario` y ajuste por delta mandan el `usuarioId`
  local como `usuario_dispositivo`; `ArticuloCatalogoRequestDto` ya no
  lleva usuario (antes `"system"`). Los DTOs de response suman
  `usuarioDispositivo` opcional, y el pull y el modo REMOTO de cortes y
  retiros guardan `usuario_dispositivo ?: usuario_id`. Room sin cambios.
  `UsuarioDispositivoMappersTest` cubre el JSON de salida y el fallback del
  pull. `./gradlew testDebugUnitTest`: 486 tests, 0 fallos, 0 errores, 0
  omitidos.)*

### Decisiones abiertas

- [x] `usuario_id` del dispositivo: ¿se deja de enviar, o se guarda aparte
  como dato informativo (ej. "usuario local") junto al usuario del JWT?
  *(Resuelta (2026-10-21): se guarda aparte. `usuario_id` = usuario del
  JWT; el valor del dispositivo viaja como `usuario_dispositivo` y se
  guarda en una columna nueva, informativa. Conserva quién operó cuando el
  sync sube pendientes de un turno anterior con la sesión de otro
  usuario.)*
- [x] Ventas históricas ya guardadas con montos inconsistentes: ¿se
  auditan con una consulta única o se ignoran?
  *(Resuelta (2026-10-21): se auditan una vez con una consulta de solo
  lectura contra el backend de desarrollo; se reporta el resultado, sin
  modificar datos ni agregar código.)*

---

## Parte 39: Calidad y deuda menor pre-release  <!-- POS-181 -->

*(Sale de los hallazgos M-7, B-3, B-4, B-5, B-6, B-8, B-11, B-12 y B-13 de
`docs/review_code.md`. Ninguno bloquea por sí solo, pero varios son
visibles para el usuario (botones con texto cortado, línea de prueba en el
log) o protegen las migraciones futuras sobre datos reales (M-7).)*

Claves de Jira: épica POS-181; historias POS-182 (grupo 1), POS-183 (grupo 2), POS-184
(grupo 3) y POS-185 (grupo 4).

Objetivo: dejar el código y la app sin deuda visible ni riesgos conocidos
de mantenimiento antes del primer release.

### Checklist

**1. Pruebas instrumentadas (M-7) (POS-182)**
- [x] `android/app/src/androidTest` con un test `MigrationTestHelper` de
  `MIGRATION_7_8` (diferido desde las Partes 24 y 28) como plantilla para
  las migraciones futuras, y una prueba de las queries `isSynced = 0`.
  Criterio: `adb shell am instrument -w
  com.pdv.pos.test/androidx.test.runner.AndroidJUnitRunner` en verde en el
  Xiaomi, tras `installDebug` + instalar el APK de `androidTest`.
  *(Ajustado el 2026-10-25: `connectedAndroidTest` desinstala la app al
  terminar y borra la base Room y la clave de Keystore del dispositivo.)*
  `needs-device`.

**2. Android menor (POS-183)**
- [x] B-3: `AppLogger` con `DateTimeFormatter` (sin `SimpleDateFormat`
  compartido), purga solo al rotar, y sin la línea "Verificacion Parte 5"
  de cada arranque (`PdvApplication.logSampleLinePerCategory`). `jvm-tests`.
- [x] B-4/B-5: `String.toBigDecimalOrNull()` de Kotlin en lugar de las 6
  copias privadas; `ModeAwareAuthRepository` captura `IOException`/
  `HttpException` en vez de `runCatching`. `jvm-tests`.
- [x] B-6: medir con StrictMode en debug si `DynamicHostInterceptor` lee
  DataStore en el hilo principal; corregir solo si se confirma.
  `needs-device`. *(Medido el 2026-10-25 en 3 arranques en frío: se
  construye en el hilo principal pero bloquea 0.75-1.2 ms y no aparece en
  ninguna violación de StrictMode. No confirmado, sin corrección.)*
- [x] B-11: advertencias de lint no relacionadas con versiones resueltas
  (`ConstantLocale` en `ConfiguracionScreen.kt:263`, `ic_launcher_round` +
  carpeta `mipmap-anydpi-v26`, `mutableLongStateOf` en `CajaScreen.kt`).
  Criterio: `./gradlew lint` sin esas advertencias.
- [x] B-12: los botones segmentados de Configuración y Usuarios no cortan
  palabras en el Xiaomi (1080x2400). `needs-device`.

**3. Backend menor (POS-184)**
- [x] B-13: `path_separator = os` en `backend/alembic.ini`. Criterio:
  `uv run pytest` sin el `DeprecationWarning`.

**4. Pantalla principal y "Acerca de" (POS-185)** *(Agregado a pedido del usuario el
2026-10-17, observado en el Xiaomi tras el login.)*
- [x] La pantalla principal ya no muestra el mensaje "Hola desde una funcion
  local de Kotlin" (`HelloViewModel.buildLocalGreeting`, texto de prueba de
  la Parte 1). `jvm-tests` del ViewModel + verificación visual en el Xiaomi.
  `needs-device`.
- [x] En modo local (`LOCAL_PURO`) la pantalla principal no consulta el
  backend ni muestra "Error de backend: failed to connect to
  /192.168.0.132 (port 8000)..." (`HelloScreen`, rama `ApiResult.Error`);
  el modo local compila y funciona sin red (`CLAUDE.md` §3). `jvm-tests` del
  ViewModel + verificación en el Xiaomi sin el backend levantado.
  `needs-device`.
- [x] Apartado "Acerca de" accesible desde la app con los datos del equipo
  de desarrollo y la información relevante del release (versión
  `versionName`/`versionCode`, fecha, novedades). Esa información se
  actualiza en cada release productivo. `jvm-tests` del ViewModel +
  verificación visual en el Xiaomi. `needs-device`.

### Decisiones abiertas

- [x] "Acerca de": ¿qué datos exactos del equipo de desarrollo se muestran
  (nombres, contacto, sitio)?
  *(Resuelta (2026-10-24): equipo "German y Susana ALS", contacto
  german.antoniols@gmail.com; sin sitio.)*
- [x] "Acerca de": ¿qué fuente tiene la información del release (constantes
  en `BuildConfig`/`strings.xml`, o un recurso editable por release)?
  *(Resuelta (2026-10-24): versión y `versionCode` desde `BuildConfig`;
  fecha y novedades en `strings.xml`, editadas a mano en cada release.)*
- [x] "Acerca de": ¿se agrega a la Parte 40 (grupo 5, "Preparación del
  release") un ítem para actualizarla en cada release productivo?
  *(Resuelta (2026-10-24): sí.)*
- [x] El texto "Hola desde una funcion local de Kotlin" aparecía también en
  el grupo 7 de la Parte 40. **Resuelta (2026-10-17):** se quitó de la Parte
  40; queda solo en esta Parte.
- [x] B-8: mover los textos de UI a `strings.xml`, ¿dentro del primer
  release o después?
  *(Resuelta (2026-10-24): después del primer release; queda en el
  Backlog.)*
- [x] B-11: ¿se actualizan las dependencias marcadas por lint
  (`GradleDependency`, `NewerVersionAvailable`) en esta Parte o en una
  Parte de mantenimiento aparte?
  *(Resuelta (2026-10-24): en una Parte de mantenimiento aparte; queda en
  el Backlog.)*

---

## Parte 40: Primer release productivo (APK firmado + backend desplegado) <!-- POS-186 -->

*(Sale del análisis "qué falta para el primer release productivo" del
2026-09-08. Absorbe el hallazgo B-7 —R8/shrinking, diferido en la Parte 29
"hasta que haya distribución real"— y cierra el `[TODO]` de destino de
despliegue de `CLAUDE.md` §7. Era la Parte 34; se renumeró a 39 el
2026-10-02 para ir después de las Partes 34-38 que salen de la revisión
pre-release (`docs/review_code.md`), a 40 el 2026-10-12 al insertarse la
Parte 35, y se amplió con sus hallazgos A-7,
M-9, B-9 y B-10 y con las decisiones de producto M-5 y M-8. Verificado el
2026-09-29: `./gradlew assembleRelease` produce `app-release-unsigned.apk`
(36.5 MB) sin firmar y sin ofuscar. Desde la Parte 31 el modo REMOTO ya no
depende de `adb reverse` (Wi-Fi LAN), pero sigue sin un backend HTTPS
productivo.)*

Claves de Jira: épica POS-186; historias POS-187 (grupo 1), POS-188 (grupo 3),
POS-189 (grupo 4), POS-190 (grupo 5), POS-191 (grupo 6), POS-192 (grupo 7) y
POS-193 (grupo 8). El grupo 2, diferido completo al Backlog, no tiene historia.

Objetivo: un APK (o AAB) de release firmado, ofuscado y verificado en
dispositivo, apuntando a un backend FastAPI desplegado y accesible por HTTPS
desde fuera de la LAN. *(Ajustado el 2026-10-25: el primer release se
centra en el modo local; el backend productivo queda diferido, ver
Decisiones abiertas.)*

### Checklist

**1. Firma de release (POS-187)**
- [x] `signingConfigs.release` en `android/app/build.gradle.kts` que lee de un
  `keystore.properties` gitignoreado (o variables de entorno) y se aplica al
  `buildType release`. Criterio: `./gradlew assembleRelease` produce un APK y
  `apksigner verify --print-certs` lo reporta firmado con el certificado de
  release, distinto del de debug.
- [x] Keystore de release generado con `keytool`, respaldado fuera del repo;
  `keystore.properties` y el `.jks`/`.keystore` en `.gitignore`. Criterio:
  `git status` sobre un árbol limpio no muestra el keystore ni credenciales;
  `assembleDebug` compila sin el archivo y `assembleRelease` falla con un
  mensaje claro si falta.
- [x] `CLAUDE.md` §7 documenta dónde vive el keystore, cómo se pasan las
  contraseñas al build, y el procedimiento de rotación — sin incluir el
  keystore ni los secretos en el repo.

**2. Despliegue del backend productivo** *(Diferido completo (2026-10-25):
sin backend productivo en este release; sus 4 ítems pasan al Backlog.)*
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

**3. URL base productiva en la app (POS-188)**
- [ ] El módulo Configuración permite fijar y persistir la URL base del
  backend remoto (`https`, host, puerto), o se hornea un valor productivo por
  defecto. Criterio: en el Xiaomi, modo REMOTO contra `https://<dominio>`
  completa login + una operación de sync sin `adb reverse`. `needs-device`.
  Nota (2026-10-02): la Parte 31 ya agregó esquema http/https, host y
  puerto editables en Configuración y en el login; queda decidir el valor
  por defecto (ver Decisiones abiertas) y verificar contra HTTPS real.
  *(Diferido (2026-10-25): sin backend productivo en este release; ver
  Decisiones abiertas y Backlog.)*
- [x] Cleartext HTTP deshabilitado en release (network security config /
  `usesCleartextTraffic=false`), con allowlist explícito solo si se decide
  mantener pruebas locales. Criterio: una petición `http://` a un host
  productivo falla; `https://` funciona. *(Ajustado (2026-10-25): sin host
  productivo, el criterio es que el APK de release no alcanza por `http://`
  el backend de la LAN de desarrollo. Consecuencia: en release los modos
  REMOTO y LOCAL_CON_SINCRONIZACION solo funcionarán contra un backend
  HTTPS.)* *(2026-10-25: `usesCleartextTraffic="false"` en el manifest
  principal; el debug sigue alcanzando la LAN por `http://` (200 OK en el
  Xiaomi). La prueba con el APK de release queda para la sesión única de
  verificación del release en el Xiaomi, después del grupo 4.)*
  *(2026-10-26: verificado por el usuario con el APK de release: "Probar
  conexión" contra `http://192.168.0.132:8000` responde "CLEARTEXT
  communication ... not permitted by network security policy"; OkHttp lo
  rechaza antes de conectar, así que no depende de que el backend esté
  arriba.)*

**4. Endurecimiento del build de release (hallazgo B-7 de `docs/review_code_2026-09-28_1816.md`) (POS-189)**
- [x] `isMinifyEnabled = true` e `isShrinkResources = true` en el `buildType
  release`. Criterio: `./gradlew assembleRelease` en verde; el APK de release
  pesa menos que el equivalente sin shrink. *(2026-10-25: BUILD SUCCESSFUL
  sin advertencias de R8; `app-release.apk` baja de 36 560 235 a 25 103 991
  bytes (-31 %), firmado con la clave de release.)*
- [x] Reglas keep en `android/app/proguard-rules.pro` para Room, Hilt,
  kotlinx-serialization, Retrofit y ML Kit barcode. Criterio: con el APK de
  release firmado instalado en el Xiaomi, los 6 módulos abren y una venta +
  un sync end-to-end completan sin `ClassNotFoundException` ni errores de
  (de)serialización. *(Decidido (2026-10-25): las cinco librerías traen
  reglas de consumidor que R8 ya aplica (visibles en
  `app/build/outputs/mapping/release/configuration.txt`); `proguard-rules.pro`
  queda vacío y solo recibe una regla si el APK de release falla en el
  Xiaomi, con el stack trace como evidencia.)*
  *(2026-10-26: el APK de release falló en el Xiaomi al pasar a
  `LOCAL_CON_SINCRONIZACION`: `Unable to create converter for class
  java.lang.Object` / `Serializer for class 'Any' is not found`, desofuscado
  con `mapping.txt` a `HealthApiService.getHealth()`. La pantalla principal
  del grupo 7 dejó de usar la respuesta, R8 quitó `HealthResponseDto` y la
  firma genérica de la `Continuation` quedó en `Object`. Regla agregada:
  `-keep,allowobfuscation` de las clases `@Serializable` de
  `data/remote/dto`; ningún otro DTO de respuesta estaba eliminado. Sin
  backend HTTPS el sync end-to-end no es probable en release: lo sustituyen
  el chat de IA por HTTPS y el cambio de modo, verificados por el usuario
  junto con los 6 módulos, la importación y una venta, sin errores en el
  logcat.)*
- [x] Verificación en el dispositivo físico Xiaomi M2102J20SG con el APK de
  release (firmado y ofuscado), no solo debug. `needs-device`.
  *(2026-10-26: verificado por el usuario en el Xiaomi 24030PN60G con
  `pos-pdv-release-v1.0.0.apk`. Hallazgos corregidos en la sesión, cada uno
  con un test que lo reproducía: (1) apagar la IA sin pulsar "Guardar" no
  persistía; ahora persiste al momento y retira el consentimiento. (2) En
  LOCAL la sucursal por defecto se mostraba pero no se guardaba, y chat,
  venta y caja la exigían; ahora `SucursalInicial` la preselecciona al
  arrancar y `ConfiguracionViewModel` también la guarda en LOCAL. (3) La
  regla de R8 del ítem anterior. (4) La importación de
  `docs/inventario-inicial.csv` fallaba por el BOM UTF-8 del encabezado;
  `InventarioCsvImporter` lo quita y se importan los 115 artículos.)*

**5. Preparación del release (POS-190)**
- [x] `versionCode` / `versionName` subidos desde `1` / `"0.1"` al primer
  valor de release según el esquema de `CLAUDE.md` §7 (ej. `1.0.0` ->
  `versionCode 10000`), en el commit que prepara el release. *(2026-10-25:
  `1.0.0` / `10000`; `aapt2 dump badging` del APK lo confirma.)*
- [x] Regla de `outputFileName` para el variant `release` siguiendo la
  convención `{app}-{proposito}-{tipo-build}-v{version}` (hoy solo existe
  para `debug`). Criterio: `assembleRelease` emite un archivo con ese nombre.
  *(2026-10-25: `pos-pdv-release-v1.0.0.apk`, el sufijo sale de
  `versionName`.)*
- [x] Suite unitaria en verde sobre el variant de release:
  `./gradlew testReleaseUnitTest` (mostrar salida completa). `jvm-tests`.
  *(2026-10-25: BUILD SUCCESSFUL; 96 suites, 492 tests, 0 fallos, 0
  errores, 0 omitidos.)* *(2026-10-26, con los grupos 6-8 y los arreglos
  de la sesión de release: BUILD SUCCESSFUL; 97 suites, 516 tests, 0
  fallos, 0 errores, 0 omitidos.)*
- [ ] B-9 (`docs/review_code.md`): `.github/workflows/android-ci.yml`
  compila la variante de release (`assembleRelease`) para que un fallo de
  reglas keep de R8 aparezca en CI y no en el teléfono. Criterio: el
  workflow corre en verde en el PR de esta Parte. *(2026-10-25: paso
  "Compilar la variante de release" agregado, firmado con un keystore
  desechable generado con `keytool` en el job; se cierra al ver el PR en
  verde.)*
- [ ] B-10 (`docs/review_code.md`): decidido y aplicado si
  `docs/manual-tecnico.html`/`.pdf` (salida de `/docs-sync`) se versionan o
  se agregan a `.gitignore`. Criterio: `git status` limpio antes de
  etiquetar el release. *(Decidido (2026-10-25): dejan de versionarse y se
  generan bajo demanda; se ajusta el hook de push que los vigila.)*
  *(2026-10-25: en `.gitignore` y fuera del índice con `git rm --cached`;
  `docs_guard.py` ya no corre `build_manual.py --check` antes del push y
  sigue bloqueando la edición a mano de los derivados; `/docs-sync` y
  `ALCANCE-DOCS.md` ajustados. Se cierra con `git status` limpio tras el
  commit.)*
- [x] Apartado "Acerca de" (Parte 39, grupo 4) actualizado: fecha y
  novedades del release en `strings.xml` (la versión sale de `BuildConfig`).
  *(Agregado el 2026-10-24 por decisión de la Parte 39.)* Declara además la
  limitación M-8: cantidades enteras y sin IVA desglosado. *(2026-10-25:
  fecha `2026-10-25` y nueva sección "Limitaciones conocidas"
  (`release_limitaciones`); se revisa en el Xiaomi en la sesión de
  release.)* *(2026-10-26: verificado por el usuario con el APK de
  release: "1.0.0 (versionCode 10000)", fecha, limitaciones y logo.)*

**6. Distribución (POS-191)**
- [ ] Artefacto generado según el canal elegido (ver Decisiones abiertas):
  APK firmado para sideload, o AAB (`./gradlew bundleRelease`) para Play
  Console. Criterio: el artefacto instala/valida en un dispositivo limpio
  distinto del de desarrollo. *(Diferido (2026-10-26): no hay un segundo
  dispositivo; pasa al Backlog. El APK firmado se generó e instaló en el
  Xiaomi desde cero.)*
- [ ] Si el canal es Play Console: cuenta de desarrollador, ficha (icono,
  capturas, descripción), política de privacidad publicada (URL), formulario
  Data Safety, cuestionario de content rating y justificación del permiso
  `CAMERA`. Criterio: la app pasa la revisión de un track interno de Play.
  *(No aplica (2026-10-25): el canal es sideload.)*
- [x] Flujo de permiso `CAMERA` en runtime (escaneo de código de barras)
  probado en el dispositivo con el build de release: concesión y denegación.
  `needs-device`. *(2026-10-26: verificado por el usuario con el APK de
  release.)*
- [x] A-7 (`docs/review_code.md`): la política de privacidad y el formulario
  Data Safety declaran los datos que el asistente IA envía al proveedor
  (nombres, precios, costos y totales de caja, `ia/EstadoPuntoVenta.kt`) y
  el uso de `RECORD_AUDIO` (dictado); la app pide consentimiento explícito
  al activar la IA. Criterio: el consentimiento aparece en el Xiaomi al
  activar la IA y sin él no se envía nada al proveedor. `needs-device`.
  *(2026-10-25: con sideload no hay ficha de Play ni Data Safety; la
  declaración de datos vive en el diálogo de consentimiento de
  Configuración. Encender la IA abre el diálogo; "Acepto" guarda
  `ia_consentimiento` junto con `ia_activo`; guardar la IA apagada retira
  el consentimiento; `ChatViewModel` exige activo + consentimiento antes de
  llamar al proveedor, y un dispositivo con la IA activa de antes vuelve a
  pedirlo. Tests JVM: 5 nuevos en `ConfiguracionViewModelTest` y 1 en
  `ChatViewModelTest`, en verde. Falta la verificación en el Xiaomi.)*
  *(2026-10-26: verificado por el usuario con el APK de release. Hallazgo
  corregido: apagar el switch sin "Guardar" no persistía; ahora apagar
  guarda `ia_activo=false` y retira el consentimiento al momento.)*

**7. Pantalla principal y tema (hallazgo A-7) (POS-192)**
- [x] La pantalla principal deja de mostrar el texto de prueba de la
  Parte 1 ("Backend: ok (v0.1.0)") y muestra contenido propio del punto de
  venta (sucursal, usuario en turno, estado de conexión). `jvm-tests` del
  ViewModel + verificación visual en el Xiaomi. `needs-device`. *(El texto
  "Hola desde una funcion local de Kotlin" se quitó de este ítem el
  2026-10-17: lo resuelve la Parte 39, grupo 4.)*
  *(2026-10-25: verificado por el usuario en el Xiaomi con el build de
  debug (`HelloViewModelTest`: 15 tests en verde, 5 nuevos); revisado de nuevo con el APK de release el 2026-10-26.)*
- [x] Tema propio de la Activity (paleta "Recibo" de `CLAUDE.md` §3) y
  pantalla de arranque con `core-splashscreen` (librería nueva, señalar
  según `CLAUDE.md` §9), sin el destello claro del tema de plataforma en el
  arranque en frío con modo oscuro. `needs-device`.
  *(2026-10-25: verificado por el usuario en el Xiaomi con el build de
  debug en modo claro y oscuro, sin destello claro; revisado de nuevo con el APK de release el 2026-10-26.)*
- [x] Ícono propio de la app (adaptativo + monocromo) y logo en la pantalla
  de arranque, a partir de los diseños de `docs/mipe-logo/`; verificación
  visual en el Xiaomi en modo claro y oscuro. `needs-device`. *(Agregado el
  2026-10-25 a pedido del usuario. Decidido: el nombre visible pasa a
  "MiPE PDV" (`app_name` y "Acerca de"; `applicationId`
  `com.pdv.pos` sin cambio); logo horizontal en el login y logo con eslogan
  en "Acerca de", en versión clara u oscura según el tema; de
  `docs/mipe-logo/` se versionan solo los SVG.)*
  *(2026-10-25: verificado por el usuario en el Xiaomi con el build de
  debug (ícono, splash, login y "Acerca de"); revisado de nuevo con el APK de release el 2026-10-26.)*

**8. Expiración de la sesión (M-5) (POS-193)** *(Agregado el 2026-10-25 por la
decisión "Expiración de la sesión en modo LOCAL".)*
- [x] La sesión persistida expira tras 12 horas sin uso: al abrir la app
  después de ese plazo se muestra el login. `jvm-tests` de la lógica de
  expiración + verificación en el Xiaomi. `needs-device`.
  *(2026-10-25: "sin uso" = tiempo en segundo plano. `MainActivity.onStop`
  guarda la hora (`session_ultimo_uso`) y `onStart` cierra la sesión si
  pasaron más de 12 h (`SessionManager.cerrarSiExpirada`); la restauración
  al arrancar el proceso descarta la sesión vencida. Decidido: una sesión
  expirada detiene el sync de `LOCAL_CON_SINCRONIZACION` hasta el próximo
  login (`MOTIVO_SIN_SESION`), y la verificación en el Xiaomi se hace
  adelantando el reloj del teléfono 13 h. Tests JVM en verde:
  `SessionManagerTest` 11 (6 nuevos), `SessionStoreTest` 8 (1 nuevo),
  `DefaultSyncOrchestratorTest` 9 (1 nuevo). Falta la verificación en el
  Xiaomi, en la sesión de release.)* *(2026-10-26: verificado por el
  usuario con el APK de release: segundo plano, reloj adelantado 13 h, al
  volver aparece el login; hora automática restaurada.)*

### Decisiones abiertas

- [x] **Canal de distribución**: Play Console (AAB; requiere cuenta, fichas,
  Data Safety y revisiones, pero gestiona updates y firma) vs sideload de APK
  firmado (sin store, distribución manual). Afecta los grupos 1, 5 y 6.
  *(Resuelta (2026-10-25): sideload del APK firmado. Play Console se planifica
  después; los ítems de Play del grupo 6 no aplican a este release.)*
- [x] **Destino de despliegue del backend** (`CLAUDE.md` §7 lo tiene como
  `[TODO]` desde el inicio): Render / Fly.io / VPS propio / AWS. A sopesar:
  costo, TLS y dominio incluidos, correr el `docker-compose` tal cual,
  backups de Postgres.
  *(Resuelta (2026-10-25): diferido. El primer release se centra en el modo local;
  el backend productivo se despliega en una fase futura, bajo demanda del
  cliente. El grupo 2 y la verificación contra HTTPS real del grupo 3 pasan
  al Backlog.)*
- [x] **Play App Signing** (solo si el canal es Play): ¿se delega la clave de
  firma de la app a Google, o se mantiene la clave localmente?
  *(Resuelta (2026-10-25): no aplica: el canal es sideload.)*
- [x] **URL base**: ¿siempre configurable por el usuario en Configuración, o
  valor productivo por defecto con override oculto para soporte?
  *(Resuelta (2026-10-25): sigue configurable en Configuración y en el login
  (Parte 31), sin valor productivo por defecto mientras no exista el
  backend productivo.)*
- [x] **Expiración de la sesión en modo LOCAL** (M-5 de `docs/review_code.md`,
  reproducido: la app abrió con la sesión de `admin` de 17 días antes): ¿la
  sesión persistida expira por inactividad, al cerrar turno/corte final, o
  se mantiene como hoy? Si entra al release, se agregan sus ítems a esta
  Parte.
  *(Resuelta (2026-10-25): expira por inactividad (12 horas sin uso); entra al
  release como grupo 8.)*
- [x] **Venta por fracción/peso e IVA en el ticket** (M-8 de
  `docs/review_code.md`): hoy `cantidad` es entera en el carrito y
  `descuento`/`impuestos` están fijos en cero. ¿Lo necesita el primer
  release a comercios reales, o se declara la limitación en la ficha de la
  app y se planifica después?
  *(Resuelta (2026-10-25): se declara la limitación (cantidades enteras, sin IVA
  desglosado) en "Acerca de"; se planifica en una Parte posterior.)*

---

## Backlog (trabajo futuro, fuera del alcance actual)

- **Backend productivo** (diferido desde la Parte 40 el 2026-10-25, bajo
  demanda del cliente): grupo 2 completo de la Parte 40 (despliegue con TLS
  y `docker-compose.prod.yml` real, `alembic current` en prod, `CLAUDE.md`
  §7 con el destino, M-9 con Gunicorn + proxy TLS + respaldo de Postgres
  probado) y la verificación de modo REMOTO contra `https://<dominio>` del
  grupo 3. Recomendación registrada: VPS propio con el compose tal cual,
  Caddy como proxy TLS y `pg_dump` programado.
- **Distribución por Play Console** (Parte 40, canal sideload elegido el
  2026-10-25): cuenta, ficha, Data Safety, content rating, Play App
  Signing.
- **APK de release en un dispositivo limpio distinto del de desarrollo**
  (Parte 40, grupo 6, diferido el 2026-10-26: no hay un segundo
  dispositivo): instalar `pos-pdv-release-v1.0.0.apk` por sideload en un
  teléfono que nunca tuvo la app y confirmar instalación, login bootstrap
  y "Sucursal principal" en la pantalla principal.
- **Lectura de disco en el hilo principal en `LoggingModule`** (hallazgo
  de la medición B-6, Parte 39, 2026-10-25): StrictMode en debug marca
  `DiskReadViolation` en `LoggingModule.provideLogDirectory` al construir
  el grafo de Hilt desde `MainActivity`. Evaluar si el costo justifica
  moverlo fuera del hilo principal.
- **Textos de UI a `strings.xml`** (B-8 de `docs/review_code.md`, decidido
  en la Parte 39 el 2026-10-24): después del primer release.
- **Actualización de dependencias marcadas por lint** (B-11,
  `GradleDependency`/`NewerVersionAvailable`/`AndroidGradlePluginVersion`,
  decidido en la Parte 39 el 2026-10-24): en una Parte de mantenimiento
  aparte.

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
- **Subida de conflictos al backend** (hallazgo Parte 36, 2026-10-19): los
  conflictos de `sync_conflicts` viven solo en Room; `subirConflicto`
  (`RemoteSyncConflictRepository`) no tiene llamadores y el panel en REMOTO
  lista solo lo que haya en el backend. Además, los valores de
  `politica_aplicada` que usa el dispositivo (`evento_aditivo_delta`,
  `last_write_wins_*`, `reconciliacion_inicial_backend_gana`,
  `rechazo_backend`) no están en el `Literal` del backend (`last_write_wins`
  / `evento_aditivo`). Subirlos exige alinear ese enum primero.
- **Reintentar filas rechazadas por el sync** (Parte 36): las filas con
  `syncRechazo` quedan intactas pero no hay acción en la app para
  reintentarlas tras corregir la causa (ej. darle el módulo al rol en el
  backend).
- **Política de contraseñas** (revisión tras la Parte 37, 2026-10-21): no
  hay reglas de fortaleza. Crear un usuario (`UsuarioViewModel`) solo exige
  que la contraseña no esté vacía; `CambiarPasswordViewModel` además exige
  confirmación y rechaza `admin123`. El backend no puede validarla porque
  recibe el hash bcrypt, no el texto (solo comprueba el prefijo `$2`), así
  que la política debe vivir en la app, en un validador compartido por
  ambas pantallas. Definir: longitud mínima, si se exige variedad de
  caracteres, y rechazo de `admin123` también al crear o editar usuarios.
  Relacionado: el modo local no limita los intentos de login (el bloqueo
  de 5 fallos en 15 min de la Parte 37 existe solo en el backend).

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
- **2026-10-12**: durante la ejecución de la Parte 34 (sub-pasos 1-3 listos:
  contrato, backend y push/pull Android) el `code-reviewer` detectó que el
  ajuste por delta dejó de subir atributos de catálogo y `ubicacion`, y que
  el pull nunca mergea `articulos`. Se evaluó absorberlo en las Partes
  35-39 y ninguna encajaba (36 sucursal/rechazos, 37 seguridad, 38 integridad
  del backend, 39 deuda cosmética, 40 release), y por ser una regresión que
  introduce la 34 no va al Backlog: se insertó como Parte 35 "Convergencia de
  catálogo y ubicación en el sync", por decisión del usuario. Las antiguas
  35-39 pasaron a 36-40 (ninguna tenía claves de Jira asignadas). La Parte
  35 se agrega a la lista de delegación a subagentes feature-dev de
  `CLAUDE.md` §11 y `.claude/commands/parte.md`. Decisiones de la Parte 34
  (resueltas por el usuario): ajuste por delta como `POST
  /ajustes-inventario` con un request por movimiento, idempotente por
  `movimientos.local_id`; reconciliación híbrida (backend gana en filas sin
  pendientes, `remoto + pendientes` en las demás, un registro resumen en
  `sync_conflicts`); cursor por `(entidad, sucursal)` con ventana de solape
  de 2 minutos.
- **2026-10-16**: se cierra la Parte 35. Decisiones (resueltas por el
  usuario): endpoint nuevo `PATCH /articulos/{id}` con `ubicacion` en el body;
  `ubicacion` viaja solo por la edición (el ajuste por delta queda solo de
  stock); señal de pendiente en la columna `inventario.edicionPendiente`
  (Room 8 a 9, con rescate de ediciones perdidas de la Parte 34); LWW en el
  servidor por `editado_en` contra `articulos.updated_at`, con registro en
  `sync_conflicts` (`last_write_wins_remoto_gana`) solo si la edición local
  pierde. Alcance agregado a pedido del usuario: filtro y permisos del
  backend, creación o enlace por SKU de artículos desde el pull y
  reconciliación única de catálogo por sucursal. Verificación en dispositivo
  hecha en un Xiaomi 24030PN60G distinto al de las Partes anteriores. Sin
  cubrir en JVM: la carrera del DAO (`cerrarEdicionSubida`) y la migración
  8 a 9; `InventarioPuller.resolverArticulo` no es atómico (ventana de
  milisegundos entre `hayEdicionPendiente` y `updateArticulo`).
