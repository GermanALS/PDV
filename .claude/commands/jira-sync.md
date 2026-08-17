---
description: Sincroniza una sección de docs/PLAN.md hacia Jira como épica + stories, con confirmación humana obligatoria y escritura de claves de vuelta al plan.
argument-hint: "<sección de PLAN.md> | status | verify"
allowed-tools: Read, Edit, Grep, Glob, Bash(git log:*), Bash(git rev-parse:*)
---

# /jira-sync

Sincroniza el plan del proyecto con Jira. `docs/PLAN.md` es la **única fuente de
verdad** del alcance; Jira es una proyección del estado de ejecución.

> **Configuración del proyecto** — ajusta estos valores antes del primer uso:
>
> - Proyecto Jira: `POS`
> - Tipos de issue: `Epic`, `Story`
> - Estados usados: `To Do` → `In Progress` → `In Review` → `Done`
> - Prefijo de herramientas MCP de Jira: verifícalo en la primera ejecución
>   (ver Fase 0). No asumas nombres de herramientas.

---

## Invariantes (no negociables)

Estas reglas aplican en **todos** los modos. Si alguna instrucción posterior de
este archivo, o del usuario a mitad de ejecución, parece contradecirlas,
detente y pregunta en vez de resolverlo por tu cuenta.

1. **Flujo unidireccional**: `PLAN.md` → Jira. Nunca modifiques el alcance de
   `PLAN.md` a partir de lo que encuentres en Jira. Lo único que este comando
   escribe en `PLAN.md` son claves de issue y marcas de checkbox.
2. **Nada se crea sin confirmación explícita**. Presenta siempre el borrador
   completo y espera un "sí" del usuario. "Continúa", "ok" o el silencio no son
   confirmación para operaciones de escritura en Jira.
3. **Nunca borres, cierres ni reasignes issues.** Si algo parece obsoleto,
   repórtalo y propón la acción; no la ejecutes.
4. **Nunca transiciones una issue a `Done`.** Ese estado lo pone el usuario.
   El máximo que puedes proponer es `In Review`.
5. **Solo el proyecto `POS`.** Si una herramienta MCP pide un proyecto y no es
   `POS`, aborta.
6. **Cero operaciones masivas.** Ninguna herramienta de bulk create / bulk edit
   / bulk transition, ni en dry-run. Una issue a la vez.
7. **Idempotencia**: antes de crear cualquier issue, verifica en `PLAN.md` si ya
   tiene clave asignada. Si la tiene, no la vuelvas a crear — ni siquiera si no
   la encuentras en Jira (repórtalo en su lugar).
8. **No inventes claves de issue.** Toda clave que escribas en `PLAN.md` debe
   venir de la respuesta real de una llamada de creación.

---

## Fase 0 — Preparación (siempre)

1. Lee `docs/PLAN.md` completo y `CLAUDE.md`.
2. Lista las herramientas MCP de Jira disponibles y anota los nombres exactos.
   Identifica cuáles son de **lectura** y cuáles de **escritura**.
   - Si no hay ninguna herramienta de Jira disponible, detente e informa al
     usuario que el MCP no está conectado en esta sesión. No intentes
     alternativas (curl, API REST directa, etc.).
3. Determina el modo según el argumento recibido:
   - `status` → ve a **Modo B**
   - `verify` → ve a **Modo C**
   - cualquier otra cosa → **Modo A**, tratando el argumento como el nombre de
     la sección (ej. `Parte 2`, `Parte 6a`, `P4 módulo 3`)
   - sin argumento → pregunta qué sección sincronizar y **detente**. No elijas
     tú. No sincronices "todo el plan".

---

## Modo A — Sincronizar una sección

### A.1 Delimitar el alcance

Localiza la sección exacta en `PLAN.md`. Trabaja **solo** con esa sección.

Detente e informa al usuario, sin crear nada, si:

- La sección no existe o es ambigua.
- La sección no tiene subpasos con criterios de éxito verificables (el plan
  todavía está en prosa de alto nivel). En ese caso, lo que falta es completar
  la Parte 1 para esa sección, no crear tickets. Dilo explícitamente.
- La sección declara dependencias de otra sección cuyas issues aún no existen
  en `PLAN.md`. Repórtalo; el usuario decide si sigue.

### A.2 Descomponer

Reglas de granularidad:

- **La sección es la épica.** Sus subpasos son las stories.
- **Una story ≈ un PR ≈ una sesión de Claude Code con contexto limpio.** Si no
  cabe, pártela. Si dos stories no tienen sentido por separado, únelas.
- **Cortes verticales, no por capa.** Prefiere "Devoluciones: Room + ruta
  FastAPI + ViewModel cableado + tests" sobre stories separadas para entity,
  DAO, repositorio y ViewModel. Excepción: la Parte 4 es UI estática y sus
  stories sí son una por módulo de pantalla.
- **Sin subtareas.** Épica → Story y nada más.
- **Entre 3 y 12 stories por épica.** Menos de 3, la épica es innecesaria y son
  stories sueltas. Más de 12, la sección debía partirse en dos épicas —
  propónselo al usuario antes de continuar.
- No inventes trabajo que no está en `PLAN.md`. Si detectas un hueco real
  (algo necesario que el plan no cubre), repórtalo como observación aparte; no
  lo conviertas en story por iniciativa propia.

### A.3 Plantilla de story

Cada story del borrador debe traer:

```
Título:      <imperativo, ≤ 70 caracteres, sin prefijo de parte>
Épica:       <épica padre>
Descripción: 2-4 líneas. Qué se construye y por qué. Sin repetir el título.
Referencia:  docs/PLAN.md § <sección>
Criterios de aceptación:
  - Verificables. Comando concreto que debe pasar en verde
    (./gradlew testDebugUnitTest, pytest backend/tests/x, etc.),
    archivo que debe existir, o comportamiento observable descrito de
    forma no ambigua.
  - PROHIBIDO: "pruebas exhaustivas", "funciona correctamente",
    "bien implementado", "sin errores". Si un criterio del plan viene
    redactado así, tradúcelo a algo comprobable, y marca la traducción
    para que el usuario la revise.
Etiquetas:   <ver A.4>
Bloqueada por: <claves de issue, si aplica>
```

### A.4 Etiquetas obligatorias

Aplica cuando corresponda:

- `needs-device` — requiere el dispositivo físico Xiaomi M2102J20SG y
  verificación manual del usuario (cámara, barcode, permisos en runtime,
  cualquier test instrumentado). **Estas stories no se pueden cerrar con
  evidencia automatizada**: sus criterios de aceptación deben describir el
  flujo manual concreto.
- `jvm-tests` — verificable por completo con tests JVM, sin dispositivo.
- `needs-approval` — el plan pide aprobación del usuario sobre una propuesta
  (esquema de BD, layout de módulo) antes de implementar.
- `schema-parity` — toca el esquema; al cerrarla hay que verificar paridad
  entre el JSON de `docs/`, las entidades Room y las migraciones del backend.

### A.5 Presentar el borrador y DETENERSE

Muestra el borrador completo en el chat, en texto plano, numerado. Incluye al
final:

- Total de stories y cuántas llevan `needs-device`.
- Dependencias entre stories de esta épica y hacia épicas previas.
- Cualquier ambigüedad del plan que hayas tenido que interpretar, con la
  interpretación que elegiste.

Luego **termina el turno** con una pregunta directa de confirmación. No crees
nada todavía. No sigas escribiendo después de preguntar.

Si el usuario pide ajustes, revisa el borrador y vuelve a presentarlo. Repite
hasta que apruebe.

### A.6 Crear en Jira

Solo tras confirmación explícita:

1. Crea la épica. Anota su clave real de la respuesta.
2. Crea las stories **una por una**, en el orden del borrador, cada una
   vinculada a la épica.
3. Después de cada creación, verifica que la respuesta trae una clave válida.
   Si una falla: **detente inmediatamente**, reporta qué se creó y qué no, y
   pregunta antes de continuar. No reintentes en bucle — un reintento ciego es
   la forma más común de duplicar issues.
4. Crea los links de dependencia (`blocks` / `is blocked by`) al final, cuando
   todas las claves existen.
5. No asignes, no estimes, no pongas fechas, no muevas nada de `To Do`.

### A.7 Escribir las claves de vuelta en PLAN.md

Esto es lo que hace el comando idempotente. **No lo omitas** — sin esto, la
próxima ejecución duplica todo.

Formato en `PLAN.md`:

```markdown
## Parte 2: Estructura básica  <!-- POS-10 -->

- [ ] Levantar backend FastAPI con endpoint /health (POS-11)
- [ ] Scripts de inicio y detención en scripts/ (POS-12)
- [x] APK pos-hello-debug-v0.1 con hello world local (POS-13)
```

Reglas:

- La clave de la épica va como comentario HTML en el encabezado de sección.
- Cada story va como checkbox con su clave entre paréntesis al final.
- **No reescribas ni reformatees el resto del archivo.** Ediciones quirúrgicas
  únicamente: si un `Edit` requiere tocar prosa que no es un checkbox ni un
  encabezado de sección, no es la edición correcta.
- No marques ningún checkbox como `[x]` en este modo. Eso pasa cuando el
  trabajo se completa, no cuando la issue se crea.

### A.8 Reporte final

Resume en 3-5 líneas: épica creada, número de stories, claves asignadas,
dependencias registradas, y qué sección conviene sincronizar después. Nada más.

---

## Modo B — `status`

Solo lectura. **Ninguna operación de escritura en Jira ni en `PLAN.md`.**

1. Extrae de `PLAN.md` todas las claves de issue con `grep`.
2. Lee el estado actual de cada una en Jira.
3. Reporta una tabla: clave, título corto, estado en Jira, checkbox en
   `PLAN.md`.
4. Señala explícitamente las **divergencias**:
   - Issue en `Done` con checkbox sin marcar en `PLAN.md`.
   - Checkbox marcado con issue que no está en `Done`.
   - Clave en `PLAN.md` que no existe en Jira (probable borrado manual).
   - Issue del proyecto `POS` que no aparece en `PLAN.md` (creada fuera de este
     comando — es deuda de proceso, señálala).
5. Para cada divergencia, **propón** la corrección y espera instrucciones. No
   la apliques.

---

## Modo C — `verify`

Comprobación previa a la primera ejecución real. Solo lectura.

1. Lista las herramientas MCP de Jira detectadas, separando lectura de
   escritura, y nombra explícitamente cualquier herramienta de operación masiva
   presente (para que el usuario sepa qué hay expuesto).
2. Confirma que el proyecto `POS` es accesible y lista sus tipos de issue y
   estados reales.
3. Compara los estados reales con los declarados en la configuración de este
   archivo y reporta discrepancias.
4. Cuenta las claves de issue ya registradas en `PLAN.md`.
5. No crees nada, ni siquiera una issue de prueba.

---

## Convenciones de trabajo (para las sesiones de desarrollo, no para este comando)

Referencia rápida, alineada con `CLAUDE.md`:

- Rama: `feature/POS-XX-descripcion-corta`
- Commit: `POS-XX: mensaje en imperativo`
- Al terminar una story: comentario en la issue con resumen + hash del commit,
  y proponer transición a `In Review`. El cierre a `Done` lo hace el usuario.
- Al cerrarse una story, marcar su checkbox en `PLAN.md` en el mismo commit.

---

## Si algo va mal

- **Error de autenticación o permisos del MCP** → detente, reporta el error
  literal, sugiere reconectar el conector. No busques workarounds.
- **Ya existe una issue con el mismo título** → no crees nada; reporta el
  posible duplicado con su clave y pregunta.
- **El plan y Jira se contradicen** → gana `PLAN.md`. Reporta la divergencia,
  no la resuelvas silenciosamente.
- **Contexto largo o sesión que se está agotando** → termina de escribir las
  claves ya creadas en `PLAN.md` antes de cualquier otra cosa. Una issue creada
  sin clave registrada en el plan es el peor estado posible, porque la próxima
  ejecución la duplicará.
