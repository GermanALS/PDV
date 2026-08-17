# CLAUDE.md

Contexto de proyecto para Claude Code. Este archivo vive en la raíz del monorepo.
Edita las secciones marcadas con `[TODO]` a medida que el proyecto tome forma;
mantén este archivo enfocado (evita que crezca de forma descontrolada).

## 1. Resumen del proyecto

- **Nombre**: PDV (código de proyecto Jira: `POS`; mismo prefijo usado en la
  convención de nombres de builds Android, ej. `pos-hello-debug-v0.1`).
- **Qué hace**: aplicación de punto de venta para comercios con varias
  sucursales — venta de mostrador, control de inventario y entradas de
  mercancía, corte de caja, devoluciones, y administración de usuarios y
  turnos. Funciona completamente offline (Kotlin + Room) y puede
  sincronizarse con un backend FastAPI remoto compartido entre sucursales;
  incluye un asistente de IA (DeepSeek) opcional para consultas y
  actualizaciones asistidas del punto de venta.
- **Estado**: proyecto nuevo, en fase de scaffolding inicial.

## 2. Estructura del repositorio

```
PDV/
├── android/     -> App Android nativa (Kotlin)
├── backend/     -> API FastAPI (Python)
├── scripts/     -> Scripts multiplataforma de arranque/detención del backend
└── docs/
    ├── api-contract.md   -> Contrato de endpoints (fuente de verdad compartida)
    └── PLAN.md            -> Diseño en desarrollo (features no estabilizados aún)
```

Todos los documentos necesarios para planificar y ejecutar este proyecto se
encontrarán en el directorio `docs/`. **Por favor, revisa el documento
`docs/PLAN.md` antes de continuar.**

Los dos módulos (`android/` y `backend/`) son independientes en cuanto a build
system, pero deben mantenerse sincronizados vía `docs/api-contract.md`.
Cuando cambies un endpoint, actualiza primero el contrato y luego ambos lados.

`docs/PLAN.md` documenta features en diseño activo (opciones evaluadas,
decisiones pendientes, fases de implementación). Cuando un feature ahí
descrito se estabiliza, su resumen final se migra a este archivo (CLAUDE.md)
y `docs/PLAN.md` queda como bitácora histórica de esa sección.

## 3. Módulo Android (`android/`)

### Stack técnico
- Kotlin, Jetpack Compose (UI declarativa)
- Arquitectura: MVVM (ViewModel + StateFlow/UiState)
- Inyección de dependencias: Hilt
- Networking: Retrofit + kotlinx.serialization (o Ktor si se decide luego)
- Persistencia local: Room (SQLite) — fuente de datos del modo local/offline
- WorkManager — dispara sincronización diferida en background cuando hay conectividad
- DataStore — guarda la preferencia de `BackendMode` (local/remoto) y estado de sync
- Corrutinas para concurrencia estructurada (nunca `GlobalScope`)
- Testing: JUnit5 + MockK (unitarios), Compose UI Test (instrumentados)

### Convenciones
- Un `ViewModel` por pantalla; el estado se expone como `StateFlow<UiState>` inmutable.
- Los data class de red viven en `data/remote/dto/`; los modelos de dominio en `domain/model/`.
- Nunca lanzar excepciones sin capturar desde la capa de red: usar `Result<T>` o sealed classes (`ApiResult.Success/Error`).
- Nombrar los casos de uso como `VerboSustantivoUseCase` (ej. `GetUserProfileUseCase`).

### Arquitectura offline-first (local + remoto)
La app funciona de forma completa sin conexión a internet (lógica en Kotlin +
Room) y puede sincronizar de forma diferida contra el backend FastAPI cuando
el usuario activa el modo remoto. Reglas:

- La capa de dominio (`domain/`) es agnóstica del origen de datos: los casos
  de uso dependen únicamente de una interfaz de repositorio, nunca de Room o
  Retrofit directamente.
- Estructura por entidad:
  - `domain/repository/ItemRepository.kt` → interfaz
  - `data/local/LocalItemRepository.kt` → implementación 100% Kotlin + Room,
    sin ninguna dependencia de red
  - `data/remote/RemoteItemRepository.kt` → implementación que llama al
    backend FastAPI vía Retrofit
- La implementación inyectada (local o remota) se resuelve en el módulo de
  Hilt según el `BackendMode` guardado en DataStore.
- **Regla estricta**: `data/local/` no debe importar ni depender de nada en
  `data/remote/`. El modo local debe compilar y funcionar de forma
  completamente aislada de la red.
- El diseño detallado del motor de sincronización diferida (resolución de
  conflictos, campos de tracking, fases de implementación) vive en
  `docs/PLAN.md` mientras esté en desarrollo activo.
- **Excepción parcial al patrón repositorio/Room**: en el módulo de
  Configuración (`docs/PLAN.md`, módulo Configuración), `BackendMode`,
  parámetros de conexión, y cuál sucursal está seleccionada son preferencia
  de dispositivo y se persisten directamente en DataStore, sin Room. El
  catálogo de sucursales en sí (`Sucursal`: id, nombre, dirección, activa)
  es dato de dominio y sí sigue el patrón repositorio/Room normal
  (`SucursalRepository` / `LocalSucursalRepository`).

### Paleta de colores ("Recibo")
Identidad visual: tinta sobre papel térmico — sobria, alto contraste,
apropiada para un contexto de facturación/punto de venta. El modo oscuro es
una derivación de la misma identidad ("tinta sobre papel oscuro"), no una
paleta distinta.

| Rol | Claro | Oscuro | Uso |
|---|---|---|---|
| `primary` | `#1B3A5C` | `#8FB4D9` | Botones principales, barra superior, navegación activa |
| `secondary` | `#6B7A8F` | `#A8B4C2` | Elementos secundarios, texto de apoyo, bordes de énfasis medio |
| `tertiary` | `#C9A227` | `#E4C15B` | Acento puntual (ej. badge de turno activo). Un solo uso por pantalla, nunca como fondo amplio |
| `background` | `#F7F5EF` | `#1C1B18` | Fondo de pantalla |
| `surface` | `#FFFFFF` | `#242320` | Tarjetas, superficies elevadas |
| `success` | `#2F7D5D` | `#6FBE99` | Confirmaciones (venta completada, sync exitoso) |
| `error` | `#B23A34` | `#E2726C` | Fallos (error de conexión, conflicto de sync) |

Implementación: usar `lightColorScheme()` / `darkColorScheme()` de Material3
con estos valores para `primary`/`secondary`/`tertiary`/`background`/
`surface`. Material3 no incluye roles de `success` por defecto — extender el
`ColorScheme` con propiedades custom (`val ColorScheme.success` /
`val ColorScheme.onSuccess`) en vez de hardcodear el color en cada
composable. Seguir la convención de nombres de la sección 8 (propiedades no
`const`: `camelCase`).

### Comandos habituales
```bash
cd android
./gradlew build              # compilar
./gradlew test                # tests unitarios
./gradlew connectedAndroidTest  # tests instrumentados (requiere emulador/dispositivo)
./gradlew lint                 # análisis estático
./gradlew assembleDebug        # genera el APK (renombrar según convención {app}-{proposito}-{tipo-build}-v{version}, ej. pos-hello-debug-v0.1)
./gradlew installDebug          # instala el APK en el dispositivo/emulador conectado
```

**Definición de hecho**: un cambio en `android/` no se considera terminado
solo por compilar o pasar revisión textual. Debe quedar instalado y
verificado corriendo en el dispositivo físico Xiaomi M2102J20SG (objetivo
principal de pruebas del entorno actual), o en el emulador de fallback si el
dispositivo no está disponible.

## 4. Backend FastAPI (`backend/`)

### Stack técnico
- Python 3.11+, FastAPI, Pydantic v2 para esquemas/validación
- Servidor: Uvicorn (dev) / Gunicorn+Uvicorn workers (prod)
- ORM: SQLAlchemy 2.0 (async — `DeclarativeBase` + `Mapped`/`mapped_column`)
  + Alembic para migraciones. Motor: PostgreSQL vía `asyncpg`.
- Testing: pytest + httpx (TestClient)

### Convenciones
- Endpoints agrupados por `APIRouter` en `app/routers/`.
- Esquemas de entrada/salida en `app/schemas/` (Pydantic), nunca reutilizar modelos ORM directamente como response_model.
- Manejo de errores centralizado con `HTTPException` + un exception handler global.
- Todas las rutas versionadas bajo `/api/v1/...`.

### Logging
- Módulo `logging` estándar de Python, nunca `print()`.
- Nivel `INFO` por defecto; `ERROR` en excepciones no capturadas por el
  exception handler global.
- Salida a stdout/stderr (el contenedor Docker la captura); sin archivos
  propios ni rotación — es logging operativo del servidor, distinto del
  sistema de auditoría de la app Android (`docs/PLAN.md`, módulo Logs), que
  vive únicamente en el dispositivo.

### Comandos habituales

**Vía scripts (recomendado — empaquetado en Docker, consistente entre SO):**
```bash
# Mac
scripts/start-mac.sh      # levanta el contenedor
scripts/stop-mac.sh       # lo detiene

# Linux
scripts/start-linux.sh
scripts/stop-linux.sh

# Windows
scripts/start-windows.ps1
scripts/stop-windows.ps1
```
Backend disponible en `http://localhost:8000` en los tres casos.

**Alternativa manual (sin Docker, útil para iterar rápido en debug):**
```bash
cd backend
python -m venv .venv && .venv\Scripts\activate     # Windows
pip install -r requirements.txt
uvicorn app.main:app --reload                       # levantar en local
pytest                                                # correr tests
```

## 5. Contrato de API (`docs/api-contract.md`)

Antes de generar código en cualquiera de los dos lados, Claude Code debe
verificar/actualizar este archivo con: método, ruta, request body, response
body y códigos de error esperados. Es la fuente de verdad para generar tanto
los `schemas` de FastAPI como los DTOs/clientes Kotlin.

## 6. Testing (criterio general)

- Ningún PR/feature se considera terminado sin tests unitarios en el lado que se modificó.
- Backend: cobertura mínima de happy path + 1 caso de error por endpoint.
- Android: ViewModel testeado con estados mockeados; no testear detalles de implementación de Compose.

## 7. Despliegue (inicial)

- **Backend**: empaquetado en Docker (`backend/Dockerfile`). Arranque y
  detención vía los scripts multiplataforma en `scripts/`
  (`start-mac.sh`/`stop-mac.sh`, `start-linux.sh`/`stop-linux.sh`,
  `start-windows.ps1`/`stop-windows.ps1`), disponible en
  `http://localhost:8000`. [TODO: definir destino final — Render/Fly.io/AWS/otro]
- **Android**: por ahora, builds locales (`./gradlew assembleDebug`).
  [TODO: definir cuándo se sube a Play Console Internal Testing]
- Aún no hay CI/CD configurado — es una tarea pendiente de priorizar
  (sugerencia: GitHub Actions con un workflow por módulo).

## 8. Convenciones de nombres

### Idioma
- **Documentación y comunicación** (este archivo, docs/PLAN.md, docs/api-contract.md,
  mensajes de commit, discusión con el agente): español.
- **Código** (nombres de variables, funciones, clases, parámetros, comentarios
  inline, docstrings): inglés. Esto sigue la convención del ecosistema
  (Kotlin, Android SDK, FastAPI, librerías de terceros) y evita mezclar
  idiomas dentro del mismo archivo de código (ej. no `fetchUsuarioActivo()`
  junto a `LazyColumn`/`ViewModel`).

### Kotlin (`android/`)
- Variables y funciones: `camelCase` (ej. `userName`, `fetchUserProfile()`)
- Constantes verdaderas (`const val`): `UPPER_SNAKE_CASE` (ej. `const val MAX_RETRY_COUNT = 3`)
- Propiedades `val`/`var` normales (no `const`): `camelCase`, aunque sean inmutables (ej. `val defaultTimeout = 30_000L`)
- Clases, interfaces, objetos: `PascalCase` (ej. `UserRepository`, `ApiResult`)
- Backing properties privadas: prefijo `_` (ej. `private val _uiState = MutableStateFlow(...)`, expuesta como `val uiState = _uiState.asStateFlow()`)
- Booleanos: prefijo `is`/`has`/`should` (ej. `isLoading`, `hasError`)

### Python (`backend/`)
- Variables y funciones: `snake_case` (ej. `user_name`, `fetch_user_profile()`)
- Constantes: `UPPER_SNAKE_CASE` a nivel de módulo (ej. `MAX_RETRY_COUNT = 3`)
- Clases: `PascalCase` (ej. `UserRepository`)
- Privado/interno: prefijo `_` (ej. `_internal_cache`)
- Modelos Pydantic: `PascalCase` con sufijo semántico (ej. `UserCreateSchema`, `UserResponseSchema`)

## 9. Instrucciones para Claude Code (agente)

- Al recibir una tarea que toque ambos módulos, primero propone/actualiza el
  contrato en `docs/api-contract.md`, luego genera backend, luego cliente Kotlin.
- Preferir cambios pequeños y verificables (compilar + correr tests) sobre
  refactors masivos sin confirmación.
- No introducir nuevas librerías de terceros sin señalarlo explícitamente en la respuesta.
- Si una tarea requiere decisiones de arquitectura no cubiertas aquí, preguntar
  antes de asumir (ej. elegir ORM, elegir gestor de estado, etc.).
- Actualizar este archivo cuando se tomen decisiones arquitectónicas nuevas.
- **No usar try/catch de forma excesiva ni programar a la defensiva.**
  Capturar excepciones solo en los puntos donde realmente se espera un fallo
  recuperable (ej. una llamada de red, una operación de I/O). No envolver
  cada función o bloque "por si acaso"; no validar condiciones que ya están
  garantizadas por el sistema de tipos o por una capa anterior. Preferir que
  un error real y no anticipado falle de forma visible (excepción/crash en
  dev, log claro en backend) en lugar de silenciarlo con un catch genérico.
- **No usar emojis ni imágenes** en código, comentarios, mensajes de commit,
  logs, ni documentación (incluyendo este archivo y el contrato de API).
  Texto plano únicamente.
