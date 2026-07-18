from app.config import get_settings


def test_settings_load_from_env():
    s = get_settings()
    assert s.jwt_secret  # required — present via conftest
    assert s.gateway_url == "http://gateway.test"
    assert s.service_name == "ai-service"
    assert s.service_port == 3010
    assert s.llm_provider  # default "gemini"
