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
remoto/contrato → wiring). Las 16 Partes tienen su propia sección
`### Checklist` con ítems verificables y criterios de éxito concretos
(comando, archivo, o comportamiento observable — nunca "pruebas
exhaustivas"). La Parte 3 (esquema de datos, `docs/schema-pos.json`) quedó
aprobada el 2026-08-18; el siguiente paso es la Parte 4 (login ficticio).

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

## Parte 7: Módulo Venta de mostrador

Escanear un barcode con la cámara o ingresar la descripción del producto, y
mostrar sus datos principales alineados al esquema de la Parte 3.

### Checklist

**1. UI**
- [ ] Propuesta de pantalla (campos, tipo, layout) presentada y aprobada
  por el usuario — criterio: aprobación explícita registrada antes de
  escribir código.
- [ ] Composable implementado con datos estáticos de ejemplo (mínimo 5
  campos del producto) — criterio: `./gradlew build` pasa y la pantalla es
  navegable desde el menú principal.
- [ ] Instalado y verificado corriendo en el Xiaomi (o emulador fallback)
  — criterio: `./gradlew installDebug` + confirmación manual de que la
  pantalla se ve con los datos de ejemplo. `needs-device`

**2. Repositorio local**
- [ ] `VentaRepository` (interfaz) en `domain/repository/` — criterio:
  compila sin referencias a Room ni Retrofit.
- [ ] `LocalVentaRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` pasa para sus pruebas. `jvm-tests`
- [ ] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger con la categoría
  correcta.
- [ ] Pruebas unitarias (JUnit5 + MockK) del happy path + 1 caso de error
  (CLAUDE.md §6) — criterio: `./gradlew testDebugUnitTest` en verde.

**3. Repositorio remoto**
- [ ] `docs/api-contract.md` actualizado con los endpoints de `ventas`
  antes de tocar código (CLAUDE.md §9) — criterio: sección nueva en el
  contrato, revisada por el usuario.
- [ ] Ruta FastAPI (`app/routers/ventas.py`) — criterio: `pytest
  backend/tests/test_ventas.py` en verde, happy path + 1 error (CLAUDE.md
  §6).
- [ ] `RemoteVentaRepository` (Retrofit) en `data/remote/` — criterio:
  `./gradlew testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests`
- [ ] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout y verifica la llamada al
  logger.

**4. Wiring**
- [ ] `ViewModel` conectado a los casos de uso reales según `BackendMode`
  (Parte 6) — criterio: prueba de `ViewModel` con estados mockeados
  (CLAUDE.md §6) confirma que resuelve el repositorio correcto según el
  modo.
- [ ] Modo local-con-sincronización trata las ventas como eventos aditivos
  (política Parte 6) — criterio: prueba de integración que sincroniza dos
  ventas concurrentes desde dos dispositivos y verifica que ambas se
  aplican (no que una sobreescribe a la otra). Aquí se cierra la
  validación de la rama "eventos aditivos" del motor de sync, diferida
  desde la Parte 6.
- [ ] Verificado end-to-end en el Xiaomi en los tres modos (local / remoto
  / local-con-sync) — `needs-device`

### Decisiones abiertas

- [ ] Librería de escaneo de barcode y manejo del permiso de cámara
  (`CLAUDE.md` §9: no introducir dependencias de terceros sin señalarlo).
- [ ] Modelado de la venta como evento aditivo: si el evento incluye el
  decremento de inventario, o si el inventario se deriva de la suma de
  ventas. Depende de la decisión de cantidades de la Parte 6.

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 8: Módulo Entrada de mercancía

Simular la entrada de artículos nuevos (agregándolos al maestro de datos y
luego al inventario) y de artículos existentes (agregándolos solo al
inventario), con los datos principales del artículo y el estante de
almacenamiento. Toda entrada queda asociada al usuario en turno (Parte 4).

### Checklist

**1. UI**
- [ ] Propuesta de pantalla (artículo nuevo vs. existente, estante de
  almacenamiento) presentada y aprobada — criterio: aprobación explícita
  registrada antes de implementar.
- [ ] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable.
- [ ] Instalado y verificado en el Xiaomi — `needs-device`

**2. Repositorio local**
- [ ] `EntradaRepository` (interfaz) en `domain/repository/` — criterio:
  compila sin Room ni Retrofit.
- [ ] `LocalEntradaRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests`
- [ ] Cada entrada queda asociada al `usuario` en turno (Parte 4) —
  criterio: prueba unitaria verifica que el registro guarda el usuario de
  la sesión activa.
- [ ] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger.
- [ ] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde.

**3. Repositorio remoto**
- [ ] `docs/api-contract.md` actualizado con los endpoints de
  `entradas`/`movimientos` (CLAUDE.md §9) — criterio: sección nueva,
  revisada.
- [ ] Ruta FastAPI (`app/routers/entradas.py`) — criterio: `pytest
  backend/tests/test_entradas.py` en verde.
- [ ] `RemoteEntradaRepository` (Retrofit) — criterio: `./gradlew
  testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests`
- [ ] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout.

**4. Wiring**
- [ ] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto.
- [ ] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 9: Módulo Inventario

Consultar el inventario, la existencia de artículos específicos, y
modificar los atributos de esos artículos consultados.

### Checklist

**1. UI**
- [ ] Propuesta de pantalla (consulta de existencias, edición de
  atributos) presentada y aprobada — criterio: aprobación explícita
  registrada antes de implementar.
- [ ] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable.
- [ ] Instalado y verificado en el Xiaomi — `needs-device`

**2. Repositorio local**
- [ ] `InventarioRepository` (interfaz) en `domain/repository/` —
  criterio: compila sin Room ni Retrofit.
- [ ] `LocalInventarioRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests`
- [ ] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger.
- [ ] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde.

**3. Repositorio remoto**
- [ ] `docs/api-contract.md` actualizado con los endpoints de
  `inventario` (CLAUDE.md §9) — criterio: sección nueva, revisada.
- [ ] Ruta FastAPI (`app/routers/inventario.py`) — criterio: `pytest
  backend/tests/test_inventario.py` en verde.
- [ ] `RemoteInventarioRepository` (Retrofit) — criterio: `./gradlew
  testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests`
- [ ] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout.

**4. Wiring**
- [ ] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto.
- [ ] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 10: Módulo Caja

Realizar un corte de caja de lo vendido en un periodo especificado.

### Checklist

**1. UI**
- [ ] Propuesta de pantalla (corte por periodo especificado) presentada
  y aprobada — criterio: aprobación explícita registrada antes de
  implementar.
- [ ] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable.
- [ ] Instalado y verificado en el Xiaomi — `needs-device`

**2. Repositorio local**
- [ ] `CajaRepository` (interfaz) en `domain/repository/` — criterio:
  compila sin Room ni Retrofit.
- [ ] `LocalCajaRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests`
- [ ] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger.
- [ ] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde.

**3. Repositorio remoto**
- [ ] `docs/api-contract.md` actualizado con los endpoints de
  `cortes_caja` (CLAUDE.md §9) — criterio: sección nueva, revisada.
- [ ] Ruta FastAPI (`app/routers/caja.py`) — criterio: `pytest
  backend/tests/test_caja.py` en verde.
- [ ] `RemoteCajaRepository` (Retrofit) — criterio: `./gradlew
  testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests`
- [ ] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout.

**4. Wiring**
- [ ] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto.
- [ ] Los cortes de caja se tratan como eventos aditivos en modo
  local-con-sincronización (política Parte 6) — criterio: prueba de
  integración sincroniza dos cortes concurrentes desde dos dispositivos y
  verifica que ambos se aplican.
- [ ] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 11: Módulo Devoluciones

Registrar devoluciones de clientes y consultar la lista de productos en
esta condición para gestionar la devolución con el proveedor.

### Checklist

**1. UI**
- [ ] Propuesta de pantalla (registrar devolución, consultar lista para
  gestión con proveedor) presentada y aprobada — criterio: aprobación
  explícita registrada antes de implementar.
- [ ] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable.
- [ ] Instalado y verificado en el Xiaomi — `needs-device`

**2. Repositorio local**
- [ ] `DevolucionRepository` (interfaz) en `domain/repository/` —
  criterio: compila sin Room ni Retrofit.
- [ ] `LocalDevolucionRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests`
- [ ] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger.
- [ ] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde.

**3. Repositorio remoto**
- [ ] `docs/api-contract.md` actualizado con los endpoints de
  `devoluciones` (CLAUDE.md §9) — criterio: sección nueva, revisada.
- [ ] Ruta FastAPI (`app/routers/devoluciones.py`) — criterio: `pytest
  backend/tests/test_devoluciones.py` en verde.
- [ ] `RemoteDevolucionRepository` (Retrofit) — criterio: `./gradlew
  testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests`
- [ ] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout.

**4. Wiring**
- [ ] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto.
- [ ] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 12: Módulo Administración de usuarios (demo)

CRUD de administrador y encargados de turno, y asignación de turnos. Este
CRUD persiste usuarios y turnos de verdad (repositorio local/remoto, como
los demás módulos); lo que se difiere a la Parte 13 es la aplicación real
de permisos por rol sobre los demás módulos.

### Checklist

**1. UI**
- [ ] Propuesta de pantalla (CRUD de administrador/encargados de turno,
  asignación de turnos) presentada y aprobada — criterio: aprobación
  explícita registrada antes de implementar.
- [ ] Composable implementado con datos estáticos de ejemplo — criterio:
  `./gradlew build` pasa y la pantalla es navegable.
- [ ] Instalado y verificado en el Xiaomi — `needs-device`

**2. Repositorio local**
- [ ] `UsuarioRepository` (interfaz) en `domain/repository/` — criterio:
  compila sin Room ni Retrofit.
- [ ] `LocalUsuarioRepository` (Room) en `data/local/` — criterio:
  `./gradlew testDebugUnitTest` en verde. `jvm-tests`
- [ ] Esquema de `Usuario` reserva el campo de contraseña hasheada que la
  Parte 13 va a usar (aunque todavía no se valida contra él) — criterio:
  campo presente en la entidad Room, sin lógica de login real todavía.
- [ ] Cada escritura registra `DB_WRITE` en el log (Parte 5) — criterio:
  prueba unitaria verifica la invocación al logger.
- [ ] Pruebas unitarias del happy path + 1 caso de error (CLAUDE.md §6) —
  criterio: `./gradlew testDebugUnitTest` en verde.

**3. Repositorio remoto**
- [ ] `docs/api-contract.md` actualizado con los endpoints de `usuarios`
  (CLAUDE.md §9) — criterio: sección nueva, revisada.
- [ ] Ruta FastAPI (`app/routers/usuarios.py`) — criterio: `pytest
  backend/tests/test_usuarios.py` en verde.
- [ ] `RemoteUsuarioRepository` (Retrofit) — criterio: `./gradlew
  testDebugUnitTest` pasa mockeando Retrofit. `jvm-tests`
- [ ] Fallas de red registran `ERROR`/`WARN` en el log (Parte 5) —
  criterio: prueba unitaria simula timeout.

**4. Wiring**
- [ ] `ViewModel` conectado según `BackendMode` (Parte 6) — criterio:
  prueba con estados mockeados confirma el repositorio correcto.
- [ ] Verificado end-to-end en el Xiaomi en los tres modos — `needs-device`

Realiza pruebas de integración exhaustivas.

---

## Parte 13: Gestión de usuarios (real)

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

**1. Roles y permisos**
- [ ] Roles personalizados (Administrador, encargado de turno, y
  adicionales configurables) implementados — criterio: CRUD de roles con
  `pytest backend/tests/test_roles.py` y prueba Android en verde.
- [ ] Permisos por rol y activación/desactivación de módulos según esos
  permisos — criterio: prueba de integración confirma que un usuario sin
  permiso no ve/usa el módulo restringido.

**2. Reemplazo del login ficticio**
- [ ] Campo de contraseña hasheada agregado al esquema de `Usuario` —
  criterio: migración de Alembic aplicada, campo presente.
- [ ] `POST /auth/login` real, documentado primero en
  `docs/api-contract.md` (CLAUDE.md §9) — criterio: `pytest
  backend/tests/test_auth.py` en verde, valida credenciales contra
  `UsuarioRepository` y emite `access_token`.
- [ ] Credenciales ficticias (`admin`/`password`, `user1`/`password`)
  dejan de aceptarse — criterio: prueba de integración confirma rechazo.
- [ ] Cliente Android usa el login real en vez del hardcodeado de la
  Parte 4 — criterio: prueba de `ViewModel` con estados mockeados.

**3. Logging**
- [ ] Acciones rechazadas por falta de permiso se registran en el log
  (Parte 5, categoría `AUTH`) — criterio: prueba unitaria verifica la
  invocación al logger con la categoría correcta.

**4. Verificación**
- [ ] Instalado y verificado end-to-end en el Xiaomi (login real,
  permisos aplicados) — `needs-device`

### Decisiones abiertas

- [ ] Autenticación en modo local: no existe backend que emita
  `access_token`, así que el login debe validar contra Room usando la
  contraseña hasheada. Definir si es un segundo camino de autenticación
  explícito o una implementación local de la misma interfaz de sesión.
- [ ] Librería de hashing de contraseñas y dónde se calcula el hash: solo
  backend, solo dispositivo, o ambos con el mismo algoritmo para que un
  usuario creado en modo local pueda sincronizarse al remoto sin
  reescribir la credencial.

---

## Parte 14: Conectividad de IA

Permite que el backend realice una llamada de IA a través de DeepSeek.
Prueba la conectividad con una prueba sencilla de "2+2" y asegúrate de que
la llamada de IA funcione.

### Checklist
- [ ] Cliente DeepSeek configurado en el backend con la API key vía
  variable de entorno, nunca hardcodeada — criterio: se lee desde
  `.env`/entorno; `.env` sigue en `.gitignore`.
- [ ] Prueba de conectividad "2+2" recibe respuesta de DeepSeek —
  criterio: `pytest backend/tests/test_deepseek.py` en verde.
- [ ] Manejo de error si DeepSeek no está disponible o la API key es
  inválida — criterio: prueba unitaria del caso de error (CLAUDE.md §6).

---

## Parte 15: Refinamiento de IA

Amplía la llamada al backend para que siempre envíe a la IA información en
formato JSON desde el punto de venta, además de la pregunta del usuario (y
el historial de la conversación). La IA debe responder con salidas
estructuradas que incluyan la respuesta al usuario y, opcionalmente, una
actualización del punto de venta de acuerdo a la instrucción.

**Validación de permisos (obligatoria)**: toda actualización del punto de
venta propuesta por la IA debe validarse contra los permisos del usuario en
turno (Parte 13) antes de aplicarse. Las acciones rechazadas por falta de
permiso se registran en el log (Parte 5, categoría `AUTH`) y se le informa
al usuario en el chat por qué no se ejecutó.

### Checklist

**1. Payload y respuesta estructurada**
- [ ] Backend envía a la IA el estado del punto de venta en JSON, más la
  pregunta y el historial — criterio: prueba backend verifica el shape
  del payload enviado.
- [ ] IA responde con salida estructurada (respuesta al usuario +
  actualización opcional) — criterio: prueba backend valida el schema de
  la respuesta con Pydantic.

**2. Validación de permisos**
- [ ] Toda actualización propuesta por la IA se valida contra los
  permisos del usuario en turno (Parte 13) antes de aplicarse — criterio:
  prueba de integración con un usuario sin permiso confirma el rechazo.
- [ ] Acciones rechazadas se registran en el log (Parte 5, categoría
  `AUTH`) y se informan en el chat — criterio: prueba verifica ambas
  cosas.

### Decisiones abiertas

- [ ] Ruta de escritura de las actualizaciones propuestas por la IA: si
  pasan por el mismo `LocalXRepository`/`RemoteXRepository` —y por tanto
  registran `DB_WRITE` y participan del sync como cualquier otra
  escritura— o si toman un camino aparte.
- [ ] Dónde vive la validación de permisos: el backend propone la
  actualización, pero el catálogo de permisos existe en ambos lados vía
  `UsuarioRepository`. Definir si la validación es de servidor, de
  cliente, o doble.

---

## Parte 16: Chat IA

Agrega un widget flotante a la interfaz de usuario que permita el chat
completo con IA y permita que el LLM actualice el punto de venta basándose
en sus resultados estructurados (Parte 15). Si la IA actualiza el punto de
venta, la interfaz de usuario debe actualizarse automáticamente cuando
aplique.

### Comportamiento por modo y conectividad

- **Modo remoto o local-con-sincronización, con conexión**: chat completo
  disponible, incluyendo actualizaciones al punto de venta (sujetas a
  validación de permisos de la Parte 15).
- **Modo local, con conexión a internet**: la IA puede responder dudas sobre
  funcionalidad de la app, usando el contenido del FAQ empaquetado (ver
  abajo) como contexto para DeepSeek. **No puede realizar cambios en la base
  de datos local del dispositivo** bajo ninguna circunstancia en este modo.
- **Sin conexión a internet** (cualquier modo): el chat con IA no está
  disponible (DeepSeek requiere conexión); se muestra el FAQ estático
  empaquetado como recurso de ayuda.

### FAQ empaquetado

Se genera durante el desarrollo y se empaqueta como recurso de la app
(disponible sin conexión). Sirve como contenido de ayuda estático y como
contexto para la IA en modo local con conexión, mientras el RAG (ver
Backlog) no esté implementado.

### Checklist

**1. Widget de chat**
- [ ] Widget flotante implementado, accesible desde cualquier pantalla —
  criterio: Compose UI Test confirma que aparece y funciona.
- [ ] La UI se actualiza automáticamente cuando la IA modifica el punto
  de venta — criterio: prueba de integración confirma el refresco del
  estado tras una respuesta con actualización.

**2. Comportamiento por modo y conectividad**
- [ ] Modo remoto/local-con-sync + conexión: chat completo con
  actualizaciones sujetas a la Parte 15 — criterio: prueba de
  integración.
- [ ] Modo local + conexión: solo responde dudas de funcionalidad con el
  FAQ empaquetado como contexto, sin poder tocar la base de datos local —
  criterio: prueba de integración confirma que un intento de
  actualización se rechaza en este modo.
- [ ] Sin conexión (cualquier modo): chat deshabilitado, se muestra el
  FAQ estático — criterio: prueba de integración simula sin conexión y
  confirma el fallback.

**3. FAQ empaquetado**
- [ ] FAQ generado y empaquetado como recurso offline de la app —
  criterio: archivo de recurso presente, accesible sin conexión.

**4. Verificación**
- [ ] Instalado y verificado en el Xiaomi los tres escenarios de
  conectividad/modo — `needs-device`

---

## Backlog (trabajo futuro, fuera del alcance actual)

- **RAG para el chat de IA**: indexar `docs/` del proyecto más una fuente de
  documentación externa combinada, para mejorar las respuestas de
  funcionalidad de la app en modo local con conexión, reemplazando el
  contexto de FAQ estático usado en la Parte 16. No se implementa en esta
  fase.
- **Panel de revisión manual de conflictos de sincronización** (mencionado
  en la Parte 6, módulo Configuración).

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
