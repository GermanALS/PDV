import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, String, Uuid
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class SyncConflict(Base):
    """Auditoria de sincronizacion (PLAN.md Parte 3, tabla sync_conflicts).

    No sigue el patron local_id/remote_id/is_synced/deleted_at: es en si
    misma el registro de auditoria, no una entidad que se sincroniza. El id
    lo genera el dispositivo, por lo que la subida (POST) es idempotente.
    """

    __tablename__ = "sync_conflicts"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    entidad: Mapped[str] = mapped_column(String(120))
    entidad_local_id: Mapped[uuid.UUID] = mapped_column(Uuid)
    sucursal_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid, ForeignKey("sucursales.id"), nullable=True
    )
    valor_local: Mapped[dict] = mapped_column(JSONB)
    valor_remoto: Mapped[dict] = mapped_column(JSONB)
    valor_resuelto: Mapped[dict] = mapped_column(JSONB)
    politica_aplicada: Mapped[str] = mapped_column(String(30))
    resuelto_automaticamente: Mapped[bool] = mapped_column(Boolean)
    fecha_deteccion: Mapped[datetime] = mapped_column(DateTime(timezone=True))
