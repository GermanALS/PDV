"""unique local_id en devoluciones

Revision ID: 0013
Revises: 0012
Create Date: 2026-09-10

"""

from typing import Sequence, Union

from alembic import op

revision: str = "0013"
down_revision: Union[str, None] = "0012"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


# Idempotencia de POST /devoluciones por local_id (PLAN.md Parte 32, gap 1:
# la Parte 23 agrego el UNIQUE a ventas/movimientos/cortes_caja/
# retiros_efectivo pero dejo devoluciones afuera). Un UNIQUE normal en
# Postgres no choca entre multiples NULL, asi que la columna sigue nullable
# sin backfill: solo las filas nuevas (local_id obligatorio en
# DevolucionCreateSchema desde esta Parte) participan de la deduplicacion.
def upgrade() -> None:
    op.create_unique_constraint("uq_devoluciones_local_id", "devoluciones", ["local_id"])


def downgrade() -> None:
    op.drop_constraint("uq_devoluciones_local_id", "devoluciones", type_="unique")
