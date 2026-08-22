from pydantic import BaseModel, Field

from app.schemas.usuario import UsuarioResponseSchema


class AuthLoginRequestSchema(BaseModel):
    username: str = Field(min_length=1)
    password: str = Field(min_length=1)


class AuthLoginResponseSchema(BaseModel):
    access_token: str
    token_type: str = "bearer"
    usuario: UsuarioResponseSchema
