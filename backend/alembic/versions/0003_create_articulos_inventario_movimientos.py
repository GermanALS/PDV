"""create articulos, inventario and movimientos

Revision ID: 0003
Revises: 0002
Create Date: 2026-08-19

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0003"
down_revision: Union[str, None] = "0002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "articulos",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("codigo_barras", sa.String(length=60), nullable=True),
        sa.Column("sku", sa.String(length=60), nullable=False, unique=True),
        sa.Column("nombre", sa.String(length=160), nullable=False),
        sa.Column("descripcion", sa.String(), nullable=True),
        sa.Column("categoria", sa.String(length=80), nullable=True),
        sa.Column("unidad_medida", sa.String(length=30), nullable=False),
        sa.Column("precio_venta", sa.Numeric(12, 2), nullable=False),
        sa.Column("costo", sa.Numeric(12, 2), nullable=True),
        sa.Column("activo", sa.Boolean(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_table(
        "inventario",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("sucursal_id", sa.Uuid(), sa.ForeignKey("sucursales.id"), nullable=False),
        sa.Column("articulo_id", sa.Uuid(), sa.ForeignKey("articulos.id"), nullable=False),
        sa.Column("cantidad", sa.Numeric(14, 3), nullable=False),
        sa.Column("ubicacion", sa.String(length=80), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        sa.UniqueConstraint("sucursal_id", "articulo_id", name="uq_inventario_sucursal_articulo"),
    )
    op.create_table(
        "movimientos",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("sucursal_id", sa.Uuid(), sa.ForeignKey("sucursales.id"), nullable=False),
        sa.Column("articulo_id", sa.Uuid(), sa.ForeignKey("articulos.id"), nullable=False),
        sa.Column("usuario_id", sa.String(length=120), nullable=False),
        sa.Column("tipo", sa.String(length=20), nullable=False),
        sa.Column("cantidad", sa.Numeric(14, 3), nullable=False),
        sa.Column("ubicacion", sa.String(length=80), nullable=True),
        sa.Column("referencia_tipo", sa.String(length=40), nullable=True),
        sa.Column("referencia_id", sa.Uuid(), nullable=True),
        sa.Column("fecha", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_table("movimientos")
    op.drop_table("inventario")
    op.drop_table("articulos")
