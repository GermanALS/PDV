from fastapi import FastAPI

from app.routers import caja, devoluciones, entradas, health, inventario, sucursales, ventas

app = FastAPI(title="PDV API")
app.include_router(health.router, prefix="/api/v1")
app.include_router(sucursales.router, prefix="/api/v1")
app.include_router(ventas.router, prefix="/api/v1")
app.include_router(entradas.router, prefix="/api/v1")
app.include_router(inventario.router, prefix="/api/v1")
app.include_router(caja.router, prefix="/api/v1")
app.include_router(devoluciones.router, prefix="/api/v1")
