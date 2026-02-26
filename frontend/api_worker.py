import requests
from PyQt5.QtCore import QThread, pyqtSignal


class ApiWorker(QThread):
    """네트워크 I/O를 백그라운드에서 처리하고 결과를 시그널로 전달한다."""
    finished = pyqtSignal(object)

    def __init__(self, method, url, payload=None, headers=None, timeout=65):
        super().__init__()
        self.method = method
        self.url = url
        self.payload = payload
        self.headers = headers
        self.timeout = timeout

    def run(self):
        """요청/응답 파싱 오류를 일관된 구조로 래핑해 반환한다."""
        method = (self.method or "GET").upper()
        request_kwargs = {
            "headers": self.headers,
            "timeout": self.timeout,
        }

        if self.payload is not None:
            request_kwargs["json"] = self.payload

        try:
            response = requests.request(method, self.url, **request_kwargs)
            response.raise_for_status()

            parsed_json = self._safe_parse_json(response)
            result = {
                "ok": True,
                "headers": dict(response.headers),
                "status_code": response.status_code,
            }

            if parsed_json is not None:
                result["json"] = parsed_json
            else:
                result["json"] = {}
                response_text = response.text.strip()
                if response_text:
                    result["text"] = response_text

            self.finished.emit(result)
        except requests.exceptions.RequestException as exc:
            error_result = {"ok": False, "error": str(exc)}
            if exc.response is not None:
                error_result["status_code"] = exc.response.status_code
                error_json = self._safe_parse_json(exc.response)
                if error_json is not None:
                    error_result["json"] = error_json
                else:
                    error_text = exc.response.text.strip()
                    if error_text:
                        error_result["text"] = error_text
            self.finished.emit(error_result)
        except Exception as exc:
            self.finished.emit({"ok": False, "error": f"예상하지 못한 오류: {exc}"})

    def _safe_parse_json(self, response):
        """응답이 JSON이 아닐 때 예외를 전파하지 않고 None을 반환한다."""
        try:
            return response.json()
        except ValueError:
            return None
