import os

os.environ.setdefault("DATABASE_URL", "postgresql+asyncpg://pdv:pdv@localhost:5432/pdv")

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine

from app.database import Base, get_db
from app.main import app


@pytest_asyncio.fixture(scope="session")
async def engine():
    test_engine = create_async_engine(os.environ["DATABASE_URL"])
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield test_engine
    await test_engine.dispose()


# Cada test corre en su propia transaccion, revertida al final (rollback):
# permite correr los tests contra la misma base de desarrollo sin dejar
# datos de prueba residuales entre corridas. join_transaction_mode usa un
# SAVEPOINT para el commit() que hace la ruta real, sin cerrar la
# transaccion externa que se revierte al final (recipe de SQLAlchemy 2.0
# para test suites).
@pytest_asyncio.fixture
async def session(engine):
    async with engine.connect() as conn:
        trans = await conn.begin()
        async_session = AsyncSession(
            bind=conn, join_transaction_mode="create_savepoint", expire_on_commit=False
        )
        app.dependency_overrides[get_db] = lambda: async_session
        try:
            yield async_session
        finally:
            app.dependency_overrides.pop(get_db, None)
            await async_session.close()
            await trans.rollback()


@pytest_asyncio.fixture
async def client(session):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
