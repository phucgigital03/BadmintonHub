"""Alembic environment — runs migrations synchronously (psycopg) against ai_db.

The app uses an async driver (+asyncpg); Alembic uses the sync driver (+psycopg) derived
from the same AI_DB_URL. target_metadata = Base.metadata (all models imported).
"""

from __future__ import annotations

from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from app import models  # noqa: F401 — populate Base.metadata
from app.config import get_settings
from app.db import Base

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def _sync_url() -> str:
    url = get_settings().ai_db_url
    # asyncpg is async-only — Alembic needs a sync driver.
    return url.replace("+asyncpg", "+psycopg")


def run_migrations_offline() -> None:
    context.configure(
        url=_sync_url(),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    section = config.get_section(config.config_ini_section, {})
    section["sqlalchemy.url"] = _sync_url()
    connectable = engine_from_config(
        section, prefix="sqlalchemy.", poolclass=pool.NullPool
    )
    with connectable.connect() as connection:
        context.configure(
            connection=connection, target_metadata=target_metadata, compare_type=True
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
