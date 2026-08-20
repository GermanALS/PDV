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
