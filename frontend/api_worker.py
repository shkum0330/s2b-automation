import requests
from PyQt5.QtCore import QThread, pyqtSignal


# API 요청을 비동기적으로 처리하는 스레드
class ApiWorker(QThread):
    # 작업 완료 신호
    finished = pyqtSignal(object)

    def __init__(self, method, url, payload=None, headers=None, timeout=65, session=None):
        super().__init__()
        self.method = method
        self.url = url
        self.payload = payload
        self.headers = headers
        self.timeout = timeout

        # 전달받은 세션이 있으면 사용, 없으면 임시 세션 생성 (혹은 requests 모듈 직접 사용)
        self.session = session if session else requests.Session()

    # 스레드 시작 시 자동으로 실행되는 메인 로직
    def run(self):
        try:
            # [수정] self.session을 사용하여 요청 전송 (쿠키가 자동 관리됨)
            if self.method.upper() == 'POST':
                response = self.session.post(self.url, json=self.payload, headers=self.headers, timeout=self.timeout)
            else:  # GET
                response = self.session.get(self.url, headers=self.headers, timeout=self.timeout)

            # HTTP 에러 발생 시 예외 처리
            response.raise_for_status()

            result = {
                'ok': True,
                'json': response.json(),
                'headers': dict(response.headers)
            }
            # 작업 완료 후 결과와 함께 신호 발생
            self.finished.emit(result)

        except requests.exceptions.RequestException as e:
            # 모든 요청 관련 예외 처리
            error_result = {'ok': False, 'error': str(e)}
            if e.response is not None:
                try:
                    error_result['json'] = e.response.json()
                except ValueError:
                    error_result['text'] = e.response.text
            self.finished.emit(error_result)