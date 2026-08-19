from fastapi import FastAPI

from app.routers import health, sucursales

app = FastAPI(title="PDV API")
app.include_router(health.router, prefix="/api/v1")
app.include_router(sucursales.router, prefix="/api/v1")
