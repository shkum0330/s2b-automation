# api_worker.py

import requests
from PyQt5.QtCore import QThread, pyqtSignal

class ApiWorker(QThread):
    """
    범용 ApiWorker
    - 성공 시: {'ok': True, 'json': ..., 'headers': ...} 딕셔너리를 반환
    - 실패 시: {'ok': False, 'error': ..., 'json': ...} 딕셔너리를 반환
    """
    finished = pyqtSignal(object)

    def __init__(self, method, url, payload=None, headers=None, timeout=65):
        super().__init__()
        self.method = method
        self.url = url
        self.payload = payload
        self.headers = headers
        self.timeout = timeout

    def run(self):
        try:
            if self.method.upper() == 'POST':
                response = requests.post(self.url, json=self.payload, headers=self.headers, timeout=self.timeout)
            else:  # GET
                response = requests.get(self.url, headers=self.headers, timeout=self.timeout)

            response.raise_for_status()

            # 성공 시, 응답 본문과 헤더를 함께 딕셔너리로 묶어 반환
            result = {
                'ok': True,
                'json': response.json(),
                'headers': dict(response.headers)
            }
            self.finished.emit(result)

        except requests.exceptions.RequestException as e:
            # 실패 시, 에러 정보와 함께 응답 본문(이 있을 경우)을 반환
            error_result = {'ok': False, 'error': str(e)}
            if e.response is not None:
                try:
                    error_result['json'] = e.response.json()
                except ValueError:
                    error_result['text'] = e.response.text
            self.finished.emit(error_result)