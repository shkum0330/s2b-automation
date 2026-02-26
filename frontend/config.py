"""
프론트엔드 실행 시 필요한 환경설정을 로드한다.

설정 우선순위는 환경변수 > config.ini > 기본값이다.
"""

import configparser
import os
from pathlib import Path

CONFIG_PATH = Path(__file__).with_name("config.ini")
_config = configparser.ConfigParser()
if CONFIG_PATH.exists():
    _config.read(CONFIG_PATH, encoding="utf-8")


def _resolve_setting(env_name, section, option, default_value=""):
    """운영 환경변수와 로컬 설정파일을 순차 조회해 설정값을 반환한다."""
    env_value = os.getenv(env_name)
    if env_value:
        return env_value

    file_value = _config.get(section, option, fallback=default_value)
    return file_value.strip() if isinstance(file_value, str) else file_value


BASE_URL = _resolve_setting(
    "S2B_BASE_URL",
    "server",
    "base_url",
    "http://localhost:8080",
).rstrip("/")

KAKAO_CLIENT_ID = _resolve_setting(
    "S2B_KAKAO_CLIENT_ID",
    "auth",
    "kakao_client_id",
    "",
)

KAKAO_REDIRECT_URI = _resolve_setting(
    "S2B_KAKAO_REDIRECT_URI",
    "auth",
    "kakao_redirect_uri",
    "http://localhost:8989",
)

PAYMENT_CHECKOUT_URL = _resolve_setting(
    "S2B_PAYMENT_CHECKOUT_URL",
    "payment",
    "checkout_url",
    f"{BASE_URL.rstrip('/')}/payment/checkout.html",
)

TOSS_CLIENT_KEY = _resolve_setting(
    "S2B_TOSS_CLIENT_KEY",
    "keys",
    "toss_client_key",
    "",
)
