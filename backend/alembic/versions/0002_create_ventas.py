"""create ventas and venta_detalle

Revision ID: 0002
Revises: 0001
Create Date: 2026-08-19

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0002"
down_revision: Union[str, None] = "0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "ventas",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("sucursal_id", sa.Uuid(), sa.ForeignKey("sucursales.id"), nullable=False),
        sa.Column("usuario_id", sa.String(length=120), nullable=False),
        sa.Column("folio", sa.String(length=60), nullable=False),
        sa.Column("fecha", sa.DateTime(timezone=True), nullable=False),
        sa.Column("subtotal", sa.Numeric(12, 2), nullable=False),
        sa.Column("descuento", sa.Numeric(12, 2), nullable=False),
        sa.Column("impuestos", sa.Numeric(12, 2), nullable=False),
        sa.Column("total", sa.Numeric(12, 2), nullable=False),
        sa.Column("metodo_pago", sa.String(length=30), nullable=False),
        sa.Column("estado", sa.String(length=30), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_table(
        "venta_detalle",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("venta_id", sa.Uuid(), sa.ForeignKey("ventas.id"), nullable=False),
        sa.Column("articulo_id", sa.Uuid(), nullable=False),
        sa.Column("cantidad", sa.Numeric(12, 3), nullable=False),
        sa.Column("precio_unitario", sa.Numeric(12, 2), nullable=False),
        sa.Column("subtotal", sa.Numeric(12, 2), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_table("venta_detalle")
    op.drop_table("ventas")
