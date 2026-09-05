import uuid
from datetime import datetime, timezone
from decimal import Decimal

from sqlalchemy import DateTime, ForeignKey, Numeric, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class Movimiento(Base):
    __tablename__ = "movimientos"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    # UNIQUE (permite multiples NULL): clave de idempotencia de POST /entradas
    # (PLAN.md Parte 23) - migracion 0012. Las filas de venta/ajuste no
    # llevan local_id, asi que no participan de la deduplicacion.
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True, unique=True)
    sucursal_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("sucursales.id"))
    articulo_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("articulos.id"))
    usuario_id: Mapped[str] = mapped_column(String(120))
    tipo: Mapped[str] = mapped_column(String(20))
    cantidad: Mapped[Decimal] = mapped_column(Numeric(14, 3))
    ubicacion: Mapped[str | None] = mapped_column(String(80), nullable=True)
    referencia_tipo: Mapped[str | None] = mapped_column(String(40), nullable=True)
    referencia_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
