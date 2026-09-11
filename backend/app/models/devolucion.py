import uuid
from datetime import datetime, timezone
from decimal import Decimal

from sqlalchemy import DateTime, ForeignKey, Numeric, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Devolucion(Base):
    __tablename__ = "devoluciones"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    # UNIQUE (permite multiples NULL): clave de idempotencia de POST
    # /devoluciones (PLAN.md Parte 32, gap 1 - la Parte 23 lo agrego a
    # ventas/movimientos/cortes_caja/retiros_efectivo pero no aqui) -
    # migracion 0013.
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True, unique=True)
    sucursal_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("sucursales.id"))
    usuario_id: Mapped[str] = mapped_column(String(120))
    venta_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("ventas.id"), nullable=True)
    folio: Mapped[str] = mapped_column(String(60))
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    estado: Mapped[str] = mapped_column(String(30), default="registrada")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    lineas: Mapped[list["DevolucionDetalle"]] = relationship(back_populates="devolucion", cascade="all, delete-orphan")


class DevolucionDetalle(Base):
    __tablename__ = "devolucion_detalle"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    devolucion_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("devoluciones.id"))
    articulo_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("articulos.id"))
    cantidad: Mapped[Decimal] = mapped_column(Numeric(12, 3))
    motivo: Mapped[str | None] = mapped_column(String(255), nullable=True)
    condicion: Mapped[str | None] = mapped_column(String(30), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    devolucion: Mapped["Devolucion"] = relationship(back_populates="lineas")
