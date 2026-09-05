"""unique local_id en ventas, movimientos, cortes_caja, retiros_efectivo

Revision ID: 0012
Revises: 0011
Create Date: 2026-09-04

"""

from typing import Sequence, Union

from alembic import op

revision: str = "0012"
down_revision: Union[str, None] = "0011"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


# Idempotencia de POST /ventas, /entradas, /cortes-caja, /retiros-efectivo
# por local_id (PLAN.md Parte 23, hallazgo A-3 de docs/review_code.md). Un
# UNIQUE normal en Postgres no choca entre multiples NULL, asi que la
# columna sigue nullable sin backfill: solo las filas nuevas (local_id
# obligatorio desde esta Parte) participan de la deduplicacion.
def upgrade() -> None:
    op.create_unique_constraint("uq_ventas_local_id", "ventas", ["local_id"])
    op.create_unique_constraint("uq_movimientos_local_id", "movimientos", ["local_id"])
    op.create_unique_constraint("uq_cortes_caja_local_id", "cortes_caja", ["local_id"])
    op.create_unique_constraint("uq_retiros_efectivo_local_id", "retiros_efectivo", ["local_id"])


def downgrade() -> None:
    op.drop_constraint("uq_retiros_efectivo_local_id", "retiros_efectivo", type_="unique")
    op.drop_constraint("uq_cortes_caja_local_id", "cortes_caja", type_="unique")
    op.drop_constraint("uq_movimientos_local_id", "movimientos", type_="unique")
    op.drop_constraint("uq_ventas_local_id", "ventas", type_="unique")
