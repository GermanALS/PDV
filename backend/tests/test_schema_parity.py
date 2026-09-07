from alembic.autogenerate import compare_metadata
from alembic.runtime.migration import MigrationContext

from app.database import Base


async def test_migraciones_coinciden_con_los_modelos(engine):
    async with engine.connect() as conn:
        diff = await conn.run_sync(
            lambda sync_conn: compare_metadata(
                MigrationContext.configure(sync_conn), Base.metadata
            )
        )
    assert diff == [], (
        f"El esquema producido por las migraciones diverge de los modelos: {diff}"
    )
