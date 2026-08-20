import uuid
from datetime import datetime, timezone
from decimal import Decimal

from sqlalchemy import DateTime, ForeignKey, Numeric, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class CorteCaja(Base):
    __tablename__ = "cortes_caja"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    sucursal_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("sucursales.id"))
    usuario_id: Mapped[str] = mapped_column(String(120))
    tipo: Mapped[str] = mapped_column(String(20))
    fecha_inicio: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    fecha_fin: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    total_ventas: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    total_efectivo: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    total_tarjeta: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    total_retiros: Mapped[Decimal] = mapped_column(Numeric(12, 2), default=0)
    monto_esperado: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    monto_contado: Mapped[Decimal | None] = mapped_column(Numeric(12, 2), nullable=True)
    diferencia: Mapped[Decimal | None] = mapped_column(Numeric(12, 2), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class RetiroEfectivo(Base):
    __tablename__ = "retiros_efectivo"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    sucursal_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("sucursales.id"))
    usuario_id: Mapped[str] = mapped_column(String(120))
    monto: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    motivo: Mapped[str | None] = mapped_column(String(255), nullable=True)
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
