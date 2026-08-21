"""create devoluciones and devolucion_detalle

Revision ID: 0005
Revises: 0004
Create Date: 2026-08-20

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0005"
down_revision: Union[str, None] = "0004"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "devoluciones",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("sucursal_id", sa.Uuid(), sa.ForeignKey("sucursales.id"), nullable=False),
        sa.Column("usuario_id", sa.String(length=120), nullable=False),
        sa.Column("venta_id", sa.Uuid(), sa.ForeignKey("ventas.id"), nullable=True),
        sa.Column("folio", sa.String(length=60), nullable=False),
        sa.Column("fecha", sa.DateTime(timezone=True), nullable=False),
        sa.Column("estado", sa.String(length=30), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_table(
        "devolucion_detalle",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("devolucion_id", sa.Uuid(), sa.ForeignKey("devoluciones.id"), nullable=False),
        sa.Column("articulo_id", sa.Uuid(), sa.ForeignKey("articulos.id"), nullable=False),
        sa.Column("cantidad", sa.Numeric(12, 3), nullable=False),
        sa.Column("motivo", sa.String(length=255), nullable=True),
        sa.Column("condicion", sa.String(length=30), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_table("devolucion_detalle")
    op.drop_table("devoluciones")
