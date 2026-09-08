FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY alembic ./alembic
COPY alembic.ini .

# Run as a non-privileged user (PLAN.md Parte 29, B-4). The container only
# reads /app at runtime (uvicorn, alembic upgrade); chown keeps bytecode
# caching from failing on the read-only tree.
RUN adduser --system --group --no-create-home app \
    && chown -R app:app /app
USER app

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
