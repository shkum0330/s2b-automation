# api_worker.py

import requests
from PyQt5.QtCore import QThread, pyqtSignal

# API 요청을 비동기적으로 처리하는 스레드
class ApiWorker(QThread):
    # 작업 완료 신호
    finished = pyqtSignal(object)

    # ApiWorker 초기화
    def __init__(self, method, url, payload=None, headers=None, timeout=65):
        super().__init__()
        self.method = method
        self.url = url
        self.payload = payload
        self.headers = headers
        self.timeout = timeout

    # 스레드 시작 시 자동으로 실행되는 메인 로직
    def run(self):
        try:
            if self.method.upper() == 'POST':
                response = requests.post(self.url, json=self.payload, headers=self.headers, timeout=self.timeout)
            else:  # GET
                response = requests.get(self.url, headers=self.headers, timeout=self.timeout)

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