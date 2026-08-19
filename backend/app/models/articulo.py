import uuid
from datetime import datetime, timezone
from decimal import Decimal

from sqlalchemy import Boolean, DateTime, Numeric, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class Articulo(Base):
    __tablename__ = "articulos"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    codigo_barras: Mapped[str | None] = mapped_column(String(60), nullable=True)
    sku: Mapped[str] = mapped_column(String(60), unique=True)
    nombre: Mapped[str] = mapped_column(String(160))
    descripcion: Mapped[str | None] = mapped_column(String, nullable=True)
    categoria: Mapped[str | None] = mapped_column(String(80), nullable=True)
    unidad_medida: Mapped[str] = mapped_column(String(30))
    precio_venta: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    costo: Mapped[Decimal | None] = mapped_column(Numeric(12, 2), nullable=True)
    activo: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
