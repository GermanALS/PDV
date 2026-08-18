from fastapi import FastAPI

from app.routers import health

app = FastAPI(title="PDV API")
app.include_router(health.router, prefix="/api/v1")
