import time
from collections.abc import Callable

MAX_FALLOS = 5
VENTANA_SEGUNDOS = 15 * 60


class LimiteIntentosLogin:
    """Bloqueo en memoria por username: MAX_FALLOS fallos dentro de la
    ventana bloquean ese username hasta que el mas viejo salga de ella.

    Vive en el proceso (un contador por worker, se pierde al reiniciar):
    decision de la Parte 37 (M-4), sin librerias ni tablas nuevas.
    """

    def __init__(
        self,
        max_fallos: int = MAX_FALLOS,
        ventana_segundos: float = VENTANA_SEGUNDOS,
        reloj: Callable[[], float] = time.monotonic,
    ) -> None:
        self._max_fallos = max_fallos
        self._ventana = ventana_segundos
        self._reloj = reloj
        self._fallos: dict[str, list[float]] = {}

    def bloqueado(self, username: str) -> bool:
        return len(self._recientes(username)) >= self._max_fallos

    def registrar_fallo(self, username: str) -> None:
        self._fallos[username] = self._recientes(username) + [self._reloj()]

    def reiniciar(self, username: str) -> None:
        self._fallos.pop(username, None)

    def _recientes(self, username: str) -> list[float]:
        ahora = self._reloj()
        recientes = [t for t in self._fallos.get(username, []) if ahora - t < self._ventana]
        if recientes:
            self._fallos[username] = recientes
        else:
            self._fallos.pop(username, None)
        return recientes


limite_login = LimiteIntentosLogin()
