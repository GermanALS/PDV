# PLAN.md — Plan de desarrollo del Punto de Venta

Este documento vive en `docs/PLAN.md` y contiene el diseño en desarrollo activo
del proyecto. A diferencia de `CLAUDE.md` (contexto estable: arquitectura ya
decidida, convenciones, comandos), aquí se documentan features en fase de
diseño, decisiones que aún pueden iterar, y el checklist de avance. Cuando una
Parte se estabiliza, su resumen final se migra a `CLAUDE.md`.

**Estado del documento**: las Partes 1-28 están implementadas y mergeadas; su
detalle (checklists cerrados, decisiones resueltas, criterios de verificación)
se archivó en `docs/PLAN-historico.md` para mantener este archivo enfocado
(hallazgo B-9 de `docs/review_code.md`). Aquí quedan únicamente las Partes en
curso o pendientes (29-30), el Backlog de trabajo futuro y el historial de
decisiones estructurales.

Las Partes 21-30 incorporan los hallazgos de `docs/review_code.md` (revisión
de código del 2026-08-30). Cada Parte tiene su sección `### Checklist` con
ítems verificables y criterios de éxito concretos (comando, archivo, o
comportamiento observable). Las Partes con decisiones sin cerrar llevan además
una subsección `### Decisiones abiertas` con checkboxes: son preguntas que el
agente debe hacer antes de implementar, nunca resolver por cuenta propia
(`CLAUDE.md` §9).

Cómo leen estos checklists las herramientas de sincronización (`/jira-sync`,
convención de ramas/commits): ver `CLAUDE.md` sección 10.

---

## Parte 29: Deuda técnica menor (varios)  <!-- POS-131 -->

*(Hallazgos tipo B de `docs/review_code.md`, salvo B-2 -Parte 26- y B-3
-Parte 22-. Cada grupo del checklist es independiente y se puede cerrar en
cualquier orden.)*

### Checklist

**1. Config de build del backend** (POS-132)
- [x] B-1: versiones fijas en `backend/requirements.txt` (`==` o lockfile con
  `uv`/`pip-tools`). Criterio: `pip install -r requirements.txt` resuelve el
  mismo set en dos entornos. Decidido: pin `==` de las 10 directas más 26
  transitivas, congeladas del árbol resuelto del contenedor (la migración a
  `uv` + lockfile se planificó aparte, Parte 30). Verificado: `docker compose
  build backend` sin conflictos, `pip check` limpio, y `pip freeze` de la
  imagen nueva coincide exacto con `requirements.txt` (salvo
  `pip`/`setuptools`/`wheel`, tooling del base image).
- [x] B-4: usuario no privilegiado en `backend/Dockerfile` (`adduser` + `USER`).
  Criterio: `docker run ... whoami` no devuelve `root`. Verificado:
  `RUN adduser --system --group --no-create-home app && chown -R app:app /app`
  + `USER app`; `docker run --entrypoint whoami` -> `app`,
  `docker compose exec backend whoami` -> `app`; `migrate` (`alembic upgrade
  head`) y `backend` (`/api/v1/health` -> `ok`) corren como no-root.

**2. Android menor** (POS-133)
- [x] B-5: `DynamicHostInterceptor` cachea `deviceConfig` (ej. `StateFlow` en el
  interceptor) en vez de `runBlocking { preferences.deviceConfig.first() }` por
  request. Criterio: `./gradlew testDebugUnitTest` en verde; el `runBlocking`
  repetido desaparece. `jvm-tests` Verificado: campo `@Volatile config`
  sembrado una sola vez de forma bloqueante al construir el singleton y
  mantenido al día por un colector en un scope propio; `intercept()` ya no
  llama `runBlocking`. `testDebugUnitTest` en verde (`BUILD SUCCESSFUL`);
  `DynamicHostInterceptorTest` 3/3, incluida una prueba nueva de que el cache
  se refresca ante un cambio de conexión posterior a la construcción.
- [x] B-6: `Converters.toModulosPermitidos` / `fromModulosPermitidos` con un
  separador que no pueda aparecer en una clave de módulo (o JSON). Criterio:
  prueba con una clave que contenga el separador antiguo. Verificado:
  separador `""` (Unit Separator, carácter de control); `ConvertersTest`
  4/4 en verde, incluida `a key containing the old comma separator is
  preserved as one element`. Los roles de sistema se auto-corrigen al
  siguiente `observeRoles()` (`LocalRolRepository` reescribe si difiere del
  seed); un rol personalizado guardado con el formato viejo se re-guarda al
  editarlo — sin migración de datos por ser etapa de desarrollo (CLAUDE.md §9).
- [ ] B-7: `isMinifyEnabled = true` en el build de release + reglas Proguard para
  Room/Hilt/kotlinx-serialization/Retrofit. Criterio: `./gradlew assembleRelease`
  en verde y la app funciona (verificación en dispositivo). `needs-device`
  DIFERIDO (decisión abierta B-7, 2026-09-07): se pospone hasta que haya
  distribución real; activarlo obliga a mantener reglas Proguard de varias
  librerías y re-verificar en dispositivo cada release, sin distribución
  todavía. Mismo criterio con el que se difirió el destino de despliegue.
- [x] B-8: esquema definido para `versionCode`/`versionName` por release (hoy
  fijos en `1` / `"0.1"` tras 20 Partes). Criterio: documentado en `CLAUDE.md`
  §7 o en el build. Verificado: `CLAUDE.md` §7 documenta `versionName` SemVer
  `MAJOR.MINOR.PATCH` y `versionCode = MAJOR*10000 + MINOR*100 + PATCH`
  (monotónico, sin colisiones entre APKs distribuidos); comentario en
  `android/app/build.gradle.kts` que apunta al esquema.

**3. Documentación** (POS-134)
- [x] B-9: archivar las Partes ya migradas a `CLAUDE.md` en
  `docs/PLAN-historico.md`, dejando en `docs/PLAN.md` solo lo activo; actualizar
  el conteo "16 Partes" / "Partes 2-16" del encabezado de `PLAN.md` (líneas
  10-18), que quedó desactualizado. Criterio: `PLAN.md` más corto y su encabezado
  coherente con el número real de Partes. Verificado: `docs/PLAN-historico.md`
  creado con las Partes 1-28 (~3940 líneas); `docs/PLAN.md` quedó en ~200
  líneas (encabezado + Parte 29 + Parte 30 + Backlog + Historial de
  decisiones). Encabezado reescrito ("las Partes 1-28 están implementadas y
  mergeadas ... Aquí quedan únicamente las Partes en curso o pendientes
  29-30"). `CLAUDE.md` §2 lista el archivo nuevo y aclara que es solo lectura.

### Decisiones abiertas

- [x] B-7 (R8/shrinking): ¿en el alcance ahora, o se difiere hasta que haya
  distribución real? Activarlo obliga a mantener reglas Proguard de varias
  librerías y a re-verificar en dispositivo cada release. **Decidido**
  (2026-09-07): se difiere hasta que haya distribución real. El ítem B-7 del
  checklist queda sin marcar con la nota de diferimiento.
- [x] B-9: ¿el archivo histórico es `docs/PLAN-historico.md`, o se mueven las
  Partes cerradas a `CLAUDE.md` como bitácora? Propuesta: `docs/PLAN-historico.md`
  (CLAUDE.md debe quedar enfocado - CLAUDE.md §1). **Decidido** (2026-09-07):
  `docs/PLAN-historico.md`.

---

## Parte 30: Migración del backend a `uv` + lockfile  <!-- POS-135 -->

*(Sale de la decisión abierta B-1 de la Parte 29, 2026-09-07: para cerrar B-1
en "deuda menor" se fijó `requirements.txt` con `==` + freeze transitivo. La
migración a `uv` es la opción de largo plazo — la que pide el `CLAUDE.md`
global del usuario — pero es un cambio estructural que toca Dockerfile,
scripts y CI, así que se planifica aparte.)*

Objetivo: `uv` como gestor de dependencias del backend, con `pyproject.toml` +
`uv.lock` (lock transitivo completo con hashes) reemplazando
`backend/requirements.txt`. Instalación reproducible y verificada por hash en
Docker, en CI y en el venv de desarrollo del host.

### Checklist

**1. Manifiesto y lock** (POS-136)
- [x] `backend/pyproject.toml` con las 10 dependencias directas (grupo
  `dev` para `pytest`/`pytest-asyncio`/`httpx`); `uv.lock` generado con
  `uv lock`. Criterio: `uv sync --frozen` en un entorno limpio instala el
  mismo set que hoy (comparar contra el `pip freeze` fijado en la Parte 29).
  Verificado (2026-09-07): `pyproject.toml` con 7 directas runtime pineadas
  `==` + grupo `dev` (3), `[tool.uv] package = false`, `requires-python =
  ">=3.11"`; `.python-version` -> `3.11` (uv bajó CPython 3.11.15 gestionado).
  `uv.lock` = 38 paquetes, 759 hashes; `uvloop` queda con marcador
  `sys_platform != 'win32'` (se instala en la imagen Linux). `uv sync
  --frozen` en venv limpio 3.11.15 OK, `uv pip check` limpio, `uv run pytest`
  156 passed. Único cambio de set vs Parte 29: `anyio` 4.14.2 -> 4.15.1
  (bump menor compatible, aceptado explícitamente; ahora fijado por hash);
  `colorama`/`uvloop` difieren solo por marcador de plataforma.
- [x] `backend/requirements.txt` eliminado. Criterio: no quedan referencias a
  `requirements.txt` en `backend/`, `Dockerfile`, `scripts/` ni workflows.
  Verificado (2026-09-07): el `requirements.txt` hand-maintained se reemplazó
  por un export generado (`uv export --no-hashes --no-emit-project`, decisión
  D2 = mantener para compat externa); su cabecera documenta el comando de
  regeneración. `git grep "requirements.txt"` en `Dockerfile`/`compose`/
  `scripts/`/`.github/` -> sin coincidencias; nada del build lo consume.

**2. Docker y scripts** (POS-137)
- [x] `backend/Dockerfile` usa `uv` (`COPY` de `uv` desde su imagen oficial o
  `pip install uv`, luego `uv sync --frozen --no-dev`). Criterio: `docker
  compose build backend` en verde; `pip check` / `uv pip check` limpio;
  imagen sigue corriendo como usuario no-root (B-4). Verificado (2026-09-07):
  `COPY --from=ghcr.io/astral-sh/uv:0.10.12 /uv /uvx /bin/` (decisión D1) +
  `uv sync --frozen --no-dev --no-install-project` con cache mount; venv en
  `PATH` para que `uvicorn` (CMD) y `alembic` (comando del servicio
  `migrate`) resuelvan sin `uv run`. `docker compose build backend` verde;
  `docker run --entrypoint whoami` -> `app`; `uv pip check --no-cache` en la
  imagen -> "All installed packages are compatible" (27 paquetes, `--no-dev`
  descarta pytest/httpx/etc., `uvloop` presente); `docker compose up -d` ->
  `migrate` corrió todas las migraciones Alembic como no-root, `backend`
  healthy, `/api/v1/health` -> `{"status":"ok","version":"0.1.0"}`.
- [x] `scripts/start-*`/`stop-*` y la sección "alternativa manual" de
  `CLAUDE.md` §4 actualizadas a `uv run` / `uv sync`. Criterio: los scripts
  levantan el stack sin `pip` ni `venv` manual. Verificado (2026-09-07): los
  `scripts/start-*`/`stop-*` solo hacen `docker compose up/down` — nunca
  tocaron `pip`/`venv`, criterio ya satisfecho, sin cambios. La sección
  "Alternativa manual" de `CLAUDE.md` §4 reescrita a `uv sync` / `uv run
  uvicorn` / `uv run pytest` / `uv run alembic`, con nota de que
  `requirements.txt` es un export generado solo-compat.

**3. CI** (POS-138)
- [x] Workflow de CI del backend (Parte 26) usa `uv sync --frozen` +
  `uv run pytest` + `uv run alembic ...`. Criterio: el workflow corre en
  verde en un PR de prueba y falla si `uv.lock` está desactualizado
  (`uv lock --check`). Verificado (2026-09-07): `.github/workflows/
  backend-ci.yml` usa `astral-sh/setup-uv@v6` (pin `0.10.12`, cache,
  decisión D3 — dependencia de terceros nueva, señalada por §9), luego
  `uv lock --check`, `uv sync --frozen`, `uv run alembic upgrade head`,
  `uv run alembic check`, `uv run pytest`; `actions/setup-python` eliminado
  (uv baja 3.11 según `.python-version`). Simulación local de cada step OK.
  `uv lock --check` probado: exit 1 con `pyproject.toml` alterado sin
  re-lock ("The lockfile at `uv.lock` needs to be updated"), exit 0 tras
  revertir. El "verde en un PR de prueba" lo dispara el PR que mergee esta
  Parte (el filtro `paths` cubre `backend/**` y el propio workflow).

**4. Documentación** (POS-139)
- [x] `CLAUDE.md` §4 (stack, comandos) refleja `uv` como gestor; nota de que
  `requirements.txt` ya no existe. Criterio: ninguna referencia obsoleta a
  `pip install -r requirements.txt` en la doc. Verificado (2026-09-07):
  `CLAUDE.md` §4 "Stack técnico" tiene línea nueva de `uv` como gestor y
  aclara que `requirements.txt` es un export generado solo-compat (D2 lo
  mantuvo, no se eliminó del todo); "Rebuild obligatorio" y "Alternativa
  manual" a `uv run`. `git grep "pip install -r"` solo aparece en el texto
  de criterio de los propios checklists de Parte 29/30 en este archivo, no
  en doc operativa.

### Decisiones abiertas

- [x] ¿`uv` se instala en la imagen Docker vía `COPY --from=ghcr.io/astral-sh/uv`
  (pin de versión, sin red en build) o `pip install uv==X`? Propuesta:
  `COPY --from`, es el patrón recomendado por Astral. **Decidido**
  (2026-09-07): `COPY --from=ghcr.io/astral-sh/uv:0.10.12`. (D1)
- [x] ¿Se mantiene un `requirements.txt` exportado (`uv export`) como
  compatibilidad para herramientas que no entienden `uv`, o se corta del
  todo? Propuesta: cortar del todo; nada en el proyecto lo necesita.
  **Decidido** (2026-09-07): se mantiene el export generado (`uv export
  --no-hashes`) por compatibilidad externa; no lo consume ni el `Dockerfile`
  ni el CI. El ítem "requirements.txt eliminado" del checklist se cerró con
  esa aclaración (se reemplaza el hand-maintained por el generado). (D2)
- [x] CI: ¿`uv` vía action `astral-sh/setup-uv` o installer standalone?
  **Decidido** (2026-09-07): `astral-sh/setup-uv@v6` (pin `0.10.12`);
  dependencia de terceros nueva, señalada por §9. (D3)

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
- **2026-09-07** (Parte 29, B-9): las Partes 1-28 (todas mergeadas) se
  movieron a `docs/PLAN-historico.md` con sus checklists finales y decisiones
  resueltas; `docs/PLAN.md` quedó solo con lo activo (Parte 29, Parte 30,
  Backlog, este historial). El encabezado de `PLAN.md` — que seguía diciendo
  "16 Partes" / "Partes 2-16" / "Partes 1-20 implementadas" — se reescribió.
  En la misma Parte se decidió pinear `backend/requirements.txt` con `==`
  (B-1) y planificar la migración a `uv` como Parte 30 aparte, y diferir R8
  en el build de release (B-7) hasta que haya distribución real.
