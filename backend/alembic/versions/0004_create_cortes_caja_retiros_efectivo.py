"""create cortes_caja and retiros_efectivo

Revision ID: 0004
Revises: 0003
Create Date: 2026-08-20

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0004"
down_revision: Union[str, None] = "0003"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "cortes_caja",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("sucursal_id", sa.Uuid(), sa.ForeignKey("sucursales.id"), nullable=False),
        sa.Column("usuario_id", sa.String(length=120), nullable=False),
        sa.Column("tipo", sa.String(length=20), nullable=False),
        sa.Column("fecha_inicio", sa.DateTime(timezone=True), nullable=False),
        sa.Column("fecha_fin", sa.DateTime(timezone=True), nullable=False),
        sa.Column("total_ventas", sa.Numeric(12, 2), nullable=False),
        sa.Column("total_efectivo", sa.Numeric(12, 2), nullable=False),
        sa.Column("total_tarjeta", sa.Numeric(12, 2), nullable=False),
        sa.Column("total_retiros", sa.Numeric(12, 2), nullable=False),
        sa.Column("monto_esperado", sa.Numeric(12, 2), nullable=False),
        sa.Column("monto_contado", sa.Numeric(12, 2), nullable=True),
        sa.Column("diferencia", sa.Numeric(12, 2), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_table(
        "retiros_efectivo",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("sucursal_id", sa.Uuid(), sa.ForeignKey("sucursales.id"), nullable=False),
        sa.Column("usuario_id", sa.String(length=120), nullable=False),
        sa.Column("monto", sa.Numeric(12, 2), nullable=False),
        sa.Column("motivo", sa.String(length=255), nullable=True),
        sa.Column("fecha", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_table("retiros_efectivo")
    op.drop_table("cortes_caja")
