from app.limite_login import LimiteIntentosLogin

# PLAN.md Parte 37, M-4: contador en memoria por username.


class _Reloj:
    def __init__(self) -> None:
        self.ahora = 0.0

    def __call__(self) -> float:
        return self.ahora


def test_bloquea_al_llegar_al_maximo_de_fallos():
    limite = LimiteIntentosLogin(max_fallos=3, ventana_segundos=60, reloj=_Reloj())
    for _ in range(2):
        limite.registrar_fallo("admin")
    assert not limite.bloqueado("admin")

    limite.registrar_fallo("admin")

    assert limite.bloqueado("admin")
    assert not limite.bloqueado("otro")


def test_el_bloqueo_vence_con_la_ventana():
    reloj = _Reloj()
    limite = LimiteIntentosLogin(max_fallos=2, ventana_segundos=60, reloj=reloj)
    limite.registrar_fallo("admin")
    limite.registrar_fallo("admin")

    reloj.ahora = 61

    assert not limite.bloqueado("admin")


def test_reiniciar_borra_los_fallos():
    limite = LimiteIntentosLogin(max_fallos=1, ventana_segundos=60, reloj=_Reloj())
    limite.registrar_fallo("admin")

    limite.reiniciar("admin")

    assert not limite.bloqueado("admin")
