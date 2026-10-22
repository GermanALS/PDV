import asyncio
import uuid
from datetime import datetime, timezone
from decimal import Decimal

from fastapi import Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.articulo import Articulo
from app.models.inventario import Inventario
from app.models.movimiento import Movimiento
from app.models.sucursal import Sucursal
from app.models.usuario import Usuario
from app.models.venta import Venta
from app.routers.ventas import create_venta
from app.schemas.venta import VentaCreateSchema, VentaDetalleCreateSchema


async def _seeded_sucursal_id(client_autenticado) -> str:
    response = await client_autenticado.get("/api/v1/sucursales")
    return response.json()["items"][0]["id"]


async def _articulo_con_existencia(client_autenticado, sucursal_id: str, sku: str, cantidad: str) -> str:
    response = await client_autenticado.post(
        "/api/v1/entradas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_dispositivo": "admin",
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
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_dispositivo": "admin",
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
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_dispositivo": "admin",
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
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_dispositivo": "admin",
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
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_dispositivo": "admin",
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


async def test_create_venta_metodo_pago_invalido_returns_422(client_autenticado):
    # M-3 (docs/review_code.md): antes de fijar el Literal, una variante como
    # "Efectivo" pasaba la validacion y quedaba fuera del total de efectivo
    # del corte de forma silenciosa. Ahora se rechaza en el borde.
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_dispositivo": "admin",
            "folio": "F-007",
            "fecha": "2026-08-19T12:00:00Z",
            "subtotal": "50.00",
            "total": "50.00",
            "metodo_pago": "Efectivo",
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

    assert response.status_code == 422


async def test_create_venta_estado_invalido_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": sucursal_id,
            "usuario_dispositivo": "admin",
            "folio": "F-008",
            "fecha": "2026-08-19T12:00:00Z",
            "subtotal": "50.00",
            "total": "50.00",
            "metodo_pago": "efectivo",
            "estado": "cancelada",
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

    assert response.status_code == 422


async def test_create_venta_sin_local_id_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "sucursal_id": sucursal_id,
            "usuario_dispositivo": "admin",
            "folio": "F-005",
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

    assert response.status_code == 422


async def test_create_venta_reintento_con_mismo_local_id_es_idempotente(client_autenticado, session):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="VTA-005", cantidad="10")
    payload = {
        "local_id": str(uuid.uuid4()),
        "sucursal_id": sucursal_id,
        "usuario_dispositivo": "admin",
        "folio": "F-006",
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
    }

    primera = await client_autenticado.post("/api/v1/ventas", json=payload)
    segunda = await client_autenticado.post("/api/v1/ventas", json=payload)

    assert primera.status_code == 201
    assert segunda.status_code == 200
    assert segunda.json()["id"] == primera.json()["id"]

    ventas = (
        await session.execute(
            select(Venta).where(Venta.local_id == uuid.UUID(payload["local_id"]))
        )
    ).scalars().all()
    assert len(ventas) == 1

    inventario = (
        await session.execute(
            select(Inventario).where(
                Inventario.sucursal_id == uuid.UUID(sucursal_id),
                Inventario.articulo_id == uuid.UUID(articulo_id),
            )
        )
    ).scalar_one()
    assert inventario.cantidad == 7


async def _sembrar_articulo(engine, sku: str, cantidad_inicial: Decimal | None) -> tuple[uuid.UUID, uuid.UUID]:
    """Siembra con commit real un articulo y, si cantidad_inicial no es None,
    su fila de inventario en la primera sucursal."""
    async with engine.connect() as conn_setup:
        session_setup = AsyncSession(bind=conn_setup, expire_on_commit=False)
        sucursal = (await session_setup.execute(select(Sucursal).limit(1))).scalars().first()
        articulo = Articulo(
            sku=sku,
            nombre="Articulo de prueba de concurrencia",
            unidad_medida="pieza",
            precio_venta=Decimal("50.00"),
        )
        session_setup.add(articulo)
        await session_setup.flush()
        if cantidad_inicial is not None:
            session_setup.add(Inventario(sucursal_id=sucursal.id, articulo_id=articulo.id, cantidad=cantidad_inicial))
        await session_setup.commit()
        return sucursal.id, articulo.id


async def _vender_en_conexion_propia(engine, sucursal_id, articulo_id, folio: str, cantidad: Decimal) -> int:
    """Llama a create_venta en su propia conexion/transaccion; devuelve el
    status HTTP (500 si la ruta lanza)."""
    subtotal = cantidad * Decimal("50.00")
    async with engine.connect() as conn:
        session = AsyncSession(bind=conn, expire_on_commit=False)
        payload = VentaCreateSchema(
            local_id=uuid.uuid4(),
            sucursal_id=sucursal_id,
            folio=folio,
            fecha=datetime.now(timezone.utc),
            subtotal=subtotal,
            total=subtotal,
            metodo_pago="efectivo",
            lineas=[
                VentaDetalleCreateSchema(
                    articulo_id=articulo_id,
                    cantidad=cantidad,
                    precio_unitario=Decimal("50.00"),
                    subtotal=subtotal,
                )
            ],
        )
        response = Response(status_code=201)
        try:
            await create_venta(payload, response, db=session, usuario=Usuario(id=uuid.uuid4()))
        except Exception:
            return 500
        return response.status_code


async def _cantidad_final(engine, sucursal_id, articulo_id) -> Decimal:
    async with engine.connect() as conn_check:
        session_check = AsyncSession(bind=conn_check, expire_on_commit=False)
        return (
            await session_check.execute(
                select(Inventario.cantidad).where(
                    Inventario.sucursal_id == sucursal_id, Inventario.articulo_id == articulo_id
                )
            )
        ).scalar_one()


async def _limpiar_ventas(engine, sucursal_id, articulo_id, folios: list[str]) -> None:
    async with engine.connect() as conn_cleanup:
        session_cleanup = AsyncSession(bind=conn_cleanup, expire_on_commit=False)
        ventas_creadas = (
            await session_cleanup.execute(
                select(Venta)
                .options(selectinload(Venta.lineas))
                .where(Venta.sucursal_id == sucursal_id, Venta.folio.in_(folios))
            )
        ).scalars().all()
        venta_ids = [venta.id for venta in ventas_creadas]
        for venta in ventas_creadas:
            await session_cleanup.delete(venta)
        if venta_ids:
            await session_cleanup.execute(
                Movimiento.__table__.delete().where(Movimiento.referencia_id.in_(venta_ids))
            )
        await session_cleanup.execute(
            Inventario.__table__.delete().where(
                Inventario.sucursal_id == sucursal_id, Inventario.articulo_id == articulo_id
            )
        )
        await session_cleanup.execute(Articulo.__table__.delete().where(Articulo.id == articulo_id))
        await session_cleanup.commit()


async def test_create_venta_concurrente_no_pierde_decremento(engine):
    # M-1 (docs/review_code.md): dos ventas concurrentes del mismo
    # articulo/sucursal no deben perder un decremento por lost-update. El
    # lock de with_for_update() solo se ejercita entre conexiones/
    # transacciones reales, asi que este test no usa el fixture `session`
    # (una sola conexion con SAVEPOINT) - siembra y limpia con commits
    # reales contra la base de dev, a diferencia del resto de la suite
    # (nota B-2 de docs/review_code.md).
    sucursal_id, articulo_id = await _sembrar_articulo(engine, "CONC-001", Decimal("10"))
    folios = ["CONC-1", "CONC-2"]
    try:
        await asyncio.gather(
            *(_vender_en_conexion_propia(engine, sucursal_id, articulo_id, folio, Decimal("3")) for folio in folios)
        )
        assert await _cantidad_final(engine, sucursal_id, articulo_id) == Decimal("4.000")
    finally:
        await _limpiar_ventas(engine, sucursal_id, articulo_id, folios)


async def test_create_venta_concurrente_sin_fila_de_inventario_no_da_500(engine):
    # B-1 (docs/review_code.md): 6 ventas concurrentes de un articulo sin
    # fila de inventario en la sucursal daban [500, 201, 201, 201, 201, 201]
    # por uq_inventario_sucursal_articulo. Con INSERT ... ON CONFLICT DO
    # NOTHING + FOR UPDATE todas crean su venta y ningun decremento se pierde.
    sucursal_id, articulo_id = await _sembrar_articulo(engine, "CONC-B1", None)
    folios = [f"B1-{n}" for n in range(6)]
    try:
        estados = await asyncio.gather(
            *(_vender_en_conexion_propia(engine, sucursal_id, articulo_id, folio, Decimal("1")) for folio in folios)
        )
        assert estados == [201] * 6
        assert await _cantidad_final(engine, sucursal_id, articulo_id) == Decimal("-6.000")
    finally:
        await _limpiar_ventas(engine, sucursal_id, articulo_id, folios)


async def test_create_venta_sucursal_id_inexistente_returns_404(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="VTA-404", cantidad="5")

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json={
            "local_id": str(uuid.uuid4()),
            "sucursal_id": str(uuid.uuid4()),
            "usuario_dispositivo": "admin",
            "folio": "F-404",
            "fecha": "2026-08-19T12:00:00Z",
            "subtotal": "50.00",
            "total": "50.00",
            "metodo_pago": "efectivo",
            "lineas": [
                {
                    "articulo_id": articulo_id,
                    "cantidad": "1",
                    "precio_unitario": "50.00",
                    "subtotal": "50.00",
                }
            ],
        },
    )

    assert response.status_code == 404


def _venta_payload(sucursal_id: str, articulo_id: str, **cambios) -> dict:
    payload = {
        "local_id": str(uuid.uuid4()),
        "sucursal_id": sucursal_id,
        "usuario_dispositivo": "cajero-local",
        "folio": "F-M2",
        "fecha": "2026-08-19T12:00:00Z",
        "subtotal": "100.00",
        "descuento": "0.00",
        "impuestos": "0.00",
        "total": "100.00",
        "metodo_pago": "efectivo",
        "lineas": [
            {"articulo_id": articulo_id, "cantidad": "2", "precio_unitario": "50.00", "subtotal": "100.00"}
        ],
    }
    payload.update(cambios)
    return payload


async def test_create_venta_usuario_sale_del_jwt(client_autenticado, usuario_autenticado, session):
    # M-1 (docs/review_code.md): usuario_id = "otro-usuario-inventado" se
    # guardaba tal cual. Ahora se ignora y manda el usuario del JWT; el del
    # dispositivo queda aparte (PLAN.md Parte 38).
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="VTA-M1", cantidad="10")

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json=_venta_payload(sucursal_id, articulo_id, usuario_id="otro-usuario-inventado"),
    )

    assert response.status_code == 201
    body = response.json()
    assert body["usuario_id"] == str(usuario_autenticado.id)
    assert body["usuario_dispositivo"] == "cajero-local"
    movimiento = (
        await session.execute(select(Movimiento).where(Movimiento.referencia_id == uuid.UUID(body["id"])))
    ).scalar_one()
    assert movimiento.usuario_id == str(usuario_autenticado.id)
    assert movimiento.usuario_dispositivo == "cajero-local"


async def test_create_venta_usuario_dispositivo_demasiado_largo_returns_422(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="VTA-M1L", cantidad="10")

    response = await client_autenticado.post(
        "/api/v1/ventas", json=_venta_payload(sucursal_id, articulo_id, usuario_dispositivo="x" * 121)
    )

    assert response.status_code == 422


async def test_create_venta_montos_inconsistentes_returns_422(client_autenticado):
    # M-2 (docs/review_code.md): venta con subtotal 10 y total 0.01 -> 201.
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="VTA-M2", cantidad="10")
    linea_10 = {"articulo_id": articulo_id, "cantidad": "1", "precio_unitario": "10.00", "subtotal": "10.00"}
    casos = {
        "total no cuadra (reproduccion)": {"subtotal": "10.00", "total": "0.01", "lineas": [linea_10]},
        "linea no cuadra": {
            "lineas": [{"articulo_id": articulo_id, "cantidad": "2", "precio_unitario": "50.00", "subtotal": "90.00"}],
            "subtotal": "90.00",
            "total": "90.00",
        },
        "suma de lineas no cuadra": {"subtotal": "120.00", "total": "120.00"},
        "descuento e impuestos": {"descuento": "10.00", "impuestos": "5.00", "total": "100.00"},
    }

    for nombre, cambios in casos.items():
        response = await client_autenticado.post("/api/v1/ventas", json=_venta_payload(sucursal_id, articulo_id, **cambios))
        assert response.status_code == 422, nombre


async def test_create_venta_con_descuento_e_impuestos_consistentes_returns_201(client_autenticado):
    sucursal_id = await _seeded_sucursal_id(client_autenticado)
    articulo_id = await _articulo_con_existencia(client_autenticado, sucursal_id, sku="VTA-M2OK", cantidad="10")

    response = await client_autenticado.post(
        "/api/v1/ventas",
        json=_venta_payload(
            sucursal_id,
            articulo_id,
            descuento="10.00",
            impuestos="5.50",
            total="95.50",
            lineas=[{"articulo_id": articulo_id, "cantidad": "3", "precio_unitario": "33.333", "subtotal": "100.00"}],
        ),
    )

    assert response.status_code == 201
