import os
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt

# Valor de desarrollo por defecto: no hay destino de despliegue definido
# todavia (CLAUDE.md sec. 7, "[TODO: definir destino final]"). Cambiar via
# la variable de entorno JWT_SECRET_KEY antes de exponer el backend fuera
# de la red local.
JWT_SECRET_KEY = os.environ.get("JWT_SECRET_KEY", "dev-secret-key-cambiar-en-produccion")
JWT_ALGORITHM = "HS256"
JWT_EXPIRES_MINUTES = 60 * 24


def hash_password(plain_password: str) -> str:
    return bcrypt.hashpw(plain_password.encode(), bcrypt.gensalt(rounds=12)).decode()


def verify_password(plain_password: str, password_hash: str) -> bool:
    return bcrypt.checkpw(plain_password.encode(), password_hash.encode())


def create_access_token(usuario_id: str) -> str:
    payload = {
        "sub": usuario_id,
        "exp": datetime.now(timezone.utc) + timedelta(minutes=JWT_EXPIRES_MINUTES),
    }
    return jwt.encode(payload, JWT_SECRET_KEY, algorithm=JWT_ALGORITHM)
