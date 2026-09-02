import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, ForeignKey, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.models.rol import Rol


class Usuario(Base):
    __tablename__ = "usuarios"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    local_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, nullable=True)
    username: Mapped[str] = mapped_column(String(60), unique=True)
    nombre_completo: Mapped[str] = mapped_column(String(120))
    password_hash: Mapped[str | None] = mapped_column(String, nullable=True)
    rol_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("roles.id"))
    # lazy="raise": el rol se carga explicitamente (selectinload) donde se
    # necesita - la dependencia usuario_actual. Un acceso sin eager load
    # falla de forma visible en vez de disparar un lazy load en contexto
    # async.
    rol: Mapped[Rol] = relationship(lazy="raise")
    activo: Mapped[bool] = mapped_column(Boolean, default=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
