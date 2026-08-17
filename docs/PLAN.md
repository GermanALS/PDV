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
(6-12) con sus propios 4 sub-pasos (UI → repositorio local → repositorio
remoto/contrato → wiring). Pendiente: detalle campo-por-campo del esquema de
datos (Parte 3, sin ejecutar ni aprobar todavía) y checklists granulares de
subpasos con pruebas y criterios de éxito por cada Parte — se trabajarán como
siguiente paso.

---

## Parte 1: Plan

Amplía este documento para planificar cada una de estas partes en detalle,
con los subpasos enumerados en forma de lista de verificación que el agente
deberá marcar, y con pruebas y criterios de éxito para cada uno. Asegúrate de
que el usuario revise y apruebe el plan.

---

## Parte 2: Estructura básica

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

---

## Parte 3: Modelado de base de datos

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

---

## Parte 4: Experiencia de inicio de sesión (usuario ficticio)

Actualiza el código para que, al acceder por primera vez a la app, sea
necesario iniciar sesión con credenciales ficticias ("admin", "password";
"user1", "password") para poder ver la aplicación de punto de venta, y para
que sea posible cerrar sesión. Realiza pruebas exhaustivas.

Se ubica aquí, antes de la infraestructura de logs (Parte 5) y del
desarrollo por módulo (Partes 6-12), porque el formato de log necesita el
concepto de "usuario en turno" desde el inicio, y varios módulos (ej.
entrada de mercancía, administración de usuarios) requieren atribuir
acciones a ese usuario desde su primera implementación.

---

## Parte 5: Logs de la aplicación

Genera un sistema de logs que capture:

- Eventos que afectan directamente a la base de datos.
- Issues de comunicación (fallas de red, timeouts).
- Errores de escritura y conflictos con el sistema de sincronización.

**Se construye aquí, antes de los módulos**, porque es infraestructura
compartida (igual que el motor de sync de la Parte 6): cada
`LocalXRepository`/`RemoteXRepository` de las Partes 6-12 necesita poder
escribir a este sistema desde el momento en que se implementa, en vez de
integrarlo después en 7 módulos ya construidos. Solo depende de la Parte 4
(login), de donde toma el campo `usuario` de cada línea.

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

---

## Parte 6: Módulo Configuración

Implementa de punta a punta el módulo de Configuración: parámetros de
conexión al backend FastAPI (IP, puerto, nombre de base de datos),
`sucursal_id` del dispositivo, selector de modo (local / remoto / local con
sincronización), gestión de permisos por tipo de usuario (interfaz
simulada por ahora — se conecta de verdad en la Parte 13), y cierre de
sesión.

**Este módulo va primero entre los 7 módulos del POS**, a diferencia del
orden original, porque las Partes 7-12 necesitan que `BackendMode` funcione
de verdad para poder probar sus modos remoto y local-con-sincronización.

1. **UI**: propuesta de pantalla (campos, tipo, layout) — aprobación del
   usuario antes de implementar.
2. **Persistencia local**: guardado real en DataStore de `BackendMode`,
   parámetros de conexión y `sucursal_id` (preferencia de dispositivo, no
   dato de dominio — no usa Room). Pruebas unitarias.
3. **Motor de sync genérico**: a diferencia de los demás módulos, aquí se
   implementa la lógica compartida que todas las Partes de módulo
   transaccional (7-11) reutilizarán, en vez de repetirse por módulo:
   - **Política de resolución de conflictos**:
     - Entidades compartidas sin ambigüedad de cantidad (precios, usuarios,
       permisos): `last-write-wins` por `updated_at`.
     - Entidades de cantidad/movimiento (inventario, ventas, cortes de caja,
       devoluciones): dado que puede haber varios dispositivos operando
       simultáneamente en la misma sucursal, se tratan como **eventos que
       se aplican/suman**, no como estado final que se sobreescribe — evita
       que un dispositivo "pierda" una venta o ajuste real por llegar
       segundo a sincronizar.
     - Todo conflicto detectado (auto-resuelto o no) se registra en la
       tabla `sync_conflicts` (Parte 3) y en el log (Parte 5, categoría
       `SYNC_CONFLICT`).
     - Un panel de revisión manual de conflictos queda como mejora futura,
       no bloqueante para el MVP.
   - **Migración de datos al cambiar de modo**:
     1. Hay datos locales, no hay datos remotos: se suben los datos locales
        a la nube; si la base de datos remota no existe, se crea.
     2. Hay datos remotos, no hay datos locales: se descargan los datos
        remotos al dispositivo.
     3. Hay datos en ambos lados: se requiere confirmación del usuario
        administrador (validada con contraseña) indicando en qué dirección
        se sincroniza.
     4. En todos los casos, la app muestra un diálogo de confirmación con
        el impacto concreto de la operación antes de ejecutarla (ej. "Se
        subirán 340 registros de inventario y 12 usuarios a la nube" / "Se
        descargarán 1,204 registros a este dispositivo"), no solo un
        "¿confirmar sí/no?" genérico.
   - Pruebas unitarias y de integración exhaustivas del motor de sync,
     independientes de cualquier módulo específico.
4. **Wiring**: a diferencia de los demás módulos, el selector de modo de
   esta pantalla queda conectado a la lógica real desde esta misma Parte
   (no es interfaz simulada) — es la base de la que dependen las Partes
   7-12.

---

## Parte 7: Módulo Venta de mostrador

Escanear un barcode con la cámara o ingresar la descripción del producto, y
mostrar sus datos principales alineados al esquema de la Parte 3.

1. **UI**: propuesta de pantalla con datos estáticos de ejemplo (mínimo 5
   campos del producto) — aprobación del usuario antes de implementar.
2. **Repositorio local**: `VentaRepository` (interfaz) + `LocalVentaRepository`
   (Room), sin dependencia de red, siguiendo el patrón de CLAUDE.md. Registra
   `DB_WRITE` en el log (Parte 5) en cada escritura. Pruebas unitarias
   (JUnit5 + MockK).
3. **Repositorio remoto**: actualiza `docs/api-contract.md`, implementa la
   ruta FastAPI y `RemoteVentaRepository` (Retrofit). Registra `ERROR`/`WARN`
   en el log (Parte 5) ante fallas de red o timeouts. Pruebas backend
   (pytest) y Android.
4. **Wiring**: conecta el `ViewModel` a los casos de uso reales según el
   `BackendMode` (Parte 6). En modo local-con-sincronización, las ventas se
   tratan como eventos aditivos (política de la Parte 6), no como estado
   sobreescribible.

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 8: Módulo Entrada de mercancía

Simular la entrada de artículos nuevos (agregándolos al maestro de datos y
luego al inventario) y de artículos existentes (agregándolos solo al
inventario), con los datos principales del artículo y el estante de
almacenamiento. Toda entrada queda asociada al usuario en turno (Parte 4).

1. **UI**: propuesta de pantalla — aprobación del usuario antes de
   implementar.
2. **Repositorio local**: `EntradaRepository` + `LocalEntradaRepository`
   (Room). Registra `DB_WRITE` en el log (Parte 5). Pruebas unitarias.
3. **Repositorio remoto**: actualiza `docs/api-contract.md`, implementa la
   ruta FastAPI y `RemoteEntradaRepository` (Retrofit). Registra `ERROR`/
   `WARN` en el log (Parte 5) ante fallas de red. Pruebas backend y Android.
4. **Wiring**: conecta el `ViewModel` según `BackendMode` (Parte 6).

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 9: Módulo Inventario

Consultar el inventario, la existencia de artículos específicos, y
modificar los atributos de esos artículos consultados.

1. **UI**: propuesta de pantalla — aprobación del usuario antes de
   implementar.
2. **Repositorio local**: `InventarioRepository` + `LocalInventarioRepository`
   (Room). Registra `DB_WRITE` en el log (Parte 5). Pruebas unitarias.
3. **Repositorio remoto**: actualiza `docs/api-contract.md`, implementa la
   ruta FastAPI y `RemoteInventarioRepository` (Retrofit). Registra `ERROR`/
   `WARN` en el log (Parte 5) ante fallas de red. Pruebas backend y Android.
4. **Wiring**: conecta el `ViewModel` según `BackendMode` (Parte 6).

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 10: Módulo Caja

Realizar un corte de caja de lo vendido en un periodo especificado.

1. **UI**: propuesta de pantalla — aprobación del usuario antes de
   implementar.
2. **Repositorio local**: `CajaRepository` + `LocalCajaRepository` (Room).
   Registra `DB_WRITE` en el log (Parte 5). Pruebas unitarias.
3. **Repositorio remoto**: actualiza `docs/api-contract.md`, implementa la
   ruta FastAPI y `RemoteCajaRepository` (Retrofit). Registra `ERROR`/`WARN`
   en el log (Parte 5) ante fallas de red. Pruebas backend y Android.
4. **Wiring**: conecta el `ViewModel` según `BackendMode` (Parte 6). Los
   cortes de caja se tratan como eventos aditivos en modo
   local-con-sincronización (política de la Parte 6).

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 11: Módulo Devoluciones

Registrar devoluciones de clientes y consultar la lista de productos en
esta condición para gestionar la devolución con el proveedor.

1. **UI**: propuesta de pantalla — aprobación del usuario antes de
   implementar.
2. **Repositorio local**: `DevolucionRepository` + `LocalDevolucionRepository`
   (Room). Registra `DB_WRITE` en el log (Parte 5). Pruebas unitarias.
3. **Repositorio remoto**: actualiza `docs/api-contract.md`, implementa la
   ruta FastAPI y `RemoteDevolucionRepository` (Retrofit). Registra `ERROR`/
   `WARN` en el log (Parte 5) ante fallas de red. Pruebas backend y Android.
4. **Wiring**: conecta el `ViewModel` según `BackendMode` (Parte 6).

Realiza pruebas de integración exhaustivas antes de pasar al siguiente
módulo.

---

## Parte 12: Módulo Administración de usuarios (demo)

CRUD de administrador y encargados de turno, y asignación de turnos. Este
CRUD persiste usuarios y turnos de verdad (repositorio local/remoto, como
los demás módulos); lo que se difiere a la Parte 13 es la aplicación real
de permisos por rol sobre los demás módulos.

1. **UI**: propuesta de pantalla — aprobación del usuario antes de
   implementar.
2. **Repositorio local**: `UsuarioRepository` + `LocalUsuarioRepository`
   (Room). Registra `DB_WRITE` en el log (Parte 5). Pruebas unitarias.
3. **Repositorio remoto**: actualiza `docs/api-contract.md`, implementa la
   ruta FastAPI y `RemoteUsuarioRepository` (Retrofit). Registra `ERROR`/
   `WARN` en el log (Parte 5) ante fallas de red. Pruebas backend y Android.
4. **Wiring**: conecta el `ViewModel` según `BackendMode` (Parte 6).

Realiza pruebas de integración exhaustivas.

---

## Parte 13: Gestión de usuarios (real)

Agrega al proyecto la funcionalidad completa de multiusuarios: administración
de usuarios, roles personalizados (Administrador, encargado de turno, y
roles adicionales configurables), permisos por rol, y módulos
activados/desactivados según esos permisos. Esta parte se adelanta respecto
al orden original del proyecto para que exista un sistema de permisos real
antes de dar autonomía de escritura a la IA (Partes 14-16). Las acciones
rechazadas por falta de permiso se registran en el log (Parte 5, categoría
`AUTH`). Realiza pruebas exhaustivas de las funcionalidades implementadas,
manteniendo un buen ambiente de integración.

---

## Parte 14: Conectividad de IA

Permite que el backend realice una llamada de IA a través de DeepSeek.
Prueba la conectividad con una prueba sencilla de "2+2" y asegúrate de que
la llamada de IA funcione.

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

Realiza pruebas exhaustivas.

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

Realiza pruebas exhaustivas de los tres escenarios de conectividad/modo.

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
