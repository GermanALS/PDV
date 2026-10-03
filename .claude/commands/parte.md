---
description: Implementa una Parte de docs/PLAN.md
argument-hint: [numero-de-parte]
disable-model-invocation: true
---

Implementa la Parte $ARGUMENTS de `docs/PLAN.md`.

Lee, en este orden:
1. La sección "Parte $ARGUMENTS" completa de `docs/PLAN.md`, incluyendo su
   `### Checklist` y su `### Decisiones abiertas` si existe.
2. `docs/api-contract.md`.
3. Las secciones de `docs/PLAN.md` que esa Parte referencie explícitamente.

Aplica las reglas de `CLAUDE.md` sección 9: el checklist es el alcance,
compuerta al final de cada sub-paso, las decisiones abiertas se preguntan,
y las etiquetas `needs-device` / `jvm-tests` / `needs-approval` /
`schema-parity` determinan cómo se cierra cada ítem.

Si la Parte es la 6, 7, 13, 15, 21, 23, 25, 32 o 34 (ver `CLAUDE.md` sección 11), además:

- Antes de implementar, delega al subagente `code-architect` la evaluación
  de las `### Decisiones abiertas` de esa Parte: debe proponer 2 o 3
  enfoques con sus compensaciones, no una sola recomendación. Preséntame
  esas opciones junto con las preguntas y espera mi elección.
- Antes de marcar los ítems de cada sub-paso, delega al subagente
  `code-reviewer` la revisión del código escrito en ese sub-paso.
  `CLAUDE.md` sección 9 (no programar a la defensiva) tiene precedencia
  sobre sus hallazgos.

Antes de escribir código: confírmame el alcance entendido y hazme las
preguntas de "Decisiones abiertas".
