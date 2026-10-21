import uuid
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.inventario import Inventario


async def bloquear_inventario(db: AsyncSession, sucursal_id: uuid.UUID, articulo_id: uuid.UUID) -> Inventario:
    """Devuelve la fila de inventario de (sucursal, articulo) bloqueada con
    FOR UPDATE hasta el commit, creandola con cantidad 0 si no existe.

    El INSERT ... ON CONFLICT DO NOTHING hace que dos transacciones que crean
    la primera fila a la vez no choquen con uq_inventario_sucursal_articulo
    (antes, 500): la segunda espera a la primera y luego bloquea la fila ya
    creada (PLAN.md Parte 38, B-1). El llamador suma su delta sobre la fila.
    """
    await db.execute(
        insert(Inventario)
        .values(sucursal_id=sucursal_id, articulo_id=articulo_id, cantidad=Decimal("0"))
        .on_conflict_do_nothing(constraint="uq_inventario_sucursal_articulo")
    )
    return (
        await db.execute(
            select(Inventario)
            .where(Inventario.sucursal_id == sucursal_id, Inventario.articulo_id == articulo_id)
            .with_for_update()
        )
    ).scalar_one()
