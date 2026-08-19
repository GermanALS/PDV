import uuid
from datetime import datetime, timezone
from decimal import Decimal

from sqlalchemy import DateTime, ForeignKey, Numeric, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Venta(Base):
    __tablename__ = "ventas"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    sucursal_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("sucursales.id"))
    usuario_id: Mapped[str] = mapped_column(String(120))
    folio: Mapped[str] = mapped_column(String(60))
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    subtotal: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    descuento: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    impuestos: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    total: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    metodo_pago: Mapped[str] = mapped_column(String(30))
    estado: Mapped[str] = mapped_column(String(30), default="completada")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    lineas: Mapped[list["VentaDetalle"]] = relationship(back_populates="venta", cascade="all, delete-orphan")


class VentaDetalle(Base):
    __tablename__ = "venta_detalle"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    venta_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("ventas.id"))
    articulo_id: Mapped[uuid.UUID] = mapped_column(Uuid)
    cantidad: Mapped[Decimal] = mapped_column(Numeric(12, 3))
    precio_unitario: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    subtotal: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    venta: Mapped["Venta"] = relationship(back_populates="lineas")
