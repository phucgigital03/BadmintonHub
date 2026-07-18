"""Eureka registration via py-eureka-client.

Registers under the name `ai-service` so the existing gateway route `lb://ai-service`
(`/api/ai/**`) keeps working unchanged. Fail-open: if Eureka is down the service still
boots and serves /health (mirrors the platform-wide fail-open convention).
"""

from __future__ import annotations

from app.config import get_settings
from app.logging import get_logger

log = get_logger(__name__)


async def register() -> None:
    settings = get_settings()
    try:
        import py_eureka_client.eureka_client as eureka_client

        await eureka_client.init_async(
            eureka_server=settings.eureka_url,
            app_name=settings.service_name,
            instance_port=settings.service_port,
            should_register=True,
            should_discover=False,  # ai-service calls other services through the gateway
            renewal_interval_in_secs=10,
            duration_in_secs=30,
        )
        log.info(
            "eureka.registered",
            app=settings.service_name,
            port=settings.service_port,
            server=settings.eureka_url,
        )
    except Exception as exc:  # noqa: BLE001 — fail-open so /health stays up
        log.warning("eureka.register_failed", error=str(exc))


async def deregister() -> None:
    try:
        import py_eureka_client.eureka_client as eureka_client

        await eureka_client.stop_async()
        log.info("eureka.deregistered")
    except Exception as exc:  # noqa: BLE001
        log.warning("eureka.deregister_failed", error=str(exc))
