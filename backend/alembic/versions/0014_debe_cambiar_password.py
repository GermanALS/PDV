"""debe_cambiar_password en usuarios

Revision ID: 0014
Revises: 0013
Create Date: 2026-10-20

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0014"
down_revision: Union[str, None] = "0013"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


# El admin bootstrap de la migracion 0009 tiene una contrasena publica
# (admin123): se marca para que no de acceso a ningun modulo hasta cambiarla
# (PLAN.md Parte 37, A-3). Se marca por username, sin verificar el hash: un
# admin que ya la habia cambiado la cambia una vez mas.
def upgrade() -> None:
    op.add_column(
        "usuarios",
        sa.Column("debe_cambiar_password", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.execute(
        "UPDATE usuarios SET debe_cambiar_password = true "
        "WHERE username = 'admin' AND deleted_at IS NULL"
    )


def downgrade() -> None:
    op.drop_column("usuarios", "debe_cambiar_password")
