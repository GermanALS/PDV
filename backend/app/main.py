from fastapi import FastAPI

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
app.include_router(health.router, prefix="/api/v1")
app.include_router(sucursales.router, prefix="/api/v1")
app.include_router(sync_conflicts.router, prefix="/api/v1")
app.include_router(ventas.router, prefix="/api/v1")
app.include_router(entradas.router, prefix="/api/v1")
app.include_router(inventario.router, prefix="/api/v1")
app.include_router(caja.router, prefix="/api/v1")
app.include_router(devoluciones.router, prefix="/api/v1")
app.include_router(roles.router, prefix="/api/v1")
app.include_router(usuarios.router, prefix="/api/v1")
app.include_router(auth.router, prefix="/api/v1")
