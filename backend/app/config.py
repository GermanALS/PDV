import os
from collections.abc import Mapping

# Valor de desarrollo del docker-compose.yml local: solo se acepta con
# PDV_ENV=development (PLAN.md Parte 37, A-4).
JWT_SECRET_KEY_DESARROLLO = "dev-secret-key-cambiar-en-produccion"
ENTORNO_DESARROLLO = "development"


def cargar_entorno(env: Mapping[str, str]) -> str:
    """Entorno del backend; sin PDV_ENV se asume produccion (falla cerrado)."""
    return env.get("PDV_ENV", "production")


def cargar_jwt_secret_key(env: Mapping[str, str]) -> str:
    """Clave de firma del JWT. El proceso no arranca sin ella, ni con el valor
    de desarrollo fuera de PDV_ENV=development (api-contract.md sec. 12)."""
    clave = env.get("JWT_SECRET_KEY")
    if not clave:
        raise RuntimeError("JWT_SECRET_KEY es obligatoria: definela en el entorno del backend")
    if clave == JWT_SECRET_KEY_DESARROLLO and cargar_entorno(env) != ENTORNO_DESARROLLO:
        raise RuntimeError(
            "JWT_SECRET_KEY tiene el valor de desarrollo y PDV_ENV no es development: "
            "define una clave propia en el despliegue"
        )
    return clave


def opciones_docs(entorno: str) -> dict[str, str | None]:
    """URLs de /docs, /redoc y /openapi.json: solo existen en desarrollo
    (PLAN.md Parte 37, M-4)."""
    if entorno == ENTORNO_DESARROLLO:
        return {"docs_url": "/docs", "redoc_url": "/redoc", "openapi_url": "/openapi.json"}
    return {"docs_url": None, "redoc_url": None, "openapi_url": None}


ENTORNO = cargar_entorno(os.environ)
