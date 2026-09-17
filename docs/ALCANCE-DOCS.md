# Alcance de la documentación técnica

Define el alcance COMPLETO del manual técnico. `/docs-sync` recorre **todas**
las filas de las tablas en cada ejecución, sin importar qué Parte se cerró.
Agregar alcance = agregar una fila (lo propone el subagente, lo aprueba el usuario).

## Artefactos

| Artefacto | Rol | Quién lo escribe |
|---|---|---|
| `docs/manual-tecnico.md` | **Única fuente editable** del manual, incluido su bloque `manual-meta` | Subagente `doc-maintainer` |
| `docs/manual-tecnico.html` | Derivado para lectura en navegador (índice lateral, diagramas Mermaid, tema claro/oscuro) | `scripts/docs/build_manual.py` |
| `docs/manual-tecnico.pdf` | Derivado para distribución (portada, contenido, paginado, tamaño Carta) | `scripts/docs/build_manual.py` |
| `scripts/docs/manual-template.html` | Plantilla visual del HTML y del PDF | Solo el usuario |
| `docs/ALCANCE-DOCS.md` | Este manifiesto | El usuario; `/docs-sync` solo actualiza la línea `docs-sync:` |

Reglas:

- El HTML y el PDF nunca se editan a mano ni con las herramientas de edición de
  Claude (el hook `.claude/hooks/docs_guard.py` lo bloquea). Se regeneran con
  `uv run scripts/docs/build_manual.py`.
- Frescura verificable: `uv run scripts/docs/build_manual.py --check` compara el
  sha256 del Markdown con el embebido en el HTML y en los metadatos del PDF.
- `manual-meta` (al inicio del Markdown) alimenta portada y chips: `titulo`,
  `lead`, `actualizado` (fecha del estado descrito) y `chips` separados por `|`.
  Sus cifras son las mismas que las del cuerpo y se verifican igual.

## Mapa de secciones

| Sección del manual | Cubre | Fuentes de verdad |
|---|---|---|
| Encabezado + `manual-meta` | Estado a la fecha, fuentes vivas, chips de portada | `docs/PLAN.md`, `docs/PLAN-historico.md`, `git log` |
| §1 Descripción funcional | Módulos, pantallas, persistencia, `BackendMode` | `android/app/src/main/java/com/pdv/pos/**` (Screens, ViewModels, `BackendMode`), `CLAUDE.md` |
| §2.1 Contexto / despliegue | Dispositivo, servicios Compose, proveedor LLM | `docker-compose.yml`, `backend/Dockerfile`, cliente LLM en `android/` |
| §2.2 App Android — capas | MVVM, `domain / data.local / data.remote`, DI | `android/app/src/main/java/com/pdv/pos/{domain,data,di}/**` |
| §2.3 Backend — capas | `main.py`, routers, schemas, models, database | `backend/app/**`, `backend/alembic/versions/` |
| §2.4 Motor de sincronización | Push/pull, resolvers, `SyncWorker`, conflictos | `android/.../sync/**`, `docs/api-contract.md` §12, `backend/app/routers/sync_conflicts.py` |
| §3 Modelo de datos (ER) | Campos comunes, diagrama ER, integridad, Room | `docs/schema-pos.json`, `android/.../data/local/*Entity.kt`, `PdvDatabase`, `Migrations.kt`, `android/app/schemas/`, `backend/app/models/*.py` |
| §4 Diagramas UML | Componentes, clases `ModeAware`, secuencias, despliegue | Mismas fuentes de §2 y §3; flujos en `ModeAware*Repository`, `EjecutorAccionesIa`, `backend/app/routers/{auth,ventas}.py` |
| §5 Requerimientos e instalación | Docker, uv, Android, hardware, conectividad, IA, secretos | `scripts/*`, `docker-compose*.yml*`, `backend/pyproject.toml`, `backend/uv.lock`, `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `AndroidManifest.xml`, `app/src/debug/res/xml/network_security_config.xml` |
| §6 Configuración | Variables de entorno, DataStore, versionado | `backend/app/{database,security}.py`, `docker-compose.yml`, `*Preferences.kt`, `SyncStateStore`, `SessionStore`, `DynamicHostInterceptor`, `android/app/build.gradle.kts` |
| §7 Seguridad | JWT, bcrypt, permisos por módulo, cifrado de tokens, cleartext | `backend/app/{security,permissions,main}.py`, `AuthInterceptor`, `AndroidKeystoreTokenCipher`, `network_security_config.xml` |
| §8 Referencia de API | Tabla de endpoints y módulo de escritura | `backend/app/routers/**`, `backend/app/permissions.py` (`_ESCRITURA_MODULO`), `docs/api-contract.md` |
| §9 Operación y diagnóstico | Health, logs, auditoría, Room en device, problemas frecuentes | `backend/app/routers/health.py`, `backend/app/main.py`, `AppLogger`, `BackendHealthChecker`, `docker-compose.yml`, `docs/PLAN.md` (hallazgos) |
| §10 Mantenimiento | Estructura del repo, CI, pruebas, migraciones, convenciones, comandos y hooks de Claude Code, generación del manual | Árbol del repo (`git ls-files`), `.github/workflows/*.yml`, `backend/tests/**`, `android/app/src/test/**`, `backend/alembic/versions/`, `CLAUDE.md`, `.claude/commands/*.md`, `.claude/agents/*.md`, `.claude/hooks/*`, `.claude/settings.json`, `scripts/docs/` |
| §11 Evolución y deuda técnica | Estado del plan, Parte en curso, follow-ups, backlog, diferidos | `docs/PLAN.md`, `docs/PLAN-historico.md`, `docs/review_code.md` |
| §12 Glosario | Términos del dominio y del proceso | Todo lo anterior; cada término usado en el manual debe existir en el código o en `PLAN.md` |

## Verificaciones de deriva obligatorias

Datos que envejecen solos. Se verifican en **cada** ejecución aunque su
sección no esté afectada por el diff, y deben coincidir en todos los lugares
donde aparecen (cuerpo, `manual-meta` y chips):

- Partes implementadas/pendientes y fecha de estado (encabezado, §11.1, chips).
- Número y head de migraciones Alembic (§2.3, §9.1, §10.4, chips).
- Versión de Room, número de entidades y DAOs (§3.4, §10.4, chips).
- Número de entidades `ModeAware*` (§2.2, §11.6).
- Conteos y resultados de tests backend y Android (§10.3): solo con salida real
  de un comando o resultado registrado en `PLAN.md` con su fecha; si no hay
  evidencia nueva, conservar la cifra con su fecha original.
- Versiones: `uv`, Python, compileSdk, JDK, minSdk, `versionCode`/`versionName`,
  versión de `/health` (§5, §6.3, §9.1).
- Endpoints listados en §8 contra los routers reales (ninguno de más, ninguno de menos).
- Rutas de archivo y comandos citados en todo el manual: deben existir.
- Comandos slash, subagentes y hooks del proyecto (§10.7) contra `.claude/`.
- Anclas internas (`[§N](#...)`) que apunten a encabezados existentes.

## Última sincronización
<!-- docs-sync: commit=0000000 parte="—" fecha=— -->