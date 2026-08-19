from fastapi import FastAPI

from app.routers import health, sucursales, ventas

app = FastAPI(title="PDV API")
app.include_router(health.router, prefix="/api/v1")
app.include_router(sucursales.router, prefix="/api/v1")
app.include_router(ventas.router, prefix="/api/v1")
