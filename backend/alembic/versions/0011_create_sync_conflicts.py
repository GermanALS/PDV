"""create sync_conflicts

Revision ID: 0011
Revises: 0010
Create Date: 2026-08-29

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0011"
down_revision: Union[str, None] = "0010"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Auditoria de sincronizacion (PLAN.md Parte 3, docs/schema-pos.json
    # tabla sync_conflicts). No lleva local_id/remote_id/is_synced/
    # deleted_at: es el registro de auditoria en si. El id lo genera el
    # dispositivo, por eso no tiene default aca.
    op.create_table(
        "sync_conflicts",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("entidad", sa.String(length=120), nullable=False),
        sa.Column("entidad_local_id", sa.Uuid(), nullable=False),
        sa.Column("sucursal_id", sa.Uuid(), sa.ForeignKey("sucursales.id"), nullable=True),
        sa.Column("valor_local", postgresql.JSONB(), nullable=False),
        sa.Column("valor_remoto", postgresql.JSONB(), nullable=False),
        sa.Column("valor_resuelto", postgresql.JSONB(), nullable=False),
        sa.Column("politica_aplicada", sa.String(length=30), nullable=False),
        sa.Column("resuelto_automaticamente", sa.Boolean(), nullable=False),
        sa.Column("fecha_deteccion", sa.DateTime(timezone=True), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("sync_conflicts")
