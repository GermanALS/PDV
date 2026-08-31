# PLAN.md — Plan de desarrollo del Punto de Venta

Este documento vive en `docs/PLAN.md` y contiene el diseño en desarrollo activo
del proyecto. A diferencia de `CLAUDE.md` (contexto estable: arquitectura ya
decidida, convenciones, comandos), aquí se documentan features en fase de
diseño, decisiones que aún pueden iterar, y el checklist de avance. Cuando una
parte de este documento se estabiliza, su resumen final se migra a
`CLAUDE.md` y esta sección queda como bitácora histórica.

**Estado del documento**: reestructurado — la infraestructura compartida
(Logs en la Parte 5, motor de sync dentro de la Parte 6) se construye antes
que los módulos que la usan, y cada módulo del POS es una Parte independiente
(6-12) con sus propios sub-pasos (UI → repositorio local → repositorio
remoto/contrato → wiring). Todas las Partes tienen su propia sección
`### Checklist` con ítems verificables y criterios de éxito concretos
(comando, archivo, o comportamiento observable — nunca "pruebas
exhaustivas"). Las Partes 1-20 están implementadas y migradas a `CLAUDE.md`
en lo que se estabilizó; las Partes 21-29 incorporan los hallazgos de
`docs/review_code.md` (revisión de código del 2026-08-30) y están pendientes.

Las Partes con decisiones sin cerrar llevan además una subsección
`### Decisiones abiertas` con checkboxes: son preguntas que el agente debe
hacer antes de implementar, nunca resolver por cuenta propia
(`CLAUDE.md` §9). Cuando una se cierra, la decisión se migra a
`CLAUDE.md` y aquí queda el registro histórico.

Cómo leen estos checklists las herramientas de sincronización (`/jira-sync`,
convención de ramas/commits): ver `CLAUDE.md` sección 10.

---

## Parte 1: Plan

Amplía este documento para planificar cada una de estas partes en detalle,
con los subpasos enumerados en forma de lista de verificación que el agente
deberá marcar, y con pruebas y criterios de éxito para cada uno. Asegúrate
de que el usuario revise y apruebe el plan. 

### Checklist
- [x] Las Partes 2-16 tienen su propia sección `### Checklist` con ítems
  verificables agrupados por sub-paso — criterio: cada Parte de módulo
  (6-12) usa el mismo formato validado en la Parte 7; el resto usa un
  formato adaptado a su propio contenido. Verificado: 16 encabezados
  `## Parte` y 16 secciones `### Checklist`, uno por Parte.
- [x] Usuario revisó y aprobó el plan resultante — criterio: aprobación
  explícita registrada en la conversación. Aprobado el 2026-08-17.

---

## Parte 2: Estructura básica  <!-- POS-1 -->

Configura la infraestructura de Docker, el backend en la carpeta `backend/`
con FastAPI, un contenedor de PostgreSQL (motor definido en `CLAUDE.md`
sección 4, usado vía SQLAlchemy 2.0 async + Alembic), y escribe los scripts
de inicio y detención en el directorio `scripts/`. El `docker-compose` debe
levantar backend y base de datos juntos. Esto debería mostrar un ejemplo de
texto estático para confirmar que el ejemplo "hello world" funciona al
ejecutarse localmente, y también realizar una llamada a la API.

Adicionalmente, prepara el ambiente para facilitar las pruebas en Android
Studio, con las pruebas, depuración y el despliegue realizadas directamente
en el dispositivo físico Xiaomi M2102J20SG, que se encontrará disponible y
configurado en el ambiente de desarrollo. Esto es de vital importancia para
aprovechar los recursos del dispositivo de forma óptima.

**Nota de entorno**: el dispositivo físico Xiaomi M2102J20SG es el objetivo
principal de pruebas del ambiente de desarrollo actual. Si en algún momento
no está disponible (cambio de equipo, colaborador nuevo, etc.), el fallback
es un emulador de Android Studio con especificaciones similares. Esto no
requiere trabajo adicional ahora, solo queda documentado como supuesto.

Para esto se deberá desplegar un APK en donde se muestre un ejemplo de
"hello world" de dos formas:

1. Mediante una función local.
2. Mediante FastAPI, invocando al backend configurado previamente.

**Convención de nombres de builds**: `{app}-{proposito}-{tipo-build}-v{version}`.
El APK de esta parte se llama `pos-hello-debug-v0.1`. Todos los builds de
prueba posteriores siguen este mismo esquema para mantener trazabilidad.

### Checklist

**1. Backend + Docker** (POS-2)
- [x] `docker-compose.yml` levanta `backend` + PostgreSQL juntos —
  criterio: `docker compose up` deja ambos contenedores en estado
  healthy. Verificado: `docker compose ps` muestra `pdv-backend-1` y
  `pdv-db-1` en estado `healthy`.
- [x] Scripts de inicio/detención multiplataforma
  (`scripts/start-*`/`stop-*`) — criterio: cada script levanta/detiene el
  stack sin errores en su SO correspondiente. Verificado en Windows
  (`start-windows.ps1`/`stop-windows.ps1`); los de Mac/Linux tienen el
  mismo contenido (`docker compose up -d --build` / `docker compose
  down`) pero no se probaron en esos SO por no estar disponibles en este
  entorno.
- [x] Endpoint `GET /health` responde — criterio: `curl
  http://localhost:8000/api/v1/health` devuelve `200` (ver
  `docs/api-contract.md` §1). Verificado: respuesta
  `{"status": "ok", "version": "0.1.0"}`.

**2. Hello world** (POS-3)
- [x] Función local en Android que muestra texto estático — criterio:
  pantalla visible al abrir la app. Verificado: capturas de pantalla en el
  Xiaomi M2102J20SG muestran "Hola desde una funcion local de Kotlin".
- [x] Llamada real desde Android a `GET /health` del backend — criterio:
  la pantalla muestra la respuesta del backend, no un valor hardcodeado.
  Verificado: la pantalla muestra "Backend: ok (v0.1.0)" tras la llamada
  Retrofit real (con `adb reverse tcp:8000 tcp:8000` y network security
  config de cleartext a `localhost` solo en el build type `debug`).

**3. Ambiente Android físico** (POS-4)
- [x] Xiaomi M2102J20SG conectado y reconocido (o emulador de fallback) —
  criterio: `adb devices` lo lista. Verificado: `adb devices -l` lista
  `ee823e02 device product:vayu_global model:M2102J20SG`.
- [x] APK nombrado según la convención — criterio: `./gradlew
  assembleDebug` produce `pos-hello-debug-v0.1.apk`. Verificado: el build
  genera `app/build/outputs/apk/debug/pos-hello-debug-v0.1.apk` (naming
  configurado vía `androidComponents.onVariants` en `app/build.gradle.kts`).
- [x] Instalado y verificado corriendo en el Xiaomi — criterio: `./gradlew
  installDebug` + confirmación manual de las dos formas del hello world.
  `needs-device`. Confirmado por el usuario el 2026-08-18.

**Nota de versiones (Android)**: al armar el scaffolding se probó primero
la combinación mas reciente disponible (AGP 9.3.1 + Kotlin built-in de
AGP), pero KSP todavia no soporta el Kotlin built-in de AGP 9, y el plugin
tradicional `org.jetbrains.kotlin.android` rompe contra las clases internas
de AGP 9.x. Como Hilt depende de KSP, el proyecto quedo fijado en la ultima
combinacion estable verificada: AGP 8.13.2 + Gradle wrapper 9.5.1 (AGP 8.x
no soporta Gradle 9.6+) + Kotlin 2.2.21 + Hilt 2.57.2 (Hilt >= 2.58 exige
AGP >= 9) + compileSdk/targetSdk 36 (varias librerias de AndroidX en sus
ultimas versiones exigen compileSdk 37 + AGP >= 9.1, incompatible con lo
anterior). Revisar este set de versiones antes de actualizarlas a ciegas en
Partes futuras.

## Parte 3: Modelado de base de datos  <!-- POS-5 -->

Propone un esquema de base de datos para el punto de venta y guárdalo en
formato JSON dentro de `docs/`. Para la definición de los campos y su
relación, emplea los datos más utilizados en los puntos de venta y el
control de almacenes. Documenta el enfoque en `docs/` y obtén la aprobación
del usuario antes de continuar a la Parte 4.

### Diseño multi-sucursal (requisito de arquitectura)

La app administra varias sucursales en diferentes locaciones, y varios
dispositivos pueden operar simultáneamente dentro de una misma sucursal.
El esquema debe reflejar esto:

- **Tabla `sucursales`** (`id`, `nombre`, `direccion`, `activa`) — vive en el
  backend remoto como fuente de verdad de qué sucursales existen.
- **Cada dispositivo se configura con un `sucursal_id`** en el módulo de
  Configuración (ver Parte 6). Es un dato de identidad del
  dispositivo, no del usuario, porque varios usuarios operan el mismo
  dispositivo en distintos turnos.
- **Catálogo maestro de artículos (`articulos`) es global**, compartido entre
  sucursales — no se duplica el maestro de productos por sucursal.
- **Todo lo transaccional lleva `sucursal_id` como columna** (no tablas
  separadas por sucursal): `inventario`, `ventas`, `cortes_caja`,
  `devoluciones`, `movimientos`. Esto simplifica queries consolidadas (ej.
  inventario total de todas las sucursales) sin duplicar estructura.

### Sucursal por defecto (bootstrap)

Ningún lado debe depender del otro para tener una sucursal válida desde el
primer arranque — ni el dispositivo debe pedirle al usuario que teclee un
`sucursal_id` (es un UUID) a mano:

- **Backend remoto**: si al arrancar no existe ninguna fila en `sucursales`,
  se crea una por defecto (`nombre: "Sucursal principal"`, `activa: true`)
  como seed de la migración inicial de Alembic — una sola vez, no en cada
  arranque.
- **Room local**: si el dispositivo está en modo local (sin backend
  configurado) y no tiene ninguna sucursal guardada, se crea la misma
  sucursal por defecto localmente en el primer arranque, con `local_id`
  propio y `remote_id` nulo. Esto convierte a `sucursales` en una tabla
  sincronizable más (mismos campos de tracking que la sección siguiente),
  no solo una tabla administrativa remota.
- El módulo de Configuración (Parte 6) nunca pide el `sucursal_id` como
  texto libre: en modo local preselecciona/crea la sucursal por defecto; en
  modo remoto o local-con-sincronización la trae con `GET /sucursales` y
  deja elegir (o crear una nueva) de una lista.
- **Al migrar de local a remoto** (Parte 6, "Migración de datos al cambiar
  de modo"): si la sucursal local por defecto nunca se sincronizó
  (`remote_id` nulo), se sube como sucursal nueva en el primer push. Si el
  backend también tiene su propia sucursal por defecto, el conflicto (dos
  "Sucursal principal" sin relación) se resuelve con el mismo flujo de
  confirmación del administrador ya definido para "hay datos en ambos
  lados" — no hay merge automático de sucursales duplicadas en el MVP.

### Campos de tracking para sincronización

Toda tabla que participe en sincronización local/remota necesita:
`local_id`, `remote_id` (nullable hasta sincronizar), `updated_at`,
`is_synced` (o enum de estado), y soft-delete (`deleted_at`) en vez de
DELETE físico.

### Tabla de auditoría de conflictos

`sync_conflicts`: registra todo conflicto detectado durante la
sincronización, incluso los resueltos automáticamente (ver política en
Parte 6, módulo Configuración), para que el administrador pueda auditar qué
pasó.

### Checklist (POS-6)
- [x] Esquema JSON de los 7 módulos creado en `docs/` (ej.
  `docs/schema-pos.json`) — criterio: el archivo existe y cubre las
  tablas transaccionales (`inventario`, `ventas`, `cortes_caja`,
  `devoluciones`, `movimientos`), más `articulos`, `sucursales` y
  `usuarios`, con `sucursal_id` en cada tabla transaccional. Verificado:
  `docs/schema-pos.json` cubre las 8 tablas nombradas más `venta_detalle`
  y `devolucion_detalle` (líneas de venta/devolución, aprobadas junto con
  el resto del esquema); `sucursal_scoped: true` marca `sucursal_id` en
  `inventario`, `ventas`, `cortes_caja`, `devoluciones` y `movimientos`.
- [x] Esquema incorpora los campos de tracking de sincronización en toda
  tabla sincronizable — criterio: `local_id`, `remote_id`, `updated_at`,
  `is_synced`, `deleted_at` presentes en el JSON de cada tabla afectada.
  Verificado: bloque `sync_tracking_fields` referenciado vía
  `tracking_fields: true` en las 10 tablas de negocio (todas excepto
  `sync_conflicts`, que no participa del ciclo de sync).
- [x] Esquema incorpora `sync_conflicts` — criterio: presente en el JSON
  con sus campos. Verificado: tabla `sync_conflicts` con `entidad`,
  `entidad_local_id`, `valor_local`/`valor_remoto`/`valor_resuelto`,
  `politica_aplicada`, `resuelto_automaticamente`, `fecha_deteccion`.
- [x] Esquema documenta el seed de la sucursal por defecto — criterio:
  nota adjunta al JSON o campo `seed` describiendo la fila inicial de
  `sucursales`. Verificado: bloque `seed` en `docs/schema-pos.json`.
- [x] Aprobación explícita del usuario sobre el esquema — criterio:
  mensaje de aprobación registrado antes de continuar a la Parte 4;
  bloquea la sección 3 de `docs/api-contract.md` y cualquier endpoint
  real por módulo (ver `docs/api-contract.md` §5). `needs-approval`
  Aprobado el 2026-08-18.

---

## Parte 4: Experiencia de inicio de sesión (usuario ficticio)  <!-- POS-7 -->

Actualiza el código para que, al acceder por primera vez a la app, sea
necesario iniciar sesión con credenciales ficticias ("admin", "password";
"user1", "password") para poder ver la aplicación de punto de venta, y para
que sea posible cerrar sesión. Realiza pruebas exhaustivas.

Este login ficticio queda reemplazado por completo en la Parte 13
(autenticación real contra `UsuarioRepository`) — las credenciales
`admin`/`password` y `user1`/`password` dejan de funcionar a partir de ahí.

Se ubica aquí, antes de la infraestructura de logs (Parte 5) y del
desarrollo por módulo (Partes 6-12), porque el formato de log necesita el
concepto de "usuario en turno" desde el inicio, y varios módulos (ej.
entrada de mercancía, administración de usuarios) requieren atribuir
acciones a ese usuario desde su primera implementación.

### Checklist (POS-8)
- [x] Pantalla de login con credenciales ficticias (`admin`/`password`,
  `user1`/`password`) — criterio: `./gradlew build` pasa y la pantalla
  aparece al abrir la app sin sesión activa. `./gradlew build` verificado
  en verde; confirmación visual en el Xiaomi confirmada por el usuario el
  2026-08-18.
- [x] Sesión persiste mientras la app está abierta y permite cerrar
  sesión — criterio: prueba de `ViewModel`/Compose UI Test que abre
  sesión, navega, y cierra sesión correctamente. Verificado:
  `SessionManagerTest` (login/logout) y `HelloViewModelTest` (logout
  delega al `SessionManager`) en verde.
- [x] Credenciales inválidas muestran error sin crashear — criterio:
  prueba unitaria cubre el caso de error (CLAUDE.md §6). Verificado:
  `LoginViewModelTest` cubre credenciales inválidas y limpieza del error
  al volver a escribir.
- [x] Instalado y verificado en el Xiaomi con ambos usuarios ficticios —
  `needs-device`. Confirmado por el usuario el 2026-08-18: instalado y
  probados exitosamente todos los casos de uso en el dispositivo.

**Evidencia de `./gradlew build` y `./gradlew testDebugUnitTest`**: `BUILD
SUCCESSFUL`; 11 pruebas unitarias en verde (`SessionManagerTest`,
`LoginViewModelTest`, `HelloViewModelTest`, incluyendo los casos nuevos de
logout y username de sesión). Se deshabilitó el lint check `PropertyEscape`
en `android/app/build.gradle.kts` porque marcaba como error el
`local.properties` generado por Android Studio en Windows (archivo fuera de
git, `.gitignore` línea 11) — sin este ajuste `./gradlew build` fallaba en
cualquier entorno Windows independientemente de este cambio.

---

## Parte 5: Logs de la aplicación  <!-- POS-9 -->

Genera un sistema de logs que capture:

- Eventos que afectan directamente a la base de datos.
- Issues de comunicación (fallas de red, timeouts).
- Errores de escritura y conflictos con el sistema de sincronización.

**Se construye aquí, antes de los módulos**, porque es infraestructura
compartida (igual que el motor de sync de la Parte 6): cada
`LocalXRepository`/`RemoteXRepository` de las Partes 6-12 necesita poder
escribir a este sistema desde el momento en que se implementa, en vez de
integrarlo después en 7 módulos ya construidos.

**El logger es una utilidad genérica**: recibe `tipo`, `sucursalId` y
`usuario` como parámetros explícitos del llamador — no resuelve ninguno de
los dos internamente, así que no depende en tiempo de build de ninguna
Parte de módulo. En la práctica, `usuario` sale de la sesión iniciada en la
Parte 4 (login) y `sucursalId` de la selección hecha en la Parte 6
(Configuración); ambos ya existen para cuando cualquier módulo real
empieza a llamar al logger (Parte 6 en adelante, ver "Integración en los
módulos siguientes").

### Formato

```
[TIPO][YYYY-MM-DD HH:MM:SS][sucursal_id][usuario] Descripción del evento
```

Categorías de `TIPO`: `ERROR`, `WARN`, `INFO`, `DB_WRITE`, `SYNC_CONFLICT`,
`AUTH`.

### Almacenamiento y rotación

- Los logs se almacenan como archivos `.txt` dentro del almacenamiento
  privado de la app (no almacenamiento externo compartido, por tratarse de
  datos de negocio potencialmente sensibles).
- Rotación (split) cada 5 MB.
- Nombre de archivo con timestamp de creación:
  `app-log-YYYYMMDD-HHMMSS.txt`, para que sean identificables aunque se
  muevan o se pierda el orden.
- **Retención**: 30 días; los archivos más antiguos se purgan
  automáticamente.

### Integración en los módulos siguientes

- **Parte 6 (Configuración, motor de sync)**: categoría `SYNC_CONFLICT` en
  cada conflicto detectado (auto-resuelto o no).
- **Partes 7-12 (los 6 módulos restantes)**: cada `LocalXRepository`/
  `RemoteXRepository` registra `DB_WRITE` en escrituras exitosas y
  `ERROR`/`WARN` en fallas de red o timeouts del lado remoto.
- **Parte 13 (Gestión de usuarios real) y Parte 15 (Refinamiento de IA)**:
  categoría `AUTH` en rechazos por falta de permiso.

### Alcance: exclusivamente la app Android

Este sistema (archivo `.txt` categorizado, rotación, retención de 30 días)
vive únicamente en el dispositivo — es el registro de auditoría de negocio
del punto de venta, con `sucursal_id` y `usuario` porque su propósito es
poder reconstruir qué pasó localmente incluso sin conexión. El backend
FastAPI **no** escribe a este sistema ni lo comparte: su logging operativo
(errores del servidor, excepciones no capturadas) sigue una convención
aparte, más simple, documentada en `CLAUDE.md` sección 4 (módulo `logging`
estándar de Python a stdout, sin archivos ni rotación propios).

### Checklist

**1. Logger genérico** (POS-10)
- [x] API del logger recibe `tipo`, `sucursalId`, `usuario` y mensaje como
  parámetros explícitos — criterio: la firma compila sin importar código
  de la Parte 4 ni de la Parte 6. Verificado: `AppLogger.log(tipo,
  sucursalId, usuario, mensaje)` en `com.pdv.pos.logging`, sin imports de
  `auth` ni de módulos de negocio.
- [x] Escribe archivos `.txt` en almacenamiento privado de la app con el
  formato `[TIPO][fecha][sucursal_id][usuario] mensaje` — criterio:
  prueba unitaria verifica el formato exacto de una línea escrita.
  Verificado: `AppLoggerTest."log writes a line with the exact expected
  format"` en verde.
- [x] Las 6 categorías soportadas (`ERROR`, `WARN`, `INFO`, `DB_WRITE`,
  `SYNC_CONFLICT`, `AUTH`) — criterio: enum/sealed class con las 6,
  prueba unitaria por categoría. Verificado: enum `LogType` con las 6,
  `AppLoggerTest."log supports all six categories"` en verde.

**2. Rotación y retención** (POS-11)
- [x] Rotación al llegar a 5 MB, archivo nuevo con nombre
  `app-log-YYYYMMDD-HHMMSS.txt` — criterio: prueba unitaria fuerza el
  límite y verifica que se crea un archivo nuevo con el nombre esperado.
  Verificado: `AppLoggerTest."rotates to a new file..."` en verde.
- [x] Purga automática de archivos con más de 30 días de retención —
  criterio: prueba unitaria con archivos de fecha simulada verifica que
  los vencidos se eliminan y los vigentes no. Verificado:
  `AppLoggerTest."purges files older than..."` en verde.

**3. Verificación de alcance** (POS-12)
- [x] El módulo de logging compila de forma aislada, sin depender de
  ningún módulo de negocio — criterio: build independiente del paquete
  del logger. Verificado: `./gradlew build` en verde (`BUILD
  SUCCESSFUL`); `com.pdv.pos.logging` solo depende de `java.io`/Android
  framework, no de `auth` ni de ningún repositorio.
- [x] Instalado y verificado en el Xiaomi: generar al menos una línea de
  cada categoría y confirmar que el archivo `.txt` las contiene —
  `needs-device`. Verificado el 2026-08-18: `adb shell run-as com.pdv.pos
  cat files/logs/app-log-20260818-163132.txt` muestra las 6 categorías
  (`ERROR`, `WARN`, `INFO`, `DB_WRITE`, `SYNC_CONFLICT`, `AUTH`) con el
  formato `[TIPO][fecha][sucursal_id][usuario] mensaje`.

### Decisiones abiertas

- [x] Concurrencia e I/O del logger: función `suspend` sobre un dispatcher
  de I/O, o escritura bloqueante en un hilo dedicado. La firma la consumen
  las Partes 6-13, así que cambiarla después implica reescribir siete
  módulos. **Decidido**: `suspend fun` sobre `Dispatchers.IO`, consistente
  con el resto del proyecto (corrutinas estructuradas).
- [x] Provisión del logger: singleton de Hilt inyectado en cada
  repositorio, u `object` de nivel superior. **Decidido**: singleton de
  Hilt (`@Singleton @Inject constructor`), mismo patrón que
  `SessionManager` (Parte 4).
- [x] Comportamiento si falla la escritura del archivo `.txt`: propagar la
  excepción, degradar a stdout, o descartar la línea. Debe ser compatible
  con `CLAUDE.md` §9 (no programar a la defensiva). **Decidido**: se
  captura la `IOException` (es E/S, `CLAUDE.md` §9 lo permite) y se
  degrada a Logcat (`Log.e`) sin tumbar el flujo que disparó el log.

---

## Parte 6: Módulo Configuración  <!-- POS-13 -->

Implementa de punta a punta el módulo de Configuración: parámetros de
conexión al backend FastAPI (IP, puerto, nombre de base de datos), selector
de sucursal del dispositivo, selector de modo (local / remoto / local con
sincronización), gestión de permisos por tipo de usuario (intesimulada por ahora — se conecta de verdad en la Parte 13), y cierre de
sesión.

El selector de sucursal **nunca** es un campo de texto libre para el
`sucursal_id` (es un UUID) — es una lista: en modo local, preseleccionada
con la sucursal por defecto (Parte 3, "Sucursal por defecto"); en modo
remoto o local-con-sincronización, poblada con `GET /sucursales`.

**Este módulo va primero entre los 7 módulos del POS**, a diferencia del
orden original, porque las Partes 7-12 necesitan que `BackendMode` funcione
de verdad para poder probar sus modos remoto y local-con-sincronización.

**Nota de diseño**: a diferencia de los demás módulos, el paso 4 (motor de
sync) es lógica compartida que las Partes de módulo transaccional (7-11)
reutilizarán, y el paso 5 (wiring) conecta el selector de modo a la lógica
real desde esta misma Parte (no es interfaz simulada) — es la base de la
que dependen las Partes 7-12.

### Checklist

**1. UI** (POS-14)
- [x] Propuesta de pantalla (parámetros de conexión, selector de sucursal
  como lista, selector de modo, permisos simulados, cerrar sesión)
  presentada y aprobada — criterio: aprobación explícita registrada antes
  de implementar. Aprobado el 2026-08-18 (pantalla `ConfiguracionScreen`
  con navegación manual por estado, sin Navigation Compose).
- [x] Selector de sucursal implementado como lista, nunca campo de texto
  libre — criterio: revisión de código confirma que no existe ningún
  campo editando `sucursal_id` directamente. Verificado: `code-reviewer`
  confirmó que `SucursalSection` en `ConfiguracionScreen.kt` usa
  `ExposedDropdownMenuBox` con `OutlinedTextField(readOnly = true)` y
  selección solo vía `DropdownMenuItem`; `sucursal_id` (UUID) no aparece
  como campo editable en ningún lado.
- [x] Instalado y verificado en el Xiaomi — `needs-device` Confirmado por
  el usuario el 2026-08-18: pantalla de Configuración instalada y
  verificada (secciones Conexión, Sucursal, Modo, Permisos, Cerrar
  sesión).

**2. Persistencia local** (POS-15)
- [x] DataStore guarda `BackendMode`, parámetros de conexión, y
  `sucursal_id` seleccionado — criterio: prueba unitaria escribe y relee
  cada valor. Verificado: `ConfiguracionPreferencesTest` (4 pruebas,
  `PreferenceDataStoreFactory` sobre archivo temporal) en verde.
- [x] `SucursalRepository` (interfaz) + `LocalSucursalRepository` (Room)
  implementados — criterio: `./gradlew testDebugUnitTest` en verde.
  `jvm-tests` Verificado: `assembleDebug`/`testDebugUnitTest` en verde
  (`BUILD SUCCESSFUL`).
- [x] Sucursal por defecto se crea automáticamente en modo local si no
  existe ninguna al primer arranque (Parte 3, "Sucursal por defecto") —
  criterio: prueba unitaria arranca con Room vacío y verifica que aparece
  exactamente una sucursal `"Sucursal principal"` con `remote_id` nulo.
  Verificado: `LocalSucursalRepositoryTest` (3 pruebas) en verde. Hallazgo
  de `code-reviewer` corregido antes de cerrar: el check-then-insert
  original no era atómico (`observeSucursales()` es un `flow` frío que
  reevalúa el seed en cada colector), lo que permitía crear dos
  "Sucursal principal" con dos colectores concurrentes; se movió a
  `SucursalDao.insertIfEmpty` con `@Transaction`, que Room serializa.

**3. Repositorio remoto** (POS-16)
- [x] Migración de Alembic para `sucursales`, con seed de la sucursal por
  defecto si la tabla está vacía — criterio: `alembic upgrade head` sobre
  una base vacía deja exactamente una fila. `schema-parity` Verificado:
  `alembic upgrade head` contra el Postgres de `docker compose` dejó
  exactamente una fila (`SELECT nombre, local_id, activa FROM sucursales`
  → `Sucursal principal | (null) | t`). Migración crea la tabla y siembra
  la fila en el mismo archivo (`0001_create_sucursales.py`), sin
  necesitar chequeo condicional de vacío.
- [x] Rutas `GET /sucursales` y `POST /sucursales` documentadas primero
  en `docs/api-contract.md` (CLAUDE.md §9) — criterio: sección nueva en
  el contrato, revisada. Aprobado el 2026-08-18 (`docs/api-contract.md`
  §4: `id` es la PK del backend = `remote_id` del dispositivo, `local_id`
  opcional en el POST para correlación, `is_synced` siempre `true` en
  las respuestas del backend).
- [x] Rutas implementadas — criterio: `pytest
  backend/tests/test_sucursales.py` en verde, happy path + 1 error
  (CLAUDE.md §6). Verificado: 4 pruebas en verde (listar con seed, page
  inválida → 422, crear happy path, crear sin `nombre` → 422). Backend
  reconstruido en Docker y probado con `curl` real contra el contenedor.
- [x] `RemoteSucursalRepository` (Retrofit) — criterio: `./gradlew
  testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests` Verificado:
  `RemoteSucursalRepositoryTest` (2 pruebas, MockK) en verde;
  `assembleDebug`/`testDebugUnitTest` en verde. `code-reviewer` no
  encontró hallazgos; señaló (no bloqueante) que `Sucursal.id` significa
  `local_id` en `LocalSucursalRepository` y `id` remoto en
  `RemoteSucursalRepository` — a reconciliar en el sub-paso 4/5 (motor
  de sync / wiring), no antes.

**4. Motor de sync genérico** (POS-17)
- [x] Política `last-write-wins` — criterio: prueba de integración
  sincroniza dos versiones de la misma `Sucursal` con `updated_at`
  distintos y verifica que gana la más reciente. Verificado:
  `LastWriteWinsSyncEngineTest` (`sync/`), 3 pruebas en verde.
- [x] Lógica de la política de eventos aditivos (sin entidad real
  todavía) — criterio: prueba unitaria de la función que combina dos
  eventos, sin depender de Room/Retrofit; su validación de integración
  queda diferida a la Parte 7. Verificado: `EventoAditivoCombiner.combinar`
  (`cantidad_resultante = base + deltaLocal + deltaRemoto`, sin comparar
  `updated_at`, per la decisión híbrida registrada arriba) +
  `EventoAditivoCombinerTest`, 3 pruebas en verde. La rama "delta negativo
  → marcar conflicto" queda diferida a la Parte 7/9 a propósito: escribir
  a `sync_conflicts`/el log desde esta función violaría el propio
  criterio del checklist ("sin depender de Room/Retrofit").
- [x] Conflictos se registran en `sync_conflicts` (Parte 3) y en el log
  con categoría `SYNC_CONFLICT` (Parte 5) — criterio: prueba de
  integración provoca un conflicto y verifica ambas escrituras.
  Verificado: `LastWriteWinsSyncEngine.sincronizar` registra en
  `SyncConflictDao` (Room) y en `AppLogger` (`SYNC_CONFLICT`) toda
  divergencia de `updated_at`, incluso resuelta automáticamente
  (`resueltoAutomaticamente = true`), per PLAN.md Parte 3.
- [x] Los 4 casos de migración de datos al cambiar de modo (solo local,
  solo remoto, ambos lados, diálogo con impacto concreto) implementados
  — criterio: 4 pruebas de integración, una por caso. Verificado:
  `MigrationPlanner.planificar` (`sync/PlanMigracion.kt`) + 4 pruebas en
  `MigrationPlannerTest` en verde. Lógica pura; el wiring a UI/repositorios
  reales es el sub-paso 5 (Wiring), todavía no implementado.

`code-reviewer` revisó los 4 ítems de este sub-paso sin hallazgos.

**5. Wiring** (POS-18)
- [x] Selector de modo conectado a la lógica real — criterio: prueba de
  integración cambia el modo desde la UI y verifica que `BackendMode` en
  DataStore cambia y el repositorio inyectado cambia en consecuencia.
  Verificado: `ModeAwareSucursalRepository` (nuevo, `data/`) resuelve
  `SucursalRepository` a `LocalSucursalRepository`/`RemoteSucursalRepository`
  reactivamente según `ConfiguracionPreferences.deviceConfig.backendMode`
  (`flatMapLatest`); `ConfiguracionViewModel` reescrito para leer/escribir
  contra `ConfiguracionPreferences` y `SucursalRepository` reales en vez de
  datos estáticos del sub-paso 1. `ModeAwareSucursalRepositoryTest` +
  `ConfiguracionViewModelTest` (3 pruebas) en verde.
  `code-reviewer` encontró un hallazgo real en la primera pasada — el
  `combine` reactivo sobreescribía `ip`/`puerto`/`nombreBaseDatos` (un
  borrador local sin guardar) cada vez que cambiaba la sucursal o el modo
  — corregido separando esos tres campos en su propio seed de una sola
  vez; prueba de regresión agregada. Verificación de una segunda pasada
  confirmó el fix; señaló además una ventana de carrera mucho más
  angosta y de confianza baja (60) entre la siembra inicial y una edición
  del usuario antes del primer render de la pantalla — se deja sin
  blindaje adicional a propósito, per CLAUDE.md §9 (no programar a la
  defensiva ante una condición sin evidencia de ocurrir en la práctica).
- [x] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`
  Confirmado por el usuario el 2026-08-19: instalado y probados
  exitosamente los tres modos (Local, Remoto, Local con sincronización).

### Decisiones abiertas

- [x] Política de sincronización para las cantidades de inventario:
  `last-write-wins` sobre el campo, o saldo derivado de eventos aditivos.
  Se decide aquí porque el motor de sync se construye en esta Parte y las
  Partes 7-12 lo heredan. La Parte 9 permite modificar atributos de
  artículos consultados, y la cantidad en existencia es justo el caso
  donde `last-write-wins` pierde decrementos concurrentes de dos
  dispositivos de la misma sucursal. **Decidido**: enfoque híbrido —
  `inventario.cantidad` sigue siendo un campo real (no una vista derivada
  recalculada desde `movimientos`), pero se sincroniza aplicando deltas
  con signo (`cantidad_resultante = cantidad_base + delta_local +
  delta_remoto`), nunca comparando `updated_at` como en last-write-wins.
  `movimientos` sigue siendo la tabla de auditoría/detalle tal como ya
  está modelada en `docs/schema-pos.json`. El ajuste manual de la Parte 9
  no es un `UPDATE cantidad = X` directo: se traduce a un `movimiento`
  tipo `"ajuste"` con `delta = nuevo_valor - valor_conocido_localmente`.
  Las Partes 7-11 nunca escriben `cantidad` directamente, solo a través de
  la función de combinar deltas construida aquí. Si la suma de deltas
  concurrentes deja `cantidad` en negativo (ej. dos ventas del mismo
  artículo cuando solo había stock para una), el delta se aplica igual
  (se permite negativo temporalmente) y se registra en `sync_conflicts`
  con `resuelto_automaticamente=false` para revisión manual del
  administrador — no bloquea la sincronización de ningún dispositivo.

---

## Parte 7: Módulo Venta de mostrador <!-- POS-19 -->

Escanear un barcode con la cámara o ingresar la descripción del producto, y
mostrar sus datos principales alineados al esquema de la Parte 3.

### Checklist

**1. UI** (POS-20)
- [x] Propuesta de pantalla (campos, tipo, layout) presentada y aprobada
  por el usuario — criterio: aprobación explícita registrada antes de
  escribir código. Aprobado el 2026-08-19 (pantalla `VentaScreen`:
  búsqueda/escaneo, tarjeta de artículo con 5 campos, carrito, totales,
  método de pago; navegación manual desde `HelloScreen`, mismo patrón sin
  Navigation Compose que Configuración).
- [x] Composable implementado con datos estáticos de ejemplo (mínimo 5
  campos del producto) — criterio: `./gradlew build` pasa y la pantalla es
  navegable desde el menú principal. Verificado: `./gradlew build` en
  verde (`BUILD SUCCESSFUL`, incluye lint); catálogo estático de 5
  artículos de ejemplo en `VentaViewModel`; botón "Venta" en `HelloScreen`
  navega a `VentaScreen`. `code-reviewer` encontró y se corrigieron dos
  hallazgos antes de cerrar este ítem: búsqueda vacía coincidía con el
  primer artículo del catálogo (`"".contains("")` siempre `true` en
  Kotlin) — corregido devolviendo `null` si el término está vacío; y una
  carrera entre el bind asíncrono de `ProcessCameraProvider` y el
  `onDispose` del diálogo de escaneo podía dejar la cámara abierta sin
  liberar y el `ImageAnalysis` con el executor ya cerrado — corregido con
  una bandera `disposed` que el listener de bind respeta, más cierre del
  `BarcodeScanner` de ML Kit en `onDispose` (hallazgo adicional del mismo
  reviewer, antes no se cerraba nunca).
- [x] Instalado y verificado corriendo en el Xiaomi (o emulador fallback)
  — criterio: `./gradlew installDebug` + confirmación manual de que la
  pantalla se ve con los datos de ejemplo. `needs-device` Confirmado por
  el usuario el 2026-08-19: pantalla de Venta instalada y verificada
  (búsqueda, escaneo con permiso de cámara, tarjeta de artículo, carrito,
  totales, método de pago).

**2. Repositorio local** (POS-21)
- [x] `VentaRepository` (interfaz) en `domain/repository/` — criterio:
  compila sin referencias a Room ni Retrofit. Verificado: `VentaRepository`
  expone solo `suspend fun registrarVenta(venta: Venta)` sobre el modelo de
  dominio `Venta`/`VentaLinea` (`domain/model/Venta.kt`).
- [x] `LocalVentaRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` pasa para sus pruebas. `jvm-tests`
  Verificado: `./gradlew build` en verde (`BUILD SUCCESSFUL`, incluye
  testDebugUnitTest). `LocalVentaRepository.registrarVenta` inserta
  `VentaEntity` + `VentaDetalleEntity` (una por línea) + `MovimientoEntity`
  (tipo `"salida"`, `referenciaTipo="venta"`) en una sola transacción Room
  (`VentaDao.insertVentaCompleta`, patrón `@Transaction` sobre método
  default ya validado en `SucursalDao.insertIfEmpty`, Parte 6) — decisión
  "venta como evento aditivo" de este mismo Parte. `Converters` nuevo
  (`BigDecimal` ↔ `String` vía `toPlainString()`) para los campos
  monetarios/de cantidad; `PdvDatabase` sube a versión 2 con
  `fallbackToDestructiveMigration(dropAllTables = true)` (sin datos de
  producción que preservar en esta etapa).
- [x] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger con la categoría
  correcta. Verificado: `LocalVentaRepositoryTest` (MockK) confirma
  `appLogger.log(LogType.DB_WRITE, ...)` tras un `registrarVenta` exitoso.
- [x] Pruebas unitarias (JUnit5 + MockK) del happy path + 1 caso de error
  (CLAUDE.md §6) — criterio: `./gradlew testDebugUnitTest` en verde.
  Verificado: `LocalVentaRepositoryTest`, 2 pruebas en verde (happy path:
  inserta venta+detalle+movimiento y loguea `DB_WRITE`; error: una falla
  del DAO se propaga sin loguear `DB_WRITE`). `code-reviewer` revisó los 11
  archivos de este sub-paso sin hallazgos (confirmó explícitamente:
  round-trip de `BigDecimal` sin pérdida de precisión, atomicidad real de
  la transacción, y que el destructive migration es una pérdida de datos
  local intencional y razonable en esta etapa, no un descuido).

**3. Repositorio remoto** (POS-22)
- [x] `docs/api-contract.md` actualizado con los endpoints de `ventas`
  antes de tocar código (CLAUDE.md §9) — criterio: sección nueva en el
  contrato, revisada por el usuario. Verificado: `docs/api-contract.md`
  §5 (Ventas) documenta `POST /ventas`, request/response completos y el
  422 por `lineas` vacío o campos obligatorios faltantes.
- [x] Ruta FastAPI (`app/routers/ventas.py`) — criterio: `pytest
  backend/tests/test_ventas.py` en verde, happy path + 1 error (CLAUDE.md
  §6). Verificado: 2 pruebas en verde contra el Postgres real de `docker
  compose` (`alembic upgrade head` aplicó `0002_create_ventas` sin
  drift — `alembic check` confirmó "No new upgrade operations detected"
  tras corregir `alembic/env.py` para importar también `app.models.venta`,
  que antes solo registraba `sucursal` en `Base.metadata`). Smoke test
  manual contra el contenedor corriendo confirma el 422 con el shape de
  Pydantic esperado.
- [x] `RemoteVentaRepository` (Retrofit) en `data/remote/` — criterio:
  `./gradlew testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests`
  Verificado: `./gradlew build` en verde (`BUILD SUCCESSFUL`, incluye
  lint y `testDebugUnitTest`). `VentaApiService.createVenta` (POST
  `/ventas`) + DTOs (`VentaDto`/`VentaCreateRequestDto`, montos como
  String para no perder precisión decimal — igual convención que
  `backend/tests/test_ventas.py`); `fecha: Long` (epoch millis, dominio)
  se convierte a ISO 8601 con `java.time.Instant` (disponible nativo,
  `minSdk 26`). Cableado en `NetworkModule.provideVentaApiService`. Aún
  sin `@Binds` a `VentaRepository` en `RepositoryModule` — se agrega en
  el sub-paso 4 (Wiring), mismo orden que siguió `Sucursal` en la Parte
  6.
- [x] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout y verifica la llamada al
  logger. Verificado: `RemoteVentaRepositoryTest`, 3 pruebas en verde
  (happy path: mapea `Venta` al DTO y no loguea nada; `IOException` del
  API se loguea como `LogType.ERROR` con `sucursalId`/`usuarioId` y se
  repropaga sin capturarla silenciosamente; `HttpException` — ej. el 422
  que el propio contrato documenta — también se loguea y repropaga, no
  solo los fallos de conectividad). `code-reviewer` encontró y se
  corrigió un hallazgo antes de cerrar este ítem: solo se capturaba
  `IOException`, así que un error de servidor (422/500) se saltaba el
  log de auditoría.

**4. Wiring** (POS-23)
- [x] `ViewModel` conectado a los casos de uso reales según `BackendMode`
  (Parte 6) — criterio: prueba de `ViewModel` con estados mockeados
  (CLAUDE.md §6) confirma que resuelve el repositorio correcto según el
  modo. Verificado: `./gradlew build` en verde (incluye lint).
  `ModeAwareVentaRepository` (mismo criterio que `ModeAwareSucursalRepository`
  de la Parte 6: local y local-con-sincronización escriben en Room, remoto
  llama al backend) cableado en `RepositoryModule` vía `@Binds`.
  `VentaViewModel` ahora inyecta `VentaRepository` (interfaz, agnóstica del
  modo) + `ConfiguracionPreferences` (sucursal seleccionada) +
  `SessionManager` (usuario de sesión); `confirmarVenta()` arma el `Venta`
  de dominio y llama `registrarVenta`. `ModeAwareVentaRepositoryTest` (1
  prueba) confirma que cambiar `BackendMode` en DataStore cambia a cuál
  repositorio llega la venta; `VentaViewModelTest` (3 pruebas: happy path,
  falta sucursal/sesión, falla de red) confirma el wiring del ViewModel.
  `code-reviewer` encontró y se corrigió un hallazgo antes de cerrar este
  ítem: el catálogo estático de ejemplo (`CATALOGO_EJEMPLO`) usaba ids
  `"art-1"`..`"art-5"` en vez de UUID — como esos ids ahora fluyen a
  `VentaLinea.articuloId` y de ahí al backend, rompían toda venta en modo
  REMOTO con 422 (`articulo_id` debe ser UUID). Corregido con UUIDs
  válidos en el catálogo.
- [x] Modo local-con-sincronización trata las ventas como eventos aditivos
  (política Parte 6) — criterio: prueba de integración que sincroniza dos
  ventas concurrentes desde dos dispositivos y verifica que ambas se
  aplican (no que una sobreescribe a la otra). Aquí se cierra la
  validación de la rama "eventos aditivos" del motor de sync, diferida
  desde la Parte 6. Verificado:
  `VentaEventoAditivoIntegrationTest` — a diferencia de
  `EventoAditivoCombinerTest` (Parte 6, que solo probaba
  `EventoAditivoCombiner.combinar` con `BigDecimal` sintéticos), esta
  prueba registra dos ventas de una unidad del mismo artículo vía
  `LocalVentaRepository.registrarVenta` en dos `VentaDao` independientes
  (simulando dos dispositivos), captura el `MovimientoEntity` real que
  cada una genera, y confirma que `EventoAditivoCombiner.combinar`
  aplica ambos decrementos (`base=2` con dos deltas de `-1` da `0`, no se
  pierde ninguno).
- [x] Verificado end-to-end en el Xiaomi en los tres modos (local / remoto
  / local-con-sync) — `needs-device` Confirmado por el usuario el
  2026-08-19: una venta de 1 "Refresco de cola" en cada uno de los tres
  modos. Verificado por el agente inspeccionando el estado persistido
  directamente (no solo el mensaje de éxito en pantalla): REMOTO —
  `venta`+`venta_detalle` en el Postgres del backend (`docker compose exec
  db psql`), folio `V-1787162993327`, `articulo_id` UUID correcto,
  `$18.50`. LOCAL y LOCAL_CON_SINCRONIZACION — `venta`+`venta_detalle`+
  `movimiento` (tipo `salida`, `referenciaTipo=venta`) en `pdv.db` del
  dispositivo (`adb exec-out run-as com.pdv.pos cat databases/pdv.db`),
  folios `V-1787163026803` y `V-1787163098575`, ambos con su movimiento de
  salida asociado — confirma que la transacción atómica Room (Repositorio
  local, sub-paso 2) funciona en el dispositivo real, no solo en tests.

### Decisiones abiertas

- [x] Librería de escaneo de barcode y manejo del permiso de cámara
  (`CLAUDE.md` §9: no introducir dependencias de terceros sin señalarlo).
  **Decidido**: CameraX + ML Kit Barcode Scanning con el modelo *bundled*
  (`com.google.mlkit:barcode-scanning`, no la variante `-play-services`) —
  corre 100% on-device, no depende de Google Play Services (indiferente si
  el Xiaomi M2102J20SG los tiene). Permiso de cámara vía
  `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`
  + `Manifest.permission.CAMERA`. Dependencias nuevas a declarar en
  `android/app/build.gradle.kts` en el sub-paso 1 (UI): `androidx.camera:camera-*`
  y `com.google.mlkit:barcode-scanning`.
- [x] Modelado de la venta como evento aditivo: si el evento incluye el
  decremento de inventario, o si el inventario se deriva de la suma de
  ventas. Depende de la decisión de cantidades de la Parte 6. **Decidido**:
  al registrar una venta, `LocalVentaRepository` inserta `venta` +
  `venta_detalle` y, en la misma transacción Room, un `movimiento` tipo
  `"salida"` (`referencia_tipo="venta"`, `referencia_id=venta.localId`) por
  cada línea — el delta negativo de `inventario.cantidad` que ese
  movimiento representa es lo que se combina con
  `EventoAditivoCombiner.combinar` en sync. La fila de `venta`/
  `venta_detalle` en sí no compite con nada (fila nueva por venta) y
  sincroniza con `LastWriteWinsSyncEngine`, igual que `Sucursal`. Separación
  limpia entre los dos motores ya construidos en la Parte 6.

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

**Gap corregido en la Parte 9 (2026-08-19)**: `LocalVentaRepository`/`VentaDao`
y `POST /ventas` del backend solo escribían el `movimiento` tipo `"salida"`,
nunca aplicaban el decremento a `inventario.cantidad` — a diferencia de
`EntradaDao`/`POST /entradas` (Parte 8), que sí hacían el upsert real desde
el principio. Sin esto, la existencia que consulta la Parte 9 (Módulo
Inventario) no reflejaba ventas ya realizadas. Corregido con el mismo
patrón de delta con signo vía `EventoAditivoCombiner` (Android) y resta
directa con `db.flush()` previo para validar `articulo_id` (backend, nuevo
404 documentado en `docs/api-contract.md` §5). Ver Parte 9, sub-paso 2.

---

## Parte 8: Módulo Entrada de mercancía  <!-- POS-24 -->

Simular la entrada de artículos nuevos (agregándolos al maestro de datos y
luego al inventario) y de artículos existentes (agregándolos solo al
inventario), con los datos principales del artículo y el estante de
almacenamiento. Toda entrada queda asociada al usuario en turno (Parte 4).

**Nota (agregada tras revisión del sub-paso 1, 2026-08-19)**: se incorpora
escaneo de código de barras vía cámara (mismo enfoque CameraX + ML Kit de la
Parte 7) para agilizar la búsqueda de artículo existente y la captura del
código en artículo nuevo — ampliación aprobada explícitamente por el usuario
al checklist original. Categoría, unidad de medida y estante quedan como
campos de texto libre en esta Parte; se evaluará convertirlos en listas
cuando se implemente el módulo de Inventario (Parte 9), no antes.

### Checklist

**1. UI** (POS-25)
- [x] Propuesta de pantalla (artículo nuevo vs. existente, estante de
  almacenamiento) presentada y aprobada — criterio: aprobación explícita
  registrada antes de implementar. Aprobado el 2026-08-19 (pantalla
  `EntradaScreen`: segmented button "Artículo nuevo"/"Artículo existente",
  búsqueda del catálogo de ejemplo o formulario completo del artículo,
  cantidad y estante comunes; navegación manual desde `HelloScreen`, mismo
  patrón sin Navigation Compose que Venta/Configuración).
- [x] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable. Verificado:
  `./gradlew build` en verde (`BUILD SUCCESSFUL`, incluye lint); catálogo
  estático de 5 artículos (mismo de `VentaViewModel`, Parte 7) en
  `EntradaViewModel`; botón "Entrada de mercancía" en `HelloScreen` navega
  a `EntradaScreen`.
- [x] Escaneo de código de barras vía cámara (CameraX + ML Kit, mismo
  enfoque que la Parte 7) disponible para buscar artículo existente y para
  capturar el código de un artículo nuevo — criterio: `./gradlew build`
  pasa y el diálogo de escaneo abre/cierra correctamente sin fugas de
  cámara (mismo criterio de cierre que `BarcodeScannerDialog` de la Parte
  7). Ampliación al checklist aprobada explícitamente por el usuario tras
  revisar el sub-paso (2026-08-19), antes de cerrarlo. Verificado:
  `EntradaScreen` reutiliza `BarcodeScannerDialog` (Parte 7, sin
  duplicar lógica de cámara) desde ambos modos vía `ObjetivoEscaneo`
  (`BUSQUEDA_EXISTENTE` / `CODIGO_BARRAS_NUEVO`); `./gradlew build` en
  verde.
- [x] Instalado y verificado en el Xiaomi — `needs-device` Confirmado por
  el usuario el 2026-08-19: pantalla de Entrada instalada y verificada,
  incluyendo el escaneo de código de barras en ambos modos (artículo
  existente y artículo nuevo).

**2. Repositorio local** (POS-26)
- [x] `EntradaRepository` (interfaz) en `domain/repository/` — criterio:
  compila sin Room ni Retrofit. Verificado: `EntradaRepository` expone solo
  `suspend fun registrarEntrada(entrada: Entrada)` sobre el modelo de
  dominio `Entrada` (sealed class `DeArticuloNuevo`/`DeArticuloExistente`,
  `domain/model/Entrada.kt`).
- [x] `LocalEntradaRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests` Verificado:
  `./gradlew build` en verde (`BUILD SUCCESSFUL`, incluye
  `testDebugUnitTest`). Primera escritura real de `articulos`/`inventario`
  en Room (`ArticuloEntity`/`InventarioEntity`, nuevas) — hasta la Parte 7
  solo existían como modelo de dominio con catálogo estático.
  `LocalEntradaRepository.registrarEntrada` llama a
  `EntradaDao.insertEntradaCompleta`, que en una sola transacción Room (a)
  inserta el artículo si es nuevo, (b) hace upsert de `inventario`
  aplicando el delta con signo vía `EventoAditivoCombiner.combinar` (nunca
  un `UPDATE cantidad = X` directo, per PLAN.md Parte 6) y (c) inserta el
  `movimiento` tipo `"entrada"` (`referencia_tipo="entrada_manual"`).
  Cierra la validación de `EventoAditivoCombiner` contra una tabla
  `inventario` real, diferida desde la Parte 6/7 (antes solo se probó con
  `BigDecimal` sintéticos y con `MovimientoEntity` capturado, nunca contra
  una fila de inventario persistida).
- [x] Cada entrada queda asociada al `usuario` en turno (Parte 4) —
  criterio: prueba unitaria verifica que el registro guarda el usuario de
  la sesión activa. Verificado: `LocalEntradaRepositoryTest` confirma que
  el `MovimientoEntity` insertado lleva `usuarioId` igual al de la
  `Entrada` pasada por el llamador (la `Entrada` la arma el `ViewModel`
  con `SessionManager.session`, mismo patrón que `VentaViewModel`, en el
  sub-paso 4 de wiring).
- [x] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger. Verificado:
  `LocalEntradaRepositoryTest` confirma `appLogger.log(LogType.DB_WRITE,
  ...)` tras un `registrarEntrada` exitoso.
- [x] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde. Verificado:
  `LocalEntradaRepositoryTest`, 3 pruebas en verde (happy path artículo
  existente: upsert de inventario + movimiento + log; happy path artículo
  nuevo: además inserta la entidad `ArticuloEntity`; error: una falla del
  DAO se propaga sin loguear `DB_WRITE`).

**3. Repositorio remoto** (POS-27)
- [x] `docs/api-contract.md` actualizado con los endpoints de
  `entradas`/`movimientos` (CLAUDE.md §9) — criterio: sección nueva,
  revisada. Verificado: `docs/api-contract.md` §6 (Entradas de mercancía)
  documenta `POST /entradas` (request para artículo nuevo y para artículo
  existente, response con `movimiento`/`inventario`/`articulo`, 422 por
  falta de datos o `articulo_id`+`articulo_nuevo` ambiguo, 404 por
  `articulo_id` inexistente); reordena las secciones 6-7 originales
  (Convenciones generales → 7, Pendiente de definir → 8) y actualiza el
  pendiente de la 8 para reflejar que la alta de
  `articulos`/`inventario`/`movimientos` ya no está bloqueada.
- [x] Ruta FastAPI (`app/routers/entradas.py`) — criterio: `pytest
  backend/tests/test_entradas.py` en verde. Verificado: 4 pruebas en verde
  contra el Postgres real de `docker compose` (`alembic upgrade head`
  aplicó `0003_create_articulos_inventario_movimientos` sin drift —
  `alembic check` confirmó "No new upgrade operations detected" tras
  registrar los 3 modelos nuevos en `alembic/env.py`). Smoke test manual
  con `curl` contra el contenedor reconstruido confirma el 201 con
  `movimiento`+`inventario`+`articulo` y el 422 por artículo ambiguo.
  `POST /entradas` hace alta de artículo (si es nuevo) + upsert de
  `inventario` (`cantidad_actual + cantidad_entrada`, nunca un `UPDATE`
  directo — mismo principio que `EventoAditivoCombiner` del lado Android)
  + inserción del `movimiento`, en una sola transacción de sesión
  SQLAlchemy.
- [x] `RemoteEntradaRepository` (Retrofit) — criterio: `./gradlew
  testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests` Verificado:
  `./gradlew build` en verde (`BUILD SUCCESSFUL`, incluye lint y
  `testDebugUnitTest`). `EntradaApiService.createEntrada` (POST
  `/entradas`) + DTOs (`EntradaCreateRequestDto`/`ArticuloNuevoRequestDto`/
  `EntradaDto`, montos como String); mapea el sealed class `Entrada` al
  request (`articulo_id` XOR `articulo_nuevo` según la variante). Cableado
  en `NetworkModule.provideEntradaApiService`. Aún sin `@Binds` a
  `EntradaRepository` en `RepositoryModule` — se agrega en el sub-paso 4
  (Wiring), mismo orden que siguieron Sucursal (Parte 6) y Venta (Parte 7).
- [x] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout. Verificado:
  `RemoteEntradaRepositoryTest`, 4 pruebas en verde (dos happy path —
  artículo existente y artículo nuevo, sin loguear nada — y dos de fallo:
  `IOException` y `HttpException` del API se loguean como `LogType.ERROR`
  con `sucursalId`/`usuario` y se repropagan sin capturarlas
  silenciosamente).

**4. Wiring** (POS-28)
- [x] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto. Verificado:
  `./gradlew build` en verde (incluye lint). `ModeAwareEntradaRepository`
  (mismo criterio que `ModeAwareVentaRepository`/`ModeAwareSucursalRepository`:
  local y local-con-sincronización escriben en Room, remoto llama al
  backend) cableado en `RepositoryModule` vía `@Binds`. `EntradaViewModel`
  ahora inyecta `EntradaRepository` (interfaz, agnóstica del modo) +
  `ConfiguracionPreferences` (sucursal seleccionada) + `SessionManager`
  (usuario de sesión); `registrarEntrada()` valida el formulario, arma el
  `Entrada` de dominio (`DeArticuloNuevo`/`DeArticuloExistente` según
  `tipo`) y llama `registrarEntrada`. `ModeAwareEntradaRepositoryTest` (1
  prueba) confirma que cambiar `BackendMode` en DataStore cambia a cuál
  repositorio llega la entrada; `EntradaViewModelTest` (5 pruebas: happy
  path artículo existente, happy path artículo nuevo, falta
  sucursal/sesión, error de validación de cantidad, falla de red) confirma
  el wiring del ViewModel.
- [x] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`
  Confirmado por el usuario el 2026-08-19. Verificado por el agente
  inspeccionando el estado persistido directamente (no solo el mensaje de
  éxito en pantalla), mismo criterio que la Parte 7: REMOTO — artículo
  "Galletas" (sku `121212`) en `articulos`+`inventario`
  (cantidad 2, ubicación "galletas") +`movimientos` (tipo `entrada`) del
  Postgres del backend (`docker compose exec db psql`). LOCAL y
  LOCAL_CON_SINCRONIZACION — artículos "Chocolate" (sku `212131`,
  cantidad 3, ubicación "Dulces") y "Sabritas" (sku `124124`, cantidad 10,
  ubicación "Sabritas") en `pdv.db` del dispositivo (`adb exec-out run-as
  com.pdv.pos cat databases/pdv.db{,-wal,-shm}` — necesarios los 3 archivos
  juntos porque Room usa WAL y los datos no confirmados viven en
  `-wal`), cada uno con su `inventario`/`movimiento` de tipo `entrada`
  asociado y `is_synced=false` (escritura local sin sincronizar).

  **Hallazgo durante la verificación (no bloqueante, documentado)**: la
  primera prueba del usuario en modo REMOTO con "Artículo existente" dio
  `404 Not Found`. Causa raíz confirmada inspeccionando Postgres y los
  logs del contenedor: el catálogo de 5 artículos del cuadro de búsqueda
  (`EntradaViewModel`, mismo dato estático de ejemplo que
  `VentaViewModel` desde el sub-paso 1) nunca existió como fila real en
  ningún backend — la búsqueda de "artículo existente" sigue siendo
  puramente demo, sin consultar datos reales, porque esa consulta es
  alcance de la Parte 9 (Inventario), fuera de esta Parte (confirmado al
  aprobar el alcance de la Parte 8). El backend rechaza correctamente un
  `articulo_id` inexistente con 404 (comportamiento esperado del router,
  sección 6.1 del contrato); Room, en cambio, no tiene FK real entre
  `inventario`/`movimientos` y `articulos`, así que en LOCAL insertó la
  fila igual sin validar — no es un bug de esta Parte, es la ausencia de
  validación de integridad referencial local, consistente con el resto
  del esquema (`venta_detalle.articulo_id` tampoco tiene FK real en el
  backend, Parte 7). La verificación end-to-end final se hizo con
  "Artículo nuevo" en los tres modos, que ejercita el mismo pipeline de
  escritura sin depender de datos preexistentes. Queda para la Parte 9
  reemplazar el catálogo estático por una consulta real de artículos.

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 9: Módulo Inventario  <!-- POS-29 -->

Consultar el inventario, la existencia de artículos específicos, y
modificar los atributos de esos artículos consultados.

### Checklist

**1. UI** (POS-30)
- [x] Propuesta de pantalla (consulta de existencias, edición de
  atributos) presentada y aprobada — criterio: aprobación explícita
  registrada antes de implementar. Aprobado el 2026-08-19 (pantalla
  `InventarioScreen`: búsqueda/escaneo, lista paginada con selector de
  registros por página, diálogo de edición de atributos + cantidad en
  existencia; navegación manual desde `HelloScreen`, mismo patrón sin
  Navigation Compose que el resto de módulos). Ampliación de paginación
  (selector de tamaño de página) aprobada explícitamente por el usuario el
  2026-08-19, antes de implementar.
- [x] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable. Verificado:
  `./gradlew build` en verde (`BUILD SUCCESSFUL`, incluye lint); catálogo
  estático de 12 artículos con existencia/ubicación en `InventarioViewModel`;
  botón "Inventario" en `HelloScreen` navega a `InventarioScreen`.
- [x] Campos `categoria`/`unidad_medida`/`ubicacion` como lista editable con
  sugerencias (valores ya usados) en vez de texto libre puro, retroactivo
  también a `EntradaScreen` (Parte 8) — criterio: `./gradlew build` pasa;
  escribir un valor fuera de la lista lo acepta igual como valor nuevo.
  Ampliación aprobada explícitamente por el usuario el 2026-08-19 (mismo
  comentario diferido desde la Parte 8, "se evaluará convertirlos en listas
  cuando se implemente el módulo de Inventario"). Verificado:
  `EditableDropdownField` (nuevo, `com.pdv.pos.ui`, patrón "editable exposed
  dropdown" de Material3) usado en `InventarioScreen` (categoría, unidad de
  medida, ubicación) y en `EntradaScreen` (categoría/unidad de medida del
  artículo nuevo, estante de la entrada); `./gradlew build` en verde.
- [x] Exportar el inventario consultado (respetando la búsqueda activa) a
  CSV y a Excel (XLSX) — criterio: `./gradlew build` pasa; el archivo
  generado abre correctamente en una hoja de cálculo. Ampliación aprobada
  explícitamente por el usuario el 2026-08-19, antes de pasar al sub-paso 2.
  Sin librerías de terceros nuevas (CLAUDE.md §9): CSV es texto plano; XLSX
  se genera a mano (ZIP + XML mínimo vía `java.util.zip.ZipOutputStream` del
  JDK, sin fórmulas ni estilos) para evitar Apache POI (pesado, con
  problemas conocidos de compatibilidad en Android). Exportación vía
  `Intent.ACTION_SEND` + `FileProvider` (share sheet estándar de Android).
- [x] Instalado y verificado en el Xiaomi — `needs-device` Confirmado por el
  usuario el 2026-08-19: pantalla de Inventario instalada y verificada
  (búsqueda, escaneo, paginación, edición con campos de lista editable,
  retrofit de `EntradaScreen`, exportación a CSV y a Excel compartida desde
  el share sheet de Android).

**2. Repositorio local** (POS-31)
- [x] `InventarioRepository` (interfaz) en `domain/repository/` —
  criterio: compila sin Room ni Retrofit. Verificado: la interfaz expone
  `observarInventario(sucursalId, busqueda, pagina, tamanioPagina)`,
  `observarCategorias()`/`observarUnidadesMedida()`/`observarUbicaciones(sucursalId)`
  y `suspend fun actualizarArticulo(edicion: EdicionArticulo)`, sobre los
  modelos de dominio `PaginaInventario`/`EdicionArticulo` nuevos; sin
  imports de Room ni Retrofit.
- [x] `LocalInventarioRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests` Verificado:
  `./gradlew build` en verde (`BUILD SUCCESSFUL`, incluye lint y
  `testDebugUnitTest`). `InventarioDao` nuevo: `observarPagina`/
  `observarTotal` (join `inventario`+`articulos` con `LIMIT`/`OFFSET` real
  y filtro `LIKE` sobre nombre/sku/código de barras, reemplazando el
  recorte en memoria del sub-paso 1), `observarCategorias`/
  `observarUnidadesMedida` (`SELECT DISTINCT` sobre `articulos`) y
  `observarUbicaciones` (`SELECT DISTINCT` sobre `inventario` por
  sucursal) — mismos datos que alimentaban los `EditableDropdownField`
  estáticos del sub-paso 1, ahora reales. `actualizarArticuloCompleto`
  (`@Transaction`): `UPDATE` directo de atributos de catálogo
  (last-write-wins) + ajuste de cantidad en existencia vía delta con signo
  (`EventoAditivoCombiner`, nunca `UPDATE cantidad = X` directo, cierra la
  decisión de la Parte 6 sobre el ajuste manual de la Parte 9) + inserción
  de `movimiento` tipo `"ajuste"` — mismo patrón que
  `EntradaDao.insertEntradaCompleta`. Si la cantidad no cambió (delta = 0)
  no se toca `inventario` ni se inserta movimiento, solo el artículo.
- [x] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger. Verificado:
  `LocalInventarioRepositoryTest` confirma `appLogger.log(LogType.DB_WRITE,
  ...)` tras un `actualizarArticulo` exitoso.
- [x] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde. Verificado:
  `LocalInventarioRepositoryTest`, 2 pruebas en verde (happy path: actualiza
  atributos + cantidad y loguea `DB_WRITE`; error: una falla del DAO se
  propaga sin loguear `DB_WRITE`).

**Gap de la Parte 7 corregido en este sub-paso (aprobado explícitamente por
el usuario, ver nota al final de la Parte 7)**: `VentaDao`/
`LocalVentaRepository` ahora aplican el decremento real de
`inventario.cantidad` (delta negativo vía `EventoAditivoCombiner`) al
registrar una venta, no solo el `movimiento` tipo `"salida"`. Backend
(`POST /ventas`) recibe el mismo tratamiento — ver sub-paso siguiente para
el detalle del lado remoto; contrato actualizado en
`docs/api-contract.md` §5 (incluye el nuevo `404` por `articulo_id`
inexistente en alguna línea). Verificado: `LocalVentaRepositoryTest`/
`ModeAwareVentaRepositoryTest`/`VentaEventoAditivoIntegrationTest`
actualizados y en verde; `pytest backend/tests/test_ventas.py` (4 pruebas,
incluye `test_create_venta_decrements_inventario_and_creates_movimiento` y
el nuevo caso 404) en verde contra el Postgres real de `docker compose`;
smoke test manual con `curl` + `docker compose exec db psql` confirmó
`10 → 6` tras una venta de 4 unidades, con su `movimiento` tipo `salida`
asociado.

**3. Repositorio remoto** (POS-32)
- [x] `docs/api-contract.md` actualizado con los endpoints de
  `inventario` (CLAUDE.md §9) — criterio: sección nueva, revisada.
  Verificado: `docs/api-contract.md` §7 (Inventario) documenta `GET
  /inventario` (listar con `q`/`page`/`page_size`), `PATCH
  /inventario/{articulo_id}` (atributos de catálogo + ajuste de cantidad
  con delta con signo, `movimiento` `null` si el delta es `0`, 404 por
  artículo inexistente) y las tres rutas de solo lectura `GET
  /articulos/categorias`, `GET /articulos/unidades-medida`, `GET
  /inventario/ubicaciones` que alimentan `EditableDropdownField`; reordena
  Convenciones generales → 8 y Pendiente de definir → 9 (mismo criterio que
  la Parte 8 con la sección 6).
- [x] Ruta FastAPI (`app/routers/inventario.py`) — criterio: `pytest
  backend/tests/test_inventario.py` en verde. Verificado: 7 pruebas en
  verde contra el Postgres real de `docker compose` (listar con y sin `q`,
  página inválida → 422, ajuste con cambio de cantidad y sin cambio de
  cantidad — sin `movimiento` en este último caso —, 404 por artículo
  inexistente, valores de categoría/unidad de medida/ubicación). Sin
  migración de Alembic nueva: las tres tablas (`articulos`, `inventario`,
  `movimientos`) ya existían desde la Parte 8, esta Parte solo agrega
  rutas de lectura/ajuste sobre ellas. Backend reconstruido en Docker y
  probado con `curl` real contra el contenedor: `GET /inventario`,
  `PATCH /inventario/{id}` (`10 → 7` con `movimiento` tipo `ajuste`,
  `cantidad=-3`) y `GET /articulos/categorias` contra datos reales.
- [x] `RemoteInventarioRepository` (Retrofit) — criterio: `./gradlew
  testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests` Verificado:
  `./gradlew build` en verde (`BUILD SUCCESSFUL`, incluye lint y
  `testDebugUnitTest`). `InventarioApiService` + DTOs
  (`InventarioItemDto`/`InventarioListResponseDto`/
  `ArticuloEdicionRequestDto`/`AjusteInventarioDto`/`ValoresDto`, montos
  como String); cableado en `NetworkModule.provideInventarioApiService`.
  Los métodos de lectura (`observarInventario`/`observarCategorias`/
  `observarUnidadesMedida`/`observarUbicaciones`) propagan fallas por el
  `Flow` sin capturarlas, mismo criterio que `RemoteSucursalRepository`
  (Parte 6) — no llevan `usuarioId` en la firma del dominio, así que no
  pueden loguear (`AppLogger.log` exige `sucursalId` + `usuario`). Aún sin
  `@Binds` a `InventarioRepository` en `RepositoryModule` — se agrega en
  el sub-paso 4 (Wiring), mismo orden que Sucursal/Venta/Entrada.
- [x] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout. Verificado:
  `RemoteInventarioRepositoryTest`, 5 pruebas en verde (dos happy path de
  lectura sin loguear nada, un happy path de `actualizarArticulo` sin
  loguear nada, y `IOException`/`HttpException` de `actualizarArticulo` —
  la única operación de escritura, la única con `usuarioId` disponible —
  logueadas como `LogType.ERROR` con `sucursalId`/`usuario` y repropagadas
  sin capturarlas silenciosamente).

**4. Wiring** (POS-33)
- [x] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto.
  Verificado: `./gradlew build` en verde (incluye lint).
  `ModeAwareInventarioRepository` (combina el criterio reactivo de
  `ModeAwareSucursalRepository` para las lecturas `Flow` con el criterio de
  snapshot suspend de `ModeAwareEntradaRepository` para `actualizarArticulo`,
  porque `InventarioRepository` tiene ambos tipos de método) cableado en
  `RepositoryModule` vía `@Binds`. `InventarioViewModel` reescrito para
  inyectar `InventarioRepository` + `ConfiguracionPreferences` +
  `SessionManager` + `InventarioExportManager`; el catálogo estático del
  sub-paso 1 queda reemplazado por un pipeline reactivo
  (`combine(sucursalId, parametros).flatMapLatest { ... observarInventario
  }`) que se re-suscribe solo cuando cambian sucursal/búsqueda/página/tamaño
  de página. La exportación CSV/Excel recorre todas las páginas que
  coinciden con la búsqueda activa (no solo la página visible) contra el
  repositorio real. `ModeAwareInventarioRepositoryTest` (1 prueba) confirma
  que cambiar `BackendMode` en DataStore cambia a cuál repositorio llega el
  ajuste; `InventarioViewModelTest` (4 pruebas: happy path, falta
  sucursal/sesión, error de validación de cantidad, falla de red) confirma
  el wiring del ViewModel.

  **Hallazgo corregido antes de cerrar este ítem**: `RemoteInventarioRepository.observarInventario`
  no es reactivo de verdad (una sola llamada Retrofit por suscripción, a
  diferencia del `Flow` de Room que se refresca solo) — sin ajuste, un
  `actualizarArticulo` exitoso en modo REMOTO no habría refrescado la lista
  visible. Corregido con un contador `version` en los parámetros de consulta
  del `ViewModel` que se incrementa tras cada `actualizarArticulo` exitoso,
  forzando al `flatMapLatest` a re-suscribirse (no-op inofensivo en modo
  local, donde Room ya se refresca solo).
- [x] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`
  Confirmado por el usuario el 2026-08-19/20: instalado y probado
  exitosamente en los tres modos (Local, Remoto, Local con sincronización) —
  búsqueda/escaneo, paginación, edición de atributos + cantidad con listas
  editables, exportación CSV/Excel. Verificado por el agente inspeccionando
  el estado persistido directamente (mismo criterio que las Partes 7 y 8):
  LOCAL/LOCAL_CON_SINCRONIZACION — `pdv.db` del dispositivo (`adb exec-out
  run-as com.pdv.pos cat databases/pdv.db{,-wal,-shm}`) muestra movimientos
  tipo `ajuste` (`referenciaTipo=ajuste_manual`) con el delta correcto y
  `inventario.cantidad` ya actualizado (ej. "Sabritas" 10 → 15, delta +5).
  REMOTO — Postgres del backend (`docker compose exec db psql`) muestra
  "Galletas" (existencia original de 2 en la Parte 8) en 9.000, con su
  `movimiento` tipo `ajuste` (`cantidad=7.000`, `usuario_id=admin`).

  **Hallazgo corregido antes de cerrar este ítem**: en modo local y
  local-con-sincronización, las cantidades en existencia se mostraban con la
  escala cruda que trae el `BigDecimal` de Room (ej. "10" en vez de "10.00"),
  inconsistente con el backend (columnas `Numeric(14, 3)`, siempre 3
  decimales) — relevante para unidades como kg/litros donde el usuario
  necesita ver los decimales. Corregido con `BigDecimal.formatoCantidad()`
  (nuevo, `com.pdv.pos.inventario`, 2 decimales fijos vía
  `setScale(2, RoundingMode.HALF_UP)`), aplicado solo en los puntos de
  presentación/prellenado (lista, diálogo de edición, exportación CSV/Excel)
  — nunca en los cálculos de delta. Pruebas de `InventarioCsvExporterTest`/
  `InventarioExcelExporterTest` actualizadas; `./gradlew build` en verde.

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 10: Módulo Caja  <!-- POS-39 -->

Realizar cortes de caja de lo vendido en un periodo especificado: uno o
varios cortes parciales durante el día y un corte final que consolida el
día completo, con posibilidad de registrar retiros de efectivo que se
descuentan del monto esperado.

### Checklist

**1. UI** (POS-40)
- [x] Propuesta de pantalla (corte por periodo especificado) presentada
  y aprobada — criterio: aprobación explícita registrada antes de
  implementar. Aprobado el 2026-08-20, ampliado el mismo día con corte
  parcial/final y retiros de efectivo (ver "Decisiones abiertas"):
  selector "Tipo de corte" (parcial/final, `SegmentedButton`); periodo de
  solo lectura y calculado automáticamente si es parcial, con date/time
  pickers editables (default: primera venta de hoy a las 10:00 pm) si es
  final; botón "Calcular corte" (desglose total ventas/efectivo/tarjeta/
  retiros, monto esperado); campo "Monto contado" con diferencia
  calculada; botón "Guardar corte"; sección independiente "Registrar
  retiro de efectivo" (monto + motivo opcional); historial de cortes
  anteriores con su tipo.
- [x] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable. Reemplaza la versión
  inicial (solo corte único) por el diseño de arriba. Verificado:
  `CajaScreen`/`CajaViewModel`/`CajaUiState` (`caja/`) +
  `CorteCaja`/`RetiroEfectivo` (`domain/model/`); `./gradlew build` en
  verde (`BUILD SUCCESSFUL`).
- [x] Instalado y verificado en el Xiaomi — `needs-device` Confirmado por
  el usuario el 2026-08-20: pantalla de Caja instalada y verificada
  (selector de tipo de corte, periodo automático/editable según tipo,
  cálculo con retiros, diálogo de registro de retiro, guardado en
  historial).

**2. Repositorio local** (POS-41)
- [x] `CajaRepository` (interfaz, `cortes_caja`) en `domain/repository/` —
  criterio: compila sin Room ni Retrofit. Verificado:
  `domain/repository/CajaRepository.kt` (`calcularTotales`,
  `guardarCorte`), solo importa `domain/model/`.
- [x] `LocalCajaRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests` Verificado:
  `LocalCajaRepository` + `CajaDao`/`CorteCajaEntity`
  (`data/local/`); `LocalCajaRepositoryTest` (3 pruebas) en verde.
- [x] `RetiroEfectivoRepository` (interfaz) en `domain/repository/` —
  criterio: compila sin Room ni Retrofit. Verificado:
  `domain/repository/RetiroEfectivoRepository.kt`.
- [x] `LocalRetiroEfectivoRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests` Verificado:
  `LocalRetiroEfectivoRepository` + `RetiroDao`/`RetiroEfectivoEntity`
  (`data/local/`); `LocalRetiroEfectivoRepositoryTest` (2 pruebas) en
  verde.
- [x] Cálculo de totales de un corte (ventas/retiros agregados por rango
  de fecha, `monto_esperado = total_efectivo - total_retiros`) — criterio:
  prueba unitaria con datos de ventas y retiros conocidos verifica cada
  total. Verificado: `LocalCajaRepositoryTest."calcularTotales sums
  ventas by metodo de pago and subtracts retiros from monto esperado"`.
  La agregación lee las filas del periodo (`VentaDao.getVentasDelPeriodo`,
  nuevo; `RetiroDao.getRetirosDelPeriodo`) y suma en Kotlin con
  `BigDecimal`, no con `SUM()` de SQLite (evita la conversión a
  double de SQLite en columnas `BigDecimal`, que Room persiste como
  texto vía `Converters`).
- [x] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger. Verificado en ambos
  repositorios.
- [x] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde. Verificado: 5 pruebas
  en verde (`LocalCajaRepositoryTest` x3, `LocalRetiroEfectivoRepositoryTest`
  x2); `./gradlew build` completo también en verde (`BUILD SUCCESSFUL`).

**3. Repositorio remoto** (POS-42)
- [x] `docs/api-contract.md` actualizado con los endpoints de
  `cortes_caja` y `retiros_efectivo` (CLAUDE.md §9) — criterio: sección
  nueva, revisada. Verificado: sección 8 (`POST /cortes-caja`, `POST
  /retiros-efectivo`, `GET /cortes-caja/totales` — contraparte remota de
  `LocalCajaRepository.calcularTotales`, necesaria para que `RemoteCajaRepository`
  cumpla la interfaz `CajaRepository` completa); secciones renumeradas
  (9 Convenciones, 10 Pendiente de definir).
- [x] Ruta FastAPI (`app/routers/caja.py`) — criterio: `pytest
  backend/tests/test_caja.py` en verde. Verificado: 6 pruebas en verde
  (corte happy path, tipo inválido → 422, retiro happy path, monto
  inválido → 422, totales happy path, totales sin sucursal_id → 422);
  migración `0004_create_cortes_caja_retiros_efectivo` aplicada
  (`alembic upgrade head`) contra el Postgres de `docker compose`;
  backend reconstruido y probado con `curl` real contra el contenedor
  (los 3 endpoints). Suite completa del backend: 25 pruebas en verde.
- [x] `RemoteCajaRepository` y `RemoteRetiroEfectivoRepository` (Retrofit)
  — criterio: `./gradlew testDebugUnitTest` pasa mockeando Retrofit.
  `jvm-tests` Verificado: `CajaApiService`/`RetiroApiService` +
  DTOs (`data/remote/`); `RemoteCajaRepositoryTest` (4 pruebas) +
  `RemoteRetiroEfectivoRepositoryTest` (3 pruebas) en verde;
  `./gradlew build` completo en verde.
- [x] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout. Verificado en ambos
  repositorios (`IOException`/`HttpException`, mismo criterio que
  `RemoteInventarioRepository`: las lecturas sin `usuarioId`
  —`calcularTotales`— propagan la falla sin loggear; solo las
  escrituras, que sí traen `usuarioId`, loguean `ERROR`).

**4. Wiring** (POS-43)
- [x] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto.
  Verificado: `ModeAwareCajaRepository`/`ModeAwareRetiroEfectivoRepository`
  (`data/`, bindeados en `RepositoryModule`) resuelven local/remota según
  `ConfiguracionPreferences.deviceConfig.backendMode`, mismo patrón que
  `ModeAwareVentaRepository`; `CajaViewModel` reescrito para usar
  `CajaRepository`/`RetiroEfectivoRepository` reales (sucursal desde
  `ConfiguracionPreferences`, usuario desde `SessionManager`) en vez de
  los datos estáticos del sub-paso 1. `ModeAwareCajaRepositoryTest` (2
  pruebas) + `ModeAwareRetiroEfectivoRepositoryTest` (1 prueba) +
  `CajaViewModelTest` (6 pruebas) en verde. El default de periodo del
  corte parcial/final ya no simula una "primera venta"; usa el fallback
  decidido en la Parte 10 ("Decisiones abiertas": hoy 00:00 cuando no hay
  corte previo ese día).
- [x] Los cortes de caja y los retiros de efectivo se tratan como eventos
  aditivos en modo local-con-sincronización (política Parte 6) —
  criterio: prueba de integración sincroniza dos cortes y dos retiros
  concurrentes desde dos dispositivos y verifica que todos se aplican.
  Verificado: `CajaEventoAditivoIntegrationTest` (2 pruebas) — a
  diferencia de `inventario.cantidad` (delta con signo vía
  `EventoAditivoCombiner`), aquí "aditivo" es más simple: cada corte/
  retiro es su propio registro con `local_id` por dispositivo, así que
  "ambos se aplican" se reduce a que dos `INSERT` concurrentes nunca se
  pisan entre sí — sin campo compartido que combinar.
- [x] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`
  Confirmado por el usuario el 2026-08-20, con una corrección en el
  camino: el corte parcial no reflejaba un retiro registrado justo
  después de calcular (el retiro sí quedaba guardado — confirmado vía
  `DB_WRITE` en el log de la app — pero el `fechaFin` del periodo se
  congelaba desde la última vez que se fijó, en vez de recalcularse a la
  hora de solicitud en cada click de "Calcular corte"; el retiro caía
  fuera del rango consultado hasta el ciclo de cálculo siguiente).
  Corregido en `CajaViewModel.onCalcularClick`: para tipo parcial,
  recalcula el periodo antes de consultar; el corte final no se toca (su
  periodo sigue editable por el usuario). `CajaViewModelTest` +2 pruebas
  de regresión (8 en total). Verificado en Local, Remoto y Local con
  sincronización tras la corrección.

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

### Decisiones abiertas

- [x] Regla de consolidación del corte final: ¿suma los cortes parciales
  ya guardados, o recalcula desde cero sobre su propio periodo?
  **Decidido**: recalcula desde cero — cada corte (parcial o final)
  agrega `ventas`/`retiros_efectivo` filtrando por su propio
  `fecha_inicio`/`fecha_fin`. El corte final no depende de que los
  parciales se hayan hecho ni sean correctos: como su rango cubre todo el
  día, ya incluye lo mismo que ellos.
- [x] Relación `retiros_efectivo`↔`cortes_caja`: ¿FK directa o agregación
  por rango de fecha? **Decidido**: agregación por fecha, mismo patrón
  que `ventas`↔`cortes_caja` (sin FK); ver `docs/schema-pos.json`.
- [x] Horario del corte parcial: ¿editable como el final? **Decidido**:
  no editable — inicio = fin del último corte del día (parcial o final),
  o primera venta de hoy si no hay corte previo ese día; fin = hora de
  solicitud (ahora).
- [x] Caso sin ninguna venta registrada hoy todavía: ¿fallback de
  `fecha_inicio`? **Decidido**: hoy 00:00, editable únicamente en el
  corte final.

---

## Parte 11: Módulo Devoluciones  <!-- POS-44 -->

Registrar devoluciones de clientes y consultar la lista de productos en
esta condición para gestionar la devolución con el proveedor.

### Checklist

**1. UI** (POS-45)
- [x] Propuesta de pantalla (registrar devolución, consultar lista para
  gestión con proveedor) presentada y aprobada — criterio: aprobación
  explícita registrada antes de implementar. Aprobado el 2026-08-20
  (pantalla `DevolucionScreen`: búsqueda de artículo del catálogo estático,
  formulario cantidad/motivo/condición, líneas de la devolución, folio de
  venta original opcional, botón "Registrar devolución", historial de la
  sesión actual para gestión con proveedor — sin `GET` de listado
  persistente, mismo criterio que Ventas Parte 7; navegación manual desde
  `HelloScreen`).
- [x] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable. Verificado: `./gradlew
  build` en verde (`BUILD SUCCESSFUL`, incluye lint); catálogo estático de
  3 artículos de ejemplo en `DevolucionViewModel`; botón "Devoluciones" en
  `HelloScreen` navega a `DevolucionScreen`. `code-reviewer` encontró y se
  corrigió un hallazgo antes de cerrar este ítem: `quitarLinea` filtraba
  por `articulo.id`, así que si el mismo artículo se agregaba dos veces con
  distinta condición/motivo (caso válido en Devoluciones, a diferencia del
  carrito de Venta que garantiza una sola línea por artículo), "Quitar" en
  una fila borraba todas las líneas de ese artículo — corregido con un `id`
  propio por `LineaDevolucion`.
- [x] Instalado y verificado en el Xiaomi — `needs-device` Confirmado por
  el usuario el 2026-08-20: pantalla de Devoluciones instalada y verificada
  (búsqueda de artículo, formulario cantidad/motivo/condición, líneas,
  quitar una línea individual, folio de venta original opcional, botón
  "Registrar devolución" y el historial de la sesión).

**2. Repositorio local** (POS-46)
- [x] `DevolucionRepository` (interfaz) en `domain/repository/` —
  criterio: compila sin Room ni Retrofit. Verificado: expone solo `suspend
  fun registrarDevolucion(devolucion: Devolucion)` sobre el modelo de
  dominio `Devolucion`/`DevolucionLinea` (`domain/model/Devolucion.kt`).
- [x] `LocalDevolucionRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests` Verificado:
  `./gradlew build` en verde (`BUILD SUCCESSFUL`, incluye lint y
  `testDebugUnitTest`/`testReleaseUnitTest`). `LocalDevolucionRepository.
  registrarDevolucion` inserta `DevolucionEntity` + `DevolucionDetalleEntity`
  (una por línea) en una sola transacción Room (`DevolucionDao.
  insertDevolucionCompleta`, mismo patrón `@Transaction` que `VentaDao.
  insertVentaCompleta`) — a diferencia de Venta/Entrada, **no** genera
  `movimiento` ni toca `inventario` (decisión de alcance de esta Parte, no
  un descuido). `PdvDatabase` sube a versión 5 (agrega `DevolucionEntity`/
  `DevolucionDetalleEntity`); `DatabaseModule` provee `DevolucionDao`.
- [x] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger. Verificado:
  `LocalDevolucionRepositoryTest` (MockK) confirma `appLogger.log(LogType.
  DB_WRITE, ...)` tras un `registrarDevolucion` exitoso.
- [x] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde. Verificado:
  `LocalDevolucionRepositoryTest`, 2 pruebas en verde (happy path: inserta
  devolución+detalle y loguea `DB_WRITE`; error: una falla del DAO se
  propaga sin loguear `DB_WRITE`). `code-reviewer` revisó los 7 archivos de
  este sub-paso (más el wiring de `PdvDatabase`/`DatabaseModule`) sin
  hallazgos — confirmó explícitamente atomicidad de la transacción,
  nulabilidad consistente con `docs/schema-pos.json`, y paridad campo por
  campo entre el esquema aprobado y las entidades Room.

**3. Repositorio remoto** (POS-47)
- [x] `docs/api-contract.md` actualizado con los endpoints de
  `devoluciones` (CLAUDE.md §9) — criterio: sección nueva, revisada.
  Verificado: `docs/api-contract.md` §9 (Devoluciones) documenta
  `POST /devoluciones`, request/response completos, el 422 por `lineas`
  vacío o campos obligatorios faltantes, y el 404 por `venta_id`/
  `articulo_id` inexistente.
- [x] Ruta FastAPI (`app/routers/devoluciones.py`) — criterio: `pytest
  backend/tests/test_devoluciones.py` en verde. Verificado: 5 pruebas en
  verde contra el Postgres real de `docker compose` (happy path con y sin
  `venta_id`, 422 por `lineas` vacío, 404 por `articulo_id` inexistente,
  404 por `venta_id` inexistente); `alembic upgrade head` aplicó
  `0005_create_devoluciones` y `alembic check` confirmó "No new upgrade
  operations detected" — de paso corrigió un gap preexistente de la Parte
  10 (`alembic/env.py` nunca importaba `app.models.caja`, así que
  `cortes_caja`/`retiros_efectivo` no estaban en `Base.metadata` y
  `alembic check` los reportaba como tablas a eliminar). Suite completa del
  backend: 30/30 en verde. Smoke test manual contra el contenedor
  reconstruido confirma el 422 y el 404 con el shape de Pydantic esperado.
- [x] `RemoteDevolucionRepository` (Retrofit) en `data/remote/` —
  criterio: `./gradlew testDebugUnitTest` pasa mockeando Retrofit.
  `jvm-tests` Verificado: `./gradlew build` en verde (`BUILD SUCCESSFUL`,
  incluye lint y ambas suites de tests unitarios). `DevolucionApiService.
  createDevolucion` (POST `/devoluciones`) + DTOs (`DevolucionDto`/
  `DevolucionCreateRequestDto`, cantidad como String); `fecha: Long` se
  convierte a ISO 8601 con `java.time.Instant`. Cableado en
  `NetworkModule.provideDevolucionApiService`. Aún sin `@Binds` a
  `DevolucionRepository` en `RepositoryModule` — se agrega en el sub-paso 4
  (Wiring), mismo orden que Venta/Caja.
- [x] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout. Verificado:
  `RemoteDevolucionRepositoryTest`, 3 pruebas en verde (happy path: mapea
  `Devolucion` al DTO y no loguea nada; `IOException` se loguea como
  `LogType.ERROR` y se repropaga; `HttpException` — 422 — también se
  loguea y repropaga). `code-reviewer` revisó los 9 archivos de este
  sub-paso (backend + Android) sin hallazgos.

**4. Wiring** (POS-48)
- [x] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto. Verificado:
  `./gradlew build` en verde (incluye lint). `ModeAwareDevolucionRepository`
  (mismo criterio que `ModeAwareVentaRepository`: local y
  local-con-sincronización escriben en Room, remoto llama al backend)
  cableado en `RepositoryModule` vía `@Binds`. `DevolucionViewModel` ahora
  inyecta `DevolucionRepository` (interfaz, agnóstica del modo) +
  `ConfiguracionPreferences` (sucursal seleccionada) + `SessionManager`
  (usuario de sesión); `registrarDevolucion()` arma el `Devolucion` de
  dominio (folio `"D-<timestamp>"`, mismo patrón que `"V-<timestamp>"` de
  Venta; `ventaOriginal` vacío se mapea a `ventaId = null`) y llama
  `registrarDevolucion`. `ModeAwareDevolucionRepositoryTest` (1 prueba)
  confirma que cambiar `BackendMode` en DataStore cambia a cuál repositorio
  llega la devolución; `DevolucionViewModelTest` (3 pruebas: happy path,
  falta sucursal/sesión, falla de red) confirma el wiring del ViewModel.
  `code-reviewer` revisó los 5 archivos de este sub-paso sin hallazgos.
- [x] Verificado end-to-end en el Xiaomi en los tres modos (local / local
  con sincronización / remoto) — `needs-device` Confirmado por el usuario
  el 2026-08-20. En el camino aparecieron y se corrigieron dos problemas
  de entorno, ninguno de código nuevo de esta Parte: (1) el modo remoto
  crasheaba la app con `ConnectException` no capturado — causa raíz:
  `adb reverse tcp:8000 tcp:8000` no estaba configurado en la sesión del
  dispositivo, así que `RemoteSucursalRepository.observeSucursales()`
  (Parte 6) no podía alcanzar el backend y la excepción sin capturar
  tumbaba la app (gap de robustez preexistente en Configuración, fuera del
  alcance de esta Parte — documentado aquí, no corregido en código); (2)
  una vez con red, el registro remoto devolvía 404 porque los artículos del
  catálogo estático de `DevolucionScreen` (mismos UUID que el catálogo de
  ejemplo de Venta) nunca existían como fila real en `articulos` del
  backend — se sembró manualmente vía SQL (`INSERT INTO articulos ...` con
  el UUID `11111111-1111-4111-8111-111111111111`, "Refresco de cola
  600ml"), sin cambios de código. Verificado por el agente inspeccionando
  el estado persistido directamente: LOCAL y LOCAL_CON_SINCRONIZACION — 4
  filas en `devoluciones`/`devolucion_detalle` de `pdv.db` del dispositivo
  (`adb exec-out run-as com.pdv.pos cat databases/pdv.db{,-wal,-shm}` —
  hubo que extraer también el WAL sin checkpoint, un `cat` directo del
  `.db` solo mostraba las tablas vacías), `isSynced=0` en las cuatro.
  REMOTO — 1 devolución (folio `D-1787271456769`) con 2 líneas en el
  Postgres del backend (`docker compose exec db psql`), `articulo_id`
  válido contra la fila sembrada.

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

**Adición post-cierre (2026-08-20)** (POS-49): escaneo de código de barras con
cámara en la búsqueda de artículo de `DevolucionScreen`, pedida por el
usuario fuera del checklist original — no era parte de la propuesta de
pantalla aprobada en el sub-paso 1. Reutiliza `BarcodeScannerDialog`
(`android/.../venta/BarcodeScannerDialog.kt`, CameraX + ML Kit) tal cual,
mismo patrón cross-módulo que ya usan Entrada e Inventario; no se
introdujo ninguna dependencia nueva ni lógica duplicada.
`DevolucionViewModel.onEscanearClick`/`onEscanerDismiss`/
`onBarcodeEscaneado` son réplica exacta de sus contrapartes en
`VentaViewModel` (Parte 7). `code-reviewer` revisó el cambio sin
hallazgos; instalado y confirmado por el usuario en el Xiaomi.

---

## Parte 12: Módulo Administración de usuarios (demo)  <!-- POS-50 -->

CRUD de administrador y encargados de turno, y asignación de turnos. Este
CRUD persiste usuarios y turnos de verdad (repositorio local/remoto, como
los demás módulos); lo que se difiere a la Parte 13 es la aplicación real
de permisos por rol sobre los demás módulos.

### Checklist

**1. UI** (POS-51)
- [x] Propuesta de pantalla (CRUD de administrador/encargados de turno,
  asignación de turnos) presentada y aprobada — criterio: aprobación
  explícita registrada antes de implementar. Aprobado el 2026-08-20:
  "asignación de turnos" se interpreta como el campo `rol`
  (administrador/encargado_turno) del propio CRUD de `Usuario` — no hay
  tabla `turnos` separada (no está en el esquema aprobado ni en el
  checklist de Repositorio local de esta Parte); pantalla `UsuarioScreen`
  con lista de usuarios y formulario alta/edición (usuario, nombre
  completo, selector de rol, toggle activo), sin campo de contraseña
  (llega en Parte 13); navegación manual desde `HelloScreen`.
- [x] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable. Verificado: `./gradlew
  build` en verde (`BUILD SUCCESSFUL`, incluye tests y lint); catálogo
  estático de 2 usuarios de ejemplo (`admin`/administrador,
  `user1`/encargado_turno) en `UsuarioViewModel`; alta, edición,
  activar/desactivar y eliminación en memoria; botón "Usuarios" en
  `HelloScreen` navega a `UsuarioScreen`.
- [x] Instalado y verificado en el Xiaomi — `needs-device` Confirmado por
  el usuario el 2026-08-20: pantalla de Usuarios instalada y verificada.

**2. Repositorio local** (POS-52)
- [x] `UsuarioRepository` (interfaz) en `domain/repository/` — criterio:
  compila sin Room ni Retrofit. Verificado: solo importa `domain.model.Usuario`
  y `kotlinx.coroutines.flow.Flow`; `crearUsuario`/`actualizarUsuario`/
  `eliminarUsuario` reciben `sucursalId`/`actorUsuario` aparte (atribución
  del log `DB_WRITE`) porque `usuarios` no es `sucursal_scoped`.
- [x] `LocalUsuarioRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests` Verificado: `BUILD
  SUCCESSFUL`, 5 tests en `LocalUsuarioRepositoryTest` en verde.
  `UsuarioEntity`/`UsuarioDao` agregados a `PdvDatabase` (versión 6,
  `fallbackToDestructiveMigration`, sin migración formal — mismo criterio
  que el resto del proyecto).
- [x] Esquema de `Usuario` reserva el campo de contraseña hasheada que la
  Parte 13 va a usar (aunque todavía no se valida contra él) — criterio:
  campo presente en la entidad Room, sin lógica de login real todavía.
  Verificado: `UsuarioEntity.passwordHash: String?`, siempre `null` al
  crear (sin login real hasta la Parte 13).
- [x] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger. Verificado: las 3
  escrituras (`crearUsuario`/`actualizarUsuario`/`eliminarUsuario`
  soft-delete vía `deletedAt`) logean `DB_WRITE`, cubierto en
  `LocalUsuarioRepositoryTest`.
- [x] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde. Verificado: 5 tests
  (happy path de crear/actualizar/eliminar + 2 casos de error: fallo del
  DAO al crear, usuario inexistente al actualizar).

**3. Repositorio remoto** (POS-53)
- [x] `docs/api-contract.md` actualizado con los endpoints de `usuarios`
  (CLAUDE.md §9) — criterio: sección nueva, revisada. Verificado: sección
  10 "Usuarios" agregada (CRUD completo: listar/crear/editar/eliminar),
  secciones 11-12 renumeradas; sin `sucursal_id` (no es `sucursal_scoped`);
  `password_hash` nunca se expone; ítem correspondiente quitado de la
  sección "Pendiente de definir".
- [x] Ruta FastAPI (`app/routers/usuarios.py`) — criterio: `pytest
  backend/tests/test_usuarios.py` en verde. Verificado: 8 tests en verde
  (`pytest tests/test_usuarios.py`, y la suite completa de 38 tests sigue
  en verde); migración Alembic `0006_create_usuarios` agregada
  (mismo patrón que las Partes anteriores); `rol` restringido a
  `administrador`/`encargado_turno` (`Literal`, 422 si no cumple);
  `username` único (409 en duplicado, alta o edición); `DELETE` hace
  soft-delete (`deleted_at`), no borra la fila.
- [x] `RemoteUsuarioRepository` (Retrofit) — criterio: `./gradlew
  testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests` Verificado:
  `BUILD SUCCESSFUL`, 4 tests en `RemoteUsuarioRepositoryTest` en verde.
- [x] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout. Verificado:
  `crearUsuario logs ERROR and rethrows on a timeout` simula
  `SocketTimeoutException`; `eliminarUsuario` cubre `IOException`.

**4. Wiring** (POS-54)
- [x] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto. Verificado:
  `ModeAwareUsuarioRepository` (mismo criterio que
  `ModeAwareSucursalRepository`/`ModeAwareDevolucionRepository`) resuelve
  local/remota según `BackendMode` en DataStore, cubierto por
  `ModeAwareUsuarioRepositoryTest` (2 tests: lectura reactiva y escritura);
  `UsuarioViewModel` conectado a `UsuarioRepository` real (ya no el
  catálogo estático del sub-paso 1), cubierto por `UsuarioViewModelTest`
  (4 tests: crear/editar/eliminar vía repositorio, falta sucursal/sesión).
  `./gradlew build` en verde. Migración `0006_create_usuarios` aplicada
  contra el Postgres de `docker compose` (`alembic stamp head` — la tabla
  ya existía por `Base.metadata.create_all` de la suite de pytest corrida
  antes en la misma base; `alembic check` confirmó "No new upgrade
  operations detected"); imagen del backend reconstruida
  (`scripts/start-windows.ps1`) para incluir la ruta `usuarios` en modo
  REMOTO.
- [x] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`
  Confirmado por el usuario el 2026-08-21: instalado y probado exitosamente
  en los tres modos (Local, Remoto, Local con sincronización). Primer
  intento en REMOTO falló: el túnel `adb reverse tcp:8000 tcp:8000` no
  estaba activo (se pierde al reconectar el cable/reiniciar adb, no es
  persistente entre sesiones) — diagnosticado inspeccionando logs del
  contenedor backend (cero requests a `/usuarios`) y el log de la app en
  el dispositivo (sin entradas `ERROR` de red para Usuarios, solo
  `DB_WRITE` de una prueba local anterior). Corregido rehaciendo el túnel;
  reintento confirmó los tres modos funcionando en todos los módulos.
  A raíz de esto se agregaron `scripts/install-apk-tuneling.ps1` (
  `installDebug` + `adb reverse`) y `scripts/stop-apk-services.ps1` (quita
  el túnel), documentados en `CLAUDE.md` sec. 3 como tooling de depuración
  ajeno al alcance de esta Parte.

Realiza pruebas de integración exhaustivas.

---

## Parte 13: Gestión de usuarios (real)  <!-- POS-55 -->

Agrega al proyecto la funcionalidad completa de multiusuarios: administración
de usuarios, roles personalizados (Administrador, encargado de turno, y
roles adicionales configurables), permisos por rol, y módulos
activados/desactivados según esos permisos. Esta parte se adelanta respecto
al orden original del proyecto para que exista un sistema de permisos real
antes de dar autonomía de escritura a la IA (Partes 14-16).

**Reemplazo del login ficticio**: esta Parte reemplaza por completo el
login hardcodeado de la Parte 4. Se agrega un campo de contraseña hasheada
al esquema de `Usuario` (Parte 3, biblioteca de hashing a definir junto con
el detalle campo-por-campo), se implementa `POST /auth/login` real contra
los registros que `UsuarioRepository` (Parte 12) ya persiste, y se emite
`access_token`. Las credenciales `admin`/`password` y `user1`/`password`
dejan de aceptarse a partir de aquí — coincide con lo ya declarado en
`docs/api-contract.md` §2.

Las acciones rechazadas por falta de permiso se registran en el log
(Parte 5, categoría `AUTH`).

### Checklist

**1. Roles y permisos** (POS-56)
- [x] Roles personalizados (Administrador, encargado de turno, y
  adicionales configurables) implementados — criterio: CRUD de roles con
  `pytest backend/tests/test_roles.py` y prueba Android en verde.
  Verificado: tabla `roles` nueva (`nombre`, `modulos_permitidos`,
  `es_sistema`) + migraciones `0007_create_roles` (crea tabla y seedea los
  2 roles de sistema) y `0008_usuarios_rol_id` (agrega `usuarios.rol_id`
  FK, backfill desde el `rol` literal, borra la columna vieja);
  `pytest` 48/48 en verde. Lado Android: `RolRepository`/
  `LocalRolRepository`/`RemoteRolRepository`/`ModeAwareRolRepository`
  calcan el patrón ya usado 6 veces en el proyecto; pantalla `RolScreen`
  (CRUD con checkboxes de módulo) navegable desde `UsuarioScreen`; roles de
  sistema protegidos de edición/eliminación en ambos lados (400 en backend,
  `IllegalStateException` en `LocalRolRepository`). `./gradlew build` en
  verde (`BUILD SUCCESSFUL`, incluye tests y lint).
- [x] Permisos por rol y activación/desactivación de módulos según esos
  permisos — criterio: prueba de integración confirma que un usuario sin
  permiso no ve/usa el módulo restringido. Verificado:
  `PermisosModuloIntegrationTest` confirma, con `LocalUsuarioRepository` y
  `LocalRolRepository` reales, que un usuario con rol "encargado_turno" no
  obtiene los módulos `usuarios`/`configuracion`. El wiring de UI
  (`HelloScreen` filtrando botones contra la sesión real) se difiere al
  sub-paso 2: hoy `SessionManager` sigue siendo el login ficticio de la
  Parte 4 sin `Usuario` real garantizado, y forzar ese wiring ahora exigiría
  un lookup username→Usuario frágil que se descartaría en cuanto el login
  real (sub-paso 2) traiga el `Usuario`/rol directamente en la sesión — sin
  riesgo de seguridad mientras tanto, porque hoy ningún punto de la UI
  aplica el permiso todavía (deferral validado por `code-reviewer` en la
  revisión de este sub-paso).

**2. Reemplazo del login ficticio** (POS-57)
- [x] Campo de contraseña hasheada agregado al esquema de `Usuario` —
  criterio: migración de Alembic aplicada, campo presente. Verificado: ya
  estaba (migración `0006_create_usuarios`, Parte 12); esta Parte lo usa
  por primera vez.
- [x] `POST /auth/login` real, documentado primero en
  `docs/api-contract.md` (CLAUDE.md §9) — criterio: `pytest
  backend/tests/test_auth.py` en verde, valida credenciales contra
  `UsuarioRepository` y emite `access_token`. Verificado: `docs/api-contract.md`
  §2 reescrita antes de implementar; `backend/app/routers/auth.py` valida
  usuario activo + `bcrypt.checkpw` (backend/app/security.py) y emite un
  JWT (`PyJWT`, `HS256`, 24h) vía `create_access_token`; `pytest` 54/54 en
  verde. Bootstrap: migración `0009_seed_admin_usuario` siembra
  `admin`/`admin123` (documentado, contraseña de desarrollo) porque un
  backend recién levantado no tendría ningún usuario con el que iniciar
  sesión.
- [x] Credenciales ficticias (`admin`/`password`, `user1`/`password`)
  dejan de aceptarse — criterio: prueba de integración confirma rechazo.
  Verificado: `test_login_credenciales_ficticias_de_la_parte_4_returns_401`
  en `backend/tests/test_auth.py`.
- [x] Cliente Android usa el login real en vez del hardcodeado de la
  Parte 4 — criterio: prueba de `ViewModel` con estados mockeados.
  Verificado: `AuthRepository` (interfaz única, patrón ModeAware — Decisión
  1) con `LocalAuthRepository` (verifica bcrypt contra Room, con el mismo
  bootstrap `admin`/`admin123` sembrado perezosamente para que un
  dispositivo LOCAL nuevo tampoco quede sin punto de entrada) y
  `RemoteAuthRepository` (POST `/auth/login`); hash bcrypt (cost 12)
  calculado siempre en Android vía `PasswordHasher`, `suspend` sobre
  `Dispatchers.Default` (hallazgo de `code-reviewer`: bcrypt es
  deliberadamente lento, no debe bloquear el hilo principal — corregido).
  `SessionManager` reemplazado por completo (ya no valida nada, solo
  contiene la `Session` que arma `AuthRepository`); `LoginViewModel` y
  `LoginViewModelTest` en verde. `UsuarioScreen` gana un campo de
  contraseña (opcional; en blanco = no cambiar la existente al editar).
  `./gradlew build`: `BUILD SUCCESSFUL`, todos los tests y lint en verde.

**3. Logging** (POS-58)
- [x] Acciones rechazadas por falta de permiso se registran en el log
  (Parte 5, categoría `AUTH`) — criterio: prueba unitaria verifica la
  invocación al logger con la categoría correcta. Verificado:
  `HelloViewModel.onIntentoNavegar(modulo)` es la segunda compuerta además
  de ocultar el botón — mismo punto de verificación que usarán las Partes
  14-16 antes de que la IA ejecute una acción en nombre del usuario, con o
  sin botón visible. Un intento denegado registra `LogType.AUTH` con
  `sucursalId`/`usuario`/`mensaje` (`sucursalId = "-"` si el dispositivo
  todavía no tiene sucursal seleccionada — hallazgo de `code-reviewer`: sin
  este fallback la denegación se perdía en silencio, ahora cubierto por
  test). `HelloScreen` conecta cada botón de módulo a través de esta
  compuerta. `./gradlew build`: `BUILD SUCCESSFUL`, todos los tests y lint
  en verde.

**4. Verificación** (POS-59)
- [x] Instalado y verificado end-to-end en el Xiaomi (login real,
  permisos aplicados) — `needs-device`. Confirmado por el usuario el
  2026-08-22: login real, permisos por rol, CRUD de roles y contraseña al
  editar/crear probados exitosamente. Primera pasada encontró un hueco de
  UX (la contraseña quedaba opcional también al crear un usuario, no solo
  al editar) — corregido (`UsuarioViewModel` exige contraseña no vacía al
  crear; al editar, vacío sigue significando "no cambiar la existente") y
  reverificado en el dispositivo, confirmado el 2026-08-22.

### Decisiones abiertas

- [x] Autenticación en modo local: no existe backend que emita
  `access_token`, así que el login debe validar contra Room usando la
  contraseña hasheada. Definir si es un segundo camino de autenticación
  explícito o una implementación local de la misma interfaz de sesión.
  **Decidido** (evaluado por `code-architect`, elegido por el usuario):
  interfaz única `AuthRepository`, mismo patrón ModeAware que el resto del
  proyecto (`LocalAuthRepository`/`RemoteAuthRepository`/
  `ModeAwareAuthRepository`).
- [x] Librería de hashing de contraseñas y dónde se calcula el hash: solo
  backend, solo dispositivo, o ambos con el mismo algoritmo para que un
  usuario creado en modo local pueda sincronizarse al remoto sin
  reescribir la credencial. **Decidido**: bcrypt (cost factor 12),
  calculado siempre en el dispositivo Android (`at.favre.lib:bcrypt`) del
  lado que recibe el texto plano; el backend (`bcrypt` de Python) solo
  verifica en `POST /auth/login`, nunca genera hashes de usuarios reales.

### Nota futura: recuperación de contraseña por correo (sin implementar)

Idea planteada 2026-08-22, no forma parte del checklist de esta Parte: agregar
`email` a `Usuario` para poder enviar una contraseña aleatoria al correo
registrado cuando el usuario la olvida. Puntos identificados si se retoma:

- Esquema: campo chico, mismo patrón que `rol_id` (migración, entidad Room,
  DTOs, formulario) — no es lo costoso.
- Requiere infraestructura de envío de correo que el proyecto no tiene hoy
  (SMTP o proveedor tipo SendGrid/SES, credenciales nuevas) — **solo
  funcionaría en modo REMOTO**; en modo LOCAL no hay forma segura de
  disparar un correo desde el dispositivo.
- El endpoint de recuperación tendría que generar y hashear la contraseña
  en el backend para poder enviarla — es una excepción a la Decisión 2 de
  esta Parte ("el hash se calcula siempre en Android"), habría que
  documentarla explícitamente si se implementa.
- Expone un hueco ya existente: hoy nadie puede cambiar su propia
  contraseña sin el permiso de módulo `usuarios` (`UsuarioScreen` está
  detrás de ese permiso) — recuperar la contraseña sin poder después
  cambiarla manualmente requeriría una pantalla nueva de "cambiar mi
  contraseña" fuera de ese permiso.
- Vale considerar un límite básico de abuso en el endpoint (no es
  "programar a la defensiva" sin sentido — un disparador de envío de
  correo sin límite es un vector de spam/costo real).

---

## Parte 14: Conectividad de IA multi-proveedor  <!-- POS-60 -->

Permite que la app se conecte a un proveedor de IA a elección del usuario
(DeepSeek, OpenAI u OpenRouter) y agrega a Configuración el módulo donde se
activa/desactiva la IA, se elige el proveedor y se introduce el API token.

**Decisión de arquitectura (confirmada con el usuario, 2026-08-22)**: la
llamada al LLM se hace siempre directo desde Android al proveedor, nunca a
través del backend FastAPI. Las tres APIs (DeepSeek, OpenAI, OpenRouter) son
compatibles con el formato "chat completions" de OpenAI, así que es un solo
cliente HTTP parametrizado por `baseUrl`/`apiKey`/`modelo` — mismo espíritu
que el patrón `ModeAware*Repository` ya usado en el proyecto, pero
seleccionando proveedor en vez de modo. Esto resuelve de forma natural el
modo LOCAL+internet de la Parte 16 (no depende de que haya backend
configurado) y evita que el token del usuario viaje por nuestro backend: solo
viaja hacia el proveedor, por HTTPS. El backend no gana ninguna ruta de IA;
su único cambio relacionado es agregar la clave de módulo `"ia"` al catálogo
de permisos ya existente (sub-paso 3).

### Checklist

**1. Cliente LLM multi-proveedor** (POS-61)
- [x] Abstracción `LlmProvider` (`ia/` nuevo paquete Android): enum
  `DeepSeek`/`OpenAi`/`OpenRouter` con `baseUrl` y modelo por defecto
  (`deepseek-chat`, `gpt-4o-mini`; OpenRouter usa un campo `modelo` de texto
  libre por agrupar múltiples modelos bajo un mismo endpoint) — criterio:
  compila sin duplicar el cliente HTTP por proveedor.
- [x] Prueba de conectividad "2+2" contra los tres proveedores, parametrizada
  (no triplicada) — criterio: `./gradlew testDebugUnitTest` en verde contra
  un servidor HTTP de prueba (MockWebServer/interceptor de test).
- [x] Manejo de error si el proveedor no está disponible, el token es
  inválido, o no hay conexión — criterio: prueba unitaria de cada caso
  (CLAUDE.md §6), nunca crashea.
- [x] `OkHttpClient` dedicado para este servicio, sin `HttpLoggingInterceptor`
  (o en `Level.NONE`) — criterio: revisión de código confirma que el header
  `Authorization` con el token nunca llega a Logcat.

**2. UI de Configuración — sección "Asistente de IA"** (POS-62)
- [x] Propuesta de pantalla (switch activo/inactivo, selector de proveedor,
  campo de modelo para OpenRouter, campo de token enmascarado) presentada y
  aprobada — criterio: aprobación explícita registrada antes de implementar.
  `needs-approval` (aprobada 2026-08-22; se agregó ademas un boton "Probar
  conexion" fuera del checklist original, tambien aprobado explicitamente)
- [x] Campo de token nunca muestra el valor guardado — al reabrir la
  pantalla aparece vacío con un indicador "token configurado" si ya hay uno;
  escribir un valor nuevo lo reemplaza, sin acción de "ver en claro" —
  criterio: revisión de código confirma que el valor descifrado nunca se
  asigna a un campo de texto visible.
- [x] Instalado y verificado en el Xiaomi — `needs-device` (confirmado por
  el usuario 2026-08-22)

Post-aprobación (mismo sub-paso, pedido explícito del usuario tras probar en
el Xiaomi): el campo de modelo se extendió de "solo texto libre en
OpenRouter" a un combo editable disponible para los 3 proveedores
(`LlmProvider.modelosSugeridos`, sugerencias filtradas a medida que se
escribe, con el modelo por defecto de cada proveedor como primera opción) —
para poder elegir un modelo distinto y economizar tokens o mejorar el
razonamiento. También se corrigió un bug preexistente (no introducido por
esta Parte, pero expuesto por el crecimiento de la pantalla): el `Column`
raíz de `ConfiguracionScreen` no tenía scroll, así que el contenido se
cortaba en pantallas más chicas — se agregó `.verticalScroll(...)`, mismo
patrón que ya usan las otras 6 pantallas del proyecto.

**3. Cifrado en reposo y permisos** (POS-63)
- [x] Token cifrado con clave AES-GCM respaldada por Android Keystore
  (`android.security.keystore`, API nativa) antes de guardarse en DataStore
  (ciphertext + IV, nunca texto plano) — criterio: prueba unitaria confirma
  que el valor persistido no es el texto plano. Implementado como
  `TokenCipher` (interfaz) + `AndroidKeystoreTokenCipher` (impl real,
  `config/`): AndroidKeyStore no es testeable en JVM, asi que
  `IaPreferencesTest` inyecta un `FakeTokenCipher` para probar el contrato
  de `IaPreferences` (nunca persiste texto plano, descifra al valor
  original). `AndroidKeystoreTokenCipher` en si se ejercita end-to-end via
  el flujo de Guardar/Probar conexion ya verificado en el Xiaomi
  (sub-paso 2).
- [x] `"ia"` agregado a `Modulo` (`backend/app/schemas/rol.py`) y a
  `MODULOS_DISPONIBLES`/`RolesDeSistemaSeed` (Android) — criterio: `pytest`
  y `./gradlew testDebugUnitTest` en verde. `schema-parity`
- [x] Migración nueva que agrega `"ia"` a `modulos_permitidos` de los dos
  roles de sistema ya sembrados (`administrador` y `encargado_turno`) —
  criterio: `alembic upgrade head` deja ambos roles con `"ia"` incluido.
  Migración `0010_add_ia_modulo_roles`, verificada con upgrade/downgrade/
  upgrade contra la base del contenedor.
- [x] `docs/api-contract.md` §10 actualizado con la clave `"ia"` — criterio:
  revisión antes de cerrar este sub-paso (CLAUDE.md §9). `docs/schema-pos.json`
  tambien actualizado (mismo catalogo de claves referenciado ahi).

### Decisiones abiertas

- [x] ¿El rol de sistema `encargado_turno` debe incluir `"ia"` por defecto,
  o debe quedar reservado a roles que el administrador habilite
  explícitamente? **Decidido** (2026-08-22): sí, `encargado_turno` incluye
  `"ia"` por defecto — ya tiene venta/entrada/inventario/caja/devoluciones,
  así que la IA no le da ningún permiso de acción que no tuviera ya.

---

## Parte 15: Refinamiento de IA  <!-- POS-64 -->

Define el estado del punto de venta que se envía a la IA como contexto, el
esquema de salida estructurada (respuesta al usuario + lista de acciones
propuestas), y la validación de permisos antes de ejecutar cualquier acción.

**Validación de permisos (obligatoria)**: cada acción propuesta por la IA se
valida contra los permisos del usuario en turno (Parte 13) antes de
ejecutarse; una acción rechazada no bloquea al resto de la respuesta. Las
acciones rechazadas se registran en el log (Parte 5, categoría `AUTH`) y se
informan al usuario en el chat.

**Ejecución sin camino paralelo**: cada acción se traduce a una llamada al
mismo caso de uso/repositorio que ya usa la pantalla manual de ese módulo
(ej. una acción de corte parcial llama exactamente lo mismo que usa
`CajaViewModel` al confirmar un corte parcial desde la UI) — mismo
`DB_WRITE`, mismo motor de sync que cualquier acción manual, per la decisión
de arquitectura de la Parte 14 (todo ocurre en Android, vía los
repositorios `ModeAware*` existentes).

**Confirmación explícita**: antes de ejecutar cualquier acción con efecto en
datos de negocio, el chat muestra una tarjeta de confirmación (ej. "¿Confirmas
retiro de $500 en corte parcial?"); el detalle visual se propone y aprueba en
la Parte 16 (sub-paso de UI).

### Checklist

**1. Estado del punto de venta** (POS-65)
- [x] Función que arma el JSON de contexto (catálogo/inventario resumido,
  sucursal y caja actual, historial corto de conversación) reutilizando las
  lecturas ya existentes de cada `ModeAwareXRepository` — criterio: prueba
  unitaria verifica el shape del JSON. Verificado: `EstadoPuntoVentaBuilder`
  (`android/app/src/main/java/com/pdv/pos/ia/EstadoPuntoVenta.kt`) arma
  `EstadoPuntoVenta` (sucursal, resumen de caja del día vía
  `CajaRepository.calcularTotales`, hasta 50 artículos de
  `InventarioRepository.observarInventario` ordenados por nombre,
  historial de `ChatMessageDto` reutilizado de la Parte 14) y lo serializa
  a JSON con el `Json` singleton ya provisto por Hilt. Sin caminos de
  escritura ni validaciones nuevas (revisión de `code-reviewer`, sin
  hallazgos). `EstadoPuntoVentaBuilderTest` (3 casos: shape completo,
  artículo sin costo/ubicación, sucursal no encontrada) en verde;
  `./gradlew build`: `BUILD SUCCESSFUL`, todos los tests y lint en verde.

**2. Esquema de salida estructurada** (POS-66)
- [x] Esquema único `{ respuesta_usuario, acciones: [{ modulo, tipo,
  parametros }] }`, usando el modo de salida estructurada/tool-calling de
  cada proveedor — criterio: prueba unitaria valida un ejemplo de cada uno
  de los 3 tipos de acción pedidos por el usuario (alta a inventario +
  ajuste de costo, corte parcial con retiro, devolución). La sincronización
  manual queda fuera del alcance de la IA en esta Parte — ver Decisiones
  abiertas. Verificado: `RespuestaIaDto`/`AccionIaDto`
  (`android/app/src/main/java/com/pdv/pos/ia/RespuestaIa.kt`, `parametros`
  como `JsonObject` sin tipar — el mapeo a los modelos de dominio reales es
  del sub-paso 3). `LlmClient.chatEstructurado` (Parte 14) usa
  `response_format: {"type": "json_object"}` — el modo soportado en común
  por los 3 proveedores, a diferencia del `json_schema` estricto
  específico de OpenAI, que OpenRouter no puede garantizar en todos los
  modelos que enruta; se agregó `ResponseFormatDto` a
  `ChatCompletionRequestDto` (campo opcional, no rompe `chat()`/
  `probarConectividad()` existentes). `LlmClientTest` cubre los 3 tipos de
  acción de ejemplo más el caso de JSON inválido devuelto por el proveedor.
  Revisión de `code-reviewer`: sin hallazgos. `./gradlew build`: `BUILD
  SUCCESSFUL`, todos los tests y lint en verde.
- [x] **Prompt de sistema / rol de la IA presentado como texto completo y
  aprobado explícitamente antes de activarse de forma operativa** — ver
  Decisiones abiertas (bloqueante, `needs-approval`). Aprobado 2026-08-22,
  texto completo en Decisiones abiertas. Pendiente de implementación
  (constante inicial + editable en Configuración, ver Decisión 3).

**3. Validación de permisos y ejecución** (POS-67)
- [x] Cada acción se valida contra los permisos reales del usuario en turno
  antes de ejecutarse — criterio: prueba de integración con un usuario sin
  permiso confirma el rechazo selectivo (una acción se ejecuta, otra en la
  misma respuesta se rechaza). Verificado: `EjecutorAccionesIa`
  (`android/app/src/main/java/com/pdv/pos/ia/EjecutorAccionesIa.kt`) valida
  cada acción de forma independiente (Decisión 2) contra
  `modulosPermitidos`. El módulo que autoriza se deriva siempre de
  `accion.tipo` vía un mapeo fijo en código
  (`moduloRequeridoPorTipo`), nunca de `accion.modulo` — ambos campos
  vienen de la misma fuente no confiable (el LLM) y nada los mantiene
  consistentes entre sí; confiar en `modulo` habría permitido que una
  respuesta con los campos cruzados salteara el permiso real (hallazgo
  crítico de `code-reviewer`, corregido y cubierto por test).
  `EjecutorAccionesIaIntegrationTest` (11 casos): rechazo selectivo con
  `AppLogger` real, el caso de módulo/tipo cruzados, y paridad de campos
  contra las pantallas manuales para los 3 tipos de acción restantes
  (`alta_articulo`, `retiro_efectivo`, `registrar_devolucion`).
- [x] Acciones rechazadas se registran `AUTH` (Parte 5) y se informan en el
  chat — criterio: prueba verifica ambas cosas. Verificado: log `AUTH` con
  `sucursalId`/`usuario`/mensaje (mismo patrón que
  `HelloViewModel.onIntentoNavegar`, Parte 13) y `ResultadoAccionIa.
  RechazadaPorPermiso.mensaje` para mostrar en el chat — ambos verificados
  en el mismo test de rechazo selectivo.
- [x] Cada acción ejecutada pasa por el caso de uso real del módulo
  correspondiente, sin camino de escritura aparte — criterio: revisión de
  código explícita al cierre de este sub-paso. Verificado: `alta_articulo`
  → `EntradaRepository.registrarEntrada` (`Entrada.DeArticuloNuevo`, cubre
  alta con costo inicial opcional — la Decisión 1 pre-descope de "alta +
  ajuste de costo" se resuelve como un solo tipo de acción, el costo es un
  parámetro de la alta, no una edición aparte de un artículo existente);
  `corte_parcial` → `CajaRepository.calcularTotales` +
  `guardarCorte` (mismos totales reales que `CajaViewModel`, la IA nunca
  inventa montos); `retiro_efectivo` → `RetiroEfectivoRepository.
  registrarRetiro`; `registrar_devolucion` → `DevolucionRepository.
  registrarDevolucion` (folio/estado con el mismo formato que
  `DevolucionViewModel`). Validación de `cantidad`/`monto` > 0 antes de
  escribir, igual que las 3 pantallas manuales (segundo hallazgo de
  `code-reviewer`, corregido). Dos rondas de revisión de `code-reviewer`
  (hallazgos corregidos + verificación de que las correcciones no
  introdujeron regresiones); sin hallazgos pendientes.
  `./gradlew build`: `BUILD SUCCESSFUL`, todos los tests y lint en verde.

### Decisiones abiertas

- [x] Nombre/alcance del `modulo` sintético de sincronización manual y qué
  permiso exige. **Decidido** (2026-08-22): se descarta por completo — la
  IA no obtiene un tipo de acción de sincronización en esta Parte. La
  sincronización sigue funcionando exclusivamente por el mecanismo
  diferido/manual ya existente (motor `LastWriteWinsSyncEngine`,
  Parte 6), sin un punto de entrada nuevo disparable por la IA. Motivo
  encontrado durante la evaluación (`code-architect`): no existe hoy ningún
  orquestador "sincronizar todo" que reusar (`LastWriteWinsSyncEngine`
  expone solo un método genérico por-entidad) — crearlo únicamente para que
  la IA lo dispare violaría el principio "sin camino paralelo" de esta
  misma Parte. Los 3 tipos de acción de ejemplo del checklist quedan:
  alta a inventario + ajuste de costo, corte parcial con retiro,
  devolución.
- [x] Si una respuesta con múltiples acciones se ejecuta todo-o-nada o
  parcialmente. **Decidido** (2026-08-22, evaluado por `code-architect`,
  elegido por el usuario): independiente por acción — cada acción se
  confirma/ejecuta o se descarta por separado, sin afectar a las demás de
  la misma respuesta. Coincide con que las acciones no comparten
  transacción de dominio en el código existente (repositorios distintos,
  sin wrapper transaccional común) y con el comportamiento que tendría el
  usuario haciendo cada acción manualmente en pantallas separadas.
- [x] **Prompt de sistema / rol de la IA — bloqueante**: antes de activar
  esta Parte de forma operativa en la app (build instalado con datos
  reales), el usuario debe poder revisar y modificar el texto exacto del
  prompt de sistema. **Decidido** (2026-08-22): editable desde
  Configuración como dato de dispositivo, mismo patrón DataStore que
  `IaPreferences` (Parte 14). Guardar un cambio al prompt exige reingresar
  la contraseña del usuario administrador de la sesión activa (paso
  adicional de reautenticación, verificado vía `AuthRepository.login`
  existente — sin endpoint ni camino de escritura nuevo) antes de
  persistir el nuevo texto — mitiga el riesgo señalado por
  `code-architect` de que un dispositivo desatendido y ya logueado permita
  alterar los límites de la IA sin pasar por este control. Texto inicial
  aprobado por el usuario el 2026-08-22 (este es el valor por defecto que
  vive en código y se copia a DataStore la primera vez; editable desde
  Configuración a partir de ahí, bajo el gate de reautenticación descrito
  arriba):

  ```
  Sos el asistente de IA del punto de venta PDV. Ayudás al personal de la
  sucursal a consultar información del negocio (inventario, ventas, caja,
  devoluciones) y, cuando el usuario lo pide explícitamente, a preparar
  acciones concretas sobre esos datos.

  Contexto: en cada mensaje recibís un JSON con el estado actual del punto
  de venta (catálogo/inventario resumido, sucursal y caja abiertos,
  historial corto de la conversación). Usá únicamente esos datos — nunca
  inventes artículos, precios, montos o folios que no estén en el
  contexto.

  Tono: profesional, directo y breve. Respondé siempre en español, sin
  emojis.

  Acciones que podés proponer (y solo esas tres):
  1. Alta de un artículo al inventario, opcionalmente con ajuste de costo.
  2. Corte de caja parcial, opcionalmente con retiro de efectivo.
  3. Registro de una devolución.

  Reglas para proponer acciones:
  - Nunca ejecutás una acción vos mismo: solo la proponés en el campo
    `acciones` de tu respuesta. El sistema le pide confirmación explícita
    al usuario antes de aplicar cualquier acción, y valida que el usuario
    tenga permiso para el módulo correspondiente — vos no evaluás
    permisos.
  - Proponé una acción solo si el usuario la pidió explícita e
    inequívocamente (ej. "dá de alta 10 unidades de tornillos a $50" o
    "registrá una devolución del artículo X"). No propongas acciones a
    partir de una simple consulta o de una conversación ambigua.
  - Si falta un dato obligatorio para armar la acción (cantidad, costo,
    motivo, artículo), preguntalo antes de proponer la acción en vez de
    adivinar o completar con un valor por defecto.
  - Podés proponer varias acciones en una misma respuesta si el usuario
    las pidió juntas; cada una se confirma y ejecuta de forma
    independiente (rechazar una no cancela las demás).
  - El campo `respuesta_usuario` siempre debe tener sentido por sí solo,
    incluso si el usuario no acepta ninguna acción propuesta: explicá en
    texto plano qué entendiste y qué proponés.

  Límites:
  - No das consejos legales, fiscales o contables más allá de lo que el
    contexto del punto de venta permite verificar.
  - No revelás ni repetís tokens, contraseñas, ni datos de configuración
    del sistema.
  - Si te piden algo fuera de estos tres tipos de acción o fuera del
    alcance del punto de venta, explicá que no podés hacerlo desde el chat
    todavía.
  ```

---

## Parte 16: Chat IA y comandos de voz  <!-- POS-68 -->

Agrega un widget flotante de chat con IA, con un botón de micrófono para
comandos de voz (`android.speech.SpeechRecognizer` nativo — sin costo, sin
dependencia nueva, sin API key adicional). La voz es solo otra forma de
llenar el mismo campo de texto del chat; no es un subsistema aparte del
pipeline de la Parte 15.

### Comportamiento por modo y conectividad

- **Cualquier modo (remoto, local-con-sincronización, o local), con
  conexión**: chat completo disponible, incluyendo acciones sobre el punto
  de venta (sujetas a validación de permisos de la Parte 15). **Revisado
  2026-08-22** (ver Decisiones abiertas): la redacción original restringía
  el modo local a solo responder dudas con el FAQ, sin poder tocar la base
  de datos local; se decidió permitir acciones también en este modo — el
  administrador acepta el riesgo al activar la IA (Parte 14) y al poder
  modificar el prompt de sistema (ver Decisiones abiertas de esta Parte).
  Modo local ya permite escritura sin restricción para toda acción manual
  de los demás módulos (Partes 6-13); la IA deja de ser una excepción.
- **Sin conexión a internet** (cualquier modo): el chat con IA no está
  disponible; se muestra el FAQ estático empaquetado como recurso de ayuda.
  Detección reactiva (ver Decisiones abiertas): sin chequeo proactivo de
  conectividad — se intenta la llamada real al proveedor y, si falla por
  `IOException` (ya distinguido en `LlmClient`, Parte 14), el widget cae al
  FAQ en vez de mostrar el error genérico.

Con la decisión de arquitectura de la Parte 14 (Android llama directo al
proveedor), no hay contradicción entre "modo LOCAL sin backend" y "chat
disponible con conexión a internet": el proveedor se llama igual en los tres
casos, sin depender de que haya backend configurado.

### FAQ empaquetado

Se genera durante el desarrollo y se empaqueta como recurso de la app
(disponible sin conexión). Sirve como contenido de ayuda estático y como
contexto para la IA en modo local con conexión. La Parte 20 reemplaza este
`res/raw/faq.md` de texto libre por un FAQ curado en `res/raw/faq.jsonl`
consultable por número.

### Checklist

**1. Widget de chat** (POS-69)
- [x] Widget flotante implementado, accesible desde cualquier pantalla —
  criterio: Compose UI Test confirma que aparece y funciona. `AsistenteIaWidget`
  (`ia/AsistenteIaWidget.kt`, `FloatingActionButton` + `ModalBottomSheet`) se
  monta una sola vez en `MainActivity.kt`, fuera del `when(pantalla)` de
  navegación manual (dentro de un `Box` nuevo que envuelve las 9 pantallas
  existentes), así queda accesible sin agregarlo a cada pantalla. Sin
  instrumented Compose UI Test (ningún módulo del proyecto los tiene hasta
  ahora — CLAUDE.md §6 pide testear el ViewModel, no detalles de Compose):
  verificado con `ChatViewModelTest` (8 pruebas) a nivel de `ChatViewModel` +
  confirmación visual en el Xiaomi diferida al sub-paso 5 (`needs-device`).
  `./gradlew build`: `BUILD SUCCESSFUL`.
- [x] Widget oculto/deshabilitado si el usuario en turno no tiene el permiso
  de módulo `"ia"` (Parte 14) — criterio: prueba de integración. Esta es la
  primera compuerta, antes incluso de la validación por acción de la
  Parte 15. Verificado: mismo patrón que
  `HelloViewModel.observarModulosPermitidos` (Parte 13) —
  `ChatViewModel` observa `SessionManager.session` +
  `RolRepository.observeRoles()` y expone `visible` en `ChatUiState`.
  `ChatViewModelTest` cubre visible/oculto según el rol de la sesión.
- [x] Tarjeta de confirmación de acciones con efecto en datos de negocio,
  propuesta y aprobada antes de implementar — criterio: aprobación explícita
  registrada. `needs-approval` Aprobado el 2026-08-22: `ModalBottomSheet`
  con lista de mensajes, tarjeta de confirmación inline por acción propuesta
  (Confirmar/Rechazar independientes, Decisión 2 de la Parte 15), campo de
  texto + botón de micrófono, botón de ayuda/FAQ en el header, y vista de
  solo-FAQ cuando no hay conexión.
- [x] La UI se actualiza automáticamente cuando la IA modifica el punto de
  venta — criterio: prueba de integración confirma el refresco del estado
  tras una acción confirmada. Verificado: `confirmarAccion` llama a
  `EjecutorAccionesIa.ejecutar`, que a su vez llama a los mismos
  repositorios `ModeAware*` (Room/Retrofit reales) que las pantallas
  manuales de cada módulo — el refresco reactivo ya existe en esas
  pantallas (Flow de Room observado con `collectAsState`, Partes 6-11), sin
  código nuevo. `ChatViewModelTest` confirma que `confirmarAccion` invoca al
  ejecutor real y marca la tarjeta como resuelta con el resultado.
- [x] Prompt de sistema editable desde Configuración (mismo patrón
  DataStore que `IaPreferences`, Parte 14), con reautenticación del
  administrador de la sesión activa antes de persistir un cambio
  (`AuthRepository.login` existente, sin endpoint ni camino de escritura
  nuevo) — cierra el gate bloqueante dejado pendiente en la Parte 15,
  Decisión 3 (ítem agregado tras confirmar el alcance de esta Parte con el
  usuario, ver Decisiones abiertas). Verificado: `PromptIaPreferences`
  (`config/`) + sección "Prompt de sistema de la IA" en `ConfiguracionScreen`
  (visible cuando la IA está activa) + `ConfiguracionViewModel.onGuardarPromptIa`,
  que llama a `AuthRepository.login(username, password)` antes de persistir
  — contraseña incorrecta no guarda el cambio y muestra
  "Contraseña incorrecta, cambio no guardado.". Valor por defecto:
  `PROMPT_SISTEMA_DEFAULT` (`ia/PromptSistema.kt`) con el texto exacto
  aprobado el 2026-08-22 (Parte 15). `PromptIaPreferencesTest` (2 pruebas) +
  `ConfiguracionViewModelTest` (2 pruebas nuevas: guardado exitoso
  reautenticado, rechazo por contraseña incorrecta) en verde.
  `code-reviewer` revisó el flujo completo del sub-paso 1 y confirmó que no
  hay forma de persistir un cambio de prompt sin reautenticación exitosa, y
  que el token/contraseña nunca llegan a logs ni a estado visible. Encontró
  un hallazgo real (confianza 85) no relacionado con el prompt: `ChatViewModel`
  se monta una sola vez a nivel de `MainActivity` (fuera del `when(pantalla)`),
  así que la misma instancia sobrevivía a un logout/login — el historial de
  chat y cualquier acción sin confirmar de un usuario quedaban visibles (y
  confirmables, atribuyéndose al usuario nuevo) para el siguiente que
  iniciara sesión en el mismo dispositivo. Corregido: el `init` de
  `ChatViewModel` ahora reinicia el estado completo (`ChatUiState(visible = ...)`)
  cada vez que cambia el `username` de la sesión activa, no solo el flag
  `visible`. Prueba de regresión agregada
  (`ChatViewModelTest."cambiar de usuario en el mismo dispositivo..."`).
  `./gradlew build` tras la corrección: `BUILD SUCCESSFUL`.

**2. Comandos de voz** (POS-70)
- [x] Botón de micrófono + permiso `RECORD_AUDIO` (runtime permission,
  mismo patrón que el permiso de cámara de la Parte 7) — criterio: prueba de
  integración simula una transcripción y confirma que llena el campo de
  texto del chat. Verificado: `VoiceInputButton` (`ia/VoiceInputButton.kt`)
  replica el patrón de permiso de `BarcodeScannerDialog` (Parte 7) —
  `rememberLauncherForActivityResult(RequestPermission())` sobre
  `Manifest.permission.RECORD_AUDIO`, agregado a `AndroidManifest.xml` junto
  con `<uses-feature android:name="android.hardware.microphone"
  android:required="false" />`. `android.speech.SpeechRecognizer` +
  `RecognitionListener` nativos (sin dependencia nueva); el resultado se
  cablea directo a `ChatViewModel.onTextoChange` desde `AsistenteIaWidget`
  — la voz llena el mismo campo que escribir, sin estado ni camino aparte
  (intro de esta Parte). Sin test unitario del reconocimiento en
  sí (`SpeechRecognizer`/`RecognitionListener` son APIs de Android sin
  Robolectric en este proyecto) — mismo criterio que `BarcodeAnalyzer`/ML Kit
  (Parte 7): se verifica en el Xiaomi (`needs-device`, sub-paso 5).
  `ChatViewModelTest."una transcripcion de voz llena el campo de texto..."`
  cubre el contrato que el botón consume (`onTextoChange` llena
  `entradaTexto`). `./gradlew build`: `BUILD SUCCESSFUL` (incluye lint,
  sin findings nuevos por el permiso agregado).

**3. Comportamiento por modo y conectividad** (POS-71)
- [x] Modo remoto/local-con-sync + conexión: chat completo con acciones
  sujetas a la Parte 15 — criterio: prueba de integración. Verificado:
  `ChatViewModel` no lee `BackendMode` en ningún punto del envío de
  mensajes ni de la ejecución de acciones — un solo camino de armado de
  mensaje y ejecución para los tres modos (Decisiones abiertas de esta
  Parte). Cubierto desde el sub-paso 1 (`ChatViewModelTest` usa
  `BackendMode.LOCAL` por defecto) y explícitamente por el ítem siguiente.
- [x] Modo local + conexión: chat completo con acciones, mismo criterio que
  remoto/local-con-sync (revisión 2026-08-22, ver Decisiones abiertas) —
  criterio: prueba de integración confirma que una acción se ejecuta igual
  que en los otros dos modos. Verificado:
  `ChatViewModelTest."una accion se ejecuta igual en los tres modos de
  backend, incluido LOCAL"` recorre `BackendMode.entries` y confirma que
  `EjecutorAccionesIa.ejecutar` se invoca igual en los tres.
- [x] Sin conexión (cualquier modo): chat deshabilitado, se muestra el FAQ
  estático — criterio: prueba de integración simula sin conexión y confirma
  el fallback. Verificado: detección reactiva (Decisiones abiertas) — se
  extrajo la constante `MENSAJE_SIN_CONEXION_IA` en `LlmClient.kt`
  (antes un string duplicado en sus dos `catch (e: IOException)`).
  `ChatViewModel.obtenerRespuesta` distingue ese mensaje exacto del resto de
  los errores del proveedor y, si coincide, pone `ChatUiState.sinConexion =
  true` en vez de agregar un mensaje de error más; `AsistenteIaWidget`
  reemplaza el panel completo por `FaqPanelContent` (texto de
  `FaqContent.texto()` + botón "Reintentar" que llama a
  `ChatViewModel.reintentarConexion()`, sin perder el historial ya
  construido). `ChatViewModelTest` cubre: activación del fallback ante
  `MENSAJE_SIN_CONEXION_IA`, que un error distinto (ej. token inválido) NO
  lo activa, y que reintentar limpia `sinConexion` conservando los
  mensajes. `code-reviewer` encontró y se corrigió un hallazgo antes de
  cerrar este ítem (confianza 85): el botón "Ayuda" del header no estaba
  deshabilitado durante `enviando`, así que si el usuario lo tocaba
  (`verFaq = true`) mientras un mensaje en vuelo terminaba fallando por
  conexión (`sinConexion = true`), `reintentarConexion()` solo limpiaba
  `sinConexion` — el usuario quedaba atrapado en la vista de Ayuda tras
  tocar "Reintentar" en vez de volver al chat. Corregido: `reintentarConexion()`
  limpia ambos flags. Prueba de regresión agregada
  (`ChatViewModelTest."reintentar conexion tambien cierra la vista de
  ayuda..."`). `./gradlew build` tras la corrección: `BUILD SUCCESSFUL`.

**4. FAQ empaquetado** (POS-72)
- [x] FAQ generado y empaquetado como recurso offline de la app — criterio:
  archivo de recurso presente, accesible sin conexión. Verificado:
  `res/raw/faq.md` (preguntas/respuestas de los 8 módulos: Venta, Entrada,
  Inventario, Caja, Devoluciones, Usuarios, Configuración, Asistente de
  IA), cargado por `FaqContent` (`ia/FaqContent.kt`, mismo patrón de
  `Context` inyectado que `TicketManager`/`InventarioExportManager`) vía
  `context.resources.openRawResource(R.raw.faq)` — es un recurso empaquetado
  en el APK, sin red ni base de datos de por medio, disponible sin
  conexión por construcción. Integrado dos veces: como fallback automático
  del sub-paso 3 (`sinConexion`) y como acceso manual independiente de la
  conectividad — botón "Ayuda" en el header del panel de chat (diseño
  aprobado del sub-paso 1), que llama a `ChatViewModel.mostrarFaq()`/
  `ocultarFaq()`. `ChatViewModelTest."mostrarFaq y ocultarFaq..."` cubre el
  toggle (15 pruebas en `ChatViewModelTest` en total tras el sub-paso 3+4).
  `./gradlew build`: `BUILD SUCCESSFUL`.

**5. Verificación** (POS-73)
- [x] Instalado y verificado en el Xiaomi los tres escenarios de
  conectividad/modo, más un comando de voz de cada uno de los 4 ejemplos
  dados por el usuario ("agrega N artículos e actualiza costo", "corte
  parcial con retiro", "registra devolución", "sincroniza inventario") —
  `needs-device` Confirmado por el usuario el 2026-08-23, tras 3 rondas de
  pruebas directas en el dispositivo durante el desarrollo (widget sin
  aparecer en modo LOCAL, formato de acciones inválido, artículos
  duplicados, búsqueda de artículo existente y de venta desconectadas del
  inventario real — las 5 corregidas; ver hallazgos abajo). Los tres modos
  de conectividad y los 4 comandos de voz de ejemplo (incluido
  "sincroniza inventario", que explica que no puede hacerlo en vez de
  ejecutar nada, per Parte 15 Decisión 1) verificados explícitamente.

**Gaps encontrados en la primera ronda de pruebas en el Xiaomi (2026-08-22),
corregidos, pendientes de re-verificación en el dispositivo:**
1. El widget no aparecía en modo LOCAL (ni en modo avión, si el dispositivo
   quedaba en LOCAL): `RolDao.insertIfEmpty` solo siembra los roles de
   sistema una vez, con la tabla vacía — un dispositivo con `roles` ya
   sembrada en Room desde antes de que la Parte 14 agregara `"ia"` al seed
   se quedaba con el catálogo viejo para siempre (sin equivalente Android
   de la migración de backend `0010_add_ia_modulo_roles`, que sí corrigió
   los roles ya sembrados). Corregido:
   `LocalRolRepository.sincronizarModulosDeRolesDeSistema` reconcilia el
   `modulosPermitidos` de los roles de sistema contra el seed actual en
   cada lectura — seguro sin excepción porque `actualizarRol`/`eliminarRol`
   ya rechazan modificar un rol `esSistema`, así que nunca hay una
   personalización real que pisar. Prueba de regresión agregada
   (`LocalRolRepositoryTest."observeRoles corrige el modulosPermitidos..."`).
2. Cualquier acción (no las consultas simples) devolvía "La IA devolvió una
   respuesta con formato inválido" en modo REMOTO: el prompt de sistema
   aprobado (Parte 15) describe las acciones en prosa pero nunca especifica
   el JSON exacto que `RespuestaIaDto`/`AccionIaDto` y los DTOs de
   parámetros de `EjecutorAccionesIa.kt` esperan — cada proveedor
   improvisaba nombres de campo distintos y `Json.decodeFromString` fallaba
   con `SerializationException`. Corregido: nueva constante
   `FORMATO_SALIDA_ACCIONES` (`ia/EjecutorAccionesIa.kt`, junto a los DTOs
   privados que describe — deben mantenerse en sync) con el JSON exacto y
   las claves de `parametros` por cada uno de los 4 tipos de acción;
   `ChatViewModel.obtenerRespuesta` la concatena al prompt editable en
   tiempo de ejecución, sin tocar el texto aprobado por el usuario. Prueba
   de regresión agregada (`ChatViewModelTest."el mensaje de sistema incluye
   el formato exacto..."`, captura el mensaje real enviado a `LlmClient`).

`./gradlew build` tras ambas correcciones: `BUILD SUCCESSFUL` (16 pruebas en
`ChatViewModelTest`, 10 en `LocalRolRepositoryTest`, resto del proyecto sin
regresiones).

**Segunda ronda de pruebas en el Xiaomi (2026-08-22), con el widget ya
funcionando en modo LOCAL — 4 hallazgos más, 3 corregidos, 1 documentado
como pendiente aparte:**
1. Dictar la misma alta de artículo dos veces ("coca de 2L") creaba dos
   filas de catálogo en vez de sumar cantidad: `EjecutorAccionesIa.
   ejecutarAltaArticulo` siempre creaba `Entrada.DeArticuloNuevo`, sin
   buscar si ya existía un artículo con ese nombre/sku/código de barras.
   Corregido: `buscarArticuloExistente` consulta
   `InventarioRepository.observarInventario` (misma consulta real que ya
   usa Inventario y el contexto de la IA) por nombre exacto, luego por sku
   exacto, luego por código de barras exacto — en 3 consultas separadas,
   no reutilizando los candidatos de la búsqueda por nombre para el
   fallback (hallazgo de `code-reviewer`: con una sola consulta por
   nombre, el fallback por sku/código nunca se alcanzaba si el nombre
   dictado difería del guardado — típico del reconocimiento de voz —
   porque el artículo existente ni aparecía como candidato). Si encuentra,
   ejecuta `Entrada.DeArticuloExistente` (suma cantidad) en vez de crear
   uno nuevo. Pruebas de regresión agregadas (dedup por nombre exacto, y
   dedup por sku con nombre distinto — el caso que expuso el hallazgo).
2. Cortes de caja registrados vía IA no aparecían en la lista de Caja:
   **no corregido en esta Parte, documentado como hallazgo** — decisión
   explícita del usuario de tratarlo aparte, ver nota debajo del checklist
   y Backlog.
3. Buscar "coca" en Entrada → "Artículo existente" no encontraba nada,
   aunque el artículo ya existiera en el catálogo real: gap preexistente
   de la Parte 8/9 (no de esta Parte) — `EntradaViewModel.
   buscarArticuloExistente()` todavía buscaba en un catálogo estático de 5
   artículos de ejemplo (`CATALOGO_EJEMPLO`), nunca migrado a un
   repositorio real pese a que el propio comentario del código lo daba por
   hecho desde la Parte 8. Corregido: ahora consulta
   `InventarioRepository.observarInventario(sucursalId, busqueda =
   termino, ...)` de forma asíncrona (`viewModelScope.launch`), misma
   infraestructura que el punto 1. `CATALOGO_EJEMPLO` se sigue usando
   (fuera de este fix) para sembrar `categoriasDisponibles`/
   `unidadesMedidaDisponibles`. Prueba de regresión agregada (búsqueda sin
   coincidencias reales).
4. El chat "se perdía" — la lista de mensajes no bajaba sola al llegar
   contenido nuevo, el usuario tenía que desplazarse a mano cada vez.
   Corregido: `AsistenteIaWidget` usa `rememberLazyListState()` +
   `LaunchedEffect(totalItems) { listState.animateScrollToItem(...) }`
   (incluye el indicador "Escribiendo..." en el conteo). Pedido adicional
   del usuario en la misma ronda: botón "Borrar" en el header del panel
   (`ChatViewModel.limpiarHistorial()`, vacía la conversación sin tocar
   sesión/permiso ni cerrar el panel). Prueba de regresión agregada.

`./gradlew build` tras las 3 correcciones: `BUILD SUCCESSFUL` (17 pruebas en
`ChatViewModelTest`, 13 en `EjecutorAccionesIaIntegrationTest`, 6 en
`EntradaViewModelTest`, resto del proyecto sin regresiones).

**Tercera ronda de pruebas en el Xiaomi (2026-08-23), tras confirmar los 3
fixes anteriores — 1 hallazgo más, corregido, mismo patrón que el punto 3
de la ronda anterior:**
5. Venta de mostrador también estaba desconectada del inventario real:
   `VentaViewModel.buscar()` (el buscador principal de la pantalla de
   Venta) todavía buscaba en `CATALOGO_EJEMPLO`, el mismo tipo de catálogo
   estático de 5 artículos que tenía `EntradaViewModel` antes del punto 3 —
   nunca migrado a un repositorio real desde la Parte 7, pese a que el
   comentario original del código también lo daba por hecho. Toda venta
   registrada hasta ahora se hizo contra estos artículos de demostración,
   no contra el catálogo/inventario real. Corregido con el mismo patrón
   exacto ya aplicado y revisado en `EntradaViewModel`: `buscar()` pasa a
   `viewModelScope.launch`, consulta
   `InventarioRepository.observarInventario(sucursalId, busqueda =
   termino, pagina = 1, tamanioPagina = 1)`; `CATALOGO_EJEMPLO` se eliminó
   por completo (a diferencia de `EntradaViewModel`, en Venta no se usaba
   para nada más). `code-reviewer` no encontró hallazgos — confirmó que no
   hay condición de carrera real entre `buscar()` y `agregarAlCarrito()`
   (el botón "Agregar" solo se renderiza cuando `articuloEncontrado` ya no
   es nulo, así que no puede tocarse mientras la búsqueda está en vuelo).
   Prueba de regresión agregada (búsqueda sin coincidencias reales).

`./gradlew build` tras esta corrección: `BUILD SUCCESSFUL` (6 pruebas en
`VentaViewModelTest`, resto del proyecto sin regresiones).

**Punto 2 (cortes/retiros de caja sin lista reactiva real) — hallazgo
documentado, resuelto aparte por decisión del usuario (2026-08-22):**
`CajaRepository`/`RetiroEfectivoRepository` no tienen ningún método para
*leer* cortes/retiros persistidos — el "historial" de `CajaScreen` es una
lista puramente en memoria de `CajaViewModel`
(`CajaUiState.historialCortes`/`historialRetiros`), que solo crece cuando
se guarda un corte/retiro *desde esa misma pantalla*
(`onGuardarClick`/`onConfirmarRetiroClick` hacen `listOf(nuevo) +
historialActual`). El corte/retiro sí se guarda en la base real (mismo
repositorio que la pantalla manual) — el gap es que nada lo vuelve a leer.
Esto ya pasaría con un corte manual si se cierra y reabre la pantalla de
Caja (reinicia `historialCortes` vacío); es un gap preexistente de la
Parte 10 que la IA solo expuso al escribir desde otro ViewModel
(`ChatViewModel`, vía `EjecutorAccionesIa`). Esto además significa que la
verificación previa del ítem "La UI se actualiza automáticamente cuando la
IA modifica el punto de venta" (sub-paso 1 de esta Parte) fue incompleta
para el módulo Caja específicamente — se asumió sin comprobar que existía
un Flow reactivo ahí, como en Inventario. Propuesta ya evaluada, pendiente
de implementación en una sesión/Parte dedicada: agregar
`observeCortes(sucursalId)`/`observeRetiros(sucursalId)` reales (Room Flow
local + contraparte remota, patrón `ModeAware*`), y que `CajaViewModel`
los observe en vez de mantener listas locales. Ver también Backlog.

### Decisiones abiertas

- [x] Prompt de sistema editable desde Configuración (gate bloqueante
  dejado pendiente en la Parte 15, Decisión 3: solo se aprobó el texto y su
  valor por defecto en código, la UI editable con reautenticación quedó
  para cuando el chat se volviera operativo). **Decidido** (2026-08-22): se
  implementa en esta Parte, no se difiere — es la Parte que activa el chat
  operativamente, así que es el punto natural para cerrar ese gate. Ítem
  agregado al sub-paso 1 del checklist.
- [x] Modo LOCAL + conexión: ¿la IA puede ejecutar acciones sobre el punto
  de venta (igual que remoto/local-con-sincronización), o queda restringida
  a solo responder dudas con el FAQ como contexto, sin tocar la base de
  datos local? La redacción original de esta Parte (antes de empezar a
  implementarse) elegía la segunda opción. **Decidido** (2026-08-22): se
  permite también en LOCAL — el administrador acepta el riesgo al activar
  la IA (Parte 14) y al poder modificar el prompt de sistema (decisión
  anterior); modo local ya permite escritura sin restricción para toda
  acción manual de los demás módulos (Partes 6-13), así que la IA deja de
  ser una excepción. Un solo modo de llamada (`chatEstructurado`, con
  `EstadoPuntoVenta` + esquema de acciones) para los tres modos de backend
  con conexión, en vez de un camino aparte de solo texto para LOCAL. La
  sección "Comportamiento por modo y conectividad" y el ítem 3 del
  checklist se actualizaron para reflejar esta decisión.
- [x] Detección de "sin conexión a internet" para activar el fallback de
  FAQ: chequeo proactivo (`ConnectivityManager`, permiso `ACCESS_NETWORK_STATE`
  nuevo) o reactivo (intentar la llamada real y usar el `IOException` que
  `LlmClient` ya distingue). **Decidido** (2026-08-22): reactivo — sin
  permiso nuevo ni polling, mismo patrón de manejo de errores que
  `LlmClient`/`RemoteVentaRepository` ya usan.

---

## Parte 17: Mejoras de venta de mostrador — pago en efectivo y ticket <!-- POS-34 -->

Extiende el módulo de Venta de mostrador (Parte 7, ya cerrada) con 3 mejoras
solicitadas sobre el flujo ya funcionando: cobro en efectivo con cálculo de
cambio, generación en background de un ticket en PDF, e impresión con
reimpresión opcional para el cliente. No toca `docs/api-contract.md` ni
`backend/` — las 3 mejoras son enteramente locales al dispositivo.

### Checklist

**1. Pago en efectivo y cambio** (POS-35)
- [x] Al confirmar una venta en efectivo, `VentaViewModel` pide el monto
  recibido (diálogo `EfectivoRecibidoDialog`) antes de registrar la venta,
  valida que sea un número `>= total`, y calcula el cambio — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests` Verificado:
  `VentaViewModelTest` (5 pruebas) — flujo de 2 pasos con cambio calculado
  (`cambioEntregado = efectivoRecibido - total`), monto insuficiente
  bloquea el registro y muestra `errorEfectivo`, venta con tarjeta sigue
  registrando directo sin abrir el diálogo, y las 2 pruebas preexistentes
  de precondición (sin sucursal/sesión, falla del repositorio) siguen en
  verde adaptadas al nuevo flujo. `code-reviewer` encontró y se corrigió un
  hallazgo antes de cerrar este ítem: `cambioEntregado`/`ticketPdf`/
  `mensajeConfirmacion` de la venta anterior seguían visibles en pantalla
  mientras se armaba el carrito de la siguiente venta (riesgo real de que
  el encargado entregara el cambio o reimprimiera el ticket equivocado) —
  corregido limpiando esos 3 campos en `agregarAlCarrito`.
- [x] El cambio se muestra de forma prominente tras la venta (no solo
  dentro del mensaje de confirmación) — criterio: `VentaScreen` renderiza
  una `Card` propia con `cambioEntregado` cuando no es nulo. Verificado:
  `./gradlew build` en verde (incluye lint).

**2. Generación de ticket PDF** (POS-36)
- [x] `TicketFormatter` (objeto puro, sin dependencias de Android) arma las
  líneas del ticket (encabezado de sucursal, folio, fecha, artículos,
  totales, método de pago, y efectivo/cambio si aplica) — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests` Verificado:
  `TicketFormatterTest` (2 pruebas: venta en efectivo con cambio, venta con
  tarjeta sin esas líneas).
- [x] `TicketPdfWriter` dibuja esas líneas en un `android.graphics.pdf.PdfDocument`
  (ancho fijo tipo recibo térmico, sin librería nueva) y `TicketManager` lo
  persiste en background (`Dispatchers.IO`) dentro de una carpeta nombrada
  por la fecha de creación de la venta — criterio: `./gradlew build` en
  verde. Verificado: `TicketManager.generarTicket` escribe en
  `context.filesDir/tickets/<yyyy-MM-dd>/ticket-<folio>.pdf`; directorio
  persistente (no `cacheDir`), mismo criterio que `logs/` de `AppLogger`
  (Parte 5). Sin test unitario de `TicketPdfWriter`/`TicketManager` — mismo
  criterio que `BarcodeScannerDialog`/`InventarioExportManager`: se testea
  el contenido puro (`TicketFormatter`), no el trazo en `Canvas` ni el I/O
  de archivos.
- [x] `VentaViewModel` resuelve nombre/dirección de la sucursal actual
  (`SucursalRepository`) y dispara la generación del ticket justo después
  de registrar la venta exitosamente, guardando el `File` resultante en
  `VentaUiState.ticketPdf` — criterio: `./gradlew testDebugUnitTest` en
  verde con `TicketManager` mockeado. `jvm-tests` Verificado: cubierto por
  `VentaViewModelTest` (mock de `TicketManager.generarTicket`).
  `code-reviewer` encontró y se corrigió un hallazgo antes de cerrar este
  ítem: el registro de la venta y la generación del ticket compartían un
  único `try/catch`, así que un fallo de I/O solo en la escritura del PDF
  (venta ya persistida) se reportaba como "no se pudo registrar la venta"
  sin vaciar el carrito — el encargado podía reintentar y duplicar la venta
  ya guardada. Corregido separando ambos pasos: el carrito se vacía y la
  venta se confirma en pantalla apenas `ventaRepository.registrarVenta`
  tiene éxito; la generación del ticket corre después en su propio
  `try/catch` (`generarTicketSeguro`), y si falla solo deja `ticketPdf` en
  `null` (sin botón de impresión), sin afectar el resultado de la venta.
  Hallazgo adicional del mismo reviewer (simplificación, no bug): el nuevo
  parseo de `BigDecimal` desde texto duplicaba con otro nombre un helper ya
  existente en `InventarioViewModel.kt`/`EntradaViewModel.kt` — unificado a
  la misma firma (`String.toBigDecimalOrNull()`).

**3. Impresión y reimpresión** (POS-37)
- [x] Botón "Imprimir ticket" visible cuando hay un `ticketPdf` generado,
  que imprime vía el Print Framework de Android (`PrintManager` +
  `PrintDocumentAdapter`) — decisión de arquitectura confirmada por el
  usuario, ver "Decisiones abiertas". Criterio: `./gradlew build` en verde.
  Verificado: `Context.imprimirTicket` (`TicketPrinter.kt`) arma un
  `PrintDocumentAdapter` mínimo que copia el PDF ya generado al destino que
  entrega el sistema de impresión.
- [x] Justo después de enviar la impresión, se pregunta si se quiere una
  reimpresión para el cliente — criterio: `./gradlew build` en verde.
  Verificado: `onWriteFinished` del adapter (única señal confiable que
  expone el framework de que el documento ya se entregó al subsistema de
  impresión — Android no expone un evento de "ya salió el papel") dispara
  `VentaViewModel.onTicketImpreso()`, que muestra `ReimpresionDialog`; "Sí"
  reimprime sin volver a encadenar el diálogo (evita el bucle), "No" solo
  lo cierra.
- [x] Al cerrarse el diálogo de reimpresión (con o sin reimpresión), la
  pantalla vuelve a un estado igual al inicial — criterio: `./gradlew
  testDebugUnitTest` en verde. Hallazgo del usuario probando en el Xiaomi
  (`needs-device`, sub-paso 1 de esta Parte): tras imprimir y terminar la
  venta, el folio y el botón "Imprimir ticket" de la venta ya cerrada
  seguían visibles hasta que se agregaba el primer artículo de la
  siguiente venta. Corregido: `onReimpresionDescartada` ahora limpia
  también `mensajeConfirmacion`, `cambioEntregado` y `ticketPdf`, sin
  esperar a que arranque el carrito siguiente.
- [x] Botón "Cerrar venta" junto al de imprimir, para cuando no hace falta
  o no es posible imprimir y solo se quiere limpiar la pantalla — criterio:
  `./gradlew build` en verde. Pedido del usuario probando en el Xiaomi:
  mismo diseño de "botón doble" que `MetodoPagoSection`
  (`SingleChoiceSegmentedButtonRow` + `SegmentedButton`, con `selected`
  siempre en `false` porque no representan una selección persistida — cada
  uno dispara su propia acción una vez). `VentaViewModel.cerrarVenta()`
  reusa el mismo `sinResultadoDeVenta()` que `onReimpresionDescartada`. Si
  la generación del ticket falló (`ticketPdf == null`), el segmented row
  muestra solo "Cerrar venta".

**4. Verificación** (POS-38)
- [x] Instalado y verificado en el Xiaomi M2102J20SG: una venta en efectivo
  con cambio visible en pantalla, ticket PDF generado en
  `tickets/<fecha>/ticket-<folio>.pdf` (mismo truco `adb exec-out run-as
  com.pdv.pos cat files/tickets/...` documentado en la Parte 7), botón de
  impresión funcional, y diálogo de reimpresión tras imprimir —
  `needs-device` Confirmado por el usuario el 2026-08-20, tras varias
  rondas de prueba directa en el dispositivo durante el desarrollo (folio y
  botón de imprimir que quedaban visibles tras cerrar la venta, y falta del
  botón "Cerrar venta", ambos corregidos antes de este cierre).

### Decisiones abiertas

- [x] Mecanismo de impresión del ticket (`CLAUDE.md` §9: decisión de
  arquitectura no cubierta, preguntar antes de asumir). **Decidido**: Print
  Framework de Android (`android.print.PrintManager` +
  `PrintDocumentAdapter`) en vez de ESC/POS directo por Bluetooth — no
  agrega dependencias nuevas y no asume un modelo de impresora concreto;
  funciona con cualquier impresora que tenga un servicio de impresión
  instalado (térmicas con su app de servicio, WiFi, o "Guardar como PDF").
- [x] Formato del archivo de ticket. **Decidido**: solo PDF, generado con
  `android.graphics.pdf.PdfDocument` (API nativa, sin librería nueva) — es
  el formato natural para imprimir vía Print Framework y para archivo
  legible por humanos. Se descartó generar también XML por no tener un
  consumidor claro todavía (ej. facturación electrónica no está en
  alcance).
- [x] Persistencia del cambio entregado. **Decidido**: el cambio
  (`efectivoRecibido - total`) no se persiste en el modelo de dominio
  `Venta` ni en el backend — es un dato operativo efímero que solo le
  importa al encargado de turno en el momento de la venta, no un campo del
  ledger. Si se necesita para auditoría de caja más adelante, es una Parte
  nueva (toca `docs/api-contract.md` + Room + Alembic, `schema-parity`).

---

## Parte 18: Datos de prueba y mejoras transversales  <!-- POS-74 -->

Incorpora una función real de importación de catálogo desde CSV, agrega
teclado numérico y máscara de moneda a los campos de monto/cantidad de toda
la app, hace que la IA exporte CSV (o /calcule un total exacto) en vez de
volcar el inventario en el chat, corrige el refresco de Inventario en modo
remoto tras escrituras desde otras pantallas, y cierra el gap ya
documentado en el Backlog de cortes/retiros de caja registrados por la IA.
Seis mejoras/hallazgos reportados por el usuario tras revisión manual de la
app, con el objetivo adicional de poder hacer pruebas exhaustivas con datos
realistas (`docs/inventario-inicial.csv`, 115 artículos) en vez del
catálogo de ejemplo.

**Hallazgo de causa raíz (sub-parte E)**: el gap de refresco de Inventario
en modo remoto no es exclusivo de Entrada de mercancía (como reportó el
usuario) — el mismo problema afecta a Venta de mostrador, que también
decrementa `inventario.cantidad` desde un repositorio distinto al de la
pantalla de Inventario. La corrección se diseña a nivel de causa raíz
(señal de invalidación compartida), no solo para el caso de Entrada.

**Sub-parte G (agregada 2026-08-25)**: seis mejoras adicionales reportadas
por el usuario tras revisión manual de Configuración y Catálogo. La
investigación de código encontró dos discrepancias de causa raíz que
cambian el alcance real de dos de los seis puntos: los campos de conexión
(IP/Puerto/Nombre de base de datos) son cosméticos hoy (`NetworkModule`
usa un `BASE_URL` hardcodeado, nunca lee `ConfiguracionPreferences`), y el
modo `LOCAL_CON_SINCRONIZACION` no trae la lista de sucursales del backend
pese a que el diseño ya aprobado en la Parte 3 dice que debería. Ambos se
corrigen aquí como parte del mismo trabajo, no solo el renombrado de
campos que se pidió originalmente.

### Checklist

**A. Importación de catálogo desde CSV** (POS-75)
- [x] Propuesta de pantalla ("Importar catálogo" en Configuración, selector
  de archivo vía `ACTION_OPEN_DOCUMENT`, formato esperado documentado en
  pantalla: mismas columnas que `docs/inventario-inicial.csv`) presentada
  y aprobada — criterio: aprobación explícita registrada antes de
  implementar. `needs-approval` **Aprobado 2026-08-23.**
- [x] Parsing valida encabezado y tipos de cada fila (cantidad/precio/costo
  numéricos, resto de campos no vacíos), sin abortar la importación
  completa por filas inválidas — criterio: prueba unitaria con un CSV que
  mezcla filas válidas e inválidas verifica que las válidas se procesan y
  las inválidas quedan listadas en el resumen de errores. `jvm-tests`
  (`InventarioCsvImporterTest`)
- [x] Cada fila se escribe reutilizando `EntradaRepository.registrarEntrada`
  (sin endpoint ni tabla nueva), con el mismo criterio de deduplicación por
  SKU/nombre/código de barras agregado en la Parte 16 (artículo existente
  suma cantidad vía `Entrada.DeArticuloExistente`; artículo nuevo se da de
  alta vía `Entrada.DeArticuloNuevo`) — criterio: prueba de integración
  importa un CSV con un artículo repetido y confirma que la segunda
  ocurrencia suma cantidad en vez de duplicar el artículo. `jvm-tests`
  (`ImportadorCatalogoTest`; dedup extraído a `BuscadorArticuloExistente`,
  compartido con `EjecutorAccionesIa`)
- [x] UI de progreso (N de total) y resumen final (creados / actualizados /
  con error) — criterio: `./gradlew build` en verde y la pantalla se ve
  navegable con datos de ejemplo. (`./gradlew build` en verde)
- [x] Instalado y verificado en el Xiaomi importando
  `docs/inventario-inicial.csv` completo (115 artículos) en los tres modos
  — `needs-device`. Hallazgo de pruebas: el resumen de la importación
  anterior quedaba visible al reingresar a la pantalla (misma instancia de
  ViewModel persistida a nivel Activity) — corregido limpiando el estado al
  presionar "Atrás".

**B. Teclado numérico automático** (POS-76)
- [x] `KeyboardOptions(keyboardType = KeyboardType.Decimal)` aplicado a los
  10 campos de monto/cantidad identificados: `VentaScreen.kt` (efectivo
  recibido), `EntradaScreen.kt` (precio de venta, costo, cantidad),
  `InventarioScreen.kt` diálogo de edición (precio de venta, costo,
  cantidad en existencia), `CajaScreen.kt` (monto contado, monto de
  retiro), `DevolucionScreen.kt` (cantidad a devolver) — sin cambios de
  tipo de dato (siguen siendo `String` hasta la conversión a `BigDecimal`
  ya existente). Criterio: `./gradlew build` (lint) en verde.
- [x] Instalado y verificado en el Xiaomi: teclado numérico aparece en los
  10 campos — `needs-device`.

**C. Máscara de moneda `$`** (POS-77)
- [x] `VisualTransformation` propio (prefijo `$ `, sin separador de miles
  ni redondeo, consistente con que el resto de la app no formatea moneda
  con locale) aplicado solo a los campos de **monto** (precio de venta,
  costo, efectivo recibido, monto contado, monto de retiro) — nunca a los
  de **cantidad** — criterio: prueba unitaria del mapeo de offsets
  (`OffsetMapping`) para que el cursor no salte al escribir/borrar.
  `jvm-tests`
- [x] Instalado y verificado en el Xiaomi — `needs-device`.

**D. IA: exportar CSV o calcular total exacto en vez de listar** (POS-78)
- [x] Texto exacto agregado a `PROMPT_SISTEMA_DEFAULT` (Parte 15)
  describiendo las dos herramientas de solo lectura nuevas y cuándo
  usarlas, presentado y aprobado explícitamente antes de activarse —
  criterio: aprobación explícita registrada. `needs-approval` **Aprobado
  2026-08-25**: nueva sección "Consultas de solo lectura" en el prompt
  (`exportar_inventario`, `consultar_stock`, ejecutan sin tarjeta de
  confirmación) + dos líneas nuevas en `FORMATO_SALIDA_ACCIONES`.
- [x] Acción `exportar_inventario` (`parametros`: filtro de búsqueda
  opcional) en `EjecutorAccionesIa`, de solo lectura (sin tarjeta de
  confirmación, validada contra el permiso del módulo `"inventario"`):
  reutiliza el mismo bucle de paginación que
  `InventarioViewModel.obtenerTodosLosItemsFiltrados()` +
  `InventarioExportManager.exportarCsv()`. Se activa cuando se pide el
  inventario completo o cuando la respuesta listaría más de 20 artículos
  — criterio: prueba unitaria de `EjecutorAccionesIa` cubre ambos
  disparadores y el caso sin permiso. `jvm-tests` Verificado: "filtro" es
  ambiguo a propósito (categoría o texto de búsqueda) y se resuelve
  contra `InventarioRepository.observarCategorias()`; 4 pruebas nuevas en
  `EjecutorAccionesIaIntegrationTest` (sin filtro, filtro=categoría,
  filtro=texto libre, sin permiso).
- [x] Acción `consultar_stock` (`parametros`: artículo o categoría
  opcionales; sin ninguno = total general), de solo lectura: suma
  `cantidad` sobre todas las páginas que matchean el filtro (no solo los
  50 artículos del contexto de chat) y devuelve el número exacto — nunca
  calculado por el LLM — criterio: prueba unitaria con artículo
  específico, categoría, y total general. `jvm-tests` Verificado: 4
  pruebas nuevas en `EjecutorAccionesIaIntegrationTest` (total general,
  artículo específico, categoría, sin permiso).
- [x] `ChatViewModel` enruta ambas acciones automáticamente (sin tarjeta de
  confirmación, a diferencia de las 4 acciones de escritura existentes) —
  criterio: prueba de integración confirma que se ejecutan sin pasar por
  el flujo de confirmación. `jvm-tests` Verificado: `tiposSoloLectura` en
  `ChatViewModel.obtenerRespuesta()` ejecuta `exportar_inventario`/
  `consultar_stock` de inmediato vía `ejecutarConsultaAutomatica` (mismo
  resuelto de sesión/sucursal/permisos que `confirmarAccion`), sin crear
  `ChatUiMessage.AccionPendiente`; 2 pruebas nuevas en `ChatViewModelTest`
  (sin tarjeta pendiente; archivo para compartir expuesto y limpiado).
  `./gradlew build` en verde (compila, lint y las 56 pruebas de
  `com.pdv.pos.ia` en verde, incluidas las 8 nuevas).
- [x] Instalado y verificado en el Xiaomi: pedir el inventario completo,
  pedir más de 20 artículos de una categoría, y preguntar el stock total y
  el de un artículo/categoría específica — `needs-device`. Confirmado por
  el usuario el 2026-08-26: exportación de CSV (inventario completo y
  categoría con más de 20 artículos) y respuesta con número exacto (total,
  artículo puntual, categoría) verificados en el Xiaomi.

**E. Corrección: Inventario no se refresca en modo remoto tras escrituras externas** (POS-79)
- [x] `InventarioRefreshSignal` (singleton Hilt, `MutableSharedFlow<Unit>`)
  emitido tras cada escritura exitosa que afecta `inventario`/`articulos`
  desde fuera de `InventarioViewModel`: `EntradaRepository.registrarEntrada`,
  `VentaRepository.registrarVenta` (hallazgo adicional de causa raíz, ver
  intro de esta Parte), y la acción `alta_articulo` de `EjecutorAccionesIa`
  — criterio: prueba unitaria confirma la emisión en cada uno de los 3
  puntos. `jvm-tests` Verificado: emisión centralizada en
  `ModeAwareEntradaRepository.registrarEntrada` y
  `ModeAwareVentaRepository.registrarVenta` (único punto de escritura de
  cada uno; `alta_articulo` llama al mismo `registrarEntrada`, sin emisión
  propia). `replay = 1` (no solo `extraBufferCapacity`) para que un
  colector que se suscribe después del emit igual lo reciba — hallazgo de
  pruebas: con solo `extraBufferCapacity` las 3 pruebas nuevas colgaban
  (`TimeoutCancellationException`), porque ese buffer no retiene valores
  para colectores que aún no estaban suscritos en el momento del emit.
  3 pruebas nuevas confirman los 3 puntos (`ModeAwareEntradaRepositoryTest`,
  `ModeAwareVentaRepositoryTest`, `EjecutorAccionesIaIntegrationTest` con un
  `ModeAwareEntradaRepository` real).
- [x] `InventarioViewModel` combina esta señal con su `flatMapLatest`
  existente para forzar re-suscripción (no-op en modo local, donde Room ya
  cubre el caso) — criterio: prueba de integración en modo REMOTO: escribir
  una entrada (o venta) con la pantalla de Inventario "suscrita" refresca
  la lista sin cambiar página ni buscar. `jvm-tests` Verificado:
  `combine(sucursalId, parametros, inventarioRefreshSignal.refrescos.onStart
  { emit(Unit) })` reemplaza el `combine` de 2 flows anterior;
  `InventarioViewModelTest` nueva prueba simula el comportamiento de un solo
  disparo de `RemoteInventarioRepository` y confirma que `emitir()` fuerza
  un nuevo fetch sin cambiar `paginaMostrada` ni `busqueda`. `./gradlew
  build` en verde (compila, lint, todas las pruebas).
- [x] Instalado y verificado en el Xiaomi en modo REMOTO — `needs-device`.
  Confirmado por el usuario el 2026-08-26 y cruzado contra Postgres
  directamente: con Inventario abierto en modo Remoto, entradas de un
  artículo nuevo y de un artículo ya existente (verificado
  `inventario.cantidad = 54` en el backend tras 4 entradas acumuladas)
  se reflejan solas, sin recargar ni reabrir la pantalla. La primera
  corrida de esta verificación dio un falso negativo (aparente "no se
  actualiza" en artículo existente) causado por un filtro de búsqueda
  (`q=SKU`) + tamaño de página 1 que dejaba fuera de vista la fila
  correcta — no por el mecanismo de refresco en sí.

**F. Historial reactivo de cortes y retiros de caja** (POS-80)

*(Cierra el ítem ya documentado en el Backlog de este plan; se elimina de
ahí al cerrar esta sub-parte.)*

- [x] `docs/api-contract.md` actualizado con `GET /cortes-caja` y `GET
  /retiros-efectivo` (listado paginado por `sucursal_id`, mismo shape que
  el resto de endpoints de listado) — criterio: sección nueva, revisada
  antes de tocar código (CLAUDE.md §9). Verificado: secciones 8.4/8.5,
  mismo estilo que `GET /inventario` (sección 7.1).
- [x] Rutas FastAPI (`app/routers/caja.py`) — criterio: `pytest
  backend/tests/test_caja.py` en verde, happy path + 1 error por endpoint.
  Verificado: 4 pruebas nuevas (happy path + 422 por `sucursal_id`
  faltante, por endpoint); `pytest` completo del backend en verde (58
  pruebas).
- [x] `CajaDao` gana una query de listado por sucursal que devuelve `Flow`
  (análoga a `RetiroDao.getRetirosDelPeriodo`, ya existente) —
  criterio: `./gradlew testDebugUnitTest` en verde. `jvm-tests` Nota: la
  plantilla real de un `Flow` reactivo es `InventarioDao.observarPagina`
  (`getRetirosDelPeriodo` es `suspend`, de un solo disparo) — `CajaDao`
  gana `observarCortes(sucursalId): Flow<List<CorteCajaEntity>>` y
  `RetiroDao` gana `observarRetiros(sucursalId): Flow<List<RetiroEfectivoEntity>>`
  (nueva, sin tocar `getRetirosDelPeriodo`), ambas ordenadas por fecha
  descendente.
- [x] `RemoteCajaRepository`/`RemoteRetiroEfectivoRepository` consumen los
  endpoints nuevos; `CajaRepository`/`RetiroEfectivoRepository` (interfaz)
  ganan `observeCortes(sucursalId)`/`observeRetiros(sucursalId)`;
  `ModeAwareCajaRepository`/`ModeAwareRetiroEfectivoRepository` resuelven
  según `BackendMode`, mismo patrón que el resto de módulos — criterio:
  `./gradlew testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests`
  Verificado: mismo patrón `flatMapLatest` sobre `BackendMode` que
  `ModeAwareInventarioRepository`; pruebas nuevas de dispatch en
  `ModeAwareCajaRepositoryTest`/`ModeAwareRetiroEfectivoRepositoryTest`.
- [x] `CajaViewModel` reemplaza `historialCortes`/`historialRetiros` en
  memoria por la observación reactiva de los repositorios reales —
  criterio: prueba de integración confirma que un corte/retiro registrado
  desde la IA (`ChatViewModel`/`EjecutorAccionesIa`) aparece en
  `CajaScreen` sin reabrir la pantalla, repitiendo el escenario que expuso
  el gap en la Parte 16. `jvm-tests` **Hallazgo de causa raíz (mismo
  patrón que sub-parte E)**: para que el escenario funcione también en
  modo REMOTO (el `needs-device` de abajo pide los tres modos), se agregó
  `CajaRefreshSignal` (idéntico a `InventarioRefreshSignal`) emitido desde
  el único punto de escritura de cada repositorio
  (`ModeAwareCajaRepository.guardarCorte`/
  `ModeAwareRetiroEfectivoRepository.registrarRetiro`) y combinado en el
  `init{}` de `CajaViewModel` — sin esto, `RemoteCajaRepository.observeCortes`
  (un solo disparo, igual que `RemoteInventarioRepository`) nunca se habría
  refrescado tras una escritura externa en REMOTO. Verificado: pruebas de
  emisión en `ModeAwareCajaRepositoryTest`/`ModeAwareRetiroEfectivoRepositoryTest`,
  prueba de `EjecutorAccionesIaIntegrationTest` con un `ModeAwareCajaRepository`
  real confirma que `corte_parcial` dispara la señal, y pruebas de
  `CajaViewModelTest` confirman que el historial se actualiza sin llamar a
  ningún método del ViewModel. `./gradlew build` en verde (compila, lint,
  todas las pruebas).
- [x] Instalado y verificado en el Xiaomi en los tres modos — `needs-device`.
  Confirmado por el usuario el 2026-08-26: un corte/retiro registrado
  desde la IA, con la pantalla Caja abierta, aparece en el historial sin
  reabrirla, en los tres modos (Local, Remoto, Local con sincronización).
  En Remoto se cruzó además contra Postgres directamente (`cortes_caja`/
  `retiros_efectivo` con el `sucursal_id` correcto). Durante esta
  verificación se encontró y documentó por separado en el Backlog un bug
  de la Parte 6 (reconciliación de `sucursalIdSeleccionada` al cambiar de
  modo) que bloqueaba las escrituras remotas hasta reseleccionar la
  sucursal manualmente — no forma parte del alcance de esta Parte 18.

**G. Configuración: orden condicional, conexión real y limpieza** (POS-81)
- [x] Propuesta de la nueva estructura de `ConfiguracionScreen` presentada y
  aprobada — orden: `ModoSection` primero; `ConexionSection` +
  `SucursalSection` solo visibles si `backendMode != LOCAL`; sección
  "Permisos (simulados)" eliminada; botón "Importar catálogo" movido antes
  de `AsistenteIaSection`; botón "Cerrar sesión" eliminado de esta pantalla
  (queda solo en `HelloScreen`) — criterio: aprobación explícita
  registrada antes de implementar. `needs-approval` **Aprobado 2026-08-25.**
- [x] `ModoSection` se renderiza primero y sin condición; `ConexionSection`
  y `SucursalSection` solo se renderizan cuando el modo seleccionado no es
  `LOCAL` — criterio: revisión de código + `./gradlew build` en verde.
  Verificado: `if (uiState.modo != BackendMode.LOCAL) { ConexionSection(...);
  SucursalSection(...) }` en `ConfiguracionScreen`, `ModoSection` fuera del
  `if`. `./gradlew build` en verde.
- [x] Campo "IP" renombrado a "IP / Servidor"; campo "Puerto" renombrado a
  "Puerto del servidor"; campo "Nombre de base de datos" eliminado de la
  UI, de `DeviceConfig`, `ConfiguracionPreferences`
  (`KEY_NOMBRE_BASE_DATOS`, `setConexion`) y de `ConfiguracionUiState` —
  criterio: `./gradlew testDebugUnitTest` en verde tras actualizar
  `ConfiguracionPreferencesTest`. `jvm-tests` Verificado: `setConexion(ip,
  puerto)` sin tercer parámetro; `ConfiguracionPreferencesTest` y
  `ChatViewModelTest` actualizados; `./gradlew :app:testDebugUnitTest` en
  verde.
- [x] `NetworkModule` deja de usar un `BASE_URL` hardcodeado: el
  `OkHttpClient`/`Retrofit` toman la IP/Puerto guardados en
  `ConfiguracionPreferences` (mecanismo dinámico, ej. interceptor que
  reescribe host/puerto desde el valor observado de `deviceConfig`, sin
  requerir reiniciar la app) — criterio: prueba unitaria confirma que
  cambiar la IP/Puerto guardado cambia el host efectivo de una llamada.
  `jvm-tests` Verificado: `DynamicHostInterceptor` (`@Singleton`, lee
  `deviceConfig.first()` vía `runBlocking` en el hilo de dispatch de OkHttp;
  no-op si no hay IP/puerto guardados, deja el host del `BASE_URL` que ahora
  solo aporta esquema y path base). `DynamicHostInterceptorTest`: 2 pruebas
  (reescribe host/puerto guardados; passthrough sin conexión guardada).
- [x] Sección "Permisos (simulados)" eliminada (`PermisosSection`,
  `PermisoModulo`, `permisosSimuladosDeEjemplo` en
  `ConfiguracionViewModel`/`ConfiguracionScreen`) — criterio: `./gradlew
  build` en verde, sin referencias residuales. No requiere reemplazo: la
  administración real de permisos ya es accesible desde la pantalla
  principal (botón "Usuarios", Parte 13). Verificado: sin referencias
  residuales a `PermisoModulo`/`permisosSimulados` en `app/src`; `./gradlew
  build` en verde.
- [x] Botón "Importar catálogo" movido antes de `AsistenteIaSection` —
  criterio: revisión de código confirma el nuevo orden. Verificado: el
  `Button` de "Importar catálogo" está entre el bloque condicional
  Conexión/Sucursal y `AsistenteIaSection`.
- [x] Botón "Cerrar sesión" eliminado de `ConfiguracionScreen` (duplicado
  del de `HelloScreen`) — criterio: `./gradlew build` en verde; un solo
  botón "Cerrar sesión" en toda la app. Verificado: `ConfiguracionViewModel
  .logout()` y la prueba `logout delegates to the session manager`
  eliminados; el único botón "Cerrar sesión" queda en `HelloScreen`.
- [x] `ModeAwareSucursalRepository` usa `RemoteSucursalRepository` también
  en `LOCAL_CON_SINCRONIZACION` (no solo en `REMOTO`), alineado con el
  diseño ya aprobado en la Parte 3 ("en modo remoto o
  local-con-sincronización la trae con `GET /sucursales`") — criterio:
  prueba unitaria nueva en `ModeAwareSucursalRepositoryTest` cubre
  `LOCAL_CON_SINCRONIZACION`. `jvm-tests` Verificado: `when` reagrupado a
  `LOCAL -> local` / `REMOTO, LOCAL_CON_SINCRONIZACION -> remote`; prueba
  nueva `LOCAL_CON_SINCRONIZACION also reads the sucursal catalog from the
  backend`.
- [x] Instalado y verificado en el Xiaomi en los tres modos — Modo aparece
  primero; en LOCAL no se ven Conexión ni Sucursal; en REMOTO y
  LOCAL_CON_SINCRONIZACION sí, y la lista de sucursales llega del backend
  en ambos modos; cambiar IP/Puerto y guardar hace que la app hable
  efectivamente con ese backend (probar apuntando a un puerto distinto al
  hardcodeado); "Importar catálogo" aparece antes de "Asistente de IA"; no
  hay sección Permisos ni botón "Cerrar sesión" en Configuración —
  `needs-device`. Confirmado por el usuario el 2026-08-28: los tres modos
  OK en el Xiaomi.

**H. Corrección: sucursalIdSeleccionada no se reconcilia al cambiar de modo** (POS-82)

*(Cierra el bug documentado en el Backlog de este plan, encontrado durante
la verificación needs-device de las sub-partes E/F; se elimina de ahí al
agregar esta sub-parte.)*

- [x] Propuesta de fix presentada y aprobada — criterio: aprobación
  explícita registrada antes de implementar. `needs-approval` Alcance: al
  resolver la lista de sucursales en modo REMOTO o
  LOCAL_CON_SINCRONIZACION (`ModeAwareSucursalRepository`), si
  `sucursalIdSeleccionada` no aparece en esa lista, el fallback ya usado
  para poblar el dropdown (`sucursales.firstOrNull()`) se persiste
  automáticamente de vuelta en `ConfiguracionPreferences` — no solo se usa
  para el estado en memoria de `ConfiguracionScreen`. **Aprobado
  2026-08-28**: la reconciliación vive en el `combine(deviceConfig,
  observeSucursales())` de `ConfiguracionViewModel` (que ya calcula ese
  fallback), acotada a `modo != LOCAL`; sin loop porque al persistir el
  `find` acierta y no reescribe.
- [x] Persistencia automática implementada — criterio: prueba unitaria en
  `ModeAwareSucursalRepositoryTest`/`ConfiguracionViewModelTest` simula un
  `sucursalIdSeleccionada` local ausente en la lista remota tras un cambio
  de modo y confirma que `ConfiguracionPreferences.deviceConfig
  .sucursalIdSeleccionada` queda actualizado sin intervención manual del
  usuario. `jvm-tests` Verificado: 2 pruebas nuevas en
  `ConfiguracionViewModelTest` (non-LOCAL reconcilia al fallback; LOCAL deja
  el id intacto); `./gradlew :app:testDebugUnitTest` en verde.
- [x] Verificado en el Xiaomi: reproducir el escenario original (operar en
  LOCAL, cambiar a REMOTO sin tocar el dropdown de sucursal) y confirmar
  que una escritura remota (ej. un retiro) ya no falla con
  `ForeignKeyViolationError`. `needs-device`. Confirmado por el usuario el
  2026-08-28: retiro remoto OK sin error de FK tras cambiar de LOCAL a
  REMOTO sin tocar el dropdown.

**I. Caja: historial limitado a 7 días y exportación de cortes/retiros por periodo** (POS-83)

- [x] Propuesta presentada y aprobada — criterio: aprobación explícita
  registrada antes de implementar. `needs-approval` Contenido de la
  propuesta:
  - El historial de `CajaScreen` (cortes y retiros) se filtra a los
    últimos 7 días (`fechaFin`/`fecha >= ahora - 7 días`) sobre el mismo
    `observeCortes`/`observeRetiros` reactivo ya existente (sub-parte F) —
    sin tocar `TAMANIO_PAGINA_HISTORIAL = 50` en `RemoteCajaRepository`,
    que ya cubre holgadamente una semana de operación típica.
  - Nuevo botón "Exportar cortes" en `CajaScreen` que abre un diálogo de
    periodo (mismo date/time picker que ya usa el corte final), con
    periodo por defecto = últimos 7 días, editable a cualquier rango.
  - Al confirmar, genera un CSV con los cortes y los retiros de efectivo
    del periodo elegido (no acotado a los 7 días de la vista) y lo
    comparte vía el mismo share sheet que usa la exportación de
    Inventario (`InventarioExportManager`, Parte 9).

  **Aprobado 2026-08-28** con estas decisiones: (a) filtro de 7 días
  client-side en el collector del `init{}` de `CajaViewModel` (cortes por
  `fechaFin`, retiros por `fecha`), sin cambiar firmas de
  `observeCortes`/`observeRetiros`; (b) CSV: **un solo archivo, dos
  bloques** — `CORTES` (columnas: `id, tipo, fecha_inicio, fecha_fin,
  total_ventas, total_efectivo, total_tarjeta, total_retiros,
  monto_esperado, monto_contado, diferencia`), línea en blanco, `RETIROS`
  (`id, fecha, monto, motivo`); (c) `CajaExportManager` nuevo que espeja
  `InventarioExportManager` (FileProvider, `cacheDir/exports`, filename
  `caja-cortes-<timestamp>.csv`), sin tocar el módulo de Inventario;
  (d) implementación en 3 checkpoints: contrato+backend+pytest / repos
  android+CSV+jvm-tests / UI wiring+build, luego el `needs-device`.
- [x] `CajaRepository`/`RetiroEfectivoRepository` ganan
  `obtenerCortesDelPeriodo`/`obtenerRetirosDelPeriodo` (suspend, un solo
  disparo, por rango de fechas) — criterio: `./gradlew testDebugUnitTest`
  en verde. `jvm-tests` `LocalCajaRepository`/`LocalRetiroEfectivoRepository`
  implementan con una query Room nueva, mismo patrón que
  `VentaDao.getVentasDelPeriodo`/`RetiroDao.getRetirosDelPeriodo` (ya
  usadas por `calcularTotales`). Verificado: `CajaDao.getCortesDelPeriodo`
  nueva (`fechaFin BETWEEN`, orden ascendente para el CSV cronológico);
  `LocalRetiroEfectivoRepository` reutiliza la `RetiroDao.getRetirosDelPeriodo`
  ya existente y la ordena por `fecha`. `./gradlew :app:testDebugUnitTest`
  en verde.
- [x] `docs/api-contract.md` actualizado: `GET /cortes-caja` y `GET
  /retiros-efectivo` (secciones 8.4/8.5) ganan parámetros opcionales
  `desde`/`hasta` — criterio: sección revisada antes de tocar código
  (CLAUDE.md §9). Verificado: secciones 8.4/8.5 documentan `desde`/`hasta`
  (ISO-8601, rango inclusivo sobre `fecha_fin` y `fecha` respectivamente;
  omitidos = sin filtro, comportamiento previo).
- [x] Rutas FastAPI filtran por `desde`/`hasta` cuando se envían —
  criterio: `pytest backend/tests/test_caja.py` en verde, con casos con y
  sin filtro de fecha. Verificado: `list_cortes_caja`/`list_retiros_efectivo`
  agregan `CorteCaja.fecha_fin`/`RetiroEfectivo.fecha` a los filtros solo si
  el parámetro llega; 2 pruebas nuevas (`test_list_cortes_caja_filtra_por
  _rango_de_fechas`, `test_list_retiros_efectivo_filtra_por_rango_de_fechas`);
  `pytest` completo del backend en verde (60 pruebas).
- [x] `RemoteCajaRepository`/`RemoteRetiroEfectivoRepository` implementan
  los métodos de periodo contra los parámetros nuevos — criterio:
  `./gradlew testDebugUnitTest` mockeando Retrofit. `jvm-tests` Verificado:
  `CajaApiService.getCortes`/`RetiroApiService.getRetiros` ganan
  `@Query("desde")`/`@Query("hasta")` opcionales (`String? = null`, Retrofit
  los omite si son null); los métodos de periodo recorren páginas de 100
  hasta juntar `total` (patrón `InventarioViewModel
  .obtenerTodosLosItemsFiltrados`, porque un mes puede exceder el
  `TAMANIO_PAGINA_HISTORIAL = 50`) y ordenan ascendente igual que el local.
- [x] `ModeAwareCajaRepository`/`ModeAwareRetiroEfectivoRepository`
  exponen los métodos de periodo resolviendo local/remoto según
  `BackendMode` — criterio: prueba unitaria confirma el dispatch correcto.
  `jvm-tests` Verificado: mismo `when` sobre `deviceConfig.backendMode` que
  el resto de métodos (`LOCAL`/`LOCAL_CON_SINCRONIZACION` → local, `REMOTO`
  → remote); 1 prueba de dispatch nueva por repositorio en
  `ModeAwareCajaRepositoryTest`/`ModeAwareRetiroEfectivoRepositoryTest`.
- [x] Generador de CSV (nuevo, mismo patrón que `InventarioCsvExporter`)
  produce un archivo con los cortes y los retiros del periodo — criterio:
  prueba unitaria del formato exacto del CSV generado. `jvm-tests`
  Verificado: `CajaCsvExporter` (objeto en `com.pdv.pos.caja.export`) —
  un archivo, bloque `CORTES` (11 columnas) + línea en blanco + bloque
  `RETIROS` (4 columnas), fechas ISO-8601 UTC, montos `toPlainString`,
  nulos vacíos, escape RFC 4180 básico; `CajaExportManager` espeja
  `InventarioExportManager` (FileProvider, `cacheDir/exports`,
  `caja-cortes-<timestamp>.csv`). `CajaCsvExporterTest`: 2 pruebas (formato
  exacto con corte sin contar / retiro con coma en el motivo; caso vacío
  deja solo encabezados).
- [x] `CajaViewModel`/`CajaScreen` conectan el botón "Exportar" al flujo
  real (selección de periodo → fetch → generar CSV → compartir) —
  criterio: `./gradlew build` en verde. Verificado: `CajaViewModel` filtra
  `historialCortes`/`historialRetiros` a `DIAS_HISTORIAL_MS = 7 días` en los
  dos collectors; `onExportarClick`/`onConfirmarExportarClick` (periodo por
  defecto = últimos 7 días, editable) hacen fetch de ambos repos por periodo
  → `CajaExportManager` → `archivoExportado`; `CajaScreen` gana botón
  "Exportar cortes" + `ExportarDialog` (reusa `SelectorFechaHora`) y un
  `LaunchedEffect` que abre el share sheet (`Intent.ACTION_SEND`, chooser
  "Exportar cortes de caja"). 3 pruebas nuevas en `CajaViewModelTest`
  (filtro de 7 días descarta cortes viejos; flujo de exportación expone el
  archivo y cierra el diálogo). `./gradlew build` en verde (compila, lint,
  pruebas debug y release).
- [x] Instalado y verificado en el Xiaomi en los tres modos: el historial
  muestra solo los últimos 7 días, y exportar un periodo distinto (ej. el
  mes completo) genera un CSV correcto con cortes y retiros de ese rango
  — `needs-device`. Confirmado por el usuario el 2026-08-28.

### Decisiones abiertas

- [x] Alcance de la importación CSV: ¿script de prueba de una sola vez, o
  función real de la app reutilizable para altas masivas? **Decidido**
  (2026-08-23): función real de la app, accesible desde Configuración —
  ver sub-parte A.
- [x] Disparador de exportación CSV vs. respuesta numérica de la IA en
  consultas de inventario. **Decidido** (2026-08-23): pedido explícito de
  inventario completo o una respuesta que listaría más de 20 artículos →
  exportar CSV (compartir archivo); consulta parcial/agregada (total
  general, de un artículo, o de una categoría) → la app calcula y devuelve
  un número exacto, nunca el LLM — ver sub-parte D.
- [x] Alcance del cierre del gap de cortes/retiros de caja por IA.
  **Decidido** (2026-08-23): se implementa tal como estaba evaluado en el
  Backlog (`observeCortes`/`observeRetiros`, patrón `ModeAware*`) — ver
  sub-parte F.
- [x] Multi-franquicia: ¿un backend por franquicia (deployment separado) o
  un backend compartido multi-tenant? **Decidido** (2026-08-25): un backend
  por franquicia — cada franquicia corre su propio stack Docker (backend +
  Postgres propios); los dispositivos de cada franquicia apuntan su
  IP/Puerto (sub-parte G) al backend correspondiente. Aislamiento total sin
  cambio de esquema ni tabla de tenant. La alternativa (una sola Postgres
  sirviendo varias franquicias vía `tenant_id`) se evaluó y no se elige por
  ahora — ver Backlog. **Nota de concurrencia**: al ser procesos/contenedores
  separados por franquicia, no hay estado compartido (threads, event loop,
  pool de conexiones a BD) entre franquicias — nada nuevo que sincronizar
  entre ellas. La concurrencia *dentro* de una franquicia sigue como ya está
  documentado en `CLAUDE.md` §4 (Uvicorn en dev, Gunicorn+Uvicorn workers en
  prod), sin cambios. Esto era justamente el riesgo de la alternativa
  descartada (una sola Postgres/pool compartido entre tenants).

---

## Parte 19: Panel de revisión manual de conflictos de sincronización  <!-- POS-84 -->

*(Cierra el punto ya mencionado en el Backlog de este plan desde la Parte
6; se elimina de ahí al agregar esta Parte.)*

Pantalla de administración (accesible desde Configuración, mismo criterio
de acceso que "Usuarios") que lista los registros de `sync_conflicts`
(Parte 3) generados por `LastWriteWinsSyncEngine`/`EventoAditivoCombiner`
(Parte 6) — incluyendo los resueltos automáticamente — para que un
administrador pueda auditar qué pasó durante la sincronización.

### Checklist

**1. UI** (POS-85)
- [x] Propuesta de pantalla (lista de conflictos con entidad, valores
  local/remoto/resuelto, política aplicada, si se resolvió
  automáticamente) presentada y aprobada — criterio: aprobación explícita
  registrada antes de implementar. `needs-approval` Aprobado el 2026-08-28
  (pantalla propia `RevisionConflictosScreen`, solo lectura, filtro
  Todos/Pendientes/Auto-resueltos, tarjeta por conflicto con chip de
  estado; acceso desde `ConfiguracionScreen`).
- [x] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` en verde y la pantalla es navegable desde
  Configuración. Verificado: `./gradlew build` en verde (`BUILD
  SUCCESSFUL`, incluye lint y `testReleaseUnitTest`/`test`). Paquete
  `com.pdv.pos.conflictos` (`RevisionConflictosScreen`/`ViewModel`/
  `UiState`) con 3 conflictos de ejemplo; `Pantalla.CONFLICTOS` nuevo en
  `MainActivity`, botón "Revisión de conflictos de sincronización" en
  `ConfiguracionScreen`. Gate de permiso (`onIntentoNavegar("usuarios")`)
  diferido al sub-paso 4 (Wiring).
- [ ] Instalado y verificado en el Xiaomi — `needs-device`.

**2. Repositorio local** (POS-86)
- [x] `SyncConflictRepository` (interfaz) en `domain/repository/` —
  criterio: compila sin Room ni Retrofit. Verificado: `SyncConflictRepository`
  expone solo `observeConflictos(): Flow<List<SyncConflict>>` sobre el
  modelo de dominio `SyncConflict`; sin imports de Room ni Retrofit.
- [x] `LocalSyncConflictRepository` (Room, sobre `SyncConflictDao` ya
  existente desde la Parte 6) — criterio: `./gradlew testDebugUnitTest`
  en verde. `jvm-tests` Verificado: `./gradlew testDebugUnitTest` en verde
  (`BUILD SUCCESSFUL in 2m 59s`); `LocalSyncConflictRepositoryTest` 3/3
  (mapeo entity->dominio, lista vacía, múltiples conflictos) y
  `LastWriteWinsSyncEngineTest` 3/3 (sin romper por el nuevo método del
  DAO). Se agregó `SyncConflictDao.observeAll(): Flow<...>` con
  `ORDER BY fechaDeteccion DESC` (se mantienen `insert`/`getAll`).

**3. Repositorio remoto** (POS-87)
- [x] `docs/api-contract.md` actualizado con el endpoint de listado de
  conflictos — criterio: sección nueva, revisada antes de tocar código
  (CLAUDE.md §9). Aprobado el 2026-08-28: reemplazó el placeholder de la
  sección 3 (Items) por "Conflictos de sincronización" — `GET
  /sync-conflicts` (paginado, filtros opcionales `sucursal_id` /
  `resuelto_automaticamente`, orden `fecha_deteccion` desc) y `POST
  /sync-conflicts` (subida device->backend con `id` generado por el
  dispositivo, idempotente: `201` nuevo / `200` ya existía / `422`
  inválido). Sin `DELETE`.
- [x] Ruta FastAPI + `RemoteSyncConflictRepository` (Retrofit) — criterio:
  `pytest`/`./gradlew testDebugUnitTest` en verde. `jvm-tests` `schema-parity`
  Verificado backend: `alembic upgrade head` aplicó `0011_create_sync_conflicts`
  y `alembic check` → "No new upgrade operations detected"; `pytest`
  completo 65/65 en verde (`test_sync_conflicts.py` 5/5: GET orden desc,
  POST happy path, idempotencia `201`->`200` sin sobreescribir, filtro
  `resuelto_automaticamente`, `422` por política inválida). `schema-parity`:
  `docs/schema-pos.json` `sync_conflicts` == `SyncConflict` (SQLAlchemy,
  `JSONB` para los tres `valor_*`) == `SyncConflictEntity` (Room, Parte 6);
  `alembic/env.py` ahora importa también `rol` y `sync_conflict` (faltaba
  `rol`, rompía `alembic check`). Verificado Android: DTOs
  (`valor_*` como `JsonElement`), `SyncConflictApiService` (GET/POST),
  `RemoteSyncConflictRepository` (`observeConflictos()` vía GET +
  `subirConflicto()` vía POST, fuera de la interfaz de solo lectura),
  wiring en `NetworkModule`, `RemoteSyncConflictRepositoryTest` 3/3.
  `./gradlew testDebugUnitTest` en verde (`BUILD SUCCESSFUL in 2m 3s`).

**4. Wiring** (POS-88)
- [x] `ViewModel` conectado según `BackendMode` (Parte 6), mismo patrón
  `ModeAware*` que el resto de módulos — criterio: prueba con estados
  mockeados. `jvm-tests` Verificado: `./gradlew build` en verde (`BUILD
  SUCCESSFUL in 11m 59s`, incluye lint y `test`). `ModeAwareSyncConflictRepository`
  (`flatMapLatest` sobre `deviceConfig.backendMode`; LOCAL y
  LOCAL_CON_SINCRONIZACION leen Room, REMOTO lee el backend, mismo split
  que `ModeAwareVentaRepository`) cableado en `RepositoryModule` vía
  `@Binds`. `RevisionConflictosViewModel` reescrito: inyecta
  `SyncConflictRepository`, mapea `SyncConflict` -> `ConflictoUi` con fecha
  formateada, sin datos estáticos. Gate de acceso al panel en
  `ConfiguracionViewModel.onIntentoAbrirConflictos()` (mismo criterio que
  el módulo "usuarios", registra `AUTH` en el log si se deniega — como
  `HelloViewModel.onIntentoNavegar`). Pruebas:
  `ModeAwareSyncConflictRepositoryTest` 3/3,
  `RevisionConflictosViewModelTest` 2/2, `ConfiguracionViewModelTest` 18/18
  (+2 del gate).
- [x] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`.
  Confirmado por el usuario el 2026-08-29: modo REMOTO muestra los dos
  conflictos sembrados vía `POST /sync-conflicts` (orden `fecha_deteccion`
  desc, chip rojo "Pendiente de revisión" / verde "Auto-resuelto", filtro
  Todos/Pendientes/Auto-resueltos); modos LOCAL y LOCAL_CON_SINCRONIZACION
  abren el panel leyendo de Room (vacío -> "Sin conflictos registrados",
  correcto porque el dispositivo nunca corrió una sincronización).

### Decisiones abiertas

- [x] Alcance de la acción del administrador sobre un conflicto: ¿solo
  visualizar (auditoría de solo lectura), o permitir revertir/forzar un
  valor distinto al ya resuelto? Impacta si el módulo escribe algo además
  de leer `sync_conflicts`. **Decidido** (2026-08-28): solo lectura
  (auditoría). El panel no escribe `sync_conflicts` ni la entidad
  afectada; `SyncConflictRepository`/`LocalSyncConflictRepository`/
  `RemoteSyncConflictRepository` exponen únicamente listado/observación.
- [x] Ubicación en la navegación: ¿pantalla propia accesible desde
  Configuración, o sub-sección dentro de una pantalla existente?
  **Decidido** (2026-08-28): pantalla propia (`Pantalla` nueva en
  `MainActivity`), mismo patrón que Usuarios/Roles/Venta/Caja, con gate de
  permiso vía `onIntentoNavegar`.
- [x] Lado backend del sub-paso 3 (no listada originalmente; surgió al
  implementar): hoy nada llena `sync_conflicts` en el backend (solo Room
  local, sin motor de sync remoto real). **Decidido** (2026-08-28): el
  backend gana tabla + modelo SQLAlchemy + migración Alembic + `GET`
  paginado **y** un `POST` de subida para que cada dispositivo empuje sus
  conflictos al sincronizar, cerrando el ciclo end-to-end en esta Parte.

---

## Parte 20: FAQ curado para el chat de IA  <!-- POS-89 -->

*(Cierra el punto ya mencionado en el Backlog de este plan desde la Parte
16; se elimina de ahí al agregar esta Parte. El título original era "RAG
para el chat de IA"; se redefinió el 2026-08-29 — ver Decisiones abiertas.)*

Reemplaza el contexto de FAQ estático usado hoy por `ChatViewModel`/
`PromptSistema` (Parte 16) —el archivo de texto libre `res/raw/faq.md`
inyectado entero— por un FAQ curado en `res/raw/faq.jsonl`: una lista de
entradas `{"faq": N, "question": ..., "answer": ...}`. La lista de
preguntas (número + texto) se concatena al prompt de sistema en tiempo de
ejecución, igual que ya se hace con `FORMATO_SALIDA_ACCIONES` (Parte 16).
Cuando la pregunta del usuario coincide con una del FAQ, el modelo emite
una nueva consulta de solo lectura `consultar_faq` con `{"numero": "N"}` y
la app devuelve el `answer` **verbatim** al chat, con sus marcadores
Markdown originales — mismo mecanismo que `consultar_stock` /
`exportar_inventario` (Parte 18, sub-parte D). Además, si el usuario
escribe `qN` (atajo), la app responde la entrada N sin llamar al LLM
(funciona sin conexión).

Objetivo: respuestas de ayuda precisas y consistentes (texto curado, no
parafraseo del modelo), sin dependencias nuevas, en los tres modos de
backend con conexión.

### Checklist

**1. Contenido del FAQ** (POS-90)
- [x] `android/app/src/main/res/raw/faq.jsonl` con 45 entradas (una línea
  JSON por entrada: `{"faq": N, "question": string, "answer": string}`),
  derivadas de las 8 secciones de `res/raw/faq.md` + comportamiento
  estabilizado en `CLAUDE.md` y Partes 6-19. `answer` en español con
  marcadores Markdown, sin diagramas. Numeración `faq` contigua 1..45.
  `needs-approval` Aprobado el 2026-08-29 (45 entradas presentadas:
  Venta 1-6, Entrada 7-10, Inventario 11-15, Caja 16-20, Devoluciones
  21-23, Usuarios/roles 24-27, Configuración 28-33, Asistente de IA 34-41,
  Sincronización/transversal 42-45; JSON validado).
- [x] `res/raw/faq.md` eliminado; `res/raw/faq.jsonl` es la única fuente
  del FAQ — Verificado: no quedan referencias a `R.raw.faq` que esperen
  Markdown libre (`FaqContent.kt` eliminado; `FaqRepository` parsea JSONL).

**2. Carga, lookup e inyección al prompt** (POS-91)
- [x] `ia/FaqRepository.kt` (`FaqEntry` `@Serializable`; `entries` parsea
  `R.raw.faq` una vez vía `parseFaqJsonl`; `find(numero)` → `buscarFaq`;
  `instruccionFaq` = `construirInstruccionFaq` arma el bloque `# FAQ` +
  lista numerada de preguntas). Reemplaza a `ia/FaqContent.kt` (eliminado)
  — criterio: `./gradlew testDebugUnitTest --tests "com.pdv.pos.ia.*"` en
  verde. `jvm-tests` Verificado: `FaqRepositoryTest` 4/4 (parseo sin error
  con 45 entradas, numeración contigua 1..45, `buscarFaq` acierta y da
  `null` fuera de rango, `instruccionFaq` empieza con `# FAQ` y lista las
  45). `EjecutorAccionesIaIntegrationTest` 23/23 sin cambios.
- [x] `ChatViewModel.obtenerRespuesta` concatena `faqRepository.instruccionFaq`
  después de `FORMATO_SALIDA_ACCIONES`; `faqTexto` pasa a
  `faqRepository.textoAyuda` — criterio: test de `ChatViewModelTest` que
  captura el mensaje de sistema y verifica `# FAQ` + una pregunta.
  `jvm-tests` Verificado: `ChatViewModelTest` 20/20 (incluye el test nuevo
  `el mensaje de sistema incluye la instruccion de FAQ...`).

**3. Acción de solo lectura `consultar_faq`** (POS-92)
- [x] En `EjecutorAccionesIa`: `ParametrosConsultarFaqDto(numero: String)`,
  7ª opción en `FORMATO_SALIDA_ACCIONES` (`tipo "consultar_faq"`, `modulo
  "ia"`, `parametros {"numero": "12"}`), `moduloRequeridoPorTipo` →
  `"ia"`, rama en el `when` → `ejecutarConsultarFaq` (devuelve
  `Ejecutada(entry.answer)` verbatim, o `Fallida` si el número no existe;
  `numero` no numérico lo captura el `catch (NumberFormatException)`
  existente). `FaqRepository` inyectado por constructor — criterio:
  `./gradlew testDebugUnitTest --tests "com.pdv.pos.ia.*"` en verde.
  `jvm-tests` Verificado: `EjecutorAccionesIaIntegrationTest` 25/25
  (`consultar_faq` con `{"numero":"5"}` → `Ejecutada` con el `answer`
  exacto; `{"numero":"999"}` → `Fallida`).
- [x] `ChatViewModel.tiposSoloLectura` incluye `"consultar_faq"` (se
  ejecuta de inmediato, sin tarjeta de confirmación, vía
  `ejecutarConsultaAutomatica`) — criterio: test que verifica que no se
  genera `AccionPendiente` y el `answer` aparece como mensaje de IA.
  `jvm-tests` Verificado: `ChatViewModelTest` 21/21 (test nuevo
  `consultar_faq se ejecuta de inmediato...`).

**4. Respuesta instantánea sin LLM: atajo `qN` y match por texto** (POS-93)
- [x] `ChatViewModel.enviarMensaje` detecta `^[qQ]\s?(\d{1,3})$` antes de
  llamar al LLM (`numeroFaqInstantaneo` / `responderFaqInstantaneo`) y
  responde `faqRepository.find(n)?.answer` verbatim, sin
  token/sucursal/conexión — criterio: `./gradlew testDebugUnitTest --tests
  "com.pdv.pos.ia.ChatViewModelTest"` en verde. `jvm-tests` Verificado:
  `ChatViewModelTest` (`q3` responde la entrada 3 con `coVerify(exactly
  = 0)` sobre el proveedor; `q99` inexistente → "No encontré la pregunta
  99"; "que hago con la pregunta q3" NO dispara el atajo y sí llama al
  proveedor).
- [x] Match exacto tras normalizar (ronda de dispositivo 2026-08-30: en el
  celular cuesta escribir `¿`, la pregunta no coincía literal y el modelo
  improvisaba una respuesta además del texto verbatim).
  `FaqRepository.matchPorTexto` → `buscarFaqPorTexto` + `normalizarConsultaFaq`
  (minúsculas, sin acentos ni signos, espacios colapsados); en
  `enviarMensaje` se chequea después de `qN` y responde sin llamar al LLM.
  Además, en `obtenerRespuesta`, cuando la única acción de la respuesta del
  modelo es `consultar_faq` se descarta su `respuesta_usuario` y se muestra
  solo el texto verbatim. No cubre parafraseos (→ embeddings, trabajo
  futuro) — criterio: `./gradlew testDebugUnitTest --tests
  "com.pdv.pos.ia.*"` en verde. `jvm-tests` Verificado: `FaqRepositoryTest`
  7/7 (normaliza `¿Cómo cobro, en efectivo?!` → `como cobro en efectivo`;
  matchea la misma pregunta con `¿`/acentos/mayúsculas; no matchea
  parafraseo ni blanco); `ChatViewModelTest` 27/27 (`¿Cómo cobro en
  efectivo?` responde verbatim con `coVerify(exactly = 0)`; con
  `consultar_faq` no se muestra la `respuesta_usuario` improvisada).

**5. Prompt de sistema por defecto** (POS-94)
- [x] `PROMPT_SISTEMA_DEFAULT` (`ia/PromptSistema.kt`): ítem 6 nuevo
  ("Responder una pregunta frecuente con el texto exacto del FAQ") en la
  lista de consultas de solo lectura; conteos en prosa ajustados ("solo
  estas dos" → "solo estas tres", "consultas de solo lectura (4 y 5)" →
  "(4, 5 y 6)", "cinco tipos (tres acciones, dos consultas)" → "seis
  tipos (tres acciones, tres consultas)"). El mecanismo real lo dispara
  `instruccionFaq` en runtime; este cambio mantiene coherente el texto por
  defecto para instalaciones nuevas. `needs-approval` Aprobado el
  2026-08-29 (4 cambios presentados y aceptados; un dispositivo con prompt
  ya editado en DataStore conserva su copia y el FAQ le funciona igual).
  Verificado: `./gradlew testDebugUnitTest --tests "com.pdv.pos.ia.*"
  --tests "com.pdv.pos.config.ConfiguracionViewModelTest" --tests
  "com.pdv.pos.config.PromptIaPreferencesTest"` en verde (los tests
  comparan contra la constante, sin strings hardcodeados).

**6. Panel de ayuda** (POS-95)
- [x] `ChatViewModel.faqTexto` se arma desde `FaqRepository.textoAyuda`
  (`entries.joinToString("\n\n") { "P: ...\nR: ..." }`); `FaqPanelContent`
  (`AsistenteIaWidget.kt`) no cambia de firma — sigue recibiendo
  `viewModel.faqTexto` y lo renderiza como texto plano — criterio:
  `./gradlew build` en verde y la vista de Ayuda / el fallback sin
  conexión siguen mostrando el FAQ. `jvm-tests` Verificado: `./gradlew
  build` `BUILD SUCCESSFUL` (compila debug+release, `:app:check` con
  unit tests y lintVital, `assemble`); `ChatViewModelTest` 25/25 (incluye
  `faqTexto viene del FaqRepository`).

**7. Verificación end-to-end en dispositivo** (POS-96)
- [x] Instalado y verificado en el Xiaomi M2102J20SG — `needs-device`.
  Confirmado por el usuario el 2026-08-30: (a) *"¿cómo cobro en efectivo?"*
  → la IA emite `consultar_faq` y aparece el `answer` verbatim con sus
  marcadores; (b) `q3` → respuesta instantánea sin latencia de red; (c)
  modo avión → `q3` sigue respondiendo y un mensaje normal cae al panel de
  ayuda con las 45 entradas en formato P/R; (d) *"dá de alta 10 tornillos
  a $50"* → tarjeta de confirmación normal; (e) `consultar_faq` + alta de
  artículo verificados en LOCAL, REMOTO y LOCAL_CON_SINCRONIZACION.
  Durante la prueba se observó, de forma **intermitente**, "La IA devolvió
  una respuesta con formato inválido" en un `alta_articulo` multi-turno; se
  investigó (ver Hallazgo abajo) y se determinó que es un fallo
  preexistente del proveedor, no una regresión de esta Parte — el mismo
  pedido funcionó al reintentar.
- [x] Ronda 2 (`needs-device`): match por texto del grupo 4. Confirmado por
  el usuario el 2026-08-30 en el Xiaomi: (a) pregunta del FAQ **sin** `¿` y
  sin acentos → respuesta verbatim instantánea, sin "Escribiendo..." (no
  llamó al LLM); (b) parafraseo distinto → sigue yendo al modelo; (c) con
  `consultar_faq` ya no aparece la respuesta improvisada además del texto
  verbatim.

### Hallazgo (2026-08-30): DeepSeek devuelve `content` vacío de forma intermitente

En pruebas de dispositivo, `alta_articulo` en un intercambio multi-turno
falló con "La IA devolvió una respuesta con formato inválido". El
diagnóstico agregado (`LlmClient` loguea `finish_reason` + `usage` +
contenido crudo cuando no parsea) mostró que DeepSeek `deepseek-chat` en
modo `response_format: json_object` a veces devuelve `content` vacío
(`""`), que el parser interpretaba como EOF. Es intermitente (el mismo
pedido, reintentado, funcionó) y preexistente — la Parte 16 ya lo
documenta como el motivo de `FORMATO_SALIDA_ACCIONES`. La Parte 20 alarga
el prompt de sistema (~3 KB por `instruccionFaq`), lo que podría subir la
frecuencia, pero no es una regresión dura (no falla de forma sistemática).

Cambios hechos en esta Parte a raíz del hallazgo (`ia/LlmClient.kt`,
`data/remote/dto/ChatCompletionDto.kt`):
- `ChatCompletionChoiceDto.finishReason` y `ChatCompletionResponseDto.usage`
  (`prompt_tokens` / `completion_tokens`) — nullable, `ignoreUnknownKeys`.
- `LlmClient.chatEstructurado` distingue **contenido vacío** (→ "El
  proveedor no devolvió ninguna respuesta", antes daba el engañoso
  "formato inválido") de **JSON mal formado**, y loguea `finish_reason` +
  `usage` en ambos casos. `LlmClientTest` cubre los dos.

Robustez del reintento / `max_tokens` explícito → Backlog.

### Decisiones abiertas

- [x] Almacén vectorial: ¿embeddings + índice on-device, o un servicio
  remoto (pgvector, u otro)? **Decidido** (2026-08-29): ninguno. El corpus
  real (~4 KB de ayuda) no justifica un índice vectorial ni una
  dependencia de modelo de embeddings on-device (25-100 MB) — CLAUDE.md
  §"no sobre-ingeniería". Se inyecta la lista de preguntas al prompt y el
  modelo elige por número.
- [x] Proveedor de embeddings: ¿el mismo de IA (Parte 14) u otro
  dedicado? **Decidido** (2026-08-29): no aplica — no se usan embeddings en
  esta Parte.
- [x] Fuente de documentación externa a combinar con `docs/`. **Decidido**
  (2026-08-29): ninguna fuente externa. El contenido de `faq.jsonl` es
  curado a mano a partir de `res/raw/faq.md` + `CLAUDE.md` + Partes 6-19 de
  este plan.
- [x] Alcance de la Parte: ¿reemplaza el FAQ estático de la Parte 16 o
  convive con él? **Decidido** (2026-08-29): lo reemplaza por completo.
  `res/raw/faq.md` se elimina; `faq.jsonl` es la única fuente, y sigue
  sirviendo como fallback sin conexión (renderizada legible por
  `FaqRepository`).
- [x] Embeddings on-device (MiniLM / EmbeddingGemma int8) para match por
  similaridad. **Decidido** (2026-08-29): fase futura, fuera del alcance de
  esta Parte (ver "Trabajo futuro" abajo).

### Trabajo futuro (fuera del alcance de esta Parte)

- Embeddings on-device (MiniLM / EmbeddingGemma int8) para elegir la
  entrada del FAQ por similaridad de coseno en vez de inyectar la lista al
  prompt — útil solo si el FAQ crece hasta un tamaño donde la lista de
  preguntas encarezca el prompt.
- Indexación RAG de `docs/` completo o de fuentes externas.
- Renderizado Markdown de las respuestas en el chat (hoy los marcadores se
  muestran literales; el proyecto no tiene renderer y CLAUDE.md pide no
  agregar librerías).

---

## Partes 21-29: incorporación de la revisión de código (`docs/review_code.md`)

*(El 2026-08-30 se produjo `docs/review_code.md`, una revisión de todo el
monorepo con 24 hallazgos: 4 tipo A —atender antes de exponer el backend o
distribuir la app—, 11 tipo M —atender pronto— y 9 tipo B —deuda menor—. Estas
Partes trasladan cada hallazgo al plan: cada A es una Parte propia; los M se
agrupan por relación; los B van juntos salvo B-2 —Parte 26— y B-3 —Parte 22—.
`docs/review_code.md` queda como documento fuente. El marcador `<!-- POS-XXX -->`
lo agrega `/jira-sync` al crear cada épica.)*

| Parte | Hallazgos | Módulo |
|---|---|---|
| 21 Autenticación JWT | A-1, M-10 | backend + android |
| 22 Rotación de secretos | A-2, B-3 | raíz / ops |
| 23 Idempotencia de POST de sync | A-3 | backend + contrato |
| 24 Migraciones de Room | A-4 | android |
| 25 Robustez y correctitud del backend | M-1, M-2, M-3, M-5, M-6 | backend (+android) |
| 26 Despliegue y CI del backend | M-4, M-11, B-2 | backend + transversal |
| 27 Navigation-Compose | M-7 | android |
| 28 Android: logging y agregaciones | M-8, M-9 | android |
| 29 Deuda técnica menor | B-1, B-4, B-5, B-6, B-7, B-8, B-9 | ambos + docs |

---

## Parte 21: Enforcement de autenticación JWT en el backend  <!-- POS-97 -->

*(Cierra el pendiente ya anotado en `docs/api-contract.md` §13, "Enforcement del
header `Authorization: Bearer`…"; se mueve ahí a implementado al terminar esta
Parte. Hallazgos A-1 y M-10 de `docs/review_code.md`.)*

Hoy `create_access_token` (`backend/app/security.py`) emite un JWT en
`POST /auth/login`, pero ningún endpoint lo valida: `backend/app/` no tiene
`jwt.decode` ni dependencia de autenticación, así que todo salvo `/auth/login` y
`/health` está abierto — incluido `POST /usuarios` y `POST /roles`, con lo que
cualquiera con acceso de red al puerto 8000 puede crear un usuario administrador.
El enforcement de permisos por módulo (M-10) hoy vive solo en la UI de Android
(`HelloScreen` oculta botones, `HelloViewModel.onIntentoNavegar` valida); sin
identidad verificada en el servidor no hay barrera real, así que se resuelve en
esta misma Parte.

Objetivo: toda ruta fuera de `/auth/*` y `/health` exige
`Authorization: Bearer <access_token>` válido; el cliente Android lo adjunta en
modo REMOTO; los endpoints sensibles chequean el módulo del rol del usuario
autenticado. Modo LOCAL / LOCAL_CON_SINCRONIZACION no cambia (no hay backend que
valide).

### Checklist

**1. Contrato** (POS-98)
- [ ] `docs/api-contract.md` §12/§13 actualizado: header
  `Authorization: Bearer <access_token>` obligatorio fuera de `/auth/*` y
  `/health`; `401` sin token o con token inválido/expirado; `403` para usuario
  autenticado sin permiso de módulo. El ítem de §13 pasa de "pendiente" a
  "implementado". Criterio: sección revisada y aprobada antes de tocar código
  (CLAUDE.md §9). `needs-approval`
- [ ] Ejemplos de request/response de `401` y `403` en el formato
  `{ "detail": ... }` de §12.

**2. Dependencia de auth en FastAPI** (POS-99)
- [ ] `usuario_actual` (dependencia): `HTTPBearer` -> `jwt.decode` con
  `JWT_SECRET_KEY`/`JWT_ALGORITHM` -> carga `Usuario` por `sub`, `deleted_at IS
  NULL` y `activo`. `401` en cualquier fallo (token ausente, firma inválida,
  expirado, usuario inexistente/inactivo). Criterio: `pytest` de la dependencia
  aislada (token válido, expirado, firma mala, usuario borrado).
- [ ] Aplicada como `dependencies=[Depends(usuario_actual)]` a nivel de
  `APIRouter` en todos los routers salvo `auth` y `health`. Criterio: `pytest` -
  cada endpoint existente gana un caso `401` sin header; los happy-path
  existentes se ajustan para enviar el header (fixture `client_autenticado`).
- [ ] `pytest` completo en verde tras el ajuste de todos los tests existentes.
  `jvm-tests` (backend: mostrar salida de `pytest`).

**3. Cliente Android adjunta el token** (POS-100)
- [ ] `Session`/`SessionManager` conservan el `accessToken` del login remoto
  (hoy `LocalAuthRepository` devuelve `accessToken = null`; `RemoteAuthRepository`
  ya lo recibe). Interceptor OkHttp en `di/NetworkModule.kt` que agrega
  `Authorization: Bearer` cuando hay sesión con token. Criterio:
  `./gradlew testDebugUnitTest` con el interceptor probado (con token / sin
  token). `jvm-tests`
- [ ] En `401` del backend, la app cierra la sesión y vuelve a `LoginScreen`
  (misma reacción que sesión nula). Criterio: prueba de ViewModel con `401`
  mockeado.

**4. Permisos server-side** (POS-101, absorbe M-10)
- [ ] Los endpoints sensibles (`usuarios`, `roles`, y los de escritura que
  correspondan) verifican que `usuario_actual` tenga el módulo requerido en su
  rol; `403` si no. Criterio: `pytest` - usuario con rol sin `usuarios` recibe
  `403` en `POST /usuarios`.
- [ ] El mapeo endpoint -> módulo requerido queda en un solo lugar, no repetido
  por ruta.

**5. Verificación en dispositivo** (POS-102)
- [ ] Modo REMOTO: login exigido, el header viaja en cada request, un token
  manipulado da `401` y expulsa al login. Modos LOCAL / LOCAL_CON_SINCRONIZACION:
  sin cambios (Room, sin token). `needs-device`

### Decisiones abiertas

- [ ] ¿Modo LOCAL / LOCAL_CON_SINCRONIZACION queda explícitamente exento (no hay
  backend que valide) y solo REMOTO exige token? (propuesta: sí).
- [ ] ¿Se agrega `refresh_token` ahora o se difiere? `docs/api-contract.md` §13
  ya lo lista como genuinamente abierto (propuesta: diferir; mantener el JWT de
  24 h y re-login al expirar).
- [ ] ¿La dependencia de auth se aplica router por router, o como
  `dependencies=` global de la app excluyendo `auth`/`health`? (afecta cómo se
  montan `health` y `auth`).
- [ ] ¿`401` por token expirado se distingue de `401` por credenciales, para que
  la app muestre "sesión expirada" en vez de "credenciales inválidas"?

---

## Parte 22: Rotación de secretos y saneamiento de `.env`  <!-- POS-103 -->

*(Hallazgos A-2 y B-3 de `docs/review_code.md`.)*

`.env` en la raíz del repo contiene un Personal Access Token de GitHub
(`github_pat_...`) y una API key de DeepSeek (`sk-...`) reales, en texto plano. El
archivo está en `.gitignore` y **no aparece en el historial de git** (verificado
en la revisión), pero los secretos quedaron expuestos en el entorno de trabajo y
deben tratarse como comprometidos. Ni `docker-compose.yml` ni el `Dockerfile`
consumen ese `.env`, y no se encontró uso de `API_KEY_DEEPSEEK` en
`backend/app/`. Parte mayormente operativa, poco código.

Objetivo: secretos rotados, `.env` fuera del repo (o reducido a un
`.env.example` sin valores), y la config de despliegue sin secretos por defecto.

### Checklist

**1. Rotación de credenciales** (POS-104)
- [ ] PAT de GitHub revocado y regenerado. Criterio: el token anterior deja de
  autenticar (verificación manual). `needs-device`
- [ ] API key de DeepSeek rotada. Criterio: la key anterior deja de responder;
  la nueva se inyecta por variable de entorno donde se necesite. `needs-device`

**2. Saneamiento del repo** (POS-105)
- [ ] Confirmado si `.env` hace falta en runtime. Si no, se elimina; si sí, se
  reemplaza por `.env.example` sin valores y se documenta en `CLAUDE.md` /
  `README.md` que la inyección es por entorno. Criterio: `git status` limpio y
  ningún secreto real versionado.
- [ ] `.gitignore` revisado para cubrir `.env` en todos los subdirectorios.

**3. Higiene de config de despliegue** (POS-106, = B-3)
- [ ] `docker-compose.yml`: `JWT_SECRET_KEY` explícito por entorno (no el default
  `dev-secret-key-cambiar-en-produccion` de `security.py`); credenciales de
  Postgres fuera de `pdv/pdv` para cualquier entorno no-local; revisada la
  exposición del puerto `5432:5432`. Criterio: `docker compose config` no muestra
  el secreto por defecto ni credenciales triviales para el perfil no-local.

### Decisiones abiertas

- [ ] ¿`.env` se elimina del repo por completo, o se conserva un `.env.example`
  documentado?
- [ ] ¿La config de despliegue no-local se maneja con un `docker-compose` de
  override, variables de entorno del host, o un gestor de secretos? (hoy no hay
  destino de despliegue definido - `CLAUDE.md` §7).

---

## Parte 23: Idempotencia de los POST de sincronización  <!-- POS-107 -->

*(Hallazgo A-3 de `docs/review_code.md`.)*

`POST /ventas`, `POST /entradas`, `POST /cortes-caja` y `POST /retiros-efectivo`
generan un `id` nuevo en el servidor (`default=uuid.uuid4`) e ignoran `local_id`
como clave. Si un dispositivo envía una venta, el servidor la persiste, y la
respuesta se pierde por un corte de red, el reintento del motor de sync crea una
**segunda** venta con el mismo `local_id`. `POST /sync-conflicts` (Parte 19) ya
resuelve esto: usa el `id` generado por el dispositivo y devuelve `200` con la
fila existente en vez de duplicar. Se replica ese patrón en las 4 rutas
transaccionales.

Objetivo: reintentar cualquiera de esos POST con el mismo `local_id` no duplica
datos y devuelve la entidad ya persistida.

### Checklist

**1. Contrato** (POS-108)
- [ ] `docs/api-contract.md` secciones 5 (ventas), 6 (entradas) y 8
  (cortes/retiros): POST idempotente por `local_id`; `201` cuando se crea, `200`
  cuando ya existía (mismo shape de respuesta que `sync-conflicts` §3.2).
  Criterio: secciones revisadas y aprobadas antes de tocar código. `needs-approval`

**2. Backend** (POS-109)
- [ ] En cada uno de los 4 POST: chequeo previo por `local_id` -> si existe,
  `200` con la fila existente sin insertar ni recalcular inventario/movimientos;
  si no, el camino actual. Criterio: `pytest` por ruta - "POST repetido con el
  mismo `local_id` devuelve `200`, no duplica la fila, no vuelve a mover
  inventario".
- [ ] Si se elige el constraint `UNIQUE(local_id)` (ver Decisiones abiertas):
  migración Alembic en las 4 tablas + captura de `IntegrityError`. `schema-parity`
  (JSON de `docs/` + entidad Room + migración Alembic alineados).
- [ ] `pytest` completo en verde. `jvm-tests`

**3. Verificación end-to-end** (POS-110)
- [ ] Sincronización con corte de red simulado entre el commit del servidor y la
  recepción de la respuesta: no se generan duplicados al reintentar.
  `needs-device`

### Decisiones abiertas

- [ ] ¿Pre-chequeo con `db.get(local_id)` (sin cambio de esquema, más simple) o
  `UNIQUE(local_id)` + `IntegrityError` (robusto ante dos reintentos concurrentes,
  requiere migración en 4 tablas)? Propuesta: `UNIQUE(local_id)`.
- [ ] ¿`local_id` pasa a ser obligatorio (no-null) en el body de esos 4 POST, o
  sigue nullable y la idempotencia solo aplica cuando viene?
- [ ] ¿Alcance solo backend, o el motor de sync de Android también debe marcar la
  entidad como sincronizada al recibir el `200` (hoy podría re-encolarla)?

---

## Parte 24: Migraciones de esquema de Room  <!-- POS-111 -->

*(Hallazgo A-4 de `docs/review_code.md`.)*

`di/DatabaseModule.kt` construye `PdvDatabase` con
`fallbackToDestructiveMigration(dropAllTables = true)`, y `PdvDatabase` está en
`version = 7` con `exportSchema = false`. En una app offline-first donde Room
**es** la fuente de verdad, el primer cambio de esquema tras tener datos reales en
un dispositivo borra todo el inventario, ventas y cortes locales de ese equipo.
`exportSchema = false` además impide los tests de migración de Room y deja sin
registro histórico el esquema.

Objetivo: esquema exportado y versionado, migraciones reales a partir de una
línea base, sin `fallbackToDestructiveMigration`, antes de la primera
distribución a un comercio.

### Checklist

**1. Exportar y versionar el esquema** (POS-112)
- [ ] `exportSchema = true` en `@Database` + `room.schemaLocation` configurado en
  `app/build.gradle.kts`; los JSON generados quedan versionados en git. Criterio:
  `./gradlew build` genera `app/schemas/com.pdv.pos.data.local.PdvDatabase/7.json`.

**2. Línea base y migraciones** (POS-113)
- [ ] Definida la línea base (ver Decisiones abiertas) y escrito el andamiaje de
  `Migration` (aunque la primera sea 7 -> 8 en la próxima Parte que toque
  esquema). `fallbackToDestructiveMigration` eliminado; `Room.databaseBuilder`
  registra las migraciones. Criterio: `./gradlew build` en verde y arranque
  limpio sobre una `pdv.db` existente en v7.
- [ ] Test de migración con `MigrationTestHelper` para la primera migración real.
  Criterio: el test aplica la migración y valida el esquema resultante.
  `jvm-tests` o `needs-device` según el runner elegido.

**3. Verificación en dispositivo** (POS-114)
- [ ] Instalar una versión con esquema nuevo sobre una instalación previa con
  datos (ventas, inventario, cortes) y confirmar que se conservan. `needs-device`

### Decisiones abiertas

- [ ] ¿Línea base en la v7 actual (nadie tiene datos de producción que preservar;
  se asume v7 como punto de partida y se escriben migraciones de v7 en adelante),
  o se reconstruye el historial 1 -> 7? Propuesta: línea base v7.
- [ ] Tests de migración: ¿instrumentados (`connectedAndroidTest`, `needs-device`,
  sin dependencia nueva) o Robolectric (corre en JVM, `jvm-tests`, pero es
  dependencia nueva - CLAUDE.md §9)?
- [ ] ¿Se aprovecha esta Parte para dejar `exportSchema` como gate permanente
  (CI que falla si el JSON no está commiteado)? (se cruza con la Parte 26).

---

## Parte 25: Robustez y correctitud del backend  <!-- POS-115 -->

*(Hallazgos M-1, M-2, M-3, M-5 y M-6 de `docs/review_code.md`.)*

Cinco defectos de robustez/correctitud en `backend/app/routers/` y
`backend/app/main.py` que conviene resolver en una sola pasada. M-3 puede
producir una diferencia de caja silenciosa: `caja.get_totales_corte` filtra por
`metodo_pago == "efectivo"` / `"tarjeta"` y `estado == "completada"` con
literales exactos, pero `VentaCreateSchema` deja `metodo_pago` y `estado` como
`str` libres, así que una venta con `metodo_pago = "Efectivo"` (u otra variante)
queda fuera del total de efectivo del corte.

### Checklist

**1. Concurrencia e integridad de inventario** (POS-116, M-1 + M-6)
- [ ] Decremento/incremento de inventario en `ventas.create_venta` y
  `entradas.create_entrada` sin read-modify-write en Python: `select(...)
  .with_for_update()` dentro de la transacción, o `UPDATE inventario SET cantidad
  = cantidad - :n WHERE ...` atómico (mismo principio de "delta con signo" que ya
  siguen). Criterio: `pytest` con dos requests concurrentes para el mismo
  `(sucursal, articulo)` no pierde un decremento.
- [ ] `entradas.create_entrada` captura `IntegrityError` cuando `articulo_nuevo`
  colisiona con el `UNIQUE` de `sku`/`codigo_barras` -> `409` (hoy -> `500`),
  con `rollback` (mismo patrón que `usuarios`/`roles`). Criterio: `pytest` -
  `sku` duplicado devuelve `409`.

**2. Validación de dominio** (POS-117, M-2 + M-3)
- [ ] `create_venta` valida los artículos de las líneas con un único
  `select(Articulo.id).where(Articulo.id.in_(ids))` + diferencia de conjuntos,
  en vez de N `await db.get` en un `for`. Criterio: `pytest` happy path y "línea
  con `articulo_id` inexistente -> `404`" siguen pasando.
- [ ] `metodo_pago` y `estado` de `VentaCreateSchema` pasan a `Literal[...]`
  (o `enum.StrEnum`) con los valores que usan las queries de `caja`
  (`efectivo`/`tarjeta`; `completada`/...). `schema-parity`: alineado con
  `docs/schema-pos.json`, el modelo SQLAlchemy y las constantes de Android
  (`METODO_PAGO_EFECTIVO` / `METODO_PAGO_TARJETA` en `LocalCajaRepository`,
  `estado` de venta). Criterio: `pytest` - `metodo_pago` inválido -> `422`;
  el total de efectivo del corte incluye todas las ventas en efectivo.
- [ ] `pytest` completo en verde. `jvm-tests`

**3. Manejo de errores centralizado** (POS-118, M-5)
- [ ] `@app.exception_handler` global que devuelve el shape `{ "detail": ... }`
  de `docs/api-contract.md` §12 para excepciones no previstas, con log a nivel
  `ERROR` (CLAUDE.md §4). Criterio: `pytest` - una excepción no prevista devuelve
  JSON consistente, no traceback.
- [ ] `CORSMiddleware` agregado en `app/main.py`. Criterio: preflight `OPTIONS`
  responde con los headers CORS.

### Decisiones abiertas

- [ ] M-3: ¿`enum.StrEnum` en Pydantic (y constantes equivalentes en Kotlin), o
  solo `Literal[...]` en Pydantic reutilizando las constantes que Android ya
  tiene? Propuesta: `Literal` + constantes existentes, sin infra nueva.
- [ ] ¿Qué valores válidos tiene `estado` de venta además de `completada`
  (`cancelada`, `devuelta`)? Confirmar antes de fijar el `Literal`.
- [ ] `CORSMiddleware`: ¿orígenes `*` por ahora (no hay cliente web), o lista
  explícita vacía hasta que exista uno?

---

## Parte 26: Despliegue y CI del backend  <!-- POS-119 -->

*(Hallazgos M-4, M-11 y B-2 de `docs/review_code.md`. M-11 ya figura como
pendiente en `CLAUDE.md` §7.)*

`backend/Dockerfile` hace `COPY app ./app` únicamente: sin `alembic/` ni
`alembic.ini` en la imagen, el contenedor no puede correr `alembic upgrade head`
(M-4), así que hoy las migraciones dependen de un venv en el host. No hay CI
(M-11): con ~386 pruebas entre ambos módulos, un workflow por módulo cerraría de
paso el gate `schema-parity` de forma automática. Y la suite de backend crea el
esquema con `Base.metadata.create_all` en vez de las migraciones (B-2), así que
una divergencia modelo/migración pasa verde.

### Checklist

**1. Migraciones dentro de la imagen** (POS-120, M-4)
- [ ] `Dockerfile` copia `alembic/` + `alembic.ini`; `alembic` disponible en la
  imagen. Migración vía entrypoint (`alembic upgrade head` antes de `uvicorn`) o
  servicio/job separado en `docker-compose.yml`. Criterio: `docker compose up`
  sobre una base vacía deja el esquema aplicado sin intervención del host.

**2. Fixture de test por migraciones** (POS-121, B-2)
- [ ] `tests/conftest.py` deja de usar `Base.metadata.create_all` y aplica
  `alembic upgrade head` sobre una base de test dedicada (no la de desarrollo).
  Criterio: `pytest` en verde con el nuevo fixture; un modelo cambiado sin su
  migración correspondiente rompe la suite. `jvm-tests`
- [ ] Test explícito de paridad esquema-migraciones (`alembic check` /
  comparación de `Base.metadata` contra el resultado de las migraciones).

**3. Workflow de CI backend** (POS-122)
- [ ] GitHub Actions: `pytest` + `alembic upgrade head` + `alembic check` contra
  un Postgres de servicio. Criterio: el workflow corre en verde en un PR de
  prueba y falla si `pytest` o `alembic check` fallan.

**4. Workflow de CI Android** (POS-123)
- [ ] GitHub Actions: `./gradlew test lint` (y verificación de que los JSON de
  esquema de Room están commiteados, si la Parte 24 ya está). Criterio: verde en
  un PR de prueba.

### Decisiones abiertas

- [ ] ¿Migración en el entrypoint del contenedor de la app, o job separado
  (`depends_on` + `restart: on-failure`)? Propuesta: job separado (una sola
  ejecución, sin condición de carrera si hay varios workers).
- [ ] ¿CI en un solo archivo de workflow con dos jobs, o un archivo por módulo?
  `CLAUDE.md` §7 sugiere uno por módulo.
- [ ] ¿El CI de backend levanta Postgres como service container, o usa SQLite
  para los tests (implicaría quitar `JSONB`/`ARRAY` específicos de Postgres)?
  Propuesta: service container Postgres.

---

## Parte 27: Navegación con Navigation-Compose  <!-- POS-124 -->

*(Hallazgo M-7 de `docs/review_code.md`.)*

`MainActivity` usa un `enum Pantalla` + `when` hecho a mano: no hay pila de
navegación (cada pantalla vuelve a `HELLO` con un `onBack` fijo), el botón físico
de atrás del sistema cierra la app en vez de navegar, y "volver" desde `ROLES` va
siempre a `USUARIOS` aunque se haya entrado desde otro lado. La dependencia
`androidx.hilt:hilt-navigation-compose` ya está; falta
`androidx.navigation:navigation-compose` (dependencia nueva - CLAUDE.md §9).

Objetivo: back stack real y navegación declarativa, sin cambiar el
comportamiento de los ViewModels ni de `AsistenteIaWidget`.

### Checklist

**1. Grafo de navegación** (POS-125)
- [ ] `NavHost` con una ruta por pantalla (las 11 de `Pantalla`).
  `AsistenteIaWidget` sigue montado una sola vez fuera del `NavHost` (como hoy
  fuera del `when`), con la misma instancia de `ChatViewModel` durante la vida de
  la Activity. Criterio: `./gradlew build` en verde; todas las pantallas
  navegables desde donde lo eran antes.
- [ ] El gate de permiso por pantalla (hoy en `HelloViewModel.onIntentoNavegar` /
  `ConfiguracionViewModel.onIntentoAbrirConflictos`) se conserva. Criterio:
  pruebas de esos ViewModels sin cambios de comportamiento.

**2. Back stack real** (POS-126)
- [ ] El botón de atrás del sistema navega hacia atrás en el stack; se retiran
  los `onBack: () -> Unit` fijos de cada `*Screen` en favor del `NavController`.
  Criterio: `./gradlew testDebugUnitTest` en verde; revisión manual del stack
  (ej. `USUARIOS -> ROLES -> atrás` vuelve a `USUARIOS`; desde `HELLO`, atrás
  sale de la app).

**3. Verificación en dispositivo** (POS-127)
- [ ] Recorrer las 11 pantallas y el botón de atrás en cada una; el widget de IA
  sigue accesible desde todas. `needs-device`

### Decisiones abiertas

- [ ] ¿Nueva dependencia `androidx.navigation:navigation-compose` aprobada?
  (CLAUDE.md §9).
- [ ] ¿El destino actual debe sobrevivir a muerte de proceso como hoy
  (`rememberSaveable`)? `NavHost` lo maneja vía `rememberNavController` +
  `SavedStateHandle`; confirmar que el comportamiento observable no cambia.
- [ ] ¿Se aprovecha para introducir rutas con argumento (ej. detalle) o el
  alcance es 1:1 con las pantallas actuales? Propuesta: 1:1, sin ampliar alcance.

---

## Parte 28: Android - logging de red y agregaciones numéricas  <!-- POS-128 -->

*(Hallazgos M-8 y M-9 de `docs/review_code.md`.)*

Dos mejoras de Android independientes. `di/NetworkModule.kt` agrega
`HttpLoggingInterceptor(Level.BASIC)` de forma incondicional, también en builds
de release (M-8) - `BASIC` no registra cuerpos ni headers, pero no debería estar
en release. Y `Converters` guarda `BigDecimal` como TEXT (`toPlainString`):
correcto para precisión, pero implica que no se puede `SUM()` / `ORDER BY` /
comparar cantidades en SQL de forma fiable, así que `LocalCajaRepository.
calcularTotales` suma en Kotlin y `EjecutorAccionesIa.todosLosItems` pagina
**todo** el inventario a memoria (bucle de 100 en 100) para responder
`consultar_stock` / `exportar_inventario` (M-9) - escala mal con catálogos
grandes.

### Checklist

**1. Logging de red solo en debug** (POS-129, M-8)
- [ ] `HttpLoggingInterceptor` gateado con `BuildConfig.DEBUG` en
  `di/NetworkModule.kt` (el cliente de IA, `LlmNetworkModule`, ya no lo tiene -
  no se toca). Criterio: revisión de que el build de release no incluye el
  interceptor; `./gradlew testDebugUnitTest` en verde. `jvm-tests`

**2. Agregaciones numéricas de inventario** (POS-130, M-9)
- [ ] Elegido el enfoque (ver Decisiones abiertas) e implementado el total de
  stock y el orden por cantidad sin traer todo el catálogo a memoria.
  `EjecutorAccionesIa.ejecutarConsultarStock` y el camino de exportación usan la
  nueva ruta. Criterio: `jvm-tests` de la nueva agregación; comprobación
  aproximada de que el uso de memoria no crece con el tamaño del catálogo.
- [ ] `LocalCajaRepository.calcularTotales` revisado bajo el mismo criterio (o
  documentado por qué se deja como está). Criterio: `./gradlew testDebugUnitTest`
  en verde.

### Decisiones abiertas

- [ ] M-9: ¿columna numérica paralela en las entidades Room (para `SUM` / `ORDER
  BY` / comparación en SQL, manteniendo la de TEXT para exactitud), o mover
  `consultar_stock` y los totales de stock al backend cuando el modo lo permita
  (análogo a `GET /cortes-caja/totales`, que ya agrega en Postgres con
  `Numeric`)? Propuesta: columna paralela, para no crear dependencia de red en
  una consulta de solo lectura que hoy funciona offline.
- [ ] ¿M-8 y M-9 se mantienen en una sola Parte, o M-9 (diseño de esquema) se
  separa de M-8 (cambio trivial)?

---

## Parte 29: Deuda técnica menor (varios)  <!-- POS-131 -->

*(Hallazgos tipo B de `docs/review_code.md`, salvo B-2 -Parte 26- y B-3
-Parte 22-. Cada grupo del checklist es independiente y se puede cerrar en
cualquier orden.)*

### Checklist

**1. Config de build del backend** (POS-132)
- [ ] B-1: versiones fijas en `backend/requirements.txt` (`==` o lockfile con
  `uv`/`pip-tools`). Criterio: `pip install -r requirements.txt` resuelve el
  mismo set en dos entornos.
- [ ] B-4: usuario no privilegiado en `backend/Dockerfile` (`adduser` + `USER`).
  Criterio: `docker run ... whoami` no devuelve `root`.

**2. Android menor** (POS-133)
- [ ] B-5: `DynamicHostInterceptor` cachea `deviceConfig` (ej. `StateFlow` en el
  interceptor) en vez de `runBlocking { preferences.deviceConfig.first() }` por
  request. Criterio: `./gradlew testDebugUnitTest` en verde; el `runBlocking`
  repetido desaparece.
- [ ] B-6: `Converters.toModulosPermitidos` / `fromModulosPermitidos` con un
  separador que no pueda aparecer en una clave de módulo (o JSON). Criterio:
  prueba con una clave que contenga el separador antiguo.
- [ ] B-7: `isMinifyEnabled = true` en el build de release + reglas Proguard para
  Room/Hilt/kotlinx-serialization/Retrofit. Criterio: `./gradlew assembleRelease`
  en verde y la app funciona (verificación en dispositivo). `needs-device`
- [ ] B-8: esquema definido para `versionCode`/`versionName` por release (hoy
  fijos en `1` / `"0.1"` tras 20 Partes). Criterio: documentado en `CLAUDE.md`
  §7 o en el build.

**3. Documentación** (POS-134)
- [ ] B-9: archivar las Partes ya migradas a `CLAUDE.md` en
  `docs/PLAN-historico.md`, dejando en `docs/PLAN.md` solo lo activo; actualizar
  el conteo "16 Partes" / "Partes 2-16" del encabezado de `PLAN.md` (líneas
  10-18), que quedó desactualizado. Criterio: `PLAN.md` más corto y su encabezado
  coherente con el número real de Partes.

### Decisiones abiertas

- [ ] B-7 (R8/shrinking): ¿en el alcance ahora, o se difiere hasta que haya
  distribución real? Activarlo obliga a mantener reglas Proguard de varias
  librerías y a re-verificar en dispositivo cada release.
- [ ] B-9: ¿el archivo histórico es `docs/PLAN-historico.md`, o se mueven las
  Partes cerradas a `CLAUDE.md` como bitácora? Propuesta: `docs/PLAN-historico.md`
  (CLAUDE.md debe quedar enfocado - CLAUDE.md §1).

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
  nueva se implementó en esa sesión.
