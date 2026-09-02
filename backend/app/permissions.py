from fastapi import Depends, HTTPException, Request

from app.dependencies import usuario_actual
from app.models.usuario import Usuario

# Modulo del rol requerido por cada escritura (POST / PATCH / DELETE). Las
# lecturas (GET) solo exigen token valido: otros modulos las consumen de
# forma legitima. Fuente unica del mapeo (api-contract.md sec. 12); la clave
# es (metodo HTTP, path template local del router, sin el prefijo /api/v1 -
# que es lo que expone request.scope["route"].path).
_ESCRITURA_MODULO: dict[tuple[str, str], str] = {
    ("POST", "/ventas"): "venta",
    ("POST", "/entradas"): "entrada",
    ("PATCH", "/inventario/{articulo_id}"): "inventario",
    ("POST", "/cortes-caja"): "caja",
    ("POST", "/retiros-efectivo"): "caja",
    ("POST", "/devoluciones"): "devoluciones",
    ("POST", "/usuarios"): "usuarios",
    ("PATCH", "/usuarios/{usuario_id}"): "usuarios",
    ("DELETE", "/usuarios/{usuario_id}"): "usuarios",
    ("POST", "/roles"): "usuarios",
    ("PATCH", "/roles/{rol_id}"): "usuarios",
    ("DELETE", "/roles/{rol_id}"): "usuarios",
    ("POST", "/sucursales"): "configuracion",
    # POST /sync-conflicts: sin modulo, es infraestructura del motor de
    # sincronizacion, no una accion de usuario.
}


async def verificar_modulo(
    request: Request,
    usuario: Usuario = Depends(usuario_actual),
) -> None:
    """Dependencia a nivel de router: si la ruta actual es una escritura con
    modulo requerido, exige que el rol del usuario lo tenga; `403` si no."""
    modulo = _ESCRITURA_MODULO.get((request.method, request.scope["route"].path))
    if modulo is not None and modulo not in usuario.rol.modulos_permitidos:
        raise HTTPException(
            status_code=403,
            detail=f"el rol no tiene permiso para el modulo: {modulo}",
        )
