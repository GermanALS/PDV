import os
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt

from app.config import cargar_jwt_secret_key

# Sin default en codigo: el proceso no arranca si falta (PLAN.md Parte 37, A-4).
JWT_SECRET_KEY = cargar_jwt_secret_key(os.environ)
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
