import uuid

from sqlalchemy import select

from app.models.inventario import Inventario
from app.models.movimiento import Movimiento


async def _seeded_sucursal_id(client_autenticado) -> str:
    response = await client_autenticado.get("/api/v1/sucursales")
    return response.json()["items"][0]["id"]


async def _articulo_con_existencia(client_autenticado, sucursal_id: str, sku: str, cantidad: str) -> str:
    response = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "fecha": "2026-08-19T12:00:00Z",
            "cantidad": cantidad,
            "articulo_nuevo": {
                "sku": sku,
                "nombre": "Articulo de prueba para venta",
                "unidad_medida": "pieza",
                "precio_venta": "50.00",
            },
        },
    )
    return response.json()["articulo"]["id"]


async def test_create_venta_happy_path(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="VTA-001", cantidad="10")

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "F-001",
            "fecha": "2026-08-19T12:00:00Z",
            "subtotal": "100.00",
            "descuento": "0.00",
            "impuestos": "0.00",
            "total": "100.00",
            "metodo_pago": "efectivo",
            "estado": "completada",
            "lineas": [
                {
                    "articulo_id": articulo_id,
                    "cantidad": "2",
                    "precio_unitario": "50.00",
                    "subtotal": "100.00",
                }
            ],
        },
    )

    assert response.status_code == 201
    body = response.json()
    assert body["folio"] == "F-001"
    assert body["sucursal_id"] == sucursal_id
    assert body["is_synced"] is True
    assert body["id"] is not None
    assert len(body["lineas"]) == 1
    assert body["lineas"][0]["cantidad"] == "2.000"


async def test_create_venta_decrements_inventario_and_creates_movimiento(client_autenticado, session):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="VTA-002", cantidad="10")

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "F-003",
            "fecha": "2026-08-19T12:00:00Z",
            "subtotal": "150.00",
            "total": "150.00",
            "metodo_pago": "efectivo",
            "lineas": [
                {
                    "articulo_id": articulo_id,
                    "cantidad": "3",
                    "precio_unitario": "50.00",
                    "subtotal": "150.00",
                }
            ],
        },
    )
    assert response.status_code == 201
    venta_id = response.json()["id"]

    # GET /inventario y GET /movimientos aun no existen (PLAN.md Parte 9,
    # sub-paso 3) - se verifica el efecto directo sobre las tablas via la
    # misma sesion de SQLAlchemy que uso la ruta (join_transaction_mode con
    # SAVEPOINT, PLAN.md Parte 6 nota de conftest.py).
    inventario = (
        await session.execute(
            select(Inventario).where(
                Inventario.sucursal_id == uuid.UUID(sucursal_id),
                Inventario.articulo_id == uuid.UUID(articulo_id),
            )
        )
    ).scalar_one()
    assert inventario.cantidad == 7

    movimiento = (
        await session.execute(select(Movimiento).where(Movimiento.referencia_id == uuid.UUID(venta_id)))
    ).scalar_one()
    assert movimiento.tipo == "salida"
    assert movimiento.referencia_tipo == "venta"
    assert movimiento.cantidad == 3


async def test_create_venta_without_lineas_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "F-002",
            "fecha": "2026-08-19T12:00:00Z",
            "subtotal": "0.00",
            "total": "0.00",
            "metodo_pago": "efectivo",
            "lineas": [],
        },
    )

    assert response.status_code == 422


async def test_create_venta_articulo_id_inexistente_returns_404(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_id": "admin",
            "folio": "F-004",
            "fecha": "2026-08-19T12:00:00Z",
            "subtotal": "50.00",
            "total": "50.00",
            "metodo_pago": "efectivo",
            "lineas": [
                {
                    "articulo_id": str(uuid.uuid4()),
                    "cantidad": "1",
                    "precio_unitario": "50.00",
                    "subtotal": "50.00",
                }
            ],
        },
    )

    assert response.status_code == 404
