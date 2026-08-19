"""create sucursales

Revision ID: 0001
Revises:
Create Date: 2026-08-18

"""

import uuid
from datetime import datetime, timezone
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "sucursales",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("nombre", sa.String(length=120), nullable=False),
        sa.Column("direccion", sa.String(), nullable=True),
        sa.Column("activa", sa.Boolean(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )

    sucursales = sa.table(
        "sucursales",
        sa.column("id", sa.Uuid()),
        sa.column("local_id", sa.Uuid()),
        sa.column("nombre", sa.String()),
        sa.column("direccion", sa.String()),
        sa.column("activa", sa.Boolean()),
        sa.column("updated_at", sa.DateTime(timezone=True)),
        sa.column("deleted_at", sa.DateTime(timezone=True)),
    )
    # Seed de la sucursal por defecto (PLAN.md Parte 3, "Sucursal por
    # defecto"): la tabla recien se creo en esta misma migracion, asi que
    # esta insercion deja exactamente una fila sin necesitar chequear si
    # esta vacia.
    op.bulk_insert(
        sucursales,
        [
            {
                "id": uuid.uuid4(),
                "local_id": None,
                "nombre": "Sucursal principal",
                "direccion": None,
                "activa": True,
                "updated_at": datetime.now(timezone.utc),
                "deleted_at": None,
            }
        ],
    )


def downgrade() -> None:
    op.drop_table("sucursales")
