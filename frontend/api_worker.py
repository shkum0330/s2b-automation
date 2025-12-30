import requests
from PyQt5.QtCore import QThread, pyqtSignal
from config import BASE_URL


class ApiWorker(QThread):
    finished = pyqtSignal(object)
    token_refreshed = pyqtSignal(str)  # 토큰 갱신 성공 시그널
    session_expired = pyqtSignal()  # 세션 만료 시그널

    def __init__(self, method, url, payload=None, headers=None, timeout=65, session=None):
        super().__init__()
        self.method = method
        self.url = url
        self.payload = payload
        self.headers = headers
        self.timeout = timeout
        self.session = session if session else requests.Session()  # 세션 유지

    def run(self):
        try:
            # 1. 최초 요청 시도
            response = self._send_request()

            # 2. 401(Unauthorized) 발생 시 토큰 갱신 시도
            if response.status_code == 401:
                print("🚨 401 Unauthorized 감지! 토큰 갱신을 시도합니다...")

                if self.refresh_access_token():
                    print("✅ 토큰 갱신 성공! 원래 요청을 재시도합니다.")
                    response = self._send_request()
                else:
                    print("❌ 토큰 갱신 실패. 세션이 만료되었습니다.")
                    self.session_expired.emit()
                    return

            response.raise_for_status()

            result = {
                'ok': True,
                'json': response.json(),
                'headers': dict(response.headers)
            }
            self.finished.emit(result)

        except requests.exceptions.RequestException as e:
            error_result = {'ok': False, 'error': str(e)}
            if e.response is not None:
                try:
                    error_result['json'] = e.response.json()
                except ValueError:
                    error_result['text'] = e.response.text
            self.finished.emit(error_result)

    def _send_request(self):
        if self.method.upper() == 'POST':
            return self.session.post(self.url, json=self.payload, headers=self.headers, timeout=self.timeout)
        else:
            return self.session.get(self.url, headers=self.headers, timeout=self.timeout)

    def refresh_access_token(self):
        try:
            # 쿠키(Refresh Token)는 self.session에 저장되어 있음
            refresh_url = f"{BASE_URL}/api/v1/auth/token"
            res = self.session.post(refresh_url, timeout=10)

            if res.status_code == 200:
                new_token = res.headers.get("Authorization")
                if new_token:
                    if self.headers:
                        self.headers["Authorization"] = new_token
                    self.token_refreshed.emit(new_token)
                    return True
            return False
        except Exception as e:
            print(f"토큰 갱신 중 오류 발생: {e}")
            return False