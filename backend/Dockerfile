FROM python:3.11-slim

# uv: version-pinned binary copied from the official image, so the build needs
# no network to fetch the installer (PLAN.md Parte 30, decision D1).
COPY --from=ghcr.io/astral-sh/uv:0.10.12 /uv /uvx /bin/

WORKDIR /app

# Resolve dependencies from the lockfile only: no dev group (pytest et al.),
# no managed Python download (the slim base already provides 3.11), no attempt
# to install the app itself as a package. Copying only the manifest + lock
# first keeps this layer cached across app-code changes.
ENV UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never
COPY pyproject.toml uv.lock .python-version ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-dev --no-install-project

COPY app ./app
COPY alembic ./alembic
COPY alembic.ini .

# Put the project venv on PATH so `uvicorn` (CMD) and `alembic` (the compose
# migrate service command) resolve without an explicit `uv run`.
ENV PATH="/app/.venv/bin:$PATH"

# Run as a non-privileged user (PLAN.md Parte 29, B-4). The container only
# reads /app at runtime (uvicorn, alembic upgrade); chown keeps bytecode
# caching from failing on the read-only tree.
RUN adduser --system --group --no-create-home app \
    && chown -R app:app /app
USER app

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
