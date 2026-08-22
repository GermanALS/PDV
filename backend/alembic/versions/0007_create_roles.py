"""create roles

Revision ID: 0007
Revises: 0006
Create Date: 2026-08-21

"""

import uuid
from datetime import datetime, timezone
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0007"
down_revision: Union[str, None] = "0006"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

MODULOS_ADMINISTRADOR = [
    "venta",
    "entrada",
    "inventario",
    "caja",
    "devoluciones",
    "usuarios",
    "configuracion",
]
MODULOS_ENCARGADO_TURNO = ["venta", "entrada", "inventario", "caja", "devoluciones"]


def upgrade() -> None:
    op.create_table(
        "roles",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("local_id", sa.Uuid(), nullable=True),
        sa.Column("nombre", sa.String(length=60), nullable=False, unique=True),
        sa.Column("modulos_permitidos", sa.ARRAY(sa.String()), nullable=False),
        sa.Column("es_sistema", sa.Boolean(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
    )

    roles = sa.table(
        "roles",
        sa.column("id", sa.Uuid()),
        sa.column("local_id", sa.Uuid()),
        sa.column("nombre", sa.String()),
        sa.column("modulos_permitidos", sa.ARRAY(sa.String())),
        sa.column("es_sistema", sa.Boolean()),
        sa.column("updated_at", sa.DateTime(timezone=True)),
        sa.column("deleted_at", sa.DateTime(timezone=True)),
    )
    # Seed de los 2 roles de sistema (PLAN.md Parte 13): protegidos de
    # edicion/eliminacion (es_sistema=True), mismos nombres que el literal
    # `rol` que usuarios usaba hasta esta Parte, para que la migracion 0008
    # pueda hacer el backfill por nombre exacto.
    op.bulk_insert(
        roles,
        [
            {
                "id": uuid.uuid4(),
                "local_id": None,
                "nombre": "administrador",
                "modulos_permitidos": MODULOS_ADMINISTRADOR,
                "es_sistema": True,
                "updated_at": datetime.now(timezone.utc),
                "deleted_at": None,
            },
            {
                "id": uuid.uuid4(),
                "local_id": None,
                "nombre": "encargado_turno",
                "modulos_permitidos": MODULOS_ENCARGADO_TURNO,
                "es_sistema": True,
                "updated_at": datetime.now(timezone.utc),
                "deleted_at": None,
            },
        ],
    )


def downgrade() -> None:
    op.drop_table("roles")
