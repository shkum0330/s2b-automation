import requests
from PyQt5.QtCore import QThread, pyqtSignal

# 범용 ApiWorker는 짧은 요청들을 처리
class ApiWorker(QThread):
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
            self.finished.emit(response.json())
        except requests.exceptions.RequestException as e:
            try:
                error_body = e.response.json()
                self.finished.emit(error_body)
            except:
                self.finished.emit({"error": str(e)})