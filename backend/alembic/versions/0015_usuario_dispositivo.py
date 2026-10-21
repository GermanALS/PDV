"""usuario_dispositivo en las tablas de escrituras atribuidas

Revision ID: 0015
Revises: 0014
Create Date: 2026-10-21

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0015"
down_revision: Union[str, None] = "0014"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

# Desde la Parte 38 (M-1) usuario_id lo fija el servidor con el usuario del
# JWT; el usuario local que opero en el dispositivo se guarda aparte, solo
# informativo. Las filas existentes quedan en NULL.
_TABLAS = ("ventas", "movimientos", "cortes_caja", "retiros_efectivo", "devoluciones")


def upgrade() -> None:
    for tabla in _TABLAS:
        op.add_column(tabla, sa.Column("usuario_dispositivo", sa.String(120), nullable=True))


def downgrade() -> None:
    for tabla in _TABLAS:
        op.drop_column(tabla, "usuario_dispositivo")
