"""usuarios.rol -> usuarios.rol_id (FK a roles)

Revision ID: 0008
Revises: 0007
Create Date: 2026-08-21

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0008"
down_revision: Union[str, None] = "0007"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("usuarios", sa.Column("rol_id", sa.Uuid(), nullable=True))
    # Backfill: el `rol` literal ("administrador"/"encargado_turno") pasa a
    # apuntar al rol de sistema con el mismo nombre, seedeado en la
    # migracion 0007.
    op.execute(
        """
        UPDATE usuarios
        SET rol_id = roles.id
        FROM roles
        WHERE roles.nombre = usuarios.rol
        """
    )
    op.alter_column("usuarios", "rol_id", nullable=False)
    op.create_foreign_key(
        "fk_usuarios_rol_id_roles", "usuarios", "roles", ["rol_id"], ["id"]
    )
    op.drop_column("usuarios", "rol")


def downgrade() -> None:
    op.add_column("usuarios", sa.Column("rol", sa.String(length=30), nullable=True))
    op.execute(
        """
        UPDATE usuarios
        SET rol = roles.nombre
        FROM roles
        WHERE roles.id = usuarios.rol_id
        """
    )
    op.alter_column("usuarios", "rol", nullable=False)
    op.drop_constraint("fk_usuarios_rol_id_roles", "usuarios", type_="foreignkey")
    op.drop_column("usuarios", "rol_id")
