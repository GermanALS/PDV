import logging

from fastapi import APIRouter, Depends, FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.dependencies import usuario_actual
from app.permissions import verificar_modulo
from app.routers import (
    auth,
    caja,
    devoluciones,
    entradas,
    health,
    inventario,
    roles,
    sucursales,
    sync_conflicts,
    usuarios,
    ventas,
)

logger = logging.getLogger(__name__)

app = FastAPI(title="PDV API")

# Sin cliente web propio todavia (toda la app es Android nativo); origenes
# abiertos hasta que exista uno real (PLAN.md Parte 25, hallazgo M-5).
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# Excepciones no previstas (ej. IntegrityError sin capturar, error de
# programacion) devuelven el shape {"detail": ...} de api-contract.md sec.
# 12 en vez de un traceback (PLAN.md Parte 25, hallazgo M-5). No intercepta
# HTTPException/RequestValidationError: Starlette resuelve el handler por la
# clase mas especifica de la excepcion (ver ExceptionMiddleware), y esas ya
# tienen su propio handler registrado por FastAPI.
@app.exception_handler(Exception)
async def excepcion_no_prevista_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.error("excepcion no prevista en %s %s", request.method, request.url.path, exc_info=exc)
    return JSONResponse(status_code=500, content={"detail": "error interno del servidor"})


# Rutas abiertas: exentas del header Authorization (api-contract.md sec. 12).
app.include_router(health.router, prefix="/api/v1")
app.include_router(auth.router, prefix="/api/v1")

# Resto de la API: exige un JWT valido (usuario_actual) y, en las
# escrituras, el modulo del rol (verificar_modulo). Ambas se declaran una
# sola vez a nivel del router `protected`, no repetidas por router.
protected = APIRouter(dependencies=[Depends(usuario_actual), Depends(verificar_modulo)])
protected.include_router(sucursales.router)
protected.include_router(sync_conflicts.router)
protected.include_router(ventas.router)
protected.include_router(entradas.router)
protected.include_router(inventario.router)
protected.include_router(caja.router)
protected.include_router(devoluciones.router)
protected.include_router(roles.router)
protected.include_router(usuarios.router)
app.include_router(protected, prefix="/api/v1")
