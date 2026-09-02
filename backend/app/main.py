from fastapi import APIRouter, Depends, FastAPI

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

app = FastAPI(title="PDV API")

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
