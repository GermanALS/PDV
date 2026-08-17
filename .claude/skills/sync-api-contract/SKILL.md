---
name: sync-api-contract
description: Keep docs/api-contract.md, the FastAPI backend, and the Android Kotlin client in sync whenever an endpoint is added or changed. Use this whenever a task touches an API endpoint (new endpoint, changed request/response shape, changed error codes) on either the backend or Android side.
---

Este proyecto (PDV) mantiene `docs/api-contract.md` como fuente de verdad
compartida entre `backend/` (FastAPI) y `android/` (Kotlin). Antes de tocar
código en cualquiera de los dos lados para una tarea que involucra un
endpoint, sigue este orden:

1. **Actualiza `docs/api-contract.md` primero.** Para el endpoint en
   cuestión, define/actualiza: método, ruta (bajo `/api/v1/...`), request
   body, response body, y códigos de error esperados. Sigue el formato ya
   presente en el archivo (convenciones de fechas ISO 8601, ids UUID v4,
   forma de paginación, forma de errores).

2. **Genera o actualiza el lado backend** a partir del contrato recién
   escrito:
   - Esquemas Pydantic en `backend/app/schemas/` (nunca reutilizar modelos
     ORM directamente como `response_model`).
   - Ruta en el `APIRouter` correspondiente bajo `backend/app/routers/`.
   - Manejo de errores vía `HTTPException` / el exception handler global.

3. **Genera o actualiza el lado Android** a partir del mismo contrato:
   - DTOs de red en `android/.../data/remote/dto/`.
   - Cliente Retrofit correspondiente.
   - Si el endpoint alimenta un modelo de dominio, actualiza también
     `domain/model/` y el `RemoteItemRepository`-equivalente (nunca
     `data/local/`, que debe permanecer aislado de la red).

4. **Verifica que ambos lados coincidan con el contrato**: mismos nombres de
   campo, mismos tipos, mismos códigos de error. Si algo no calza, el
   contrato manda — corrige el código, no el contrato, salvo que el propio
   contrato tenga un error (en cuyo caso corrígelo y avisa explícitamente).

No saltees el paso 1: nunca generes código de request/response para un
endpoint que no esté reflejado en `docs/api-contract.md`.