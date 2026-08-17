# PLAN.md — Plan de desarrollo del Punto de Venta

Este documento vive en `docs/PLAN.md` y contiene el diseño en desarrollo activo
del proyecto. A diferencia de `CLAUDE.md` (contexto estable: arquitectura ya
decidida, convenciones, comandos), aquí se documentan features en fase de
diseño, decisiones que aún pueden iterar, y el checklist de avance. Cuando una
parte de este documento se estabiliza, su resumen final se migra a
`CLAUDE.md` y esta sección queda como bitácora histórica.

**Estado del documento**: reestructurado y con las políticas de arquitectura
principales acordadas. Pendiente: detalle campo-por-campo de los 7 módulos
del POS (Parte 4) y checklists granulares de subpasos con pruebas y criterios
de éxito por cada Parte — se trabajarán como siguiente paso.

---

## Parte 1: Plan

Amplía este documento para planificar cada una de estas partes en detalle,
con los subpasos enumerados en forma de lista de verificación que el agente
deberá marcar, y con pruebas y criterios de éxito para cada uno. Asegúrate de
que el usuario revise y apruebe el plan.

---

## Parte 2: Estructura básica

Configura la infraestructura de Docker, el backend en la carpeta `backend/`
con FastAPI, y escribe los scripts de inicio y detención en el directorio
`scripts/`. Esto debería mostrar un ejemplo de texto estático para confirmar
que el ejemplo "hello world" funciona al ejecutarse localmente, y también
realizar una llamada a la API.

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
  Configuración (ver Parte 4, módulo 7). Es un dato de identidad del
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
Parte 6b), para que el administrador pueda auditar qué pasó.

---

## Parte 4: Incorporación al frontend

Ahora actualiza el código para que el frontend se compile y se ejecute en la
UI Compose nativa, de modo que la aplicación muestre el frontend de un punto
de venta de demostración, usando datos estáticos alineados al esquema
definido en la Parte 3 (para no rehacer la UI cuando se conecte a datos
reales), en donde se simulen los siguientes módulos:

1. **Venta de mostrador**: escanear un barcode con la cámara o ingresar la
   descripción, y mostrar 5 datos estáticos relacionados a un producto.
2. **Entrada de mercancía**: simular la entrada de artículos nuevos
   (agregándolos al maestro de datos y luego al inventario) y de artículos
   existentes (agregándolos solo al inventario), con los datos principales
   del artículo y el estante de almacenamiento.
3. **Inventario**: consultar el inventario, la existencia de artículos
   específicos, y modificar los atributos de esos artículos consultados.
4. **Caja**: realizar un corte de caja de lo vendido en un periodo
   especificado.
5. **Devoluciones**: registrar devoluciones de clientes y consultar la lista
   de productos en esta condición para gestionar la devolución con el
   proveedor.
6. **Administración de usuarios**: CRUD de administrador y encargados de
   turno, y asignación de turnos. Las entradas/salidas de mercancía deben
   quedar asociadas al usuario en turno.
7. **Configuración**: administrar si la app corre completamente local,
   completamente remota, o local con sincronización de base de datos.
   Configurar los parámetros de FastAPI (IP del servidor, puerto, nombre de
   base de datos, `sucursal_id` del dispositivo). Administrar permisos por
   tipo de usuario y roles personalizados. Cerrar sesión.

**Nota de alcance**: en esta parte, todos los módulos —incluyendo el toggle
de modo local/remoto/sync del módulo 7— son solo interfaz simulada con datos
estáticos, sin lógica real de cambio de modo ni persistencia todavía. Esa
lógica se conecta en las Partes 6 y 7. El agente no debe implementar
sincronización real de forma anticipada en esta parte.

Presenta una propuesta de cada módulo (campos, tipo, layout) antes de
implementar. Espera retroalimentación y aprobación del usuario para
continuar. Realiza pruebas exhaustivas de unidad e integración.

---

## Parte 5: Experiencia de inicio de sesión (usuario ficticio)

Actualiza el código para que, al acceder por primera vez a la app, sea
necesario iniciar sesión con credenciales ficticias ("admin", "password";
"user1", "password") para poder ver la aplicación de punto de venta, y para
que sea posible cerrar sesión. Realiza pruebas exhaustivas.

---

## Parte 6a: Repositorio local

Implementa la capa de datos local (Room) para cada módulo del punto de
venta, siguiendo el patrón de repositorio definido en `CLAUDE.md`: una
interfaz de dominio (`XRepository`) y su implementación `LocalXRepository`,
sin ninguna dependencia de red. Si la base de datos local (Room) no existe al
arrancar, debe crearse automáticamente. Prueba esto a fondo con pruebas
unitarias (JUnit5 + MockK).

---

## Parte 6b: Repositorio remoto y sincronización

Implementa las rutas del backend FastAPI necesarias para exponer cada módulo
del punto de venta (documentadas primero en `docs/api-contract.md`), y la
implementación `RemoteXRepository` en Android que las consume vía Retrofit.
Si la base de datos remota no existe, debe crearse vía migraciones. Prueba a
fondo con pruebas unitarias del backend (pytest) y de Android (JUnit5 +
MockK).

### Política de resolución de conflictos

- **Entidades compartidas sin ambigüedad de cantidad** (precios, usuarios,
  permisos): `last-write-wins` por `updated_at`.
- **Entidades de cantidad/movimiento** (inventario, ventas, cortes de caja,
  devoluciones): dado que puede haber varios dispositivos operando
  simultáneamente en la misma sucursal, estas entidades se tratan como
  **eventos que se aplican/suman**, no como estado final que se sobreescribe
  — evita que un dispositivo "pierda" una venta o ajuste real por llegar
  segundo a sincronizar.
- Todo conflicto detectado (auto-resuelto o no) se registra en la tabla
  `sync_conflicts` (Parte 3) y en el log de la Parte 12.
- Un panel de revisión manual de conflictos queda como mejora futura, no
  bloqueante para el MVP.

### Migración de datos al cambiar de modo

1. **Hay datos locales, no hay datos remotos**: se suben los datos locales a
   la nube; si la base de datos remota no existe, se crea.
2. **Hay datos remotos, no hay datos locales**: se descargan los datos
   remotos al dispositivo.
3. **Hay datos en ambos lados**: se requiere confirmación del usuario
   administrador (validada con contraseña) indicando en qué dirección se
   sincroniza.
4. **En todos los casos**, la app muestra un diálogo de confirmación con el
   impacto concreto de la operación antes de ejecutarla (ej. "Se subirán 340
   registros de inventario y 12 usuarios a la nube" / "Se descargarán 1,204
   registros a este dispositivo"), no solo un "¿confirmar sí/no?" genérico.

---

## Parte 7: UI + Local + Servidor

Conecta los `ViewModel` de cada módulo (hasta ahora usando datos estáticos de
demo) a los casos de uso reales, que a su vez dependen de la interfaz de
repositorio (`XRepository`) definida en la Parte 6a. La implementación
inyectada (`LocalXRepository` o `RemoteXRepository`) se resuelve según el
`BackendMode` guardado en DataStore, configurado por el usuario en el módulo
de Configuración. Para el modo "local con sincronización", conecta también
el motor de sync de la Parte 6b, incluyendo los diálogos de confirmación de
migración de datos.

La app debe comportarse como un punto de venta persistente real en los tres
modos. Realiza pruebas de integración exhaustivas cubriendo los tres modos
por separado.

---

## Parte 8: Gestión de usuarios (real)

Agrega al proyecto la funcionalidad completa de multiusuarios: administración
de usuarios, roles personalizados (Administrador, encargado de turno, y
roles adicionales configurables), permisos por rol, y módulos
activados/desactivados según esos permisos. Esta parte se adelanta respecto
al orden original del proyecto para que exista un sistema de permisos real
antes de dar autonomía de escritura a la IA (Partes 9-11). Realiza pruebas
exhaustivas de las funcionalidades implementadas, manteniendo un buen
ambiente de integración.

---

## Parte 9: Conectividad de IA

Permite que el backend realice una llamada de IA a través de DeepSeek.
Prueba la conectividad con una prueba sencilla de "2+2" y asegúrate de que
la llamada de IA funcione.

---

## Parte 10: Refinamiento de IA

Amplía la llamada al backend para que siempre envíe a la IA información en
formato JSON desde el punto de venta, además de la pregunta del usuario (y
el historial de la conversación). La IA debe responder con salidas
estructuradas que incluyan la respuesta al usuario y, opcionalmente, una
actualización del punto de venta de acuerdo a la instrucción.

**Validación de permisos (obligatoria)**: toda actualización del punto de
venta propuesta por la IA debe validarse contra los permisos del usuario en
turno (Parte 8) antes de aplicarse. Las acciones rechazadas por falta de
permiso se registran en el log de la Parte 12 (categoría `AUTH`) y se le
informa al usuario en el chat por qué no se ejecutó.

Realiza pruebas exhaustivas.

---

## Parte 11: Chat IA

Agrega un widget flotante a la interfaz de usuario que permita el chat
completo con IA y permita que el LLM actualice el punto de venta basándose
en sus resultados estructurados (Parte 10). Si la IA actualiza el punto de
venta, la interfaz de usuario debe actualizarse automáticamente cuando
aplique.

### Comportamiento por modo y conectividad

- **Modo remoto o local-con-sincronización, con conexión**: chat completo
  disponible, incluyendo actualizaciones al punto de venta (sujetas a
  validación de permisos de la Parte 10).
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

## Parte 12: Logs de la aplicación

Genera un sistema de logs que capture:

- Eventos que afectan directamente a la base de datos.
- Issues de comunicación (fallas de red, timeouts).
- Errores de escritura y conflictos con el sistema de sincronización.

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

---

## Backlog (trabajo futuro, fuera del alcance actual)

- **RAG para el chat de IA**: indexar `docs/` del proyecto más una fuente de
  documentación externa combinada, para mejorar las respuestas de
  funcionalidad de la app en modo local con conexión, reemplazando el
  contexto de FAQ estático usado en la Parte 11. No se implementa en esta
  fase.
- **Panel de revisión manual de conflictos de sincronización** (mencionado
  en la Parte 6b).

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
