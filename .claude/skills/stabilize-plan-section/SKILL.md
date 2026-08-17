---
name: stabilize-plan-section
description: Migrate a feature from docs/PLAN.md into CLAUDE.md once its design has stabilized (decisions made, no longer actively changing). Use when the user says a PLAN.md feature/section is done, decided, or ready to be finalized, or asks to update CLAUDE.md from PLAN.md.
---

`docs/PLAN.md` documenta features en diseño activo (opciones evaluadas,
decisiones pendientes, fases de implementación). Cuando el usuario indique
que una de esas secciones ya se estabilizó, sigue este proceso:

1. **Identifica la sección de `docs/PLAN.md`** correspondiente al feature
   señalado por el usuario ($ARGUMENTS si se especificó, si no, pregunta
   cuál sección).

2. **Extrae el resumen final**: de toda la sección (que puede incluir
   opciones descartadas, debates, alternativas evaluadas), destila solo la
   decisión final y su forma concreta — no el proceso de cómo se llegó ahí.

3. **Migra ese resumen a `CLAUDE.md`**, en la sección correspondiente
   (Android, Backend, contrato de API, etc. — según a qué módulo aplique).
   Sigue las convenciones ya usadas en `CLAUDE.md`: español para prosa,
   inglés para nombres de código, sin emojis.

4. **Dejá `docs/PLAN.md` como bitácora histórica de esa sección**: no la
   borres. Marca claramente (ej. encabezado o nota al inicio de la sección)
   que esa parte ya fue migrada a `CLAUDE.md` y quedó como registro de cómo
   se llegó a la decisión, no como fuente de verdad activa.

5. Si la sección de PLAN.md tenía TODOs o decisiones pendientes que siguen
   sin resolver, no los migres como si estuvieran resueltos — dejalos en
   PLAN.md y señalalos al usuario.

No modifiques otras secciones de CLAUDE.md ni de PLAN.md que no estén
relacionadas con el feature señalado.
