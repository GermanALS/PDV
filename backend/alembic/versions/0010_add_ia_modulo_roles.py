"""add ia modulo to system roles

Revision ID: 0010
Revises: 0009
Create Date: 2026-08-22

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0010"
down_revision: Union[str, None] = "0009"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

ROLES_DE_SISTEMA = ("administrador", "encargado_turno")


def upgrade() -> None:
    roles = sa.table(
        "roles",
        sa.column("nombre", sa.String()),
        sa.column("modulos_permitidos", sa.ARRAY(sa.String())),
    )
    conn = op.get_bind()
    for nombre in ROLES_DE_SISTEMA:
        row = conn.execute(sa.select(roles.c.modulos_permitidos).where(roles.c.nombre == nombre)).first()
        if row is None or "ia" in row[0]:
            continue
        conn.execute(
            roles.update().where(roles.c.nombre == nombre).values(modulos_permitidos=row[0] + ["ia"]),
        )


def downgrade() -> None:
    roles = sa.table(
        "roles",
        sa.column("nombre", sa.String()),
        sa.column("modulos_permitidos", sa.ARRAY(sa.String())),
    )
    conn = op.get_bind()
    for nombre in ROLES_DE_SISTEMA:
        row = conn.execute(sa.select(roles.c.modulos_permitidos).where(roles.c.nombre == nombre)).first()
        if row is None:
            continue
        conn.execute(
            roles.update().where(roles.c.nombre == nombre).values(
                modulos_permitidos=[modulo for modulo in row[0] if modulo != "ia"],
            ),
        )
