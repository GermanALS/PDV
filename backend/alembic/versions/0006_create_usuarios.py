"""create usuarios

Revision ID: 0006
Revises: 0005
Create Date: 2026-08-21

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0006"
down_revision: Union[str, None] = "0005"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "usuarios",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("username", sa.String(length=60), nullable=False, unique=True),
        sa.Column("nombre_completo", sa.String(length=120), nullable=False),
        sa.Column("password_hash", sa.String(), nullable=True),
        sa.Column("rol", sa.String(length=30), nullable=False),
        sa.Column("activo", sa.Boolean(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_table("usuarios")
