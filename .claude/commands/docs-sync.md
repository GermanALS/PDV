---
description: Actualiza el manual técnico (MD) contra el código con alcance global fijo y regenera su HTML y PDF, con confirmación humana.
argument-hint: "<Parte N> | check"
allowed-tools: Read, Edit, Grep, Glob, Task, Agent, Bash(git diff:*), Bash(git log:*), Bash(git status:*), Bash(git rev-parse:*), Bash(uv run scripts/docs/build_manual.py:*)
---

# /docs-sync

Mantiene el manual técnico alineado con el código. Se corre al terminar una
Parte completa, **antes** de `/jira-sync`.

- Fuente editable: `docs/manual-tecnico.md`
- Derivados: `docs/manual-tecnico.html`, `docs/manual-tecnico.pdf`
- Alcance: `docs/ALCANCE-DOCS.md` (siempre completo, nunca parcial)

## Invariantes

1. Tú no lees código ni el manual: delegas en el subagente `doc-maintainer`
   para mantener limpio el contexto de esta sesión.
2. Nada se genera ni se registra sin un "sí" explícito del usuario.
3. El HTML y el PDF solo los produce `scripts/docs/build_manual.py`. Si su
   salida es incorrecta, se corrige el Markdown o la plantilla, nunca el derivado.
4. No haces commit ni push.

## Fase 0 — Preparación

1. Determina el modo:
    - `check` -> modo check.
    - Argumento de Parte (ej. `Parte 34`) -> modo update.
    - Sin argumento -> pregunta qué Parte se terminó y **detente**.
2. Solo modo update: ejecuta `git status --porcelain -- . ":!docs"`.
   Si hay cambios de código sin commit, detente y pide al usuario hacer commit
   del trabajo de la Parte primero. El manual debe describir un commit
   concreto; de lo contrario el registro de sincronización queda ambiguo y el
   hook de push lo marcará como desactualizado.

## Fase 1 — Delegar

Invoca al subagente `doc-maintainer` con: modo, Parte y cualquier instrucción
de ajuste del usuario.

En modo `check`, ejecuta además `uv run scripts/docs/build_manual.py --check`,
muestra ambos resultados y **termina**. No generes nada.

## Fase 2 — Presentar y DETENERSE (modo update)

Muestra el reporte del subagente y `git diff --stat -- docs/manual-tecnico.md`.
Termina el turno preguntando si se aceptan los cambios. Solo "sí" es
confirmación; "ok", "continúa" o el silencio no lo son.

Si el usuario pide ajustes, vuelve a invocar al subagente con esas
instrucciones y regresa a esta fase.

## Fase 3 — Tras la confirmación

1. Genera los derivados: `uv run scripts/docs/build_manual.py`.
    - Chromium ausente -> indica
      `uv run --with playwright==1.56.0 playwright install chromium` y detente.
    - Diagramas Mermaid rotos -> muestra el error, no sigas y pregunta si el
      subagente debe corregirlos.
2. Verifica: `uv run scripts/docs/build_manual.py --check` debe responder
   "Manual al día". Si no, detente y reporta.
3. Actualiza la línea del manifiesto con una edición quirúrgica (única edición
   que haces tú en todo el comando):
   `<!-- docs-sync: commit=<git rev-parse --short HEAD> parte="<Parte>" fecha=<YYYY-MM-DD> -->`
4. Reporte final en 3-4 líneas: secciones tocadas, archivos listos para el
   commit de cierre (`docs/manual-tecnico.md`, `.html`, `.pdf`,
   `docs/ALCANCE-DOCS.md`) y siguiente paso sugerido: `/jira-sync <Parte>`.

Si el usuario rechaza los cambios, no los reviertas tú: indícale
`git restore docs/manual-tecnico.md` y detente.