from pydantic import BaseModel, Field

from app.schemas.usuario import BCRYPT_PATTERN, UsuarioResponseSchema


class AuthLoginRequestSchema(BaseModel):
    username: str = Field(min_length=1)
    password: str = Field(min_length=1)


class AuthCambiarPasswordRequestSchema(BaseModel):
    # Hash bcrypt calculado en Android (PLAN.md Parte 13, Decision 2).
    password_hash: str = Field(pattern=BCRYPT_PATTERN)


class AuthLoginResponseSchema(BaseModel):
    access_token: str
    token_type: str = "bearer"
    usuario: UsuarioResponseSchema
