"""seed default admin usuario

Revision ID: 0009
Revises: 0008
Create Date: 2026-08-21

"""

import uuid
from datetime import datetime, timezone
from typing import Sequence, Union

import bcrypt
import sqlalchemy as sa
from alembic import op

revision: str = "0009"
down_revision: Union[str, None] = "0008"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

# Bootstrap: sin este usuario, un backend recien levantado no tiene forma de
# iniciar sesion (POST /auth/login solo valida contra usuarios existentes),
# y sin sesion no se puede llegar a la pantalla de Usuarios para crear el
# primero - mismo problema de arranque que roles (migracion 0007) y
# sucursales (migracion 0001). Contrasena de desarrollo documentada en
# docs/api-contract.md; se espera que se cambie tras el primer login.
DEFAULT_ADMIN_PASSWORD = "admin123"


def upgrade() -> None:
    bind = op.get_bind()
    rol_administrador_id = bind.execute(
        sa.text("SELECT id FROM roles WHERE nombre = 'administrador'")
    ).scalar_one()

    usuarios = sa.table(
        "usuarios",
        sa.column("id", sa.Uuid()),
        sa.column("local_id", sa.Uuid()),
        sa.column("username", sa.String()),
        sa.column("nombre_completo", sa.String()),
        sa.column("password_hash", sa.String()),
        sa.column("rol_id", sa.Uuid()),
        sa.column("activo", sa.Boolean()),
        sa.column("updated_at", sa.DateTime(timezone=True)),
        sa.column("deleted_at", sa.DateTime(timezone=True)),
    )
    password_hash = bcrypt.hashpw(DEFAULT_ADMIN_PASSWORD.encode(), bcrypt.gensalt(rounds=12)).decode()
    op.bulk_insert(
        usuarios,
        [
            {
                "id": uuid.uuid4(),
                "local_id": None,
                "username": "admin",
                "nombre_completo": "Administrador",
                "password_hash": password_hash,
                "rol_id": rol_administrador_id,
                "activo": True,
                "updated_at": datetime.now(timezone.utc),
                "deleted_at": None,
            }
        ],
    )


def downgrade() -> None:
    op.execute("DELETE FROM usuarios WHERE username = 'admin'")
