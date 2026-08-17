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

Aplica las reglas de `CLAUDE.md` §9: el checklist es el alcance, compuerta
al final de cada sub-paso, decisiones abiertas se preguntan, y las
etiquetas `needs-device` / `jvm-tests` determinan cómo se cierra cada ítem.
Si la Parte aparece en la lista de `CLAUDE.md` §11, sigue además el mapeo
de fases de feature-dev.

Antes de escribir código: confírmame el alcance entendido y hazme las
preguntas de "Decisiones abiertas".