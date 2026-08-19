import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class Sucursal(Base):
    __tablename__ = "sucursales"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    nombre: Mapped[str] = mapped_column(String(120))
    direccion: Mapped[str | None] = mapped_column(String, nullable=True)
    activa: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
