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
curso o pendientes (29-33), el Backlog de trabajo futuro y el historial de
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

## Parte 31: Conexión remota desde el dispositivo (Wi-Fi LAN + acceso a la config desde el login)  <!-- POS-140 -->

*(Sale de un hueco operativo detectado el 2026-09-08: si el dispositivo
quedó en modo REMOTO y el backend deja de ser alcanzable —teléfono fuera de
la red de la PC, backend apagado, URL remota mal escrita—, el usuario no
puede autenticarse, y como la configuración de conexión vive dentro del
módulo Configuración que está detrás del login, tampoco puede volver a modo
LOCAL ni corregir la URL. Queda encerrado fuera de la app. Además, hoy el
dispositivo físico solo llega al backend por `adb reverse` sobre USB
(`localhost:8000`); para probar sobre Wi-Fi en la misma subred falta
habilitar cleartext HTTP hacia la IP de la LAN en el build de debug. Con
`adb reverse` el encierro es raro; sobre Wi-Fi —IP DHCP variable, el
teléfono sale de la red— es rutinario, por eso las dos piezas van juntas.)*

Claves de Jira: épica `POS-140`; historias `POS-141`..`POS-145` (asignadas
por `/jira-sync` el 2026-09-09).

Objetivo: (a) exponer la configuración de conexión (BackendMode + parámetros
de la conexión remota) desde la propia pantalla de login, disponible sin
sesión y sin depender de que el backend responda; y (b) que el dispositivo
alcance el backend por Wi-Fi en la misma subred (IP de la LAN + puerto), sin
el túnel USB. Sin cambios de esquema (los parámetros de conexión ya son
preferencia de dispositivo en DataStore, `CLAUDE.md` §3) ni de contrato de
API (reusa `GET /api/v1/health`). El cambio de red es solo en el build de
debug (`src/debug/`); el endurecimiento de cleartext para release vive en la
Parte 33.

### Checklist

**1. Acceso y panel de conexión en el login** (POS-141)
- [x] La pantalla de login expone un acceso a la configuración de conexión
  (ej. icono en la barra superior o enlace bajo el formulario) que abre un
  panel disponible sin estar autenticado. Criterio: con la app recién
  instalada y sin sesión, el panel se abre desde el login. Hecho
  (2026-09-09): `LoginScreen` con enlace "Configurar conexión" bajo el
  formulario → `PanelConexionBottomSheet` (composable dedicado en
  `com.pdv.pos.auth`, hoja modal porque el login se renderiza fuera del
  NavHost). Verificado en dispositivo (Grupo 3, paso d).
- [x] El panel permite cambiar BackendMode LOCAL/REMOTO y editar
  esquema/host/puerto de la conexión remota, leyendo y persistiendo en el
  mismo DataStore que usa el módulo Configuración (sin almacenamiento
  duplicado). Criterio: un cambio hecho desde el login se ve luego en la
  pantalla Configuración y viceversa. Hecho (2026-09-09):
  `PanelConexionViewModel` usa el mismo `ConfiguracionPreferences`; nuevo
  campo `esquema` (`EsquemaConexion` http/https) en `DeviceConfig` +
  `ConfiguracionPreferences` + selector en ambas pantallas;
  `DynamicHostInterceptor` reescribe también el scheme. Verificado en
  dispositivo (Grupo 3, pasos d/e).
- [x] Botón "Probar conexión" que llama a `GET /api/v1/health` contra la URL
  configurada y muestra el resultado (ok / error con detalle) sin cerrar el
  panel. Criterio: URL inválida muestra error y deja el panel abierto; URL
  válida muestra ok. Hecho (2026-09-09): `BackendHealthChecker` (OkHttp
  propio, sin `DynamicHostInterceptor` ni `AuthInterceptor`, contra la URL
  tecleada). Verificado en dispositivo (Grupo 3, paso e).

**2. Fallo de login en modo remoto** (POS-142)
- [x] Cuando el login en modo REMOTO falla por conectividad (timeout / host
  inalcanzable, distinto de credenciales inválidas), el mensaje lo indica
  explícitamente y ofrece una acción directa para abrir el panel de
  conexión. Criterio: con el backend remoto apagado, intentar login muestra
  un error de conectividad (no uno genérico de credenciales) con botón a
  "Configurar conexión". Hecho (2026-09-09): `LoginViewModel` separa
  `IOException`/`HttpException` (flag `connectivityError`) de
  `CredencialesInvalidas`; con `connectivityError` el enlace "Configurar
  conexión" pasa a `Button` prominente. Verificado en dispositivo (Grupo 3).
- [x] Abrir el panel y cambiar de modo desde el login no requiere sesión ni
  que el backend responda. Criterio: en modo REMOTO con backend caído, desde
  el login se puede pasar a LOCAL y autenticarse contra los datos locales.
  Hecho (2026-09-09): `PanelConexionViewModel` no recibe `SessionManager`;
  el modo se persiste al instante en DataStore y `ModeAwareAuthRepository`
  relee el modo por llamada. Verificado en dispositivo (Grupo 3, paso d).

**3. Verificación del acceso desde el login** (POS-143)
- [x] `LoginViewModel` (o el ViewModel del panel) testeado: fallo por
  conectividad vs credenciales, y que exponer/guardar la config no depende
  de sesión. `jvm-tests`: `./gradlew testDebugUnitTest` en verde (mostrar
  salida completa). Verificado (2026-09-09): `BUILD SUCCESSFUL`, suite
  completa 359 tests / 0 fallos / 0 errores / 76 clases. `LoginViewModelTest`
  10/10 (conectividad IOException vs credenciales vs Http;
  `onConexionConfigurada` limpia solo el error de conectividad);
  `PanelConexionViewModelTest` 6/6 (siembra desde DataStore, `onGuardar`
  persiste sin `SessionManager` -no lo recibe-, modo persiste al instante,
  anti-clobber de host, "Probar conexión" con checker mockeado);
  `BackendHealthCheckerTest` 5/5 (200/500/host inalcanzable + validación
  puerto/host contra MockWebServer); `DynamicHostInterceptorTest` 4/4
  (+scheme https); `ConfiguracionPreferencesTest` 4/4 (round-trip esquema).
- [x] Verificación en el dispositivo Xiaomi M2102J20SG: (a) fijar modo
  REMOTO con la URL de la PC, (b) apagar el backend, (c) reiniciar la app,
  (d) desde el login abrir el panel, cambiar a LOCAL y loguearse, (e)
  reconfigurar una URL remota nueva y "Probar conexión" en ok. `needs-device`.
  Verificado (2026-09-09) con `localhost:8000` sobre el túnel `adb reverse`
  (USB): pasos (a)-(e) OK — el login muestra error de conectividad explícito
  con el backend caído, el panel permite pasar a LOCAL y autenticarse sin
  sesión ni backend, y "Probar conexión" contra `http://localhost:8000` da
  ok sin cerrar el panel. Con la IP LAN de la PC (`192.168.0.132:8000`) la
  prueba falla: es el hueco que cierra el Grupo 4 (cleartext HTTP hacia el
  rango LAN solo está habilitado para `localhost` en el
  `network_security_config` de debug).

**4. Conectividad por Wi-Fi en la misma subred** (POS-144)
- [x] `app/src/debug/res/xml/network_security_config.xml` permite cleartext
  para el rango LAN privado (o `base-config` en el build de debug), no solo
  `localhost`. Criterio: un debug build hace `GET http://<IP-LAN>:8000/api/v1/health`
  en ok desde el dispositivo por Wi-Fi, sin `adb reverse`. Hecho
  (2026-09-09): `<base-config cleartextTrafficPermitted="true" />` (sin lista
  de `<domain>` porque la config de Android no admite rangos/CIDR y la IP LAN
  es DHCP variable); solo build de debug (`src/main` no define
  `networkSecurityConfig` ni `usesCleartextTraffic`, release bloquea
  cleartext por defecto). Verificado en dispositivo por Wi-Fi con
  `192.168.0.132:8000` (venta en REMOTO sin `adb reverse`).
- [x] `CLAUDE.md` §3: Wi-Fi (IP de la PC + puerto en Configuración, regla de
  firewall inbound TCP 8000) documentado como camino principal para el
  dispositivo físico; `adb reverse` por USB queda como alternativa. Nota
  sobre IP DHCP variable (reserva DHCP o IP estática) y sobre aislamiento de
  clientes del router. Hecho (2026-09-09): §3 reestructurada — subsección
  "Wi-Fi en la misma subred (camino principal)" con la regla
  `New-NetFirewallRule ... TCP 8000`, nota de DHCP y de AP/client isolation;
  el bloque de `adb reverse` pasa a "Alternativa".
- [x] Verificación en el Xiaomi M2102J20SG: mismo Wi-Fi que la PC,
  Configuración -> REMOTO + IP + `8000`, y una venta + un sync end-to-end
  contra el backend dockerizado sin túnel USB. `needs-device`. Verificado
  (2026-09-09): mismo Wi-Fi, REMOTO + `192.168.0.132` + `8000`, una venta
  completa contra el backend dockerizado sin `adb reverse` (en REMOTO la
  venta es el round-trip end-to-end). Regla de firewall inbound TCP 8000
  aplicada en la PC.

**5. Fix de crash + logout en `LOCAL_CON_SINCRONIZACION`** (POS-145)
(agregado 2026-09-09 por decisión del usuario: arreglar dentro de esta Parte
en vez de diferir a Backlog). Al probar Wi-Fi con el backend alcanzable, una venta o
entrar a Configuración en modo `LOCAL_CON_SINCRONIZACION` primero cerraba la
app (crash) y, tras el primer fix, mandaba al usuario a la pantalla de login.
Causa raíz: `ModeAwareSucursalRepository` es el único `ModeAware*` que enruta
ese modo al backend (`observeSucursales()` → `GET /api/v1/sucursales`), pero
un dispositivo que inició sesión con `LocalAuthRepository` no tiene JWT; desde
la Parte 21 ese endpoint responde 401. Dos efectos: (a) la `HttpException`
subía sin manejar por `RemoteSucursalRepository` (flow sin `catch`) hasta
colectores sin `.catch` (`VentaViewModel.generarTicketSeguro` — solo
capturaba `IOException` —, `ConfiguracionViewModel`, `EstadoPuntoVenta`) →
crash; (b) `AuthInterceptor` cerraba sesión ante cualquier 401 fuera de
`/auth/login`, incluso los de una request que nunca llevó token. Latente
desde la Parte 21; la Parte 31 lo destapó al hacer el backend alcanzable por
Wi-Fi.
- [x] `ModeAwareSucursalRepository`: en `LOCAL_CON_SINCRONIZACION` la lectura
  remota del catálogo es best-effort — `remote.observeSucursales().catch { emitAll(local.observeSucursales()) }`.
  No propaga, no muestra lista vacía: degrada al catálogo local, que es la
  semántica offline-first del modo. REMOTO sin cambios.
- [x] `AuthInterceptor`: solo cierra sesión ante un 401 si la request **sí
  llevaba** token (`token != null`). Un 401 a una request sin token es
  esperado en LOCAL / LOCAL_CON_SINCRONIZACION y lo maneja el repositorio que
  llamó, no expulsa al usuario. `jvm-tests` Verificado (2026-09-09):
  `testDebugUnitTest` en verde, suite 361/0/0; `AuthInterceptorTest` 8/8 con
  prueba nueva `a 401 to a tokenless request does not clear a local session`;
  `ModeAwareSucursalRepositoryTest` 3/3 con `falls back to the local catalog
  when the backend read fails` (403 sin JWT). El push de sync diferido de
  este modo sigue sin implementar (WorkManager + orquestador) — Parte propia.
- [x] Reverificación en el Xiaomi: venta y entrada a Configuración en
  `LOCAL_CON_SINCRONIZACION` con el backend alcanzable por Wi-Fi ya no
  cierran la app ni mandan al login. `needs-device`. Verificado
  (2026-09-09): en `LOCAL_CON_SINCRONIZACION` por Wi-Fi, una venta se
  completa (baja inventario) y entrar a Configuración no crashea ni expulsa
  al login.

### Decisiones abiertas

- [x] ¿El panel de conexión del login reutiliza el composable de la pantalla
  Configuración tal cual, o es una versión reducida dedicada (solo modo +
  URL + probar)? Propuesta: versión reducida, para no arrastrar el resto de
  Configuración (sucursal seleccionada, etc.) a un contexto pre-login.
  **Decidido** (2026-09-09): versión reducida dedicada (composable nuevo y
  acotado: modo + esquema/host/puerto + "Probar conexión"), compartiendo
  `ConfiguracionPreferences`/DataStore para que los cambios sigan
  sincronizados con la pantalla Configuración.
- [x] ¿Se agrega un fallback automático a LOCAL tras N fallos de conexión
  remota en el login, o queda siempre como acción manual? Propuesta: manual;
  un fallback automático puede enmascarar problemas de red reales y cambiar
  de modo sin que el usuario lo note. **Decidido** (2026-09-09): siempre
  manual.
- [x] El grupo 1 del checklist dice "editar esquema/host/puerto"; hoy la
  conexión remota es `http` fijo (`BASE_URL` en `NetworkModule`,
  `DynamicHostInterceptor` solo reescribe host/puerto). **Decidido**
  (2026-09-09): se agrega en esta Parte un campo `esquema` (http/https) a
  `DeviceConfig`/`ConfiguracionPreferences` y `DynamicHostInterceptor` pasa
  a reescribir también el scheme, dejando el panel del login y Configuración
  listos para un backend HTTPS. El endurecimiento de cleartext para release
  sigue en la Parte 33.

---

## Parte 32: Motor de sincronización diferida (`LOCAL_CON_SINCRONIZACION`)  <!-- POS-146 -->

*(Sale del análisis "qué falta para terminar el modo local + la sync remota"
del 2026-09-09. Hoy `LOCAL_CON_SINCRONIZACION` es, en la práctica, `LOCAL` +
mostrar el catálogo remoto de sucursales: no sube nada de lo creado offline
ni baja cambios de otras sucursales. Las piezas que existen son primitivas
puras sin orquestación — `LastWriteWinsSyncEngine` y `EventoAditivoCombiner`
solo los llaman los tests; `MigrationPlanner` / `PlanMigracion` (4 casos de
migración al cambiar de modo) es lógica que nunca se invoca; `isSynced =
false` se escribe en las 11 entidades pero ningún DAO lo lee. No hay
dependencia WorkManager en el build (pese a que `CLAUDE.md` §3 la lista en el
stack), ni orquestador de subida, ni pull. El backend ya está listo para
recibir el push: POST idempotentes por `local_id` (Parte 23) y enforcement
`Authorization: Bearer` (Parte 21); falta el lado cliente y el contrato del
pull.)*

Claves de Jira: épica `POS-146`; historias `POS-147`..`POS-151` (asignadas por
`/jira-sync` el 2026-09-11).

Objetivo: que un dispositivo en `LOCAL_CON_SINCRONIZACION` suba en background
lo creado offline —sin duplicar, reusando los POST idempotentes de la Parte
23— y baje los cambios remotos de otras sucursales/dispositivos, con el
catálogo local como fuente de verdad offline. **Alcance: entidades
transaccionales** (`ventas`, `entradas`, `cortes_caja`, `retiros_efectivo`,
`devoluciones`, `inventario`/`movimientos`). `usuarios`/`roles`/`sucursales`
quedan fuera de esta Parte. Sin cambios de esquema salvo las columnas que
necesiten los DAO de pendientes (`schema-parity` donde aplique).

### Checklist

**1. Infraestructura de push (Android)** (POS-147)
- [x] Dependencia `androidx.work` (WorkManager) + `androidx.hilt:hilt-work`
  en `android/gradle/libs.versions.toml` y `android/app/build.gradle.kts`.
  Librería nueva — señalar explícitamente (`CLAUDE.md` §9). Criterio:
  `./gradlew assembleDebug` en verde con la dependencia integrada en el grafo
  de Hilt. Hecho (2026-09-09): `androidxWork = 2.10.1` +
  `androidxHilt = 1.2.0`; `work-runtime-ktx`/`hilt-work` +
  `ksp(androidx-hilt-compiler)`; `PdvApplication : Configuration.Provider`
  con `HiltWorkerFactory`; `WorkManagerInitializer` removido del manifest
  (`tools:node="remove"`). `assembleDebug` en verde.
- [x] `SyncStateStore` (DataStore compartido): `lastSuccessAtMillis`,
  `lastError`, cursor `updated_since` por entidad del pull. `jvm-tests`
  (round-trip). Hecho (2026-09-09): `SyncStateStore` sobre el mismo
  `DataStore<Preferences>` que `IaPreferences`/`ConfiguracionPreferences`;
  `registrarExito` limpia el `lastError`, `registrarError` no toca el último
  éxito, cursores por entidad con clave dinámica. `SyncStateStoreTest` 4/4.
- [x] `SyncScheduler` (encola el periódico + one-time; no-op salvo
  `BackendMode == LOCAL_CON_SINCRONIZACION`) + `SyncWorker` (`CoroutineWorker`
  + `@HiltWorker`, delega en `SyncOrchestrator`), con `Constraints` de
  conectividad y backoff exponencial. Criterio: `jvm-tests` del gating del
  scheduler; no se encola en `LOCAL` puro ni en `REMOTO`. Hecho (2026-09-09):
  `SyncScheduler` (periódico 15 min con `KEEP` + `sincronizarAhora()`
  one-time), `SyncWorker` delgado (guarda de modo + mapeo a `Result`),
  `SyncOrchestrator` interfaz + `DefaultSyncOrchestrator` stub (el push/pull
  real llega en Grupos 2-3), `di/SyncModule` (bind + `WorkManager`),
  `PdvApplication` programa según el modo persistido al arrancar.
  `SyncSchedulerTest` 5/5. Suite completa 370/0/0. (Las queries DAO de
  pendientes + `marcarSincronizado` se construyen en el Grupo 2, junto a cada
  pusher — su forma depende de la unidad de push por entidad; `needs-device`,
  D5.)

**2. Orquestador de subida y merge** (POS-148)
- [x] `SyncMappers.kt` (`internal`, `Entity` → `*CreateRequestDto` directo,
  extrayendo las extensiones `private` de los `Remote*Repository`) + un
  pusher por entidad en `sync/push/` que llama a los `*ApiService` (ya
  devuelven el DTO con `id`) y devuelve el `remote_id` (D3.1). Incluye las
  queries DAO de pendientes (`isSynced = 0 AND deletedAt IS NULL`) +
  `marcarSincronizado(localId, remoteId)` que cada pusher necesita
  (`needs-device`, D5; `schema-parity` si toca columnas). Hecho (2026-09-10):
  `sync/SyncMappers.kt`; `sync/push/EntityPusher.kt` (interfaz + helper
  `empujarFila` con la política de errores) + `Venta`/`Entrada`/`CorteCaja`/
  `Retiro`/`Devolucion`/`InventarioAjuste` `Pusher`. Queries en
  `VentaDao`/`EntradaDao`/`CajaDao`/`RetiroDao`/`DevolucionDao`/`InventarioDao`
  (sin columnas nuevas → sin `schema-parity`). `EntradaPusher`: `articulo_nuevo`
  vs `articulo_id` remoto según `ArticuloEntity.remoteId`. `InventarioAjustePusher`:
  agrupa movimientos `ajuste` por `(sucursal, articulo)`, un `PATCH` con la
  cantidad local actual; salta si el artículo no tiene `remoteId` aún.
  `SyncMappersTest`, `VentaPusherTest`, `EntradaPusherTest`,
  `SyncPushersHappyPathTest`, `InventarioAjustePusherTest`.
- [x] `SyncOrchestrator.push()` recorre las entidades pendientes, empuja, y
  al recibir el `id` llama `marcarSincronizado`. Reintento seguro por la
  idempotencia `local_id` (Parte 23). Errores: `IOException` → reintentar el
  ciclo; `403`/`404`/`422` → saltar esa fila, loguear, seguir; `401` → parar
  y marcar la sesión expirada (gap 2). Hecho (2026-09-10):
  `DefaultSyncOrchestrator` corre los 6 pushers en el orden de `SyncModule`
  (entradas → ventas → ajustes → cortes → retiros → devoluciones, para que
  los artículos creados offline existan antes de referenciarlos);
  `IOException` → `Reintentar`, `401` → `Fallo("sesion expirada")`, `5xx` →
  `Reintentar`; el skip por fila (`4xx` no-401) lo maneja `empujarFila` en
  el pusher. `DefaultSyncOrchestratorTest` (orden + 3 mapeos de error).
- [x] Idempotencia de `devoluciones` en el backend (gap 1: la Parte 23 no la
  cubrió y el push la necesita): migración Alembic `UNIQUE(local_id)` +
  captura de `IntegrityError` en el router + `local_id` obligatorio en el
  schema + contrato §9. `schema-parity` (`docs/schema-pos.json` +
  `DevolucionEntity` + migración). Hecho (2026-09-10): `Devolucion.local_id`
  `unique=True`; migración `0013_unique_local_id_devoluciones` (cadena
  `0012 -> 0013 (head)`); `create_devolucion` con `flush()` + `except
  IntegrityError` → `200` con la fila existente (`selectinload`), patrón
  exacto de `ventas.py`; `DevolucionCreateSchema.local_id: uuid.UUID`
  obligatorio; `api-contract.md` §9.1 con la nota de idempotencia + `422`
  por `local_id` faltante. `schema-pos.json` sin cambios (consistente con
  Parte 23: `local_id` ya figura como `primary_key`; Room lo tiene como
  `@PrimaryKey`). Verificado: `uv run alembic upgrade head` OK, `uv run
  alembic check` → "No new upgrade operations detected" (schema-parity),
  `uv run pytest` → **158 passed** (+2: `sin_local_id_returns_422`,
  `reintento_con_mismo_local_id_es_idempotente`).
- [x] `EventoAditivoCombiner`: rama "delta negativo → conflicto" diferida
  desde la Parte 6 (`combinarConDeteccion` devuelve el valor + si quedó
  negativo, sin dejar de ser puro). `EventoAditivoSyncEngine` escribe el
  conflicto en `sync_conflicts` con `resueltoAutomaticamente = false` + log
  `SYNC_CONFLICT` (D4: regla fija, sin UI de resolución). Borrado local vs
  edición remota: last-write-wins por `updatedAt` sobre el tombstone. Hecho
  (2026-09-10): `EventoAditivoCombiner.combinarConDeteccion` →
  `CombinacionResultado(valor, quedoNegativo)`; `EventoAditivoSyncEngine.combinarYRegistrar`
  aplica siempre el delta y solo registra el conflicto (no auto-resuelto) +
  log cuando queda negativo. `EventoAditivoCombinerTest` +2,
  `EventoAditivoSyncEngineTest`. (La rama "borrado vs edición" y la
  integración al merge se cierran en el Grupo 3.)
- [x] Resumen de pendientes antes del cambio de modo (D3.2). Hecho
  (2026-09-10/11): `SyncPendientesResumen` (cuenta por entidad vía los
  `EntityPusher`, `SyncPendientesResumenTest`); el reprogramado del
  `SyncWorker` al cambiar de modo se movió a `PdvApplication` (colecta
  `deviceConfig.backendMode` con `distinctUntilChanged`). El diálogo de
  confirmación se integró en `ConfiguracionViewModel`/`ConfiguracionScreen`
  junto con el Grupo 5 (evitó tocar el constructor de `ConfiguracionViewModel`
  dos veces) y quedó verificado en el Xiaomi (ver Grupo 5, sub-pasos 15/17).
- [x] `jvm-tests` del orquestador (con `*ApiService`/DAO fake): push happy
  path, reintento sin duplicar, `403` salta una fila y sigue, conflicto
  registrado en `sync_conflicts` + log `SYNC_CONFLICT`. Hecho (2026-09-10):
  cubierto entre `DefaultSyncOrchestratorTest` (orden + mapeo de errores),
  `VentaPusherTest` (403 salta y sigue, reintento por idempotencia,
  IOException/401 cortan) y `EventoAditivoSyncEngineTest` (conflicto +
  `SYNC_CONFLICT`). Suite Android **408 / 0 / 0**, `assembleDebug` verde.

**3. Pull de cambios remotos** (POS-149)
- [x] `docs/api-contract.md`: definir el parámetro de delta
  (`?updated_since=<iso8601>`, distinto de `desde`/`hasta` que filtran por
  fecha de negocio) en `GET /inventario`, `GET /cortes-caja` y
  `GET /retiros-efectivo` (D3.3: el pull se limita a las entidades que ya
  tienen `GET`; `GET /ventas`/`GET /entradas` quedan para follow-up).
  Contrato primero (skill `sync-api-contract`), luego backend, luego cliente
  Kotlin (`CLAUDE.md` §9). `needs-approval`. Aprobado e implementado
  (2026-09-10): §7.1 gana `updated_since` + el campo `updated_at` en cada
  item (hoy no lo traía; es el cursor + base del merge); §8.4/§8.5 ganan
  `updated_since` (por `updated_at`, coexiste con `desde`/`hasta`).
- [x] Backend: filtro `updated_since` en `inventario` y `caja` (cortes +
  retiros), con test happy path + 1 caso de error (`422` timestamp inválido)
  por endpoint tocado (`CLAUDE.md` §6). Sin cambios de esquema esperados.
  Hecho (2026-09-10): `list_inventario`/`list_cortes_caja`/`list_retiros_efectivo`
  con `updated_since: datetime | None`; `InventarioItemResponseSchema` gana
  `updated_at`. `uv run alembic check` sin drift (`updated_at` ya era
  columna); `uv run pytest` → **164 passed** (+6: filtro + `422` por
  endpoint).
- [x] Android: descarga incremental y merge a Room a través del motor de
  sync; divergencias a `sync_conflicts` (Room) + log `SYNC_CONFLICT`.
  `jvm-tests`. Hecho (2026-09-10): `sync/pull/` — `EntityPuller` +
  `InventarioPuller` / `CorteCajaPuller` / `RetiroPuller`; paginan con el
  cursor `updated_since` de `SyncStateStore` y lo avanzan al `max(updated_at)`.
  Inventario: fila ausente → insert `isSynced=true`; fila limpia →
  overwrite; fila sucia que difiere → `EventoAditivoSyncEngine.registrarDivergenciaLocalGana`
  (conflicto no auto-resuelto + `SYNC_CONFLICT`, gana lo local). Cortes/retiros:
  insert-if-absent por `local_id`/`id`. `DefaultSyncOrchestrator` corre push
  y luego pull, acotado a `sucursalIdSeleccionada` (sincroniza con las demás
  terminales de la misma sucursal). `InventarioPullerTest`,
  `SyncPullersHappyPathTest`, `DefaultSyncOrchestratorTest` (push→pull +
  errores). Suite **417 / 0 / 0**. Follow-up anotado: un artículo remoto
  desconocido localmente se omite (no propaga el catálogo); el merge por
  deltas con baseline (`combinarYRegistrar`) se retoma cuando se trackee el
  valor remoto de última sincronización.

**4. Autenticación del push desatendido** (POS-150)
- [x] El `SyncWorker` adjunta `Authorization: Bearer` (enforcement Parte 21)
  en las llamadas de push/pull del modo `LOCAL_CON_SINCRONIZACION`. Se
  verifica de forma indirecta pero concluyente en la verificación e2e del
  Grupo 5 (2026-09-11): todos los `POST`/`GET` de push y pull pasan por el
  mismo cliente Retrofit con `AuthInterceptor` (Parte 21), y el backend
  exige `Authorization: Bearer` en toda ruta fuera de `/auth`/`/health`
  (`401` sin él) — las docenas de `201`/`200` observados (ventas, entradas,
  cortes, retiros, devoluciones, pull) no habrían sido posibles sin el
  header adjunto. `SessionStore` (sub-paso siguiente) es lo que le da al
  `SyncWorker` una sesión con token tras un reinicio del proceso.
- [x] `SessionStore`: persiste la sesión (incluido el `accessToken`) cifrada
  con `TokenCipher`/AndroidKeystore (patrón `IaPreferences`, Parte 14) y
  siembra `SessionManager` al arrancar el proceso (D3: sin `refresh_token`;
  re-login forzado al expirar/401). Criterio: reiniciar el proceso y que el
  worker siga autenticando con el token persistido. Hecho (2026-09-10):
  `SessionStore` interfaz + `DataStoreSessionStore` (username/usuarioId/rolId
  en claro, `accessToken` cifrado Base64 vía `TokenCipher`) + `SessionStore.NoOp`;
  `SecurityModule` lo bindea; `SessionManager.restaurarSesion()` +
  persistencia best-effort en `iniciarSesion`/`logout` (firmas sync
  intactas, scope interno); `PdvApplication` restaura al arrancar.
  `SessionStoreTest` 5/5 + `SessionManagerTest` +3. El "reiniciar el
  proceso" en dispositivo se cierra en el Grupo 5. `jvm-tests`.
- [x] `ModeAwareAuthRepository`: en `LOCAL_CON_SINCRONIZACION` el login hace
  `local.login` (para operar offline) y, best-effort si el backend es
  alcanzable, `remote.login` para capturar el JWT que usará el worker. Hecho
  (2026-09-10): `loginLocalConTokenRemoto` — login local; si es exitoso,
  `runCatching { remote.login(...) }` y se adjunta el `accessToken` remoto si
  llegó. Cualquier fallo remoto (red, credenciales inexistentes en el
  backend, 5xx) deja una sesión local sin token, sin afectar el login.
  `ModeAwareAuthRepositoryTest` 7/7. `jvm-tests`.

**5. Estado de sync visible + verificación en dispositivo** (POS-151)
- [x] Sección "Sincronización" en Configuración: última sync correcta, nº de
  pendientes (suma de los `countPendientes()`), último error — leídos de
  `SyncStateStore`/Room — y botón "Sincronizar ahora" que encola un
  `OneTimeWorkRequest` único (D2). Hecho (2026-09-10): sección visible solo
  en `LOCAL_CON_SINCRONIZACION` (REMOTO no acumula pendientes); pendientes
  recalculado al entrar a la pantalla (`onRefrescarEstadoSync`, los DAO
  exponen `suspend fun` no `Flow`). Diálogo de confirmación del cambio de
  modo (D3.2, sub-paso 11 diferido) integrado en el mismo `ConfiguracionViewModel`:
  sin pendientes aplica directo, con pendientes muestra el resumen por
  entidad y pide "Cambiar de todos modos" / "Cancelar". `ConfiguracionViewModelTest`
  +7. Suite Android **423 / 0 / 0**, `assembleDebug` verde.
- [x] Verificación en el Xiaomi M2102J20SG, `LOCAL_CON_SINCRONIZACION` por
  Wi-Fi: crear venta/entrada/corte/retiro/devolución con el backend
  inalcanzable, restaurar conectividad, y comprobar que suben sin duplicar,
  que `isSynced` pasa a `true`, y que un cambio hecho en otro
  dispositivo/sucursal baja por el pull. `needs-device`. **En curso
  (2026-09-11)**: primer intento en el Xiaomi encontró 382 pendientes
  acumulados (historial real de las Partes 7-31, nunca sincronizado hasta
  ahora) y "Sincronizar ahora" terminó en `HTTP 500`. Causa raíz confirmada
  por log del contenedor (`docker compose logs backend`):
  `ForeignKeyViolationError` en `inventario_sucursal_id_fkey` — una fila
  local antigua trae un `sucursal_id` que no existe en el backend actual (el
  backend se recreó varias veces a lo largo del proyecto; datos escritos en
  modo `LOCAL` puro llevan el `local_id` de `LocalSucursalRepository`, sin
  relación con ningún backend). Como `POST /entradas`/`/ventas`/
  `/cortes-caja`/`/retiros-efectivo`/`/devoluciones` y `PATCH /inventario/{id}`
  no validaban `sucursal_id` antes de escribir (a diferencia de `articulo_id`,
  que sí), el `IntegrityError` no capturado escalaba a `500` sin capturar —y
  como `empujarFila` trata un `5xx` como error de ciclo completo (`Reintentar`),
  una sola fila envenenada bloqueaba las 381 restantes en cada ciclo. Corregido:
  los 6 endpoints ahora validan `sucursal_id` con `db.get(Sucursal, ...)` →
  `404` limpio si no existe (mismo patrón que la validación de `articulo_id`
  ya existente), que `empujarFila` ya trata como "descartar fila, loguear,
  seguir". No resuelve que esas filas históricas concretas puedan sincronizar
  algún día (su `sucursal_id` no corresponde a ningún backend real y nunca lo
  hará) — quedan como `saltadas` permanentes, sin bloquear el resto. `uv run
  pytest` → **170 passed** (+6: `sucursal_id inexistente → 404` por
  endpoint). Backend reconstruido (`docker compose up -d --build backend`) y
  redesplegado para el Xiaomi.

  Segundo hallazgo de la misma verificación, **sin relación con el motor de
  sync**: no se pudo registrar la devolución de prueba porque
  `DevolucionViewModel.buscar()` seguía sobre `CATALOGO_EJEMPLO` (3
  artículos hardcodeados del sub-paso 1 de la Parte 11) — el mismo gap ya
  encontrado y corregido en Venta y Entrada (Parte 16), pero nunca corregido
  en Devolución; ningún artículo real del catálogo podía encontrarse.
  Afecta a los tres modos por igual (`buscar()` no distingue `BackendMode`).
  Corregido en la misma sesión, mismo patrón que
  `VentaViewModel`/`EntradaViewModel`: `DevolucionViewModel` inyecta
  `InventarioRepository` y busca por código de barras/SKU/nombre contra el
  catálogo real, con el mismo mensaje "Selecciona una sucursal en
  Configuración" si no hay sucursal seleccionada. `DevolucionViewModelTest`
  reescrito (2 casos de `buscar()` nuevos + los 3 de `registrarDevolucion`
  ajustados para seleccionar sucursal antes de buscar). Suite Android
  **425 / 0 / 0**, `assembleDebug` verde.

  Tercer hallazgo, con ambos fixes ya instalados: con 379 pendientes,
  "Sincronizar ahora" no daba ninguna señal y el usuario lo tocó varias
  veces sin ver cambios. Evidencia (`docker compose logs backend`): **5139
  POSTs en 45 minutos**, la enorme mayoría `404` repetidos contra las
  mismas filas. Causa raíz confirmada — dos bugs de diseño del propio motor
  de sync, no del dato: (1) `empujarFila` descartaba una fila con `4xx`
  pero **nunca marcaba `isSynced`**, así que cada ciclo (periódico o manual)
  volvía a reintentar las ~370 filas irrecuperables desde cero; (2) el
  botón no exponía ningún estado de "corriendo"/"listo", y
  `sincronizarAhora()` encolaba con `ExistingWorkPolicy.KEEP` — si un
  intento previo seguía encolado, un tap nuevo no hacía nada, sin avisar.
  Corregido: cada pusher marca la fila como descartada
  (`marcarXDescartada`/`marcarMovimientoSincronizadoSinRemoto`, `isSynced =
  true` sin `remoteId`) cuando `empujarFila` la rechaza de forma no
  recuperable, así deja de reintentarse en cada ciclo;
  `sincronizarAhora()` pasa a `ExistingWorkPolicy.REPLACE`; `SyncScheduler
  .observarTrabajoInmediato()` (nuevo, `WorkInfo.State` vía
  `getWorkInfosForUniqueWorkFlow`) alimenta `ConfiguracionViewModel` para
  mostrar "Sincronizando…" (botón deshabilitado) y refrescar pendientes al
  terminar. Tests: `VentaPusherTest` (403 → descartada, no solo saltada),
  `InventarioAjustePusherTest` (+1: descarta un ajuste irrecuperable, sin
  tocar el caso legítimamente temporal de "artículo aún sin sincronizar"),
  `SyncSchedulerTest` (+2: `REPLACE`, `observarTrabajoInmediato`),
  `ConfiguracionViewModelTest` (+1: progreso + refresco al terminar).
  Suite Android **429 / 0 / 0**, `assembleDebug` verde. APK reinstalado.

  Resultado con los tres fixes: **382 → 1 pendiente**, "sincronizó bien"
  (usuario). Pero el log de la app en el dispositivo (`adb run-as ... cat
  files/logs/app-log-*.txt`, mismo camino que memoria previa) mostró
  `Sync OK: subidas=0, saltadas=379` — **ninguna fila subió de verdad**,
  las 379 se descartaron. Entre ellas, una devolución creada en la propia
  sesión de prueba (`D-1789099884662`, con el catálogo real ya arreglado)
  reintentada ~10 veces en 20 minutos, siempre `404`.

  **Cuarto hallazgo, más grave que los anteriores** — no es dato viejo, es
  un bug de diseño del push que afecta a *toda* venta/devolución con
  líneas, de cualquier antigüedad: `VentaPusher`/`DevolucionPusher`
  mandaban `articulo_id` (y `DevolucionPusher` además `venta_id`) con el id
  **local** de Room tal cual, sin resolverlo al id remoto — a diferencia de
  `EntradaPusher`, que sí lo hacía. El backend nunca podía encontrar ese
  artículo/venta → `404` → con el fix anterior, **descartada
  permanentemente en el primer intento**, silenciosamente marcada
  `isSynced = true` sin haber llegado nunca al backend. Corregido:
  `SyncMappers.toCreateRequestDto` para venta/devolución recibe una función
  de resolución local→remoto; `VentaPusher`/`DevolucionPusher` la resuelven
  vía `dao.getArticulo(...)`/`dao.getVentaRemoteId(...)` antes de mapear, y
  si algún artículo (o la venta referenciada) todavía no tiene `remoteId`,
  **aplazan** la fila entera (no la descartan) hasta el próximo ciclo, en
  vez de mandar un id que el backend no puede resolver. Tests:
  `SyncMappersTest`, `VentaPusherTest`, `DevolucionPusherTest` (nuevo) — casos
  de resolución y de aplazamiento. Suite Android **436 / 0 / 0**,
  `assembleDebug` verde. APK reinstalado.

  Efecto colateral aceptado: la devolución de prueba `D-1789099884662` ya
  quedó descartada por el fix anterior antes de este arreglo — no
  resincroniza sola; para volver a probarla hay que crear una devolución
  nueva. Es dato de prueba, sin impacto real.

  **Verificación con los cuatro fixes (2026-09-11): confirmada.** Venta +
  devolución nuevas creadas offline, reconectado y sincronizado:
  `Sync OK: subidas=2, saltadas=1` en el log del dispositivo; backend
  confirma `POST /ventas` y `POST /devoluciones` en `201 Created` con los
  folios exactos (`V-1789149864361`, `D-1789149885440`); sin filas
  duplicadas por `local_id` en Postgres. El único pendiente restante es el
  ajuste huérfano ya explicado (artículo de una entrada histórica
  descartada, nunca tendrá `remoteId` — inofensivo, no genera tráfico).

  **Pull cross-terminal: confirmado (2026-09-11).** Se simuló un retiro de
  efectivo creado "desde otra terminal" (POST directo contra el backend con
  el JWT del admin, misma sucursal, sin pasar por el Xiaomi). Se verificó
  con `adb exec-out run-as ... cat databases/pdv.db{,-wal,-shm}` (mismo
  camino que la técnica de memoria previa, esta vez incluyendo los sidecars
  de WAL — sin ellos `sqlite3`/`python` reportan "database disk image is
  malformed") que la fila llegó a Room local con su `remoteId` correcto.
  La confusión inicial ("no aparece") fue de la propia inspección, no del
  motor: Caja no tiene una lista general de retiros, solo el total del
  corte en curso (acotado a su período) y el export CSV (acotado a un rango
  de fechas) — el retiro simulado llevaba una `fecha` fuera de ambos
  períodos consultados al principio. Al hacer un corte parcial nuevo (cuyo
  período sí cubría esa fecha), el retiro de prueba apareció en el total.
  Push y pull confirmados de punta a punta con evidencia directa de base
  de datos, no solo de logs.

  **Hallazgo lateral, fuera del motor de sync**: la confusión de arriba
  ("18:14" vs. la hora real, 12:14 en México UTC-6) llevó a notar que
  `CajaCsvExporter` mostraba las fechas en UTC crudo (`Instant.toString()`,
  decisión deliberada de la Parte 18, "Fechas en ISO-8601 UTC") — confuso
  para un negocio de una sola sucursal en una sola zona horaria. Corregido
  (2026-09-11): `CajaCsvExporter.generar` recibe una `ZoneId` (default
  `ZoneId.systemDefault()`, la del dispositivo) y formatea con
  `DateTimeFormatter.ISO_OFFSET_DATE_TIME` — hora local con el offset
  explícito (ej. `2026-09-11T12:14:28-06:00`), no solo local sin offset, así
  el archivo sigue siendo inequívoco si se reprocesa en otra zona.
  `CajaCsvExporterTest` +1 (zona fija `-06:00`, verifica el corrimiento
  exacto). El resto de la app (pantallas, ticket PDF, log) ya mostraba hora
  local correctamente vía `SimpleDateFormat` sin zona explícita (usa la del
  dispositivo por defecto) — el export era el único punto con UTC crudo.
  Suite Android **437 / 0 / 0**, `assembleDebug` verde. APK reinstalado.
- [x] Verificación en el Xiaomi: cambio de modo `LOCAL` ↔
  `LOCAL_CON_SINCRONIZACION` muestra el resumen de pendientes y, al
  confirmar, sube los datos reales (ventas/inventario/cortes) sin pérdida.
  `needs-device`. Verificado (2026-09-11): con el pendiente huérfano
  presente, el diálogo apareció con su resumen; "Cambiar de todos modos" /
  "Cancelar" funcionaron correctamente (usuario).

### Decisiones abiertas

*(Evaluadas por `code-architect` el 2026-09-09 — 2-3 enfoques por decisión —
y resueltas con el usuario en la misma sesión. `CLAUDE.md` §11.)*

- [x] **D1 — Orden y acoplamiento push/pull.** Decidido: un solo `SyncWorker`
  con ciclo ordenado push→pull y un único `UniqueWork`; el merge del pull
  siempre keyed por `local_id` (queda listo para partir en dos workers
  después sin retrabajo). El pull corre aunque el push falle parcialmente,
  acumulando los errores.
- [x] **D2 — Disparadores del `SyncWorker`.** Decidido: periódico (~15 min) +
  `Constraints` de conectividad + backoff, MÁS un botón "Sincronizar ahora"
  en Configuración que encola un `OneTimeWorkRequest` único. Sin one-time
  automático en foreground/tras venta por ahora.
- [x] **D3 — Persistencia de sesión para el worker.** Decidido: persistir el
  JWT actual cifrado con AndroidKeystore (patrón Parte 14) + re-login forzado
  al expirar/401. Sin `refresh_token` en esta Parte (se agrega después sobre
  esta misma base; sigue diferido en `api-contract.md` §13). Un dispositivo
  offline > 24 h no sincroniza hasta el próximo login interactivo — aceptado.
- [x] **D4 — Conflictos no auto-resolubles.** Decidido: regla fija, sin UI
  nueva (es lo ya aprobado en la Parte 6). El delta se aplica (stock negativo
  transitorio), se registra en `sync_conflicts` con
  `resueltoAutomaticamente = false` + log `SYNC_CONFLICT`, y el admin corrige
  con un ajuste normal (Parte 9). Borrado local vs edición remota:
  last-write-wins por `updatedAt` sobre el tombstone. El panel de la Parte 19
  sigue solo-lectura.
- [x] **D5 — Infra de test de Room en JVM.** Decidido: no se agrega
  Robolectric. La lógica de orquestación/merge/conflicto/gating se prueba en
  JVM con fakes (`jvm-tests`, MockK); solo las queries `WHERE isSynced = 0` y
  las migraciones quedan como instrumentado `needs-device`. Los ítems DAO del
  checklist se etiquetan `needs-device` en consecuencia.
- [x] **D3.1 — Cómo obtener el `remote_id` tras el push.** Decidido: pushers
  dedicados en `sync/push/` que usan los `*ApiService` (ya devuelven el DTO
  con `id`) + un `SyncMappers.kt` `internal` que mapea `Entity` →
  `*CreateRequestDto` directo. `Local*`/`Remote*`/`ModeAware*` intactos.
- [x] **D3.2 — `MigrationPlanner`.** Decidido: la idempotencia de la Parte 23
  eliminó el hazard de "ambos lados con datos", así que el ítem se
  reinterpreta a un resumen de pendientes pre-toggle + confirmación. Los 4
  casos de `MigrationPlanner`/`PlanMigracion` siguen solo para sucursales,
  fuera del alcance de esta Parte.
- [x] **D3.3 — Alcance del pull.** Decidido: se limita a `inventario` /
  `cortes-caja` / `retiros-efectivo` (ya tienen `GET`) + `?updated_since=`.
  `GET /inventario` ya da existencia consolidada cross-sucursal. `GET /ventas`
  y `GET /entradas` nuevos quedan para follow-up, salvo que la verificación
  en dispositivo del Grupo 5 los exija.
- [x] **Gap 1 — Idempotencia de `devoluciones`.** Decidido: entra en esta
  Parte (Grupo 2) — la Parte 23 no la cubrió y el push la necesita. Migración
  Alembic `UNIQUE(local_id)` + `IntegrityError` en el router + `local_id`
  obligatorio + `schema-parity` + contrato §9.

---

## Parte 33: Primer release productivo (APK firmado + backend desplegado)

*(Sale del análisis "qué falta para el primer release productivo" del
2026-09-08. Absorbe el hallazgo B-7 —R8/shrinking, diferido en la Parte 29
"hasta que haya distribución real"— y cierra el `[TODO]` de destino de
despliegue de `CLAUDE.md` §7. Hoy `./gradlew assembleRelease` produce un APK
sin firmar y sin ofuscar, y el modo REMOTO de la app depende de
`adb reverse` contra `localhost:8000`.)*

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

**3. URL base productiva en la app**
- [ ] El módulo Configuración permite fijar y persistir la URL base del
  backend remoto (`https`, host, puerto), o se hornea un valor productivo por
  defecto. Criterio: en el Xiaomi, modo REMOTO contra `https://<dominio>`
  completa login + una operación de sync sin `adb reverse`. `needs-device`.
- [ ] Cleartext HTTP deshabilitado en release (network security config /
  `usesCleartextTraffic=false`), con allowlist explícito solo si se decide
  mantener pruebas locales. Criterio: una petición `http://` a un host
  productivo falla; `https://` funciona.

**4. Endurecimiento del build de release (hallazgo B-7)**
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
