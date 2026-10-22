from decimal import ROUND_HALF_UP, Decimal
from typing import Annotated

from pydantic import Field

_CENTAVO = Decimal("0.01")

# Usuario local que opero en el dispositivo (PLAN.md Parte 38, M-1): solo
# informativo, usuario_id lo fija el servidor con el usuario del JWT.
UsuarioDispositivo = Annotated[str | None, Field(max_length=120)]


def a_centavos(valor: Decimal) -> Decimal:
    """Redondea un monto a 2 decimales (ROUND_HALF_UP) para comparar montos
    recalculados por el servidor con los que manda el cliente (PLAN.md Parte
    38, M-2)."""
    return valor.quantize(_CENTAVO, rounding=ROUND_HALF_UP)
